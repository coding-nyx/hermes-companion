package com.hermes.companion.ui.v1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.ui.theme.DisplayMedium
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.PlexMono
import com.hermes.companion.ui.theme.StatusOk
import com.hermes.companion.ui.theme.StatusWarn

/**
 * Full-screen Settings sheet (390 × 844 in the mock; we render edge-to-edge
 * so it adapts to phone or tablet).
 *
 * One sectioned list, ten groups: Account, Gateway, Profiles, Notification
 * routing, Voice, Pair as node (CTA card), Appearance, Diagnostics, Outbox,
 * About. Each row follows the same anatomy: icon + label + current value +
 * chevron. The "Pair as node" row is a CTA card rather than a chevron row
 * because it opens a multi-step flow.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun V1SettingsSheet(
    @Suppress("UNUSED_PARAMETER") vm: V1ShellViewModel,
    onDismiss: () -> Unit,
    onOpenPairAsNode: () -> Unit,
    onOpenOutbox: () -> Unit,
    onOpenDiscover: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Settings", style = DisplayMedium)
                    Text(
                        "@ash · gw-home",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Sections
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Section("Account") {
                    AccountRow(
                        initials = "A",
                        name = "Ash",
                        handle = "@ash",
                        meta = "nyx@hermes.local · paired 14 Mar",
                    )
                    Divider()
                    ChevronRow(
                        icon = Icons.Filled.Person,
                        label = "Change handle",
                        value = "@ash",
                        onClick = {},
                    )
                }

                Section("Gateway") {
                    GatewayRow("gw-home · mac-studio", "11 ms · wss · full", StatusOk, "Active")
                    Divider()
                    GatewayRow("gw-cloud", "stale 4m · degraded", StatusWarn, null, actionLabel = "Switch")
                    Divider()
                    AddRow(
                        icon = Icons.Filled.Devices,
                        label = "Add gateway",
                        onClick = onOpenDiscover,
                    )
                }

                Section("Profiles") {
                    SettingsProfileRow("A", "@ash · coder-lab", isActive = true, onEdit = {})
                    Divider()
                    SettingsProfileRow("M", "@misty · research-lab", isActive = false, onMakeActive = {})
                    Divider()
                    AddRow(
                        icon = Icons.Filled.Person,
                        label = "Add profile",
                        onClick = {},
                    )
                }

                Section("Notification routing") {
                    ChevronRow(
                        icon = Icons.Filled.NotificationsActive,
                        label = "Mode",
                        value = "ImportantOnly",
                        valueIsAccent = true,
                        onClick = {},
                    )
                    Divider()
                    ChevronRow(
                        icon = Icons.Filled.Tune,
                        label = "Per-package overrides",
                        value = "12 packages",
                        onClick = {},
                    )
                    Divider()
                    ChevronRow(
                        icon = Icons.Filled.Info,
                        label = "Reply with rules",
                        value = "4 rules",
                        onClick = {},
                    )
                }

                Section("Voice") {
                    ChevronRow(
                        icon = Icons.Filled.Headphones,
                        label = "TTS provider",
                        value = "edge · alloy",
                        onClick = {},
                    )
                    Divider()
                    ChevronRow(
                        icon = Icons.Filled.Mic,
                        label = "STT",
                        value = "whisper · en-IN",
                        onClick = {},
                    )
                    Divider()
                    PlainRow(
                        icon = Icons.Filled.Bolt,
                        label = "Playback speed",
                        value = "1.1×",
                    )
                }

                // CTA card (different shape from the chevron rows above)
                Section("Pair this phone") {
                    PairNodeCta(onClick = onOpenPairAsNode)
                }

                Section("More") {
                    ChevronRow(
                        icon = Icons.Filled.WbSunny,
                        label = "Appearance",
                        value = "Night · Indigo",
                        onClick = {},
                    )
                    Divider()
                    ChevronRow(
                        icon = Icons.Filled.Info,
                        label = "Diagnostics",
                        value = "healthy",
                        valueIsAccent = true,
                        onClick = {},
                    )
                    Divider()
                    ChevronRow(
                        icon = Icons.Filled.Outbox,
                        label = "Outbox",
                        value = "2 pending",
                        valueIsAccent = true,
                        onClick = onOpenOutbox,
                    )
                    Divider()
                    ChevronRow(
                        icon = Icons.Filled.Info,
                        label = "About",
                        value = "v0.4.1 · build 412",
                        onClick = {},
                    )
                }

                Box(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
        ) {
            content()
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun AccountRow(initials: String, name: String, handle: String, meta: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(HermesColors.Primary.copy(alpha = 0.20f))
                .border(1.dp, HermesColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, color = HermesColors.Primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    handle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PlexMono,
                        fontSize = 12.sp,
                    ),
                    color = HermesColors.Primary,
                )
            }
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Chevron()
    }
}

@Composable
private fun ChevronRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    valueIsAccent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (valueIsAccent) HermesColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Chevron()
    }
}

@Composable
private fun PlainRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono, fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun GatewayRow(
    label: String,
    meta: String,
    dotColor: androidx.compose.ui.graphics.Color,
    badge: String?,
    actionLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = if (dotColor == StatusWarn) StatusWarn else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(StatusOk.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    badge.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PlexMono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                    ),
                    color = StatusOk,
                )
            }
        }
        if (actionLabel != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, HermesColors.Primary.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                    .clickable { /* TODO: switch gateway */ }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = HermesColors.Primary,
                )
            }
        }
    }
}

@Composable
private fun SettingsProfileRow(
    initials: String,
    label: String,
    isActive: Boolean,
    onMakeActive: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    val tone = if (isActive) HermesColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = 0.16f))
                .border(1.dp, tone.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, color = tone, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (isActive) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(HermesColors.Primary.copy(alpha = 0.14f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    "Active".uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PlexMono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                    ),
                    color = HermesColors.Primary,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, HermesColors.Primary.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onMakeActive)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "Make active",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = HermesColors.Primary,
                )
            }
        }
    }
}

@Composable
private fun AddRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = HermesColors.Primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = HermesColors.Primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PairNodeCta(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StatusOk.copy(alpha = 0.14f))
                .border(1.dp, StatusOk.copy(alpha = 0.30f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Devices,
                contentDescription = null,
                tint = StatusOk,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Pair as a node on gw-home",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                "Not paired · discover nearby or enter URL",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp),
        )
    }
}
