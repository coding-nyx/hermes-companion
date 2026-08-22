package com.hermes.companion.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.GatewayKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory())) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gateways", style = MaterialTheme.typography.titleLarge)
            Box(Modifier.weight(1f))
            Button(onClick = { showAdd = true }) { Text("Add") }
        }
        Text(
            "Each gateway has its own profiles, sessions, and capability grants. Switching is isolated.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Box(Modifier.size(12.dp))
        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.gateways, key = { it.id }) { gw ->
                GatewayRow(gw = gw, onRemove = { vm.removeGateway(gw.id) })
            }
        }
    }

    if (showAdd) {
        AddGatewayDialog(
            onDismiss = { showAdd = false },
            onConfirm = { label, kind, profiles ->
                vm.addMockGateway(label, kind, profiles)
                showAdd = false
            },
        )
    }
}

@Composable
private fun GatewayRow(gw: GatewayConnection, onRemove: () -> Unit) {
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
                    "${gw.id} · ${gw.kind.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
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
    onConfirm: (label: String, kind: GatewayKind, profileIds: List<String>) -> Unit,
) {
    var label by remember { mutableStateOf("Workshop") }
    var profilesCsv by remember { mutableStateOf("ash, misty") }
    var kind by remember { mutableStateOf(GatewayKind.Local) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add mock gateway") },
        text = {
            Column {
                Text(
                    "PoC adds an in-process mock gateway. Real builds wire to Hermes API + Keystore tokens.",
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
                    value = profilesCsv,
                    onValueChange = { profilesCsv = it },
                    label = { Text("Profiles (comma-separated)") },
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
                val ids = profilesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (label.isNotBlank() && ids.isNotEmpty()) {
                    onConfirm(label.trim(), kind, ids)
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
