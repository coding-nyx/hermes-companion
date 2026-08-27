package com.hermes.companion.ui.node

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.companion.CompanionApp
import com.hermes.companion.data.repo.CapabilityStatus
import com.hermes.companion.data.repo.NodeCapabilityItem
import com.hermes.companion.data.repo.NodeRepository
import com.hermes.companion.data.repo.NodeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NodeUiState(
    val node: NodeState = NodeState(),
    val filter: CapabilityStatus? = null,
    val filteredCapabilities: List<NodeCapabilityItem> = emptyList(),
)

class NodeViewModel(
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

    fun runCanary() {
        viewModelScope.launch {
            repo.runCanary()
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NodeViewModel(CompanionApp.get().data.node) as T
            }
    }
}
