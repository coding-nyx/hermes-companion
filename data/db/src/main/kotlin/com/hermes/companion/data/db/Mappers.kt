package com.hermes.companion.data.db

import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayHealth
import com.hermes.companion.domain.GatewayKind
import com.hermes.companion.domain.Message
import com.hermes.companion.domain.ProfileHandle
import com.hermes.companion.domain.RunState
import com.hermes.companion.domain.Session
import com.hermes.companion.domain.ToolRun
import com.hermes.companion.domain.ToolStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Serializable
internal data class ToolRunDto(
    val id: String,
    val name: String,
    val status: String,
    val input: String,
    val output: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
)

fun List<ToolRun>.encode(): String =
    json.encodeToString(
        ListSerializer(ToolRunDto.serializer()),
        map { ToolRunDto(it.id, it.name, it.status.name, it.input, it.output, it.startedAt, it.completedAt) },
    )

fun String.decodeToolRuns(): List<ToolRun> {
    if (isBlank()) return emptyList()
    return runCatching {
        json.decodeFromString(ListSerializer(ToolRunDto.serializer()), this).map {
            ToolRun(
                id = it.id,
                name = it.name,
                status = runCatching { ToolStatus.valueOf(it.status) }.getOrDefault(ToolStatus.Pending),
                input = it.input,
                output = it.output,
                startedAt = it.startedAt,
                completedAt = it.completedAt,
            )
        }
    }.getOrDefault(emptyList())
}

// ----- gateway -----

fun GatewayConnection.toEntity(
    health: GatewayHealth = this.health,
    lastOkAt: Long? = null,
    staleSince: Long? = null,
    error: String? = null,
) = GatewayEntity(
    id = id,
    label = label,
    kind = kind.name,
    url = baseUrl,
    authRef = authRef,
    health = health.name,
    lastOkAt = lastOkAt,
    staleSince = staleSince,
    error = error,
)

fun GatewayEntity.toDomain() = GatewayConnection(
    id = id,
    label = label,
    kind = runCatching { GatewayKind.valueOf(kind) }.getOrDefault(GatewayKind.RemoteHttp),
    baseUrl = url,
    authRef = authRef,
    health = runCatching { GatewayHealth.valueOf(health) }.getOrDefault(GatewayHealth.Unknown),
)

// ----- profile -----

fun AgentProfile.toEntity() = ProfileEntity(
    gatewayId = gatewayId,
    profileId = profileId,
    displayName = displayName,
    handleDisplay = handle.display,
    multiplexed = multiplexed,
)

fun ProfileEntity.toDomain() = AgentProfile(
    gatewayId = gatewayId,
    profileId = profileId,
    displayName = displayName,
    handle = ProfileHandle(profileId = profileId, display = handleDisplay),
    multiplexed = multiplexed,
)

// ----- session -----

fun Session.toEntity(updatedAt: Long) = SessionEntity(
    gatewayId = gatewayId,
    profileId = profileId,
    sessionId = sessionId,
    title = title,
    modelLock = modelLock,
    runState = runState.name,
    unreadCount = unreadCount,
    updatedAt = updatedAt,
)

fun SessionEntity.toDomain() = Session(
    sessionId = sessionId,
    profileId = profileId,
    gatewayId = gatewayId,
    title = title,
    modelLock = modelLock,
    runState = runCatching { RunState.valueOf(runState) }.getOrDefault(RunState.Idle),
    unreadCount = unreadCount,
)

// ----- message -----

fun Message.toEntity(runId: String? = null, streaming: Boolean = false, pending: Boolean = false) =
    when (this) {
        is Message.User -> MessageEntity(
            id, gatewayId, profileId, sessionId, "user", text, "", createdAt, runId, streaming, pending,
        )
        is Message.Assistant -> MessageEntity(
            id, gatewayId, profileId, sessionId, "assistant", text, toolRuns.encode(), createdAt,
            runId, streaming || isStreaming, pending,
        )
    }

fun MessageEntity.toDomain(): Message =
    if (role == "user") {
        Message.User(id, sessionId, profileId, gatewayId, createdAt, text)
    } else {
        Message.Assistant(
            id = id,
            sessionId = sessionId,
            profileId = profileId,
            gatewayId = gatewayId,
            createdAt = createdAt,
            text = text,
            toolRuns = toolRunsJson.decodeToolRuns(),
            isStreaming = streaming,
        )
    }
