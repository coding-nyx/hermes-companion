package com.hermes.companion.ui.v1

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.ui.theme.Coral40
import com.hermes.companion.ui.theme.Coral80
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.PlexMono
import kotlinx.coroutines.delay

/**
 * Phase B · spec 6 — full-width voice recording overlay anchored at
 * the bottom of the chat surface while the user holds the mic.
 *
 * Anatomy (from the mock):
 *   - 12-bar live waveform (red, animated)
 *   - pulsing red dot
 *   - elapsed timer (mono)
 *   - stop button (coral)
 *   - hint: "Release to attach · slide up to cancel"
 *
 * The composable owns its own elapsed-time ticker via [LaunchedEffect]
 * — callers pass the [onStop] / [onCancel] callbacks. Audio capture
 * itself is the parent shell's responsibility (decoupled for test).
 */
@Composable
fun V1BVoiceRecordingOverlay(
    onStop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isCancelArmed: Boolean = false,
) {
    var elapsedMs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            delay(100)
            elapsedMs = System.currentTimeMillis() - start
        }
    }
    val animatedArmed by animateFloatAsState(
        targetValue = if (isCancelArmed) 1f else 0f,
        animationSpec = tween(180),
        label = "v1b-voice-armed",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HermesColors.Background)
            .border(1.dp, if (isCancelArmed) Coral40 else HermesColors.Border, RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PulsingDot()
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black.copy(alpha = 0.10f + animatedArmed * 0.05f))
                    .border(
                        width = 1.dp,
                        color = if (isCancelArmed) Coral40 else Coral80.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                V1BLiveWaveform(modifier = Modifier.fillMaxWidth().padding(end = 56.dp))
                Text(
                    formatElapsed(elapsedMs),
                    style = TextStyle(
                        fontFamily = PlexMono,
                        fontSize = 12.sp,
                        color = if (isCancelArmed) Coral80 else HermesColors.Muted,
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Coral40)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(HermesColors.OnPrimary),
                )
            }
        }
        Text(
            if (isCancelArmed) "Release to cancel" else "Release to attach · slide up to cancel",
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                color = if (isCancelArmed) Coral80 else HermesColors.Muted,
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "v1b-voice-dot")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "v1b-voice-dot-scale",
    )
    // Convert scale overshoot (0.85..1.2) into a small inset so the dot
    // visibly pulses. coerceIn to avoid negative padding when scale>1.
    val padDp = (((scale - 1f).coerceIn(0f, 0.4f)) * 6f).dp
    Box(
        Modifier
            .size(10.dp)
            .padding(padDp)
            .clip(CircleShape)
            .background(Coral40),
    )
}

/**
 * 12-bar live waveform used in the body of [V1BVoiceRecordingOverlay].
 * Each bar is a vertical column with an independently animated height so
 * the bar looks like a real-time audio meter, not a rigid equaliser.
 */
@Composable
private fun V1BLiveWaveform(modifier: Modifier = Modifier, barColor: Color = Coral40) {
    val barCount = 12
    val transition = rememberInfiniteTransition(label = "v1b-voice-wave")
    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(barCount) { i ->
            val phase = (i * 53) % 360
            val heightFrac by transition.animateFloat(
                initialValue = 0.20f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600 + (i * 37) % 250),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "v1b-voice-wave-bar-$i",
            )
            // Optional per-bar phase is honoured by varying the spec.
            @Suppress("UNUSED_VARIABLE") val unusedPhase = phase
            Box(
                Modifier
                    .size(width = 2.dp, height = (4f + heightFrac * 22f).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor),
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val s = (ms / 1000).toInt()
    val mm = s / 60
    val ss = s % 60
    return "%d:%02d".format(mm, ss)
}
