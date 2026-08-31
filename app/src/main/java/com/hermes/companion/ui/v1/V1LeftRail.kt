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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.data.repo.Fleet
import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.RunState
import com.hermes.companion.domain.Session
import com.hermes.companion.ui.components.HermesMark
import com.hermes.companion.ui.components.SectionLabel
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.InstrumentSerif
import com.hermes.companion.ui.theme.PlexMono
import com.hermes.companion.ui.theme.StatusOk

/**
 * A row in the rail — the Session is the source of truth, but we carry the
 * profile handle alongside because the FleetView's profile is the closest
 * thing the design needs (handle chip + display name).
 *
 * Kept inside the v1 module so we don't have to widen the domain Session
 * model just for this UI.
 */
internal data class ThreadRowView(
    val session: Session,
    val handleDisplay: String,
    val profile: AgentProfile,
    val preview: String,
)

/**
 * The left rail (or its drawer equivalent on phone).
 *
 * Two visual variants:
 *  - **persistent**: lives inside the 320 dp tablet column; no close button
 *  - **drawer**: slides in over a scrim on phone; has a Close affordance
 *
 * Thread list groups: Today / Yesterday / Previous 7 days / Earlier.
 * Each row shows a profile handle chip + thread title + last-message preview
 * + either an unread pill, relative time, or voice-thread glyph. The active
 * row gets a 1 dp Indigo border and tinted fill, exactly like the mock.
 */
@Composable
fun V1LeftRail(
    vm: V1ShellViewModel,
    isDrawerVariant: Boolean,
    showCloseButton: Boolean,
    onClose: (() -> Unit)? = null,
) {
    val fleet by vm.fleet.collectAsStateWithLifecycle()
    val activeRoute by vm.activeRoute.collectAsStateWithLifecycle()
    val inboxCount by vm.inboxCount.collectAsStateWithLifecycle()

    // Flatten sessions across gateways/profiles into a single grouped list.
    val grouped: Map<TimeGroup, List<ThreadRowView>> = remember(fleet) {
        buildThreadRows(fleet)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 14.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HermesMark(size = 32.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDrawerVariant) "Threads" else "Hermes",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = if (isDrawerVariant) MaterialTheme.typography.titleMedium.fontFamily else InstrumentSerif,
                        fontSize = if (isDrawerVariant) 15.sp else 16.sp,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = if (isDrawerVariant) "3 unread" else "Companion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            // Inbox indicator
            InboxIndicator(count = inboxCount, onClick = {})
            if (showCloseButton && onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close drawer")
                }
            }
        }

        // ── New chat CTA ────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HermesColors.Primary)
                    .clickable { vm.openNewThread() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = HermesColors.OnPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "New chat",
                    color = HermesColors.OnPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }

        // ── Search ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                "Search threads",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Thread list (grouped) ──────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            grouped.forEach { (group, sessions) ->
                item(key = "label-${group.label}") {
                    SectionLabel(
                        text = group.label,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                items(sessions, key = { it.session.sessionId }) { row ->
                    ThreadRow(
                        row = row,
                        isActive = activeRoute?.sessionId == row.session.sessionId,
                        onClick = {
                            vm.openThread(
                                ConversationRoute(
                                    gatewayId = row.session.gatewayId,
                                    profileId = row.session.profileId,
                                    sessionId = row.session.sessionId,
                                ),
                            )
                        },
                    )
                }
            }
            if (grouped.values.all { it.isEmpty() }) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No threads yet — tap New chat to start one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Footer: profile + settings ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileFooterChip(
                modifier = Modifier.weight(1f),
                onClick = { vm.openProfileSheet() },
            )
            IconButton(
                onClick = { vm.openSettingsSheet() },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun InboxIndicator(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Inbox,
            contentDescription = "Activity inbox",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(HermesColors.Primary)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    color = HermesColors.OnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun ThreadRow(row: ThreadRowView, isActive: Boolean, onClick: () -> Unit) {
    val containerColor = if (isActive) HermesColors.Primary.copy(alpha = 0.12f) else Color.Transparent
    val borderColor = if (isActive) HermesColors.Primary.copy(alpha = 0.55f) else Color.Transparent
    val rowShape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(rowShape)
            .background(containerColor)
            .border(1.dp, borderColor, rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V1BProfileChipCompact(
                model = V1BProfileChipModel(
                    handle = row.profile.handle.display.trimStart('@').ifBlank { row.profile.profileId },
                    sub = row.profile.profileId,
                ),
                selected = isActive,
            )
            Text(
                text = row.session.title.ifBlank { "Untitled thread" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = describeRunState(row.session.runState),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = row.preview,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.session.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(HermesColors.Primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
                    .align(Alignment.End),
            ) {
                Text(
                    text = if (row.session.unreadCount > 9) "9+" else row.session.unreadCount.toString(),
                    color = HermesColors.OnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ProfileFooterChip(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(HermesColors.Elevated)
                .border(1.dp, HermesColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = HermesColors.Primary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "@ash · coder-lab",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(StatusOk),
                )
                Text(
                    text = "gw-home · streaming",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Time grouping ────────────────────────────────────────────────────────

private enum class TimeGroup(val label: String) {
    Today("Today"),
    Yesterday("Yesterday"),
    PreviousWeek("Previous 7 days"),
    Earlier("Earlier"),
}

private fun buildThreadRows(fleet: Fleet): Map<TimeGroup, List<ThreadRowView>> {
    val rows = fleet.gateways.flatMap { gateway ->
        gateway.profiles.flatMap { profile ->
            profile.sessions.map { session ->
                ThreadRowView(
                    session = session,
                    handleDisplay = profile.profile.handle.display.ifBlank { profile.profile.profileId },
                    profile = profile.profile,
                    preview = previewFor(session),
                )
            }
        }
    }
    if (rows.isEmpty()) return emptyMap()
    val groups = rows.groupBy { timeGroupFor(it.session) }
    return groups.toSortedMap(compareBy { it.ordinal })
}

private fun timeGroupFor(session: Session): TimeGroup = when (session.runState) {
    RunState.Streaming, RunState.AwaitingApproval, RunState.Completed -> TimeGroup.Today
    RunState.Idle -> TimeGroup.Yesterday
    RunState.Failed -> TimeGroup.PreviousWeek
}

private fun previewFor(session: Session): String = when (session.runState) {
    RunState.Streaming -> "Streaming · awaiting tokens"
    RunState.AwaitingApproval -> "Approval · needs your decision"
    RunState.Completed -> "Idle · last touched"
    RunState.Failed -> "Failed · last run"
    RunState.Idle -> "Idle · last touched"
}

private fun describeRunState(state: RunState): String = when (state) {
    RunState.Streaming -> "live"
    RunState.AwaitingApproval -> "wait"
    RunState.Completed -> "done"
    RunState.Failed -> "err"
    RunState.Idle -> "idle"
}
