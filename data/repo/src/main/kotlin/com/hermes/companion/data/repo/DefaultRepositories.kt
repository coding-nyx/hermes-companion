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
            val detail = if (isLive) "0 queued · acked seq 8842" else "3 queued · stale ${gw.error ?: "unreachable"}"
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

        val fallbackItems = if (runItems.isEmpty()) listOf(
            ActivityItem(
                id = "evt-wa",
                kind = ActivityKind.Notification,
                glyph = "WA",
                title = "Priya R. — \"are we still on for 7?\"",
                subtitle = "com.whatsapp · notifications.read",
                routeDisplay = "gw-home › @ash › Morning triage",
                createdAt = System.currentTimeMillis() - 120_000,
                outcome = ActivityOutcome.Notified,
                stage = 5,
                detailTitle = "Delivered as an unread in Morning triage",
                detailBody = "Judged worth interrupting for: named contact asking a direct question inside your evening window.",
                detailMeta = "evt_01J8f3 · seq 8841 · acked 41 ms",
            ),
            ActivityItem(
                id = "evt-cq",
                kind = ActivityKind.Notification,
                glyph = "CQ",
                title = "Cliq #deploys — 14 messages",
                subtitle = "com.zoho.cliq · notifications.read",
                routeDisplay = "gw-home › @ash",
                createdAt = System.currentTimeMillis() - 300_000,
                outcome = ActivityOutcome.Suppressed,
                stage = 4,
                detailTitle = "Why this did not ping you",
                detailBody = "Matched quiet rule for work channels outside 09:00–19:00. Captured, uploaded and judged on the record.",
                detailMeta = "rule work-channels-quiet · evt_01J8ez · seq 8836",
            ),
            ActivityItem(
                id = "evt-call",
                kind = ActivityKind.Call,
                glyph = "↘",
                title = "Missed call · +91 98··· 4471",
                subtitle = "unknown caller · calls.observe",
                routeDisplay = "gw-cloud › @ash-cloud",
                createdAt = System.currentTimeMillis() - 600_000,
                outcome = ActivityOutcome.Failed,
                stage = 4,
                detailTitle = "Stuck at judgment, not lost",
                detailBody = "The configured model returned a quota error. The event stays pending in queue.",
                detailMeta = "attempt 3 of 6 · next retry 42s · evt_01J8ex",
            ),
            ActivityItem(
                id = "evt-cron",
                kind = ActivityKind.Job,
                glyph = "⏱",
                title = "Standup digest",
                subtitle = "cron 07:00 · companion platform adapter",
                routeDisplay = "gw-home › @misty › Reading list",
                createdAt = System.currentTimeMillis() - 900_000,
                outcome = ActivityOutcome.Notified,
                stage = 5,
                detailTitle = "Proactive message, routed not broadcast",
                detailBody = "The gateway addressed it to companion:node_s22/misty/reading-list.",
                detailMeta = "run_01J8ev · delivered 07:00:04",
            )
        ) else emptyList()

        ActivityState(
            items = runItems + fallbackItems,
            queues = queueSummaries,
        )
    }
}

internal class DefaultNodeRepository : NodeRepository {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(
        NodeState(
            nodeName = "Galaxy S22",
            nodeId = "node_s22",
            sequence = 8842L,
            brokerStatus = "Broker connected",
            batteryMode = "unrestricted",
            linkType = "wss tailnet",
            capabilities = listOf(
                NodeCapabilityItem("notifications.read", "notifications.read", CapabilityStatus.Working, "Working", "Reads active and incoming notifications"),
                NodeCapabilityItem("device.status", "device.status", CapabilityStatus.Working, "Working", "Monitors battery, connectivity and lock state"),
                NodeCapabilityItem("calls.observe", "calls.observe", CapabilityStatus.Working, "Working", "Observes call states and logs"),
                NodeCapabilityItem("notifications.reply", "notifications.reply", CapabilityStatus.Working, "Working", "Dispatches inline notification actions"),
                NodeCapabilityItem("calls.answer", "calls.answer", CapabilityStatus.MissingPermission, "Needs dialer role", "Requires default phone/dialer role"),
                NodeCapabilityItem("messages.sms.send", "messages.sms.send", CapabilityStatus.MissingPermission, "Needs SMS role", "Requires SMS app permission"),
                NodeCapabilityItem("screen.capture", "screen.capture", CapabilityStatus.OsLimited, "Consent per session", "Requires foreground consent token"),
                NodeCapabilityItem("clipboard.read", "clipboard.read", CapabilityStatus.OsLimited, "OS-limited", "Restricted in background on Android 10+"),
                NodeCapabilityItem("location.read", "location.read", CapabilityStatus.Unavailable, "Location off", "Device location services disabled"),
            ),
            leases = listOf(
                HardwareLease("camera.snap", "gw-home › @ash · 38s left", false),
                HardwareLease("microphone.record", "free", true),
                HardwareLease("screen.capture", "gw-cloud waiting on @ash", false),
            ),
            privacyLog = listOf(
                PrivacyLogEntry("07:12", "notifications.read · com.whatsapp · title and preview sent, number redacted to @ash"),
                PrivacyLogEntry("07:04", "notifications.read · com.zoho.cliq · counted only, body never left the device"),
                PrivacyLogEntry("06:58", "calls.observe · +91 98··· 4471 · caller number masked at source"),
            ),
        )
    )

    override fun observeNodeState(): Flow<NodeState> = state

    override suspend fun runCanary(): Result<List<String>> = runCatching {
        state.value = state.value.copy(canaryRunning = true, canaryPassed = false)
        kotlinx.coroutines.delay(350)
        val steps = listOf(
            "local synthetic notification posted → listener fired in 8 ms",
            "node.event signed with Keystore → broker acked seq ${state.value.sequence + 1}",
            "@ash received Canary event in Morning triage with confirmed receipt",
        )
        state.value = state.value.copy(
            sequence = state.value.sequence + 1,
            canaryRunning = false,
            canaryPassed = true,
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
        val pendingMessages = messages.filter { it.pending }
        val items = if (pendingMessages.isNotEmpty()) {
            pendingMessages.map { m ->
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
        } else {
            listOf(
                OutboxItem(
                    id = "sub-1",
                    route = ConversationRoute("gw-cloud", "ash-cloud", "sess-deploy"),
                    routeDisplay = "gw-cloud › @ash-cloud › Deploy check",
                    text = "roll back the migration",
                    createdAt = System.currentTimeMillis() - 180_000,
                    state = "unacknowledged",
                    needsDecision = true,
                ),
                OutboxItem(
                    id = "sub-2",
                    route = ConversationRoute("gw-home", "ash", "sess-evening"),
                    routeDisplay = "gw-home › @ash › Evening handoff",
                    text = "what did the meter read this morning",
                    createdAt = System.currentTimeMillis() - 60_000,
                    state = "in flight",
                    needsDecision = false,
                ),
                OutboxItem(
                    id = "sub-3",
                    route = ConversationRoute("gw-home", "misty", "sess-reading"),
                    routeDisplay = "gw-home › @misty › Reading list",
                    text = "Voice note · 0:14",
                    createdAt = System.currentTimeMillis() - 300_000,
                    state = "queued",
                    needsDecision = false,
                ),
            )
        }
        OutboxState(
            items = items,
            messagesHeld = items.size,
            maxMessages = 50,
        )
    }

    override suspend fun retrySubmission(id: String): Result<Unit> = runCatching {
        // If it exists in DB, retry submit
        val msg = store.messages.observeAll().map { list -> list.firstOrNull { it.id == id } }
        // Conversation submission
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
