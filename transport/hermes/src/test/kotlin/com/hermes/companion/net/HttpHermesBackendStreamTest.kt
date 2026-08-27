package com.hermes.companion.net

import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayKind
import com.hermes.companion.domain.RunEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpHermesBackendStreamTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var backend: HttpHermesBackend
    private val route = ConversationRoute("gw-test", "ash", "sess-1")

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        backend = HttpHermesBackend(
            GatewayConnection(
                id = "gw-test",
                label = "Test",
                kind = GatewayKind.RemoteHttp,
                baseUrl = server.url("/gw-test").toString(),
                authRef = "none",
            ),
            client = client,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sse(body: String) = MockResponse()
        .setHeader("content-type", "text/event-stream")
        .setBody(body)

    private fun runId(id: String) = MockResponse()
        .setResponseCode(202)
        .setHeader("content-type", "application/json")
        .setBody("""{"run_id":"$id","session_id":"sess-1"}""")

    /** submit() then runEvents(), which is what the UI now does. */
    private suspend fun stream() = backend.runEvents(route, backend.submit(route, "hi"))

    @Test
    fun `maps a full run in order`() = runTest {
        server.enqueue(runId("r1"))
        server.enqueue(
            sse(
                buildString {
                    append(":ok\n\n")
                    append("event: run.created\ndata: {\"run_id\":\"r1\",\"session_id\":\"sess-1\"}\n\n")
                    append("event: tool.started\ndata: {\"run_id\":\"r1\",\"session_id\":\"sess-1\",\"tool_run\":{\"id\":\"t1\",\"name\":\"echo\",\"status\":\"running\",\"input\":\"hi\"}}\n\n")
                    append("event: tool.completed\ndata: {\"run_id\":\"r1\",\"session_id\":\"sess-1\",\"tool_run\":{\"id\":\"t1\",\"name\":\"echo\",\"status\":\"completed\",\"input\":\"hi\",\"output\":\"ok\"}}\n\n")
                    append("event: assistant.delta\ndata: {\"run_id\":\"r1\",\"session_id\":\"sess-1\",\"delta\":\"He\"}\n\n")
                    append("event: assistant.delta\ndata: {\"run_id\":\"r1\",\"session_id\":\"sess-1\",\"delta\":\"llo\"}\n\n")
                    append("event: run.completed\ndata: {\"run_id\":\"r1\",\"session_id\":\"sess-1\",\"final_text\":\"Hello\"}\n\n")
                }
            )
        )

        val events = stream().toList()

        // run.created is intentionally not surfaced as a RunEvent.
        assertEquals(
            listOf("ToolStarted", "ToolCompleted", "AssistantDelta", "AssistantDelta", "RunCompleted"),
            events.map { it::class.simpleName },
        )
        assertEquals("Hello", (events.last() as RunEvent.RunCompleted).finalText)
    }

    @Test
    fun `no delta is dropped when the collector is slow`() = runBlocking {
        // callbackFlow + an ignored trySend silently drops past its buffer;
        // this asserts every one of 200 deltas survives a slow collector.
        val n = 200
        server.enqueue(runId("r1"))
        server.enqueue(
            sse(
                buildString {
                    repeat(n) { i ->
                        append("event: assistant.delta\ndata: {\"run_id\":\"r1\",\"session_id\":\"sess-1\",\"delta\":\"$i,\"}\n\n")
                    }
                    append("event: run.completed\ndata: {\"run_id\":\"r1\",\"session_id\":\"sess-1\",\"final_text\":\"done\"}\n\n")
                }
            )
        )

        val deltas = mutableListOf<String>()
        stream().toList().forEach {
            if (it is RunEvent.AssistantDelta) {
                deltas += it.delta
                delay(1)
            }
        }

        assertEquals(n, deltas.size)
        assertEquals((0 until n).map { "$it," }, deltas)
        Unit
    }

    @Test
    fun `cancelling the collector releases the call`() = runBlocking {
        // A long, throttled stream stands in for a run still in progress: the
        // old implementation parked an OkHttp thread on it forever, because
        // cancellation never reached the call.
        server.enqueue(runId("r1"))
        server.enqueue(
            sse(
                buildString {
                    repeat(400) {
                        append("event: assistant.delta\ndata: {\"run_id\":\"r1\",\"session_id\":\"sess-1\",\"delta\":\"x\"}\n\n")
                    }
                }
            ).throttleBody(256, 120, TimeUnit.MILLISECONDS)
        )

        val got = withTimeout(5_000) {
            stream().take(2).toList()
        }
        assertEquals(2, got.size)

        // The call must be gone, not merely unobserved.
        var running = client.dispatcher.runningCallsCount()
        val deadline = System.currentTimeMillis() + 3_000
        while (running > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
            running = client.dispatcher.runningCallsCount()
        }
        assertEquals("cancellation must cancel the HTTP call", 0, running)
        assertTrue(client.connectionPool.connectionCount() <= 1)
        Unit
    }
}
