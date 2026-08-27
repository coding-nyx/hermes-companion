package com.hermes.companion.ui.nav

import com.hermes.companion.domain.ConversationRoute

/**
 * Top-level destinations of the shell. Chat is keyed on a
 * [ConversationRoute] so navigation always carries the routing key.
 */
sealed class Route(val path: String) {
    data object Agents : Route("agents")
    data object Activity : Route("activity")
    data object Node : Route("node")
    data object Settings : Route("settings")
    data object Outbox : Route("outbox")
    data object NodeSetup : Route("node_setup")
    data object NodeGrants : Route("node_grants")
    data object Discover : Route("discover")
    data object Diagnostics : Route("diagnostics")
    data object StreamRules : Route("stream_rules")

    data class Chat(val conversation: ConversationRoute) : Route("chat/${conversation.gatewayId}/${conversation.profileId}/${conversation.sessionId}") {
        companion object {
            const val pattern = "chat/{gatewayId}/{profileId}/{sessionId}"
        }
    }
}

