package com.hermes.companion.ui.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.companion.CompanionApp
import com.hermes.companion.backend.HermesBackend
import com.hermes.companion.backend.MockHermesBackend
import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgentsUiState(
    val gateways: List<GatewayConnection> = emptyList(),
    val profiles: List<AgentProfile> = emptyList(),
    val sessionsByRoute: Map<ConversationRoute, List<Session>> = emptyMap(),
    val loading: Boolean = true,
)

class AgentsViewModel(
    private val registry: com.hermes.companion.backend.BackendRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(AgentsUiState())
    val state: StateFlow<AgentsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val gateways = registry.gateways.value
            val profiles = registry.unionRoster()
            val sessions = mutableMapOf<ConversationRoute, List<Session>>()
            gateways.forEach { gw ->
                val backend = registry.backendFor(gw.id) ?: return@forEach
                require(backend is MockHermesBackend)
                profiles.filter { it.gatewayId == gw.id }.forEach { profile ->
                    val sessList = backend.listSessionsForProfile(gw.id, profile.profileId)
                    sessList.forEach { s ->
                        sessions[ConversationRoute(gw.id, profile.profileId, s.sessionId)] = sessList
                    }
                }
            }
            _state.value = AgentsUiState(
                gateways = gateways,
                profiles = profiles,
                sessionsByRoute = sessions,
                loading = false,
            )
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AgentsViewModel(CompanionApp.get().registry) as T
            }
    }
}
