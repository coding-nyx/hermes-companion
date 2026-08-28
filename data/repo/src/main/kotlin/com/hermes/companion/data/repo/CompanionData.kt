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
    private val store = openCompanionStore(context)
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
        nodeConnections.setRefreshHook { gatewayId ->
            scope.launch {
                val repo = fleet as? DefaultFleetRepository
                if (repo == null) {
                    Log.w("PairFlow", "POST-PAIR: fleet is not DefaultFleetRepository (${fleet::class.java.name}); skipping refresh")
                    return@launch
                }
                // Register the backend if this is a freshly-paired gateway
                // that bootstrap() has not seen yet. Idempotent: addGateway
                // overwrites the existing entry for the same id.
                val existing = store.gateways.find(gatewayId)
                if (existing != null && registry.backendFor(gatewayId) == null) {
                    Log.d("PairFlow", "POST-PAIR: registering backend for $gatewayId (${existing.url})")
                    registry.addGateway(httpHermesBackend(existing.toDomain()))
                } else {
                    Log.d("PairFlow", "POST-PAIR: backend already registered for $gatewayId (${existing?.url ?: "row-missing"})")
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
     * Hydrates transport from what is already stored, seeding the registry on
     * first run, then resumes any run that was open when we last stopped.
     */
    suspend fun bootstrap(seed: List<GatewayConnection>) {
        // Seed only on a truly empty store, so user deletions and gateways added
        // through Discovery/Settings are never resurrected on the next launch.
        if (store.gateways.all().isEmpty()) {
            seed.forEach { g -> store.gateways.upsert(g.toEntity(health = g.health)) }
        }
        store.gateways.all().forEach { row ->
            if (registry.backendFor(row.id) == null) {
                registry.addGateway(backendFor(row.toDomain()))
            }
        }
        store.runs.openRuns().forEach { run ->
            tracker.observe(ConversationRoute(run.gatewayId, run.profileId, run.sessionId), run.runId)
        }
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
}
