package com.hermes.companion.data.repo

import com.hermes.companion.broker.BrokerConnectionState
import com.hermes.companion.broker.BrokerHello
import com.hermes.companion.broker.NodeBroker
import com.hermes.companion.broker.NodePairingClient
import com.hermes.companion.broker.nodePairingClient
import com.hermes.companion.broker.webSocketNodeBroker
import com.hermes.companion.broker.WireCap
import com.hermes.companion.data.db.CompanionStore
import com.hermes.companion.data.db.NodeIdentityEntity
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
    private val registry: AdapterRegistry,
    private val broker: NodeBroker,
) {
    fun run(scope: CoroutineScope): Job = scope.launch {
        broker.commands().collect { command ->
            val adapter = registry.forFamily(command.capability)
            val receipt = if (adapter == null) {
                Receipt(command.requestId, command.capability, ReceiptStatus.Refused, "unknown capability", "{}", System.currentTimeMillis())
            } else {
                runCatching { adapter.invoke(command) }.getOrElse { t ->
                    Receipt(command.requestId, command.capability, ReceiptStatus.Failed, t.message ?: "adapter error", "{}", System.currentTimeMillis())
                }
            }
            broker.sendReceipt(receipt)
        }
    }
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
        val broker = brokerFactory(row.brokerUrl, row.token) { helloFor(row.nodeId) }
        broker.start(scope)
        val jobs = listOf(
            NodeDispatcher(registry, broker).run(scope),
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
            store.nodeIdentity.upsert(
                NodeIdentityEntity(
                    gatewayId = gatewayId,
                    nodeId = r.nodeId,
                    brokerUrl = r.brokerUrl,
                    token = r.token,
                    expiresAt = r.expiresAt,
                    grantedCapsCsv = r.grantedCaps.joinToString(","),
                    pairedAt = System.currentTimeMillis(),
                ),
            )
            Unit
        }
    }

    suspend fun unpair(gatewayId: String): Result<Unit> = runCatching {
        stop(gatewayId)
        store.nodeIdentity.deleteForGateway(gatewayId)
    }
}
