package com.hermes.companion.backend

import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayKind
import com.hermes.companion.domain.RunEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockHermesBackendTest {

    private fun newBackend() = MockHermesBackend(
        GatewayConnection(
            id = "gw-test",
            label = "Test",
            kind = GatewayKind.Local,
            baseUrl = "mock://gw-test",
            authRef = "none",
        ),
        profileIds = listOf("ash", "misty"),
    )

    @Test
    fun `capabilities include mock flag`() = runTest {
        val caps = newBackend().capabilities()
        assertEquals(true, caps["mock.only"])
        assertEquals(true, caps["sessions.list"])
    }

    @Test
    fun `profiles seeded on init`() = runTest {
        val profiles = newBackend().listProfiles()
        assertEquals(listOf("ash", "misty"), profiles.map { it.profileId })
    }

    @Test
    fun `listSessionsForProfile returns seeded session per profile`() = runTest {
        val backend = newBackend()
        val ash = backend.listSessionsForProfile("gw-test", "ash")
        val misty = backend.listSessionsForProfile("gw-test", "misty")
        assertEquals(1, ash.size)
        assertEquals(1, misty.size)
        assertTrue(ash[0].sessionId.contains("ash"))
        assertTrue(misty[0].sessionId.contains("misty"))
    }

    @Test
    fun `a submitted run emits tool started, tool completed, assistant deltas, run completed`() = runTest {
        val backend = newBackend()
        val route = ConversationRoute("gw-test", "ash", backend.listSessionsForProfile("gw-test", "ash")[0].sessionId)
        val events = backend.runEvents(route, backend.submit(route, "hello")).toList()
        val types = events.map { it::class.simpleName }
        assertTrue("ToolStarted in " + types, types.contains("ToolStarted"))
        assertTrue("ToolCompleted in " + types, types.contains("ToolCompleted"))
        assertTrue("AssistantDelta in " + types, types.contains("AssistantDelta"))
        assertTrue("RunCompleted in " + types, types.contains("RunCompleted"))
    }

    @Test
    fun `approval path triggers when text begins with deploy`() = runTest {
        val backend = newBackend()
        val route = ConversationRoute("gw-test", "ash", backend.listSessionsForProfile("gw-test", "ash")[0].sessionId)
        val events = backend.runEvents(route, backend.submit(route, "deploy prod")).toList()
        val approval = events.filterIsInstance<RunEvent.ApprovalRequired>().firstOrNull()
        assertNotNull("expected ApprovalRequired", approval)
        val req = approval!!.request
        backend.decideApproval(route, req.runId, req.requestId, ApprovalOption.Once)

        // The decision must actually resume the run. Before this the surface
        // existed but the loop never closed.
        val resumed = backend.runEvents(route, req.runId).toList()
        val completed = resumed.filterIsInstance<RunEvent.RunCompleted>().firstOrNull()
        assertNotNull("approval should resume the run", completed)
        assertTrue(completed!!.finalText.contains("Approved"))
        assertTrue(
            "assistant reply should be persisted",
            backend.listMessages(route).any { it is com.hermes.companion.domain.Message.Assistant && it.text.contains("Approved") },
        )
    }

    @Test
    fun `denying approval is accepted`() = runTest {
        val backend = newBackend()
        val route = ConversationRoute("gw-test", "ash", backend.listSessionsForProfile("gw-test", "ash")[0].sessionId)
        val events = backend.runEvents(route, backend.submit(route, "send payload")).toList()
        val req = (events.filterIsInstance<RunEvent.ApprovalRequired>().first()).request
        backend.decideApproval(route, req.runId, req.requestId, ApprovalOption.Deny)

        val resumed = backend.runEvents(route, req.runId).toList()
        val failed = resumed.filterIsInstance<RunEvent.RunFailed>().firstOrNull()
        assertNotNull("denial should terminate the run", failed)
        assertEquals("denied by operator", failed!!.reason)
    }

    @Test
    fun `run awaiting a decision re-emits its approval request`() = runTest {
        val backend = newBackend()
        val route = ConversationRoute("gw-test", "ash", backend.listSessionsForProfile("gw-test", "ash")[0].sessionId)
        val runId = backend.submit(route, "deploy prod")
        val req = backend.runEvents(route, runId).toList()
            .filterIsInstance<RunEvent.ApprovalRequired>().first().request

        // Reattaching before deciding must not invent a completion.
        val again = backend.runEvents(route, req.runId).toList()
        assertEquals(1, again.size)
        assertTrue(again[0] is RunEvent.ApprovalRequired)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown gateway route is rejected`() = runTest {
        val backend = newBackend()
        backend.listSessionsForProfile("gw-other", "ash")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown profile is rejected`() = runTest {
        val backend = newBackend()
        backend.listSessionsForProfile("gw-test", "ghost")
    }

    @Test
    fun `createSession creates and lists new session`() = runTest {
        val backend = newBackend()
        val route = ConversationRoute("gw-test", "ash", "dummy")
        val created = backend.createSession(route, "New Triage Session")
        assertEquals("New Triage Session", created.title)
        val sessions = backend.listSessionsForProfile("gw-test", "ash")
        assertEquals(2, sessions.size)
        assertTrue(sessions.any { it.sessionId == created.sessionId })
    }
}

