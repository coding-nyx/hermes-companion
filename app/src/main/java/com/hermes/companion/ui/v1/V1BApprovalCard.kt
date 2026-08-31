package com.hermes.companion.ui.v1

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.ui.theme.Coral40
import com.hermes.companion.ui.theme.Coral80
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.PlexMono
import com.hermes.companion.ui.theme.Sand80
import com.hermes.companion.ui.theme.StatusOk

/**
 * Phase B · spec 7 — inline approval card.
 *
 * Renders an [V1BToolRunCard]-style surface with:
 *   - shield icon + verb + profile handle + elapsed caption
 *   - "It wants to" inner box with the call + risk text
 *   - meta rows (Risk / Reversible)
 *   - two big buttons (Approve · Deny)
 *   - lock-in chip strip (Once · This session · Always for verb · Always deny)
 *
 * After a decision the card collapses into a one-line Teal receipt
 * (decision #2: `file.write` on already-granted files is a Teal
 * receipt only — no card).
 *
 * Lock-in scope (decision #5): "Always for this verb" is per-profile
 * (matches the existing capability-grant model in CapabilityGrant).
 */
data class V1BApprovalDecision(
    val lockIn: V1BApprovalLockIn,
    val outcome: V1BApprovalOutcome,
)

enum class V1BApprovalLockIn { Once, Session, AlwaysForVerb, AlwaysDeny }

enum class V1BApprovalOutcome { Pending, Approved, Denied }

@Composable
fun V1BApprovalCard(
    run: V1BToolRun,
    profileHandle: String,
    elapsedMs: Long,
    riskText: String,
    reversible: Boolean,
    reversibleHint: String,
    lockIn: V1BApprovalLockIn = V1BApprovalLockIn.Once,
    decision: V1BApprovalOutcome = V1BApprovalOutcome.Pending,
    onDecision: (V1BApprovalDecision) -> Unit,
    onLockInChange: (V1BApprovalLockIn) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (decision) {
        V1BApprovalOutcome.Approved -> V1BApprovalReceipt(
            verb = run.verb,
            elapsedMs = elapsedMs,
            outcome = "approved",
            note = "Granted · ${lockIn.name.replace('_', ' ').lowercase()}",
            modifier = modifier,
        )
        V1BApprovalOutcome.Denied -> V1BApprovalReceipt(
            verb = run.verb,
            elapsedMs = elapsedMs,
            outcome = "denied",
            note = "Denied · $profileHandle will retry without it",
            modifier = modifier,
        )
        V1BApprovalOutcome.Pending -> V1BApprovalPending(
            run = run,
            profileHandle = profileHandle,
            elapsedMs = elapsedMs,
            riskText = riskText,
            reversible = reversible,
            reversibleHint = reversibleHint,
            lockIn = lockIn,
            onDecision = onDecision,
            onLockInChange = onLockInChange,
            modifier = modifier,
        )
    }
}

@Composable
private fun V1BApprovalPending(
    run: V1BToolRun,
    profileHandle: String,
    elapsedMs: Long,
    riskText: String,
    reversible: Boolean,
    reversibleHint: String,
    lockIn: V1BApprovalLockIn,
    onDecision: (V1BApprovalDecision) -> Unit,
    onLockInChange: (V1BApprovalLockIn) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Sand80.copy(alpha = 0.08f))
            .border(1.dp, Sand80.copy(alpha = 0.40f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShieldIcon()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    run.verb,
                    style = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, color = Sand80, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    "@$profileHandle · ${(elapsedMs / 1000.0)}s ago · awaiting your decision",
                    style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp, color = HermesColors.Muted),
                )
            }
            AwaitingShimmer()
        }

        // "It wants to"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(HermesColors.Background)
                .border(1.dp, HermesColors.Border, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "IT WANTS TO",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    color = HermesColors.Muted,
                    letterSpacing = 1.2.sp,
                ),
            )
            Text(
                run.description,
                style = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, color = Sand80),
            )
        }

        // Meta rows
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            MetaRow("Risk", riskText, color = Sand80)
            MetaRow("Reversible", reversibleHint, color = if (reversible) StatusOk else Coral80)
        }

        // Approve / Deny
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ApprovalBigButton(
                label = "Approve",
                color = StatusOk,
                filled = true,
                onClick = { onDecision(V1BApprovalDecision(lockIn, V1BApprovalOutcome.Approved)) },
                modifier = Modifier.weight(1f),
            )
            ApprovalBigButton(
                label = "Deny",
                color = Coral80,
                filled = false,
                onClick = { onDecision(V1BApprovalDecision(lockIn, V1BApprovalOutcome.Denied)) },
                modifier = Modifier.weight(1f),
            )
        }

        // Lock-in chips
        V1BApprovalLockInChips(
            value = lockIn,
            onChange = onLockInChange,
        )
    }
}

@Composable
private fun V1BApprovalReceipt(
    verb: String,
    elapsedMs: Long,
    outcome: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(StatusOk.copy(alpha = 0.06f))
            .border(1.dp, StatusOk.copy(alpha = 0.30f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = StatusOk, modifier = Modifier.size(14.dp))
        Text(
            "$verb · $outcome",
            style = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, color = StatusOk, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.weight(1f))
        Text(
            "${(elapsedMs / 1000.0)}s",
            style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp, color = HermesColors.Muted),
        )
    }
    Box(Modifier.fillMaxWidth().padding(top = 4.dp, start = 24.dp)) {
        Text(
            note,
            style = TextStyle(fontFamily = PlexMono, fontSize = 11.sp, color = HermesColors.Muted),
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = TextStyle(fontSize = 11.sp, color = HermesColors.Muted))
        Text(
            value,
            style = TextStyle(fontFamily = PlexMono, fontSize = 11.sp, color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShieldIcon() {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Sand80.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("🛡", style = TextStyle(fontSize = 14.sp))
    }
}

@Composable
private fun AwaitingShimmer() {
    val transition = rememberInfiniteTransition(label = "v1b-approve-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "v1b-approve-shimmer-alpha",
    )
    Text(
        "⚠ AWAITING",
        style = TextStyle(
            fontFamily = PlexMono,
            fontSize = 10.sp,
            color = Sand80.copy(alpha = alpha),
            letterSpacing = 1.2.sp,
        ),
    )
}

@Composable
private fun ApprovalBigButton(
    label: String,
    color: Color,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) color else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (filled) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Text(
                label,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

/**
 * Internal · the actual 4-option lock-in chip strip. Used by
 * [V1BApprovalCard] and by the [V1BApprovalLockInChips] facade.
 */
@Composable
internal fun ApprovalCardLockInStrip(
    value: V1BApprovalLockIn,
    onChange: (V1BApprovalLockIn) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "LOCK-IN",
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                color = HermesColors.Muted,
            ),
        )
        ApprovalChip(
            label = "Once",
            selected = value == V1BApprovalLockIn.Once,
            accent = HermesColors.Fg,
            onClick = { onChange(V1BApprovalLockIn.Once) },
        )
        ApprovalChip(
            label = "This session",
            selected = value == V1BApprovalLockIn.Session,
            accent = HermesColors.Fg,
            onClick = { onChange(V1BApprovalLockIn.Session) },
        )
        ApprovalChip(
            label = "Always for this verb",
            selected = value == V1BApprovalLockIn.AlwaysForVerb,
            accent = HermesColors.Primary,
            onClick = { onChange(V1BApprovalLockIn.AlwaysForVerb) },
        )
        Spacer(Modifier.weight(1f))
        ApprovalChip(
            label = "Always deny",
            selected = value == V1BApprovalLockIn.AlwaysDeny,
            accent = Coral40,
            onClick = { onChange(V1BApprovalLockIn.AlwaysDeny) },
        )
    }
}

@Composable
private fun ApprovalChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else HermesColors.Border,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                color = if (selected) accent else HermesColors.Muted,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}
