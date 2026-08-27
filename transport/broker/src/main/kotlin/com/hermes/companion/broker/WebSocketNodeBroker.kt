package com.hermes.companion.broker

import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.NodeEventFrame
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.SendOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * A node broker over one OkHttp WebSocket. Reconnects with capped backoff,
 * resumes from the last acked sequence, dedupes inbound commands by requestId,
 * and keeps outbound sends honest (unacknowledged when the socket is down).
 */
class WebSocketNodeBroker(
    private val url: String,
    private val token: String,
    private val hello: () -> BrokerHello,
    private val client: OkHttpClient = OkHttpClient(),
) : NodeBroker {

    private val _connection = MutableStateFlow(BrokerConnectionState.Disconnected)
    override val connection: StateFlow<BrokerConnectionState> = _connection.asStateFlow()

    private val _commands = MutableSharedFlow<NodeCommand>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override fun commands(): Flow<NodeCommand> = _commands.asSharedFlow()

    private val seenRequests = ConcurrentHashMap.newKeySet<String>()
    private val lastAcked = AtomicLong(0)
    @Volatile private var socket: WebSocket? = null
    @Volatile private var scope: CoroutineScope? = null
    @Volatile private var running = false

    override fun start(scope: CoroutineScope) {
        this.scope = scope
        running = true
        scope.launch { connectLoop() }
    }

    override fun stop() {
        running = false
        runCatching { socket?.close(1000, "client stop") }
        socket = null
        _connection.value = BrokerConnectionState.Disconnected
    }

    private suspend fun connectLoop() {
        var failures = 0
        val s = scope ?: return
        while (running && s.isActive) {
            _connection.value = BrokerConnectionState.Connecting
            val opened = openOnce()
            if (opened) {
                failures = 0
                // Block until this socket dies; the listener flips state.
                while (running && s.isActive && _connection.value == BrokerConnectionState.Connected) {
                    kotlinx.coroutines.delay(500)
                }
            } else {
                failures++
            }
            if (!running) break
            val backoff = minOf(60_000L, 1_000L * (1L shl minOf(failures, 6)))
            kotlinx.coroutines.delay(backoff)
        }
    }

    private fun openOnce(): Boolean {
        val sep = if (url.contains("?")) "&" else "?"
        val request = Request.Builder()
            .url(url + sep + "token=" + token)
            .header("Authorization", "Bearer $token")
            .build()
        return runCatching {
            socket = client.newWebSocket(request, listener)
            true
        }.getOrDefault(false)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connection.value = BrokerConnectionState.Connected
            val h = hello()
            webSocket.send(
                BrokerJson.json.encodeToString(
                    WireFrame.serializer(),
                    WireFrame(type = "hello", nodeId = h.nodeId, caps = h.caps, sequence = lastAcked.get()),
                ),
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = runCatching { BrokerJson.json.decodeFromString(WireFrame.serializer(), text) }.getOrNull() ?: return
            when (frame.type) {
                "command" -> handleCommand(frame)
                "ack" -> frame.sequence?.let { lastAcked.set(maxOf(lastAcked.get(), it)) }
                "ping" -> webSocket.send("""{"v":1,"type":"pong"}""")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            _connection.value = BrokerConnectionState.Disconnected
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connection.value = BrokerConnectionState.Disconnected
        }
    }

    private fun handleCommand(frame: WireFrame) {
        val requestId = frame.requestId ?: return
        val capability = frame.capability ?: return
        if (!seenRequests.add(requestId)) return // dedupe by requestId
        _commands.tryEmit(
            NodeCommand(
                requestId = requestId,
                capability = capability,
                params = frame.params?.toString() ?: "{}",
                grantId = frame.grantId,
                expiresAt = frame.expiresAt,
            ),
        )
    }

    override suspend fun sendReceipt(receipt: Receipt) {
        val ws = socket ?: return
        ws.send(
            BrokerJson.json.encodeToString(
                WireFrame.serializer(),
                WireFrame(
                    type = "receipt",
                    requestId = receipt.requestId,
                    capability = receipt.capability,
                    status = receipt.status.name.lowercase(),
                    detail = receipt.detail,
                    payload = receipt.payload.toJsonObjectOrNull(),
                ),
            ),
        )
    }

    override suspend fun sendEvent(frame: NodeEventFrame): SendOutcome {
        val ws = socket ?: return SendOutcome.Unacknowledged
        if (_connection.value != BrokerConnectionState.Connected) return SendOutcome.Unacknowledged
        val sent = ws.send(
            BrokerJson.json.encodeToString(
                WireFrame.serializer(),
                WireFrame(
                    type = "node.event",
                    eventId = frame.eventId,
                    capability = frame.capability,
                    sequence = frame.sequence,
                    sentAt = frame.sentAt,
                    payload = frame.payload.toJsonObjectOrNull(),
                ),
            ),
        )
        return if (sent) SendOutcome.Acked else SendOutcome.Unacknowledged
    }
}

private fun String.toJsonObjectOrNull(): JsonObject? =
    runCatching { Json.parseToJsonElement(this) as? JsonObject }.getOrNull()
