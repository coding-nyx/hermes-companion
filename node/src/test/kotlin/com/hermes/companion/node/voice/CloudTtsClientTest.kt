package com.hermes.companion.node.voice

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 tests for [CloudTtsClient]. Uses an OkHttp interceptor to
 * capture the outgoing request body and inject a canned response — no
 * mockwebserver dep needed (same pattern as NotificationForwarderTest).
 */
class CloudTtsClientTest {

    private class CapturingInterceptor(private val onCaptured: (String) -> Unit) : Interceptor {
        var nextBody: ByteArray = ByteArray(0)
        var nextStatus: Int = 200
        var nextContentType: String = "audio/mpeg"
        var nextCacheUrl: String? = null
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            if (req.method == "POST") {
                val body = req.body?.let {
                    val sink = okio.Buffer()
                    it.writeTo(sink)
                    sink.readUtf8()
                } ?: ""
                onCaptured(body)
            }
            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(nextStatus)
                .message("OK")
                .body(nextBody.toResponseBody(nextContentType.toMediaType()))
                .apply { nextCacheUrl?.let { header("X-Cache-Url", it) } }
                .build()
        }
    }

    private fun recordingClient(
        onCaptured: (String) -> Unit,
        body: ByteArray = byteArrayOf(1, 2, 3, 4),
    ): Pair<OkHttpClient, CapturingInterceptor> {
        val rec = CapturingInterceptor(onCaptured).apply {
            this.nextBody = body
        }
        return OkHttpClient.Builder().addInterceptor(rec).build() to rec
    }

    @Test
    fun `synthesize posts the right provider and voice id to the gateway`() {
        var captured: String? = null
        val (client, _) = recordingClient({ captured = it })
        val c = CloudTtsClient(
            gatewayBaseUrl = "http://10.0.0.1:9120",
            nodeId = "node-test",
            provider = CloudTtsClient.Provider.Minimax,
            voiceId = "ash",
            client = client,
        )
        val result = c.synthesize("hello world")
        assertTrue("expected success, got $result", result.isSuccess)
        val audio = result.getOrNull() ?: error("no audio")
        assertEquals(4, audio.audioBytes.size)
        assertEquals("audio/mpeg", audio.contentType)
        val body: String = captured ?: error("no body captured")
        assertTrue("must POST (provider field in body)", body.contains("\"provider\":\"minimax\""))
        assertTrue("provider must be minimax", body.contains("\"provider\":\"minimax\""))
        assertTrue("voiceId must be ash", body.contains("\"voiceId\":\"ash\""))
        assertTrue("nodeId must be node-test", body.contains("\"nodeId\":\"node-test\""))
        assertTrue("text must be present", body.contains("hello world"))
    }

    @Test
    fun `synthesize returns audio bytes and cache url when gateway sets X-Cache-Url`() {
        var captured: String? = null
        val (client, rec) = recordingClient({ captured = it })
        rec.nextCacheUrl = "https://cdn.example/audio/abc123.mp3"
        val c = CloudTtsClient(
            gatewayBaseUrl = "http://10.0.0.1:9120",
            nodeId = "node-test",
            provider = CloudTtsClient.Provider.Grok,
            voiceId = "default",
            client = client,
        )
        val result = c.synthesize("yo")
        assertTrue(result.isSuccess)
        val audio = result.getOrNull() ?: error("no audio")
        assertNotNull(audio.cachedUrl)
        assertEquals("https://cdn.example/audio/abc123.mp3", audio.cachedUrl)
    }

    @Test
    fun `synthesize returns Result failure when gateway returns 502`() {
        var captured: String? = null
        val (client, rec) = recordingClient({ captured = it })
        rec.nextStatus = 502
        val c = CloudTtsClient(
            gatewayBaseUrl = "http://10.0.0.1:9120",
            nodeId = "node-test",
            provider = CloudTtsClient.Provider.ElevenLabs,
            voiceId = "default",
            client = client,
        )
        val r = c.synthesize("hi")
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("502"))
    }

    @Test
    fun `Provider parse handles lowercase wire names`() {
        assertEquals(CloudTtsClient.Provider.Minimax, CloudTtsClient.Provider.parse("MINIMAX"))
        assertEquals(CloudTtsClient.Provider.Grok, CloudTtsClient.Provider.parse("grok"))
        assertEquals(CloudTtsClient.Provider.ElevenLabs, CloudTtsClient.Provider.parse("elevenlabs"))
        assertEquals(CloudTtsClient.Provider.Unknown, CloudTtsClient.Provider.parse("android"))
        assertEquals(CloudTtsClient.Provider.Unknown, CloudTtsClient.Provider.parse(null))
    }
}
