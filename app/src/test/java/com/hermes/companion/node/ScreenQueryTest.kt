package com.hermes.companion.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenQueryTest {

    private val nodes = listOf(
        ScreenNode(id = 0, text = "Messages", clickable = true, l = 0, t = 0, r = 200, b = 80),
        ScreenNode(id = 1, text = "Search", editable = true, l = 10, t = 100, r = 400, b = 160),
        ScreenNode(id = 2, text = "Maya Chen", clickable = true, l = 0, t = 200, r = 400, b = 280),
        ScreenNode(id = 3, desc = "Send", clickable = true, l = 320, t = 700, r = 400, b = 780),
    )

    @Test
    fun `exact label wins`() {
        val hit = ScreenQuery.findByText(nodes, "Messages")
        assertEquals(0, hit?.id)
    }

    @Test
    fun `substring prefers clickable`() {
        val hit = ScreenQuery.findByText(nodes, "maya")
        assertEquals(2, hit?.id)
    }

    @Test
    fun `content description is a label`() {
        val hit = ScreenQuery.findByText(nodes, "send")
        assertEquals(3, hit?.id)
    }

    @Test
    fun `empty query is miss`() {
        assertNull(ScreenQuery.findByText(nodes, "  "))
    }

    @Test
    fun `smallest containing node at point`() {
        val inner = ScreenNode(id = 9, text = "chip", clickable = true, l = 40, t = 40, r = 80, b = 70)
        val hit = ScreenQuery.findAt(nodes + inner, 50, 50)
        assertEquals(9, hit?.id)
    }

    @Test
    fun `outside bounds is miss`() {
        assertNull(ScreenQuery.findAt(nodes, 9000, 9000))
    }
}
