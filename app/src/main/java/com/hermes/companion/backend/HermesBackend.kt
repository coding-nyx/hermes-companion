package com.hermes.companion.backend

import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.Message
import com.hermes.companion.domain.RunEvent
import com.hermes.companion.domain.Session
import kotlinx.coroutines.flow.Flow

/**
 * Abstract Hermes backend. Implementations include a real HTTP-backed one
 * (future work) and an in-process [MockHermesBackend] used by the PoC.
 *
 * Every method is parameterized with a [ConversationRoute] so the registry
 * can enforce per-(gateway, profile) state isolation.
 */
interface HermesBackend {

    val gateway: GatewayConnection

    /** Capability discovery; per profile when multiplexed. */
    suspend fun capabilities(profile: String? = null): Map<String, Boolean>

    suspend fun listProfiles(): List<AgentProfile>

    /** List every session for the profile, agnostic of session id. */
    suspend fun listSessionsForProfile(gatewayId: String, profileId: String): List<Session>

    suspend fun listMessages(route: ConversationRoute): List<Message>

    /**
     * Send a user message and stream the run as [RunEvent]s. The backend
     * owns the run lifecycle; the client only observes.
     */
    fun sendAndStream(route: ConversationRoute, text: String): Flow<RunEvent>

    suspend fun stopRun(route: ConversationRoute, runId: String)

    suspend fun decideApproval(
        route: ConversationRoute,
        requestId: String,
        option: ApprovalOption,
    )
}
