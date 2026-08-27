package com.hermes.companion.data.repo

import com.hermes.companion.domain.ApprovalRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Serializable
internal data class ApprovalDto(
    val requestId: String,
    val runId: String,
    val profileId: String,
    val gatewayId: String,
    val command: String,
    val digest: String,
)

internal fun ApprovalRequest.encode(): String =
    json.encodeToString(
        ApprovalDto.serializer(),
        ApprovalDto(requestId, runId, profileId, gatewayId, command, digest),
    )

internal fun String?.decodeApproval(): ApprovalRequest? {
    if (this.isNullOrBlank()) return null
    return runCatching {
        json.decodeFromString(ApprovalDto.serializer(), this).let {
            ApprovalRequest(
                requestId = it.requestId,
                runId = it.runId,
                profileId = it.profileId,
                gatewayId = it.gatewayId,
                command = it.command,
                digest = it.digest,
            )
        }
    }.getOrNull()
}
