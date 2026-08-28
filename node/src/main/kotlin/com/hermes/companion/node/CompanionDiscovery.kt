package com.hermes.companion.node

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.hermes.companion.discovery.detectTailnet
import com.hermes.companion.discovery.evaluateTier
import com.hermes.companion.domain.TransportTier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Auto-discovery for the companion plugin running on a Hermes gateway.
 *
 * Three sources, tried in order with a shared [totalBudgetMs] so the user is
 * never left waiting:
 *   1. Bonjour/mDNS for `_hermes-companion._tcp.local.` (most reliable on LAN,
 *      also surfaces tailnet peers if the gateway advertises with Tailscale
 *      MagicDNS + mDNS reflector).
 *   2. Tailscale MagicDNS — try a small list of canonical hostnames so we
 *      don't depend on mDNS across the tailnet.
 *   3. LAN IP sweep — connect-probe known gateway ports (9120 for the
 *      companion plugin, 8642 fallback) on the device's local IPv4 /24
 *      subnets, then send a `/companion/hello` to anything that answers.
 *
 * Discovery proves only that something answered on an address — gating on
 * `pair` (which requires the setup code) is the pair flow's job.
 */
class CompanionDiscovery(
    @Suppress("unused") private val context: Context? = null,
    private val okHttp: OkHttpClient = OkHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val totalBudgetMs: Long = DEFAULT_TOTAL_BUDGET_MS,
    private val perSourceBudgetMs: Long = DEFAULT_PER_SOURCE_BUDGET_MS,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val helloTimeoutMs: Long = DEFAULT_HELLO_TIMEOUT_MS,
    private val sweepSampleLimit: Int = DEFAULT_SWEEP_SAMPLE_LIMIT,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * One thing that answered with a valid `/companion/hello`. [pairing] is
     * true when we were able to confirm the `/companion/pair` endpoint
     * exists (we don't actually pair here — that requires the setup code,
     * which we don't have).
     */
    data class Candidate(
        val label: String,
        val url: String,
        val magicDns: String?,
        val tailscaleIps: List<String>,
        val pairing: Boolean,
        val source: Source,
        val tier: TransportTier,
    )

    enum class Source { Bonjour, MagicDns, Lan, Direct }

    interface Listener {
        fun onCandidate(c: Candidate)
        fun onDone()
        fun onError(message: String) {}
    }

    /**
     * Plug-in seam for tests — the real impl talks to OkHttp + NsdManager.
     * Every method is allowed to throw; the orchestrator catches and reports.
     */
    interface Probes {
        /** Open an NSD service discovery for [serviceType], return AutoCloseable. */
        fun discoverBonjour(serviceType: String, onFound: (BonjourFound) -> Unit): AutoCloseable
        /** Probe [host]:[port] with a TCP connect, return true if reachable. */
        fun tcpReachable(host: String, port: Int, timeoutMs: Int): Boolean
        /** HTTP GET [baseUrl]/companion/hello, return parsed JSON or null. */
        fun hello(baseUrl: String, timeoutMs: Long): HelloResponse?
        /** HTTP POST [baseUrl]/companion/pair with a throwaway code, true if endpoint answered. */
        fun pairEndpointReachable(baseUrl: String, timeoutMs: Long): Boolean
    }

    data class HelloResponse(
        val magicDns: String?,
        val tailscaleIps: List<String>,
        val port: Int,
    )

    /** Pre-resolved Bonjour result. [port] is 0 if NSD only gave us a name. */
    data class BonjourFound(val name: String, val host: String, val port: Int)

    /**
     * Run all sources, pushing candidates to [listener] as they arrive, then
     * call listener.onDone() once. Exceptions are swallowed and reported via
     * [Listener.onError] so the UI stays usable on a hostile network.
     */
    suspend fun discover(listener: Listener) = withContext(ioDispatcher) {
        val deadline = now() + totalBudgetMs
        val seen = ConcurrentHashMap.newKeySet<String>()

        fun emit(c: Candidate) {
            val key = c.url
            if (seen.add(key)) listener.onCandidate(c)
        }

        val ctx = context ?: error("CompanionDiscovery.discover requires a Context; use discoverWith for tests")
        val probes = AndroidProbes(ctx, okHttp, connectTimeoutMs, helloTimeoutMs)
        runSources(probes, listener, seen, deadline)
    }

    /**
     * Test seam: take the [Probes] (NsdManager+OkHttp by default) explicitly
     * so unit tests can stub them out without instrumenting the Android
     * framework.
     */
    suspend fun discoverWith(probes: Probes, listener: Listener) = withContext(ioDispatcher) {
        val deadline = now() + totalBudgetMs
        val seen = ConcurrentHashMap.newKeySet<String>()
        runSources(probes, listener, seen, deadline)
    }

    private suspend fun runSources(
        probes: Probes,
        listener: Listener,
        seen: MutableSet<String>,
        deadline: Long,
    ) {
        fun emit(c: Candidate) {
            val key = c.url
            if (seen.add(key)) listener.onCandidate(c)
        }

        try {
            coroutineScope {
                val jobs = listOf(
                    async { runCatching { scanBonjour(probes, ::emit, seen, deadline) } },
                    async { runCatching { scanMagicDns(probes, ::emit, seen, deadline) } },
                    async { runCatching { scanLanSweep(probes, ::emit, seen, deadline) } },
                    async { runCatching { scanLoopback(probes, ::emit, seen, deadline) } },
                )
                try {
                    awaitAny(jobs, deadline)
                } finally {
                    jobs.forEach { it.cancel() }
                }
            }
        } finally {
            listener.onDone()
        }
    }

    private suspend fun scanBonjour(
        probes: Probes,
        emit: (Candidate) -> Unit,
        seen: MutableSet<String>,
        deadline: Long,
    ) {
        val remaining = (deadline - now()).coerceAtLeast(0)
        if (remaining == 0L) return
        withTimeoutOrNull(remaining.coerceAtMost(perSourceBudgetMs)) {
            val closable = probes.discoverBonjour(SERVICE_TYPE) { found ->
                val url = "http://${found.host}:${found.port}"
                if (seen.add(url)) {
                    launch {
                        val hello = probes.hello(url, helloTimeoutMs)
                        val magic = hello?.magicDns
                        val tailIps = hello?.tailscaleIps ?: emptyList()
                        val pairing = if (hello != null) probes.pairEndpointReachable(url, helloTimeoutMs) else false
                        emit(
                            Candidate(
                                label = found.name,
                                url = url,
                                magicDns = magic,
                                tailscaleIps = tailIps,
                                pairing = pairing,
                                source = Source.Bonjour,
                                tier = evaluateTier(url),
                            ),
                        )
                    }
                }
            }
            try {
                awaitCancellation()
            } finally {
                runCatching { closable.close() }
            }
        }
    }

    private suspend fun scanMagicDns(
        probes: Probes,
        emit: (Candidate) -> Unit,
        seen: MutableSet<String>,
        deadline: Long,
    ) {
        val tailnet = detectTailnet()
        if (!tailnet.active) return
        val suffix = MAGICDNS_SUFFIX.takeIf { it.isNotBlank() } ?: return
        val hostnames = mutableSetOf<String>().apply {
            add("hermes-gateway.$suffix")
            add("hermes.$suffix")
            add("companion.$suffix")
            add("gateway.$suffix")
            tailnet.address?.let { tailAddr ->
                // Strip the IPv4 octets (100.X.Y.Z) and try the bare hostname
                // some users give their boxes.
                val bare = tailAddr.substringBefore('.')
                if (bare.isNotEmpty() && bare.all { it.isLetterOrDigit() || it == '-' }) {
                    add("$bare.$suffix")
                }
            }
        }
        for (host in hostnames) {
            if (now() >= deadline) return
            probeHttp(probes, "http://$host:9120", host, Source.MagicDns, emit)
        }
    }

    private suspend fun scanLanSweep(
        probes: Probes,
        emit: (Candidate) -> Unit,
        seen: MutableSet<String>,
        deadline: Long,
    ) {
        val localRanges = localIPv4Ranges().take(MAX_SUBNETS)
        if (localRanges.isEmpty()) return
        // Pick a sample of hosts across all ranges, capped so the sweep
        // finishes within the deadline on big /16 networks.
        val hosts = pickSweepHosts(localRanges, sweepSampleLimit)
        for (ip in hosts) {
            if (now() >= deadline) return
            val ok9120 = probes.tcpReachable(ip, 9120, connectTimeoutMs)
            val port = if (ok9120) {
                9120
            } else if (probes.tcpReachable(ip, 8642, connectTimeoutMs)) {
                8642
            } else {
                null
            }
            if (port != null) {
                probeHttp(probes, "http://$ip:$port", ip, Source.Lan, emit)
            }
        }
    }

    private suspend fun scanLoopback(
        probes: Probes,
        emit: (Candidate) -> Unit,
        seen: MutableSet<String>,
        deadline: Long,
    ) {
        for (host in LOOPBACK_HOSTS) {
            if (now() >= deadline) return
            probeHttp(probes, "http://$host:9120", host, Source.Lan, emit)
        }
    }

    private suspend fun probeHttp(
        probes: Probes,
        url: String,
        label: String,
        source: Source,
        emit: (Candidate) -> Unit,
    ) {
        val hello = probes.hello(url, helloTimeoutMs) ?: return
        val pairing = runCatching { probes.pairEndpointReachable(url, helloTimeoutMs) }.getOrDefault(false)
        emit(
            Candidate(
                label = label,
                url = url,
                magicDns = hello.magicDns,
                tailscaleIps = hello.tailscaleIps,
                pairing = pairing,
                source = source,
                tier = evaluateTier(url),
            ),
        )
    }

    private fun localIPv4Ranges(): List<IPv4Range> {
        val ranges = mutableListOf<IPv4Range>()
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback || iface.isPointToPoint) continue
                for (addr in iface.inetAddresses) {
                    val v4 = addr as? Inet4Address ?: continue
                    val bytes = v4.address.map { it.toInt() and 0xFF }
                    ranges += IPv4Range(bytes, guessPrefix(iface))
                }
            }
        }
        return ranges
    }

    private fun guessPrefix(iface: NetworkInterface): Int {
        // 10.x and 192.168.x are commonly /24; 172.16-31.x is /16+. Without a
        // per-iface API for the prefix, /24 is the conservative sweep and the
        // sample picker keeps the total bounded.
        return 24
    }

    private fun pickSweepHosts(ranges: List<IPv4Range>, limit: Int): List<String> {
        val all = mutableListOf<String>()
        for (r in ranges) all += r.hosts()
        if (all.size <= limit) return all
        // Stratified sample: every Nth host so we cover the range evenly.
        val step = (all.size.toDouble() / limit).coerceAtLeast(1.0).toInt()
        return all.filterIndexed { idx, _ -> idx % step == 0 }.take(limit)
    }

    private suspend fun awaitAny(jobs: List<Job>, deadline: Long) {
        // Wait for *all* of them to finish naturally, then return. We don't
        // early-return on first-finish because we want as much parallel
        // signal as the deadline permits.
        val remaining = (deadline - now()).coerceAtLeast(0)
        withTimeoutOrNull(remaining) {
            for (j in jobs) j.join()
        }
    }

    private data class IPv4Range(val base: List<Int>, val prefix: Int) {
        fun hosts(): List<String> {
            val mask: Long = if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xFFFFFFFFL
            val b0: Long = base[0].toLong() and ((mask ushr 24) and 0xFFL.toLong().toLong())
            val b1: Long = base[1].toLong() and ((mask ushr 16) and 0xFFL)
            val b2: Long = base[2].toLong() and ((mask ushr 8) and 0xFFL)
            val b3: Long = base[3].toLong() and (mask and 0xFFL)
            val netInt: Long = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            val count: Long = if (prefix >= 32) 1L else (1L shl (32 - prefix))
            // Skip network address (.0) and broadcast (.last); never probe
            // ourselves either.
            val first: Long = if (count > 2) 1L else 0L
            val last: Long = if (count > 2) count - 1L else count
            val out = ArrayList<String>((last - first).toInt())
            for (i in first until last) {
                val v: Long = netInt or i
                val i0 = ((v ushr 24) and 0xFFL).toInt()
                val i1 = ((v ushr 16) and 0xFFL).toInt()
                val i2 = ((v ushr 8) and 0xFFL).toInt()
                val i3 = (v and 0xFFL).toInt()
                out += "$i0.$i1.$i2.$i3"
            }
            return out
        }
    }

    /** The Android implementation — wires OkHttp + NsdManager into [Probes]. */
    private class AndroidProbes(
        private val context: Context,
        private val okHttp: OkHttpClient,
        private val connectTimeoutMs: Int,
        private val helloTimeoutMs: Long,
    ) : Probes {
        override fun discoverBonjour(serviceType: String, onFound: (BonjourFound) -> Unit): AutoCloseable {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return AutoCloseable { }
            val resolved = ConcurrentHashMap.newKeySet<String>()
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

                override fun onServiceFound(info: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    mgr.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val host = resolvedInfo.host?.hostAddress ?: return
                            val port = resolvedInfo.port
                            val key = "$host:$port:${resolvedInfo.serviceName}"
                            if (resolved.add(key)) {
                                onFound(BonjourFound(resolvedInfo.serviceName, host, port))
                            }
                        }
                    })
                }

                override fun onServiceLost(info: NsdServiceInfo) { resolved.remove(info.serviceName) }
            }
            runCatching { mgr.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener) }
            return AutoCloseable { runCatching { mgr.stopServiceDiscovery(listener) } }
        }

        override fun tcpReachable(host: String, port: Int, timeoutMs: Int): Boolean = runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        }.getOrDefault(false)

        override fun hello(baseUrl: String, timeoutMs: Long): HelloResponse? {
            return runCatching {
                val req = Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/companion/hello")
                    .get()
                    .build()
                val callTimeoutMs: Long = timeoutMs + connectTimeoutMs.toLong()
                val readTimeoutMs: Int = timeoutMs.toInt().coerceAtLeast(1)
                val client = okHttp.newBuilder()
                    .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    val o = JSONObject(body)
                    val magic = o.optString("magicDns").takeIf { it.isNotBlank() }
                    val tailscale = o.optJSONArray("tailscaleIps")?.let { arr ->
                        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
                    } ?: emptyList()
                    val port = o.optInt("port", 0)
                    HelloResponse(magicDns = magic, tailscaleIps = tailscale, port = port)
                }
            }.getOrNull()
        }

        override fun pairEndpointReachable(baseUrl: String, timeoutMs: Long): Boolean {
            return runCatching {
                val body = "{\"setupCode\":\"discover-probe\",\"nodeName\":\"probe\"}"
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val req = Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/companion/pair")
                    .post(body.toRequestBody(mediaType))
                    .build()
                val callTimeoutMs: Long = timeoutMs + connectTimeoutMs.toLong()
                val readTimeoutMs: Int = timeoutMs.toInt().coerceAtLeast(1)
                val client = okHttp.newBuilder()
                    .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
                    .build()
                // A 4xx (typically 401) means the endpoint exists (it
                // rejected our throwaway code). A 404 means there is no
                // companion plugin here.
                client.newCall(req).execute().use { resp -> resp.code != 404 }
            }.getOrDefault(false)
        }
    }

    companion object {
        const val DEFAULT_TOTAL_BUDGET_MS = 10_000L
        const val DEFAULT_PER_SOURCE_BUDGET_MS = 5_000L
        const val DEFAULT_CONNECT_TIMEOUT_MS = 200
        const val DEFAULT_HELLO_TIMEOUT_MS = 1_500L
        const val DEFAULT_SWEEP_SAMPLE_LIMIT = 96
        const val MAX_SUBNETS = 4
        const val SERVICE_TYPE = "_hermes-companion._tcp.local."
        val LOOPBACK_HOSTS = listOf("127.0.0.1", "10.0.2.2", "10.0.3.2")

        // MagicDNS suffix is set per-deployment. Tailscale's production
        // default is "<machine>.<tailnet>.ts.net"; we fall back to common
        // bare names against the device's own tailnet.
        val MAGICDNS_SUFFIX: String = System.getenv("HERMES_COMPANION_MAGICDNS_SUFFIX").orEmpty()

        @Suppress("unused")
        fun resolverFor(context: Context): CompanionDiscovery = CompanionDiscovery(context)
    }
}