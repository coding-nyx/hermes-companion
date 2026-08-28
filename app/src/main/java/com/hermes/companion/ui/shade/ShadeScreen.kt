package com.hermes.companion.ui.shade

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.device.ShadeStore
import com.hermes.companion.ui.components.HermesButton
import com.hermes.companion.ui.components.SectionLabel
import com.hermes.companion.ui.components.SurfaceCard
import com.hermes.companion.ui.components.ToggleRow
import com.hermes.companion.ui.nav.AskHermes
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography
import com.hermes.companion.ui.util.relativeTime

private enum class Filter { All, Unread, Queued }

@Composable
fun ShadeScreen(onAsk: (String) -> Unit) {
    val items by ShadeStore.items.collectAsState()
    var filter by remember { mutableStateOf(Filter.All) }
    var listener by remember { mutableStateOf(ShadeStore.listenerOn) }
    var forward by remember { mutableStateOf(ShadeStore.forwardToHermes) }
    val queued = items.filter { it.queued }
    val unread = items.filter { it.unread }
    val list = when (filter) {
        Filter.Unread -> unread
        Filter.Queued -> queued
        Filter.All -> items
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text("Notification shade", style = HermesTypography.displayMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Listener captures alerts. Hermes is told when you forward — or automatically, if the queue is on.",
                style = HermesTypography.bodyMedium,
            )
        }
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow("Notification listener", if (listener) "Capturing shade events" else "Paused", listener, {
                    listener = it
                    ShadeStore.listenerOn = it
                })
                ToggleRow("Forward to Hermes", "Queue new alerts for the agent", forward, {
                    forward = it
                    ShadeStore.forwardToHermes = it
                }, showDivider = false)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HermesButton("Inject alert", onClick = { ShadeStore.inject() })
            HermesButton(
                if (queued.isEmpty()) "Forward queue" else "Forward queue (${queued.size})",
                onClick = {
                    val ids = queued.map { it.id }
                    ShadeStore.markForwarded(ids)
                    AskHermes.pending = "Summarize these phone notifications: " +
                        queued.joinToString(" · ") { "${it.app}: ${it.title}" }
                    onAsk(AskHermes.pending!!)
                },
                filled = false,
                enabled = queued.isNotEmpty(),
            )
        }
        Column {
            SectionLabel("Alerts") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filter.entries.forEach { f ->
                        val on = filter == f
                        Text(
                            f.name.lowercase(),
                            style = HermesType.kicker.copy(color = if (on) HermesColors.Fg else HermesColors.Subtle),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) HermesColors.Elevated else HermesColors.Background)
                                .clickable { filter = f }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    if (list.isEmpty()) {
                        Text("Nothing in this filter.", style = HermesTypography.bodyMedium, modifier = Modifier.padding(20.dp))
                    }
                    list.forEach { n ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${n.app} · ${n.title}", style = HermesTypography.bodyLarge.copy(fontSize = 14.sp, color = HermesColors.Fg))
                                Text(n.body, style = HermesTypography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                    if (n.queued) Text("QUEUED", style = HermesType.kicker.copy(color = HermesColors.Warn, fontSize = 9.sp))
                                    if (n.forwarded) Text("SENT", style = HermesType.kicker.copy(color = HermesColors.Ok, fontSize = 9.sp))
                                    Text("Dismiss", style = HermesType.kicker.copy(color = HermesColors.Muted, fontSize = 9.sp), modifier = Modifier.clickable { ShadeStore.dismiss(n.id) })
                                }
                            }
                            Text(relativeTime(n.at), style = HermesType.mono.copy(fontSize = 10.sp, color = HermesColors.Subtle))
                        }
                    }
                }
            }
        }
    }
}
