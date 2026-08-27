package com.hermes.companion.ui.outbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.companion.CompanionApp
import com.hermes.companion.data.repo.OutboxRepository
import com.hermes.companion.data.repo.OutboxState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OutboxViewModel(
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

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OutboxViewModel(CompanionApp.get().data.outbox) as T
            }
    }
}
