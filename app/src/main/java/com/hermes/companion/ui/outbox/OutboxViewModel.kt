package com.hermes.companion.ui.outbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.data.repo.OutboxRepository
import com.hermes.companion.data.repo.OutboxState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OutboxViewModel @Inject constructor(
    private val repo: OutboxRepository,
) : ViewModel() {

    val state: StateFlow<OutboxState> = repo.observeOutbox()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutboxState())

    fun retry(id: String) {
        viewModelScope.launch {
            repo.retrySubmission(id)
        }
    }

    fun drop(id: String) {
        viewModelScope.launch {
            repo.dropSubmission(id)
        }
    }
}
