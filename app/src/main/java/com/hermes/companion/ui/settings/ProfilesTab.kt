package com.hermes.companion.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hermes.companion.data.repo.ProfileView

/**
 * T3B: Profiles tab - profiles for the active gateway.
 *
 * The current implementation always shows 1 profile per gateway (T01 finding -
 * the gateway has a single-profile facade). The list is rendered from the
 * [gateways] argument's [ProfileView] children of the active gateway.
 *
 * "Make active" is wired but is a no-op until T4 adds multi-profile support
 * to the gateway. Currently it just shows the profile id + handle.
 */
@Composable
fun ProfilesTab(
    profiles: List<ProfileView>,
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.semantics { contentDescription = "Profile ${p.profile.displayName}" },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.profile.displayName, style = MaterialTheme.typography.titleMedium)
                        AssistChip(onClick = {}, label = { Text(p.profile.profileId) })
                        Text(
                            "Sessions: ${p.sessions.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
