package com.hermes.companion.backend

import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.ProfileHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds one backend per gateway and enforces state isolation per
 * `(gateway_id, profile_id)` per §3 of the plan. Switching gateway cannot
 * leak another gateway's sessions, profiles, or active selection.
 *
 * Profile handle disambiguation: when two gateways both expose a profile
 * with the same id (e.g. `ash` on `gw-home` and `ash` on `gw-cloud`),
 * the rendered handle is `@ash` on the unique-gateway context and
 * `@ash-home` / `@ash-cloud` when both are visible in the union roster.
 */
class BackendRegistry(initial: List<HermesBackend>) {

    private val backends = ConcurrentHashMap<String, HermesBackend>()
        .apply { putAll(initial.associateBy { it.gateway.id }) }

    private val _gateways = MutableStateFlow(backends.values.map { it.gateway })
    val gateways: StateFlow<List<com.hermes.companion.domain.GatewayConnection>> = _gateways.asStateFlow()

    private val _selectedRoute = MutableStateFlow<ConversationRoute?>(null)
    val selectedRoute: StateFlow<ConversationRoute?> = _selectedRoute.asStateFlow()

    fun backendFor(gatewayId: String): HermesBackend? = backends[gatewayId]

    fun requireBackend(gatewayId: String): HermesBackend =
        backends[gatewayId] ?: error("no backend registered for gateway $gatewayId")

    fun addGateway(backend: HermesBackend) {
        backends[backend.gateway.id] = backend
        _gateways.value = backends.values.map { it.gateway }
    }

    fun removeGateway(gatewayId: String) {
        backends.remove(gatewayId)
        _gateways.value = backends.values.map { it.gateway }
        // Clearing selection if it pointed at the removed gateway.
        _selectedRoute.update { current ->
            if (current?.gatewayId == gatewayId) null else current
        }
    }

    /** Union roster across gateways, with disambiguated handles. */
    suspend fun unionRoster(): List<AgentProfile> {
        val raw = backends.values.flatMap { backend -> runIfMock(backend) { it.listProfiles() } }
        // Count duplicates of profileId to decide whether to disambiguate.
        val duplicates = raw.groupingBy { it.profileId }.eachCount()
        return raw.map { p ->
            if (duplicates[p.profileId]!! > 1) {
                p.copy(handle = ProfileHandle(
                    profileId = p.profileId,
                    display = "${p.profileId}-${p.gatewayId.removePrefix("gw-")}",
                ))
            } else p
        }
    }

    /** Select a route. The registry never holds cross-gateway selection. */
    fun selectRoute(route: ConversationRoute) {
        require(backends.containsKey(route.gatewayId)) {
            "cannot select route on unknown gateway ${route.gatewayId}"
        }
        _selectedRoute.value = route
    }

    fun clearSelection() {
        _selectedRoute.value = null
    }

    /**
     * Tiny adapter so the registry can stay backend-agnostic while the
     * PoC keeps everything in-process. Real HTTP backends will replace
     * this with their own suspend calls.
     */
    private suspend fun <T> runIfMock(backend: HermesBackend, block: suspend (MockHermesBackend) -> T): T {
        require(backend is MockHermesBackend) { "only MockHermesBackend supported in PoC" }
        return block(backend)
    }
}
