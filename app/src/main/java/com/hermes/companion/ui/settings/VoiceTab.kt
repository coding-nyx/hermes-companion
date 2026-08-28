package com.hermes.companion.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.common.VoiceConfig
import com.hermes.companion.ui.components.HermesField
import com.hermes.companion.ui.components.SectionLabel
import com.hermes.companion.ui.components.SurfaceCard
import com.hermes.companion.ui.components.ToggleRow
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesTypography

/**
 * Phase 1 Voice tab.
 *
 * Renders the persisted [VoiceConfig.VoiceSnapshot] and writes every change
 * back through [vm.setVoiceConfig]. Phase 1 keeps wake-word and STT engine
 * picker inert (Phase 2 brings the wake-word model + Whisper) — they show
 * the current value but ignore taps.
 *
 * Visual conformance: SurfaceCard sections + SectionLabel headings +
 * ToggleRow + HermesField, dark warm palette + Figtree font via the
 * master's HermesComponents.
 */
@Composable
fun VoiceTab(
    snap: VoiceConfig.VoiceSnapshot,
    onChange: (VoiceConfig.VoiceSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Voice", style = MaterialTheme.typography.titleLarge)
        Text(
            "STT + TTS engines, voice override, and push-to-talk hotkey. Phase 1 keeps wake-word and STT engine picker inert.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        // Wake-word section — Phase 1 disabled.
        SurfaceCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Wake-word")
                ToggleRow(
                    label = "Listen for wake-word",
                    detail = "Coming soon — Phase 2 wires the wake-word model",
                    checked = snap.wakeEnabled,
                    onCheckedChange = { /* disabled in Phase 1 */ },
                )
                HermesField(
                    value = snap.wakePhrase,
                    onValueChange = { /* disabled in Phase 1 */ },
                    placeholder = "hey hermes",
                    modifier = Modifier.semantics { contentDescription = "Wake phrase (disabled)" },
                )
                Text(
                    "Wake-word is on the roadmap but not yet wired — the toggle and phrase are shown read-only so you can see what Phase 2 will expose.",
                    style = HermesTypography.bodySmall,
                    color = HermesColors.Subtle,
                )
            }
        }

        // STT engine — Phase 1 locked to Android.
        SurfaceCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Speech-to-text engine")
                DisabledPicker(label = "Android (default)", detail = "Phase 1 only — Whisper + cloud STT arrive with Phase 2")
                Text(
                    "Uses Android's on-device SpeechRecognizer. No audio leaves the device for STT in Phase 1.",
                    style = HermesTypography.bodySmall,
                    color = HermesColors.Subtle,
                )
            }
        }

        // TTS engine — Phase 1 allows android / none.
        SurfaceCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Text-to-speech engine")
                EnginePicker(
                    current = snap.ttsEngine,
                    options = TTS_ENGINE_OPTIONS,
                    labelFor = { engineLabel(it) },
                    enabled = true,
                    onSelected = { engine ->
                        if (engine != snap.ttsEngine) {
                            onChange(snap.copy(ttsEngine = engine))
                        }
                    },
                )
                HermesField(
                    value = snap.ttsVoice,
                    onValueChange = { newVoice ->
                        if (newVoice != snap.ttsVoice) onChange(snap.copy(ttsVoice = newVoice))
                    },
                    placeholder = "Voice id (empty = system default)",
                    modifier = Modifier.semantics { contentDescription = "TTS voice override" },
                )
                Text(
                    "Voice id is engine-specific (e.g. \"en-US-Wavenet-A\" for Google TTS). Leave empty for the engine's default.",
                    style = HermesTypography.bodySmall,
                    color = HermesColors.Subtle,
                )
            }
        }

        // Push-to-talk hotkey.
        SurfaceCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Push-to-talk hotkey")
                EnginePicker(
                    current = snap.pttHotkey,
                    options = PTT_HOTKEY_OPTIONS,
                    labelFor = { hotkeyLabel(it) },
                    enabled = true,
                    onSelected = { hk ->
                        if (hk != snap.pttHotkey) onChange(snap.copy(pttHotkey = hk))
                    },
                )
                Text(
                    "The hardware key that starts a voice turn. \"none\" disables PTT; \"headset\" listens for the headset media button; \"bt_button\" accepts any connected Bluetooth button.",
                    style = HermesTypography.bodySmall,
                    color = HermesColors.Subtle,
                )
            }
        }

        Box(Modifier.size(8.dp))
    }
}

/** Phase 1 STT options: Android only (the picker is disabled, but the constant is here for Phase 2). */
private val STT_ENGINE_OPTIONS = listOf("android")

/** Phase 1 TTS options: on-device Android or none. Cloud engines ship in Phase 2. */
private val TTS_ENGINE_OPTIONS = listOf("android", "none")

private val PTT_HOTKEY_OPTIONS = listOf("none", "headset", "bt_button")

private fun engineLabel(engine: String): String = when (engine) {
    "android" -> "Android (on-device)"
    "none" -> "None (silent)"
    "minimax" -> "Hermes MiniMax"
    "grok" -> "Hermes Grok"
    "elevenlabs" -> "ElevenLabs"
    "whisper" -> "Whisper"
    else -> engine
}

private fun hotkeyLabel(hotkey: String): String = when (hotkey) {
    "none" -> "None (disabled)"
    "headset" -> "Headset media button"
    "bt_button" -> "Bluetooth button"
    else -> hotkey
}

/**
 * Compact picker: an [AssistChip]-styled button that opens a [DropdownMenu].
 * Same affordance as the Routing tab dropdowns, but styled against the
 * warm-dark palette instead of M3 defaults.
 */
@Composable
private fun EnginePicker(
    current: String,
    options: List<String>,
    labelFor: (String) -> String,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val chipColors = AssistChipDefaults.assistChipColors(
        containerColor = HermesColors.Surface,
        labelColor = HermesColors.Fg,
    )
    Box {
        AssistChip(
            onClick = { if (enabled) expanded = true },
            label = {
                Text(
                    labelFor(current),
                    style = HermesTypography.bodyLarge.copy(fontSize = 14.sp, color = HermesColors.Fg),
                )
            },
            enabled = enabled,
            colors = chipColors,
            modifier = Modifier.semantics {
                contentDescription = if (enabled) "Engine picker: ${labelFor(current)}"
                else "Engine picker disabled"
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            labelFor(opt),
                            style = HermesTypography.bodyMedium,
                            color = if (opt == current) HermesColors.Primary else HermesColors.Fg,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(opt)
                    },
                    modifier = Modifier.semantics { contentDescription = "Pick $opt" },
                )
            }
        }
    }
}

/**
 * Phase 1 inert picker — shows the locked value but ignores taps. Phase 2
 * will convert these into [EnginePicker] calls when STT + wake-word go live.
 */
@Composable
private fun DisabledPicker(label: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = false, onClick = {})
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = HermesTypography.bodyLarge.copy(fontSize = 14.sp, color = HermesColors.Fg.copy(alpha = 0.55f)))
            Text(detail, style = HermesTypography.bodySmall)
        }
    }
}
