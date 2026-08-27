package com.hermes.companion.discovery

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DiscoverySnapshot(
    val tailnet: TailnetStatus,
    val gateways: List<DiscoveredGateway>,
)

/**
 * The single discovery surface: mDNS results on the current network, folded
 * together with live tailnet status. Wide-area DNS-SD across a tailnet and the
 * manual host/port path layer on top of this (manual is the only route for SSH
 * tunnels, Cloud, and unusual ports).
 */
class DiscoveryClient(context: Context) {
    private val nsd = NsdDiscovery(context)

    fun discover(): Flow<DiscoverySnapshot> =
        nsd.discover().map { gateways -> DiscoverySnapshot(detectTailnet(), gateways) }

    fun tailnet(): TailnetStatus = detectTailnet()
}
