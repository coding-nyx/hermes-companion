package com.hermes.companion.data.repo

import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.ApprovalRequest
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.Message
import com.hermes.companion.domain.Session

/**
 * A gateway's reachability is data, not an exception. Every observed state
 * carries its own, per route — see `plan/10-architecture/state.md`.
 */
sealed interface Connectivity {
    data object Unknown : Connectivity
    data object Live : Connectivity
    data class Degraded(val since: Long, val reason: String) : Connectivity
    data class Down(val reason: String) : Connectivity

    val reasonOrNull: String?
        get() = when (this) {
            is Degraded -> reason
            is Down -> reason
            else -> null
        }
}

data class ProfileView(
    val profile: AgentProfile,
    val sessions: List<Session>,
)

data class GatewayView(
    val gateway: GatewayConnection,
    val connectivity: Connectivity,
    val profiles: List<ProfileView>,
)

data class Fleet(
    val gateways: List<GatewayView> = emptyList(),
    val loading: Boolean = true,
) {
    val profiles: List<AgentProfile> get() = gateways.flatMap { g -> g.profiles.map { it.profile } }
    val failures: Map<String, String> get() = gateways.mapNotNull { g ->
        g.connectivity.reasonOrNull?.let { g.gateway.id to it }
    }.toMap()
}

/** What a run is doing right now, as stored. */
data class RunView(
    val runId: String,
    val state: RunPhase,
    val error: String? = null,
)

enum class RunPhase { Streaming, AwaitingApproval, Completed, Failed;
    companion object {
        fun parse(raw: String): RunPhase = entries.firstOrNull { it.stored == raw } ?: Failed // unknown state is never treated as success
    }
    val stored: String get() = when (this) {
        Streaming -> "streaming"
        AwaitingApproval -> "awaiting_approval"
        Completed -> "completed"
        Failed -> "failed"
    }
}

data class ConversationState(
    val route: ConversationRoute? = null,
    val gatewayLabel: String = "",
    val messages: List<Message> = emptyList(),
    val activeRun: RunView? = null,
    val pendingApproval: ApprovalRequest? = null,
    val connectivity: Connectivity = Connectivity.Unknown,
) {
    val streaming: Boolean get() = activeRun?.state == RunPhase.Streaming
}

enum class ActivityKind { Notification, Call, Job, ChatRun }

enum class ActivityOutcome { Notified, Suppressed, Completed, AwaitingApproval, Failed, Streaming }

data class ActivityItem(
    val id: String,
    val kind: ActivityKind,
    val glyph: String,
    val title: String,
    val subtitle: String,
    val routeDisplay: String,
    val createdAt: Long,
    val outcome: ActivityOutcome,
    val stage: Int, // 1..5
    val detailTitle: String,
    val detailBody: String,
    val detailMeta: String,
)

data class QueueSummary(
    val gatewayId: String,
    val detail: String,
    val isLive: Boolean,
)

data class ActivityState(
    val items: List<ActivityItem> = emptyList(),
    val queues: List<QueueSummary> = emptyList(),
    val selectedFilter: ActivityKind? = null,
)

enum class CapabilityStatus { Working, MissingPermission, OsLimited, Unavailable }

data class NodeCapabilityItem(
    val id: String,
    val name: String,
    val status: CapabilityStatus,
    val stateLabel: String,
    val description: String,
)

data class HardwareLease(
    val capability: String,
    val holder: String,
    val isAvailable: Boolean,
)

data class PrivacyLogEntry(
    val time: String,
    val text: String,
)

data class NodeState(
    val nodeName: String = "Galaxy S22",
    val nodeId: String = "node_s22",
    val sequence: Long = 8842L,
    val brokerStatus: String = "Broker connected",
    val batteryMode: String = "unrestricted",
    val linkType: String = "wss tailnet",
    val capabilities: List<NodeCapabilityItem> = emptyList(),
    val leases: List<HardwareLease> = emptyList(),
    val privacyLog: List<PrivacyLogEntry> = emptyList(),
    val canaryRunning: Boolean = false,
    val canaryPassed: Boolean = false,
    val canarySteps: List<String> = emptyList(),
)

data class OutboxItem(
    val id: String,
    val route: ConversationRoute,
    val routeDisplay: String,
    val text: String,
    val createdAt: Long,
    val state: String,
    val needsDecision: Boolean,
)

data class OutboxState(
    val items: List<OutboxItem> = emptyList(),
    val messagesHeld: Int = 0,
    val maxMessages: Int = 50,
)


