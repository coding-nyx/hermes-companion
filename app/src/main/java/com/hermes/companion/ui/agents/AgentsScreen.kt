package com.hermes.companion.ui.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.companion.domain.AgentProfile
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.GatewayConnection
import com.hermes.companion.domain.Session

@Composable
fun AgentsScreen(
    onOpenChat: (ConversationRoute) -> Unit,
    vm: AgentsViewModel = viewModel(factory = AgentsViewModel.factory()),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Agents", style = MaterialTheme.typography.titleLarge)
        Text(
            "Gateway → profile → session. Same-name profiles across gateways are disambiguated.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Box(Modifier.size(12.dp))
        if (state.loading) {
            Text("Loading…")
        } else {
            state.gateways.forEach { gw ->
                GatewayGroup(
                    gateway = gw,
                    profiles = state.profiles.filter { it.gatewayId == gw.id },
                    sessionsByRoute = state.sessionsByRoute,
                    onOpenChat = onOpenChat,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun GatewayGroup(
    gateway: GatewayConnection,
    profiles: List<AgentProfile>,
    sessionsByRoute: Map<ConversationRoute, List<Session>>,
    onOpenChat: (ConversationRoute) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(Modifier.size(8.dp))
            Text(gateway.label, style = MaterialTheme.typography.titleMedium)
            Box(Modifier.size(8.dp))
            Text(
                gateway.kind.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Box(Modifier.size(8.dp))
        profiles.forEach { profile ->
            val route = ConversationRoute(gateway.id, profile.profileId, "")
            val sessions = sessionsByRoute.filterKeys { it.gatewayId == gateway.id && it.profileId == profile.profileId }
            ProfileRow(
                profile = profile,
                sessions = sessions.values.flatten().distinctBy { it.sessionId },
                onOpenChat = { sessionId ->
                    onOpenChat(ConversationRoute(gateway.id, profile.profileId, sessionId))
                },
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: AgentProfile,
    sessions: List<Session>,
    onOpenChat: (sessionId: String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@${profile.handle.display}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(Modifier.weight(1f))
                Text(
                    profile.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Box(Modifier.size(8.dp))
            sessions.forEach { session ->
                SessionRow(session, onClick = { onOpenChat(session.sessionId) })
            }
        }
    }
}

@Composable
private fun SessionRow(session: Session, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(session.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                "state=${session.runState.name} unread=${session.unreadCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = "Open",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}
