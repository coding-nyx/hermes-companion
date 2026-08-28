package com.hermes.companion.node

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Phase-1 stub tests for [CompanionLink]. We pin the wire shape (request
 * paths, JSON envelope, 4xx-on-failure semantics) without depending on
 * a real gateway. T4 will replace the test surface with full happy-path
 *   + reconnect coverage.
 */
@RunWith(RobolectricTestRunner::class)
class CompanionLinkTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `send POSTs to inbox with id session ts and message envelope`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val link = CompanionLink(
            baseUrl = server.url("/").toString().trimEnd('/'),
            sessionId = "sess-test-1234",
        )
        val payload = JSONObject().put("text", "hi")
        val receipt = link.send(payload)

        val req: RecordedRequest = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/companion/inbox", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals(receipt.id, body.getString("id"))
        assertEquals("sess-test-1234", body.getString("session"))
        assertTrue("ts is positive", body.getLong("ts") > 0)
        assertEquals("hi", body.getJSONObject("message").getString("text"))
        assertTrue("echoed=true on 2xx", receipt.echoed)
        // acceptedAt tracks the moment we posted; same scale as ts.
        assertTrue("acceptedAt > 0", receipt.acceptedAt > 0)
    }

    @Test
    fun `send throws on non-2xx so the caller can decide to retry`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("overloaded"))

        val link = CompanionLink(
            baseUrl = server.url("/").toString().trimEnd('/'),
            sessionId = "sess-test-1234",
        )
        try {
            link.send(JSONObject().put("text", "hi"))
            fail("expected send to throw on 503")
        } catch (t: Throwable) {
            assertTrue("error mentions status, got: ${t.message}", (t.message ?: "").contains("503"))
        }
    }

    @Test
    fun `poll parses messages out of the outbox JSON envelope`() = runBlocking {
        val body = JSONObject()
            .put("messages", org.json.JSONArray().apply {
                put(JSONObject()
                    .put("id", "msg-1")
                    .put("session", "sess-test-1234")
                    .put("ts", 1_700_000_000L)
                    .put("message", JSONObject().put("text", "hello"))
                )
                put(JSONObject()
                    .put("id", "msg-2")
                    .put("session", "sess-test-1234")
                    .put("ts", 1_700_000_001L)
                    .put("message", JSONObject().put("text", "world"))
                )
            })
            .toString()
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val link = CompanionLink(
            baseUrl = server.url("/").toString().trimEnd('/'),
            sessionId = "sess-test-1234",
        )
        val msgs = link.poll()

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/companion/outbox", req.path)
        assertEquals(2, msgs.size)
        assertEquals("msg-1", msgs[0].id)
        assertEquals("hello", msgs[0].message.getString("text"))
        assertEquals("msg-2", msgs[1].id)
        assertEquals("world", msgs[1].message.getString("text"))
    }

    @Test
    fun `poll appends since query param when set`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"messages":[]}""").setResponseCode(200))

        val link = CompanionLink(
            baseUrl = server.url("/").toString().trimEnd('/'),
            sessionId = "sess-test-1234",
        )
        link.poll(sinceId = "msg-prev-7")
        val req = server.takeRequest()
        assertTrue(
            "path includes since=msg-prev-7, got: ${req.path}",
            req.path?.startsWith("/companion/outbox?since=msg-prev-7") == true,
        )
    }

    @Test
    fun `poll throws on non-2xx`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val link = CompanionLink(
            baseUrl = server.url("/").toString().trimEnd('/'),
            sessionId = "sess-test-1234",
        )
        try {
            link.poll()
            fail("expected poll to throw on 500")
        } catch (t: Throwable) {
            assertTrue("error mentions status, got: ${t.message}", (t.message ?: "").contains("500"))
        }
    }

    @Test
    fun `poll returns empty list when body is empty or messages missing`() = runBlocking {
        server.enqueue(MockResponse().setBody("").setResponseCode(200))

        val link = CompanionLink(
            baseUrl = server.url("/").toString().trimEnd('/'),
            sessionId = "sess-test-1234",
        )
        assertEquals(emptyList<CompanionLink.CompanionMessage>(), link.poll())

        // body present but no messages key
        server.enqueue(MockResponse().setBody("{}").setResponseCode(200))
        assertEquals(emptyList<CompanionLink.CompanionMessage>(), link.poll())
    }

    @Test
    fun `sentStream replays what send just sent`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val link = CompanionLink(
            baseUrl = server.url("/").toString().trimEnd('/'),
            sessionId = "sess-test-1234",
        )
        val receipt = link.send(JSONObject().put("text", "hi"))

        // SharedFlow.replay = 32 means a new collector immediately sees the
        // just-emitted value. We read exactly one element so the test is
        // fast and deterministic.
        val seen = withTimeout(1_000) {
            link.sentStream().first { true }
        }
        assertEquals(receipt.id, seen.id)
        assertEquals("sess-test-1234", seen.session)
        assertEquals("hi", seen.message.getString("text"))
    }
}