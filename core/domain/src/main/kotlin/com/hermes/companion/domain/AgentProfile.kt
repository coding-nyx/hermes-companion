package com.hermes.companion.domain

/**
 * Isolated Hermes agent on a gateway. A profile is the `HERMES_HOME` state
 * boundary per §2 of the plan.
 */
data class AgentProfile(
    val gatewayId: String,
    val profileId: String,
    val displayName: String,
    val handle: ProfileHandle,
    val capabilities: Set<NodeCapability> = emptySet(),
    val multiplexed: Boolean = true,
)
