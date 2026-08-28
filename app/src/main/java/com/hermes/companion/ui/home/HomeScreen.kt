package com.hermes.companion.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.device.PhoneControl
import com.hermes.companion.device.ShadeStore
import com.hermes.companion.node.CompanionLink
import com.hermes.companion.node.NodePrefs
import com.hermes.companion.ui.components.BadgeTone
import com.hermes.companion.ui.components.Caduceus
import com.hermes.companion.ui.components.Chip
import com.hermes.companion.ui.components.LinkText
import com.hermes.companion.ui.components.QuickTile
import com.hermes.companion.ui.components.SectionLabel
import com.hermes.companion.ui.components.StatusBadge
import com.hermes.companion.ui.components.SurfaceCard
import com.hermes.companion.ui.nav.AskHermes
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography
import com.hermes.companion.ui.util.relativeTime
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpenShade: () -> Unit,
    onOpenDevice: () -> Unit,
    onAsk: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val phone by PhoneControl.state.collectAsState()
    val shade by ShadeStore.items.collectAsState()
    val linkUp = CompanionLink.isUp()
    val unread = shade.count { it.unread }
    val queued = shade.count { it.queued }

    LaunchedEffect(Unit) {
        while (true) {
            PhoneControl.refresh(ctx)
            delay(8_000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(phone.name.uppercase(), style = HermesType.kickerSubtle, modifier = Modifier.weight(1f))
                    StatusBadge(
                        if (linkUp) "plugin · live" else "reconnecting",
                        if (linkUp) BadgeTone.Live else BadgeTone.Warn,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(80.dp)) {
                            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            drawCircle(HermesColors.Elevated, radius = size.minDimension / 2 - 6, style = stroke)
                            drawArc(
                                color = HermesColors.Fg,
                                startAngle = -90f,
                                sweepAngle = (phone.battery / 100f) * 360f,
                                useCenter = false,
                                style = stroke,
                            )
                        }
                        Caduceus(Modifier.size(28.dp))
                    }
                    Column {
                        Text("${phone.battery}%", style = HermesTypography.displayMedium)
                        Text(
                            "${if (phone.charging) "Charging" else "On battery"} · ${phone.currentApp}",
                            style = HermesTypography.bodyMedium,
                        )
                        Text(
                            "${if (phone.dnd) "Do not disturb" else "Alerts on"} · ${if (phone.wifi) "Wi-Fi" else "Radio off"} · ${NodePrefs.gatewayUrl(ctx).ifBlank { "unpaired" }}",
                            style = HermesTypography.bodySmall.copy(color = HermesColors.Subtle),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Hermes is idle. Relay a notification or ask it to take the wheel.",
                    style = HermesTypography.bodyMedium.copy(color = HermesColors.Fg),
                )
            }
        }

        Column {
            SectionLabel("Quick controls") { LinkText("All") { onOpenDevice() } }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTile("Wi-Fi", phone.wifi, if (phone.wifi) Icons.Outlined.Wifi else Icons.Outlined.WifiOff, {
                        PhoneControl.patch(ctx) { it.copy(wifi = !it.wifi, airplane = false) }
                    }, modifier = Modifier.weight(1f))
                    QuickTile("Bluetooth", phone.bluetooth, Icons.Outlined.Bluetooth, {
                        PhoneControl.patch(ctx) { it.copy(bluetooth = !it.bluetooth) }
                    }, modifier = Modifier.weight(1f))
                    QuickTile("DND", phone.dnd, Icons.Outlined.NightsStay, {
                        PhoneControl.patch(ctx) { it.copy(dnd = !it.dnd) }
                    }, modifier = Modifier.weight(1f))
                    QuickTile("Flash", phone.flashlight, Icons.Outlined.FlashlightOn, {
                        PhoneControl.patch(ctx) { it.copy(flashlight = !it.flashlight) }
                    }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickTile("Airplane", phone.airplane, Icons.Outlined.AirplanemodeActive, {
                        PhoneControl.patch(ctx) { it.copy(airplane = !it.airplane, wifi = it.airplane, bluetooth = it.airplane) }
                    }, modifier = Modifier.weight(1f))
                    QuickTile("Silent", phone.ringer == 0, Icons.Outlined.VolumeUp, {
                        PhoneControl.patch(ctx) { it.copy(ringer = if (it.ringer == 0) 70 else 0) }
                    }, modifier = Modifier.weight(1f))
                    QuickTile("Alerts", unread > 0, Icons.Outlined.Notifications, onOpenShade, unread.takeIf { it > 0 }, Modifier.weight(1f))
                    QuickTile("Queue", queued > 0, Icons.Outlined.Notifications, onOpenShade, queued.takeIf { it > 0 }, Modifier.weight(1f))
                }
            }
        }

        Column {
            SectionLabel("Shade") { LinkText("Open") { onOpenShade() } }
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    shade.take(4).forEach { n ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenShade)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${n.app} · ${n.title}", style = HermesTypography.bodyLarge.copy(fontSize = 14.sp, color = HermesColors.Fg), maxLines = 1)
                                Text(n.body, style = HermesTypography.bodySmall, maxLines = 1)
                            }
                            Text(relativeTime(n.at), style = HermesType.mono.copy(fontSize = 10.sp, color = HermesColors.Subtle))
                        }
                    }
                    if (shade.isEmpty()) {
                        Text("Shade is empty.", style = HermesTypography.bodyMedium, modifier = Modifier.padding(16.dp).fillMaxWidth())
                    }
                }
            }
        }

        Column {
            SectionLabel("Ask Hermes")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // wrap chips - simple column of two rows
            }
            val asks = listOf("Summarize my notifications", "Turn on do not disturb", "Silence the ringer", "What’s on this phone?")
            asks.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { q ->
                        Chip(q, onClick = {
                            AskHermes.pending = q
                            onAsk(q)
                        }, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
