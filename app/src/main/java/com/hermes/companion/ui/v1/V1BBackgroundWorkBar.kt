package com.hermes.companion.ui.v1

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.ui.theme.Coral80
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.Indigo80
import com.hermes.companion.ui.theme.PlexMono
import com.hermes.companion.ui.theme.Sand80
import com.hermes.companion.ui.theme.StatusOk

/**
 * Phase B · spec 3 — background-work indicator.
 *
 * Three sub-states, all sharing the 36-dp row above the route capsule:
 *   drafting   → Indigo (model producing tokens, shimmer overlay)
 *   calling    → Teal    (in tool-loop, shows the queue)
 *   awaiting   → Sand    (blocked on user decision)
 *
 * The bar collapses into a 48-dp pill when the user scrolls up
 * (see [V1BBackgroundWorkPill]). Threshold (decision #7): 80 dp on
 * tablet, 48 dp on phone — caller computes this from the scroll
 * state and passes `collapsed = true`.
 */
enum class V1BBackgroundWorkKind { Drafting, CallingTools, Awaiting }

data class V1BBackgroundWorkState(
    val kind: V1BBackgroundWorkKind,
    val label: String,
    val elapsedMs: Long,
    val tokensOrSteps: String? = null,
    val queueSummary: String? = null,
    val awaitingVerb: String? = null,
)

@Composable
fun V1BBackgroundWorkBar(
    state: V1BBackgroundWorkState?,
    collapsed: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    if (collapsed) return // caller renders the pill instead

    val tint = when (state.kind) {
        V1BBackgroundWorkKind.Drafting -> Indigo80
        V1BBackgroundWorkKind.CallingTools -> StatusOk
        V1BBackgroundWorkKind.Awaiting -> Sand80
    }
    val bg = tint.copy(alpha = when (state.kind) {
        V1BBackgroundWorkKind.Drafting -> 0.10f
        V1BBackgroundWorkKind.CallingTools -> 0.08f
        V1BBackgroundWorkKind.Awaiting -> 0.10f
    })

    Column(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clickable(onClick = onTap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            V1BPulsingDot(tint)
            Text(
                state.label,
                style = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, color = tint),
            )
            Text(
                "${(state.elapsedMs / 1000.0)}s${state.tokensOrSteps?.let { " · $it" } ?: ""}",
                style = TextStyle(fontFamily = PlexMono, fontSize = 11.sp, color = HermesColors.Muted),
            )
            if (!state.queueSummary.isNullOrBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        state.queueSummary,
                        style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp, color = HermesColors.Muted),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.kind == V1BBackgroundWorkKind.Awaiting) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Indigo80.copy(alpha = 0.30f))
                        .border(1.dp, Indigo80, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Review →", style = TextStyle(fontSize = 11.sp, color = Indigo80))
                }
            } else {
                Text(
                    "Stop",
                    style = TextStyle(fontFamily = PlexMono, fontSize = 11.sp, color = Coral80),
                    modifier = Modifier.clickable(onClick = onTap),
                )
            }
        }
        if (state.kind == V1BBackgroundWorkKind.Drafting) {
            V1BShimmerProgress()
        }
    }
}

@Composable
fun V1BBackgroundWorkPill(
    state: V1BBackgroundWorkState?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    isTablet: Boolean = false,
) {
    if (state == null) return
    val pillHeight = if (isTablet) 56.dp else 44.dp
    val tint = when (state.kind) {
        V1BBackgroundWorkKind.Drafting -> Indigo80
        V1BBackgroundWorkKind.CallingTools -> StatusOk
        V1BBackgroundWorkKind.Awaiting -> Sand80
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(pillHeight)
            .clip(RoundedCornerShape(999.dp))
            .background(HermesColors.Surface)
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        V1BPulsingDot(tint)
        Text("Agent working", style = TextStyle(fontSize = 13.sp, color = HermesColors.Fg))
        Text(
            "${state.label} · ${(state.elapsedMs / 1000.0)}s",
            style = TextStyle(fontFamily = PlexMono, fontSize = 11.sp, color = HermesColors.Muted),
        )
        Spacer(Modifier.weight(1f))
        Text("⊕ expand", style = TextStyle(fontFamily = PlexMono, fontSize = 11.sp, color = Indigo80))
    }
}

@Composable
private fun V1BPulsingDot(tint: Color) {
    val transition = rememberInfiniteTransition(label = "v1b-bg-dot")
    val scale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "v1b-bg-dot-scale",
    )
    Box(
        Modifier
            .size(10.dp)
            .padding(((1f - scale) * 1.5f).coerceAtLeast(0f).dp)
            .clip(RoundedCornerShape(999.dp))
            .background(tint),
    )
}

@Composable
private fun V1BShimmerProgress() {
    val transition = rememberInfiniteTransition(label = "v1b-bg-shimmer")
    val shift by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "v1b-bg-shimmer-shift",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(HermesColors.Elevated),
    ) {
        Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val w = size.width
            val grad = Brush.linearGradient(
                colors = listOf(Color.Transparent, Indigo80.copy(alpha = 0.55f), Color.Transparent),
                start = Offset(w * shift, 0f),
                end = Offset(w * (shift + 1f), 0f),
            )
            drawRect(brush = grad)
        }
    }
}
