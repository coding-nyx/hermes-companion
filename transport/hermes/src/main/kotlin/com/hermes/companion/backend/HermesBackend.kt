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
 * Abstract Hermes backend, one instance per gateway.
 *
 * Every method is parameterized with a [ConversationRoute] so the registry
 * can enforce per-(gateway, profile) state isolation.
 *
 * [runEvents] is deliberately separate from [submit]: a run outlives
 * the screen that started it, so it must be observable by run id alone after
 * an approval, a reconnect, or process death. See
 * `plan/10-architecture/transport.md`.
 */
interface HermesBackend {

    val gateway: GatewayConnection

    /** Capability discovery; per profile when multiplexed. */
    suspend fun capabilities(profile: String? = null): Map<String, Boolean>

    suspend fun listProfiles(): List<AgentProfile>

    /** List every session for the profile, agnostic of session id. */
    suspend fun listSessionsForProfile(gatewayId: String, profileId: String): List<Session>

    suspend fun createSession(route: ConversationRoute, title: String): Session

    suspend fun listMessages(route: ConversationRoute): List<Message>

    /**
     * Start a run and return its id. Deliberately does not stream: a run
     * outlives the screen that started it, so starting one and observing one
     * are separate operations.
     */
    suspend fun submit(route: ConversationRoute, text: String): String

    /**
     * Observe a run by id. Safe to call again after an approval decision, a
     * dropped connection, or process death.
     */
    fun runEvents(route: ConversationRoute, runId: String): Flow<RunEvent>

    suspend fun stopRun(route: ConversationRoute, runId: String)

    suspend fun decideApproval(
        route: ConversationRoute,
        runId: String,
        requestId: String,
        option: ApprovalOption,
    )
}
