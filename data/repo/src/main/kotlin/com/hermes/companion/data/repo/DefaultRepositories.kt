package com.hermes.companion.data.repo

import com.hermes.companion.common.reason
import com.hermes.companion.data.db.CompanionStore
import com.hermes.companion.data.db.GatewayEntity
import com.hermes.companion.data.db.MessageEntity
import com.hermes.companion.data.db.toDomain
import com.hermes.companion.data.db.toEntity
import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayHealth
import com.hermes.companion.domain.GatewayKind
import com.hermes.companion.net.httpHermesBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

internal class DefaultFleetRepository(
    private val store: CompanionStore,
    private val registry: BackendRegistry,
) : FleetRepository {

    override fun fleet(): Flow<Fleet> = combine(
        store.gateways.observeAll(),
        store.profiles.observeAll(),
        store.sessions.observeAll(),
    ) { gateways, profiles, sessions ->
        // Disambiguation happens at read time so handles stay correct as
        // gateways are added, removed, or fail.
        val disambiguated = registry.disambiguate(profiles.map { it.toDomain() })
        Fleet(
            loading = false,
            gateways = gateways.map { row ->
                GatewayView(
                    gateway = row.toDomain(),
                    connectivity = row.connectivity(),
                    profiles = disambiguated
                        .filter { it.gatewayId == row.id }
                        .map { profile ->
                            ProfileView(
                                profile = profile,
                                sessions = sessions
                                    .filter { it.gatewayId == row.id && it.profileId == profile.profileId }
                                    .map { it.toDomain() },
                            )
                        },
                )
            },
        )
    }

    override suspend fun refresh() {
        store.gateways.all().forEach { row -> refreshGateway(row) }
    }

    private suspend fun refreshGateway(row: GatewayEntity) {
        val backend = registry.backendFor(row.id) ?: return
        val now = System.currentTimeMillis()
        try {
            val profiles = backend.listProfiles()
            store.profiles.deleteForGateway(row.id)
            store.profiles.upsertAll(profiles.map { it.toEntity() })
            profiles.forEach { profile ->
                val sessions = backend.listSessionsForProfile(row.id, profile.profileId)
                store.sessions.upsertAll(sessions.map { it.toEntity(now) })
            }
            store.gateways.setHealth(row.id, GatewayHealth.Healthy.name, null, now, null)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            // The gateway keeps its cached profiles and sessions; only its
            // health changes. A dead gateway must not blank the roster.
            store.gateways.setHealth(row.id, GatewayHealth.Down.name, t.reason(), row.lastOkAt, row.staleSince ?: now)
        }
    }

    override suspend fun addGateway(
        label: String,
        baseUrl: String,
        kind: GatewayKind,
    ): Result<String> = runCatching {
        val cleaned = baseUrl.trim().trimEnd('/')
        require(cleaned.isNotEmpty()) { "URL is required" }
        val id = deriveGatewayId(cleaned)
        val gateway = GatewayConnection(
            id = id,
            label = label.ifBlank { id },
            kind = kind,
            baseUrl = cleaned,
            authRef = "none",
        )
        store.gateways.upsert(gateway.toEntity(health = GatewayHealth.Unknown))
        registry.addGateway(httpHermesBackend(gateway))
        store.gateways.find(id)?.let { refreshGateway(it) }
        id
    }

    override suspend fun forget(gatewayId: String): Result<Unit> = runCatching {
        registry.removeGateway(gatewayId)
        store.messages.deleteForGateway(gatewayId)
        store.runs.deleteForGateway(gatewayId)
        store.sessions.deleteForGateway(gatewayId)
        store.profiles.deleteForGateway(gatewayId)
        store.gateways.delete(gatewayId)
    }

    private fun deriveGatewayId(url: String): String {
        val segment = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
        return if (segment.startsWith("gw-")) segment else "gw-adhoc-" + Integer.toHexString(url.hashCode())
    }
}

internal class DefaultConversationRepository(
    private val store: CompanionStore,
    private val registry: BackendRegistry,
    private val tracker: RunTracker,
) : ConversationRepository {

    override fun conversation(route: ConversationRoute): Flow<ConversationState> = combine(
        store.messages.observeRoute(route.gatewayId, route.profileId, route.sessionId),
        store.runs.observeRoute(route.gatewayId, route.profileId, route.sessionId),
        store.gateways.observeAll().map { rows -> rows.firstOrNull { it.id == route.gatewayId } },
    ) { messages, runs, gateway ->
        val latest = runs.firstOrNull()
        val phase = latest?.let { RunPhase.parse(it.state) }
        ConversationState(
            route = route,
            gatewayLabel = gateway?.label ?: route.gatewayId,
            messages = messages.map { it.toDomain() },
            activeRun = latest?.let { RunView(it.runId, RunPhase.parse(it.state), it.error) },
            pendingApproval = if (phase == RunPhase.AwaitingApproval) latest.approvalJson.decodeApproval() else null,
            connectivity = gateway?.connectivity() ?: Connectivity.Unknown,
        )
    }

    override suspend fun refresh(route: ConversationRoute): Result<Unit> = runCatching {
        val backend = registry.backendFor(route.gatewayId) ?: error("no backend for ${route.gatewayId}")
        val remote = backend.listMessages(route)
        // Replace confirmed history only: a pending or streaming row is ours.
        store.messages.deleteConfirmed(route.gatewayId, route.profileId, route.sessionId)
        store.messages.insertAll(remote.map { it.toEntity() })
    }

    override suspend fun createSession(route: ConversationRoute, title: String): Result<com.hermes.companion.domain.Session> = runCatching {
        val backend = registry.backendFor(route.gatewayId) ?: error("no backend for ${route.gatewayId}")
        val session = backend.createSession(route, title)
        store.sessions.upsert(session.toEntity(System.currentTimeMillis()))
        session
    }

    override suspend fun submit(route: ConversationRoute, text: String): Result<String> {
        val backend = registry.backendFor(route.gatewayId)
            ?: return Result.failure(IllegalStateException("no backend for ${route.gatewayId}"))

        // The operator's own message lands immediately and stays marked pending
        // until the gateway confirms it. Step 5 turns this into a real outbox.
        val local = MessageEntity(
            id = UUID.randomUUID().toString(),
            gatewayId = route.gatewayId,
            profileId = route.profileId,
            sessionId = route.sessionId,
            role = "user",
            text = text,
            toolRunsJson = "",
            createdAt = System.currentTimeMillis(),
            runId = null,
            streaming = false,
            pending = true,
        )
        store.messages.upsert(local)

        return runCatching { backend.submit(route, text) }
            .onSuccess { runId ->
                store.messages.upsert(local.copy(pending = false, runId = runId))
                tracker.observe(route, runId)
            }
    }

    override suspend fun decide(
        route: ConversationRoute,
        runId: String,
        requestId: String,
        option: ApprovalOption,
    ): Result<Unit> = runCatching {
        val backend = registry.backendFor(route.gatewayId) ?: error("no backend for ${route.gatewayId}")
        backend.decideApproval(route, runId, requestId, option)
        // Re-observe: the original stream ended at run.approval_required, so
        // without this the run stays gated forever.
        tracker.observe(route, runId)
    }

    override suspend fun stop(route: ConversationRoute, runId: String): Result<Unit> = runCatching {
        val backend = registry.backendFor(route.gatewayId) ?: error("no backend for ${route.gatewayId}")
        backend.stopRun(route, runId)
    }
}

internal class DefaultActivityRepository(
    private val store: CompanionStore,
) : ActivityRepository {
    override fun observeActivity(): Flow<ActivityState> = combine(
        store.runs.observeAll(),
        store.gateways.observeAll(),
        store.messages.observeAll(),
    ) { runs, gateways, _ ->
        val queueSummaries = gateways.map { gw ->
            val isLive = gw.health == GatewayHealth.Healthy.name
            // Real queue depth / ack watermark arrive with the outbound table (Phase 2).
            val detail = if (isLive) "connected" else "unreachable: ${gw.error ?: "no response"}"
            QueueSummary(gw.id, detail, isLive)
        }

        val runItems = runs.map { run ->
            val phase = RunPhase.parse(run.state)
            val outcome = when (phase) {
                RunPhase.Streaming -> ActivityOutcome.Streaming
                RunPhase.AwaitingApproval -> ActivityOutcome.AwaitingApproval
                RunPhase.Completed -> ActivityOutcome.Completed
                RunPhase.Failed -> ActivityOutcome.Failed
            }
            val stage = when (phase) {
                RunPhase.Streaming -> 4
                RunPhase.AwaitingApproval -> 3
                RunPhase.Completed -> 5
                RunPhase.Failed -> 4
            }
            ActivityItem(
                id = run.runId,
                kind = ActivityKind.ChatRun,
                glyph = "⚡",
                title = "Run ${run.runId.take(8)} — ${run.profileId}",
                subtitle = "session: ${run.sessionId.takeLast(6)} · ${run.gatewayId}",
                routeDisplay = "${run.gatewayId} › @${run.profileId} › ${run.sessionId.takeLast(6)}",
                createdAt = run.updatedAt,
                outcome = outcome,
                stage = stage,
                detailTitle = "State: ${phase.name}",
                detailBody = if (run.error != null) "Error: ${run.error}" else "Run processed on gateway ${run.gatewayId}.",
                detailMeta = "${run.runId} · updated ${java.time.Instant.ofEpochMilli(run.updatedAt)}",
            )
        }


        ActivityState(
            items = runItems,
            queues = queueSummaries,
        )
    }
}

internal class DefaultNodeRepository : NodeRepository {
    // Honest placeholder until the node runtime + real Android adapters land
    // (Phase 5). It advertises NO capability it cannot actually serve, so the
    // Node screen shows an unpaired device rather than fabricated coverage.
    private val state = kotlinx.coroutines.flow.MutableStateFlow(
        NodeState(
            nodeName = "This device",
            nodeId = "",
            sequence = 0L,
            brokerStatus = "Not paired",
            batteryMode = "unknown",
            linkType = "none",
            capabilities = emptyList(),
            leases = emptyList(),
            privacyLog = emptyList(),
        )
    )

    override fun observeNodeState(): Flow<NodeState> = state

    override suspend fun runCanary(): Result<List<String>> = runCatching {
        state.value = state.value.copy(canaryRunning = true, canaryPassed = false)
        val steps = listOf(
            "Node is not paired yet — pair a gateway and enable Full Node Mode.",
            "The end-to-end canary runs once real capabilities are wired (Phase 5).",
        )
        state.value = state.value.copy(
            canaryRunning = false,
            canaryPassed = false,
            canarySteps = steps,
        )
        steps
    }
}

internal class DefaultOutboxRepository(
    private val store: CompanionStore,
    private val conversations: ConversationRepository,
) : OutboxRepository {
    override fun observeOutbox(): Flow<OutboxState> = store.messages.observeAll().map { messages ->
        // Real pending submissions only. The dedicated outbound table with
        // idempotency-key replay and the "unacknowledged" terminal state lands
        // in Phase 2; today a pending message row is the stand-in.
        val items = messages.filter { it.pending }.map { m ->
            OutboxItem(
                id = m.id,
                route = ConversationRoute(m.gatewayId, m.profileId, m.sessionId),
                routeDisplay = "${m.gatewayId} › @${m.profileId} › ${m.sessionId.takeLast(6)}",
                text = m.text,
                createdAt = m.createdAt,
                state = if (m.runId != null) "in flight" else "unacknowledged",
                needsDecision = m.runId == null,
            )
        }
        OutboxState(
            items = items,
            messagesHeld = items.size,
            maxMessages = 50,
        )
    }

    override suspend fun retrySubmission(id: String): Result<Unit> = runCatching {
        val msg = store.messages.observeAll().first().firstOrNull { it.id == id }
            ?: error("submission $id not found")
        val route = ConversationRoute(msg.gatewayId, msg.profileId, msg.sessionId)
        // Drop the stale local row, then resubmit its text. Phase 2 replaces
        // this with an idempotency-key replay against the outbound table.
        store.messages.delete(id)
        conversations.submit(route, msg.text).getOrThrow()
        Unit
    }

    override suspend fun dropSubmission(id: String): Result<Unit> = runCatching {
        store.messages.delete(id)
    }
}

internal fun GatewayEntity.connectivity(): Connectivity {
    val reason = error
    return when {
        // Never reached the gateway at all, versus reached it once and lost it.
        reason != null && lastOkAt == null -> Connectivity.Down(reason)
        reason != null -> Connectivity.Degraded(staleSince ?: System.currentTimeMillis(), reason)
        health == GatewayHealth.Healthy.name -> Connectivity.Live
        else -> Connectivity.Unknown
    }
}
