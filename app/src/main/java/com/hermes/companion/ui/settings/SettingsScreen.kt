package com.hermes.companion.ui.settings

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hermes.companion.device.PhoneControl
import com.hermes.companion.node.CompanionLink
import com.hermes.companion.node.NodePrefs
import com.hermes.companion.ui.components.BadgeTone
import com.hermes.companion.ui.components.HermesButton
import com.hermes.companion.ui.components.HermesField
import com.hermes.companion.ui.components.SectionLabel
import com.hermes.companion.ui.components.StatusBadge
import com.hermes.companion.ui.components.SurfaceCard
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography

private const val PLUGIN_INSTALL =
    "curl -fsSL https://raw.githubusercontent.com/coding-nyx/hermes-companion-plugin/main/install.sh | bash"

@Composable
fun SettingsScreen(
    onUnpair: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory()),
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val phone by PhoneControl.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf(phone.name) }
    var copied by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    val linkUp = CompanionLink.isUp()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column {
            Text("More", style = HermesTypography.displayMedium)
            Text("Gateway, sessions, and how Hermes is allowed to act.", style = HermesTypography.bodyMedium)
        }

        Column {
            SectionLabel("This device")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HermesField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Device name")
                    HermesButton("Save name", onClick = {
                        if (name.isNotBlank()) PhoneControl.setName(ctx, name.trim())
                    }, filled = false, modifier = Modifier.fillMaxWidth())
                    InfoRow("Gateway", NodePrefs.gatewayUrl(ctx).ifBlank { "—" })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Link", style = HermesTypography.bodyMedium, modifier = Modifier.weight(1f))
                        StatusBadge(if (linkUp) "up" else "reconnecting", if (linkUp) BadgeTone.Live else BadgeTone.Warn)
                    }
                    InfoRow("Pairing", NodePrefs.pairingCode(ctx))
                    InfoRow("Tailnet", NodePrefs.tailnet(ctx).ifBlank { "—" })
                }
            }
        }

        Column {
            SectionLabel("Hermes plugin")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Install on the Hermes machine, enable the companion platform, then scan this tailnet. The phone only dials out.",
                        style = HermesTypography.bodyMedium,
                    )
                    Text(PLUGIN_INSTALL, style = HermesType.mono.copy(color = HermesColors.Fg))
                    HermesButton(
                        if (copied) "Copied" else "Copy install",
                        onClick = {
                            clipboard.setText(AnnotatedString(PLUGIN_INSTALL))
                            copied = true
                        },
                        filled = false,
                    )
                }
            }
        }

        Column {
            SectionLabel("Gateways")
            Text(
                "Each gateway has its own profiles, sessions, and capability grants. Switching is isolated.",
                style = HermesTypography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    state.gateways.forEach { gw ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(gw.label, style = HermesTypography.titleMedium)
                                Text("${gw.id} · ${gw.kind.name.lowercase()}", style = HermesTypography.bodySmall)
                            }
                            IconButton(onClick = { vm.removeGateway(gw.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = HermesColors.Muted)
                            }
                        }
                    }
                }
            }
        }

        if (!confirmUnpair) {
            HermesButton("Unpair this device", onClick = { confirmUnpair = true }, filled = false, modifier = Modifier.fillMaxWidth())
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This drops the mailbox session. Pair again to reconnect.", style = HermesTypography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HermesButton("Cancel", onClick = { confirmUnpair = false }, filled = false, modifier = Modifier.weight(1f))
                    HermesButton("Unpair", onClick = {
                        CompanionLink.stop()
                        NodePrefs.setSession(ctx, null)
                        onUnpair()
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HermesTypography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = HermesType.mono.copy(color = HermesColors.Fg), maxLines = 1)
    }
}
