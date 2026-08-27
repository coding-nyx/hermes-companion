package com.hermes.companion.common

import java.io.File

/**
 * T7 (companion-gateway-routing.md): the active gateway URL + nodeId are
 * written by :app to a small JSON file under the app's files dir so the
 * OS-instantiated NLS can read them without a DB join. The two modules
 * live in the same process so the /data/data/<pkg>/files/ path is shared.
 *
 * SettingsViewModel writes via [writeSync], HermesNotificationListenerService
 * reads via [readSync] on every reconnect.
 *
 * Lives in :core:common because :app (Hilt) and :node (OS-instantiated)
 * both depend on :core:common, but neither depends on the other. The
 * encoding is intentionally minimal JSON we control on both sides - not a
 * general JSON library, just enough for `{url: <str>, nodeId: <str>}`.
 */
object ActiveGatewayConfig {
    private const val FILE_NAME = "active_gateway.json"

    /** Persist (url, nodeId) as a small JSON object. Idempotent. */
    fun writeSync(filesDir: File, url: String, nodeId: String) {
        runCatching {
            val payload = "{\"url\":\"" + js(url) + "\",\"nodeId\":\"" + js(nodeId) + "\"}"
            File(filesDir, FILE_NAME).writeText(payload)
        }
    }

    /** Read the last persisted pair or (null, null) if no file or malformed. */
    fun readSync(filesDir: File): Pair<String?, String?> = runCatching {
        val f = File(filesDir, FILE_NAME)
        if (!f.exists()) return Pair(null, null)
        val text = f.readText()
        Pair(parseField(text, "url"), parseField(text, "nodeId"))
    }.getOrDefault(Pair(null, null))

    /** Minimal JSON string escaper for our payload. */
    private fun js(s: String): String {
        val sb = StringBuilder(2 + s.length)
        sb.append('"')
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\")
            '"'  -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        sb.append('"')
        return sb.toString()
    }

    /** Pull the string value of [key] from a flat JSON object. */
    private fun parseField(text: String, key: String): String? {
        val needle = "\"" + key + "\":\""
        val start = text.indexOf(needle)
        if (start == -1) return null
        var i = start + needle.length
        val sb = StringBuilder()
        while (i < text.length) {
            val c = text[i]
            if (c == '"') return sb.toString()
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2; continue }
                    '"'  -> { sb.append('"');  i += 2; continue }
                    'n'  -> { sb.append('\n'); i += 2; continue }
                    'r'  -> { sb.append('\r'); i += 2; continue }
                    't'  -> { sb.append('\t'); i += 2; continue }
                    else -> { /* fall through */ }
                }
            }
            sb.append(c)
            i += 1
        }
        return null
    }
}
