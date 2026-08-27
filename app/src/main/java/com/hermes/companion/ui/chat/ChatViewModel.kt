package com.hermes.companion.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.data.repo.ConversationRepository
import com.hermes.companion.data.repo.ConversationState
import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ConversationRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val conversation: ConversationState = ConversationState(),
    val draft: String = "",
) {
    val messages get() = conversation.messages
    val streaming get() = conversation.streaming
    val pendingApproval get() = conversation.pendingApproval
    val backendLabel get() = conversation.gatewayLabel
    /** A rendered condition, not a thrown one. */
    val error: String? get() = conversation.activeRun?.error ?: conversation.connectivity.reasonOrNull
}

/**
 * Thin by design: it maps one repository flow to one UI state and forwards
 * intents. It owns no job that outlives the screen — the run is collected by
 * the data layer, so leaving Chat no longer cancels it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversations: ConversationRepository,
) : ViewModel() {

    private val route = MutableStateFlow<ConversationRoute?>(null)
    private val draft = MutableStateFlow("")

    val state: StateFlow<ChatUiState> =
        combine(
            route.flatMapLatest { r ->
                r?.let { conversations.conversation(it) } ?: flowOf(ConversationState())
            },
            draft,
        ) { conversation, text -> ChatUiState(conversation, text) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun bind(route: ConversationRoute) {
        if (this.route.value == route) return
        this.route.value = route
        viewModelScope.launch { conversations.refresh(route) }
    }

    fun updateDraft(text: String) {
        draft.value = text
    }

    fun send() {
        val route = route.value ?: return
        val text = draft.value.trim()
        if (text.isEmpty()) return
        draft.value = ""
        viewModelScope.launch { conversations.submit(route, text) }
    }

    fun decide(option: ApprovalOption) {
        val route = route.value ?: return
        val pending = state.value.pendingApproval ?: return
        viewModelScope.launch {
            conversations.decide(route, pending.runId, pending.requestId, option)
        }
    }

    fun stop() {
        val route = route.value ?: return
        val runId = state.value.conversation.activeRun?.runId ?: return
        viewModelScope.launch { conversations.stop(route, runId) }
    }
}
