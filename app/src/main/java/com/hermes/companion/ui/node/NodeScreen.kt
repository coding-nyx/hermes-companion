package com.hermes.companion.ui.node

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hermes.companion.device.PhoneControl
import com.hermes.companion.node.CompanionA11yService
import com.hermes.companion.node.CompanionLink
import com.hermes.companion.node.Coverage
import com.hermes.companion.node.CoverageRow
import com.hermes.companion.node.CoverageStatus
import com.hermes.companion.node.NodePrefs
import com.hermes.companion.node.NodeTools
import com.hermes.companion.node.ScreenCapture
import com.hermes.companion.node.ScreenNode
import com.hermes.companion.node.UsageAccess
import com.hermes.companion.ui.components.HermesButton
import com.hermes.companion.ui.components.HermesField
import com.hermes.companion.ui.components.LevelRow
import com.hermes.companion.ui.components.SectionLabel
import com.hermes.companion.ui.components.SurfaceCard
import com.hermes.companion.ui.components.ToggleRow
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NodeScreen() {
    val ctx = LocalContext.current
    val phone by PhoneControl.state.collectAsState()
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(Coverage.snapshot(ctx)) }
    var tree by remember { mutableStateOf<List<ScreenNode>>(emptyList()) }
    var treeTitle by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf("") }
    var inputOn by remember { mutableStateOf(NodePrefs.screenInputAllowed(ctx)) }
    var clip by remember { mutableStateOf(phone.clipboard) }
    var dial by remember { mutableStateOf("") }

    fun refresh() {
        PhoneControl.refresh(ctx)
        rows = Coverage.snapshot(ctx)
        inputOn = NodePrefs.screenInputAllowed(ctx)
        val svc = CompanionA11yService.instance
        if (svc != null) {
            val dump = svc.dumpTree()
            treeTitle = "${dump.pkg} · ${dump.nodes.size} nodes"
            tree = dump.nodes.take(12)
        } else {
            treeTitle = UsageAccess.current(ctx)?.pkg ?: "accessibility off"
            tree = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            PhoneControl.refresh(ctx)
            delay(10_000)
        }
    }

    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        ScreenCapture.store(result.resultCode, result.data)
        refresh()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column {
            Text("Device", style = HermesTypography.displayMedium)
            Text("${phone.name} · ${phone.currentApp}", style = HermesTypography.bodyMedium)
        }

        Column {
            SectionLabel("Levels")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LevelRow("Brightness", phone.brightness) { v -> PhoneControl.patch(ctx) { it.copy(brightness = v) } }
                    LevelRow("Media volume", phone.volume) { v -> PhoneControl.patch(ctx) { it.copy(volume = v) } }
                    LevelRow("Ringer", phone.ringer) { v -> PhoneControl.patch(ctx) { it.copy(ringer = v) } }
                }
            }
        }

        Column {
            SectionLabel("Radios")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    ToggleRow("Wi-Fi", checked = phone.wifi, onCheckedChange = { v -> PhoneControl.patch(ctx) { it.copy(wifi = v, airplane = if (v) false else it.airplane) } })
                    ToggleRow("Bluetooth", checked = phone.bluetooth, onCheckedChange = { v -> PhoneControl.patch(ctx) { it.copy(bluetooth = v) } })
                    ToggleRow("Airplane", checked = phone.airplane, onCheckedChange = { v -> PhoneControl.patch(ctx) { it.copy(airplane = v, wifi = if (v) false else true, bluetooth = if (v) false else it.bluetooth) } })
                    ToggleRow("Do not disturb", checked = phone.dnd, onCheckedChange = { v -> PhoneControl.patch(ctx) { it.copy(dnd = v) } })
                    ToggleRow("Flashlight", checked = phone.flashlight, onCheckedChange = { v -> PhoneControl.patch(ctx) { it.copy(flashlight = v) } })
                    ToggleRow("Night light", checked = phone.nightLight, onCheckedChange = { v -> PhoneControl.patch(ctx) { it.copy(nightLight = v) } })
                    ToggleRow("Orientation lock", checked = phone.orientationLock, onCheckedChange = { v -> PhoneControl.patch(ctx) { it.copy(orientationLock = v) } })
                    ToggleRow("Location sharing", checked = phone.locationSharing, onCheckedChange = { v -> PhoneControl.patch(ctx) { it.copy(locationSharing = v) } }, showDivider = false)
                }
            }
        }

        Column {
            SectionLabel("Now playing")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(phone.mediaTitle, style = HermesTypography.bodyLarge.copy(color = HermesColors.Fg))
                    Text(phone.mediaArtist, style = HermesTypography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        IconButton(onClick = {}) { Icon(Icons.Outlined.SkipPrevious, null, tint = HermesColors.Muted) }
                        IconButton(onClick = { PhoneControl.patch(ctx) { it.copy(mediaPlaying = !it.mediaPlaying) } }) {
                            Icon(if (phone.mediaPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, tint = HermesColors.Fg)
                        }
                        IconButton(onClick = {}) { Icon(Icons.Outlined.SkipNext, null, tint = HermesColors.Muted) }
                    }
                }
            }
        }

        Column {
            SectionLabel("System keys")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("back", "home", "recents").forEach { key ->
                    HermesButton(key.replaceFirstChar { it.uppercase() }, onClick = {
                        lastResult = NodeTools.execute(ctx, "press_key", kotlinx.serialization.json.buildJsonObject {
                            put("key", kotlinx.serialization.json.JsonPrimitive(key))
                        })
                    }, filled = false, modifier = Modifier.weight(1f))
                }
            }
        }

        Column {
            SectionLabel("Clipboard")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HermesField(clip, { clip = it }, Modifier.weight(1f), placeholder = "Clipboard text")
                HermesButton("Set", onClick = { PhoneControl.patch(ctx) { it.copy(clipboard = clip) } }, filled = false)
            }
        }

        Column {
            SectionLabel("Call")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HermesField(dial, { dial = it }, Modifier.weight(1f), placeholder = "Name or number")
                HermesButton("Call", onClick = {
                    lastResult = "call $dial"
                }, enabled = dial.isNotBlank())
            }
        }

        Column {
            SectionLabel("Screen use")
            Text(
                "Reading the live UI tree, tapping, swiping, and typing need Accessibility. Hermes only drives input after you flip the grant.",
                style = HermesTypography.bodyMedium,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            rows.forEach { row ->
                CoverageCard(row) {
                    when (row.id) {
                        "capture" -> captureLauncher.launch(ScreenCapture.requestIntent(ctx))
                        "input" -> {
                            NodePrefs.setScreenInputAllowed(ctx, !inputOn)
                            refresh()
                        }
                        else -> Coverage.open(ctx, row)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow Hermes to use the screen", style = HermesTypography.titleMedium)
                        Text("Taps, swipes, and typing. Reading still works when this is off.", style = HermesTypography.bodySmall)
                    }
                    Switch(
                        checked = inputOn,
                        onCheckedChange = {
                            NodePrefs.setScreenInputAllowed(ctx, it)
                            refresh()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = HermesColors.OnPrimary,
                            checkedTrackColor = HermesColors.Primary,
                            uncheckedThumbColor = HermesColors.Muted,
                            uncheckedTrackColor = HermesColors.Elevated,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Screen now", style = HermesTypography.titleMedium)
                    Text(treeTitle.ifBlank { "Enable accessibility, then resume this page." }, style = HermesTypography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HermesButton("Read", onClick = { lastResult = NodeTools.execute(ctx, "read_screen"); refresh() }, filled = false)
                        HermesButton("Usage", onClick = { lastResult = NodeTools.execute(ctx, "app_usage"); refresh() }, filled = false)
                        HermesButton("Shot", onClick = { lastResult = NodeTools.execute(ctx, "capture_screenshot"); refresh() }, filled = false)
                    }
                    if (lastResult.isNotBlank()) {
                        Text(lastResult.take(400), style = HermesTypography.bodySmall.copy(color = HermesColors.Fg))
                    }
                    tree.take(8).forEach { node ->
                        Text(
                            "${if (node.clickable) "▸ " else ""}${node.label.ifBlank { node.cls }}",
                            style = HermesType.mono.copy(color = HermesColors.Muted),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverageCard(row: CoverageRow, onGrant: () -> Unit) {
    val tone = when (row.status) {
        CoverageStatus.Working -> HermesColors.Ok
        CoverageStatus.Limited -> HermesColors.Warn
        CoverageStatus.Missing -> HermesColors.Danger
        CoverageStatus.Off -> HermesColors.Subtle
    }
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.label, style = HermesTypography.titleMedium)
                Text(row.detail, style = HermesTypography.bodySmall)
                Text(row.status.name.lowercase(), style = HermesType.kicker.copy(color = tone))
            }
            if (row.status != CoverageStatus.Working || row.id == "input" || row.id == "capture") {
                HermesButton(
                    if (row.id == "input") "Toggle" else "Grant",
                    onClick = onGrant,
                    filled = false,
                )
            }
        }
    }
}
