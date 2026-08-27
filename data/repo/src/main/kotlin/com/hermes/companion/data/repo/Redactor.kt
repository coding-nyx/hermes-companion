package com.hermes.companion.data.repo

/**
 * How much of a source's events leaves the device (`plan/07-privacy/privacy-model.md`).
 * A per-source rule can only LOWER a sensitive event, never raise it.
 */
enum class StreamMode { StreamFull, Summarise, CountOnly, Ignore }

data class RedactedEvent(val send: Boolean, val title: String, val preview: String, val countOnly: Boolean)

/**
 * On-device redaction, applied BEFORE an event reaches the outbox so what is
 * stored for transmission is exactly what would be transmitted. Sensitive
 * categories (OTP / banking / health) are forced to metadata-only regardless of
 * the per-source rule.
 */
object Redactor {
    private val SENSITIVE = listOf(
        "otp", "one-time", "verification code", "verify", "2fa", "passcode",
        "bank", "account", "payment", "transaction", "upi", "card ending",
        "health", "prescription", "medical", "diagnosis",
    )
    private val DIGITS = Regex("\\d")

    fun isSensitive(title: String, preview: String): Boolean {
        val t = (title + " " + preview).lowercase()
        return SENSITIVE.any { t.contains(it) }
    }

    fun apply(mode: StreamMode, title: String, preview: String): RedactedEvent {
        if (mode == StreamMode.Ignore) return RedactedEvent(false, "", "", false)
        val sensitive = isSensitive(title, preview)
        // Sensitive can only be lowered: cap at CountOnly-equivalent (metadata only).
        val effective = if (sensitive && (mode == StreamMode.StreamFull || mode == StreamMode.Summarise)) StreamMode.CountOnly else mode
        return when (effective) {
            StreamMode.StreamFull -> RedactedEvent(true, title, preview, false)
            StreamMode.Summarise -> RedactedEvent(true, redact(title), redact(preview), false)
            StreamMode.CountOnly -> RedactedEvent(true, "", "", true)
            StreamMode.Ignore -> RedactedEvent(false, "", "", false)
        }
    }

    private fun redact(s: String): String = DIGITS.replace(s, "•")
}
