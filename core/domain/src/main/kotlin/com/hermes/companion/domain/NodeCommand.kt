package com.hermes.companion.domain

/**
 * A capability invocation delivered to the node over the broker. Every mutating
 * command carries [requestId], [grantId] and an [expiresAt]
 * (`plan/02-contracts/edge-contract.md`). [params] is a JSON object string so the
 * domain stays serialization-agnostic.
 */
data class NodeCommand(
    val requestId: String,
    val capability: String,
    val profile: String = "",
    val params: String = "{}",
    val grantId: String? = null,
    val expiresAt: Long? = null,
)

/**
 * The terminal (or progress) outcome of a command. Every gate — signature,
 * grant, lease, expiry, lock state — refuses with a named reason, and the
 * refusal is a receipt exactly like a success.
 */
data class Receipt(
    val requestId: String,
    val capability: String,
    val status: ReceiptStatus,
    val detail: String = "",
    val payload: String = "{}",
    val at: Long,
)

enum class ReceiptStatus { Accepted, Progress, Completed, Failed, Refused }
