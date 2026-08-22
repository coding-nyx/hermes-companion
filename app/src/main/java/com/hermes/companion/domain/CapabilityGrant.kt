package com.hermes.companion.domain

/**
 * Per §5.3: grants are scoped to (gateway, profile, node, capability), not
 * global. Mode/expiry/policy live here so the Android app can decide
 * whether to surface, prompt, or reject a capability request before it
 * reaches Hermes' approval policy.
 */
data class CapabilityGrant(
    val gatewayId: String,
    val profileId: String,
    val nodeId: String,
    val capability: NodeCapability,
    val mode: GrantMode,
    val expiry: Long? = null,
    val policy: String? = null,
)

enum class GrantMode {
    AskEveryTime,
    AllowWhileUnlocked,
    AllowUntil,
    Deny,
}
