package com.hermes.companion.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import com.hermes.companion.ui.components.HermesCard

/**
 * T3B: 3-tab Settings screen.
 *
 * Tabs:
 *   - Gateways - paired gateway list + Make-active + Add
 *   - Profiles - profiles for the active gateway
 *   - Notification Routing - placeholder, filled by T5A/T5B
 *
 * Tab state is rememberSaveable so it survives configuration changes (rotation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenOutbox: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // Tab strip
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            listOf("Gateways", "Profiles", "Routing", "Voice").forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title) },
                    modifier = Modifier.semantics { contentDescription = "$title tab" },
                )
            }
        }

        // Outbox entry banner - always visible above the active tab.
        HermesCard(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(onClick = onOpenOutbox)
                .semantics { contentDescription = "Open outbound outbox" },
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Outbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text("Outbound Outbox", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Inspect queued, in-flight, or unacknowledged submissions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }

        // Active tab content
        Box(Modifier.fillMaxSize().weight(1f)) {
            when (selectedTab) {
                0 -> GatewaysTab(
                    gateways = state.fleet.gateways,
                    activeGatewayId = state.activeGatewayId,
                    onAdd = { showAdd = true },
                    onOpenDiscover = onOpenDiscover,
                    onRemove = { vm.removeGateway(it) },
                    onMakeActive = { vm.setActive(it) },
                    onRefresh = vm::refresh,
                )
                1 -> ProfilesTab(
                    profiles = state.fleet.gateways
                        .firstOrNull { it.gateway.id == state.activeGatewayId }
                        ?.profiles ?: emptyList(),
                )
                2 -> NotificationRoutingTab(ruleRepo = vm.ruleRepo)
                3 -> VoiceTab(
                    snap = state.voice,
                    onChange = vm::setVoiceConfig,
                )
            }
        }

        // Sub-screen links
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.TextButton(onClick = onOpenAppearance) { Text("Appearance") }
            androidx.compose.material3.TextButton(onClick = onOpenDiagnostics) { Text("Diagnostics") }
        }

        // Error banner
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    if (showAdd) {
        AddGatewayDialog(
            onDismiss = { showAdd = false },
            onConfirm = { label, url, kind ->
                vm.addGateway(label, url, kind)
                showAdd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGatewayDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, baseUrl: String, kind: com.hermes.companion.domain.GatewayKind) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("Workshop") }
    var url by rememberSaveable {
        mutableStateOf("http://${com.hermes.companion.BuildConfig.DEFAULT_HERMES_HOST}:${com.hermes.companion.BuildConfig.DEFAULT_HERMES_PORT}/gw-workshop")
    }
    var kind by rememberSaveable { mutableStateOf(com.hermes.companion.domain.GatewayKind.RemoteHttp) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Add gateway") },
        text = {
            Column {
                androidx.compose.material3.Text(
                    "Add a Hermes gateway reachable over HTTP. The URL is the full gateway path, e.g. http://host:7800/gw-home.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.foundation.layout.Box(Modifier.size(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { androidx.compose.material3.Text("Label") },
                    modifier = Modifier.fillMaxWidth(0.95f),
                )
                androidx.compose.foundation.layout.Box(Modifier.size(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { androidx.compose.material3.Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(0.95f),
                )
                androidx.compose.foundation.layout.Box(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.hermes.companion.domain.GatewayKind.values().forEach { k ->
                        androidx.compose.material3.FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { androidx.compose.material3.Text(k.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                if (url.isNotBlank()) onConfirm(label.trim(), url.trim(), kind)
            }) { androidx.compose.material3.Text("Add") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") }
        },
    )
}
