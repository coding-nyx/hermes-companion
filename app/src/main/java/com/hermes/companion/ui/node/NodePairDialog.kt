package com.hermes.companion.ui.node

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.node.CompanionDiscovery
import com.hermes.companion.ui.components.HermesButton
import com.hermes.companion.ui.components.HermesField
import com.hermes.companion.ui.components.SurfaceCard
import kotlinx.coroutines.launch

/**
 * Compose dialog for "Pair this phone as a node".
 *
 * Two modes (toggle in the header):
 *  - Auto-discover (default): runs [CompanionDiscovery] and shows nearby
 *    gateways. Picking one fills the manual URL field below — the user
 *    still has to paste the setup code (it isn't shipped over the wire).
 *  - Manual: paste the gateway URL and the setup code (legacy / power-user
 *    path; unchanged from the previous build).
 *
 * @param onPair(url, code, onResult) — the VM handles the actual HTTP pair
 *   request and signals back via [onResult]: null = success, non-null = error.
 */
@Composable
fun NodePairDialog(
    onDismiss: () -> Unit,
    onPair: (url: String, code: String, onResult: (String?) -> Unit) -> Unit,
) {
    var mode by remember { mutableStateOf(Mode.Discover) }
    // Shared between the two sections so a candidate tap on Discover fills
    // the Manual URL field on the same dialog.
    var manualUrl by remember { mutableStateOf("") }
    var manualCode by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }
    var manualInFlight by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Pair this phone as a node", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                ModeToggle(mode, onChange = { mode = it })
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (mode) {
                    Mode.Discover -> DiscoverSection(
                        onUseCandidate = { url ->
                            manualUrl = url
                            manualError = null
                            mode = Mode.Manual
                        },
                    )
                    Mode.Manual -> ManualSection(
                        url = manualUrl,
                        onUrlChange = { manualUrl = it },
                        code = manualCode,
                        onCodeChange = { manualCode = it },
                        error = manualError,
                        inFlight = manualInFlight,
                        onPair = {
                            if (manualInFlight || manualUrl.isBlank() || manualCode.isBlank()) return@ManualSection
                            manualInFlight = true
                            manualError = null
                            onPair(manualUrl.trim(), manualCode.trim()) { err ->
                                manualInFlight = false
                                if (err == null) onDismiss() else manualError = err
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun ModeToggle(mode: Mode, onChange: (Mode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Mode.entries.forEach { m ->
            val active = mode == m
            val bg = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface
            val fg = if (active) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(bg)
                    .clickable { onChange(m) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    m.label,
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private enum class Mode(val label: String) {
    Discover("Discover nearby"),
    Manual("Manual"),
}

@Composable
private fun DiscoverSection(
    onUseCandidate: (url: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val candidates = remember { mutableStateListOf<CompanionDiscovery.Candidate>() }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listener = remember {
        object : CompanionDiscovery.Listener {
            override fun onCandidate(c: CompanionDiscovery.Candidate) { candidates += c }
            override fun onDone() { scanning = false }
            override fun onError(message: String) { error = message }
        }
    }

    fun startScan() {
        candidates.clear()
        error = null
        scanning = true
        scope.launch {
            try {
                CompanionDiscovery(context.applicationContext).discover(listener)
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
                scanning = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HermesButton(
            label = if (scanning) "Scanning…" else "Discover nearby",
            onClick = { if (!scanning) startScan() },
            enabled = !scanning,
        )
        if (scanning && candidates.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    "Looking on Bonjour, MagicDNS and the local LAN…",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        candidates.forEach { c ->
            CandidateRow(c, onClick = { onUseCandidate(c.url) })
        }
        if (!scanning && candidates.isEmpty() && error == null) {
            Text(
                "Tap Discover to scan. The list fills as gateways answer on Bonjour, " +
                    "Tailscale MagicDNS, or the local network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun CandidateRow(c: CompanionDiscovery.Candidate, onClick: () -> Unit) {
    SurfaceCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(sourceColor(c.source)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    c.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    c.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                c.magicDns?.let { dns ->
                    Text(
                        dns,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                c.source.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = sourceColor(c.source),
            )
        }
    }
}

@Composable
private fun sourceColor(source: CompanionDiscovery.Source) = when (source) {
    CompanionDiscovery.Source.Bonjour -> MaterialTheme.colorScheme.secondary
    CompanionDiscovery.Source.MagicDns -> MaterialTheme.colorScheme.primary
    CompanionDiscovery.Source.Lan -> MaterialTheme.colorScheme.onSurface
    CompanionDiscovery.Source.Direct -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun ManualSection(
    url: String,
    onUrlChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    error: String?,
    inFlight: Boolean,
    onPair: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Plugin base URL + setup code (from the gateway).",
            style = MaterialTheme.typography.bodySmall,
        )
        HermesField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = "http://host:9120",
        )
        HermesField(
            value = code,
            onValueChange = onCodeChange,
            placeholder = "Setup code",
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        HermesButton(
            label = if (inFlight) "Pairing…" else "Pair",
            onClick = onPair,
            enabled = url.isNotBlank() && code.isNotBlank() && !inFlight,
        )
    }
}