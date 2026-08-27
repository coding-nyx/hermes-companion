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
    suspend fun runCanary(): Result<List<String>>
}

interface OutboxRepository {
    fun observeOutbox(): Flow<OutboxState>
    suspend fun retrySubmission(id: String): Result<Unit>
    suspend fun dropSubmission(id: String): Result<Unit>
}


