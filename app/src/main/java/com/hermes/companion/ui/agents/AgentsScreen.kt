package com.hermes.companion.ui.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.ui.nav.AskHermes
import com.hermes.companion.ui.components.SurfaceCard
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography

@Composable
fun AgentsScreen(
    onOpenChat: (ConversationRoute) -> Unit,
    vm: AgentsViewModel = viewModel(factory = AgentsViewModel.factory()),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loading, state.sessionsByRoute.size) {
        if (state.loading || AskHermes.pending == null) return@LaunchedEffect
        val first = state.sessionsByRoute.keys.firstOrNull() ?: return@LaunchedEffect
        onOpenChat(first)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text("Hermes", style = HermesTypography.displayMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Gateway → profile → session. Same-name profiles across gateways are disambiguated.",
            style = HermesTypography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        if (state.loading) {
            Text("Loading…", style = HermesTypography.bodyMedium)
        } else {
            state.gateways.forEach { gw ->
                SectionLabel(gw.label)
                val profiles = state.profiles.filter { it.gatewayId == gw.id }
                SurfaceCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column {
                        profiles.forEach { profile ->
                            val sessions = state.sessionsByRoute
                                .filterKeys { it.gatewayId == gw.id && it.profileId == profile.profileId }
                                .values.flatten().distinctBy { it.sessionId }
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "@${profile.handle.display}",
                                        style = HermesTypography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(profile.displayName, style = HermesTypography.bodySmall)
                                }
                                Spacer(Modifier.height(8.dp))
                                sessions.forEach { session ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onOpenChat(ConversationRoute(gw.id, profile.profileId, session.sessionId))
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(session.title, style = HermesTypography.bodyLarge.copy(fontSize = 14.sp, color = HermesColors.Fg))
                                            Text(
                                                "${session.runState.name.lowercase()} · ${session.unreadCount} unread",
                                                style = HermesType.kickerSubtle,
                                            )
                                        }
                                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = HermesColors.Subtle)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
