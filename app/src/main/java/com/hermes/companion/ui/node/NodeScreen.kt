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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
private fun CapabilityRow(cap: NodeCapabilityItem) {
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (canTap) it.then(Modifier.clickable(onClick = onTap)) else it },
        colors = CardDefaults.cardColors(
            containerColor = if (canTap) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusDot(statusColor, size = 8.dp)
            Column(Modifier.weight(1f)) {
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            Text(
                if (canTap) "${cap.stateLabel}  ›  grant" else cap.stateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
        var url by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Pair this phone as a node") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("Plugin base URL") },
                        placeholder = { Text("http://host:9120") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = code, onValueChange = { code = it },
                        label = { Text("Setup code") },
                        singleLine = true,
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = url.isNotBlank() && code.isNotBlank(),
                    onClick = { vm.pair(url, code) { err -> if (err == null) showDialog = false else error = err } },
                ) { Text("Pair") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        )
    }
}
