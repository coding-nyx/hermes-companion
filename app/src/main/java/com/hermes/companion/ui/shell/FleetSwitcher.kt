package com.hermes.companion.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.data.repo.Connectivity
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.ui.agents.AgentsViewModel
import com.hermes.companion.ui.components.MetaText
import com.hermes.companion.ui.theme.LocalHermesStatus

/**
 * The fleet switcher: gateway → profile → thread across the whole fleet, from one
 * sheet. Reuses the agents fleet flow; tapping a thread opens it in Chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetSwitcher(
    onDismiss: () -> Unit,
    onOpenChat: (ConversationRoute) -> Unit,
    vm: AgentsViewModel = hiltViewModel(),
) {
    val fleet by vm.state.collectAsStateWithLifecycle()
    val status = LocalHermesStatus.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text("Switch route", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (fleet.gateways.isEmpty()) {
                item {
                    Text(
                        "No gateways yet — add one from Settings → Discover.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            fleet.gateways.forEach { gw ->
                item(key = "gw-${gw.gateway.id}") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val dot = when (gw.connectivity) {
                            is Connectivity.Live -> status.ok
                            is Connectivity.Degraded -> status.warn
                            is Connectivity.Down -> status.error
                            Connectivity.Unknown -> status.dim
                        }
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Text(gw.gateway.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(gw.tier.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                gw.profiles.forEach { p ->
                    item(key = "p-${gw.gateway.id}-${p.profile.profileId}") {
                        Text(
                            "@${p.profile.handle.display}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                        )
                    }
                    items(p.sessions, key = { "s-${gw.gateway.id}-${it.sessionId}" }) { s ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onOpenChat(ConversationRoute(gw.gateway.id, p.profile.profileId, s.sessionId)) }
                                .padding(start = 28.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(s.title.ifBlank { s.sessionId.takeLast(8) }, style = MaterialTheme.typography.bodyMedium)
                                MetaText(s.sessionId.takeLast(12))
                            }
                            if (s.unreadCount > 0) {
                                Text("${s.unreadCount}", style = MaterialTheme.typography.labelSmall, color = status.warn)
                            }
                        }
                    }
                }
            }
            item { Box(Modifier.size(16.dp)) }
        }
    }
}
