package com.hermes.companion.domain

data class Session(
    val sessionId: String,
    val profileId: String,
    val gatewayId: String,
    val title: String,
    val modelLock: String? = null,
    val runState: RunState = RunState.Idle,
    val unreadCount: Int = 0,
)

enum class RunState { Idle, Streaming, AwaitingApproval, Completed, Failed }
