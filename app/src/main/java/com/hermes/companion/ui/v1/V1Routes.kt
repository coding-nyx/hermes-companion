package com.hermes.companion.ui.v1

import com.hermes.companion.domain.ConversationRoute

/**
 * Route table for the Phase A v1 shell. Distinct from [com.hermes.companion.ui.nav.Route]
 * so the two shells can coexist; MainActivity picks one based on the BuildConfig
 * `USE_V1_SHELL` flag.
 *
 * The shell is mostly state-driven (drawers / sheets), not navigation-driven —
 * these destinations exist so deep links (from notifications, notifications
 * action replies, and AskHermes) can land on the right surface without
 * touching the v0.2 routes.
 */
sealed class V1Route(val path: String) {
    data object Chat : V1Route("v1/chat")
    data object Settings : V1Route("v1/settings")
    data object PairAsNode : V1Route("v1/pair-as-node")
    data object Discover : V1Route("v1/discover")
    data object Outbox : V1Route("v1/outbox")
    data object Diagnostics : V1Route("v1/diagnostics")
    data object Appearance : V1Route("v1/appearance")
    data object Profile : V1Route("v1/profile")

    /** Chat keyed on a [ConversationRoute]. Same shape as the v0.2 Chat route. */
    data class ChatThread(val conversation: ConversationRoute) :
        V1Route("v1/chat/${conversation.gatewayId}/${conversation.profileId}/${conversation.sessionId}") {
        companion object {
            const val pattern = "v1/chat/{gatewayId}/{profileId}/{sessionId}"
        }
    }
}
