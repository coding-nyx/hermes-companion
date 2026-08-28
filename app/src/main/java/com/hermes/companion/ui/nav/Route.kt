package com.hermes.companion.ui.nav

import com.hermes.companion.domain.ConversationRoute

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Hermes : Route("hermes")
    data object Shade : Route("shade")
    data object Device : Route("device")
    data object More : Route("more")

    data class Chat(val conversation: ConversationRoute) : Route(
        "chat/${conversation.gatewayId}/${conversation.profileId}/${conversation.sessionId}",
    ) {
        companion object {
            const val pattern = "chat/{gatewayId}/{profileId}/{sessionId}"
        }
    }
}
