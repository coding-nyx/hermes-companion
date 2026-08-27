package com.hermes.companion.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.data.repo.DiscoveredGatewayItem
import com.hermes.companion.domain.TransportTier
import com.hermes.companion.ui.theme.StatusOk
import com.hermes.companion.ui.theme.StatusWarn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    vm: DiscoverViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find your gateway", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Discovery only proves something answered on an address. Nothing is trusted until you add it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Tailscale status
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tailscale", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (state.tailnetActive) "Tailnet active · ${state.tailnetAddress}"
                            else "No tailnet detected — start Tailscale to reach a remote gateway",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TierChip(if (state.tailnetActive) TransportTier.Full else TransportTier.Limited)
                }
            }

            Text("ON THIS NETWORK", style = MaterialTheme.typography.labelMedium)
            if (state.gateways.isEmpty()) {
                Text(
                    "No gateways advertised on this network yet. Add one manually below, or by its Tailscale name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.gateways.forEach { g -> DiscoveredRow(g) { vm.add(g.label, g.baseUrl) { onAdded() } } }

            Text("ADD MANUALLY", style = MaterialTheme.typography.labelMedium)
            ManualAdd(vm, onAdded)
        }
    }
}

@Composable
private fun DiscoveredRow(g: DiscoveredGatewayItem, onAdd: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.label, style = MaterialTheme.typography.titleSmall)
                Text("${g.host}:${g.port} · ${g.source}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TierChip(g.tier)
            Button(onClick = onAdd, modifier = Modifier.padding(start = 8.dp)) { Text("Add") }
        }
    }
}

@Composable
private fun ManualAdd(vm: DiscoverViewModel, onAdded: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val tier = if (url.isBlank()) null else vm.tierOf(url)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(url, { url = it }, label = { Text("Base URL") },
            placeholder = { Text("http://host.ts.net:7800/gw-home") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(label, { label = it }, label = { Text("Label (optional)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        if (tier != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TierChip(tier)
                if (tier == TransportTier.Limited) {
                    Text("Chat + read-only only — no node session over untrusted cleartext.",
                        style = MaterialTheme.typography.labelSmall, color = StatusWarn)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(
            enabled = url.isNotBlank(),
            onClick = { vm.add(label.ifBlank { url }, url) { err -> if (err == null) onAdded() else error = err } },
        ) { Text("Add gateway") }
    }
}

@Composable
private fun TierChip(tier: TransportTier) {
    val (text, color) = when (tier) {
        TransportTier.Full -> "full access" to StatusOk
        TransportTier.Limited -> "limited" to StatusWarn
    }
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
    )
}
