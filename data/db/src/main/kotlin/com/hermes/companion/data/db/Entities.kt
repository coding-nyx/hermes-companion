package com.hermes.companion.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Composite keys carry the route wherever the route *is* the identity, per
 * `plan/10-architecture/data.md`. Nothing here is keyed by session id alone:
 * that is the class of bug that made every created session unreachable.
 */

@Entity(tableName = "gateways")
data class GatewayEntity(
    @PrimaryKey val id: String,
    val label: String,
    val kind: String,
    val url: String,
    val authRef: String,
    val health: String,
    val lastOkAt: Long?,
    val staleSince: Long?,
    /** Last failure reason, rendered as-is. Null when the gateway is healthy. */
    val error: String?,
)

@Entity(tableName = "profiles", primaryKeys = ["gatewayId", "profileId"])
data class ProfileEntity(
    val gatewayId: String,
    val profileId: String,
    val displayName: String,
    val handleDisplay: String,
    val multiplexed: Boolean,
)

@Entity(tableName = "sessions", primaryKeys = ["gatewayId", "profileId", "sessionId"])
data class SessionEntity(
    val gatewayId: String,
    val profileId: String,
    val sessionId: String,
    val title: String,
    val modelLock: String?,
    val runState: String,
    val unreadCount: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    indices = [Index("gatewayId", "profileId", "sessionId", "createdAt")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val gatewayId: String,
    val profileId: String,
    val sessionId: String,
    val role: String,
    val text: String,
    val toolRunsJson: String,
    val createdAt: Long,
    /** The run this message belongs to, when it came from one. */
    val runId: String?,
    /** True while the run is still writing into this row. */
    val streaming: Boolean,
    /** True for a locally-written message the gateway has not confirmed. */
    val pending: Boolean,
)

@Entity(tableName = "runs", primaryKeys = ["gatewayId", "profileId", "sessionId", "runId"])
data class RunEntity(
    val gatewayId: String,
    val profileId: String,
    val sessionId: String,
    val runId: String,
    val state: String,
    val cursor: String?,
    val error: String?,
    /** The approval this run is blocked on, encoded. Null unless awaiting. */
    val approvalJson: String?,
    val updatedAt: Long,
)
