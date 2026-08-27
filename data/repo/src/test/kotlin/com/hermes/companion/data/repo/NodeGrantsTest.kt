package com.hermes.companion.data.repo

import com.hermes.companion.broker.FakeNodeBroker
import com.hermes.companion.data.db.GrantEntity
import com.hermes.companion.domain.AndroidRequirement
import com.hermes.companion.domain.CapabilityHealth
import com.hermes.companion.domain.GrantMode
import com.hermes.companion.domain.LeaseResult
import com.hermes.companion.domain.NodeCapability
import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.ReceiptStatus
import com.hermes.companion.node.AdapterRegistry
import com.hermes.companion.node.CapabilityAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class StubAdapter(
    override val capability: NodeCapability,
    private val status: ReceiptStatus = ReceiptStatus.Completed,
) : CapabilityAdapter {
    override val requires: Set<AndroidRequirement> = emptySet()
    override fun health() = CapabilityHealth.Working
    override suspend fun invoke(command: NodeCommand) =
        Receipt(command.requestId, capability.family, status, "ok", "{}", 1L)
}

class NodeGrantsTest {

    private fun grant(store: com.hermes.companion.data.db.CompanionStore, cap: String, mode: GrantMode) = kotlinx.coroutines.runBlocking {
        store.grants.upsert(GrantEntity("gw", "", "n", cap, mode.name, null, null, 1L))
    }

    private fun dispatcher(fakes: Fakes, adapter: CapabilityAdapter) = NodeDispatcher(
        gatewayId = "gw", nodeId = "n",
        registry = AdapterRegistry(listOf(adapter)),
        broker = FakeNodeBroker(),
        grants = GrantChecker(fakes.store),
        leases = LeaseManager(fakes.store),
    )

    @Test
    fun `lease is mutually exclusive, released, then re-acquirable`() = runTest {
        val fakes = Fakes()
        val lm = LeaseManager(fakes.store)
        assertTrue(lm.acquire("camera.snap", "gw", "", "r1", 10_000) is LeaseResult.Acquired)
        assertTrue(lm.acquire("camera.snap", "gw", "", "r2", 10_000) is LeaseResult.Held)
        lm.release("camera.snap", "r1")
        assertTrue(lm.acquire("camera.snap", "gw", "", "r3", 10_000) is LeaseResult.Acquired)
    }

    @Test
    fun `grant checker honours mode`() = runTest {
        val fakes = Fakes()
        val gc = GrantChecker(fakes.store)
        assertTrue(gc.evaluate("gw", "", "n", "device.status") is GrantDecision.Denied) // no grant
        grant(fakes.store, "device.status", GrantMode.AllowWhileUnlocked)
        assertEquals(GrantDecision.Allowed, gc.evaluate("gw", "", "n", "device.status"))
        grant(fakes.store, "device.status", GrantMode.Deny)
        assertTrue(gc.evaluate("gw", "", "n", "device.status") is GrantDecision.Denied)
    }

    @Test
    fun `dispatch refuses without a grant and completes with one`() = runTest {
        val fakes = Fakes()
        val d = dispatcher(fakes, StubAdapter(NodeCapability.DeviceStatus))
        val cmd = NodeCommand("r1", "device.status", profile = "")
        assertEquals(ReceiptStatus.Refused, d.dispatch(cmd).status)
        grant(fakes.store, "device.status", GrantMode.AllowWhileUnlocked)
        assertEquals(ReceiptStatus.Completed, d.dispatch(cmd).status)
    }

    @Test
    fun `dispatch refuses an exclusive capability whose lease is held`() = runTest {
        val fakes = Fakes()
        grant(fakes.store, "camera.snap", GrantMode.AllowWhileUnlocked)
        // Someone else already holds the camera lease.
        LeaseManager(fakes.store).acquire("camera.snap", "gw", "", "other", 30_000)
        val d = dispatcher(fakes, StubAdapter(NodeCapability.CameraSnap))
        val receipt = d.dispatch(NodeCommand("r9", "camera.snap", profile = ""))
        assertEquals(ReceiptStatus.Refused, receipt.status)
        assertTrue(receipt.detail.contains("held by"))
    }
}
