package com.hermes.companion.ui.outbox

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.hermes.companion.ui.theme.Indigo80
import com.hermes.companion.ui.theme.StatusError
import com.hermes.companion.ui.theme.StatusOk
import com.hermes.companion.ui.theme.StatusWarn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.hermes.companion.data.repo.OutboxItem
import com.hermes.companion.ui.components.HermesCard
import com.hermes.companion.ui.components.MetaText
import com.hermes.companion.ui.components.SectionHeader

private val Teal = StatusOk
private val Sand = StatusWarn
private val Coral = StatusError
private val Purple = Indigo80

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboxScreen(
    onBack: () -> Unit,
    vm: OutboxViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val needsDecisionCount = state.items.count { it.needsDecision }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Outbox", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (needsDecisionCount > 0) "$needsDecisionCount submission needs your decision" else "All submissions resolved",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (needsDecisionCount > 0) Coral else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { item ->
                    OutboxCard(
                        item = item,
                        onRetry = { vm.retry(item.id) },
                        onDrop = { vm.drop(item.id) },
                    )
                }
            }

            // Per gateway limits info card
            HermesCard(
                modifier = Modifier.padding(bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = 12.dp,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionHeader("Per gateway limits")
                Row(Modifier.fillMaxWidth()) {
                    Text("Messages held", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Box(Modifier.weight(1f))
                    Text("${state.messagesHeld} of ${state.maxMessages}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("Attachments held", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Box(Modifier.weight(1f))
                    Text("1.4 of 48 MB", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Text(
                    "Every submission is journalled locally before the network is touched and replayed under an idempotency key.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun OutboxCard(
    item: OutboxItem,
    onRetry: () -> Unit,
    onDrop: () -> Unit,
) {
    val stateColor = when (item.state) {
        "acked" -> Teal
        "in flight" -> Purple
        "queued" -> Sand
        else -> Coral
    }

    HermesCard(
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = 12.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(stateColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    item.state,
                    color = stateColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(Modifier.size(8.dp))
            Text(
                item.routeDisplay,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            MetaText(
                formatTime(item.createdAt),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        if (item.needsDecision) {
            Text(
                "Written, sent, but unacknowledged. Replaying reuses the same key, ensuring no duplicate run is created.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Send it again")
                }
                OutlinedButton(
                    onClick = onDrop,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Drop it")
                }
            }
        }
    }
}

private fun formatTime(epochMs: Long): String {
    val local = java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
    return String.format("%02d:%02d", local.hour, local.minute)
}
