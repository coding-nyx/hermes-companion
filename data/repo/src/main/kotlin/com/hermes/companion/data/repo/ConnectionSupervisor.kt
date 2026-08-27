package com.hermes.companion.data.repo

import com.hermes.companion.common.reason
import com.hermes.companion.data.db.CompanionStore
import com.hermes.companion.data.db.GatewayEntity
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayHealth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** What the foreground notification reports. Honest counts, not "running". */
data class FleetStatus(
    val gateways: Int = 0,
    val live: Int = 0,
    val unreachable: Int = 0,
    val profiles: Int = 0,
    val openRuns: Int = 0,
) {
    val allDown: Boolean get() = gateways > 0 && live == 0
}

/**
 * One supervision loop per gateway, each with its own backoff. A gateway
 * failing cannot slow another down, and no gateway is polled on a global clock
 * — the structural version of §3's isolation rule.
 *
 * Implements `plan/10-architecture/runtime.md`. The broker and outbound pump
 * loops described there arrive with steps 5 and 7.
 */
class ConnectionSupervisor internal constructor(
    private val store: CompanionStore,
    private val registry: BackendRegistry,
    private val tracker: RunTracker,
) {
    val status: Flow<FleetStatus> = combine(
        store.gateways.observeAll(),
        store.profiles.observeAll(),
        store.runs.observeOpenRuns(),
    ) { gateways, profiles, openRuns ->
        FleetStatus(
            gateways = gateways.size,
            live = gateways.count { it.error == null && it.health == GatewayHealth.Healthy.name },
            unreachable = gateways.count { it.error != null },
            profiles = profiles.size,
            openRuns = openRuns.size,
        )
    }

    /**
     * Starts supervision, adding and dropping per-gateway children as the
     * registry changes. The returned job is cancelled by the service.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        // supervisorScope, not launch(SupervisorJob()): a failing gateway must
        // not cancel its siblings, but supervision as a whole must still stop
        // when the service's scope is cancelled. Passing a fresh SupervisorJob
        // would detach these loops from the service entirely.
        supervisorScope {
            val children = mutableMapOf<String, Job>()
            store.gateways.observeAll()
                .map { rows -> rows.map(GatewayEntity::id).toSet() }
                .distinctUntilChanged()
                .collect { ids ->
                    (children.keys - ids).toList().forEach { gone ->
                        children.remove(gone)?.cancel()
                    }
                    (ids - children.keys).forEach { id ->
                        children[id] = launch { supervise(id) }
                    }
                }
        }
    }

    private suspend fun supervise(gatewayId: String) {
        var failures = 0
        var firstPass = true
        while (currentCoroutineContext().isActive) {
            if (!firstPass) {
                // Consecutive failures drive the delay. Feeding a jittered
                // value back in would let one small random number reset the
                // growth, leaving a dead gateway probed twice a second forever.
                delay(if (failures == 0) HEALTHY_POLL_MS else backoffDelay(failures))
            }
            firstPass = false
            failures = if (probe(gatewayId)) 0 else failures + 1
        }
    }

    /**
     * Cheapest real call that proves the gateway answers *and* refreshes what
     * it can do. On success, any run still open is re-observed — reconciliation
     * asks only about outstanding work and never sends an all-clear.
     */
    private suspend fun probe(gatewayId: String): Boolean {
        val backend = registry.backendFor(gatewayId) ?: return false
        val row = store.gateways.find(gatewayId) ?: return false
        return try {
            backend.capabilities()
            val now = System.currentTimeMillis()
            store.gateways.setHealth(gatewayId, GatewayHealth.Healthy.name, null, now, null)
            store.runs.openRuns()
                .filter { it.gatewayId == gatewayId }
                .forEach { tracker.observe(ConversationRoute(it.gatewayId, it.profileId, it.sessionId), it.runId) }
            true
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            store.gateways.setHealth(
                gatewayId,
                GatewayHealth.Down.name,
                t.reason(),
                row.lastOkAt,
                row.staleSince ?: System.currentTimeMillis(),
            )
            false
        }
    }

    companion object {
        const val HEALTHY_POLL_MS = 30_000L
        const val FIRST_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}

/**
 * Ceiling for the nth consecutive failure, 1-based: doubling, then capped.
 * Derived from the failure count rather than the last delay, so jitter cannot
 * flatten the curve.
 */
internal fun backoffCeiling(failures: Int): Long {
    if (failures <= 1) return ConnectionSupervisor.FIRST_BACKOFF_MS
    var ceiling = ConnectionSupervisor.FIRST_BACKOFF_MS
    repeat(failures - 1) {
        ceiling *= 2
        if (ceiling >= ConnectionSupervisor.MAX_BACKOFF_MS) return ConnectionSupervisor.MAX_BACKOFF_MS
    }
    return ceiling
}

/**
 * Full jitter inside that ceiling, with a floor so a retry is never immediate.
 * Reset happens only on a successful exchange, never on a socket opening.
 */
internal fun backoffDelay(failures: Int, random: () -> Double = { Math.random() }): Long {
    val ceiling = backoffCeiling(failures)
    val floor = (ConnectionSupervisor.FIRST_BACKOFF_MS / 2).coerceAtMost(ceiling)
    return (ceiling * random()).toLong().coerceIn(floor, ceiling)
}
