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

/**
 * The durable outbound journal (`plan/05-reliability/offline-behavior.md`). A
 * submission is written here BEFORE the network, replayed under its unique
 * [idempotencyKey], and only ever reaches a terminal state on a real answer —
 * an unanswered send becomes `unacknowledged`, never `sent`.
 */
@Entity(
    tableName = "outbound",
    indices = [Index(value = ["idempotencyKey"], unique = true)],
)
data class OutboundEntity(
    @PrimaryKey val id: String,
    val gatewayId: String,
    val profileId: String,
    val sessionId: String,
    val text: String,
    val idempotencyKey: String,
    val createdAt: Long,
    val attempts: Int,
    val state: String,
    val runId: String?,
    val expiresAt: Long?,
    val attachmentBytes: Long,
    val lastError: String?,
)

/**
 * A paired node's identity + broker credential, per gateway. Kept OUT of
 * GatewayEntity/GatewayConnection so the token never reaches a UI-facing type
 * (the compile-time boundary). Phase 10 seals [token] in the Keystore; today it
 * is a plaintext column that only the data-layer node code reads.
 */
@Entity(tableName = "node_identity")
data class NodeIdentityEntity(
    @PrimaryKey val gatewayId: String,
    val nodeId: String,
    val brokerUrl: String,
    val sealedToken: String,
    val expiresAt: Long,
    val grantedCapsCsv: String,
    val pairedAt: Long,
)

/** Capability grants, scoped to (gateway, profile, node, capability) — never global. */
@Entity(tableName = "grants", primaryKeys = ["gatewayId", "profileId", "nodeId", "capability"])
data class GrantEntity(
    val gatewayId: String,
    val profileId: String,
    val nodeId: String,
    val capability: String,
    val mode: String,
    val expiry: Long?,
    val policy: String?,
    val updatedAt: Long,
)

/**
 * Exclusive-capability leases. The PRIMARY KEY is the capability, so mutual
 * exclusion is a uniqueness constraint rather than a lock
 * (`plan/10-architecture/capabilities.md`).
 */
@Entity(tableName = "leases")
data class LeaseEntity(
    @PrimaryKey val capability: String,
    val gatewayId: String,
    val profileId: String,
    val requestId: String,
    val acquiredAt: Long,
    val expiresAt: Long,
)

/** Per-source streaming rule: how much of a source's events leaves the device. */
@Entity(tableName = "stream_rules")
data class StreamRuleEntity(
    @PrimaryKey val source: String,
    val mode: String,
    val updatedAt: Long,
)

/**
 * T3A: Active-gateway selection (singleton).
 *
 * Exactly one row, PK = 1. The row holds the currently-active gatewayId and
 * the timestamp of the last set. No row means no active selection; callers
 * fall back to "first gateway" or "ask the user".
 *
 * Why a singleton table and not a column on `gateways`: a column forces every
 * gateway row to track "am I the active one?" separately, and updates must
 * clear all other rows' flags in a transaction. The singleton form keeps the
 * selection logic in one place and the gateway table stays pure identity.
 */
@Entity(tableName = "active_gateway")
data class ActiveGatewayEntity(
    @PrimaryKey val id: Int = 1,
    val gatewayId: String,
    /**
     * Snapshot of the gateway URL at the moment it was made active; lets
     * background services like the NotificationListenerService POST to the
     * gateway without joining through [gateways]. Kept inline so a corrupted
     * gateways row can't silently fail inbound notification forwarding.
     */
    val url: String,
    /**
     * The node_id this device registered as when pairing. The gateway's
     * /v1/notifications endpoints accept a `nodeId` field; the NLS uses this
     * so the gateway can forward events to the correct WS connection without
     * re-deriving it on every notification.
     */
    val nodeId: String,
    val updatedAt: Long,
)
