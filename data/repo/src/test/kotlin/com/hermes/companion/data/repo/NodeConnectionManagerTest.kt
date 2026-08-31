package com.hermes.companion.data.repo

import com.hermes.companion.broker.BrokerHello
import com.hermes.companion.broker.FakeNodeBroker
import com.hermes.companion.broker.NodeBroker
import com.hermes.companion.data.db.NodeIdentityEntity
import com.hermes.companion.node.AdapterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Regression coverage for the "node keeps unpairing on app kill" bug.
 *
 * NodeConnectionManager is a Hilt @Singleton: its `live` map survives across
 * service restarts even though the previous launch's sockets and child jobs
 * died. Without the fix in `start()`, `live.containsKey(gatewayId)` returned
 * true against a stale entry and `connect()` was skipped — so a force-stop
 * + relaunch left the WS broker dead forever, and the user saw the node
 * appear to "unpair" until they repeated the pair flow.
 *
 * These tests verify that `start()` clears stale entries before subscribing
 * to `node_identity`, reconnects on a second `start()` call, and that the
 * `stop()` path still tears the entry down cleanly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NodeConnectionManagerTest {

    /** Counts broker constructions; lets the test assert connect() ran. */
    private class CountingBrokerFactory {
        var count = 0
        val factory: (url: String, token: String, hello: () -> BrokerHello) -> NodeBroker = { _, _, _ ->
            count++
            FakeNodeBroker()
        }
    }

    private fun newManager(fakes: Fakes, factory: CountingBrokerFactory) =
        NodeConnectionManager(
            store = fakes.store,
            registry = AdapterRegistry(emptyList()),
            brokerFactory = factory.factory,
        )

    /**
     * Creates a dummy row that evaluates to a Full transport tier (127.0.0.1
     * is local-trusted) so `connect()` doesn't bail out at the tier check.
     * `sealedToken` is a garbage string — `BrokerTokens.reveal` will throw
     * a `Stub!` exception in plain JVM tests and `connect()` falls back to
     * the raw token, which is fine for the FakeNodeBroker.
     */
    private fun mkRow(gatewayId: String, url: String = "http://127.0.0.1:9120", nodeId: String = "n-1") =
        NodeIdentityEntity(
            gatewayId = gatewayId,
            nodeId = nodeId,
            brokerUrl = url,
            sealedToken = "token-$gatewayId",
            expiresAt = Long.MAX_VALUE,
            grantedCapsCsv = "device.status",
            pairedAt = 1L,
        )

    /**
     * Service-owned scope for one lifecycle, sharing the test scheduler so
     * tests can advance virtual time deterministically. We use
     * [advanceTimeBy] (a bounded jump) rather than [advanceUntilIdle]
     * because the NodeEventPump safety loop contains `delay(30_000)` —
     * `advanceUntilIdle` would advance virtual time forever chasing it.
     */
    private fun TestScope.newServiceScope() =
        CoroutineScope(StandardTestDispatcher(testScheduler))

    // ----- the fix -----

    @Test
    fun `start clears stale entries that no longer match any row`() = runTest(timeout = 20.seconds) {
        val fakes = Fakes()
        val factory = CountingBrokerFactory()
        val manager = newManager(fakes, factory)
        fakes.nodeIdentity.upsert(mkRow("gw-stale"))

        // First lifecycle: bootstrap so `live` holds gw-stale.
        val scope1 = newServiceScope()
        manager.start(scope1)
        // Run currently-runnable tasks: preamble + first emission + connect().
        // 100ms is more than enough for connect() — broker factory is sync.
        advanceTimeBy(100)
        assertEquals("sanity: bootstrap connected once", 1, factory.count)
        assertTrue(
            "sanity: bootstrap populated connections",
            manager.connections.value.containsKey("gw-stale"),
        )

        // Simulate the post force-stop state: the service scope is cancelled,
        // but the row was removed from the DB. `live` still holds the
        // now-orphaned gw-stale entry; observeAll() will never mention it
        // again, so the OLD code would leak it forever.
        scope1.cancel()
        fakes.nodeIdentity.deleteForGateway("gw-stale")

        // New lifecycle: a fresh service scope starts again.
        val scope2 = newServiceScope()
        manager.start(scope2)
        // runCurrent drains the synchronous preamble (`live.keys.toList().forEach
        // { stop(it) }`) plus the immediate empty-list emission from the
        // StateFlow. With the fix, gw-stale must be gone BEFORE the
        // connections flow is updated by the new (empty) emission.
        runCurrent()

        assertFalse(
            "stale gw-stale entry must be cleared before subscribe",
            manager.connections.value.containsKey("gw-stale"),
        )

        scope2.cancel()
    }

    @Test
    fun `start reconnects after a scope cancellation that simulates a force-stop relaunch`() =
        runTest(timeout = 20.seconds) {
            val fakes = Fakes()
            val factory = CountingBrokerFactory()
            val manager = newManager(fakes, factory)
            fakes.nodeIdentity.upsert(mkRow("gw-restart"))

            // First lifecycle: open the WS broker.
            val scope1 = newServiceScope()
            manager.start(scope1)
            advanceTimeBy(100)
            assertEquals("first connect() ran", 1, factory.count)

            // Simulate Android service teardown: scope is cancelled. The jobs
            // and the WS socket die with the scope, but `live` retains the
            // (now-dead) entry, and `node_identity` still has the row. This
            // is the exact post force-stop state.
            scope1.cancel()

            // Simulate the user reopening the app: a new service scope runs
            // start() again on the same singleton manager.
            val scope2 = newServiceScope()
            manager.start(scope2)
            advanceTimeBy(100)

            // With the fix: factory is invoked a second time. The old entry
            // was cleared by the preamble, the containsKey guard saw an
            // empty map, and connect() was called for the still-present row.
            // Without the fix: factory stays at 1 — the containsKey guard
            // short-circuited and the WS stays dead until the user re-pairs.
            assertEquals(
                "start() must clear the stale entry and reconnect",
                2,
                factory.count,
            )
            assertTrue(
                "connections should track the reconnected broker",
                manager.connections.value.containsKey("gw-restart"),
            )

            scope2.cancel()
        }

    @Test
    fun `stop removes the entry from connections and cancels the broker`() =
        runTest(timeout = 20.seconds) {
            val fakes = Fakes()
            val factory = CountingBrokerFactory()
            val manager = newManager(fakes, factory)
            fakes.nodeIdentity.upsert(mkRow("gw-stop"))

            val scope = newServiceScope()
            manager.start(scope)
            advanceTimeBy(100)
            assertEquals(1, factory.count)
            assertTrue(manager.connections.value.containsKey("gw-stop"))

            // Direct teardown. Production callers go through start()'s drop
            // loop or unpair(); this exercises stop() in isolation so a
            // regression here (e.g. forgetting to remove from `_connections`)
            // surfaces before any integration test catches it.
            manager.stop("gw-stop")

            assertFalse(
                "stop must drop the entry from the public connections flow",
                manager.connections.value.containsKey("gw-stop"),
            )

            // Calling stop() again is a no-op — important because the
            // preamble in start() calls stop() for every key without first
            // checking membership.
            manager.stop("gw-stop")
            assertFalse(manager.connections.value.containsKey("gw-stop"))

            scope.cancel()
        }
}
