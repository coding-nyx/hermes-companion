package com.hermes.companion.data.repo

import android.content.Context
import android.util.Log
import com.hermes.companion.data.db.openCompanionStore
import com.hermes.companion.data.db.toDomain
import com.hermes.companion.data.db.toEntity
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayHealth
import com.hermes.companion.net.httpHermesBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Assembles the data layer. The [scope] outlives every screen and owns run
 * observation; step 4 replaces it with the foreground service's scope, at which
 * point runs also survive backgrounding.
 */
class CompanionData(
    context: Context,
    private val scope: CoroutineScope,
    private val gate: com.hermes.companion.common.BiometricGate = com.hermes.companion.common.AllowAllGate,
) {
    private val store = com.hermes.companion.data.db.openCompanionStore(context.also {
        Log.i("BootSequence", "openCompanionStore: building Room database (this is the first store access)")
    })
    private val adapters = com.hermes.companion.node.defaultAdapterRegistry(context)
    val nodeConnections = NodeConnectionManager(store, adapters)

    /** Registers the Shizuku binder listeners; safe when Shizuku is absent. */
    fun installElevatedTier() = com.hermes.companion.node.installElevatedTier()
    private val registry = BackendRegistry(emptyList())
    private val tracker = RunTracker(scope, registry, store)

    val fleet: FleetRepository = DefaultFleetRepository(store, registry)
    val conversations: ConversationRepository = DefaultConversationRepository(store, registry, tracker, gate)
    val activity: ActivityRepository = DefaultActivityRepository(store)
    val node: NodeRepository = DefaultNodeRepository(context, adapters, store, nodeConnections, gate)
    val discovery: DiscoveryRepository = DefaultDiscoveryRepository(context)
    val outbox: OutboxRepository = DefaultOutboxRepository(store, registry, tracker)

    /** Per-app notification routing rules (T5B; in-memory for v0.2). */
    val notificationRules: NotificationRuleRepository = InMemoryNotificationRuleRepository()

    /** Driven by the foreground service; see plan/10-architecture/runtime.md. */
    val supervisor: ConnectionSupervisor = ConnectionSupervisor(store, registry, tracker)

    init {
        // T8: a successful Node tab -> "Pair as node" flow writes a
        // node_identity row and (now) a gateways row. Fire-and-forget a
        // profiles/sessions refresh for the just-added gateway so the
        // user sees their Profiles populate immediately.
        //
        // DefaultFleetRepository.refreshGateway() runs on the supplied
        // CoroutineScope so the pair call itself doesn't block on the
        // HTTP GET /v1/profiles round-trip. Failures are swallowed inside
        // refreshGateway (it just sets health=Down).
        //
        // CRITICAL (T2fix): the BackendRegistry is only seeded from
        // bootstrap() once at app start. A fresh gateways row written by
        // NodeConnection.pair() is therefore unknown to the registry, and
        // refreshGateway() short-circuits on `registry.backendFor(id) ?: return`,
        // so the /api/profiles HTTP call never fires and the profiles table
        // stays empty. The hook must register the backend BEFORE refreshing.
        //
        // CRITICAL (T2fix2): the gateways row is written inside the pair()
        // coroutine just before invoke() runs. A subsequent store.gateways.find()
        // on the same coroutine that just wrote the row can, in rare race
        // conditions (e.g. write-ahead log not yet checkpointed, Room
        // invalidation tracker lag, or a separate dispatcher), return null
        // and short-circuit the refresh. The hook therefore retries find()
        // with a short bounded backoff so the post-pair refresh survives
        // the visible-row race without the user having to manually hit
        // "Refresh" on the Gateways tab.
        nodeConnections.setRefreshHook { gatewayId ->
            scope.launch {
                val repo = fleet as? DefaultFleetRepository
                if (repo == null) {
                    Log.w("PairFlow", "POST-PAIR: fleet is not DefaultFleetRepository (${fleet::class.java.name}); skipping refresh")
                    return@launch
                }
                // Wait for the gateway row to be visible. We just wrote it
                // ourselves in NodeConnection.pair(), but Room reads from a
                // separate connection and the WAL might not be visible to a
                // second reader immediately. Bounded retry (5 × 200ms = 1s
                // budget) is plenty: the row was written microseconds ago
                // on the same process.
                val row = waitForGatewayRow(gatewayId)
                if (row == null) {
                    Log.e(
                        "PairFlow",
                        "POST-PAIR: gateway row never landed in DB after 1s for $gatewayId; skipping refresh. " +
                            "Operator: check Database Inspector for the gateways table.",
                    )
                    return@launch
                }
                // Register the backend if this is a freshly-paired gateway
                // that bootstrap() has not seen yet. Idempotent: addGateway
                // overwrites the existing entry for the same id.
                if (registry.backendFor(gatewayId) == null) {
                    Log.d("PairFlow", "POST-PAIR: registering backend for $gatewayId (${row.url})")
                    registry.addGateway(httpHermesBackend(row.toDomain()))
                } else {
                    Log.d("PairFlow", "POST-PAIR: backend already registered for $gatewayId (${row.url})")
                }
                Log.d("PairFlow", "POST-PAIR: firing refreshGatewayFor($gatewayId)")
                try {
                    repo.refreshGatewayFor(gatewayId)
                    Log.d("PairFlow", "POST-PAIR: refresh complete for $gatewayId")
                } catch (t: Throwable) {
                    Log.e("PairFlow", "POST-PAIR: refresh failed for $gatewayId", t)
                }
            }
        }
    }

    /**
     * Poll [store.gateways.find] for up to ~1s waiting for the row to be
     * visible. Returns null only if the row never lands within the budget.
     *
     * Each attempt is a real Room SELECT, so Room's invalidation tracker
     * sees the row the instant the writer's transaction commits and the
     * retry loop terminates on attempt 1 in the common case.
     */
    private suspend fun waitForGatewayRow(gatewayId: String): com.hermes.companion.data.db.GatewayEntity? {
        repeat(MAX_REFRESH_WAIT_ATTEMPTS) { attempt ->
            val row = store.gateways.find(gatewayId)
            if (row != null) {
                if (attempt > 0) {
                    Log.d("PairFlow", "POST-PAIR: gateway row visible on attempt ${attempt + 1}/$MAX_REFRESH_WAIT_ATTEMPTS")
                }
                return row
            }
            Log.d("PairFlow", "POST-PAIR: gateway row not yet visible (attempt ${attempt + 1}/$MAX_REFRESH_WAIT_ATTEMPTS)")
            delay(REFRESH_WAIT_INTERVAL_MS)
        }
        return null
    }

    /**
     * Hydrates transport from what is already stored, seeding the registry on
     * first run, then resumes any run that was open when we last stopped.
     */
    suspend fun bootstrap(seed: List<GatewayConnection>) {
        Log.i("BootSequence", "bootstrap: first DAO access (store.gateways.all())")
        // Seed only on a truly empty store, so user deletions and gateways added
        // through Discovery/Settings are never resurrected on the next launch.
        val existing = store.gateways.all()
        Log.i("BootSequence", "bootstrap: store.gateways.all() returned ${existing.size} rows")
        if (existing.isEmpty()) {
            seed.forEach { g -> store.gateways.upsert(g.toEntity(health = g.health)) }
        }
        existing.forEach { row ->
            if (registry.backendFor(row.id) == null) {
                registry.addGateway(backendFor(row.toDomain()))
            }
        }
        store.runs.openRuns().forEach { run ->
            tracker.observe(ConversationRoute(run.gatewayId, run.profileId, run.sessionId), run.runId)
        }
        Log.i("BootSequence", "bootstrap: ${existing.size} backend(s) registered from store")
    }

    /**
     * Backend selection by URL scheme. A `mock://` gateway is served by an
     * in-process [MockHermesBackend] — the PoC demo fleet, holding no tokens
     * and reaching no network (`plan/08-delivery/poc-scope.md`). Everything
     * else is the real HTTP/SSE backend. Profiles for a mock gateway are read
     * from the `profiles` query, e.g. `mock://home?profiles=ash,misty`.
     */
    private fun backendFor(g: GatewayConnection): com.hermes.companion.backend.HermesBackend =
        if (g.baseUrl.startsWith("mock://")) {
            val profiles = g.baseUrl.substringAfter("profiles=", "")
                .substringBefore('&')
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf("default") }
            com.hermes.companion.backend.MockHermesBackend(g, profiles)
        } else {
            httpHermesBackend(g)
        }

    private companion object {
        const val MAX_REFRESH_WAIT_ATTEMPTS = 5
        const val REFRESH_WAIT_INTERVAL_MS = 200L
    }
}
