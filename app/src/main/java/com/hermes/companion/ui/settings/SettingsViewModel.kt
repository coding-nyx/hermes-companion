package com.hermes.companion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.common.reason
import com.hermes.companion.data.repo.Fleet
import com.hermes.companion.data.repo.FleetRepository
import com.hermes.companion.domain.GatewayKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val fleet: Fleet = Fleet(),
    val activeGatewayId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val fleet: FleetRepository,
) : ViewModel() {

    private val errors = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> =
        combine(fleet.fleet(), fleet.observeActive(), errors) { f, a, e -> SettingsUiState(f, a, e) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun addGateway(label: String, baseUrl: String, kind: GatewayKind) {
        viewModelScope.launch {
            errors.value = fleet.addGateway(label, baseUrl, kind).exceptionOrNull()?.reason()
        }
    }

    fun removeGateway(gatewayId: String) {
        viewModelScope.launch {
            errors.value = fleet.forget(gatewayId).exceptionOrNull()?.reason()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            fleet.refresh()
        }
    }

    fun setActive(gatewayId: String) {
        viewModelScope.launch {
            errors.value = fleet.setActive(gatewayId).exceptionOrNull()?.reason()
        }
    }
}
