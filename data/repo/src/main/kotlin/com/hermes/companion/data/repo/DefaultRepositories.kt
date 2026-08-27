package com.hermes.companion.data.repo

import com.hermes.companion.common.reason
import com.hermes.companion.data.db.CompanionStore
import com.hermes.companion.data.db.GatewayEntity
import com.hermes.companion.data.db.MessageEntity
import com.hermes.companion.domain.SubmissionState
import com.hermes.companion.domain.Submission
import com.hermes.companion.data.db.OutboundEntity
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

        val id = UUID.randomUUID().toString()
        val key = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Journal to the durable outbox BEFORE the network. The display bubble
        // shares the submission id so the two stay correlated.
        val submission = Submission(
            id = id,
            gatewayId = route.gatewayId,
            profileId = route.profileId,
            sessionId = route.sessionId,
            text = text,
            idempotencyKey = key,
            createdAt = now,
            state = SubmissionState.Queued,
        )
        store.outbound.upsert(submission.toEntity())

        val local = MessageEntity(
            id = id,
            gatewayId = route.gatewayId,
            profileId = route.profileId,
            sessionId = route.sessionId,
            role = "user",
            text = text,
            toolRunsJson = "",
            createdAt = now,
            runId = null,
            streaming = false,
            pending = true,
        )
        store.messages.upsert(local)

        return runCatching { backend.submit(route, text, key) }
            .onSuccess { runId ->
                store.outbound.upsert(
                    submission.copy(state = SubmissionState.Acknowledged, runId = runId, attempts = 1).toEntity(),
                )
                store.messages.upsert(local.copy(pending = false, runId = runId))
                tracker.observe(route, runId)
            }
            .onFailure { t ->
                // Written and transmitted with no confirmed run id: ambiguous,
                // never silently retried. The operator decides in the Outbox.
                store.outbound.upsert(
                    submission.copy(state = SubmissionState.Unacknowledged, attempts = 1)
                        .toEntity(lastError = t.reason()),
                )
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

internal class DefaultNodeRepository(
    private val context: android.content.Context,
    private val registry: com.hermes.companion.node.AdapterRegistry,
    private val store: com.hermes.companion.data.db.CompanionStore,
    private val connections: NodeConnectionManager,
) : NodeRepository {

    override fun observePairings(): Flow<List<NodePairing>> =
        combine(store.nodeIdentity.observeAll(), connections.connections) { rows, states ->
            rows.map { row ->
                NodePairing(
                    gatewayId = row.gatewayId,
                    nodeId = row.nodeId,
                    brokerUrl = row.brokerUrl,
                    connected = states[row.gatewayId] == com.hermes.companion.broker.BrokerConnectionState.Connected,
                    grantedCaps = row.grantedCapsCsv.split(",").filter { it.isNotBlank() },
                )
            }
        }

    override suspend fun pairNode(baseUrl: String, setupCode: String): Result<Unit> =
        connections.pair(baseUrl, setupCode)

    override suspend fun unpairNode(gatewayId: String): Result<Unit> =
        connections.unpair(gatewayId)

    override fun observeSetup(): Flow<List<SetupRung>> =
        combine(ticker, canary) { _, _ ->
            com.hermes.companion.node.nodeRequirements(context).map { r ->
                SetupRung(
                    id = r.id,
                    kind = r.kind,
                    label = r.label,
                    detail = r.detail,
                    satisfied = r.satisfied,
                    target = r.target,
                    enablesCount = r.enablesCount,
                )
            }
        }

    private val canary = kotlinx.coroutines.flow.MutableStateFlow(CanaryState())

    private val ticker = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(3_000)
        }
    }

    override fun observeNodeState(): Flow<NodeState> =
        combine(ticker, canary) { _, c -> buildState(c) }

    override suspend fun runCanary(): Result<List<String>> = runCatching {
        canary.value = CanaryState(running = true)
        val steps = mutableListOf<String>()
        val notif = registry.forFamily("notifications.read")
        val listenerOk = notif?.health() == com.hermes.companion.domain.CapabilityHealth.Working
        steps += if (listenerOk) {
            "notification listener connected → live snapshot available"
        } else {
            "notification access NOT granted — enable it in Full Node Mode"
        }
        val status = registry.forFamily("device.status")
        steps += if (status?.health() == com.hermes.companion.domain.CapabilityHealth.Working) {
            "device.status readable → battery and network reported"
        } else {
            "device.status unavailable"
        }
        steps += "broker delivery pending — node pairing lands in a later step"
        canary.value = CanaryState(running = false, passed = listenerOk, steps = steps)
        steps
    }

    private fun buildState(c: CanaryState): NodeState {
        val coverage = registry.coverage()
        val ds = (registry.forFamily("device.status")
            as? com.hermes.companion.node.adapters.DeviceStatusAdapter)?.snapshot()
        return NodeState(
            nodeName = android.os.Build.MODEL ?: "This device",
            nodeId = "",
            sequence = 0L,
            brokerStatus = "Not paired",
            batteryMode = ds?.let { "${it.batteryPercent}%" + if (it.charging) " · charging" else "" } ?: "unknown",
            linkType = ds?.network ?: "none",
            capabilities = coverage.map { cov ->
                NodeCapabilityItem(
                    id = cov.capability.family,
                    name = cov.capability.family,
                    status = cov.health.toStatus(),
                    stateLabel = cov.detail,
                    description = describeCapability(cov.capability.family),
                )
            },
            leases = emptyList(),
            privacyLog = emptyList(),
            canaryRunning = c.running,
            canaryPassed = c.passed,
            canarySteps = c.steps,
        )
    }
}

private data class CanaryState(
    val running: Boolean = false,
    val passed: Boolean = false,
    val steps: List<String> = emptyList(),
)

private fun com.hermes.companion.domain.CapabilityHealth.toStatus(): CapabilityStatus = when (this) {
    com.hermes.companion.domain.CapabilityHealth.Working -> CapabilityStatus.Working
    com.hermes.companion.domain.CapabilityHealth.PermissionMissing -> CapabilityStatus.MissingPermission
    com.hermes.companion.domain.CapabilityHealth.OsLimited -> CapabilityStatus.OsLimited
    com.hermes.companion.domain.CapabilityHealth.Unavailable -> CapabilityStatus.Unavailable
}

private fun describeCapability(family: String): String = when (family) {
    "device.status" -> "Battery, network and charging state"
    "notifications.read" -> "Reads active and incoming notifications"
    else -> family
}

internal class DefaultOutboxRepository(
    private val store: CompanionStore,
    private val registry: BackendRegistry,
    private val tracker: RunTracker,
) : OutboxRepository {

    override fun observeOutbox(): Flow<OutboxState> = store.outbound.observeAll().map { rows ->
        val items = rows
            .filter { it.state != SubmissionState.Acknowledged.name && it.state != SubmissionState.Expired.name }
            .map { it.toItem() }
        OutboxState(items = items, messagesHeld = items.size, maxMessages = 50)
    }

    override suspend fun retrySubmission(id: String): Result<Unit> = runCatching {
        val row = store.outbound.find(id) ?: error("submission $id not found")
        val backend = registry.backendFor(row.gatewayId) ?: error("no backend for ${row.gatewayId}")
        val route = ConversationRoute(row.gatewayId, row.profileId, row.sessionId)
        // Replay under the SAME idempotency key: a gateway that already saw this
        // submission returns the original run id instead of creating a duplicate.
        val runId = backend.submit(route, row.text, row.idempotencyKey)
        store.outbound.upsert(
            row.copy(state = SubmissionState.Acknowledged.name, runId = runId, attempts = row.attempts + 1, lastError = null),
        )
        store.messages.upsert(
            MessageEntity(
                id = row.id,
                gatewayId = row.gatewayId,
                profileId = row.profileId,
                sessionId = row.sessionId,
                role = "user",
                text = row.text,
                toolRunsJson = "",
                createdAt = row.createdAt,
                runId = runId,
                streaming = false,
                pending = false,
            ),
        )
        tracker.observe(route, runId)
        Unit
    }

    override suspend fun dropSubmission(id: String): Result<Unit> = runCatching {
        store.outbound.delete(id)
        store.messages.delete(id)
    }

    private fun OutboundEntity.toItem(): OutboxItem {
        val st = runCatching { SubmissionState.valueOf(state) }.getOrDefault(SubmissionState.Queued)
        val display = when (st) {
            SubmissionState.Acknowledged -> "acked"
            SubmissionState.Sent -> "in flight"
            SubmissionState.Queued -> "queued"
            SubmissionState.Unacknowledged -> "no answer"
            SubmissionState.Failed -> "failed"
            SubmissionState.Expired -> "expired"
        }
        return OutboxItem(
            id = id,
            route = ConversationRoute(gatewayId, profileId, sessionId),
            routeDisplay = "$gatewayId › @$profileId › ${sessionId.takeLast(6)}",
            text = text,
            createdAt = createdAt,
            state = display,
            needsDecision = st == SubmissionState.Unacknowledged || st == SubmissionState.Failed,
        )
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
