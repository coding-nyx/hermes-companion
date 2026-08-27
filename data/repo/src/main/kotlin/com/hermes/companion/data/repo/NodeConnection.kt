package com.hermes.companion.data.repo

import com.hermes.companion.broker.BrokerConnectionState
import com.hermes.companion.broker.BrokerHello
import com.hermes.companion.broker.NodeBroker
import com.hermes.companion.broker.NodePairingClient
import com.hermes.companion.broker.nodePairingClient
import com.hermes.companion.broker.webSocketNodeBroker
import com.hermes.companion.broker.WireCap
import com.hermes.companion.data.db.CompanionStore
import com.hermes.companion.data.db.GrantEntity
import com.hermes.companion.discovery.evaluateTier
import com.hermes.companion.domain.TransportTier
import com.hermes.companion.data.db.NodeIdentityEntity
import com.hermes.companion.domain.GrantMode
import com.hermes.companion.domain.NodeEventFrame
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.ReceiptStatus
import com.hermes.companion.node.AdapterRegistry
import com.hermes.companion.node.service.HermesNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Connects broker → adapters → receipts. Every command Hermes sends is resolved
 * by the matching adapter and answered with a receipt (a refusal is a receipt
 * too). Grant/lease gates slot in front of invoke() in a later step.
 */
internal class NodeDispatcher(
    private val gatewayId: String,
    private val nodeId: String,
    private val registry: AdapterRegistry,
    private val broker: NodeBroker,
    private val grants: GrantChecker,
    private val leases: LeaseManager,
) {
    fun run(scope: CoroutineScope): Job = scope.launch {
        broker.commands().collect { command -> broker.sendReceipt(dispatch(command)) }
    }

    // broker frame -> validate grant -> lease if exclusive -> invoke -> receipt.
    // Every gate refuses with a named reason, and a refusal is a receipt too.
    internal suspend fun dispatch(command: com.hermes.companion.domain.NodeCommand): Receipt {
        val adapter = registry.forFamily(command.capability)
            ?: return refuse(command, "unknown capability")

        val decision = grants.evaluate(gatewayId, command.profile, nodeId, command.capability)
        if (decision is GrantDecision.Denied) return refuse(command, decision.reason)

        if (!adapter.exclusive) return invoke(adapter, command)

        val lease = leases.acquire(command.capability, gatewayId, command.profile, command.requestId, ttlMs = 30_000)
        return when (lease) {
            is com.hermes.companion.domain.LeaseResult.Held -> refuse(
                command,
                "held by ${lease.by.gatewayId}/@${lease.by.profileId} until ${lease.until}",
            )
            is com.hermes.companion.domain.LeaseResult.Acquired -> try {
                invoke(adapter, command)
            } finally {
                leases.release(command.capability, command.requestId)
            }
        }
    }

    private suspend fun invoke(adapter: com.hermes.companion.node.CapabilityAdapter, command: com.hermes.companion.domain.NodeCommand): Receipt =
        runCatching { adapter.invoke(command) }.getOrElse { t ->
            Receipt(command.requestId, command.capability, ReceiptStatus.Failed, t.message ?: "adapter error", "{}", System.currentTimeMillis())
        }

    private fun refuse(command: com.hermes.companion.domain.NodeCommand, reason: String) =
        Receipt(command.requestId, command.capability, ReceiptStatus.Refused, reason, "{}", System.currentTimeMillis())
}

/**
 * Pushes captured device events to the gateway. Minimal today: it forwards new
 * notification postings (titles only) with a monotonic sequence. Redaction,
 * rate-limiting and durable receipts arrive with the node runtime.
 */
internal class NodeEventPump(
    private val nodeId: String,
    private val gatewayId: String,
    private val broker: NodeBroker,
) {
    private val seq = AtomicLong(0)
    fun run(scope: CoroutineScope): Job = scope.launch {
        var seen = emptySet<String>()
        while (isActive) {
            if (HermesNotificationListenerService.isConnected()) {
                val snapshot = HermesNotificationListenerService.activeSnapshot()
                val fresh = snapshot.filter { it.key !in seen }
                fresh.forEach { n ->
                    broker.sendEvent(
                        NodeEventFrame(
                            eventId = "evt_" + UUID.randomUUID().toString().take(12),
                            gatewayId = gatewayId,
                            profile = "",
                            nodeId = nodeId,
                            sequence = seq.incrementAndGet(),
                            sentAt = java.time.Instant.ofEpochMilli(n.postedAt).toString(),
                            capability = "notifications.read",
                            payload = """{"package":"${n.packageName}","title":${quote(n.title)}}""",
                        ),
                    )
                }
                seen = snapshot.map { it.key }.toSet()
            }
            delay(4_000)
        }
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""
}

/**
 * Owns one broker per paired node. Observes node_identity, opening a broker +
 * dispatcher + event pump for each row and tearing them down when the row goes.
 * Lives on the foreground service scope so a node keeps answering in the
 * background. Broker construction is injectable for tests.
 */
class NodeConnectionManager internal constructor(
    private val store: CompanionStore,
    private val registry: AdapterRegistry,
    private val pairingClient: NodePairingClient = nodePairingClient(),
    private val brokerFactory: (url: String, token: String, hello: () -> BrokerHello) -> NodeBroker =
        { url, token, hello -> webSocketNodeBroker(url, token, hello) },
) {
    private data class Live(val broker: NodeBroker, val jobs: List<Job>)

    private val grantChecker = GrantChecker(store)
    private val leaseManager = LeaseManager(store)

    /** Exposed so the Node screen can render live leases with holder + expiry. */
    fun leases() = leaseManager.observe()

    private val live = ConcurrentHashMap<String, Live>()
    private val _connections = MutableStateFlow<Map<String, BrokerConnectionState>>(emptyMap())
    val connections: StateFlow<Map<String, BrokerConnectionState>> = _connections.asStateFlow()

    fun start(scope: CoroutineScope): Job = scope.launch {
        store.nodeIdentity.observeAll().collect { rows ->
            val wanted = rows.associateBy { it.gatewayId }
            // Drop nodes whose row disappeared.
            (live.keys - wanted.keys).forEach { stop(it) }
            // Connect newly-appeared nodes.
            wanted.forEach { (gatewayId, row) -> if (!live.containsKey(gatewayId)) connect(scope, row) }
        }
    }

    private fun connect(scope: CoroutineScope, row: NodeIdentityEntity) {
        // A gateway that dropped to a limited tier loses its node session.
        if (evaluateTier(row.brokerUrl) == TransportTier.Limited) return
        val broker = brokerFactory(row.brokerUrl, row.token) { helloFor(row.nodeId) }
        broker.start(scope)
        val jobs = listOf(
            NodeDispatcher(row.gatewayId, row.nodeId, registry, broker, grantChecker, leaseManager).run(scope),
            NodeEventPump(row.nodeId, row.gatewayId, broker).run(scope),
            scope.launch {
                broker.connection.collect { state ->
                    _connections.value = _connections.value + (row.gatewayId to state)
                }
            },
        )
        live[row.gatewayId] = Live(broker, jobs)
    }

    private fun stop(gatewayId: String) {
        live.remove(gatewayId)?.let { l ->
            l.jobs.forEach { it.cancel() }
            l.broker.stop()
        }
        _connections.value = _connections.value - gatewayId
    }

    private fun helloFor(nodeId: String): BrokerHello = BrokerHello(
        nodeId = nodeId,
        caps = registry.coverage().map { cov ->
            WireCap(
                family = cov.capability.family,
                health = cov.health.name,
                mutating = cov.capability.mutating,
                exclusive = cov.capability.exclusive,
            )
        },
    )

    suspend fun pair(baseUrl: String, setupCode: String): Result<Unit> {
        // Transport tier caps a node session: no pairing over untrusted cleartext.
        if (evaluateTier(baseUrl) == TransportTier.Limited) {
            return Result.failure(
                IllegalStateException("Node pairing needs TLS, LAN/.local, an emulator, or a Tailscale address — this URL is limited tier."),
            )
        }
        val requested = registry.all().map { it.capability.family }
        return pairingClient.pair(
            baseUrl = baseUrl,
            setupCode = setupCode,
            publicKey = "poc-" + UUID.randomUUID().toString().take(16), // Ed25519 in Phase 10
            nodeName = android.os.Build.MODEL ?: "Android node",
            nodeModel = android.os.Build.MODEL ?: "",
            requestedCaps = requested,
        ).mapCatching { r ->
            val gatewayId = "node-" + Integer.toHexString(baseUrl.hashCode())
            val now = System.currentTimeMillis()
            store.nodeIdentity.upsert(
                NodeIdentityEntity(
                    gatewayId = gatewayId,
                    nodeId = r.nodeId,
                    brokerUrl = r.brokerUrl,
                    token = r.token,
                    expiresAt = r.expiresAt,
                    grantedCapsCsv = r.grantedCaps.joinToString(","),
                    pairedAt = now,
                ),
            )
            // Seed a default grant per granted capability (profile "" = any),
            // mode AllowWhileUnlocked. The Grants screen lets Nyx tighten each.
            store.grants.upsertAll(
                r.grantedCaps.map { cap ->
                    GrantEntity(gatewayId, "", r.nodeId, cap, GrantMode.AllowWhileUnlocked.name, null, null, now)
                },
            )
            Unit
        }
    }

    suspend fun unpair(gatewayId: String): Result<Unit> = runCatching {
        stop(gatewayId)
        store.nodeIdentity.deleteForGateway(gatewayId)
        store.grants.deleteForGateway(gatewayId)
        store.leases.deleteForGateway(gatewayId)
    }
}
