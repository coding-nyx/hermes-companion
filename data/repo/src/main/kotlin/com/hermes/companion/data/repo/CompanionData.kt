package com.hermes.companion.data.repo

import android.content.Context
import com.hermes.companion.data.db.openCompanionStore
import com.hermes.companion.data.db.toDomain
import com.hermes.companion.data.db.toEntity
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayHealth
import com.hermes.companion.net.httpHermesBackend
import kotlinx.coroutines.CoroutineScope

/**
 * Assembles the data layer. The [scope] outlives every screen and owns run
 * observation; step 4 replaces it with the foreground service's scope, at which
 * point runs also survive backgrounding.
 */
class CompanionData(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val store = openCompanionStore(context)
    private val registry = BackendRegistry(emptyList())
    private val tracker = RunTracker(scope, registry, store)

    val fleet: FleetRepository = DefaultFleetRepository(store, registry)
    val conversations: ConversationRepository = DefaultConversationRepository(store, registry, tracker)
    val activity: ActivityRepository = DefaultActivityRepository(store)
    val node: NodeRepository = DefaultNodeRepository()
    val outbox: OutboxRepository = DefaultOutboxRepository(store, conversations)

    /** Driven by the foreground service; see plan/10-architecture/runtime.md. */
    val supervisor: ConnectionSupervisor = ConnectionSupervisor(store, registry, tracker)

    /**
     * Hydrates transport from what is already stored, seeding the registry on
     * first run, then resumes any run that was open when we last stopped.
     */
    suspend fun bootstrap(seed: List<GatewayConnection>) {
        val existing = store.gateways.all().map { it.id }.toSet()
        seed.forEach { g ->
            if (g.id !in existing) {
                store.gateways.upsert(g.toEntity(health = GatewayHealth.Unknown))
            }
        }
        store.gateways.all().forEach { row ->
            if (registry.backendFor(row.id) == null) {
                registry.addGateway(httpHermesBackend(row.toDomain()))
            }
        }
        store.runs.openRuns().forEach { run ->
            tracker.observe(ConversationRoute(run.gatewayId, run.profileId, run.sessionId), run.runId)
        }
    }
}
