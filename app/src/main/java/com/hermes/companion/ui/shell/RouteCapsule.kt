package com.hermes.companion.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.companion.domain.ConversationRoute
import com.hermes.companion.ui.theme.HermesMono

/**
 * The always-visible route capsule — `gateway › @profile › thread`. Tap opens the
 * fleet switcher. The same profile name can live on two gateways, so the active
 * route must always be legible (`plan/06-ux/information-architecture.md`).
 */
@Composable
fun RouteCapsule(route: ConversationRoute?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            if (route == null) {
                Text(
                    "Pick a gateway › profile › thread",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    "${route.gatewayId} › @${route.profileId} › ${route.sessionId.takeLast(6)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = HermesMono),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Icon(Icons.Filled.UnfoldMore, contentDescription = "Switch route", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
