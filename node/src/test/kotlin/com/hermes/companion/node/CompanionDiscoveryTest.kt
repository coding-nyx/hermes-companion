package com.hermes.companion.node

import com.hermes.companion.discovery.evaluateTier
import com.hermes.companion.domain.TransportTier
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors

/**
 * Pure-JVM test for the discovery orchestrator.
 *
 * We don't try to fake the Android `NsdManager` directly (it has no
 * constructor we can stub). Instead we exercise the test seam
 * [CompanionDiscovery.discoverWith] which takes a [CompanionDiscovery.Probes]
 * parameter; the production [CompanionDiscovery.discover] wires the same
 * `Probes` to OkHttp + NsdManager. That keeps the orchestrator's branching
 * (dedupe, timeout, multi-source fan-out) covered without Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class CompanionDiscoveryTest {

    private lateinit var server: MockWebServer
    private lateinit var singleThread: ExecutorCoroutineDispatcher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        singleThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }

    @After
    fun tearDown() {
        server.shutdown()
        singleThread.close()
    }

    @Test
    fun `dedupe collapses same url and on done is called exactly once`() = runBlocking {
        val url = server.url("/").toString().trimEnd('/')
        val helloPayload = helloBody(magic = "lab.tail.ts.net", ips = listOf("100.64.0.5"))
        repeat(4) { server.enqueue(MockResponse().setBody(helloPayload)) }
        repeat(4) { server.enqueue(MockResponse().setResponseCode(401)) }

        val collector = CapturingListener()
        val probes = StaticProbes(
            hello = { baseUrl ->
                if (baseUrl == url) CompanionDiscovery.HelloResponse(
                    magicDns = "lab.tail.ts.net",
                    tailscaleIps = listOf("100.64.0.5"),
                    port = 9120,
                ) else null
            },
            pair = { _ -> true },
            tcp = { host, port -> host == "127.0.0.1" && port == server.port },
        )
        val discovery = CompanionDiscovery(
            context = null,
            okHttp = okhttp,
            ioDispatcher = singleThread,
            totalBudgetMs = 800,
            perSourceBudgetMs = 200,
        )
        discovery.discoverWith(probes, collector)

        // dedupe by URL: at most one candidate per URL.
        val urls = collector.candidates.map { it.url }
        assertEquals("deduped urls", urls.size, urls.toSet().size)
        // onDone fires exactly once.
        assertEquals(1, collector.doneCount)
        // every candidate that DID emerge came from the LAN sweep (Bonjour
        // and MagicDNS are stubbed out and the loopback test won't reach
        // MockWebServer on its ephemeral port).
        for (c in collector.candidates) {
            assertEquals(CompanionDiscovery.Source.Lan, c.source)
            assertEquals(TransportTier.Full, c.tier)
        }
    }

    @Test
    fun `no candidate when hello returns null`() = runBlocking {
        val collector = CapturingListener()
        val probes = StaticProbes(
            hello = { null },
            pair = { false },
            tcp = { _, _ -> false },
        )
        val discovery = CompanionDiscovery(
            context = null,
            okHttp = okhttp,
            ioDispatcher = singleThread,
            totalBudgetMs = 200,
        )
        discovery.discoverWith(probes, collector)
        assertTrue(collector.candidates.isEmpty())
        assertEquals(1, collector.doneCount)
    }

    @Test
    fun `pairing false when pair endpoint returns 404`() = runBlocking {
        val url = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody(helloBody("lab.tail.ts.net", listOf("100.64.0.5"))))
        server.enqueue(MockResponse().setResponseCode(404))

        val collector = CapturingListener()
        val probes = StaticProbes(
            hello = { baseUrl ->
                if (baseUrl == url) CompanionDiscovery.HelloResponse(
                    magicDns = "lab.tail.ts.net",
                    tailscaleIps = listOf("100.64.0.5"),
                    port = 9120,
                ) else null
            },
            // Pair flag is false → pairing=false on emitted candidates.
            pair = { _ -> false },
            tcp = { host, port -> host == "127.0.0.1" && port == server.port },
        )
        val discovery = CompanionDiscovery(
            context = null,
            okHttp = okhttp,
            ioDispatcher = singleThread,
            totalBudgetMs = 300,
        )
        discovery.discoverWith(probes, collector)
        for (c in collector.candidates) {
            if (c.url == url) {
                assertFalse("expected pairing=false", c.pairing)
            }
        }
    }

    @Test
    fun `tier downgrade reflects the candidate url`() {
        assertEquals(TransportTier.Full, evaluateTier("http://lab.tail.ts.net:9120"))
        assertEquals(TransportTier.Full, evaluateTier("http://127.0.0.1:9120"))
        assertEquals(TransportTier.Limited, evaluateTier("http://8.8.8.8:9120"))
    }

    @Test
    fun `bonjour source marker is preserved when source is bonjour`() {
        val c = CompanionDiscovery.Candidate(
            label = "hermes-mac",
            url = "http://hermes-mac.local:9120",
            magicDns = "hermes-mac.tail.ts.net",
            tailscaleIps = listOf("100.64.0.5"),
            pairing = true,
            source = CompanionDiscovery.Source.Bonjour,
            tier = TransportTier.Full,
        )
        assertEquals(CompanionDiscovery.Source.Bonjour, c.source)
        assertTrue(c.pairing)
        assertEquals("hermes-mac.tail.ts.net", c.magicDns)
    }

    @Test
    fun `lan sweep respects the total budget`() = runBlocking {
        // tcpReachable always false -> no candidates; we still need onDone.
        val collector = CapturingListener()
        val probes = StaticProbes(
            hello = { null },
            pair = { false },
            tcp = { _, _ -> false },
        )
        val discovery = CompanionDiscovery(
            context = null,
            okHttp = okhttp,
            ioDispatcher = singleThread,
            totalBudgetMs = 50,
        )
        val start = System.currentTimeMillis()
        discovery.discoverWith(probes, collector)
        val elapsed = System.currentTimeMillis() - start
        assertTrue("discovery took too long: ${elapsed}ms", elapsed < 5_000)
        assertEquals(1, collector.doneCount)
    }

    private val okhttp = okhttp3.OkHttpClient.Builder().build()

    private fun helloBody(magic: String, ips: List<String>): String =
        JSONObject()
            .put("magicDns", magic)
            .put("tailscaleIps", org.json.JSONArray(ips))
            .put("port", 9120)
            .toString()

    private class StaticProbes(
        private val hello: (String) -> CompanionDiscovery.HelloResponse?,
        private val pair: (String) -> Boolean,
        private val tcp: (String, Int) -> Boolean,
    ) : CompanionDiscovery.Probes {
        override fun discoverBonjour(serviceType: String, onFound: (CompanionDiscovery.BonjourFound) -> Unit): AutoCloseable {
            return AutoCloseable { }
        }

        override fun tcpReachable(host: String, port: Int, timeoutMs: Int): Boolean = tcp(host, port)
        override fun hello(baseUrl: String, timeoutMs: Long): CompanionDiscovery.HelloResponse? = hello(baseUrl)
        override fun pairEndpointReachable(baseUrl: String, timeoutMs: Long): Boolean = pair(baseUrl)
    }

    private class CapturingListener : CompanionDiscovery.Listener {
        val candidates = mutableListOf<CompanionDiscovery.Candidate>()
        var doneCount = 0
        val errors = mutableListOf<String>()
        override fun onCandidate(c: CompanionDiscovery.Candidate) { candidates += c }
        override fun onDone() { doneCount++ }
        override fun onError(message: String) { errors += message }
    }
}