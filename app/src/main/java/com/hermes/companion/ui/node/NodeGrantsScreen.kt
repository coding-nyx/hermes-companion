package com.hermes.companion.ui.node

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Row
import com.hermes.companion.data.repo.NodeGrantItem
import com.hermes.companion.ui.components.HermesCard

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
    HermesCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                // Capability strings can be long; wrap to at most two lines and
                // ellipsize rather than pushing the mode chip off-screen.
                Text(
                    g.capability,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // The full node/gateway id stays inspectable: one ellipsized
                // line, tap to copy the whole value to the clipboard.
                CopyableId(g.nodeId.ifBlank { g.gatewayId })
            }
            SuggestionChip(
                onClick = {
                    val next = MODE_CYCLE[(MODE_CYCLE.indexOf(g.mode).coerceAtLeast(0) + 1) % MODE_CYCLE.size]
                    vm.setGrant(g, next)
                },
                label = { Text(modeLabel(g.mode), maxLines = 1, softWrap = false) },
            )
        }
    }
}

/**
 * A one-line, end-ellipsized identifier that never truncates the real value out
 * of reach — tapping copies the full id to the clipboard and confirms with a
 * toast. Used wherever a long node/gateway id sits in a constrained subtitle.
 */
@Composable
internal fun CopyableId(id: String, modifier: Modifier = Modifier) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Text(
        id,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clickable {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(id))
            android.widget.Toast.makeText(context, "Copied $id", android.widget.Toast.LENGTH_SHORT).show()
        },
    )
}
