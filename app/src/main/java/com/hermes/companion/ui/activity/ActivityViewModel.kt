package com.hermes.companion.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.data.repo.ActivityItem
import com.hermes.companion.data.repo.ActivityKind
import com.hermes.companion.data.repo.ActivityRepository
import com.hermes.companion.data.repo.ActivityState
import com.hermes.companion.data.repo.QueueSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ActivityUiState(
    val items: List<ActivityItem> = emptyList(),
    val queues: List<QueueSummary> = emptyList(),
    val filter: ActivityKind? = null,
    val expandedId: String? = null,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repo: ActivityRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<ActivityKind?>(null)
    private val expandedId = MutableStateFlow<String?>(null)

    val state: StateFlow<ActivityUiState> = combine(
        repo.observeActivity(),
        filter,
        expandedId,
    ) { activityState, currentFilter, expanded ->
        val filteredItems = if (currentFilter == null) {
            activityState.items
        } else {
            activityState.items.filter { it.kind == currentFilter }
        }
        ActivityUiState(
            items = filteredItems,
            queues = activityState.queues,
            filter = currentFilter,
            expandedId = expanded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    fun setFilter(kind: ActivityKind?) {
        filter.value = kind
    }

    fun toggleExpanded(id: String) {
        expandedId.value = if (expandedId.value == id) null else id
    }
}
