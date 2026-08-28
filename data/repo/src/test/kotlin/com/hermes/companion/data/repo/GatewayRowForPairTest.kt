package com.hermes.companion.data.repo

import com.hermes.companion.data.db.GatewayEntity
import com.hermes.companion.domain.GatewayHealth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T8: after a successful NodeConnection.pair we want a gateways row to exist
 * so Settings -> Gateways shows the paired gateway, and a subsequent
 * refreshGateway can populate profiles + sessions for it.
 *
 * The helper [ensureGatewayRowForPair] is a free function in :data:repo so
 * [NodeConnection.pair] can call it without pulling in BackendRegistry.
 */
class GatewayRowForPairTest {

    @Test
    fun `ensureGatewayRowForPair writes a row when none exists for that URL`() = runTest {
        val fakes = Fakes()
        val out = ensureGatewayRowForPair(
            store = fakes.store,
            baseUrl = "http://lab:9120",
            gatewayId = "node-deadbeef",
        )
        assertEquals("http://lab:9120", out.url)
        assertTrue("id should start with gw-", out.id.startsWith("gw-"))
        assertEquals(1, fakes.gateways.all().size)
    }

    @Test
    fun `ensureGatewayRowForPair is idempotent when a row already exists for that URL`() = runTest {
        val fakes = Fakes()
        fakes.gateways.upsert(
            GatewayEntity(
                id = "gw-existing",
                label = "Lab (pre-existing)",
                kind = "remoteHttp",
                url = "http://lab:9120",
                authRef = "none",
                health = GatewayHealth.Healthy.name,
                lastOkAt = 1L,
                staleSince = null,
                error = null,
            ),
        )
        val out = ensureGatewayRowForPair(
            store = fakes.store,
            baseUrl = "http://lab:9120",
            gatewayId = "node-deadbeef",
        )
        assertEquals("gw-existing", out.id)
        assertEquals("Lab (pre-existing)", out.label)
        assertEquals(1, fakes.gateways.all().size)
    }

    @Test
    fun `ensureGatewayRowForPair derives a stable id for the same URL`() = runTest {
        val fakes = Fakes()
        val a = ensureGatewayRowForPair(fakes.store, "http://lab:9120", "node-1")
        val b = ensureGatewayRowForPair(fakes.store, "http://lab:9120", "node-2")
        assertEquals(a.id, b.id)
        assertEquals(1, fakes.gateways.all().size)
    }
}
