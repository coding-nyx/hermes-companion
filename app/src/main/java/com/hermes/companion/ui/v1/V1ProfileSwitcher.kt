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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.data.repo.GatewayView
import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.ui.theme.DisplayMedium
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.PlexMono
import com.hermes.companion.ui.theme.StatusDim
import com.hermes.companion.ui.theme.StatusOk
import com.hermes.companion.ui.theme.StatusWarn

/**
 * ProfileSwitcher — bottom sheet that lists every known profile grouped by
 * gateway. The user can see topology at a glance ("ah, my cloud profile is
 * degraded, that's why @ash-cloud is stale") and tap a row to make that
 * profile the active one.
 *
 * Active row pinned with an Indigo check + accent border; inactive rows in
 * the neutral SurfaceCard. Search field at the top filters by handle /
 * display name.
 *
 * Reuses HermesMark (small) and StatusBadge semantics; the badge tones
 * (Live / Muted / Warn) map to gateway connectivity (Live / Unknown /
 * Degraded+Down).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V1ProfileSwitcher(
    vm: V1ShellViewModel,
    onDismiss: () -> Unit,
) {
    val fleet by vm.fleet.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Drag handle
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)),
            )

            // Title row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Switch profile", style = DisplayMedium)
                    Text(
                        "Active profile routes every new message.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // Search (non-functional placeholder — the real impl would wire
            // a rememberSaveable String + filter the list).
            SearchFieldPlaceholder()

            // Profile list, grouped by gateway
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                fleet.gateways.forEach { gateway ->
                    item(key = "group-${gateway.gateway.id}") {
                        GatewayGroupHeader(gateway)
                    }
                    items(gateway.profiles, key = { "p-${gateway.gateway.id}-${it.profile.profileId}" }) { pv ->
                        ProfileSwitcherRow(pv.profile)
                    }
                }
                if (fleet.gateways.isEmpty()) {
                    item {
                        Text(
                            "No profiles yet — add a gateway in Settings → Pair as Node.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Footer hint
            Text(
                "Active profile routes every new message and inherits the next empty thread. Existing threads stay where they are.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchFieldPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
        Text(
            "Search profiles",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GatewayGroupHeader(gateway: GatewayView) {
    val isLive = gateway.connectivity == com.hermes.companion.data.repo.Connectivity.Live
    val isWarn = gateway.connectivity is com.hermes.companion.data.repo.Connectivity.Degraded ||
        gateway.connectivity is com.hermes.companion.data.repo.Connectivity.Down
    val dotColor = when {
        isLive -> StatusOk
        isWarn -> StatusWarn
        else -> StatusDim
    }
    val staleLabel = when (val c = gateway.connectivity) {
        is com.hermes.companion.data.repo.Connectivity.Degraded -> "stale ${c.since / 60_000}m"
        is com.hermes.companion.data.repo.Connectivity.Down -> "down"
        else -> "${gateway.profiles.size} profiles"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            gateway.gateway.id,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            staleLabel,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = if (isWarn) StatusWarn else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f))
        Text(
            "${gateway.profiles.size} profiles",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileSwitcherRow(profile: AgentProfile) {
    // Treat the first profile as the "active" one — sufficient for the visual.
    val isActive = true
    val rowShape = RoundedCornerShape(14.dp)
    val containerColor = if (isActive) HermesColors.Primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (isActive) HermesColors.Primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
    val initialColor = HermesColors.Primary
    val initialBg = HermesColors.Primary.copy(alpha = 0.20f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(containerColor)
            .border(1.dp, borderColor, rowShape)
            .clickable { /* TODO: route activation */ }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(initialBg)
                .border(1.dp, HermesColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.displayName.take(1).ifBlank { "?" },
                color = initialColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                fontFamily = PlexMono,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    profile.displayName.ifBlank { profile.profileId },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    "@${profile.handle.display}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PlexMono,
                        fontSize = 12.sp,
                    ),
                    color = if (isActive) HermesColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${profile.profileId} · opus-4.6 · streaming",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isActive) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Active",
                tint = HermesColors.Primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
