package com.hermes.companion.ui.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.companion.data.repo.Connectivity
import com.hermes.companion.data.repo.GatewayView
import com.hermes.companion.data.repo.ProfileView
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.domain.Session

@Composable
fun AgentsScreen(
    onOpenChat: (ConversationRoute) -> Unit,
    vm: AgentsViewModel = viewModel(factory = AgentsViewModel.factory()),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var newThreadTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // gatewayId to profileId

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
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
            state.gateways.forEach { gateway ->
                GatewayGroup(
                    view = gateway,
                    onOpenChat = onOpenChat,
                    onNewThread = { profileId -> newThreadTarget = gateway.gateway.id to profileId }
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
    }

    newThreadTarget?.let { (gatewayId, profileId) ->
        NewThreadDialog(
            profileId = profileId,
            onDismiss = { newThreadTarget = null },
            onConfirm = { title ->
                vm.createThread(gatewayId, profileId, title) { route ->
                    newThreadTarget = null
                    onOpenChat(route)
                }
            }
        )
    }
}

@Composable
private fun GatewayGroup(
    view: GatewayView,
    onOpenChat: (ConversationRoute) -> Unit,
    onNewThread: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(view.connectivity.dotColor()),
            )
            Box(Modifier.size(8.dp))
            Text(view.gateway.label, style = MaterialTheme.typography.titleMedium)
            Box(Modifier.size(8.dp))
            Text(
                view.gateway.kind.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        // Reachability is data on the row, not an exception that emptied the
        // screen. Cached profiles stay visible while a gateway is down.
        view.connectivity.reasonOrNull?.let { reason ->
            Text(
                "Unreachable — $reason",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Box(Modifier.size(8.dp))
        view.profiles.forEach { profile ->
            ProfileRow(
                view = profile,
                onOpenChat = { sessionId ->
                    onOpenChat(ConversationRoute(view.gateway.id, profile.profile.profileId, sessionId))
                },
                onNewThread = { onNewThread(profile.profile.profileId) },
            )
        }
    }
}

@Composable
private fun ProfileRow(
    view: ProfileView,
    onOpenChat: (String) -> Unit,
    onNewThread: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${view.profile.handle.display}", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.size(8.dp))
                Text(
                    view.profile.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Box(Modifier.weight(1f))
                IconButton(
                    onClick = onNewThread,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New Thread", modifier = Modifier.size(20.dp))
                }
            }
            Box(Modifier.size(8.dp))
            if (view.sessions.isEmpty()) {
                Text(
                    "No sessions cached",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            view.sessions.forEach { session ->
                SessionRow(session) { onOpenChat(session.sessionId) }
            }
        }
    }
}

@Composable
private fun SessionRow(session: Session, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        onClick = onClick,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "state=${session.runState.name} · unread=${session.unreadCount}",
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
}

@Composable
private fun NewThreadDialog(
    profileId: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New thread with @$profileId") },
        text = {
            Column {
                Text(
                    "Enter a title for this conversation thread.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(Modifier.size(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("e.g. Daily Triage, Deploy check") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.ifBlank { "New chat" }) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun Connectivity.dotColor() = when (this) {
    is Connectivity.Live -> MaterialTheme.colorScheme.primary
    is Connectivity.Degraded -> MaterialTheme.colorScheme.tertiary
    is Connectivity.Down -> MaterialTheme.colorScheme.error
    Connectivity.Unknown -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
}
