package com.hermes.companion.ui.v1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.companion.domain.AgentProfile

/**
 * Phase B · spec 5 — horizontal strip of profile tiles joined by ›
 * glyphs, separated from [V1BProfileChip] for the file-list contract.
 *
 * Two render modes:
 *   - `compact = true`  → route capsule tail (pill-shaped chips, no
 *     turn counts, scrollable)
 *   - `compact = false` → ContextPanel "Cast" card (rich tiles with
 *     monogram + handle + turn counts)
 *
 * The strip uses [LazyRow] so a chain of >8 profiles scrolls
 * horizontally (decision: "Handoff chain > 8 profiles → strip
 * scrolls"). Caller passes [onProfileTap] to filter the transcript
 * to that profile's turns.
 */
@Composable
fun V1BHandoffChainStrip(
    profiles: List<AgentProfile>,
    turnCounts: Map<String, Int> = emptyMap(),
    compact: Boolean = false,
    onProfileTap: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(profiles, key = { it.profileId }) { profile ->
            val accent = ProfilePalette.accentForProfile(profile.profileId)
            if (compact) {
                V1BProfileChip(
                    profile = profile,
                    accentColor = accent,
                    onClick = onProfileTap?.let { { it(profile.profileId) } },
                )
            } else {
                V1BHandoffTile(
                    profile = profile,
                    accent = accent,
                    turns = turnCounts[profile.profileId] ?: 0,
                    onTap = onProfileTap?.let { { it(profile.profileId) } },
                )
            }
        }
    }
}
