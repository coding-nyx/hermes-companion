package com.hermes.companion.ui.diagnostics

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.data.repo.Connectivity
import com.hermes.companion.ui.theme.StatusError
import com.hermes.companion.ui.theme.StatusOk
import com.hermes.companion.ui.theme.StatusWarn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    vm: DiagnosticsViewModel = hiltViewModel(),
) {
    val fleet by vm.fleet.collectAsStateWithLifecycle()
    val node by vm.nodeState.collectAsStateWithLifecycle()
    val pairings by vm.pairings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics", style = MaterialTheme.typography.titleMedium) },
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
            Text("PER GATEWAY", style = MaterialTheme.typography.labelMedium)
            fleet.gateways.forEach { gw ->
                val (state, color) = when (gw.connectivity) {
                    is Connectivity.Live -> "reachable" to StatusOk
                    is Connectivity.Degraded -> "degraded" to StatusWarn
                    is Connectivity.Down -> "unreachable" to StatusError
                    Connectivity.Unknown -> "unknown" to StatusWarn
                }
                Check(gw.gateway.label, state, color, "${gw.profiles.size} profiles · ${gw.tier.name.lowercase()} tier")
            }
            if (fleet.gateways.isEmpty()) {
                Text("No gateways yet — add one from Settings → Discover.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("NODE", style = MaterialTheme.typography.labelMedium)
            if (pairings.isEmpty()) {
                Check("Node pairing", "not paired", StatusWarn, "pair from the Node tab")
            } else {
                pairings.forEach { p ->
                    Check(
                        p.nodeId.ifBlank { p.gatewayId },
                        if (p.connected) "broker connected" else "broker offline",
                        if (p.connected) StatusOk else StatusError,
                        "${p.grantedCaps.size} capabilities",
                    )
                }
            }

            Text("END-TO-END CANARY", style = MaterialTheme.typography.labelMedium)
            Button(onClick = vm::runCanary, enabled = !node.canaryRunning, modifier = Modifier.fillMaxWidth()) {
                Text(if (node.canaryRunning) "Running…" else "Run canary")
            }
            node.canarySteps.forEach { step ->
                Text("• $step", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Check(name: String, state: String, color: androidx.compose.ui.graphics.Color, detail: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(state, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
