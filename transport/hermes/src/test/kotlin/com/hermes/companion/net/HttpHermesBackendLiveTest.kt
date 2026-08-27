package com.hermes.companion.net

import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayKind
import com.hermes.companion.domain.RunEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket

class HttpHermesBackendLiveTest {

    private val liveHost = "100.88.4.63"
    private val livePort = 7800
    private val baseUrl = "http://$liveHost:$livePort/gw-hub11"

    private lateinit var backend: HttpHermesBackend

    @Before
    fun setUp() {
        // Skip test if hub-11 is not reachable in current environment
        val reachable = runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(liveHost, livePort), 1500)
                true
            }
        }.getOrDefault(false)

        assumeTrue("Hub-11 (100.88.4.63:$livePort) is reachable over Tailscale", reachable)

        backend = HttpHermesBackend(
            GatewayConnection(
                id = "gw-hub11",
                label = "Hub-11 (Live)",
                kind = GatewayKind.RemoteHttp,
                baseUrl = baseUrl,
                authRef = "none",
            )
        )
    }

    @Test
    fun testLiveCapabilities() = runBlocking {
        val caps = backend.capabilities("ash")
        assertTrue("chat.stream capability enabled", caps["chat.stream"] == true)
        assertTrue("sessions.create capability enabled", caps["sessions.create"] == true)
    }

    @Test
    fun testLiveProfiles() = runBlocking {
        val profiles = backend.listProfiles()
        assertTrue("At least one profile found on Hub-11", profiles.isNotEmpty())
        assertTrue("Profile ash exists on Hub-11", profiles.any { it.profileId == "ash" })
    }

    @Test
    fun testLiveSessionsAndMessages() = runBlocking {
        val sessions = backend.listSessionsForProfile("gw-hub11", "ash")
        assertTrue("At least one session exists on Hub-11", sessions.isNotEmpty())

        val sid = sessions.first().sessionId
        val route = ConversationRoute("gw-hub11", "ash", sid)
        val msgs = backend.listMessages(route)
        assertTrue("Messages list returned for session", msgs.isNotEmpty())
    }

    @Test
    fun testLiveChatPromptStreaming() = runBlocking {
        val route = ConversationRoute("gw-hub11", "ash", "sess-gw-hub11-ash-1")
        val runId = backend.submit(route, "What is 10 + 15? Answer with only number.")
        assertNotNull("Run ID returned from live hub-11", runId)

        val events = backend.runEvents(route, runId).toList()
        assertTrue("Events received from live Hermes agent", events.isNotEmpty())

        val completed = events.filterIsInstance<RunEvent.RunCompleted>().firstOrNull()
        assertNotNull("RunCompleted event received", completed)
        println("Live Hermes response text: ${completed?.finalText}")
    }
}
