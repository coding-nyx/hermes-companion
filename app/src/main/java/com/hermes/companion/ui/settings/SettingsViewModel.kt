package com.hermes.companion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.common.ActiveGatewayConfig
import com.hermes.companion.common.VoiceConfig
import com.hermes.companion.common.reason
import dagger.hilt.android.qualifiers.ApplicationContext
import com.hermes.companion.data.db.ActiveGatewayEntity
import com.hermes.companion.data.repo.Fleet
import com.hermes.companion.data.repo.FleetRepository
import com.hermes.companion.domain.GatewayKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val fleet: Fleet = Fleet(),
    val activeGatewayId: String? = null,
    /**
     * T2: maps gatewayId -> active profileId for that gateway. Built off the
     * [ActiveGatewayEntity.activeProfileId] column, which is the singleton
     * row's persisted choice. Empty until the user has picked a profile (or
     * [SettingsViewModel.setActiveProfile] is invoked).
     */
    val activeProfileByGateway: Map<String, String> = emptyMap(),
    val error: String? = null,
    /** Voice feature config (Phase 1). Loaded from voice.json on init. */
    val voice: VoiceConfig.VoiceSnapshot = VoiceConfig.DEFAULT_VOICE,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val fleet: FleetRepository,
    @ApplicationContext private val context: android.content.Context,
    /** Exposed for the Routing tab, which collects rule flows directly. */
    val ruleRepo: com.hermes.companion.data.repo.NotificationRuleRepository,
) : ViewModel() {

    private val errors = MutableStateFlow<String?>(null)

    /**
     * Voice snapshot. Seeded from the file-backed JSON store so the UI shows
     * the persisted choice on first render. Updated by [setVoiceConfig].
     */
    private val voice = MutableStateFlow(VoiceConfig.readSync(context.filesDir))

    val state: StateFlow<SettingsUiState> =
        combine(
            fleet.fleet(),
            fleet.observeActive(),
            fleet.observeActiveFull(),
            errors,
            voice,
        ) { f, a, activeFull, e, v ->
            // T2: project the singleton active_gateway row's activeProfileId
            // into a {gatewayId -> profileId} map. Only the active gateway
            // has a persisted choice; everything else is empty.
            val profileMap: Map<String, String> = activeFull?.let { row ->
                val profile = row.activeProfileId
                if (profile != null && a != null && row.gatewayId == a) {
                    mapOf(a to profile)
                } else {
                    emptyMap()
                }
            } ?: emptyMap()
            SettingsUiState(
                fleet = f,
                activeGatewayId = a,
                activeProfileByGateway = profileMap,
                error = e,
                voice = v,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(voice = voice.value),
        )

    fun addGateway(label: String, baseUrl: String, kind: GatewayKind) {
        viewModelScope.launch {
            errors.value = fleet.addGateway(label, baseUrl, kind).exceptionOrNull()?.reason()
        }
    }

    fun removeGateway(gatewayId: String) {
        viewModelScope.launch {
            errors.value = fleet.forget(gatewayId).exceptionOrNull()?.reason()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            fleet.refresh()
        }
    }

    /**
     * T2: setActive(gatewayId) now optionally threads an [activeProfileId]
     * through to the file-backed [ActiveGatewayConfig]. The DB singleton
     * row is set by [fleet.setActive]; we always preserve the previously
     * chosen profile by reading it via [observeActiveProfileId] when no
     * override is given.
     */
    fun setActive(gatewayId: String, activeProfileId: String? = null) {
        viewModelScope.launch {
            val effectiveProfileId = activeProfileId ?: fleet.observeActiveProfileId(gatewayId)
            val url = fleet.fleet().first().gateways
                .firstOrNull { gatewayView -> gatewayView.gateway.id == gatewayId }
                ?.gateway?.baseUrl
            if (url == null) {
                errors.value = "gateway $gatewayId not in fleet"
                return@launch
            }
            val nodeId = fleet.observeActiveNodeId(gatewayId)
            if (nodeId == null) {
                errors.value = "node not paired for $gatewayId"
                return@launch
            }
            ActiveGatewayConfig.writeSync(context.filesDir, url, nodeId, effectiveProfileId)
            // If the caller passed a profile override, persist it to the
            // singleton row too — `fleet.setActive` resets the row and would
            // wipe the prior profile id otherwise.
            if (activeProfileId != null) {
                fleet.setActiveProfile(gatewayId, activeProfileId)
            }
            errors.value = fleet.setActive(gatewayId, url, nodeId).exceptionOrNull()?.reason()
        }
    }

    /**
     * T2: Switch the active profile for [gatewayId]. Persists to both the
     * file-backed [ActiveGatewayConfig] (so the OS-instantiated NLS picks
     * up the change on its next reconnect) and the DB singleton row (so
     * the SettingsUiState.activeProfileByGateway map reflects the new pick
     * across config changes).
     */
    fun setActiveProfile(gatewayId: String, profileId: String) {
        viewModelScope.launch {
            val result = fleet.setActiveProfile(gatewayId, profileId)
            if (result.isFailure) {
                errors.value = result.exceptionOrNull()?.reason()
                return@launch
            }
            // Mirror the persisted profile id into the in-process
            // ActiveGatewayConfig so the NLS sees it on its next reconnect.
            val url = fleet.fleet().first().gateways
                .firstOrNull { it.gateway.id == gatewayId }
                ?.gateway?.baseUrl
            val nodeId = fleet.observeActiveNodeId(gatewayId)
            if (url != null && nodeId != null) {
                ActiveGatewayConfig.writeSync(context.filesDir, url, nodeId, profileId)
            }
        }
    }

    /**
     * Update the persisted voice config and the UI state. Writes
     * `voice.json` under [Context.filesDir] via [VoiceConfig.writeSync] so
     * the OS-instantiated voice services in :node pick it up on their next
     * reconnect. Same file-backed bridge pattern as [setActive].
     *
     * Synchronous file I/O — voice.json is <100 bytes and the call site is
     * the user tapping a picker, which already does its own dispatch.
     */
    fun setVoiceConfig(snap: VoiceConfig.VoiceSnapshot) {
        VoiceConfig.writeSync(context.filesDir, snap)
        voice.value = snap
    }
}