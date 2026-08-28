package com.hermes.companion.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hermes.companion.node.CompanionLink
import com.hermes.companion.node.NodePrefs
import com.hermes.companion.ui.components.BadgeTone
import com.hermes.companion.ui.components.HermesButton
import com.hermes.companion.ui.components.HermesField
import com.hermes.companion.ui.components.HermesMark
import com.hermes.companion.ui.components.StatusBadge
import com.hermes.companion.ui.components.SurfaceCard
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private enum class Step { Install, Discover, Pair }

private data class Peer(val id: String, val label: String, val detail: String, val url: String)

private const val PLUGIN_INSTALL =
    "curl -fsSL https://raw.githubusercontent.com/coding-nyx/hermes-companion-plugin/main/install.sh | bash"

@Composable
fun PairingScreen(onPaired: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var step by remember { mutableStateOf(if (NodePrefs.pluginAck(ctx)) Step.Discover else Step.Install) }
    var tailnet by remember { mutableStateOf(NodePrefs.tailnet(ctx)) }
    var extraHost by remember { mutableStateOf(NodePrefs.gatewayUrl(ctx).ifBlank { "" }) }
    var code by remember { mutableStateOf(NodePrefs.pairingCode(ctx)) }
    var peers by remember { mutableStateOf(listOf<Peer>()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    fun scan() {
        scanning = true
        error = ""
        NodePrefs.setTailnet(ctx, tailnet)
        scope.launch {
            val found = probePeers(tailnet, extraHost)
            peers = found
            if (selected == null || found.none { it.id == selected }) selected = found.firstOrNull()?.id
            scanning = false
        }
    }

    LaunchedEffect(step) {
        if (step == Step.Discover || (step == Step.Pair && peers.isEmpty())) scan()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HermesColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HermesMark(40.dp)
            Column {
                Text("Hermes", style = HermesTypography.titleMedium.copy(fontFamily = com.hermes.companion.ui.theme.InstrumentSerif))
                Text("COMPANION", style = HermesType.kicker)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Step.entries.forEachIndexed { i, s ->
                Text(
                    "${i + 1} ${s.name.lowercase()}",
                    style = if (step == s) HermesType.kicker.copy(color = HermesColors.Fg) else HermesType.kickerSubtle,
                    modifier = Modifier.clickable { step = s },
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 28.dp),
        ) {
            when (step) {
                Step.Install -> {
                    Text("PLUGIN", style = HermesType.kicker)
                    Spacer(Modifier.height(12.dp))
                    Text("Install Companion on the Hermes box.", style = HermesTypography.displayLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "This is a gateway platform plugin. Hermes advertises itself on Tailscale; the phone only dials out.",
                        style = HermesTypography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("ON THE HERMES MACHINE", style = HermesType.kickerSubtle)
                            Spacer(Modifier.height(12.dp))
                            Text(PLUGIN_INSTALL, style = HermesType.mono.copy(color = HermesColors.Fg))
                            Spacer(Modifier.height(12.dp))
                            HermesButton(
                                if (copied) "Copied" else "Copy",
                                onClick = {
                                    clipboard.setText(AnnotatedString(PLUGIN_INSTALL))
                                    copied = true
                                },
                                filled = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Then hermes plugins enable companion and hermes gateway. Listens on port 8642 for MagicDNS.",
                                style = HermesTypography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    HermesButton(
                        "Plugin is installed",
                        onClick = {
                            NodePrefs.setPluginAck(ctx, true)
                            step = Step.Discover
                        },
                        large = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Step.Discover -> {
                    Text("TAILSCALE", style = HermesType.kicker)
                    Spacer(Modifier.height(12.dp))
                    Text("Find the gateway on your tailnet.", style = HermesTypography.displayLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Scans MagicDNS names like hermes.<tailnet>.ts.net. Type a host if discovery misses it.",
                        style = HermesTypography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    HermesField(tailnet, { tailnet = it }, Modifier.fillMaxWidth(), placeholder = "tailnet.ts.net")
                    Spacer(Modifier.height(12.dp))
                    HermesField(extraHost, { extraHost = it }, Modifier.fillMaxWidth(), placeholder = "hermes or 100.x.y.z:8642")
                    Spacer(Modifier.height(12.dp))
                    HermesButton(
                        if (scanning) "Scanning tailnet…" else "Scan again",
                        onClick = { scan() },
                        filled = false,
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Column {
                            if (peers.isEmpty() && !scanning) {
                                Text(
                                    "No peers yet. Scan your tailnet.",
                                    style = HermesTypography.bodyMedium,
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                )
                            }
                            peers.forEach { p ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { selected = p.id }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.label, style = HermesTypography.bodyLarge.copy(fontSize = androidx.compose.ui.unit.sp(14)))
                                        Text(p.detail, style = HermesTypography.bodySmall)
                                    }
                                    if (selected == p.id) StatusBadge("selected", BadgeTone.Solid)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    HermesButton(
                        "Use this gateway",
                        onClick = { step = Step.Pair },
                        enabled = selected != null,
                        large = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Step.Pair -> {
                    Text("PAIR", style = HermesType.kicker)
                    Spacer(Modifier.height(12.dp))
                    Text("Show Hermes this code.", style = HermesTypography.displayLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "The plugin accepts the phone’s code. Optional COMPANION_PAIRING on the box locks it.",
                        style = HermesTypography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text("PAIRING CODE", style = HermesType.kickerSubtle)
                                    Spacer(Modifier.height(8.dp))
                                    Text(code, style = HermesType.code.copy(fontSize = androidx.compose.ui.unit.sp(28)))
                                }
                                IconButton(onClick = { code = NodePrefs.rotateCode(ctx) }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Rotate", tint = HermesColors.Muted)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "${peers.find { it.id == selected }?.label ?: "Gateway"} · outbound only",
                                style = HermesTypography.bodySmall,
                            )
                        }
                    }
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(error, style = HermesTypography.bodyMedium.copy(color = HermesColors.Danger))
                    }
                    Spacer(Modifier.height(20.dp))
                    HermesButton(
                        if (linking) "Linking gateway…" else "Connect this device",
                        onClick = {
                            val peer = peers.find { it.id == selected } ?: peers.firstOrNull()
                            if (peer == null) {
                                error = "No gateway selected"
                                return@HermesButton
                            }
                            linking = true
                            error = ""
                            NodePrefs.setGatewayUrl(ctx, peer.url)
                            scope.launch {
                                try {
                                    CompanionLink.pair(ctx, peer.url, code)
                                    onPaired()
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not pair"
                                } finally {
                                    linking = false
                                }
                            }
                        },
                        enabled = !linking,
                        large = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private suspend fun probePeers(tailnet: String, extra: String): List<Peer> = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build()
    val hosts = buildList {
        val extraTrim = extra.trim()
        if (extraTrim.isNotBlank()) add(normalize(extraTrim) to extraTrim)
        add("http://hermes:8642" to "hermes")
        val tn = tailnet.trim().removePrefix("https://").removePrefix("http://")
        if (tn.isNotBlank()) {
            val magic = if (tn.contains("hermes.")) "http://$tn:8642" else "http://hermes.$tn:8642"
            add(magic to magic.removePrefix("http://"))
        }
    }.distinctBy { it.first }
    hosts.mapNotNull { (url, label) ->
        val alive = runCatching {
            val req = Request.Builder().url("$url/companion/outbox").get().build()
            client.newCall(req).execute().use { it.code == 401 || it.isSuccessful }
        }.getOrDefault(false)
        if (alive) Peer(url, label, url, url) else null
    }
}

private fun normalize(raw: String): String {
    val t = raw.trim()
    if (t.startsWith("http://") || t.startsWith("https://")) return t.trimEnd('/')
    return if (t.contains(":")) "http://${t.trimEnd('/')}" else "http://$t:8642"
}
