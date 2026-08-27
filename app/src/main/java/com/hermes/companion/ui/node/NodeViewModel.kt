package com.hermes.companion.ui.node

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.data.repo.CapabilityStatus
import com.hermes.companion.data.repo.NodeCapabilityItem
import com.hermes.companion.data.repo.NodeGrantItem
import com.hermes.companion.data.repo.NodePairing
import com.hermes.companion.data.repo.NodeRepository
import com.hermes.companion.data.repo.StreamRuleItem
import com.hermes.companion.data.repo.NodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NodeUiState(
    val node: NodeState = NodeState(),
    val filter: CapabilityStatus? = null,
    val filteredCapabilities: List<NodeCapabilityItem> = emptyList(),
)

@HiltViewModel
class NodeViewModel @Inject constructor(
    private val repo: NodeRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<CapabilityStatus?>(null)

    val state: StateFlow<NodeUiState> = combine(
        repo.observeNodeState(),
        filter,
    ) { nodeState, currentFilter ->
        val filtered = if (currentFilter == null) {
            nodeState.capabilities
        } else {
            nodeState.capabilities.filter { it.status == currentFilter }
        }
        NodeUiState(
            node = nodeState,
            filter = currentFilter,
            filteredCapabilities = filtered,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NodeUiState())

    fun setFilter(status: CapabilityStatus?) {
        filter.value = status
    }

    val pairings: StateFlow<List<NodePairing>> = repo.observePairings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun runCanary() {
        viewModelScope.launch { repo.runCanary() }
    }

    fun pair(baseUrl: String, setupCode: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val err = repo.pairNode(baseUrl.trim(), setupCode.trim()).exceptionOrNull()?.message
            onResult(err)
        }
    }

    fun unpair(gatewayId: String) {
        viewModelScope.launch { repo.unpairNode(gatewayId) }
    }

    val grants: StateFlow<List<NodeGrantItem>> = repo.observeGrants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setGrant(item: NodeGrantItem, mode: String) {
        viewModelScope.launch {
            repo.setGrant(item.gatewayId, item.nodeId, item.profileId, item.capability, mode)
        }
    }

    val streamRules: StateFlow<List<StreamRuleItem>> = repo.observeStreamRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStreamRule(source: String, mode: String) {
        viewModelScope.launch { repo.setStreamRule(source, mode) }
    }
}
