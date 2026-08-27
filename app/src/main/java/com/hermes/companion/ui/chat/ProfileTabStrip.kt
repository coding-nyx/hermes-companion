package com.hermes.companion.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hermes.companion.data.repo.GatewayView
import com.hermes.companion.data.repo.ProfileView

/**
 * T4 closure: horizontal chip strip - one chip per (gateway x profile) pair.
 *
 * Stateless: takes the fleet list + the current selection, dispatches an
 * onSelect callback when a chip is tapped. Pure UI - no Flow / ViewModel
 * ownership; the host screen (ChatScreen) wires it to its ViewModel.
 *
 * Why chips (not tabs): the v0.1 fleet is small (typically 1-2 gateways,
 * 1-2 profiles each = 1-4 chips). Tabs would imply an equal-weight
 * navigation model; chips correctly read as "switch the active profile".
 */
@Composable
fun ProfileTabStrip(
    gateways: List<GatewayView>,
    activeGatewayId: String?,
    activeProfileId: String?,
    onSelect: (gatewayId: String, profileId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pairs = gateways.flatMap { gw ->
        gw.profiles.map { profile -> gw to profile }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(pairs, key = { (gw, p) -> "${gw.gateway.id}/${p.profile.profileId}" }) { (gw, p) ->
            val selected = gw.gateway.id == activeGatewayId && p.profile.profileId == activeProfileId
            val label = "${gw.gateway.label} / ${p.profile.displayName}"
            FilterChip(
                selected = selected,
                onClick = { onSelect(gw.gateway.id, p.profile.profileId) },
                label = { Text(label) },
                modifier = Modifier.semantics { contentDescription = "Profile $label" },
                // FilterChip.selectedContainerColor / selectedLabelColor via FilterChipDefaults.filterChipColors
                // (we use the default palette for both states; selected state is conveyed by the
                //  filter-checkmark icon and the primaryContainer in the chip's leading slot.)
            )
        }
    }
}
