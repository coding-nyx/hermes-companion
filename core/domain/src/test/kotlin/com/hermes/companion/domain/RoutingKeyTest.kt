package com.hermes.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoutingKeyTest {

    @Test
    fun `conversation route equality is structural`() {
        val a = ConversationRoute("gw-home", "ash", "sess-1")
        val b = ConversationRoute("gw-home", "ash", "sess-1")
        assertEquals(a, b)
    }

    @Test
    fun `same session id on different profiles does not collide`() {
        val a = ConversationRoute("gw-home", "ash", "sess-1")
        val b = ConversationRoute("gw-home", "misty", "sess-1")
        assertNotEquals(a, b)
    }

    @Test
    fun `same profile id on different gateways does not collide`() {
        val a = ConversationRoute("gw-home", "ash", "sess-1")
        val b = ConversationRoute("gw-cloud", "ash", "sess-1")
        assertNotEquals(a, b)
    }

    @Test
    fun `node route always carries its conversation route`() {
        val conv = ConversationRoute("gw-home", "ash", "sess-1")
        val node = NodeRoute(
            conversation = conv,
            nodeId = "node-s22",
            capability = "notifications.read",
            requestId = "req-1",
        )
        assertEquals(conv, node.conversation)
        assertEquals("notifications.read", node.capability)
    }
}
