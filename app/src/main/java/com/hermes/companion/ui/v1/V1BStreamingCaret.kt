package com.hermes.companion.ui.v1

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Phase B · spec 1 — 1-px Indigo caret that blinks at 1 Hz.
 *
 * The HTML mock uses a step-end keyframe (1 → 0.12 over 50% of the
 * cycle). We replicate with a `keyframes` block:
 *   0%    opacity 1.0
 *   49%   opacity 1.0
 *   50%   opacity 0.12
 *   100%  opacity 0.12
 *
 * If [visible] is false we render a 0×0 box (no caret) so callers
 * don't need to conditionally include this composable.
 */
@Composable
fun V1BStreamingCaret(
    color: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "v1b-caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1.00f at 0
                1.00f at 499
                0.12f at 500
                0.12f at 1000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "v1b-caret-alpha",
    )
    // A small empty box collapses to zero size when not visible so the
    // caller's layout doesn't shift.
    if (!visible) {
        Box(modifier.size(0.dp))
        return
    }
    Box(
        modifier
            .size(width = 2.dp, height = 16.dp)
            .background(color.copy(alpha = alpha)),
    )
}

/**
 * Variant that smoothly fades opacity to 0.4 when token throughput
 * stalls (decision #1 — spec edge case: "<2 tokens/sec → caret fades").
 */
@Composable
fun V1BStreamingCaretSlow(
    color: Color,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) {
        Box(modifier.size(0.dp))
        return
    }
    val transition = rememberInfiniteTransition(label = "v1b-caret-slow")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "v1b-caret-slow-alpha",
    )
    Box(
        modifier
            .size(width = 2.dp, height = 16.dp)
            .background(color.copy(alpha = alpha)),
    )
}
