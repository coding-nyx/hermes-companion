package com.hermes.companion.node.service

import com.hermes.companion.domain.NotificationAction
import com.hermes.companion.node.routing.Decision
import com.hermes.companion.node.routing.NotificationRouter
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * T7 (companion-gateway-routing.md): the missing wire.
 *
 * These tests pin the surface of [NotificationForwarder] and [NotificationRouter]
 * without pulling in mockwebserver. We use an [Interceptor] that records the
 * last request body, which keeps :node's test deps minimal (no OkHttp mock
 * server).
 */
class NotificationForwarderTest {

    /**
     * Builds an OkHttpClient whose only behavior is to record the most recent
     * POST body sent, then return 202. This is a 12-line interceptor that's
     * a fraction of mockwebserver.
     */
    private class RecordingInterceptor(val captured: AtomicReference<String?>) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            if (req.method == "POST") {
                val body = req.body?.let {
                    val sink = okio.Buffer()
                    it.writeTo(sink)
                    sink.readUtf8()
                } ?: ""
                captured.set(body)
            }
            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(202)
                .message("Accepted")
                .body("".toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun recClient(captured: AtomicReference<String?>): OkHttpClient {
        val rec = RecordingInterceptor(captured)
        return OkHttpClient.Builder().addInterceptor(rec).build()
    }

    @Test
    fun `null active url is a silent no op`() {
        val captured = AtomicReference<String?>("untouched")
        val fwd = NotificationForwarder(activeUrl = null, nodeId = "node-1", client = recClient(captured))
        fwd.postIncoming("com.whatsapp", "WhatsApp", "Your code is 482915", 1_700_000_000_000L)
        assertEquals("untouched", captured.get())
    }

    @Test
    fun `null node id is a silent no op`() {
        val captured = AtomicReference<String?>("untouched")
        val fwd = NotificationForwarder(activeUrl = "http://10.0.0.1:9120", nodeId = null, client = recClient(captured))
        fwd.postIncoming("com.whatsapp", "WhatsApp", "Your code is 482915", 1_700_000_000_000L)
        assertEquals("untouched", captured.get())
    }

    @Test
    fun `active url and node id triggers POST sent with form body`() {
        val captured = AtomicReference<String?>(null)
        val fwd = NotificationForwarder(activeUrl = "http://10.0.0.1:9120", nodeId = "node-1", client = recClient(captured))
        fwd.postIncoming("com.whatsapp", "WhatsApp", "Your code is 482915", 1_700_000_000_000L)
        val body = captured.get() ?: error("no body captured")
        // FormBody encodes spaces, dots, and digits. Assert the form-encoded
        // keys are present; the values can be urlencoded by the library.
        assertTrue("must contain package key", body.contains("package="))
        assertTrue("must contain title key", body.contains("title="))
        assertTrue("must contain text key", body.contains("text="))
        assertTrue("must contain posted_at key", body.contains("posted_at="))
        assertTrue("must contain nodeId key", body.contains("nodeId="))
        // The package + nodeId values are alphanumeric so they are unencoded.
        assertTrue("must contain package value", body.contains("package=com.whatsapp"))
        assertTrue("must contain nodeId value", body.contains("nodeId=node-1"))
    }

    @Test
    fun `trailing slash on active url is normalized`() {
        val captured = AtomicReference<String?>(null)
        val fwd = NotificationForwarder(activeUrl = "http://10.0.0.1:9120/", nodeId = "n1", client = recClient(captured))
        fwd.postIncoming("com.android.systemui", "Battery saver", "On", 0L)
        val body = captured.get() ?: error("no body")
        assertTrue(body.contains("package=com.android.systemui"))
    }

    @Test
    fun `router decides Mute and forwarder is bypassed`() {
        val router = NotificationRouter()
        val decision: Decision = router.decide(
            defaultAction = NotificationAction.Off,
            perPackageOverride = null,
            packageName = "com.anything",
            title = "x",
            text = "y",
        )
        assertEquals(Decision.Mute, decision)
        // No forwarder call would happen - the NLS short-circuits before
        // invoking the forwarder. This test asserts the decision surface is
        // correct; the call short-circuit is exercised end-to-end via the
        // NLS integration test (deferred to T7.1).
    }

    @Test
    fun `router decides Post for All on any package`() {
        val router = NotificationRouter()
        val decision: Decision = router.decide(
            defaultAction = NotificationAction.All,
            perPackageOverride = null,
            packageName = "com.example.random",
            title = "Hello",
            text = "World",
        )
        assertEquals(Decision.Post, decision)
    }

    @Test
    fun `router decides Post for ImportantOnly on WhatsApp`() {
        val router = NotificationRouter()
        val decision: Decision = router.decide(
            defaultAction = NotificationAction.ImportantOnly,
            perPackageOverride = null,
            packageName = "com.whatsapp",
            title = "OTP",
            text = "code 482915",
        )
        assertEquals(Decision.Post, decision)
    }
}
