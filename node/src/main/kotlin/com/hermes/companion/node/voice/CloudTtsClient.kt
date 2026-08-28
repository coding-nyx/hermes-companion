package com.hermes.companion.node.voice

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Phase 1: thin HTTP client for cloud TTS providers (MiniMax, Grok, ElevenLabs).
 * The companion gateway's [POST /v1/voice/synthesize] endpoint already proxies
 * to the upstream Hermes agent's pooled TTS providers; this class just calls
 * that endpoint with the right `provider` field. No provider-specific API
 * keys live on the phone — auth is owned by the gateway.
 *
 * Failures are surfaced via [Result] so callers can walk the provider chain
 * Android -> MiniMax -> Grok -> ElevenLabs per the design.
 */
class CloudTtsClient(
    private val gatewayBaseUrl: String,
    private val nodeId: String,
    private val provider: Provider,
    private val voiceId: String,
    private val client: OkHttpClient = defaultClient,
) {

    enum class Provider(val wire: String) {
        Minimax("minimax"),
        Grok("grok"),
        ElevenLabs("elevenlabs"),
        Unknown("unknown");

        companion object {
            fun parse(s: String?): Provider = when (s?.lowercase()) {
                "minimax"   -> Minimax
                "grok"      -> Grok
                "elevenlabs"-> ElevenLabs
                "android"   -> Unknown  // Android is local; this client isn't called for it
                else        -> Unknown
            }
        }
    }

    /** Result of a synthesize call. [audioBytes] is raw MP3 (or PCM for some providers). */
    data class SynthesisResult(
        val audioBytes: ByteArray,
        val contentType: String,
        val cachedUrl: String?,
    )

    /**
     * Synthesize [text] to audio. Returns the bytes + metadata on success.
     * The phone never sees provider-specific API keys — only the gateway URL.
     */
    fun synthesize(text: String): Result<SynthesisResult> = runCatching {
        require(provider != Provider.Unknown) { "provider must be minimax/grok/elevenlabs" }
        val url = gatewayBaseUrl.trimEnd('/') + "/v1/voice/synthesize"
        val json = """
            {"text":${js(text)},"provider":"${provider.wire}","voiceId":${js(voiceId)},"nodeId":${js(nodeId)}}
        """.trimIndent()
        val req = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("cloud TTS failed: HTTP ${resp.code} ${resp.message}")
            }
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            val ct = resp.header("Content-Type") ?: "audio/mpeg"
            val cached = resp.header("X-Cache-Url")
            SynthesisResult(bytes, ct, cached)
        }
    }.onFailure { t ->
        // android.util.Log isn't available in pure JVM unit tests — guard
        // so the Log call only fires on real Android (Robolectric/instrumented
        // tests). The error is still surfaced via the Result return value.
        runCatching { Log.w(TAG, "synthesize($provider) failed: ${t.javaClass.simpleName}: ${t.message}") }
    }

    private fun js(s: String): String {
        val sb = StringBuilder(s.length + 2)
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

    companion object {
        private const val TAG = "hermes-cloud-tts"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        // Short timeouts: a hung gateway should never block the user's
        // voice turn indefinitely. Cloud TTS is normally 1-2s, so 8s is
        // generous.
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build()
    }
}
