package com.hermes.companion.ui.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.companion.CompanionApp
import com.hermes.companion.data.repo.ConversationRepository
import com.hermes.companion.data.repo.Fleet
import com.hermes.companion.data.repo.FleetRepository
import com.hermes.companion.domain.ConversationRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Observes the database. It has no backend reference, cannot throw a network
 * exception, and does not need an error field: reachability arrives as data on
 * every gateway row.
 */
class AgentsViewModel(
    private val fleet: FleetRepository,
    private val conversations: ConversationRepository,
) : ViewModel() {

    val state: StateFlow<Fleet> = fleet.fleet()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Fleet())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { fleet.refresh() }
    }

    fun createThread(gatewayId: String, profileId: String, title: String, onCreated: (ConversationRoute) -> Unit) {
        viewModelScope.launch {
            val dummyRoute = ConversationRoute(gatewayId, profileId, "new")
            conversations.createSession(dummyRoute, title)
                .onSuccess { session ->
                    onCreated(ConversationRoute(gatewayId, profileId, session.sessionId))
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = CompanionApp.get()
                    return AgentsViewModel(app.data.fleet, app.data.conversations) as T
                }
            }
    }
}

