package com.hermes.companion.data.repo

import com.hermes.companion.data.db.CompanionStore
import com.hermes.companion.data.db.GatewayEntity
import com.hermes.companion.domain.GatewayHealth
import com.hermes.companion.domain.GatewayKind

/**
 * T8 (companion-gateway-routing.md): the Node tab's "Pair as node" flow
 * writes a [com.hermes.companion.data.db.NodeIdentityEntity] but historically
 * did NOT write a [GatewayEntity]. That left Settings -> Gateways empty even
 * when Node tab said "paired", and the [com.hermes.companion.broker.BackendRegistry]
 * had no entry to call [com.hermes.companion.data.repo.DefaultFleetRepository.refreshGateway]
 * against - so profiles and sessions never populated for paired gateways.
 *
 * This helper closes that gap. It's called from NodeConnection.pair after a
 * successful pair so the two stores stay coherent. The helper is intentionally
 * a free function (not a method on DefaultFleetRepository) so it can be tested
 * with a [FakeStore] and no DI graph.
 *
 * Idempotent: if a row already exists for [baseUrl] we leave it alone, so
 * pre-existing user data (label, auth, cached profiles) is never overwritten
 * just because the OS restarted and re-paired.
 *
 * @return the [GatewayEntity] that exists in the store after the call
 *   (either the freshly-written one or the pre-existing one).
 */
suspend fun ensureGatewayRowForPair(
    store: CompanionStore,
    baseUrl: String,
    gatewayId: String,
): GatewayEntity {
    val cleaned = baseUrl.trim().trimEnd('/')
    require(cleaned.isNotEmpty()) { "URL is required" }
    // Match DefaultFleetRepository.deriveGatewayId so the id is stable
    // whether the gateway was added via Settings -> Add Gateway OR via a
    // Node tab pair. Same URL -> same id.
    val id = deriveGatewayIdForPair(cleaned)
    // Lookup by URL (not by id) because pre-existing rows added via Settings
    // -> Add Gateway might use any id format. URL is the stable key for
    // "is this the same gateway as the one we just paired?".
    val existing = store.gateways.all().firstOrNull { it.url == cleaned }
    if (existing != null) return existing
    val row = GatewayEntity(
        id = id,
        label = cleaned,
        kind = GatewayKind.RemoteHttp.name,
        url = cleaned,
        authRef = "none",
        health = GatewayHealth.Unknown.name,
        lastOkAt = null,
        staleSince = null,
        error = null,
    )
    store.gateways.upsert(row)
    return row
}

/**
 * Stable id derived from URL. Mirrors
 * [com.hermes.companion.data.repo.DefaultFleetRepository.deriveGatewayId]
 * so the pair path and the Add Gateway path land on the same row.
 */
internal fun deriveGatewayIdForPair(url: String): String {
    val segment = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
    return if (segment.startsWith("gw-")) segment
    else "gw-adhoc-" + Integer.toHexString(url.hashCode())
}
