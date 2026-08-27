package com.hermes.companion.data.repo

import com.hermes.companion.common.reason
import com.hermes.companion.data.db.CompanionStore
import com.hermes.companion.data.db.MessageEntity
import com.hermes.companion.data.db.RunEntity
import com.hermes.companion.data.db.decodeToolRuns
import com.hermes.companion.data.db.encode
import com.hermes.companion.domain.ApprovalRequest
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.RunEvent
import com.hermes.companion.domain.ToolRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects a run's events into the database, on a scope that outlives any
 * screen. This is the whole point of step 3: leaving Chat mid-run no longer
 * cancels the only observer of that run.
 *
 * Step 4 hands this scope to the foreground service, at which point a run also
 * survives the app being backgrounded.
 */
internal class RunTracker(
    private val scope: CoroutineScope,
    private val registry: BackendRegistry,
    private val store: CompanionStore,
) {
    private val jobs = ConcurrentHashMap<String, Job>()

    /**
     * Idempotent per run. Started lazily on purpose: a flow that completes
     * synchronously would otherwise run its `finally` — removing its own key —
     * before the key was ever stored, leaving a dead job that blocks every
     * later attempt to re-observe the run. That is exactly what happens when a
     * gated run is approved and has to be picked up again.
     */
    fun observe(route: ConversationRoute, runId: String) {
        jobs[runId]?.let { if (it.isActive) return }
        val backend = registry.backendFor(route.gatewayId) ?: return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                writeRun(route, runId, RunPhase.Streaming, error = null, approval = null)
                backend.runEvents(route, runId).collect { apply(route, runId, it) }
                // A stream that ends without a terminal event still has to
                // stop claiming it is streaming.
                closeStreamingMessage(route, runId)
                if (currentPhase(route, runId) == RunPhase.Streaming) {
                    writeRun(route, runId, RunPhase.Completed, error = null, approval = null)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                closeStreamingMessage(route, runId)
                writeRun(route, runId, RunPhase.Failed, error = t.reason(), approval = null)
            } finally {
                jobs.remove(runId)
            }
        }
        jobs[runId] = job
        job.start()
    }

    private suspend fun apply(route: ConversationRoute, runId: String, event: RunEvent) {
        when (event) {
            is RunEvent.AssistantDelta -> {
                val row = runMessage(route, runId)
                store.messages.upsert(row.copy(text = row.text + event.delta, streaming = true))
            }
            is RunEvent.ToolStarted -> upsertTools(route, runId) { it + event.toolRun }
            is RunEvent.ToolCompleted -> upsertTools(route, runId) { tools ->
                tools.map { if (it.id == event.toolRun.id) event.toolRun else it }
            }
            is RunEvent.ApprovalRequired -> {
                closeStreamingMessage(route, runId)
                writeRun(route, runId, RunPhase.AwaitingApproval, error = null, approval = event.request)
            }
            is RunEvent.RunCompleted -> {
                val row = runMessage(route, runId)
                store.messages.upsert(
                    row.copy(
                        text = event.finalText.ifEmpty { row.text },
                        streaming = false,
                        pending = false,
                    )
                )
                writeRun(route, runId, RunPhase.Completed, error = null, approval = null)
            }
            is RunEvent.RunFailed -> {
                closeStreamingMessage(route, runId)
                writeRun(route, runId, RunPhase.Failed, error = event.reason, approval = null)
            }
        }
    }

    private suspend fun upsertTools(
        route: ConversationRoute,
        runId: String,
        transform: (List<ToolRun>) -> List<ToolRun>,
    ) {
        val row = runMessage(route, runId)
        val updated = transform(row.toolRunsJson.decodeToolRuns())
        store.messages.upsert(row.copy(toolRunsJson = updated.encode(), streaming = true))
    }

    /**
     * One row per run, created on demand. The PoC appended a placeholder and
     * then a second message, so every run rendered twice.
     */
    private suspend fun runMessage(route: ConversationRoute, runId: String): MessageEntity {
        store.messages.findRunMessage(route.gatewayId, route.profileId, route.sessionId, runId)?.let { return it }
        val fresh = MessageEntity(
            id = "msg-run-$runId",
            gatewayId = route.gatewayId,
            profileId = route.profileId,
            sessionId = route.sessionId,
            role = "assistant",
            text = "",
            toolRunsJson = "",
            createdAt = System.currentTimeMillis(),
            runId = runId,
            streaming = true,
            pending = false,
        )
        store.messages.upsert(fresh)
        return fresh
    }

    private suspend fun closeStreamingMessage(route: ConversationRoute, runId: String) {
        store.messages.findRunMessage(route.gatewayId, route.profileId, route.sessionId, runId)?.let {
            if (it.streaming) store.messages.upsert(it.copy(streaming = false))
        }
    }

    private suspend fun currentPhase(route: ConversationRoute, runId: String): RunPhase? =
        store.runs.find(route.gatewayId, route.profileId, route.sessionId, runId)
            ?.let { RunPhase.parse(it.state) }

    private suspend fun writeRun(
        route: ConversationRoute,
        runId: String,
        phase: RunPhase,
        error: String?,
        approval: ApprovalRequest?,
    ) {
        store.runs.upsert(
            RunEntity(
                gatewayId = route.gatewayId,
                profileId = route.profileId,
                sessionId = route.sessionId,
                runId = runId,
                state = phase.stored,
                cursor = null,
                error = error,
                approvalJson = approval?.encode(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}
