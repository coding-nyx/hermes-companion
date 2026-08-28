package com.hermes.companion.ui.node

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.hermes.companion.ui.theme.StatusDim
import com.hermes.companion.ui.theme.StatusError
import com.hermes.companion.ui.theme.StatusOk
import com.hermes.companion.ui.theme.StatusWarn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.hermes.companion.data.repo.CapabilityStatus
import com.hermes.companion.data.repo.HardwareLease
import com.hermes.companion.data.repo.NodeCapabilityItem
import com.hermes.companion.data.repo.PrivacyLogEntry
import com.hermes.companion.data.repo.deepLinkFor
import com.hermes.companion.data.repo.isRuntimePermissionRequest
import com.hermes.companion.ui.components.HermesCard
import com.hermes.companion.ui.components.MetaText
import com.hermes.companion.ui.components.SectionHeader
import com.hermes.companion.ui.components.StatusDot

private val Teal = StatusOk
private val Sand = StatusWarn
private val Coral = StatusError
private val LimitedColor = StatusDim

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeScreen(
    onOpenSetup: () -> Unit = {},
    onOpenGrants: () -> Unit = {},
    onOpenStreamRules: () -> Unit = {},
    vm: NodeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val node = state.node
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onOpenSetup, modifier = Modifier.weight(1f)) {
                Text("Full Node Mode")
            }
            OutlinedButton(onClick = onOpenGrants, modifier = Modifier.weight(1f)) {
                Text("Grants")
            }
        }
        OutlinedButton(onClick = onOpenStreamRules, modifier = Modifier.fillMaxWidth()) {
            Text("What this phone streams")
        }

        NodePairingSection(vm)
        Text("Node", style = MaterialTheme.typography.titleLarge)

        // Device summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = Teal,
                        modifier = Modifier.size(20.dp),
                    )
                    Box(Modifier.size(8.dp))
                    Text(
                        node.nodeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Teal.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            node.brokerStatus,
                            color = Teal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StatPair("node", node.nodeId)
                    StatPair("seq", node.sequence.toString())
                    StatPair("battery", node.batteryMode)
                    StatPair("link", node.linkType)
                }
            }
        }

        // Grant-all-missing shortcut: open the first settings panel for any
        // permission that's still "Needs permission" on this device. Runtime
        // permissions are requested in the VM one at a time via the launcher.
        val context = LocalContext.current
        val missingCount = node.capabilities.count { it.status == CapabilityStatus.MissingPermission }
        if (missingCount > 0) {
            Button(
                onClick = { vm.grantFirstMissing(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Grant $missingCount missing") }
        }

        // Filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilterChip(
                selected = state.filter == null,
                onClick = { vm.setFilter(null) },
                label = { Text("All") },
            )
            FilterChip(
                selected = state.filter == CapabilityStatus.Working,
                onClick = { vm.setFilter(CapabilityStatus.Working) },
                label = { Text("Working") },
            )
            FilterChip(
                selected = state.filter == CapabilityStatus.MissingPermission,
                onClick = { vm.setFilter(CapabilityStatus.MissingPermission) },
                label = { Text("Needs permission") },
            )
            FilterChip(
                selected = state.filter == CapabilityStatus.OsLimited,
                onClick = { vm.setFilter(CapabilityStatus.OsLimited) },
                label = { Text("OS-limited") },
            )
        }

        // Coverage Matrix Section
        val workingCount = node.capabilities.count { it.status == CapabilityStatus.Working }
        val restCount = node.capabilities.size - workingCount
        SectionHeader("Coverage — $workingCount working, $restCount not")

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.filteredCapabilities.forEach { cap ->
                CapabilityRow(cap)
            }
        }

        // Exclusive hardware leases
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Exclusive hardware")
                    Box(Modifier.weight(1f))
                    Text(
                        "one holder at a time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }

                node.leases.forEach { lease ->
                    LeaseRow(lease)
                }
            }
        }

        // Action Buttons: Run Canary / Reconcile / Revoke
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = { vm.runCanary() },
                enabled = !node.canaryRunning,
                modifier = Modifier.weight(1f),
            ) {
                if (node.canaryRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Box(Modifier.size(6.dp))
                    Text("Running…")
                } else {
                    Text(if (node.canaryPassed) "Test again" else "Run end-to-end test")
                }
            }

            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }) {
                Text("Notification access")
            }
        }

        // Canary Results Card
        if (node.canaryPassed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Teal.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Teal, modifier = Modifier.size(18.dp))
                        Box(Modifier.size(6.dp))
                        Text(
                            "End-to-end canary passed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Teal,
                        )
                    }
                    node.canarySteps.forEach { step ->
                        Text(
                            "• $step",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        // Privacy Log Section
        SectionHeader("What left this phone", modifier = Modifier.padding(top = 4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            node.privacyLog.forEach { entry ->
                PrivacyLogRow(entry)
            }
        }
    }
}

@Composable
private fun StatPair(key: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$key: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
        MetaText(value, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
    }
}

/**
 * One capability row in the Coverage matrix.
 *
 * Layout is a horizontal [Row] with three slots:
 *
 *   [StatusDot]  |  Name + description (weight 1f, truncates with ellipsis)  |  StatusPill
 *
 * The status pill is icon-led and as small as the status warrants:
 *   - Working          → teal CheckCircle, no text
 *   - OS-limited       → muted Lock  + "OS" pill
 *   - PermissionNeeded → amber Shield + "Grant ›" pill (whole row is tappable)
 *   - Unavailable      → coral Lock + "—" pill
 *
 * Long-pressing the name area shows a tooltip with the full capability name
 * and description — useful on narrow phones when the ellipsized name hides
 * the dotted suffix (e.g. "notifications.dis…").
 *
 * Row total height is ~52dp (6dp top/bottom card padding + ~40dp content),
 * down from the previous ~80dp two-line description layout.
 */
@Composable
internal fun CapabilityRow(
    cap: NodeCapabilityItem,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (cap.status) {
        CapabilityStatus.Working -> Teal
        CapabilityStatus.MissingPermission -> Sand
        CapabilityStatus.OsLimited -> LimitedColor
        CapabilityStatus.Unavailable -> Coral
    }
    val context = LocalContext.current

    // Tap → either request a runtime permission via the system dialog, or
    // deep-link to the matching settings panel. After returning from either
    // path, the VM's capability flow re-reads the grant state and the row
    // flips to "Working" automatically.
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* ignored — VM observes the change */ }
    val canTap = cap.status == CapabilityStatus.MissingPermission && cap.requirement != null
    val onTap: () -> Unit = onTap@{
        val req = cap.requirement ?: return@onTap
        if (isRuntimePermissionRequest(req)) {
            permLauncher.launch(req.detail)
        } else {
            runCatching { context.startActivity(deepLinkFor(req) ?: return@onTap) }
        }
    }

    // Whole-row tap target only when there's a grant to chase. We use plain
    // [clickable] (not combinedClickable) so the inner TooltipBox's
    // long-press handler still fires for the tooltip on long-cap-name rows.
    val rowMod = modifier
        .fillMaxWidth()
        .let { base -> if (canTap) base.then(Modifier.clickable(onClick = onTap)) else base }

    Card(
        modifier = rowMod.semantics { testTag = "capability-row" },
        colors = CardDefaults.cardColors(
            containerColor = if (canTap) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusDot(statusColor, size = 8.dp)

            // Name + description take all remaining width; both truncate.
            // The tooltip on long-press surfaces the full strings.
            CapabilityRowNameTooltip(cap = cap, modifier = Modifier.weight(1f))

            // Status pill: icon-led, hugs the right edge, fixed width band
            // so columns stay aligned across rows.
            StatusPill(
                status = cap.status,
                canTap = canTap,
                color = statusColor,
            )
        }
    }
}

/**
 * Inline tooltip wrapper for the capability name column. Extracted so the
 * Material3 TooltipBox experimental opt-in stays local and the row body
 * stays readable.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CapabilityRowNameTooltip(cap: NodeCapabilityItem, modifier: Modifier = Modifier) {
    val tooltipState = rememberTooltipState(isPersistent = false)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(
            spacingBetweenTooltipAndAnchor = 4.dp,
        ),
        tooltip = {
            PlainTooltip {
                Text(
                    text = cap.name + "\n" + cap.description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        state = tooltipState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                cap.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                cap.description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * Compact icon-led status indicator on the right edge of a [CapabilityRow].
 * For grant-pending rows the pill carries a chevron to signal that tapping
 * the row will launch the grant flow.
 */
@Composable
private fun StatusPill(
    status: CapabilityStatus,
    canTap: Boolean,
    color: Color,
) {
    val spec = capabilityPillSpec(status, canTap)
    Row(
        modifier = Modifier
            .widthIn(min = if (canTap) 72.dp else 24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = if (canTap) 8.dp else 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val icon = when (spec.icon) {
            StatusIcon.CheckCircle -> Icons.Filled.CheckCircle
            StatusIcon.Lock -> Icons.Filled.Lock
            StatusIcon.Shield -> Icons.Filled.Shield
        }
        Icon(
            icon,
            contentDescription = spec.contentDescription,
            tint = color,
            modifier = Modifier.size(if (spec.icon == StatusIcon.CheckCircle) 16.dp else 14.dp),
        )
        if (spec.label != null) {
            Text(
                spec.label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        if (spec.showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Icon set used by the capability status pill. Pure data for testability. */
internal enum class StatusIcon { CheckCircle, Lock, Shield }

/**
 * Pure-data descriptor for a capability status pill. Extracted from the
 * composable so unit tests can assert the spec without spinning up Compose.
 */
internal data class StatusPillSpec(
    val icon: StatusIcon,
    val label: String?,
    val showChevron: Boolean,
    val contentDescription: String,
)

/**
 * Maps a [CapabilityStatus] (and whether the row has a grant to chase) to the
 * pill design we want on the right edge of the row.
 *
 *   Working         → CheckCircle, no text, no chevron
 *   OS-limited      → Lock + "OS", no chevron
 *   PermissionNeeded + canTap → Shield + "Grant" + chevron (tappable row)
 *   PermissionNeeded + !canTap → Shield + "Grant", no chevron
 *   Unavailable     → Lock, no text, no chevron
 */
internal fun capabilityPillSpec(
    status: CapabilityStatus,
    canTap: Boolean,
): StatusPillSpec = when (status) {
    CapabilityStatus.Working -> StatusPillSpec(
        icon = StatusIcon.CheckCircle,
        label = null,
        showChevron = false,
        contentDescription = "Working",
    )
    CapabilityStatus.OsLimited -> StatusPillSpec(
        icon = StatusIcon.Lock,
        label = "OS",
        showChevron = false,
        contentDescription = "OS-limited",
    )
    CapabilityStatus.MissingPermission -> StatusPillSpec(
        icon = StatusIcon.Shield,
        label = "Grant",
        showChevron = canTap,
        contentDescription = "Permission needed",
    )
    CapabilityStatus.Unavailable -> StatusPillSpec(
        icon = StatusIcon.Lock,
        label = null,
        showChevron = false,
        contentDescription = "Unavailable",
    )
}

@Composable
private fun LeaseRow(lease: HardwareLease) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            lease.capability,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MetaText(
            lease.holder,
            color = if (lease.isAvailable) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else Sand,
        )
    }
}

@Composable
private fun PrivacyLogRow(entry: PrivacyLogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MetaText(
            entry.time,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        Text(
            entry.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NodePairingSection(vm: NodeViewModel) {
    val pairings by vm.pairings.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PAIRED WITH HERMES", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { showDialog = true }) { Text("Pair as node") }
        }
        if (pairings.isEmpty()) {
            Text(
                "Not paired. Pair with a gateway's companion plugin to let Hermes read and control this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        pairings.forEach { p ->
            HermesCard(contentPadding = 12.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val pairId = p.nodeId.ifBlank { p.gatewayId }
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    val ctx = LocalContext.current
                    Column(Modifier.weight(1f)) {
                        // Full pairing id stays inspectable: one ellipsized line,
                        // tap to copy the whole value.
                        Text(
                            pairId,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(pairId))
                                android.widget.Toast.makeText(ctx, "Copied $pairId", android.widget.Toast.LENGTH_SHORT).show()
                            },
                        )
                        Text(
                            (if (p.connected) "connected · " else "offline · ") + "${p.grantedCaps.size} caps",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (p.connected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.unpair(p.gatewayId) }) { Text("Unpair") }
                }
            }
        }
    }

    if (showDialog) {
        NodePairDialog(
            onDismiss = { showDialog = false },
            onPair = { url, code, onResult ->
                vm.pair(url, code) { err -> onResult(err); if (err == null) showDialog = false }
            },
        )
    }
}
