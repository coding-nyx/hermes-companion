package com.hermes.companion.domain

/** A persisted transcript message under a session. */
sealed interface Message {
    val id: String
    val sessionId: String
    val profileId: String
    val gatewayId: String
    val createdAt: Long

    data class User(
        override val id: String,
        override val sessionId: String,
        override val profileId: String,
        override val gatewayId: String,
        override val createdAt: Long,
        val text: String,
    ) : Message

    data class Assistant(
        override val id: String,
        override val sessionId: String,
        override val profileId: String,
        override val gatewayId: String,
        override val createdAt: Long,
        val text: String,
        val toolRuns: List<ToolRun> = emptyList(),
        val isStreaming: Boolean = false,
    ) : Message
}

/** A single tool invocation embedded in an assistant message. */
data class ToolRun(
    val id: String,
    val name: String,
    val status: ToolStatus,
    val input: String,
    val output: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
)

enum class ToolStatus { Pending, Running, Completed, Failed }
