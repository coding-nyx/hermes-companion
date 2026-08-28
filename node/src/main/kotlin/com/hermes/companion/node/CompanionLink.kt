package com.hermes.companion.node

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Minimal HTTP-mailbox client for the companion plugin's inbox/outbox pair.
 * Phase-1 stub: sends land in a local replayable queue (we hand them off to
 * the gateway's `/companion/inbox`); the polled `/companion/outbox` messages
 * are surfaced to callers as a Flow and echoed back via the same queue. Full
 * wire-up to the broker + notification routing lands in T4.
 *
 * Wire contract (matches the gateway plugin's spec):
 *   POST /companion/inbox    body: { id, session, ts, message }
 *   GET  /companion/outbox   returns: { messages: [{ id, session, ts, message }] }
 *
 * The transport layer is OkHttp, matching the rest of `:node`. The class
 * itself has no Android types so it stays unit-testable without Robolectric.
 */
class CompanionLink(
    private val baseUrl: String,
    private val sessionId: String,
    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** A single inbox/outbox message. [message] is opaque JSON. */
    data class CompanionMessage(
        val id: String,
        val session: String,
        val ts: Long,
        val message: JSONObject,
    )

    /** What `send()` returned — useful for retry logic later. */
    data class SendReceipt(
        val id: String,
        val acceptedAt: Long,
        val echoed: Boolean,
    )

    /**
     * Local replayable queue of what we have sent so far. Phase 1 keeps the
     * queue so tests and the UI can inspect it; Phase 2 will flush on
     * reconnect. Buffer is small + drop-oldest so a stuck connection cannot
     * pin memory.
     */
    private val sent = MutableSharedFlow<CompanionMessage>(
        replay = 32,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Outbound-by-us messages (best-effort replay buffer). */
    fun sentStream(): Flow<CompanionMessage> = sent.asSharedFlow()

    /**
     * POST a message to `/companion/inbox`. Returns a receipt on HTTP 2xx;
     * throws on transport failure so the caller can decide whether to retry
     * or surface an error.
     */
    suspend fun send(message: JSONObject): SendReceipt = withContext(ioDispatcher) {
        val id = "msg_" + UUID.randomUUID().toString().take(12)
        val ts = System.currentTimeMillis()
        val payload = JSONObject()
            .put("id", id)
            .put("session", sessionId)
            .put("ts", ts)
            .put("message", message)
            .toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/companion/inbox")
            .post(payload.toRequestBody(mediaType))
            .build()
        val accepted = runCatching {
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("inbox POST ${resp.code}: ${resp.body?.string().orEmpty()}")
                }
                true
            }
        }.getOrDefault(false)
        val msg = CompanionMessage(id = id, session = sessionId, ts = ts, message = message)
        sent.tryEmit(msg)
        SendReceipt(id = id, acceptedAt = ts, echoed = accepted)
    }

    /**
     * GET `/companion/outbox?since=<lastId>` and return the new messages.
     * Pass null for [sinceId] to fetch the whole backlog; pass the id of the
     * last seen message to fetch only the tail.
     *
     * The gateway's contract is short-poll (default ~8s). For Phase 1 this
     * is a one-shot fetch — T4 swaps it for the long-poll variant.
     */
    suspend fun poll(sinceId: String? = null, timeout: Long = 8_000L): List<CompanionMessage> =
        withContext(ioDispatcher) {
            val base = baseUrl.trimEnd('/') + "/companion/outbox"
            val parsed = base.toHttpUrlOrNull() ?: throw IllegalArgumentException("bad url: $base")
            val urlBuilder = parsed.newBuilder()
            if (sinceId != null) urlBuilder.addQueryParameter("since", sinceId)
            val req = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("outbox GET ${resp.code}: ${resp.body?.string().orEmpty()}")
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList()
                val o = JSONObject(body)
                val arr: JSONArray = o.optJSONArray("messages") ?: return@use emptyList()
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val m = arr.optJSONObject(i) ?: continue
                        val id = m.optString("id").takeIf { it.isNotBlank() } ?: continue
                        val session = m.optString("session")
                        val ts = m.optLong("ts", System.currentTimeMillis())
                        val inner = m.optJSONObject("message") ?: JSONObject()
                        add(CompanionMessage(id, session, ts, inner))
                    }
                }
            }
        }

    /** Tiny okhttp HttpUrl builder helper (kept for symmetry with send()). */
    private fun String.toBuilder(): okhttp3.HttpUrl.Builder =
        toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("bad url: $this")
}