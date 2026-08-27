package com.hermes.companion.net

import okio.BufferedSource

/**
 * Minimal SSE parser per the WHATWG spec: `event:`, `data:`, comments, blank
 * line terminates a frame, unknown fields ignored.
 *
 * Per spec a single leading space after the colon is stripped — and only one.
 * Trimming the whole value corrupts payloads whose content is significant at
 * the edges.
 *
 * [onFrame] returns false to stop parsing, which is how a cancelled collector
 * unwinds the read loop instead of blocking until the socket dies.
 */
class SseParser {

    fun parse(source: BufferedSource, onFrame: (event: String, data: String) -> Boolean) {
        var event = "message"
        val data = StringBuilder()

        fun flush(): Boolean {
            if (data.isEmpty()) return true
            val payload = data.toString()
            data.clear()
            val name = event
            event = "message"
            return onFrame(name, payload)
        }

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            when {
                line.startsWith(":") -> Unit                       // comment or heartbeat
                line.isEmpty() -> if (!flush()) return
                line.startsWith("event:") -> event = stripField(line, "event:")
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(stripField(line, "data:"))
                }
                else -> Unit                                        // ignored field
            }
        }
        flush()
    }

    /** Removes the field name and exactly one optional leading space. */
    private fun stripField(line: String, field: String): String {
        val raw = line.substring(field.length)
        return if (raw.startsWith(" ")) raw.substring(1) else raw
    }
}
