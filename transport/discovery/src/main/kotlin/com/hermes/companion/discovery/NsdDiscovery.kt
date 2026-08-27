package com.hermes.companion.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.hermes.companion.domain.TransportTier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentHashMap

/** A gateway found on the network (before it is trusted). */
data class DiscoveredGateway(
    val label: String,
    val host: String,
    val port: Int,
    val baseUrl: String,
    val tier: TransportTier,
    val source: String,
)

/**
 * mDNS/NSD browsing on the current network. Discovery proves only that something
 * answered on an address — nothing is added until the user chooses it and, for a
 * node, the pairing phrase matches. Gateways advertise `_hermes-gw._tcp`.
 */
class NsdDiscovery(private val context: Context) {

    fun discover(serviceType: String = "_hermes-gw._tcp."): Flow<List<DiscoveredGateway>> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsd == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val found = ConcurrentHashMap<String, DiscoveredGateway>()
        trySend(emptyList())

        fun emit() { trySend(found.values.sortedBy { it.label }.toList()) }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val port = resolved.port
                        // A resolved mDNS peer is a `.local`/LAN host: cleartext to it is Full tier.
                        val base = "http://$host:$port"
                        found[resolved.serviceName] = DiscoveredGateway(
                            label = resolved.serviceName,
                            host = host,
                            port = port,
                            baseUrl = base,
                            tier = evaluateTier(base).let { if (it == TransportTier.Limited) TransportTier.Full else it },
                            source = "mDNS",
                        )
                        emit()
                    }
                })
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName); emit()
            }
        }

        runCatching { nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener) }
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }
}
