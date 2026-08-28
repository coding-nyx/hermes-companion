package com.hermes.companion.data.repo

import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayKind
import kotlinx.coroutines.flow.Flow

/**
 * The only surface the UI may call. Reads are Flows off the database; commands
 * return [Result] and never throw, which is what makes an unreachable gateway
 * a rendered state instead of a dead process.
 */
interface FleetRepository {
    fun fleet(): Flow<Fleet>
    suspend fun refresh()
    suspend fun addGateway(label: String, baseUrl: String, kind: GatewayKind): Result<String>
    suspend fun forget(gatewayId: String): Result<Unit>
    /** Observes the active gatewayId, or null if none is set. */
    fun observeActive(): Flow<String?>
    /**
     * Looks up the node_id this device registered as when paired to the
     * gateway identified by [gatewayId]. Returns null if the gateway isn't
     * paired (no node_identity row). The NLS uses this to POST notifications
     * to the gateway's /v1/notifications/incoming endpoint without doing a
     * DB join through `gateways`.
     */
    suspend fun observeActiveNodeId(gatewayId: String): String?

    /**
     * T2: Looks up the active profile id for the given gateway. Returns null
     * if no active profile is set; callers fall back to "first profile for
     * the gateway" or "ask the user".
     */
    suspend fun observeActiveProfileId(gatewayId: String): String?

    /**
     * T2: Mark the active profile for a gateway. Persists to the
     * active_gateway row's activeProfileId column. Use [setActive] to also
     * switch gateways atomically.
     */
    suspend fun setActiveProfile(gatewayId: String, profileId: String): Result<Unit>

    /**
     * Full active-gateway view as a single snapshot: id, baseUrl, nodeId, whenSet.
     * Use [observeActiveId] when you only need the gatewayId (most callers).
     */
    fun observeActiveFull(): Flow<com.hermes.companion.data.db.ActiveGatewayEntity?>
    /** Just the gatewayId - convenience over [observeActiveFull]. */
    fun observeActiveId(): Flow<String?>
    /**
     * Marks [gatewayId] as the active gateway. Replaces any prior selection.
     * url+nodeId are stored alongside so background services (NLS) can POST to
     * the gateway without doing a `gateways` join.
     */
    suspend fun setActive(gatewayId: String, url: String, nodeId: String): Result<Unit>
}

interface ConversationRepository {
    fun conversation(route: ConversationRoute): Flow<ConversationState>
    suspend fun refresh(route: ConversationRoute): Result<Unit>
    suspend fun createSession(route: ConversationRoute, title: String): Result<com.hermes.companion.domain.Session>
    /** Starts a run and returns its id. Observation happens through [conversation]. */
    suspend fun submit(route: ConversationRoute, text: String): Result<String>
    suspend fun decide(
        route: ConversationRoute,
        runId: String,
        requestId: String,
        option: ApprovalOption,
    ): Result<Unit>
    suspend fun stop(route: ConversationRoute, runId: String): Result<Unit>
}

interface ActivityRepository {
    fun observeActivity(): Flow<ActivityState>
}

interface NodeRepository {
    fun observeNodeState(): Flow<NodeState>
    fun observeSetup(): Flow<List<SetupRung>>
    fun observePairings(): Flow<List<NodePairing>>
    suspend fun pairNode(baseUrl: String, setupCode: String): Result<Unit>
    suspend fun unpairNode(gatewayId: String): Result<Unit>
    fun observeGrants(): Flow<List<NodeGrantItem>>
    suspend fun setGrant(gatewayId: String, nodeId: String, profileId: String, capability: String, mode: String): Result<Unit>
    fun observeStreamRules(): Flow<List<StreamRuleItem>>
    suspend fun setStreamRule(source: String, mode: String): Result<Unit>
    suspend fun runCanary(): Result<List<String>>
}

interface OutboxRepository {
    fun observeOutbox(): Flow<OutboxState>
    suspend fun retrySubmission(id: String): Result<Unit>
    suspend fun dropSubmission(id: String): Result<Unit>
}


