package com.hermes.companion.net

import com.hermes.companion.backend.HermesBackend
import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ApprovalRequest
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.Message
import com.hermes.companion.domain.ProfileHandle
import com.hermes.companion.domain.RunEvent
import com.hermes.companion.domain.RunState
import com.hermes.companion.domain.Session
import com.hermes.companion.domain.ToolRun
import com.hermes.companion.domain.ToolStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.hermes.companion.transport.auth.RequestFactory
import com.hermes.companion.transport.auth.SignedRequestFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP-backed [HermesBackend]. Talks to the contract in
 * `plan/02-contracts/existing-api.md`; `mock-server/server.mjs` is the double.
 *
 * The base URL is the full gateway path including any `/gw-<id>` prefix.
 */
internal class HttpHermesBackend(
    override val gateway: GatewayConnection,
    private val client: OkHttpClient = defaultClient(),
    private val requests: SignedRequestFactory =
        RequestFactory.create(gateway.baseUrl, RequestFactory.credentialsFor(gateway.authRef)),
) : HermesBackend {

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // Finite, and safe because the server heartbeats. An infinite read
            // timeout turns a silently dead socket into a parked thread.
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ----- control plane -----

    override suspend fun capabilities(profile: String?): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val path = profile?.let { "/p/$it/v1/capabilities" } ?: "/v1/capabilities"
        val obj = getJson(path)["capabilities"]?.jsonObject ?: JsonObject(emptyMap())
        // Non-boolean entries are metadata (e.g. gateways.available is an array);
        // skip rather than throw, so one new field cannot break discovery.
        obj.entries.mapNotNull { (k, v) -> ((v as? JsonPrimitive)?.booleanOrNull)?.let { k to it } }.toMap()
    }

    override suspend fun listProfiles(): List<AgentProfile> = withContext(Dispatchers.IO) {
        val arr = getJson("/api/profiles")["profiles"]?.jsonArray.orEmpty()
        arr.map { el ->
            val obj = el.jsonObject
            val id = obj.str("profile_id") ?: error("profile without profile_id")
            AgentProfile(
                gatewayId = gateway.id,
                profileId = id,
                displayName = obj.str("display_name") ?: id.replaceFirstChar { it.uppercase() },
                handle = ProfileHandle(profileId = id, display = id),
                multiplexed = true,
            )
        }
    }

    override suspend fun listSessionsForProfile(gatewayId: String, profileId: String): List<Session> =
        withContext(Dispatchers.IO) {
            require(gatewayId == gateway.id) { "gatewayId mismatch" }
            val arr = getJson("/api/sessions")["sessions"]?.jsonArray.orEmpty()
            arr.map { it.jsonObject }
                .filter { it.str("profile") == profileId }
                .map { parseSession(it) }
        }

    override suspend fun createSession(route: ConversationRoute, title: String): Session =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("profile", JsonPrimitive(route.profileId))
                put("title", JsonPrimitive(title.ifBlank { "New chat" }))
            }.toString()
            val res = postJson("/api/sessions", body)
            val sessObj = res["session"]?.jsonObject ?: res
            parseSession(sessObj)
        }

    override suspend fun listMessages(route: ConversationRoute): List<Message> = withContext(Dispatchers.IO) {
        val arr = getJson("/api/sessions/${route.sessionId}/messages")["messages"]?.jsonArray.orEmpty()
        arr.map { parseMessage(it.jsonObject, route) }
    }

    override suspend fun stopRun(route: ConversationRoute, runId: String) = withContext(Dispatchers.IO) {
        post("/v1/runs/$runId/stop", "{}")
    }

    override suspend fun decideApproval(
        route: ConversationRoute,
        runId: String,
        requestId: String,
        option: ApprovalOption,
    ) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("request_id", JsonPrimitive(requestId))
            put("option", JsonPrimitive(option.name.lowercase()))
        }.toString()
        post("/v1/runs/$runId/approval", body)
    }

    // ----- streaming -----

    override suspend fun submit(route: ConversationRoute, text: String, idempotencyKey: String): String =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("session_id", JsonPrimitive(route.sessionId))
                put("profile", JsonPrimitive(route.profileId))
                put("text", JsonPrimitive(text))
                if (idempotencyKey.isNotBlank()) put("idempotency_key", JsonPrimitive(idempotencyKey))
            }.toString()
            postJson("/v1/runs", body).str("run_id") ?: throw IOException("run without run_id")
        }

    override fun runEvents(route: ConversationRoute, runId: String): Flow<RunEvent> =
        sse(requests.getEventStream("/v1/runs/$runId/events")).mapNotNull { toRunEvent(it) }

    private data class Frame(val event: String, val data: String)

    /**
     * One SSE subscription. Cancellation cancels the call, so leaving a screen
     * mid-run releases the socket instead of parking a thread on it, and
     * `trySendBlocking` applies real backpressure so no delta is dropped.
     */
    private fun sse(request: Request): Flow<Frame> = channelFlow {
        val call = client.newCall(request)
        launch(Dispatchers.IO) {
            try {
                call.execute().use { response: Response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} for ${request.url}")
                    }
                    val source = response.body?.source() ?: throw IOException("no body for ${request.url}")
                    SseParser().parse(source) { event, data ->
                        trySendBlocking(Frame(event, data)).isSuccess
                    }
                }
                close()
            } catch (c: CancellationException) {
                close()
            } catch (t: Throwable) {
                close(t)
            }
        }
        awaitClose { call.cancel() }
    }

    // ----- mapping -----

    private fun toRunEvent(frame: Frame): RunEvent? {
        val o = runCatching { json.parseToJsonElement(frame.data).jsonObject }.getOrNull() ?: return null
        val runId = o.str("run_id") ?: return null
        val sessionId = o.str("session_id") ?: return null
        return when (frame.event) {
            "tool.started" -> o["tool_run"]?.jsonObject?.let { RunEvent.ToolStarted(runId, sessionId, parseToolRun(it)) }
            "tool.completed" -> o["tool_run"]?.jsonObject?.let { RunEvent.ToolCompleted(runId, sessionId, parseToolRun(it)) }
            "assistant.delta" -> o.str("delta")?.let { RunEvent.AssistantDelta(runId, sessionId, it) }
            "run.approval_required" -> o["request"]?.jsonObject?.let {
                RunEvent.ApprovalRequired(runId, sessionId, parseApproval(it))
            }
            "run.completed" -> RunEvent.RunCompleted(runId, sessionId, o.str("final_text").orEmpty())
            "run.failed" -> RunEvent.RunFailed(runId, sessionId, o.str("reason") ?: "unknown")
            else -> null                                    // run.created and anything new
        }
    }

    private fun parseSession(obj: JsonObject) = Session(
        sessionId = obj.str("session_id") ?: error("session without session_id"),
        profileId = obj.str("profile") ?: error("session without profile"),
        gatewayId = gateway.id,
        title = obj.str("title") ?: "Untitled",
        modelLock = obj.str("model_lock"),
        runState = when (obj.str("run_state")) {
            "streaming" -> RunState.Streaming
            "awaiting_approval" -> RunState.AwaitingApproval
            "failed" -> RunState.Failed
            "completed" -> RunState.Completed
            else -> RunState.Idle
        },
        unreadCount = obj.str("unread_count")?.toIntOrNull() ?: 0,
    )

    private fun parseMessage(obj: JsonObject, route: ConversationRoute): Message {
        val id = obj.str("id") ?: java.util.UUID.randomUUID().toString()
        val createdAt = parseInstant(obj.str("created_at"))
        val text = obj.str("text").orEmpty()
        return if (obj.str("role") == "user") {
            Message.User(id, route.sessionId, route.profileId, route.gatewayId, createdAt, text)
        } else {
            Message.Assistant(id, route.sessionId, route.profileId, route.gatewayId, createdAt, text)
        }
    }

    private fun parseApproval(req: JsonObject) = ApprovalRequest(
        requestId = req.str("request_id") ?: error("approval without request_id"),
        runId = req.str("run_id").orEmpty(),
        profileId = req.str("profile").orEmpty(),
        gatewayId = req.str("gateway_id") ?: gateway.id,
        command = req.str("command").orEmpty(),
        digest = req.str("digest").orEmpty(),
    )

    private fun parseToolRun(o: JsonObject) = ToolRun(
        id = o.str("id") ?: o.str("tool_id") ?: "tool",
        name = o.str("name") ?: "tool",
        status = when (o.str("status")) {
            "running" -> ToolStatus.Running
            "completed" -> ToolStatus.Completed
            "failed" -> ToolStatus.Failed
            else -> ToolStatus.Pending
        },
        input = o.str("input").orEmpty(),
        output = o.str("output"),
        startedAt = parseInstant(o.str("started_at")),
        completedAt = o.str("completed_at")?.let { parseInstant(it) },
    )

    // ----- plumbing -----

    private fun getJson(path: String): JsonObject = readJson(requests.get(path), path)

    private fun postJson(path: String, body: String): JsonObject = readJson(requests.post(path, body), path)

    private fun post(path: String, body: String) {
        client.newCall(requests.post(path, body)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $path")
        }
    }

    private fun readJson(request: Request, path: String): JsonObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $path")
            if (body.isNullOrBlank()) throw IOException("empty body for $path")
            return json.parseToJsonElement(body).jsonObject
        }
    }

    private fun JsonObject.str(key: String): String? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content
    }

    private fun parseInstant(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching { java.time.Instant.parse(iso).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }
}
