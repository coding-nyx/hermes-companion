package com.hermes.companion.common

import java.io.File

/**
 * Voice feature config (Phase 1 — STT + TTS via Android APIs).
 *
 * Same file-backed bridge pattern as [ActiveGatewayConfig]: :app writes
 * `voice.json` from Settings -> Voice, and the OS-instantiated voice
 * services in :node read it on every reconnect. The two modules live in
 * the same process so the /data/data/<pkg>/files/ path is shared.
 *
 * Phase 1 fields cover only what Android TextToSpeech + SpeechRecognizer
 * need. Wake-word phrase, wake-word model path, and push-to-talk hotkey
 * arrive with Phase 2 (T9).
 *
 * Encoding is intentionally minimal JSON we control on both sides —
 * not a general JSON library, just enough for the flat object below.
 */
object VoiceConfig {
    private const val FILE_NAME = "voice.json"

    /** Whether the always-listening wake-word is enabled. Default off (Phase 2). */
    const val FIELD_WAKE_ENABLED = "wakeEnabled"

    /** Wake-word phrase (e.g. "hey hermes"). Empty string means use default. */
    const val FIELD_WAKE_PHRASE = "wakePhrase"

    /** STT engine id: "android" (Phase 1 default) or "whisper" (Phase 2 opt-in). */
    const val FIELD_STT_ENGINE = "sttEngine"

    /** TTS engine id: "android" (Phase 1 default), "minimax", "grok", or "elevenlabs". */
    const val FIELD_TTS_ENGINE = "ttsEngine"

    /** Selected voice id within the chosen TTS engine. Engine-specific. */
    const val FIELD_TTS_VOICE = "ttsVoice"

    /** Push-to-talk hotkey: "none", "headset", or "bt_button". */
    const val FIELD_PTT_HOTKEY = "pttHotkey"

    /** Sensible defaults for Phase 1 — Android STT + Android TTS, no wake-word yet. */
    val DEFAULT_VOICE: VoiceSnapshot = VoiceSnapshot(
        wakeEnabled = false,
        wakePhrase = "hey hermes",
        sttEngine = "android",
        ttsEngine = "android",
        ttsVoice = "",
        pttHotkey = "none",
    )

    /** Snapshot of the persisted fields. Reads happen off-thread in :node voice services. */
    data class VoiceSnapshot(
        val wakeEnabled: Boolean,
        val wakePhrase: String,
        val sttEngine: String,
        val ttsEngine: String,
        val ttsVoice: String,
        val pttHotkey: String,
    )

    /**
     * Persist the voice snapshot. Idempotent. Called from :app
     * SettingsViewModel whenever the user toggles a field.
     */
    fun writeSync(filesDir: File, snap: VoiceSnapshot) {
        runCatching {
            val payload = buildString {
                append('{')
                appendField(FIELD_WAKE_ENABLED, js(snap.wakeEnabled.toString()))
                append(',')
                appendField(FIELD_WAKE_PHRASE, js(snap.wakePhrase))
                append(',')
                appendField(FIELD_STT_ENGINE, js(snap.sttEngine))
                append(',')
                appendField(FIELD_TTS_ENGINE, js(snap.ttsEngine))
                append(',')
                appendField(FIELD_TTS_VOICE, js(snap.ttsVoice))
                append(',')
                appendField(FIELD_PTT_HOTKEY, js(snap.pttHotkey))
                append('}')
            }
            File(filesDir, FILE_NAME).writeText(payload)
        }
    }

    /** Read the last persisted snapshot or [DEFAULT_VOICE] if no file or malformed. */
    fun readSync(filesDir: File): VoiceSnapshot = runCatching {
        val f = File(filesDir, FILE_NAME)
        if (!f.exists()) return DEFAULT_VOICE
        val text = f.readText()
        val snap = VoiceSnapshot(
            wakeEnabled = parseField(text, FIELD_WAKE_ENABLED).toBooleanStrictOrDefault(false),
            wakePhrase = parseField(text, FIELD_WAKE_PHRASE) ?: DEFAULT_VOICE.wakePhrase,
            sttEngine = parseField(text, FIELD_STT_ENGINE) ?: DEFAULT_VOICE.sttEngine,
            ttsEngine = parseField(text, FIELD_TTS_ENGINE) ?: DEFAULT_VOICE.ttsEngine,
            ttsVoice = parseField(text, FIELD_TTS_VOICE) ?: "",
            pttHotkey = parseField(text, FIELD_PTT_HOTKEY) ?: DEFAULT_VOICE.pttHotkey,
        )
        // Defensive: empty strings fall back to defaults for fields where
        // empty is meaningless (stt/tts/ptt), but ttsVoice may legitimately
        // be empty (= system default voice), so leave it alone.
        snap.copy(
            sttEngine = snap.sttEngine.ifEmpty { DEFAULT_VOICE.sttEngine },
            ttsEngine = snap.ttsEngine.ifEmpty { DEFAULT_VOICE.ttsEngine },
            pttHotkey = snap.pttHotkey.ifEmpty { DEFAULT_VOICE.pttHotkey },
        )
    }.getOrDefault(DEFAULT_VOICE)

    // --- minimal JSON helpers, copied from ActiveGatewayConfig to keep
    // the two configs self-contained (no shared base) ---------------------

    private fun StringBuilder.appendField(key: String, jsValue: String) {
        append('"').append(key).append('"').append(':').append(jsValue)
    }

    /** JSON-encodes a string value (quoted, with escapes for \, ", control chars). */
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

    /** Pulls the string value of [key] from a flat JSON object. */
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

    /**
     * Best-effort boolean parse for the wakeEnabled field. We accept
     * "true"/"false" (the only values we write); anything else -> default.
     */
    private fun String?.toBooleanStrictOrDefault(default: Boolean): Boolean {
        val v = this ?: return default
        return when (v.lowercase()) {
            "true"  -> true
            "false" -> false
            else    -> default
        }
    }
}
