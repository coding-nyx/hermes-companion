package com.hermes.companion.ui.v1

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.ui.theme.DisplayMedium
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.PlexMono
import com.hermes.companion.ui.theme.StatusOk

/**
 * NewThreadDialog — invoked from the composer "+" button.
 *
 * Composition:
 *   - Title (required)
 *   - First message (optional, larger multi-line field)
 *   - Initial agent mode chips: Auto / Plan / Code / Research (default: Auto)
 *   - Route preview capsule (gw › @profile › new)
 *   - Locked-model toggle
 *   - Primary action: Create thread
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V1NewThreadDialog(
    vm: V1ShellViewModel,
    onDismiss: () -> Unit,
) {
    val fleet by vm.fleet.collectAsStateWithLifecycle()
    val activeRoute by vm.activeRoute.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf("") }
    var firstMessage by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(InitialMode.Auto) }
    var locked by remember { mutableStateOf(true) }

    // Pick the first available profile, falling back to "ash" for the demo.
    val targetGatewayId = activeRoute?.gatewayId
        ?: fleet.gateways.firstOrNull()?.gateway?.id
        ?: "gw-home"
    val targetProfileId = activeRoute?.profileId
        ?: fleet.gateways.firstOrNull()?.profiles?.firstOrNull()?.profile?.profileId
        ?: "ash"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Drag handle
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)),
            )

            // Title row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("New thread", style = DisplayMedium)
                    Text(
                        "on @${targetProfileId} · ${targetGatewayId}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Title (required)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TITLE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PlexMono,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        " *",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PlexMono,
                            fontSize = 10.sp,
                        ),
                        color = com.hermes.companion.ui.theme.StatusError,
                    )
                }
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Evening handoff",
                    height = 48.dp,
                    accent = true,
                )
            }

            // First message
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "FIRST MESSAGE ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PlexMono,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "(optional)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PlexMono,
                            fontSize = 10.sp,
                            letterSpacing = 0.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    Box(Modifier.weight(1f))
                    Text(
                        "⏎ sends",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PlexMono,
                            fontSize = 10.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextField(
                    value = firstMessage,
                    onValueChange = { firstMessage = it },
                    placeholder = "Catch me up on the @knight work and prep a 9pm standup note for tomorrow.",
                    height = 84.dp,
                    accent = false,
                    singleLine = false,
                )
            }

            // Initial mode chips
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "INITIAL MODE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PlexMono,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InitialMode.entries.forEach { m ->
                        ModeChip(
                            label = m.label,
                            selected = mode == m,
                            onClick = { mode = m },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    "Mode steers the first run. You can switch anytime by sending \"switch to plan\" in the thread.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }

            // Locked-model toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (locked) StatusOk.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .border(
                        1.dp,
                        if (locked) StatusOk.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { locked = !locked }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .border(
                            1.6.dp,
                            if (locked) StatusOk else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(5.dp),
                        )
                        .background(if (locked) StatusOk.copy(alpha = 0.35f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (locked) {
                        Text(
                            "✓",
                            color = com.hermes.companion.ui.theme.HermesColors.Fg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (locked) "Locked to opus-4.6" else "Follows the profile default",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        if (locked) {
                            "This thread keeps the same model across every run, even if the profile default moves under it."
                        } else {
                            "Runs use whatever the profile resolves at execution time. Fine for chat, risky for a long thread you compare over weeks."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Route preview + CTA
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RoutePreview(
                    gatewayId = targetGatewayId,
                    profileHandle = targetProfileId,
                    title = title.ifBlank { "new" },
                    modeLabel = mode.label.lowercase(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(HermesColors.Primary)
                        .clickable(enabled = title.isNotBlank()) {
                            vm.createThread(
                                gatewayId = targetGatewayId,
                                profileId = targetProfileId,
                                title = title.trim().ifBlank { "New chat" },
                                initialMode = mode.name,
                                lockedToModel = locked,
                                firstMessage = firstMessage.takeIf { it.isNotBlank() },
                            )
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Create thread",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        color = HermesColors.OnPrimary,
                    )
                }
            }

            Box(Modifier.size(8.dp))
        }
    }
}

private enum class InitialMode(val label: String) {
    Auto("Auto"),
    Plan("Plan"),
    Code("Code"),
    Research("Research"),
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) HermesColors.Primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                1.dp,
                if (selected) HermesColors.Primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = if (selected) HermesColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoutePreview(
    gatewayId: String,
    profileHandle: String,
    title: String,
    modeLabel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "creates",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PlexMono,
                fontSize = 12.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            gatewayId,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PlexMono,
                fontSize = 12.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "›",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
        Text(
            "@$profileHandle",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PlexMono,
                fontSize = 12.sp,
            ),
            color = HermesColors.Primary,
        )
        Text(
            "›",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
        Text(
            title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PlexMono,
                fontSize = 12.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(HermesColors.Primary.copy(alpha = 0.16f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                modeLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                ),
                color = HermesColors.Primary,
            )
        }
    }
}

@Composable
private fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    height: androidx.compose.ui.unit.Dp,
    accent: Boolean,
    singleLine: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                if (accent) HermesColors.Primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = if (singleLine) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (singleLine) 1 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = if (singleLine) androidx.compose.ui.text.font.FontFamily.Default else androidx.compose.ui.text.font.FontFamily.Default,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(HermesColors.Fg),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
