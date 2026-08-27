package com.hermes.companion.domain

/**
 * Versioned, idempotent broker frames (`plan/02-contracts/edge-contract.md`).
 * Deduped by (nodeId, eventId) and (nodeId, requestId) on both sides.
 */
sealed interface BrokerFrame {
    val v: Int
}

/** Phone → gateway: a captured device event. */
data class NodeEventFrame(
    override val v: Int = 1,
    val eventId: String,
    val gatewayId: String,
    val profile: String,
    val nodeId: String,
    val sequence: Long,
    val sentAt: String,
    val capability: String,
    val payload: String,
) : BrokerFrame

/** Gateway → phone: invoke a capability. */
data class CommandFrame(
    override val v: Int = 1,
    val gatewayId: String,
    val profile: String,
    val nodeId: String,
    val command: NodeCommand,
) : BrokerFrame

/** Phone → gateway: command lifecycle (accepted → progress* → completed|failed). */
data class CommandReceiptFrame(
    override val v: Int = 1,
    val nodeId: String,
    val receipt: Receipt,
) : BrokerFrame

/** Phone → gateway: liveness beacon emitted on backgrounding. */
data class PresenceFrame(
    override val v: Int = 1,
    val nodeId: String,
    val alive: Boolean,
    val sentAt: String,
) : BrokerFrame

/** Outcome of trying to send a node event. */
enum class SendOutcome { Acked, Unacknowledged, Refused }
