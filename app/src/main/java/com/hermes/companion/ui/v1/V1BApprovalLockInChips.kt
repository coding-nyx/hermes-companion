package com.hermes.companion.ui.v1

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Phase B · spec 7 — public facade for the 4-option lock-in chip
 * strip. The canonical implementation lives in [V1BApprovalCard]
 * (co-located with the rest of the approval UX). This thin facade
 * exists to give the Phase A shell a discoverable, single-purpose
 * import when the chips need to be rendered outside the card (e.g.
 * the Approvals queue in the ContextPanel, or a follow-up sheet).
 */
@Composable
fun V1BApprovalLockInChips(
    value: V1BApprovalLockIn,
    onChange: (V1BApprovalLockIn) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Forward to the canonical composable defined in V1BApprovalCard.kt.
    ApprovalCardLockInStrip(value, onChange, modifier)
}
