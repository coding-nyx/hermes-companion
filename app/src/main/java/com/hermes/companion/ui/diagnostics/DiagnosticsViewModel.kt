package com.hermes.companion.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.data.repo.Fleet
import com.hermes.companion.data.repo.FleetRepository
import com.hermes.companion.data.repo.NodePairing
import com.hermes.companion.data.repo.NodeRepository
import com.hermes.companion.data.repo.NodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val node: NodeRepository,
    fleet: FleetRepository,
) : ViewModel() {
    val fleet: StateFlow<Fleet> = fleet.fleet()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Fleet())
    val nodeState: StateFlow<NodeState> = node.observeNodeState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NodeState())
    val pairings: StateFlow<List<NodePairing>> = node.observePairings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun runCanary() { viewModelScope.launch { node.runCanary() } }
}
