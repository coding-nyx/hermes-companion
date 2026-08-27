package com.hermes.companion.ui.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Row
import com.hermes.companion.data.repo.NodeGrantItem

private val MODE_CYCLE = listOf("AllowWhileUnlocked", "AskEveryTime", "AllowUntil", "Deny")

private fun modeLabel(mode: String): String = when (mode) {
    "AllowWhileUnlocked" -> "While unlocked"
    "AskEveryTime" -> "Ask each time"
    "AllowUntil" -> "Allowed"
    "Deny" -> "Deny"
    else -> mode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeGrantsScreen(
    onBack: () -> Unit,
    vm: NodeViewModel = hiltViewModel(),
) {
    val grants by vm.grants.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Grant capabilities", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${grants.count { it.mode != "Deny" }} of ${grants.size} allowed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (grants.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(
                    "No grants yet. Pair this phone as a node first — pairing seeds a grant per " +
                        "capability, and you tighten each one here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(grants, key = { it.gatewayId + it.capability + it.profileId }) { g -> GrantRow(g, vm) }
            }
        }
    }
}

@Composable
private fun GrantRow(g: NodeGrantItem, vm: NodeViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(g.capability, style = MaterialTheme.typography.titleSmall)
                Text(
                    g.nodeId.ifBlank { g.gatewayId },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SuggestionChip(
                onClick = {
                    val next = MODE_CYCLE[(MODE_CYCLE.indexOf(g.mode).coerceAtLeast(0) + 1) % MODE_CYCLE.size]
                    vm.setGrant(g, next)
                },
                label = { Text(modeLabel(g.mode)) },
            )
        }
    }
}
