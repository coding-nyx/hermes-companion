package com.hermes.companion.node

import com.hermes.companion.domain.AndroidRequirement
import com.hermes.companion.domain.CapabilityHealth
import com.hermes.companion.domain.NodeCapability
import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.Receipt

/**
 * One adapter per capability family. Depends on `:core:domain` and the Android
 * SDK only — no gateway, no transport, no Room. That constraint is what makes an
 * adapter testable in isolation (`plan/10-architecture/capabilities.md`).
 */
interface CapabilityAdapter {
    val capability: NodeCapability
    val requires: Set<AndroidRequirement>
    val exclusive: Boolean get() = capability.exclusive

    /** Derived on demand from live OS state — never cached optimistically. */
    fun health(): CapabilityHealth

    suspend fun invoke(command: NodeCommand): Receipt
}

/** What the Node coverage matrix renders for one capability. */
data class CapabilityCoverage(
    val capability: NodeCapability,
    val health: CapabilityHealth,
    val detail: String,
)

/**
 * The set of adapters this device offers. Coverage is computed live from each
 * adapter's [CapabilityAdapter.health], so it never claims a capability it
 * cannot currently serve.
 */
class AdapterRegistry(private val adapters: List<CapabilityAdapter>) {
    fun all(): List<CapabilityAdapter> = adapters
    fun forFamily(family: String): CapabilityAdapter? = adapters.firstOrNull { it.capability.family == family }
    fun coverage(): List<CapabilityCoverage> = adapters.map {
        CapabilityCoverage(it.capability, it.health(), detailFor(it))
    }

    private fun detailFor(a: CapabilityAdapter): String = when (a.health()) {
        CapabilityHealth.Working -> "Working"
        CapabilityHealth.PermissionMissing -> "Permission needed"
        CapabilityHealth.OsLimited -> "OS-limited"
        CapabilityHealth.Unavailable -> "Unavailable"
    }
}
