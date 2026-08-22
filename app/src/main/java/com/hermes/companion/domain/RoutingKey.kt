package com.hermes.companion.domain

/**
 * Conversation routing key per §2 of the Hermes Companion plan.
 * Always carries `(gateway_id, profile_id, session_id)`. Node commands add
 * `(node_id, capability, request_id)` on top of this.
 */
data class ConversationRoute(
    val gatewayId: String,
    val profileId: String,
    val sessionId: String,
)

data class NodeRoute(
    val conversation: ConversationRoute,
    val nodeId: String,
    val capability: String,
    val requestId: String,
)

/**
 * Disambiguated profile handle — `@<profile>` for a single-gateway context,
 * `@<profile>-<gateway-suffix>` when the same profile name exists on multiple
 * gateways. Used purely for display; routing is still keyed on
 * (gateway_id, profile_id).
 */
data class ProfileHandle(
    val profileId: String,
    val display: String,
)
