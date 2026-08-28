package com.hermes.companion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.common.ActiveGatewayConfig
import com.hermes.companion.common.VoiceConfig
import com.hermes.companion.common.reason
import dagger.hilt.android.qualifiers.ApplicationContext
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
        combine(fleet.fleet(), fleet.observeActive(), errors, voice) { f, a, e, v ->
            SettingsUiState(fleet = f, activeGatewayId = a, error = e, voice = v)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(voice = voice.value))

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

    fun setActive(gatewayId: String) {
        viewModelScope.launch {
            // v0.2 T7: pull url + nodeId from the gateway record + node_identity
            // so the NLS can POST to the gateway without doing a DB join.
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
            // Publish to the in-process [ActiveGatewayConfig] so the
            // OS-instantiated NLS picks up the change on its next reconnect.
            ActiveGatewayConfig.writeSync(context.filesDir, url, nodeId)
            errors.value = fleet.setActive(gatewayId, url, nodeId).exceptionOrNull()?.reason()
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
