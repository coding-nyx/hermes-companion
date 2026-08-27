package com.hermes.companion.transport.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {
    @Test
    fun `deriveNodeId is stable and derived from the public-key bytes`() {
        val pub = "p256:" + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(65) { it.toByte() })
        val a = NodeIdentityKey.deriveNodeId(pub)
        val b = NodeIdentityKey.deriveNodeId(pub)
        assertEquals(a, b)
        assertTrue(a.startsWith("nd_"))
        // A different key gives a different id.
        val other = "p256:" + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(65) { (it + 1).toByte() })
        assertTrue(NodeIdentityKey.deriveNodeId(other) != a)
    }

    @Test
    fun `unauthenticated event stream sets the SSE Accept header`() {
        val f = RequestFactory.create("http://host:7800", GatewayCredentials.None)
        val req = f.getEventStream("/v1/runs/r1/events")
        assertEquals("text/event-stream", req.header("Accept"))
        assertNull(req.header("Authorization"))
    }

    @Test
    fun `credentialsFor is None without an envelope and SealedRef with one`() {
        assertTrue(RequestFactory.credentialsFor("none") is GatewayCredentials.None)
        assertTrue(RequestFactory.credentialsFor("gw-1", null) is GatewayCredentials.None)
        assertTrue(RequestFactory.credentialsFor("gw-1", "envelope") is GatewayCredentials.SealedRef)
    }
}
