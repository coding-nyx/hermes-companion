package com.hermes.companion.discovery

import com.hermes.companion.domain.TransportTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportTiersTest {

    @Test
    fun `tls is always full tier`() {
        assertEquals(TransportTier.Full, evaluateTier("https://example.com/gw"))
        assertEquals(TransportTier.Full, evaluateTier("wss://example.com/ws/node"))
    }

    @Test
    fun `cleartext to a public host is limited`() {
        assertEquals(TransportTier.Limited, evaluateTier("http://example.com:7800/gw-home"))
        assertEquals(TransportTier.Limited, evaluateTier("http://8.8.8.8:7800"))
    }

    @Test
    fun `cleartext to loopback, emulator and mdns is full`() {
        assertEquals(TransportTier.Full, evaluateTier("http://127.0.0.1:9120"))
        assertEquals(TransportTier.Full, evaluateTier("http://localhost:9120/gw"))
        assertEquals(TransportTier.Full, evaluateTier("http://10.0.2.2:7800"))
        assertEquals(TransportTier.Full, evaluateTier("http://mac-studio.local:7800"))
    }

    @Test
    fun `cleartext over a tailnet is full because wireguard encrypts it`() {
        assertEquals(TransportTier.Full, evaluateTier("http://100.88.4.63:7800/gw-hub11"))
        assertEquals(TransportTier.Full, evaluateTier("http://hub-11.tail1234.ts.net:9120"))
        assertTrue(isTailnetHost("100.64.0.1"))
        assertTrue(isTailnetHost("100.127.255.255"))
        assertTrue(isTailnetHost("studio.corp.ts.net"))
        assertFalse(isTailnetHost("100.63.0.1")) // just below the CGNAT range
        assertFalse(isTailnetHost("100.128.0.1")) // just above
        assertFalse(isTailnetHost("192.168.1.5"))
    }

    @Test
    fun `hostOf strips scheme, port, path and userinfo`() {
        assertEquals("100.88.4.63", hostOf("http://100.88.4.63:7800/gw-hub11"))
        assertEquals("example.com", hostOf("https://user@example.com:443/x?y=1"))
        assertEquals("host.ts.net", hostOf("http://host.ts.net"))
    }
}
