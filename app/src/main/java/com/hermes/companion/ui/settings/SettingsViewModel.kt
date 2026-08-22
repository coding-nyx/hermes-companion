package com.hermes.companion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.companion.CompanionApp
import com.hermes.companion.backend.BackendRegistry
import com.hermes.companion.backend.MockHermesBackend
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val gateways: List<GatewayConnection> = emptyList(),
)

class SettingsViewModel(
    private val registry: BackendRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = SettingsUiState(gateways = registry.gateways.value)
    }

    fun removeGateway(gatewayId: String) {
        registry.removeGateway(gatewayId)
        refresh()
    }

    fun addMockGateway(label: String, kind: GatewayKind, profileIds: List<String>) {
        val id = "gw-${label.lowercase().replace(" ", "-")}-${System.currentTimeMillis() % 10_000}"
        val backend = MockHermesBackend(
            gateway = GatewayConnection(
                id = id,
                label = label,
                kind = kind,
                baseUrl = "mock://$id",
                authRef = "none",
            ),
            profileIds = profileIds,
        )
        registry.addGateway(backend)
        refresh()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(CompanionApp.get().registry) as T
            }
    }
}
