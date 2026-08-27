package com.hermes.companion.net

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {

    private fun parse(raw: String): List<Pair<String, String>> {
        val frames = mutableListOf<Pair<String, String>>()
        SseParser().parse(Buffer().writeUtf8(raw)) { event, data ->
            frames += event to data
            true
        }
        return frames
    }

    @Test
    fun `named event with data`() {
        val frames = parse("event: run.completed\ndata: {\"a\":1}\n\n")
        assertEquals(listOf("run.completed" to "{\"a\":1}"), frames)
    }

    @Test
    fun `event name resets to message after a frame`() {
        val frames = parse("event: tool.started\ndata: one\n\ndata: two\n\n")
        assertEquals(listOf("tool.started" to "one", "message" to "two"), frames)
    }

    @Test
    fun `multi-line data joins with newline`() {
        val frames = parse("data: line one\ndata: line two\n\n")
        assertEquals(listOf("message" to "line one\nline two"), frames)
    }

    @Test
    fun `exactly one leading space is stripped`() {
        // The old parser trimmed the whole value, silently eating significant
        // whitespace at both edges.
        val frames = parse("data:  two spaces then text  \n\n")
        assertEquals(listOf("message" to " two spaces then text  "), frames)
    }

    @Test
    fun `comments and heartbeats are ignored`() {
        val frames = parse(":ok\n:hb\ndata: after\n\n")
        assertEquals(listOf("message" to "after"), frames)
    }

    @Test
    fun `unknown fields are ignored`() {
        val frames = parse("id: 7\nretry: 500\ndata: kept\n\n")
        assertEquals(listOf("message" to "kept"), frames)
    }

    @Test
    fun `trailing frame without a blank line is flushed`() {
        val frames = parse("data: truncated")
        assertEquals(listOf("message" to "truncated"), frames)
    }

    @Test
    fun `blank data does not emit an empty frame`() {
        val frames = parse("\n\n\ndata: real\n\n")
        assertEquals(listOf("message" to "real"), frames)
    }

    @Test
    fun `returning false stops parsing`() {
        val seen = mutableListOf<String>()
        SseParser().parse(Buffer().writeUtf8("data: one\n\ndata: two\n\ndata: three\n\n")) { _, data ->
            seen += data
            seen.size < 2                       // stop after the second frame
        }
        assertEquals(listOf("one", "two"), seen)
    }
}
