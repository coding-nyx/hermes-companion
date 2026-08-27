package com.hermes.companion.data.repo

import com.hermes.companion.backend.HermesBackend
import com.hermes.companion.backend.MockHermesBackend
import com.hermes.companion.data.db.RunEntity
import com.hermes.companion.data.db.toEntity
import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayHealth
import com.hermes.companion.domain.GatewayKind
import com.hermes.companion.domain.RunEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionSupervisorTest {

    /** Counts probes and can be flipped between answering and refusing. */
    private class ProbeBackend(id: String, var healthy: Boolean) : HermesBackend {
        override val gateway = GatewayConnection(id, id, GatewayKind.RemoteHttp, "http://host/$id", "none")
        var probes = 0
        override suspend fun capabilities(profile: String?): Map<String, Boolean> {
            probes++
            if (!healthy) throw IOException("Connection refused")
            return mapOf("chat.stream" to true)
        }
        override suspend fun listProfiles() = emptyList<com.hermes.companion.domain.AgentProfile>()
        override suspend fun listSessionsForProfile(gatewayId: String, profileId: String) =
            emptyList<com.hermes.companion.domain.Session>()
        override suspend fun createSession(route: ConversationRoute, title: String): com.hermes.companion.domain.Session =
            com.hermes.companion.domain.Session(
                sessionId = "sess",
                profileId = route.profileId,
                gatewayId = route.gatewayId,
                title = title,
            )
        override suspend fun listMessages(route: ConversationRoute) = emptyList<com.hermes.companion.domain.Message>()
        override suspend fun submit(route: ConversationRoute, text: String) = "run-x"
        override fun runEvents(route: ConversationRoute, runId: String): Flow<RunEvent> =
            kotlinx.coroutines.flow.emptyFlow()
        override suspend fun stopRun(route: ConversationRoute, runId: String) = Unit
        override suspend fun decideApproval(
            route: ConversationRoute,
            runId: String,
            requestId: String,
            option: ApprovalOption,
        ) = Unit
    }

    private suspend fun setUp(fakes: Fakes, vararg backends: HermesBackend): BackendRegistry {
        backends.forEach { fakes.gateways.upsert(it.gateway.toEntity(health = GatewayHealth.Unknown)) }
        return BackendRegistry(backends.toList())
    }

    private fun supervisorFor(fakes: Fakes, registry: BackendRegistry, scope: CoroutineScope) =
        ConnectionSupervisor(fakes.store, registry, RunTracker(scope, registry, fakes.store))

    /**
     * Runs [body] with a supervision scope that is always cancelled, including
     * on assertion failure. A leaked supervision loop would otherwise keep the
     * test scheduler busy forever at the end of the test.
     */
    private fun supervisionTest(body: suspend TestScope.(CoroutineScope) -> Unit) =
        runTest(timeout = 20.seconds) {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            try {
                body(scope)
            } finally {
                scope.cancel()
            }
        }

    // ----- backoff arithmetic -----

    @Test
    fun `backoff grows, jitters, and is capped`() {
        // Deterministic jitter so the progression is assertable.
        val full = { 1.0 }
        val seen = (1..13).map { failures -> backoffDelay(failures, full) }
        assertEquals(ConnectionSupervisor.FIRST_BACKOFF_MS, seen.first())
        assertEquals(ConnectionSupervisor.MAX_BACKOFF_MS, seen.last())
        assertTrue("must be monotonic under full jitter", seen.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("must never exceed the cap", seen.all { it <= ConnectionSupervisor.MAX_BACKOFF_MS })
    }

    @Test
    fun `jitter never collapses to a busy loop`() {
        // Even with the smallest random value, a retry cannot be immediate.
        val floor = ConnectionSupervisor.FIRST_BACKOFF_MS / 2
        listOf(0.0, 0.0001, 0.5).forEach { r ->
            assertTrue(backoffDelay(1, { r }) >= floor)
            assertTrue(backoffDelay(8, { r }) >= floor)
        }
    }

    // ----- supervision loops -----

    @Test
    fun `an unreachable gateway is retried, but backs off`() = supervisionTest { scope ->
        val fakes = Fakes()
        val dead = ProbeBackend("gw-dead", healthy = false)
        val registry = setUp(fakes, dead)
        supervisorFor(fakes, registry, scope).start(scope)

        advanceTimeBy(10)
        assertEquals("probes immediately", 1, dead.probes)

        // Nothing again inside the jitter floor.
        advanceTimeBy(ConnectionSupervisor.FIRST_BACKOFF_MS / 2 - 20)
        assertEquals("must not busy-loop", 1, dead.probes)

        advanceTimeBy(5 * 60_000)
        assertTrue("keeps retrying: ${dead.probes}", dead.probes > 3)
        assertTrue("but backs off rather than hammering: ${dead.probes}", dead.probes < 40)

        val row = fakes.gateways.rows.value.single()
        assertEquals(GatewayHealth.Down.name, row.health)
        assertNotNull(row.error)
    }

    @Test
    fun `a healthy gateway settles into a steady poll`() = supervisionTest { scope ->
        val fakes = Fakes()
        val live = ProbeBackend("gw-live", healthy = true)
        val registry = setUp(fakes, live)
        supervisorFor(fakes, registry, scope).start(scope)

        advanceTimeBy(10)
        assertEquals(1, live.probes)

        advanceTimeBy(3 * ConnectionSupervisor.HEALTHY_POLL_MS)
        assertEquals("one probe per interval, no backoff", 4, live.probes)
        assertEquals(GatewayHealth.Healthy.name, fakes.gateways.rows.value.single().health)
        assertNull(fakes.gateways.rows.value.single().error)
    }

    @Test
    fun `one gateway failing does not slow another`() = supervisionTest { scope ->
        val fakes = Fakes()
        val live = ProbeBackend("gw-live", healthy = true)
        val dead = ProbeBackend("gw-dead", healthy = false)
        val registry = setUp(fakes, live, dead)
        supervisorFor(fakes, registry, scope).start(scope)

        advanceTimeBy(3 * ConnectionSupervisor.HEALTHY_POLL_MS + 10)

        // The healthy gateway keeps its own clock regardless of its neighbour.
        assertEquals(4, live.probes)
        assertEquals(GatewayHealth.Healthy.name, fakes.gateways.rows.value.single { it.id == "gw-live" }.health)
        assertEquals(GatewayHealth.Down.name, fakes.gateways.rows.value.single { it.id == "gw-dead" }.health)
    }

    @Test
    fun `forgetting a gateway stops supervising it`() = supervisionTest { scope ->
        val fakes = Fakes()
        val backend = ProbeBackend("gw-live", healthy = true)
        val registry = setUp(fakes, backend)
        supervisorFor(fakes, registry, scope).start(scope)

        advanceTimeBy(10)
        assertEquals(1, backend.probes)

        fakes.gateways.delete("gw-live")
        advanceTimeBy(5 * ConnectionSupervisor.HEALTHY_POLL_MS)

        assertEquals("no probes after the row is gone", 1, backend.probes)
    }

    @Test
    fun `a successful probe resumes a run left open`() = supervisionTest { scope ->
        // Reconciliation asks only about outstanding work; a run that was open
        // when we stopped gets picked up again without the UI asking.
        val fakes = Fakes()
        val backend = MockHermesBackend(
            GatewayConnection("gw-test", "T", GatewayKind.Local, "mock://gw-test", "none"),
            profileIds = listOf("ash"),
        )
        val registry = setUp(fakes, backend)
        val route = ConversationRoute("gw-test", "ash", "sess-gw-test-ash-1")
        val runId = backend.submit(route, "hello")
        fakes.runs.upsert(
            RunEntity(route.gatewayId, route.profileId, route.sessionId, runId, "streaming", null, null, null, 0),
        )

        supervisorFor(fakes, registry, scope).start(scope)
        advanceTimeBy(1_000)

        assertEquals("completed", fakes.runs.rows.value.single().state)
        assertTrue(fakes.messages.rows.value.any { it.role == "assistant" && it.text.isNotEmpty() })
    }

    @Test
    fun `status reports counts, not a boolean`() = supervisionTest { scope ->
        val fakes = Fakes()
        val live = ProbeBackend("gw-live", healthy = true)
        val dead = ProbeBackend("gw-dead", healthy = false)
        val registry = setUp(fakes, live, dead)
        val supervisor = supervisorFor(fakes, registry, scope)
        supervisor.start(scope)

        advanceTimeBy(100)

        val status = supervisor.status.first()
        assertEquals(2, status.gateways)
        assertEquals(1, status.live)
        assertEquals(1, status.unreachable)
    }
}
