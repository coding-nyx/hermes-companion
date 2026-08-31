package com.hermes.companion.ui.v1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.data.repo.ActivityRepository
import com.hermes.companion.data.repo.ActivityState
import com.hermes.companion.data.repo.ConversationRepository
import com.hermes.companion.data.repo.ConversationState
import com.hermes.companion.data.repo.Fleet
import com.hermes.companion.data.repo.FleetRepository
import com.hermes.companion.domain.ConversationRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shell state for Phase A.
 *
 * Holds the currently-active thread (None until the user opens one), the
 * sheet/drawer visibility flags, and the read-through state for the rail
 * (fleet + activity inbox count), context panel (current conversation
 * + tool runs + approvals), and the bottom sheets (profile + new thread).
 *
 * The "activity inbox" count is the items that need user action — pending
 * approvals, draft replies, awaiting decisions — NOT every unread item
 * (per the Phase A decision). We approximate that here by counting items
 * whose outcome is AwaitingApproval; the Activity repository already
 * exposes that classification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class V1ShellViewModel @Inject constructor(
    private val fleetRepo: FleetRepository,
    private val conversations: ConversationRepository,
    private val activityRepo: ActivityRepository,
) : ViewModel() {

    // ── UI state ──────────────────────────────────────────────────────────

    private val _activeRoute = MutableStateFlow<ConversationRoute?>(null)
    val activeRoute: StateFlow<ConversationRoute?> = _activeRoute.asStateFlow()

    private val _leftDrawerOpen = MutableStateFlow(false)
    val leftDrawerOpen: StateFlow<Boolean> = _leftDrawerOpen.asStateFlow()

    private val _contextDrawerOpen = MutableStateFlow(false)
    val contextDrawerOpen: StateFlow<Boolean> = _contextDrawerOpen.asStateFlow()

    private val _profileSheetOpen = MutableStateFlow(false)
    val profileSheetOpen: StateFlow<Boolean> = _profileSheetOpen.asStateFlow()

    private val _settingsSheetOpen = MutableStateFlow(false)
    val settingsSheetOpen: StateFlow<Boolean> = _settingsSheetOpen.asStateFlow()

    private val _pairAsNodeOpen = MutableStateFlow(false)
    val pairAsNodeOpen: StateFlow<Boolean> = _pairAsNodeOpen.asStateFlow()

    private val _newThreadOpen = MutableStateFlow(false)
    val newThreadOpen: StateFlow<Boolean> = _newThreadOpen.asStateFlow()

    // ── Read-through data ─────────────────────────────────────────────────

    val fleet: StateFlow<Fleet> = fleetRepo.fleet()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Fleet())

    val activity: StateFlow<ActivityState> = activityRepo.observeActivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityState())

    /**
     * Inbox count for the chat top bar / rail header — items needing user
     * action only (per Phase A decision 7).
     */
    val inboxCount: StateFlow<Int> = activity
        .let { src ->
            kotlinx.coroutines.flow.combine(src, kotlinx.coroutines.flow.flowOf(Unit)) { state, _ ->
                state.items.count { it.outcome == com.hermes.companion.data.repo.ActivityOutcome.AwaitingApproval }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val conversation: StateFlow<ConversationState> =
        _activeRoute.flatMapLatest { r ->
            r?.let { conversations.conversation(it) } ?: flowOf(ConversationState())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationState())

    init {
        refreshFleet()
    }

    // ── Intents ───────────────────────────────────────────────────────────

    fun refreshFleet() {
        viewModelScope.launch { fleetRepo.refresh() }
    }

    fun openThread(route: ConversationRoute) {
        _activeRoute.value = route
        _leftDrawerOpen.value = false
        _contextDrawerOpen.value = false
    }

    fun clearThread() {
        _activeRoute.value = null
    }

    fun toggleLeftDrawer() {
        _leftDrawerOpen.value = !_leftDrawerOpen.value
        if (_leftDrawerOpen.value) _contextDrawerOpen.value = false
    }

    fun toggleContextDrawer() {
        _contextDrawerOpen.value = !_contextDrawerOpen.value
        if (_contextDrawerOpen.value) _leftDrawerOpen.value = false
    }

    fun closeDrawers() {
        _leftDrawerOpen.value = false
        _contextDrawerOpen.value = false
    }

    fun openProfileSheet() { _profileSheetOpen.value = true }
    fun closeProfileSheet() { _profileSheetOpen.value = false }

    fun openSettingsSheet() { _settingsSheetOpen.value = true }
    fun closeSettingsSheet() { _settingsSheetOpen.value = false }

    fun openPairAsNode() { _pairAsNodeOpen.value = true }
    fun closePairAsNode() { _pairAsNodeOpen.value = false }

    fun openNewThread() { _newThreadOpen.value = true }
    fun closeNewThread() { _newThreadOpen.value = false }

    fun submitNewThread(route: ConversationRoute) {
        openThread(route)
        closeNewThread()
    }

    @Suppress("UNUSED_PARAMETER")
    fun createThread(
        gatewayId: String,
        profileId: String,
        title: String,
        initialMode: String,
        lockedToModel: Boolean,
        firstMessage: String?,
    ) {
        viewModelScope.launch {
            val route = ConversationRoute(gatewayId, profileId, "new")
            conversations.createSession(route, title)
                .onSuccess { session ->
                    val real = ConversationRoute(gatewayId, profileId, session.sessionId)
                    openThread(real)
                    if (!firstMessage.isNullOrBlank()) {
                        conversations.submit(real, firstMessage.trim())
                    }
                }
        }
    }

    /**
     * Forward a typed draft to the underlying conversations repository
     * for the active thread. No-op when no thread is selected.
     */
    fun submitDraft(text: String) {
        val route = _activeRoute.value ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { conversations.submit(route, clean) }
    }
}
