package com.hermes.companion.ui.node

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.companion.data.repo.CapabilityStatus
import com.hermes.companion.data.repo.HardwareLease
import com.hermes.companion.data.repo.NodeCapabilityItem
import com.hermes.companion.data.repo.PrivacyLogEntry

private val Teal = Color(0xFF80CBC4)
private val Sand = Color(0xFFFFCC80)
private val Coral = Color(0xFFFFB4AB)
private val LimitedColor = Color(0x80E8E8EC)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeScreen(
    vm: NodeViewModel = viewModel(factory = NodeViewModel.factory()),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val node = state.node

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                    )
                    Box(Modifier.weight(1f))
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
        Text(
            "COVERAGE — $workingCount working, $restCount not",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )

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
                    Text(
                        "EXCLUSIVE HARDWARE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
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

            OutlinedButton(onClick = {}) {
                Text("Reconcile")
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
        Text(
            "WHAT LEFT THIS PHONE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp),
        )

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
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    cap.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    cap.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            Text(
                cap.stateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LeaseRow(lease: HardwareLease) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            lease.capability,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        Box(Modifier.weight(1f))
        Text(
            lease.holder,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
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
        Text(
            entry.time,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
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
