package com.hermes.companion.ui.chat

import com.hermes.companion.data.repo.ConversationRepository
import com.hermes.companion.data.repo.ConversationState
import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.RunState
import com.hermes.companion.domain.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T4 closure: ChatViewModel behavior tests.
 *
 * [Dispatchers.Main] is the dispatcher that ViewModel.viewModelScope uses. The
 * test harness replaces it with an [UnconfinedTestDispatcher] for the duration
 * of each test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val route = ConversationRoute("gw-test", "ash", "sess-1")

    @Test
    fun `bind then state reflects the route's conversation`() = runTest(mainDispatcher) {
        val repo = FakeConversationRepository(
            initialConversation = ConversationState(
                route = route,
                gatewayLabel = "Test Gateway",
            )
        )
        val vm = ChatViewModel(repo)

        vm.bind(route)
        val s = vm.state.first()
        assertEquals("Test Gateway", s.backendLabel)
        assertEquals(route, s.conversation.route)
    }

    @Test
    fun `send with empty text does nothing`() = runTest(mainDispatcher) {
        val repo = FakeConversationRepository()
        val vm = ChatViewModel(repo)
        vm.bind(route)
        vm.updateDraft("   ")
        vm.send()
        assertTrue("empty send should be a no-op", repo.submitCalls.isEmpty())
    }

    @Test
    fun `send trims draft and forwards to repository`() = runTest(mainDispatcher) {
        val repo = FakeConversationRepository()
        val vm = ChatViewModel(repo)
        vm.bind(route)
        vm.updateDraft("  hello world  ")
        vm.send()
        assertEquals(1, repo.submitCalls.size)
        assertEquals(route to "hello world", repo.submitCalls.first())
    }

    @Test
    fun `send clears draft after a successful submit`() = runTest(mainDispatcher) {
        val repo = FakeConversationRepository()
        val vm = ChatViewModel(repo)
        vm.bind(route)
        vm.updateDraft("hi")
        vm.send()
        assertEquals("", vm.state.first().draft)
    }
}

/**
 * Minimal in-memory ConversationRepository. Only the methods ChatViewModel
 * calls (conversation, refresh, submit, decide, stop, createSession) are
 * implemented. createSession returns a Session with the route's profile+gateway.
 */
class FakeConversationRepository(
    private val initialConversation: ConversationState = ConversationState(),
) : ConversationRepository {
    val submitCalls: MutableList<Pair<ConversationRoute, String>> = mutableListOf()
    val decideCalls: MutableList<Triple<ConversationRoute, String, ApprovalOption>> = mutableListOf()
    val stopCalls: MutableList<Pair<ConversationRoute, String>> = mutableListOf()

    private val state = MutableStateFlow(initialConversation)

    override fun conversation(route: ConversationRoute): Flow<ConversationState> = state
    override suspend fun refresh(route: ConversationRoute): Result<Unit> = Result.success(Unit)
    override suspend fun createSession(route: ConversationRoute, title: String): Result<Session> =
        Result.success(Session(
            sessionId = "new-sess",
            profileId = route.profileId,
            gatewayId = route.gatewayId,
            title = title,
            runState = RunState.Idle,
        ))
    override suspend fun submit(route: ConversationRoute, text: String): Result<String> {
        submitCalls += route to text
        return Result.success("run-1")
    }
    override suspend fun decide(route: ConversationRoute, runId: String, requestId: String, option: ApprovalOption): Result<Unit> {
        decideCalls += Triple(route, runId, option)
        return Result.success(Unit)
    }
    override suspend fun stop(route: ConversationRoute, runId: String): Result<Unit> {
        stopCalls += route to runId
        return Result.success(Unit)
    }
}

