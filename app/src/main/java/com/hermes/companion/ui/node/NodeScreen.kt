package com.hermes.companion.ui.node

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hermes.companion.node.CompanionA11yService
import com.hermes.companion.node.CompanionLink
import com.hermes.companion.node.Coverage
import com.hermes.companion.node.CoverageRow
import com.hermes.companion.node.CoverageStatus
import com.hermes.companion.node.NodePrefs
import com.hermes.companion.node.NodeTools
import com.hermes.companion.node.ScreenCapture
import com.hermes.companion.node.ScreenNode
import com.hermes.companion.node.UsageAccess
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(Coverage.snapshot(ctx)) }
    var code by remember { mutableStateOf(NodePrefs.pairingCode(ctx)) }
    var url by remember { mutableStateOf(NodePrefs.gatewayUrl(ctx).ifBlank { "http://hermes:8642" }) }
    var tree by remember { mutableStateOf<List<ScreenNode>>(emptyList()) }
    var treeTitle by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf(false) }
    var inputOn by remember { mutableStateOf(NodePrefs.screenInputAllowed(ctx)) }
    var linkUp by remember { mutableStateOf(CompanionLink.isUp()) }

    fun refresh() {
        rows = Coverage.snapshot(ctx)
        linkUp = CompanionLink.isUp()
        inputOn = NodePrefs.screenInputAllowed(ctx)
        val svc = CompanionA11yService.instance
        if (svc != null) {
            val dump = svc.dumpTree()
            treeTitle = "${dump.pkg} · ${dump.nodes.size} nodes"
            tree = dump.nodes.take(12)
        } else {
            treeTitle = UsageAccess.current(ctx)?.pkg ?: "accessibility off"
            tree = emptyList()
        }
    }

    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        ScreenCapture.store(result.resultCode, result.data)
        refresh()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Node", style = MaterialTheme.typography.titleLarge)
            Text(
                "Full node mode: accessibility for screen use, usage access for the foreground app, optional MediaProjection for frames. Hermes only drives input after you flip the grant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        items(rows, key = { it.id }) { row ->
            CoverageCard(
                row = row,
                onGrant = {
                    if (row.id == "capture") {
                        captureLauncher.launch(ScreenCapture.requestIntent(ctx))
                    } else if (row.id == "input") {
                        NodePrefs.setScreenInputAllowed(ctx, !inputOn)
                        refresh()
                    } else {
                        Coverage.open(ctx, row)
                    }
                },
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Allow Hermes to use the screen", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Taps, swipes, and typing. Reading the tree still works when this is off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Switch(
                    checked = inputOn,
                    onCheckedChange = {
                        NodePrefs.setScreenInputAllowed(ctx, it)
                        refresh()
                    },
                )
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pair with Hermes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (linkUp) "Live · ${NodePrefs.gatewayUrl(ctx)}"
                        else CompanionLink.lastError ?: "Phone dials out over Tailscale. Same mailbox as the web companion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(code, style = MaterialTheme.typography.headlineSmall)
                        TextButton(onClick = {
                            code = NodePrefs.rotateCode(ctx)
                        }) { Text("Rotate") }
                    }
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Gateway URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            pairing = true
                            lastResult = ""
                            scope.launch {
                                try {
                                    CompanionLink.pair(ctx, url, code)
                                    lastResult = "paired"
                                } catch (e: Exception) {
                                    lastResult = e.message ?: "pair failed"
                                } finally {
                                    pairing = false
                                    refresh()
                                }
                            }
                        },
                        enabled = !pairing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (pairing) "Pairing…" else if (linkUp) "Reconnect" else "Pair")
                    }
                    if (linkUp) {
                        TextButton(onClick = {
                            CompanionLink.stop()
                            NodePrefs.setSession(ctx, null)
                            refresh()
                        }) { Text("Disconnect") }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Screen now", style = MaterialTheme.typography.titleMedium)
                    Text(
                        treeTitle.ifBlank { "Enable accessibility, then resume this page." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                lastResult = NodeTools.execute(ctx, "read_screen")
                                refresh()
                            },
                            label = { Text("Read screen") },
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                lastResult = NodeTools.execute(ctx, "app_usage")
                                refresh()
                            },
                            label = { Text("App usage") },
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                lastResult = NodeTools.execute(ctx, "capture_screenshot")
                                refresh()
                            },
                            label = { Text("Screenshot") },
                        )
                    }
                    if (lastResult.isNotBlank()) {
                        Text(
                            lastResult.take(400),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        items(tree, key = { "n${it.id}" }) { node ->
            Text(
                "${if (node.clickable) "▸ " else ""}${node.label.ifBlank { node.cls }}  ${node.l},${node.t}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CoverageCard(row: CoverageRow, onGrant: () -> Unit) {
    val tone = when (row.status) {
        CoverageStatus.Working -> MaterialTheme.colorScheme.primary
        CoverageStatus.Limited -> MaterialTheme.colorScheme.tertiary
        CoverageStatus.Missing -> MaterialTheme.colorScheme.error
        CoverageStatus.Off -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.label, style = MaterialTheme.typography.titleMedium)
                Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                Text(
                    row.status.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                )
            }
            if (row.status != CoverageStatus.Working || row.id == "input" || row.id == "capture") {
                TextButton(onClick = onGrant) {
                    Text(if (row.id == "input") "Toggle" else "Grant")
                }
            }
        }
    }
}
