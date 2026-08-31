package com.hermes.companion.ui.v1

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.ui.components.HermesField
import com.hermes.companion.ui.theme.Coral40
import com.hermes.companion.ui.theme.Coral80
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.Indigo80
import com.hermes.companion.ui.theme.PlexMono

/**
 * Phase B · spec 4 — edit-mode message field.
 *
 * Replaces a user bubble while editing. Anatomy:
 *   - Indigo-bordered multi-line [HermesField] with the new text
 *   - footer: "⌘⏎ save · esc cancel" hint + Cancel + Save & rerun
 *   - Ghost preview of the original text, struck-through at 0.45 opacity
 *   - Coral "truncated below" divider
 *
 * The EditField is the only place edit-mode owns the new value — the
 * parent should swap the bubble out for this composable and call
 * [onSaveRerun] with the new text on commit.
 */
@Composable
fun V1BMessageEditField(
    originalText: String,
    editedText: String,
    onEditedChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSaveRerun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // Header status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(end = 4.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Indigo80),
            )
            Text(
                "Editing · original will be truncated below",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    color = Indigo80,
                ),
            )
        }

        // Edit field
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(HermesColors.Surface)
                .border(1.5.dp, Indigo80, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HermesField(
                value = editedText,
                onValueChange = onEditedChange,
                placeholder = originalText,
                singleLine = false,
                multiLine = true,
                maxHeight = 140.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⌘⏎ save · esc cancel",
                    style = TextStyle(
                        fontFamily = PlexMono,
                        fontSize = 10.sp,
                        color = HermesColors.Muted,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onCancel)
                        .border(1.dp, HermesColors.Border, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Cancel", style = TextStyle(fontSize = 12.sp, color = HermesColors.Muted))
                }
                Spacer(Modifier.size(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Indigo80)
                        .clickable(onClick = onSaveRerun)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = HermesColors.OnPrimary, modifier = Modifier.size(12.dp))
                        Text(
                            "Save & rerun",
                            style = TextStyle(fontSize = 12.sp, color = HermesColors.OnPrimary, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }

        // Ghost preview of the original (struck through)
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .padding(end = 4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                originalText,
                style = TextStyle(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = HermesColors.Muted,
                    textDecoration = TextDecoration.LineThrough,
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Coral truncation marker
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Coral40.copy(alpha = 0.6f)),
            )
            Text(
                "TRUNCATED BELOW",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    color = Coral80,
                    letterSpacing = 1.2.sp,
                ),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Coral40.copy(alpha = 0.6f)),
            )
        }
    }
}
