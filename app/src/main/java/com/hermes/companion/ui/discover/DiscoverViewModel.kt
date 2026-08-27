package com.hermes.companion.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.data.repo.DiscoveryRepository
import com.hermes.companion.data.repo.DiscoveryUiState
import com.hermes.companion.data.repo.FleetRepository
import com.hermes.companion.domain.GatewayKind
import com.hermes.companion.domain.TransportTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val discovery: DiscoveryRepository,
    private val fleet: FleetRepository,
) : ViewModel() {

    val state: StateFlow<DiscoveryUiState> = discovery.observeDiscovery()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoveryUiState())

    fun tierOf(baseUrl: String): TransportTier = discovery.tierOf(baseUrl)

    fun add(label: String, baseUrl: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val err = fleet.addGateway(label.trim(), baseUrl.trim(), GatewayKind.RemoteHttp).exceptionOrNull()?.message
            onResult(err)
        }
    }
}
