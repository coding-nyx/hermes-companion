package com.hermes.companion.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hermes.companion.data.repo.GatewayView
import com.hermes.companion.ui.components.HermesCard

/**
 * T3B: Gateways tab - paired gateway list with Make-active / Remove actions.
 * Mirrors the body of the old SettingsScreen minus tabs.
 */
@Composable
fun GatewaysTab(
    gateways: List<GatewayView>,
    activeGatewayId: String?,
    onAdd: () -> Unit,
    onOpenDiscover: () -> Unit,
    onRemove: (String) -> Unit,
    onMakeActive: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gateways", style = MaterialTheme.typography.titleLarge)
            Box(Modifier.weight(1f))
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = "Refresh Gateways" },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
            OutlinedButton(onClick = onOpenDiscover) { Text("Discover") }
            Box(Modifier.size(8.dp))
            androidx.compose.material3.Button(onClick = onAdd) { Text("Add") }
        }
        Text(
            "Each gateway has its own profiles, sessions, and capability grants. Switching is isolated.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Box(Modifier.size(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(gateways, key = { it.gateway.id }) { view ->
                GatewayRow(
                    view = view,
                    isActive = view.gateway.id == activeGatewayId,
                    onRemove = { onRemove(view.gateway.id) },
                    onMakeActive = { onMakeActive(view.gateway.id) },
                )
            }
        }
    }
}

@Composable
private fun GatewayRow(
    view: GatewayView,
    isActive: Boolean,
    onRemove: () -> Unit,
    onMakeActive: () -> Unit,
) {
    val gw = view.gateway
    HermesCard(
        containerColor = if (isActive)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surface,
        contentPadding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(gw.label, style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.size(6.dp))
                    if (isActive) {
                        AssistChip(onClick = {}, label = { Text("Active") })
                    }
                }
                Text(
                    gw.baseUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Box(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(gw.id) })
                    Box(Modifier.size(6.dp))
                    Text(
                        gw.kind.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            if (!isActive) {
                androidx.compose.material3.TextButton(onClick = onMakeActive) { Text("Make active") }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.semantics { contentDescription = "Remove ${gw.label}" },
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}
