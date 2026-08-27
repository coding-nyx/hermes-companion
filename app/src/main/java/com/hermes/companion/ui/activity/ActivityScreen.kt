package com.hermes.companion.ui.activity

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.companion.data.repo.ActivityItem
import com.hermes.companion.data.repo.ActivityKind
import com.hermes.companion.data.repo.ActivityOutcome
import com.hermes.companion.data.repo.QueueSummary

private val Teal = Color(0xFF80CBC4)
private val Sand = Color(0xFFFFCC80)
private val Coral = Color(0xFFFFB4AB)
private val DimColor = Color(0x38E8E8EC)

@Composable
fun ActivityScreen(
    vm: ActivityViewModel = viewModel(factory = ActivityViewModel.factory()),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Activity", style = MaterialTheme.typography.titleLarge)
            Text(
                "every event, and what became of it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        Box(Modifier.size(10.dp))

        // Filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilterChip(
                selected = state.filter == null,
                onClick = { vm.setFilter(null) },
                label = { Text("All") },
            )
            FilterChip(
                selected = state.filter == ActivityKind.Notification,
                onClick = { vm.setFilter(ActivityKind.Notification) },
                label = { Text("Notifications") },
            )
            FilterChip(
                selected = state.filter == ActivityKind.Call,
                onClick = { vm.setFilter(ActivityKind.Call) },
                label = { Text("Calls") },
            )
            FilterChip(
                selected = state.filter == ActivityKind.Job,
                onClick = { vm.setFilter(ActivityKind.Job) },
                label = { Text("Jobs") },
            )
        }

        Box(Modifier.size(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.items, key = { it.id }) { item ->
                ActivityCard(
                    item = item,
                    isExpanded = state.expandedId == item.id,
                    onToggle = { vm.toggleExpanded(item.id) },
                )
            }
        }

        // Footer queue summaries
        if (state.queues.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                state.queues.forEach { q ->
                    QueueSummaryRow(q)
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(
    item: ActivityItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val outcomeColor = when (item.outcome) {
        ActivityOutcome.Notified, ActivityOutcome.Completed -> Teal
        ActivityOutcome.Suppressed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        ActivityOutcome.Failed -> Coral
        ActivityOutcome.AwaitingApproval, ActivityOutcome.Streaming -> Sand
    }

    val iconBg = outcomeColor.copy(alpha = 0.15f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        item.glyph,
                        color = outcomeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text(
                            formatTime(item.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            // Progression Steps
            val steps = listOf("captured", "uploaded", "acked", "judged", "outcome")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                steps.forEachIndexed { i, stepName ->
                    val isReached = (i + 1) <= item.stage
                    val stepColor = if (isReached) outcomeColor else DimColor
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .size(height = 3.dp, width = 0.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(stepColor),
                        )
                        Box(Modifier.size(2.dp))
                        Text(
                            stepName,
                            fontSize = 9.sp,
                            color = if (isReached) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            maxLines = 1,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(iconBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        item.outcome.name,
                        color = outcomeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Expanded Detail View
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        item.detailTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = outcomeColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        item.detailBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                    Text(
                        item.routeDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    Text(
                        item.detailMeta,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueSummaryRow(q: QueueSummary) {
    val dotColor = if (q.isLive) Teal else Sand
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            q.gatewayId,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        Box(Modifier.weight(1f))
        Text(
            q.detail,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = dotColor,
        )
    }
}

private fun formatTime(epochMs: Long): String {
    val local = java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
    return String.format("%02d:%02d", local.hour, local.minute)
}
