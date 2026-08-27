package com.hermes.companion.data.repo

import android.content.Context
import com.hermes.companion.discovery.DiscoveryClient
import com.hermes.companion.discovery.evaluateTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Surfaces gateways found on the network (mDNS) plus live tailnet status. A
 * tailnet peer (100.64/10, *.ts.net) is full-tier even over cleartext because
 * WireGuard encrypts the transport.
 */
interface DiscoveryRepository {
    fun observeDiscovery(): Flow<DiscoveryUiState>
    /** Classify a manually-typed URL so the UI can warn about a limited tier. */
    fun tierOf(baseUrl: String): com.hermes.companion.domain.TransportTier
}

internal class DefaultDiscoveryRepository(context: Context) : DiscoveryRepository {
    private val client = DiscoveryClient(context.applicationContext)

    override fun observeDiscovery(): Flow<DiscoveryUiState> = client.discover().map { snap ->
        DiscoveryUiState(
            tailnetActive = snap.tailnet.active,
            tailnetAddress = snap.tailnet.address,
            gateways = snap.gateways.map {
                DiscoveredGatewayItem(it.label, it.host, it.port, it.baseUrl, it.tier, it.source)
            },
        )
    }

    override fun tierOf(baseUrl: String) = evaluateTier(baseUrl)
}
