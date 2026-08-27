package com.hermes.companion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.companion.CompanionApp
import com.hermes.companion.common.reason
import com.hermes.companion.data.repo.Fleet
import com.hermes.companion.data.repo.FleetRepository
import com.hermes.companion.domain.GatewayKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val fleet: Fleet = Fleet(),
    val error: String? = null,
)

class SettingsViewModel(
    private val fleet: FleetRepository,
) : ViewModel() {

    private val errors = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> =
        combine(fleet.fleet(), errors) { f, e -> SettingsUiState(f, e) }
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

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(CompanionApp.get().data.fleet) as T
            }
    }
}
