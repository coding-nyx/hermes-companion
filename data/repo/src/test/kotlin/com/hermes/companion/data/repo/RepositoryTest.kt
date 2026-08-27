package com.hermes.companion.data.repo

import com.hermes.companion.backend.HermesBackend
import com.hermes.companion.backend.MockHermesBackend
import com.hermes.companion.data.db.RunEntity
import com.hermes.companion.domain.SubmissionState
import com.hermes.companion.data.db.OutboundEntity
import com.hermes.companion.data.db.toEntity
import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayHealth
import com.hermes.companion.domain.GatewayKind
import com.hermes.companion.domain.Message
import com.hermes.companion.domain.ProfileHandle
import com.hermes.companion.domain.RunEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {

    private val route = ConversationRoute("gw-test", "ash", "sess-gw-test-ash-1")

    /** Registered but unreachable. */
    private class DeadBackend(id: String) : HermesBackend {
        override val gateway = GatewayConnection(id, id, GatewayKind.RemoteHttp, "http://10.0.0.1:1", "none")
        private fun boom(): Nothing = throw IOException("Connection refused")
        override suspend fun capabilities(profile: String?) = boom()
        override suspend fun listProfiles(): List<AgentProfile> = boom()
        override suspend fun listSessionsForProfile(gatewayId: String, profileId: String) = boom()
        override suspend fun createSession(route: ConversationRoute, title: String): com.hermes.companion.domain.Session = boom()
        override suspend fun listMessages(route: ConversationRoute) = boom()
        override suspend fun submit(route: ConversationRoute, text: String, idempotencyKey: String): String = boom()
        override fun runEvents(route: ConversationRoute, runId: String): Flow<RunEvent> = emptyFlow()
        override suspend fun stopRun(route: ConversationRoute, runId: String) = boom()
        override suspend fun decideApproval(
            route: ConversationRoute,
            runId: String,
            requestId: String,
            option: ApprovalOption,
        ) = boom()
    }

    private fun mock(id: String, profiles: List<String>) = MockHermesBackend(
        GatewayConnection(id, id.removePrefix("gw-"), GatewayKind.Local, "mock://$id", "none"),
        profileIds = profiles,
    )

    private suspend fun seed(fakes: Fakes, vararg backends: HermesBackend): BackendRegistry {
        backends.forEach {
            fakes.gateways.upsert(it.gateway.toEntity(health = GatewayHealth.Unknown))
        }
        return BackendRegistry(backends.toList())
    }

    // ----- reachability is data -----

    @Test
    fun `an unreachable gateway is recorded, never thrown`() = runTest {
        val fakes = Fakes()
        val registry = seed(fakes, DeadBackend("gw-dead"))
        val repo = DefaultFleetRepository(fakes.store, registry)

        repo.refresh()                                 // must not throw

        val fleet = repo.fleet().first()
        assertFalse(fleet.loading)
        assertEquals(setOf("gw-dead"), fleet.failures.keys)
        assertTrue(fleet.gateways.single().connectivity is Connectivity.Down)
    }

    @Test
    fun `one dead gateway does not hide a live one, and handles stay disambiguated`() = runTest {
        val fakes = Fakes()
        val registry = seed(fakes, mock("gw-home", listOf("ash")), mock("gw-cloud", listOf("ash")), DeadBackend("gw-far"))
        val repo = DefaultFleetRepository(fakes.store, registry)

        repo.refresh()

        val fleet = repo.fleet().first()
        assertEquals(setOf("gw-far"), fleet.failures.keys)
        assertEquals(listOf("ash-cloud", "ash-home"), fleet.profiles.map { it.handle.display }.sorted())
        assertEquals(2, fleet.gateways.count { it.connectivity is Connectivity.Live })
    }

    @Test
    fun `a gateway that goes down keeps its cached profiles`() = runTest {
        val fakes = Fakes()
        val live = mock("gw-home", listOf("ash", "misty"))
        val registry = seed(fakes, live)
        val repo = DefaultFleetRepository(fakes.store, registry)
        repo.refresh()
        assertEquals(2, repo.fleet().first().profiles.size)

        // Same gateway id, now unreachable.
        registry.addGateway(DeadBackend("gw-home"))
        repo.refresh()

        val fleet = repo.fleet().first()
        assertEquals("cached roster must survive an outage", 2, fleet.profiles.size)
        assertNotNull(fleet.failures["gw-home"])
    }

    // ----- conversations -----

    @Test
    fun `submit shows the operator's message immediately, then one message per run`() = runTest {
        val fakes = Fakes()
        val registry = seed(fakes, mock("gw-test", listOf("ash")))
        val tracker = RunTracker(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), registry, fakes.store)
        val repo = DefaultConversationRepository(fakes.store, registry, tracker)

        val runId = repo.submit(route, "hello").getOrThrow()
        // Written before any network round trip completed.
        assertTrue(fakes.messages.rows.value.any { it.role == "user" && it.text == "hello" })

        advanceUntilIdle()

        val state = repo.conversation(route).first()
        val assistants = state.messages.filterIsInstance<Message.Assistant>()
        assertEquals("exactly one assistant message per run", 1, assistants.size)
        assertFalse("the run message must not stay marked streaming", assistants.single().isStreaming)
        assertTrue(assistants.single().text.isNotEmpty())
        assertTrue(assistants.single().toolRuns.isNotEmpty())
        assertEquals(RunPhase.Completed, state.activeRun?.state)
        assertEquals(runId, state.activeRun?.runId)
        assertNull(state.pendingApproval)
    }

    @Test
    fun `a gated run surfaces its approval and resumes after a decision`() = runTest {
        val fakes = Fakes()
        val registry = seed(fakes, mock("gw-test", listOf("ash")))
        val tracker = RunTracker(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), registry, fakes.store)
        val repo = DefaultConversationRepository(fakes.store, registry, tracker)

        val runId = repo.submit(route, "deploy prod").getOrThrow()
        advanceUntilIdle()

        val gated = repo.conversation(route).first()
        assertEquals(RunPhase.AwaitingApproval, gated.activeRun?.state)
        val approval = gated.pendingApproval
        assertNotNull("the approval must survive in storage, not in a ViewModel", approval)
        assertEquals(runId, approval!!.runId)

        repo.decide(route, runId, approval.requestId, ApprovalOption.Once).getOrThrow()
        advanceUntilIdle()

        val resumed = repo.conversation(route).first()
        assertEquals(RunPhase.Completed, resumed.activeRun?.state)
        assertNull(resumed.pendingApproval)
        assertTrue(
            resumed.messages.filterIsInstance<Message.Assistant>().any { it.text.contains("Approved") },
        )
    }

    @Test
    fun `denying a run records the refusal`() = runTest {
        val fakes = Fakes()
        val registry = seed(fakes, mock("gw-test", listOf("ash")))
        val tracker = RunTracker(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), registry, fakes.store)
        val repo = DefaultConversationRepository(fakes.store, registry, tracker)

        val runId = repo.submit(route, "send payload").getOrThrow()
        advanceUntilIdle()
        val approval = repo.conversation(route).first().pendingApproval!!

        repo.decide(route, runId, approval.requestId, ApprovalOption.Deny).getOrThrow()
        advanceUntilIdle()

        val state = repo.conversation(route).first()
        assertEquals(RunPhase.Failed, state.activeRun?.state)
        assertEquals("denied by operator", state.activeRun?.error)
        assertNull(state.pendingApproval)
    }

    @Test
    fun `a run is collected even when nobody observes the conversation`() = runTest {
        // The point of moving collection out of viewModelScope: leaving the
        // screen must not cancel the run.
        val fakes = Fakes()
        val registry = seed(fakes, mock("gw-test", listOf("ash")))
        val tracker = RunTracker(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), registry, fakes.store)
        val repo = DefaultConversationRepository(fakes.store, registry, tracker)

        repo.submit(route, "hello").getOrThrow()
        advanceUntilIdle()

        // No conversation() collector was ever attached; the rows are there anyway.
        assertTrue(fakes.runs.rows.value.single().state == "completed")
        assertTrue(fakes.messages.rows.value.any { it.role == "assistant" && it.text.isNotEmpty() })
    }

    @Test
    fun `refresh replaces confirmed history but keeps a pending message`() = runTest {
        val fakes = Fakes()
        val backend = mock("gw-test", listOf("ash"))
        val registry = seed(fakes, backend)
        val tracker = RunTracker(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), registry, fakes.store)
        val repo = DefaultConversationRepository(fakes.store, registry, tracker)

        // A message the gateway has not confirmed.
        val dead = DeadBackend("gw-test")
        registry.addGateway(dead)
        repo.submit(route, "never sent")
        advanceUntilIdle()
        assertTrue(fakes.messages.rows.value.single { it.text == "never sent" }.pending)

        registry.addGateway(backend)
        repo.refresh(route).getOrThrow()

        val texts = fakes.messages.rows.value.map { it.text }
        assertTrue("pending message must survive a refresh", texts.contains("never sent"))
        assertTrue("server history must be present", texts.any { it.startsWith("Hello from ash") })
    }

    @Test
    fun `forget removes only that gateway's rows`() = runTest {
        val fakes = Fakes()
        val registry = seed(fakes, mock("gw-home", listOf("ash")), mock("gw-cloud", listOf("work")))
        val fleet = DefaultFleetRepository(fakes.store, registry)
        fleet.refresh()
        assertEquals(2, fakes.profiles.rows.value.size)

        fleet.forget("gw-cloud").getOrThrow()

        assertEquals(listOf("gw-home"), fakes.gateways.rows.value.map { it.id })
        assertEquals(listOf("gw-home"), fakes.profiles.rows.value.map { it.gatewayId }.distinct())
        assertNull(registry.backendFor("gw-cloud"))
    }

    @Test
    fun `createSession saves new session to store and returns it`() = runTest {
        val fakes = Fakes()
        val backend = mock("gw-test", listOf("ash"))
        val registry = seed(fakes, backend)
        val tracker = RunTracker(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), registry, fakes.store)
        val repo = DefaultConversationRepository(fakes.store, registry, tracker)

        val created = repo.createSession(route, "Project Triage").getOrThrow()
        assertEquals("Project Triage", created.title)
        assertTrue(fakes.sessions.rows.value.any { it.sessionId == created.sessionId && it.title == "Project Triage" })
    }

    @Test
    fun `activity repository maps real runs and one queue per gateway`() = runTest {
        val fakes = Fakes()
        seed(fakes, mock("gw-test", listOf("ash")))
        fakes.runs.upsert(
            RunEntity("gw-test", "ash", "sess-1", "run-1", RunPhase.Completed.stored, null, null, null, 1_000L),
        )
        val repo = DefaultActivityRepository(fakes.store)
        val state = repo.observeActivity().first()
        assertEquals(1, state.items.size)
        assertEquals("run-1", state.items[0].id)
        assertEquals(1, state.queues.size)
        assertEquals("gw-test", state.queues[0].gatewayId)
    }

    @Test
    fun `outbox surfaces the unacknowledged submission and drops it`() = runTest {
        val fakes = Fakes()
        val backend = mock("gw-test", listOf("ash"))
        val registry = seed(fakes, backend)
        val tracker = RunTracker(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), registry, fakes.store)
        val outboxRepo = DefaultOutboxRepository(fakes.store, registry, tracker)

        fakes.outbound.upsert(
            OutboundEntity(
                id = "sub-1",
                gatewayId = "gw-test",
                profileId = "ash",
                sessionId = "sess-1",
                text = "unsent command",
                idempotencyKey = "key-1",
                createdAt = System.currentTimeMillis(),
                attempts = 1,
                state = SubmissionState.Unacknowledged.name,
                runId = null,
                expiresAt = null,
                attachmentBytes = 0,
                lastError = "Connection refused",
            ),
        )

        val state = outboxRepo.observeOutbox().first()
        val item = state.items.single { it.id == "sub-1" }
        assertEquals("no answer", item.state)
        assertTrue(item.needsDecision)

        outboxRepo.dropSubmission("sub-1").getOrThrow()
        assertFalse(outboxRepo.observeOutbox().first().items.any { it.id == "sub-1" })
    }

    @Test
    fun `outbox retry replays under the same idempotency key and clears`() = runTest {
        val fakes = Fakes()
        val backend = mock("gw-test", listOf("ash"))
        val registry = seed(fakes, backend)
        val tracker = RunTracker(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), registry, fakes.store)
        val outboxRepo = DefaultOutboxRepository(fakes.store, registry, tracker)

        // A real session must exist for the mock backend to accept the submit.
        val session = backend.createSession(ConversationRoute("gw-test", "ash", "new"), "T")
        fakes.outbound.upsert(
            OutboundEntity(
                id = "sub-2",
                gatewayId = "gw-test",
                profileId = "ash",
                sessionId = session.sessionId,
                text = "hello",
                idempotencyKey = "key-2",
                createdAt = System.currentTimeMillis(),
                attempts = 1,
                state = SubmissionState.Unacknowledged.name,
                runId = null,
                expiresAt = null,
                attachmentBytes = 0,
                lastError = null,
            ),
        )

        outboxRepo.retrySubmission("sub-2").getOrThrow()

        // Acknowledged rows are filtered out of the outbox view.
        assertFalse(outboxRepo.observeOutbox().first().items.any { it.id == "sub-2" })
        val row = fakes.outbound.find("sub-2")!!
        assertEquals(SubmissionState.Acknowledged.name, row.state)
        assertTrue(row.runId != null)

        // Replaying the same key must NOT create a second run on the backend.
        val again = backend.submit(ConversationRoute("gw-test", "ash", session.sessionId), "hello", "key-2")
        assertEquals(row.runId, again)
    }


    @Test
    fun `observeActive returns null when no row set`() = runTest {
        val fakes = Fakes()
        val registry = seed(fakes, mock("gw-test", listOf("ash")))
        val repo = DefaultFleetRepository(fakes.store, registry)
        assertNull(repo.observeActive().first())
    }

    @Test
    fun `setActive then observeActive returns the gatewayId`() = runTest {
        val fakes = Fakes()
        val registry = seed(fakes, mock("gw-test", listOf("ash")))
        val repo = DefaultFleetRepository(fakes.store, registry)
        repo.setActive("gw-test", url = "http://mock://gw-test", nodeId = "node-test")
        val active = repo.observeActive().first()
        assertEquals("gw-test", active)
    }

    @Test
    fun `setActive replaces prior selection`() = runTest {
        val fakes = Fakes()
        val registry = seed(fakes, mock("gw-test", listOf("ash")), mock("gw-other", listOf("ash")))
        val repo = DefaultFleetRepository(fakes.store, registry)
        repo.setActive("gw-test", url = "http://mock://gw-test", nodeId = "node-test")
        repo.setActive("gw-other", url = "http://mock://gw-other", nodeId = "node-other")
        assertEquals("gw-other", repo.observeActive().first())
    }

}

