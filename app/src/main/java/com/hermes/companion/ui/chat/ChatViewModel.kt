package com.hermes.companion.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hermes.companion.CompanionApp
import com.hermes.companion.backend.HermesBackend
import com.hermes.companion.backend.MockHermesBackend
import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ApprovalRequest
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.Message
import com.hermes.companion.domain.RunEvent
import com.hermes.companion.domain.RunState
import com.hermes.companion.domain.ToolRun
import com.hermes.companion.domain.ToolStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val route: ConversationRoute? = null,
    val messages: List<Message> = emptyList(),
    val streaming: Boolean = false,
    val streamingText: String = "",
    val pendingApproval: ApprovalRequest? = null,
    val draft: String = "",
    val backendLabel: String = "",
)

class ChatViewModel(
    private val registry: com.hermes.companion.backend.BackendRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null

    fun bind(route: ConversationRoute) {
        if (_state.value.route == route) return
        streamJob?.cancel()
        _state.update { it.copy(route = route, streamingText = "", pendingApproval = null) }
        registry.selectRoute(route)
        val backend = registry.backendFor(route.gatewayId) ?: return
        require(backend is MockHermesBackend)
        viewModelScope.launch {
            val msgs = backend.listMessages(route)
            _state.update {
                it.copy(
                    messages = msgs,
                    backendLabel = backend.gateway.label,
                )
            }
        }
    }

    fun updateDraft(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun send() {
        val route = _state.value.route ?: return
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(draft = "", streaming = true, streamingText = "") }
        streamJob = viewModelScope.launch {
            val backend = registry.backendFor(route.gatewayId) ?: return@launch
            require(backend is MockHermesBackend)
            val collectedTools = mutableListOf<ToolRun>()
            backend.sendAndStream(route, text).collect { event ->
                handleEvent(route, event, collectedTools)
            }
            _state.update { it.copy(streaming = false) }
        }
    }

    private fun handleEvent(route: ConversationRoute, event: RunEvent, tools: MutableList<ToolRun>) {
        when (event) {
            is RunEvent.AssistantDelta -> {
                _state.update { it.copy(streamingText = it.streamingText + event.delta) }
            }
            is RunEvent.ToolStarted -> {
                tools += event.toolRun
                _state.update {
                    it.copy(
                        messages = it.messages + Message.Assistant(
                            id = UUID.randomUUID().toString(),
                            sessionId = route.sessionId,
                            profileId = route.profileId,
                            gatewayId = route.gatewayId,
                            createdAt = System.currentTimeMillis(),
                            text = "",
                            toolRuns = tools.toList(),
                            isStreaming = true,
                        )
                    )
                }
            }
            is RunEvent.ToolCompleted -> {
                val updated = tools.map { tr ->
                    if (tr.id == event.toolRun.id) event.toolRun else tr
                }
                tools.clear(); tools.addAll(updated)
                replaceLastAssistant(route, tools)
            }
            is RunEvent.ApprovalRequired -> {
                _state.update { it.copy(pendingApproval = event.request, streaming = false) }
            }
            is RunEvent.RunCompleted -> {
                _state.update {
                    it.copy(
                        messages = it.messages + Message.Assistant(
                            id = UUID.randomUUID().toString(),
                            sessionId = route.sessionId,
                            profileId = route.profileId,
                            gatewayId = route.gatewayId,
                            createdAt = System.currentTimeMillis(),
                            text = event.finalText,
                            toolRuns = tools.toList(),
                        ),
                        streamingText = "",
                    )
                }
            }
            is RunEvent.RunFailed -> {
                _state.update { it.copy(streaming = false, streamingText = "") }
            }
        }
    }

    private fun replaceLastAssistant(route: ConversationRoute, tools: List<ToolRun>) {
        _state.update { st ->
            val last = st.messages.lastOrNull() as? Message.Assistant
            if (last == null) st else {
                val updated = last.copy(toolRuns = tools.toList())
                st.copy(messages = st.messages.dropLast(1) + updated)
            }
        }
    }

    fun decide(option: ApprovalOption) {
        val route = _state.value.route ?: return
        val pending = _state.value.pendingApproval ?: return
        _state.update { it.copy(pendingApproval = null) }
        val backend = registry.backendFor(route.gatewayId) ?: return
        require(backend is MockHermesBackend)
        viewModelScope.launch {
            backend.decideApproval(route, pending.requestId, option)
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(CompanionApp.get().registry) as T
            }
    }
}
