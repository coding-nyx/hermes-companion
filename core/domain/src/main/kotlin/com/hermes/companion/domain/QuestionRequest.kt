package com.hermes.companion.domain

/**
 * An agent question — picks a branch, distinct from an approval which grants a
 * capability (`plan/09-parity/openclaw-node-app.md`). First answer wins across
 * devices; on expiry the stated default is taken.
 */
data class QuestionRequest(
    val questionId: String,
    val runId: String,
    val profileId: String,
    val gatewayId: String,
    val prompt: String,
    val options: List<QuestionOption> = emptyList(),
    val multiSelect: Boolean = false,
    val allowFreeText: Boolean = false,
    val defaultOptionId: String? = null,
    val expiresAt: Long? = null,
)

data class QuestionOption(
    val id: String,
    val label: String,
    val description: String? = null,
)
