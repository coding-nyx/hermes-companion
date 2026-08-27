package com.hermes.companion.domain

/**
 * Streamed event for an agent run. Surface matches the Hermes contract used
 * in §4 of the plan: `assistant.delta / tool.started / tool.completed /
 * run.completed / run.approval_required`.
 */
sealed interface RunEvent {
    val runId: String
    val sessionId: String

    data class AssistantDelta(
        override val runId: String,
        override val sessionId: String,
        val delta: String,
    ) : RunEvent

    data class ToolStarted(
        override val runId: String,
        override val sessionId: String,
        val toolRun: ToolRun,
    ) : RunEvent

    data class ToolCompleted(
        override val runId: String,
        override val sessionId: String,
        val toolRun: ToolRun,
    ) : RunEvent

    data class ApprovalRequired(
        override val runId: String,
        override val sessionId: String,
        val request: ApprovalRequest,
    ) : RunEvent

    data class RunCompleted(
        override val runId: String,
        override val sessionId: String,
        val finalText: String,
    ) : RunEvent

    data class RunFailed(
        override val runId: String,
        override val sessionId: String,
        val reason: String,
    ) : RunEvent
}
