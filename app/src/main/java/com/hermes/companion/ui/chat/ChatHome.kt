package com.hermes.companion.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.companion.domain.ConversationRoute

/**
 * The Chat destination. Shows the selected thread, or a clear empty state when
 * nothing is selected yet — so Chat is always findable even before a gateway is
 * added. No own back chrome (the route capsule sits above it).
 */
@Composable
fun ChatHome(route: ConversationRoute?, onOpenSwitcher: () -> Unit) {
    if (route == null) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No conversation yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pick a gateway → profile → thread from the route capsule above, or add a " +
                    "gateway in Settings → Discover, then start a thread in Agents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenSwitcher) { Text("Open the switcher") }
        }
    } else {
        ChatScreen(
            route = route,
            onBack = {},
            showBack = false,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        )
    }
}
