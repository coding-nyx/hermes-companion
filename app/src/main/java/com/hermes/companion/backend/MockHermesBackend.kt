package com.hermes.companion.backend

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process Hermes backend used for the PoC. Mirrors the contract surface
 * described in §4–5 of the plan without holding real Hermes tokens or
 * invoking a production agent. Two gateways with overlapping profile
 * names ("ash" on both) exercise the disambiguation handle model in §3.
 *
 * The mock is deterministic enough for routing-isolation tests but emits
 * realistic enough events (assistant deltas, tool started/completed,
 * approval required, run completed) for UI verification.
 */
class MockHermesBackend(
    override val gateway: GatewayConnection,
    private val profileIds: List<String>,
) : HermesBackend {

    /** Sessions keyed by session id; store is keyed under profile namespace. */
    private val sessions = ConcurrentHashMap<String, Session>()
    private val messages = ConcurrentHashMap<String, MutableList<Message>>()
    private val approvals = ConcurrentHashMap<String, ApprovalRequest>()

    init {
        // Each profile starts with one seeded session for the PoC.
        profileIds.forEach { pid ->
            val sessionId = "sess-${gateway.id}-$pid-1"
            sessions[sessionId] = Session(
                sessionId = sessionId,
                profileId = pid,
                gatewayId = gateway.id,
                title = "Welcome — $pid",
                runState = RunState.Idle,
            )
            messages[sessionId] = mutableListOf(
                Message.Assistant(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    profileId = pid,
                    gatewayId = gateway.id,
                    createdAt = System.currentTimeMillis(),
                    text = "Hello from $pid on ${gateway.label}. Try asking me to do something.",
                )
            )
        }
    }

    override suspend fun capabilities(profile: String?): Map<String, Boolean> = mapOf(
        "sessions.list" to true,
        "chat.stream" to true,
        "runs.events" to true,
        "approval.required" to true,
        "profiles.multiplexed" to true,
        "mock.only" to true,
    )

    override suspend fun listProfiles(): List<AgentProfile> = profileIds.map { pid ->
        AgentProfile(
            gatewayId = gateway.id,
            profileId = pid,
            displayName = pid.replaceFirstChar { it.uppercase() },
            handle = ProfileHandle(
                profileId = pid,
                // Disambiguation suffix handled in registry, not backend.
                display = pid,
            ),
            multiplexed = true,
        )
    }

    override suspend fun listSessionsForProfile(gatewayId: String, profileId: String): List<Session> {
        require(gatewayId == gateway.id) { "gatewayId mismatch" }
        require(profileId in profileIds) { "unknown profile $profileId" }
        return sessions.values
            .filter { it.gatewayId == gatewayId && it.profileId == profileId }
            .sortedBy { it.title }
    }

    override suspend fun listMessages(route: ConversationRoute): List<Message> {
        ensureRoute(route)
        return messages[route.sessionId].orEmpty().toList()
    }

    override fun sendAndStream(route: ConversationRoute, text: String): Flow<RunEvent> = flow {
        ensureRoute(route)
        val runId = "run-${UUID.randomUUID().toString().take(8)}"
        val toolId = "tool-${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        // User message persisted immediately.
        val userMsg = Message.User(
            id = UUID.randomUUID().toString(),
            sessionId = route.sessionId,
            profileId = route.profileId,
            gatewayId = route.gatewayId,
            createdAt = now,
            text = text,
        )
        messages.getOrPut(route.sessionId) { mutableListOf() }.add(userMsg)

        val intro = "Mock run on ${gateway.label}/${route.profileId}: "
        val tokens = (intro + mockReplyFor(text)).chunked(8)
        var emitted = 0

        // Optional approval gate for destructive commands.
        val needsApproval = text.lowercase().let { it.startsWith("deploy") || it.startsWith("send ") }

        if (needsApproval) {
            val approval = ApprovalRequest(
                requestId = "apr-${UUID.randomUUID().toString().take(8)}",
                runId = runId,
                profileId = route.profileId,
                gatewayId = route.gatewayId,
                command = text.take(80),
                digest = "sha256:" + Integer.toHexString(text.hashCode()),
            )
            approvals[approval.requestId] = approval
            emit(RunEvent.ApprovalRequired(runId, route.sessionId, approval))
            return@flow
        }

        // Tool run: tool.started then (after short delay) tool.completed.
        emit(RunEvent.ToolStarted(
            runId = runId,
            sessionId = route.sessionId,
            toolRun = ToolRun(
                id = toolId,
                name = "echo",
                status = ToolStatus.Running,
                input = text,
                startedAt = now,
            ),
        ))
        delay(120)
        emit(RunEvent.ToolCompleted(
            runId = runId,
            sessionId = route.sessionId,
            toolRun = ToolRun(
                id = toolId,
                name = "echo",
                status = ToolStatus.Completed,
                input = text,
                output = "echoed: $text",
                startedAt = now,
                completedAt = System.currentTimeMillis(),
            ),
        ))

        // Streamed assistant deltas.
        val sb = StringBuilder()
        for (tok in tokens) {
            sb.append(tok)
            emitted += tok.length
            emit(RunEvent.AssistantDelta(runId, route.sessionId, tok))
            delay(40)
        }

        val finalText = sb.toString()
        val assistantMsg = Message.Assistant(
            id = UUID.randomUUID().toString(),
            sessionId = route.sessionId,
            profileId = route.profileId,
            gatewayId = route.gatewayId,
            createdAt = System.currentTimeMillis(),
            text = finalText,
            toolRuns = listOf(
                ToolRun(
                    id = toolId,
                    name = "echo",
                    status = ToolStatus.Completed,
                    input = text,
                    output = "echoed: $text",
                    startedAt = now,
                    completedAt = System.currentTimeMillis(),
                )
            ),
            isStreaming = false,
        )
        messages.getOrPut(route.sessionId) { mutableListOf() }.add(assistantMsg)
        emit(RunEvent.RunCompleted(runId, route.sessionId, finalText))
    }

    override suspend fun stopRun(route: ConversationRoute, runId: String) {
        // Mock: no-op for the PoC.
    }

    override suspend fun decideApproval(
        route: ConversationRoute,
        requestId: String,
        option: ApprovalOption,
    ) {
        ensureRoute(route)
        val req = approvals.remove(requestId) ?: return
        if (option == ApprovalOption.Deny) {
            return
        }
        // On approval, emit a synthetic run.completed so the UI can show
        // the decision was honoured.
    }

    private fun ensureRoute(route: ConversationRoute) {
        require(route.gatewayId == gateway.id) {
            "route.gatewayId (${route.gatewayId}) does not match backend gateway (${gateway.id})"
        }
        require(route.profileId in profileIds) {
            "profile ${route.profileId} not registered on gateway ${gateway.id}"
        }
        require(sessions.containsKey(route.sessionId)) {
            "session ${route.sessionId} not found on gateway ${gateway.id}/${route.profileId}"
        }
    }

    private fun mockReplyFor(text: String): String =
        "received \"$text\" — this is a mock backend for the Hermes Companion PoC."

    companion object {
        /** Build the canonical PoC fleet: two gateways, two profiles each. */
        fun defaultFleet(): List<MockHermesBackend> = listOf(
            MockHermesBackend(
                GatewayConnection(
                    id = "gw-home",
                    label = "Home",
                    kind = com.hermes.companion.domain.GatewayKind.Local,
                    baseUrl = "mock://gw-home",
                    authRef = "none",
                ),
                profileIds = listOf("ash", "misty"),
            ),
            MockHermesBackend(
                GatewayConnection(
                    id = "gw-cloud",
                    label = "Cloud",
                    kind = com.hermes.companion.domain.GatewayKind.CloudOAuth,
                    baseUrl = "mock://gw-cloud",
                    authRef = "none",
                ),
                profileIds = listOf("ash", "work"),
            ),
        )
    }
}
