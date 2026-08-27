package com.hermes.companion.broker

import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.ReceiptStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class WebSocketNodeBrokerTest {

    @Test
    fun `sends hello on open, delivers a deduped command, sends a receipt`() = runBlocking(Dispatchers.Default) {
        val server = MockWebServer()
        val serverWs = AtomicReference<WebSocket?>()
        val helloLatch = CountDownLatch(1)
        val receiptLatch = CountDownLatch(1)
        val gotReceipt = AtomicReference<String?>()

        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) { serverWs.set(ws) }
                override fun onMessage(ws: WebSocket, text: String) {
                    val f = Json { ignoreUnknownKeys = true }.decodeFromString(WireFrame.serializer(), text)
                    when (f.type) {
                        "hello" -> helloLatch.countDown()
                        "receipt" -> { gotReceipt.set(text); receiptLatch.countDown() }
                    }
                }
            }),
        )
        server.start()
        val url = server.url("/ws/node").toString().replace("http", "ws")

        val broker = WebSocketNodeBroker(
            url = url,
            token = "tok",
            hello = { BrokerHello("node1", listOf(WireCap("device.status", "Working"))) },
            client = OkHttpClient(),
        )
        val scope = CoroutineScope(Dispatchers.Default)
        broker.start(scope)

        assertTrue("hello not received", helloLatch.await(5, TimeUnit.SECONDS))

        val cmdDeferred = async { withTimeout(5_000) { broker.commands().first() } }
        delay(300) // let the collector subscribe

        // Send the same command twice: the second must be deduped by requestId.
        val frame = """{"v":1,"type":"command","requestId":"r1","capability":"device.status","params":{"x":1}}"""
        serverWs.get()!!.send(frame)
        serverWs.get()!!.send(frame)

        val cmd = cmdDeferred.await()
        assertEquals("r1", cmd.requestId)
        assertEquals("device.status", cmd.capability)
        assertTrue(cmd.params.contains("\"x\""))

        broker.sendReceipt(Receipt("r1", "device.status", ReceiptStatus.Completed, "ok", "{}", 1L))
        assertTrue("receipt not received", receiptLatch.await(5, TimeUnit.SECONDS))
        assertTrue(gotReceipt.get()!!.contains("\"status\":\"completed\""))

        serverWs.get()?.close(1000, "done")
        broker.stop()
        scope.cancel()
        delay(200)
        runCatching { server.shutdown() }
        Unit
    }
}
