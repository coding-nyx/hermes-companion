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
    fun `sendAndStream emits tool started, tool completed, assistant deltas, run completed`() = runTest {
        val backend = newBackend()
        val route = ConversationRoute("gw-test", "ash", backend.listSessionsForProfile("gw-test", "ash")[0].sessionId)
        val events = backend.sendAndStream(route, "hello").toList()
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
        val events = backend.sendAndStream(route, "deploy prod").toList()
        val approval = events.filterIsInstance<RunEvent.ApprovalRequired>().firstOrNull()
        assertNotNull("expected ApprovalRequired", approval)
        val req = approval!!.request
        backend.decideApproval(route, req.requestId, ApprovalOption.Once)
        // no exception thrown
    }

    @Test
    fun `denying approval is accepted`() = runTest {
        val backend = newBackend()
        val route = ConversationRoute("gw-test", "ash", backend.listSessionsForProfile("gw-test", "ash")[0].sessionId)
        val events = backend.sendAndStream(route, "send payload").toList()
        val req = (events.filterIsInstance<RunEvent.ApprovalRequired>().first()).request
        backend.decideApproval(route, req.requestId, ApprovalOption.Deny)
        // no exception thrown
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
    fun `listMessages rejects unknown session`() = runTest {
        val backend = newBackend()
        val route = ConversationRoute("gw-test", "ash", "no-such-session")
        val result = runCatching { backend.listMessages(route) }
        assertTrue(result.isFailure)
        // Exception cleared; assert explicitly
        assertNull(result.getOrNull())
    }
}
