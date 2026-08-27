package com.hermes.companion.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.hermes.companion.BuildConfig
import com.hermes.companion.data.repo.GatewayView
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenOutbox: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gateways", style = MaterialTheme.typography.titleLarge)
            Box(Modifier.weight(1f))
            IconButton(onClick = vm::refresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh Gateways")
            }
            androidx.compose.material3.TextButton(onClick = onOpenDiagnostics) { Text("Diagnostics") }
            androidx.compose.material3.OutlinedButton(onClick = onOpenDiscover) { Text("Discover") }
            Box(Modifier.size(8.dp))
            Button(onClick = { showAdd = true }) { Text("Add") }
        }
        Text(
            "Each gateway has its own profiles, sessions, and capability grants. Switching is isolated.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Box(Modifier.size(12.dp))

        // Outbox entry banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable(onClick = onOpenOutbox),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Outbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
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
                    contentDescription = "Open Outbox",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.fleet.gateways, key = { it.gateway.id }) { view ->
                GatewayRow(view = view, onRemove = { vm.removeGateway(view.gateway.id) })
            }
        }
        state.error?.let {
            Box(Modifier.size(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
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

@Composable
private fun GatewayRow(view: GatewayView, onRemove: () -> Unit) {
    val gw = view.gateway
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(gw.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    gw.baseUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Box(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {},
                        label = { Text(gw.id) },
                    )
                    Box(Modifier.size(6.dp))
                    Text(
                        gw.kind.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                view.connectivity.reasonOrNull?.let { errorMsg ->
                    Box(Modifier.size(2.dp))
                    Text(
                        "Status: $errorMsg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGatewayDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, baseUrl: String, kind: GatewayKind) -> Unit,
) {
    var label by remember { mutableStateOf("Workshop") }
    var url by remember {
        mutableStateOf("http://${BuildConfig.DEFAULT_HERMES_HOST}:${BuildConfig.DEFAULT_HERMES_PORT}/gw-workshop")
    }
    var kind by remember { mutableStateOf(GatewayKind.RemoteHttp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add gateway") },
        text = {
            Column {
                Text(
                    "Add a Hermes gateway reachable over HTTP. The URL is the full gateway path, e.g. http://host:7800/gw-home.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(Modifier.size(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.size(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GatewayKind.values().forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.isNotBlank()) onConfirm(label.trim(), url.trim(), kind)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
