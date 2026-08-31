package com.hermes.companion.ui.v1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.data.repo.ConversationState
import com.hermes.companion.ui.components.HermesMark
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.PlexMono
import com.hermes.companion.ui.theme.StatusOk
import com.hermes.companion.ui.theme.StatusWarn

/**
 * Right-side context panel — agent status, active tool runs, recent files,
 * voice thread, and an approvals queue. Two visual variants:
 *  - **persistent**: lives inside the 320 dp tablet column
 *  - **drawer**: slides in over a scrim on phone
 *
 * Sections are top-down so the eye lands on the most urgent first
 * (status banner → in-flight tools → approval queue).
 */
@Composable
fun V1ContextPanel(
    vm: V1ShellViewModel,
    isDrawerVariant: Boolean,
    onClose: (() -> Unit)? = null,
) {
    val activeRoute by vm.activeRoute.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "CONTEXT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PlexMono,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (isDrawerVariant && onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Collapse context panel")
                }
            } else {
                IconButton(onClick = { vm.toggleContextDrawer() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Collapse context panel")
                }
            }
        }

        if (activeRoute == null) {
            // No active thread — context is empty.
            EmptyContext(modifier = Modifier.padding(horizontal = 14.dp))
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Agent status banner
            AgentStatusBanner(conversation)

            // 2. Active tool runs
            ToolRunsSection(conversation)

            // 3. Recent files
            RecentFilesSection()

            // 4. Voice thread
            VoiceThreadSection()

            // 5. Approvals queue
            ApprovalsQueue(conversation)
        }
    }
}

@Composable
private fun EmptyContext(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Open a thread to see live agent status, in-flight tools, and approval requests here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AgentStatusBanner(state: ConversationState) {
    val (statusLabel, dotColor) = when (state.activeRun?.state) {
        com.hermes.companion.data.repo.RunPhase.Streaming -> "Streaming" to HermesColors.Primary
        com.hermes.companion.data.repo.RunPhase.AwaitingApproval -> "Awaiting approval" to StatusWarn
        com.hermes.companion.data.repo.RunPhase.Completed -> "Idle" to StatusOk
        com.hermes.companion.data.repo.RunPhase.Failed -> "Failed" to com.hermes.companion.ui.theme.StatusError
        null -> "Idle" to StatusOk
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HermesMark(size = 28.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "@ash",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PlexMono,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    "coder-lab · opus-4.6",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(label = statusLabel, dotColor = dotColor)
        }
        // Token / latency meta row
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Meta("1.2k in")
            Meta("340 out")
            Meta("~9 s")
            Meta("2 tools")
        }
        // Handoff chain strip — rich (non-compact) variant in the context panel,
        // showing every profile that has touched this thread.
        val handoffProfiles = remember(state) {
            state.messages
                .map { it.profileId }
                .distinct()
                .map { id ->
                    com.hermes.companion.domain.AgentProfile(
                        gatewayId = state.route?.gatewayId ?: "",
                        profileId = id,
                        displayName = id,
                        handle = profileHandleFor(id),
                    )
                }
        }
        if (handoffProfiles.size > 1) {
            V1BHandoffChainStrip(
                profiles = handoffProfiles,
                compact = false,
            )
        }
    }
}

@Composable
private fun Meta(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusPill(label: String, dotColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(dotColor.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            ),
            color = dotColor,
        )
    }
}

@Composable
private fun ToolRunsSection(state: ConversationState) {
    val runs = state.messages.lastOrNull { it is com.hermes.companion.domain.Message.Assistant }
        ?.let { (it as com.hermes.companion.domain.Message.Assistant).toolRuns } ?: emptyList()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "TOOL RUNS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (runs.isEmpty()) {
            Text(
                "No tools yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        runs.forEach { run ->
            V1BToolRunCard(run = run.toV1B())
        }
    }
}

@Composable
private fun RecentFilesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "RECENT FILES",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        ) {
            listOf(
                "HermesComponents.kt" to "2.1 KB",
                "Color.kt" to "3.9 KB",
                "Theme.kt" to "1.5 KB",
            ).forEachIndexed { idx, (name, size) ->
                FileRow(name, size)
                if (idx < 2) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(name: String, size: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: open file */ }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PlexMono,
                fontSize = 11.5.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            size,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoiceThreadSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "VOICE THREAD",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(StatusOk.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        tint = StatusOk,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Voice thread: Trip planning",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        "@misty · 0:24 of 1:10 transcribed",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Waveform placeholder
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf(0.35f, 0.60f, 0.80f, 0.45f, 0.75f, 0.90f, 0.55f, 0.40f, 0.30f, 0.22f).forEachIndexed { i, h ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height((24 * h).dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                if (i < 6) StatusOk else StatusOk.copy(alpha = 0.30f),
                            ),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, StatusOk.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                    .clickable { /* TODO: open voice thread */ }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Open voice thread",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = StatusOk,
                )
            }
        }
    }
}

@Composable
private fun ApprovalsQueue(state: ConversationState) {
    val pendingApproval = state.pendingApproval
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "APPROVALS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pendingApproval == null) {
            Text(
                "Nothing awaiting your decision.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = StatusWarn,
                modifier = Modifier.size(14.dp),
            )
            Text(
                "1 awaiting your approval",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(StatusWarn.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "1",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PlexMono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = StatusWarn,
                )
            }
        }
    }
}
