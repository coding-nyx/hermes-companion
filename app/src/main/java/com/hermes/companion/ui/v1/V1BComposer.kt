package com.hermes.companion.ui.v1

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.ui.components.HermesField
import com.hermes.companion.ui.theme.Coral40
import com.hermes.companion.ui.theme.Coral80
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.Indigo80
import com.hermes.companion.ui.theme.PlexMono

/**
 * Phase B · spec 6 — composer with three morph states for the send
 * button:
 *
 * | State       | Send button | Field height |
 * |-------------|-------------|---------------|
 * | Empty       | mic         | 44 dp         |
 * | Has text    | arrow       | 44 dp → 160 dp|
 * | Streaming   | stop (coral)| 44 dp         |
 * | Recording   | stop (coral)| 44 dp, waveform |
 *
 * The left "+" button opens the [V1BAttachmentSheet]; ⌘K / Ctrl+K
 * opens the [V1BQuickProfilePalette]. Hold-to-talk on the mic fires
 * [onMicDown] / [onMicUp]; slide-up-to-cancel is handled via
 * [onMicCancel] from a vertical-drag detector.
 *
 * This is a *new* composable for Phase B; the Phase A `Composer` in
 * V1ChatSurface.kt is a simpler row that doesn't morph. Phase A can
 * be migrated later by replacing `private fun Composer(...)` with a
 * call to `V1BComposer(...)`.
 */
@Composable
fun V1BComposer(
    draft: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicDown: () -> Unit = {},
    onMicUp: () -> Unit = {},
    onMicCancel: () -> Unit = {},
    onAttachTap: () -> Unit = {},
    onProfilePalette: () -> Unit = {},
    isStreaming: Boolean = false,
    isRecording: Boolean = false,
    targetLabel: String = "@ash",
    modifier: Modifier = Modifier,
) {
    var cancelArmed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HermesColors.Background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = if (draft.isBlank()) Alignment.CenterVertically else Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // "+" attach button
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(HermesColors.Surface)
                    .border(1.dp, HermesColors.Border, CircleShape)
                    .clickable(onClick = onAttachTap),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Attach", tint = HermesColors.Fg, modifier = Modifier.size(20.dp))
            }

            // Field — multi-line when has text
            ComposerField(
                draft = draft,
                isRecording = isRecording,
                cancelArmed = cancelArmed,
                targetLabel = targetLabel,
                onChange = onChange,
                onPalette = onProfilePalette,
                modifier = Modifier.weight(1f),
            )

            // Send / mic / stop button — morphs by state
            ComposerSendButton(
                draft = draft,
                isStreaming = isStreaming,
                isRecording = isRecording,
                onMicDown = onMicDown,
                onMicUp = onMicUp,
                onMicCancel = onMicCancel,
                onSend = onSend,
                onCancelArmedChange = { cancelArmed = it },
            )
        }
        if (isRecording) {
            Text(
                if (cancelArmed) "Release to cancel" else "Release to attach · slide up to cancel",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    color = if (cancelArmed) Coral80 else HermesColors.Muted,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ComposerField(
    draft: String,
    isRecording: Boolean,
    cancelArmed: Boolean,
    targetLabel: String,
    onChange: (String) -> Unit,
    onPalette: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = when {
        isRecording && cancelArmed -> Coral40
        isRecording -> Coral80.copy(alpha = 0.45f)
        else -> HermesColors.Border
    }
    Box(
        modifier
            .height(if (draft.isBlank()) 44.dp else 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(HermesColors.Surface)
            .border(if (isRecording) 1.dp else 1.dp, border, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            isRecording -> V1BLiveWaveform(modifier = Modifier.fillMaxWidth().padding(end = 56.dp))
            draft.isBlank() -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Send to $targetLabel",
                    style = TextStyle(fontSize = 14.sp, color = HermesColors.Muted.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f).clickable(onClick = onPalette),
                )
                Text(
                    "⌘K",
                    style = TextStyle(fontFamily = PlexMono, fontSize = 10.sp, color = HermesColors.Muted),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(HermesColors.Surface)
                        .border(1.dp, HermesColors.Border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            else -> HermesField(
                value = draft,
                onValueChange = onChange,
                placeholder = "",
                singleLine = false,
                multiLine = true,
                maxHeight = 160.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ComposerSendButton(
    draft: String,
    isStreaming: Boolean,
    isRecording: Boolean,
    onMicDown: () -> Unit,
    onMicUp: () -> Unit,
    onMicCancel: () -> Unit,
    onSend: () -> Unit,
    onCancelArmedChange: (Boolean) -> Unit,
) {
    val bg = when {
        isStreaming -> Coral40
        isRecording -> Coral40
        draft.isNotBlank() -> Indigo80
        else -> HermesColors.Surface
    }
    val border = if (isStreaming || isRecording) Color.Transparent else HermesColors.Border
    val iconTint = when {
        isStreaming || isRecording -> HermesColors.OnPrimary
        draft.isNotBlank() -> HermesColors.OnPrimary
        else -> HermesColors.Fg
    }
    val micGlyph = draft.isBlank() && !isStreaming && !isRecording
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .let { mod ->
                if (micGlyph) {
                    mod.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onMicDown()
                                val released = tryAwaitRelease()
                                if (released) onMicUp() else onMicCancel()
                            },
                        )
                    }
                } else if (isRecording) {
                    mod.pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { onCancelArmedChange(false); onMicCancel() },
                            onDragCancel = { onCancelArmedChange(false) },
                            onVerticalDrag = { _: androidx.compose.ui.input.pointer.PointerInputChange, dragY: Float ->
                                if (dragY < -44f) onCancelArmedChange(true)
                                else if (dragY > 0f) onCancelArmedChange(false)
                            },
                        )
                    }
                } else {
                    mod.clickable {
                        if (!isStreaming && draft.isNotBlank()) onSend()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isStreaming -> StopIcon(HermesColors.OnPrimary)
            isRecording -> StopIcon(HermesColors.OnPrimary)
            draft.isNotBlank() -> Icon(Icons.Filled.Send, contentDescription = "Send", tint = iconTint, modifier = Modifier.size(20.dp))
            else -> Icon(Icons.Filled.Mic, contentDescription = "Voice input", tint = iconTint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StopIcon(tint: Color) {
    Box(
        Modifier
            .size(width = 14.dp, height = 14.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(tint),
    )
}

@Composable
private fun V1BLiveWaveform(modifier: Modifier = Modifier) {
    // Twelve bars with random-looking static heights. Distinct
    // durations give a shimmering effect; amplitude is multiplied
    // into the height for a quick tween.
    val bars = listOf(8, 16, 11, 20, 7, 14, 18, 9, 22, 12, 17, 6)
    val durations = listOf(700, 800, 600, 900, 700, 800, 650, 750, 850, 600, 800, 700)
    Row(
        modifier = modifier.height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        bars.forEachIndexed { i, heightDp ->
            val infinite = rememberInfiniteTransition(label = "v1b-wave-$i")
            val scaleY: Float by infinite.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durations[i]),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "v1b-wave-scale-$i",
            )
            // Promote Int→Float explicitly to avoid `Int * Float` overload
            // ambiguity when computing height in dp.
            val animatedHeight = (heightDp * scaleY).dp
            Box(
                Modifier
                    .size(width = 2.dp, height = animatedHeight)
                    .clip(CircleShape)
                    .background(Coral80),
            )
        }
    }
}
