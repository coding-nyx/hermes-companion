package com.hermes.companion.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hermes.companion.data.repo.ProfileView
import com.hermes.companion.ui.components.HermesButton

/**
 * T2: Profiles tab - profiles for the active gateway.
 *
 * Renders the [ProfileView] list for the currently active gateway. Each row
 * either shows an "Active" pill (when [activeProfileId] matches the row's
 * profileId) or a "Make active" tap target wired through [onMakeActive].
 *
 * Selection state comes from the SettingsViewModel; persistence is via
 * [com.hermes.companion.data.repo.FleetRepository.setActiveProfile], which
 * also rewrites the file-backed ActiveGatewayConfig so the OS-instantiated
 * NLS picks up the change on its next reconnect.
 */
@Composable
fun ProfilesTab(
    profiles: List<ProfileView>,
    activeProfileId: String?,
    onMakeActive: (profileId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Profiles", style = MaterialTheme.typography.titleLarge)
        Text(
            "The active gateway's profiles. Switching is isolated per gateway.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Box(Modifier.size(12.dp))
        if (profiles.isEmpty()) {
            Text(
                "No profiles - pair a gateway first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.semantics { contentDescription = "No profiles available" },
            )
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(profiles, key = { it.profile.profileId }) { p ->
                val isActive = activeProfileId == p.profile.profileId
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.semantics {
                        contentDescription = "Profile ${p.profile.displayName}${if (isActive) " active" else ""}"
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.profile.displayName, style = MaterialTheme.typography.titleMedium)
                            AssistChip(onClick = {}, label = { Text(p.profile.profileId) })
                            Text(
                                "Sessions: ${p.sessions.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        if (isActive) {
                            ActivePill()
                        } else {
                            HermesButton(
                                label = "Make active",
                                onClick = { onMakeActive(p.profile.profileId) },
                                filled = false,
                                modifier = Modifier.semantics {
                                    contentDescription = "Make ${p.profile.displayName} active"
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact "Active" badge. Reuses [MaterialTheme.colorScheme.primaryContainer]
 * so the pill reads as the same brand accent as the rest of the active-state
 * UI (e.g. the chosen gateway's row in GatewaysTab).
 */
@Composable
private fun ActivePill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics { contentDescription = "Currently active profile" }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "Active",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}