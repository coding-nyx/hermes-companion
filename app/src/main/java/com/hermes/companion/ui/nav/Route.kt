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
    data object ChatTab : Route("chat_home")
    data object Outbox : Route("outbox")
    data object NodeSetup : Route("node_setup")
    data object NodeGrants : Route("node_grants")
    data object Discover : Route("discover")
    data object Diagnostics : Route("diagnostics")
    data object StreamRules : Route("stream_rules")
    data object Appearance : Route("appearance")

    // Web-app tab routes (port from origin/main, additive). These match the
    // master five-tab shell (Home, Hermes, Shade, Device, More). We KEEP our
    // existing tabs above wired to the WebSocket broker + NotificationForwarder
    // + pair-flow screens. They are not yet wired in Shell.kt - they exist so
    // future AskHermes deep links and the future Shade screen have stable paths.
    data object Home : Route("home")
    data object Hermes : Route("hermes")
    data object Shade : Route("shade")
    data object Device : Route("device")
    data object More : Route("more")

    data class Chat(val conversation: ConversationRoute) : Route("chat/${conversation.gatewayId}/${conversation.profileId}/${conversation.sessionId}") {
        companion object {
            const val pattern = "chat/{gatewayId}/{profileId}/{sessionId}"
        }
    }
}
