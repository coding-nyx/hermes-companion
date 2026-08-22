package com.hermes.companion.domain

/**
 * A request-bound approval surfaced to the operator. Choices are exactly
 * the four Hermes offered — `once`, `session`, `always`, `deny` — per §10.
 */
data class ApprovalRequest(
    val requestId: String,
    val runId: String,
    val profileId: String,
    val gatewayId: String,
    val command: String,
    val digest: String,
    val options: List<ApprovalOption> = listOf(
        ApprovalOption.Once,
        ApprovalOption.Session,
        ApprovalOption.Always,
        ApprovalOption.Deny,
    ),
)

enum class ApprovalOption(val label: String) {
    Once("Once"),
    Session("Session"),
    Always("Always"),
    Deny("Deny"),
}
