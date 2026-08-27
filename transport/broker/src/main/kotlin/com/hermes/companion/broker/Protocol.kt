package com.hermes.companion.broker

import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.NodeEventFrame
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.SendOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The node-broker wire (`plan/02-contracts/edge-contract.md`). One JSON object
 * per frame, discriminated by [type], versioned, and idempotent: commands dedupe
 * by requestId on the phone, events dedupe by eventId on the gateway.
 */
@Serializable
data class WireFrame(
    val v: Int = 1,
    val type: String,
    // command (gateway -> phone)
    val requestId: String? = null,
    val capability: String? = null,
    val params: JsonObject? = null,
    val grantId: String? = null,
    val expiresAt: Long? = null,
    val profile: String? = null,
    // receipt (phone -> gateway)
    val status: String? = null,
    val detail: String? = null,
    val payload: JsonObject? = null,
    // node.event (phone -> gateway)
    val eventId: String? = null,
    val sequence: Long? = null,
    val sentAt: String? = null,
    // hello / presence
    val nodeId: String? = null,
    val caps: List<WireCap>? = null,
    val alive: Boolean? = null,
)

@Serializable
data class WireCap(
    val family: String,
    val health: String,
    val mutating: Boolean = false,
    val exclusive: Boolean = false,
)

object BrokerJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}

/** What the node advertises on connect. */
data class BrokerHello(val nodeId: String, val caps: List<WireCap>)

enum class BrokerConnectionState { Disconnected, Connecting, Connected }

/**
 * One node broker per gateway. [commands] delivers deduped invocations from the
 * gateway; [sendReceipt] returns the terminal receipt; [sendEvent] pushes a
 * captured device event and reports whether it was acknowledged.
 */
interface NodeBroker {
    val connection: StateFlow<BrokerConnectionState>
    fun commands(): Flow<NodeCommand>
    suspend fun sendReceipt(receipt: Receipt)
    suspend fun sendEvent(frame: NodeEventFrame): SendOutcome
    fun start(scope: CoroutineScope)
    fun stop()
}
