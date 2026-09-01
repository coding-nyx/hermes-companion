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

    /**
     * Map of `gatewayId → profileId` for the active selection on each gateway.
     * Surfaced to the UI so the profile switcher can mark only the truly
     * active row (was previously hardcoded `true` on every row).
     */
    private val _activeProfileByGatewayId = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeProfileByGatewayId: StateFlow<Map<String, String>> =
        _activeProfileByGatewayId.asStateFlow()

    // Local mirror of the drawer's open state for the context drawer only.
    // The left drawer's state is owned by DrawerState in V1Shell (no mirror
    // — that mirror was the source of the recursive re-toggle bug).
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

    // Hoisted composer state. Was `var draft by remember { mutableStateOf }`
    // inside V1ChatSurface — moving it here makes it survive configuration
    // changes via the ViewModel scope.
    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    // Hoisted mic-recording state. Was a local Bool in V1ChatSurface; now
    // drives the V1BVoiceRecordingOverlay from the shell.
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // Was _leftDrawerOpen — the VM no longer mirrors the left drawer state
    // because doing so caused an infinite re-toggle. The left drawer's open
    // state lives only in V1Shell's DrawerState. Keeping a no-op toggleLeft
    // here so external callers (e.g. NewThread's auto-open thread) still
    // compile; it does not need to do anything since DrawerState is
    // locally owned by V1Shell now.
    fun toggleLeftDrawer() {
        _contextDrawerOpen.value = false
    }

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
        _contextDrawerOpen.value = false
        // Mirror the route's profile/gateway in the active-by-gateway map so
        // the profile switcher can highlight it.
        _activeProfileByGatewayId.value =
            _activeProfileByGatewayId.value + (route.gatewayId to route.profileId)
    }

    fun clearThread() {
        _activeRoute.value = null
    }

    fun toggleContextDrawer() {
        _contextDrawerOpen.value = !_contextDrawerOpen.value
    }

    fun closeDrawers() {
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

    // ── Composer state (Phase 3 hoisting) ─────────────────────────────────

    fun updateDraft(text: String) { _draft.value = text }

    fun startRecording() { _isRecording.value = true }
    fun stopRecording() { _isRecording.value = false }

    /**
     * Forward a typed draft to the underlying conversations repository for
     * the active thread. When no thread is open we now open the NewThread
     * dialog with the draft as the first message, so taps are no longer
     * silently dropped (was Bug #9).
     */
    fun submitDraft(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val route = _activeRoute.value
        if (route == null) {
            _draft.value = clean
            _newThreadOpen.value = true
            return
        }
        viewModelScope.launch { conversations.submit(route, clean) }
        _draft.value = ""
    }

    // ── Profile / gateway wiring (P1 handlers) ──────────────────────────

    /**
     * Mark a profile as the active one for its gateway. Persists via
     * FleetRepository; updates the local mirror so the profile switcher
     * row stops showing every row as active (was Bug #6).
     */
    fun setActiveProfile(gatewayId: String, profileId: String) {
        _activeProfileByGatewayId.value =
            _activeProfileByGatewayId.value + (gatewayId to profileId)
        viewModelScope.launch {
            fleetRepo.setActiveProfile(gatewayId, profileId)
        }
    }

    /** Switch the active gateway. Stub — wire to setActive() when ready. */
    @Suppress("UNUSED_PARAMETER")
    fun switchGateway(gatewayId: String) {
        // No-op in this snapshot; the wire layer will land in Phase 4.
        _contextDrawerOpen.value = false
    }

    fun onPickPrompt(label: String) {
        // Auto-fill the draft with the prompt label and open the new-thread
        // dialog if no thread is open — was Bug #7 (silent no-op).
        _draft.value = label
        if (_activeRoute.value == null) {
            _newThreadOpen.value = true
        }
    }
}
