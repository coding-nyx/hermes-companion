package com.hermes.companion.discovery

import com.hermes.companion.domain.TransportTier
import java.net.NetworkInterface

/**
 * Reachability and privilege are separate questions
 * (`plan/04-connection/gateway-registry.md`). The transport caps what a
 * connection may ever be granted:
 *
 * - TLS (`https`/`wss`), or cleartext to loopback / a `.local` host / the
 *   emulator / **a Tailscale tailnet** (WireGuard-encrypted) -> [TransportTier.Full],
 *   eligible for operator access AND a node session.
 * - Cleartext to anything else -> [TransportTier.Limited]: chat and read-only
 *   inspection only, never a node session, grant, or write-scoped token.
 */
fun evaluateTier(baseUrl: String): TransportTier {
    val u = baseUrl.trim().lowercase()
    if (u.startsWith("https://") || u.startsWith("wss://")) return TransportTier.Full
    val host = hostOf(u)
    return if (isTrustedCleartextHost(host)) TransportTier.Full else TransportTier.Limited
}

/** Full-tier cleartext hosts: loopback, emulator, mDNS `.local`, or a tailnet peer. */
fun isTrustedCleartextHost(host: String): Boolean {
    val h = host.trim().lowercase().trim('[', ']')
    if (h.isEmpty()) return false
    if (h == "localhost" || h == "127.0.0.1" || h == "::1") return true
    if (h == "10.0.2.2" || h == "10.0.3.2") return true // Android / Genymotion emulator host
    if (h.endsWith(".local")) return true
    return isTailnetHost(h)
}

/**
 * Whether [host] is a Tailscale tailnet peer: a MagicDNS name (`*.ts.net`), the
 * CGNAT IPv4 range 100.64.0.0/10, or the Tailscale IPv6 ULA fd7a:115c:a1e0::/48.
 */
fun isTailnetHost(host: String): Boolean {
    val h = host.trim().lowercase().trim('[', ']')
    if (h.endsWith(".ts.net")) return true
    if (h.startsWith("fd7a:115c:a1e0")) return true
    val octets = h.split(".")
    if (octets.size == 4 && octets.all { it.toIntOrNull() != null }) {
        val a = octets[0].toInt(); val b = octets[1].toInt()
        if (a == 100 && b in 64..127) return true // 100.64.0.0/10
    }
    return false
}

fun hostOf(url: String): String {
    var s = url.substringAfter("://", url)
    s = s.substringBefore('/').substringBefore('?').substringBefore('#')
    s = s.substringAfter('@') // strip userinfo
    return if (s.startsWith("[")) {
        s.substringBefore(']').removePrefix("[") // IPv6 literal
    } else {
        s.substringBeforeLast(':').ifEmpty { s } // strip :port (safe for host:port)
            .let { if (it.count { c -> c == ':' } > 1) s else it } // don't mangle bare IPv6
    }
}

/** Live tailnet state, from the device's network interfaces. */
data class TailnetStatus(val active: Boolean, val address: String?)

fun detectTailnet(): TailnetStatus = runCatching {
    val addresses = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .flatMap { it.inetAddresses.toList() }
        .mapNotNull { it.hostAddress }
    val tailnet = addresses.firstOrNull { isTailnetHost(it.substringBefore('%')) }
    TailnetStatus(active = tailnet != null, address = tailnet?.substringBefore('%'))
}.getOrDefault(TailnetStatus(false, null))
