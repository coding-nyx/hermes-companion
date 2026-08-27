package com.hermes.companion.data.repo

import com.hermes.companion.data.db.CompanionStore
import com.hermes.companion.data.db.LeaseEntity
import com.hermes.companion.domain.GrantMode
import com.hermes.companion.domain.Lease
import com.hermes.companion.domain.LeaseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Exclusive-capability leases. The leases table's PK is the capability, so a
 * second acquire fails closed with a receipt naming the holder — never a queue,
 * never a preemption (`plan/10-architecture/capabilities.md`).
 */
class LeaseManager internal constructor(
    private val store: CompanionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun acquire(capability: String, gatewayId: String, profileId: String, requestId: String, ttlMs: Long): LeaseResult {
        val now = clock()
        store.leases.purgeExpired(now)
        val lease = LeaseEntity(capability, gatewayId, profileId, requestId, now, now + ttlMs)
        return try {
            store.leases.insert(lease)
            LeaseResult.Acquired(lease.toDomain())
        } catch (_: Throwable) {
            val held = store.leases.find(capability)
            if (held == null) {
                // Raced with a purge/release; a single retry is safe.
                runCatching { store.leases.insert(lease) }
                    .map { LeaseResult.Acquired(lease.toDomain()) as LeaseResult }
                    .getOrElse { LeaseResult.Held(lease.toDomain(), lease.expiresAt) }
            } else {
                LeaseResult.Held(held.toDomain(), held.expiresAt)
            }
        }
    }

    suspend fun release(capability: String, requestId: String) = store.leases.release(capability, requestId)

    fun observe(): Flow<List<Lease>> = store.leases.observeAll().map { rows -> rows.map { it.toDomain() } }
}

private fun LeaseEntity.toDomain() = Lease(capability, gatewayId, profileId, requestId, acquiredAt, expiresAt)

sealed interface GrantDecision {
    data object Allowed : GrantDecision
    data class Denied(val reason: String) : GrantDecision
}

/**
 * Decides whether a command may run, from the grant scoped to
 * (gateway, profile, node, capability). Fails closed: no grant is a denial.
 * Hermes' own approval policy remains authoritative on top of this.
 */
class GrantChecker internal constructor(
    private val store: CompanionStore,
    private val locked: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun evaluate(gatewayId: String, profileId: String, nodeId: String, capability: String): GrantDecision {
        val row = store.grants.find(gatewayId, profileId, nodeId, capability)
            ?: return GrantDecision.Denied("no grant for $capability")
        val mode = runCatching { GrantMode.valueOf(row.mode) }.getOrDefault(GrantMode.Deny)
        return when (mode) {
            GrantMode.Deny -> GrantDecision.Denied("denied by grant")
            GrantMode.AllowUntil -> {
                val exp = row.expiry
                if (exp != null && exp < clock()) GrantDecision.Denied("grant expired") else GrantDecision.Allowed
            }
            GrantMode.AllowWhileUnlocked ->
                if (locked()) GrantDecision.Denied("device is locked") else GrantDecision.Allowed
            // Interactive per-request approval is plumbed with the approval flow;
            // until then it fails closed rather than silently allowing.
            GrantMode.AskEveryTime -> GrantDecision.Denied("approval required")
        }
    }
}
