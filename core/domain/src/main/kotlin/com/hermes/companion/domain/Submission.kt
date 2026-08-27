package com.hermes.companion.domain

/**
 * One outbound user submission, journaled before it touches the network so a
 * reconnect can replay it under the same [idempotencyKey]
 * (`plan/05-reliability/offline-behavior.md`). [SubmissionState.Unacknowledged]
 * is the load-bearing "written and transmitted, no answer" state — never
 * silently retried, never marked sent.
 */
data class Submission(
    val id: String,
    val gatewayId: String,
    val profileId: String,
    val sessionId: String,
    val text: String,
    val idempotencyKey: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val state: SubmissionState = SubmissionState.Queued,
    val runId: String? = null,
    val expiresAt: Long? = null,
    val attachmentBytes: Long = 0,
)

enum class SubmissionState { Queued, Sent, Acknowledged, Unacknowledged, Expired, Failed }
