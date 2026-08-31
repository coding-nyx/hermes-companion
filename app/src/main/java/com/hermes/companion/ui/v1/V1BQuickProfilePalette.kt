package com.hermes.companion.ui.v1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.ProfileHandle
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.Indigo80
import com.hermes.companion.ui.theme.PlexMono

/**
 * Phase B · spec 6 — ⌘K / Ctrl+K profile palette.
 *
 * Lifted above the composer. Anatomy (from the mock):
 *   - search field (with ⌘K hint chip)
 *   - filtered profile list (matched against name or handle)
 *   - footer: "↑↓ navigate · ⏎ switch · esc close"
 *
 * Caller passes the available profiles and handles selection via
 * [onPick]. The palette doesn't actually trap keyboard input — the
 * parent should wire Ctrl+K / ⌘K to set [visible] = true and pass
 * esc to set it false. The internal filter is text-only; arrow
 * navigation is a future polish.
 */
@Composable
fun V1BQuickProfilePalette(
    profiles: List<AgentProfile>,
    visible: Boolean,
    onDismiss: () -> Unit,
    onPick: (AgentProfile) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
) {
    if (!visible) return
    var query by remember { mutableStateOf(initialQuery) }
    val filtered = remember(query, profiles) {
        if (query.isBlank()) profiles
        else profiles.filter { it.profileId.contains(query, ignoreCase = true) || it.displayName.contains(query, ignoreCase = true) }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HermesColors.Surface)
            .border(1.dp, Indigo80, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Search field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(HermesColors.Surface)
                .border(1.dp, HermesColors.Border, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "🔍",
                style = TextStyle(fontSize = 14.sp, color = HermesColors.Muted),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Search profiles…",
                        style = TextStyle(fontSize = 14.sp, color = HermesColors.Fg.copy(alpha = 0.5f)),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = HermesColors.Fg),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(HermesColors.Fg),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "⌘K",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    color = HermesColors.Muted,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(HermesColors.Surface)
                    .border(1.dp, HermesColors.Border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }

        // Filtered list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (filtered.size > 5) 220.dp else (filtered.size * 56).dp.coerceAtLeast(48.dp)),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(filtered, key = { it.profileId }) { profile ->
                V1BPaletteRow(
                    profile = profile,
                    selected = query.isNotBlank() && filtered.firstOrNull()?.profileId == profile.profileId,
                    onClick = { onPick(profile); onDismiss() },
                )
            }
        }

        // Footer hint
        Text(
            "↑↓ navigate · ⏎ switch · esc close",
            style = TextStyle(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                color = HermesColors.Muted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun V1BPaletteRow(
    profile: AgentProfile,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = ProfilePalette.accentForProfile(profile.profileId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) accent.copy(alpha = 0.30f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.profileId.take(2).lowercase(),
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                ),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "@${profile.profileId}",
                style = TextStyle(fontSize = 13.sp, color = HermesColors.Fg, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${profile.profileId}-x · on gw-home",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    color = HermesColors.Muted,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Text(
                "⏎",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    color = Indigo80,
                ),
            )
        }
    }
}

/** Convenience: build a ProfileHandle when only the id is known. */
fun profileHandleFor(id: String): ProfileHandle =
    ProfileHandle(profileId = id, display = "@$id")
