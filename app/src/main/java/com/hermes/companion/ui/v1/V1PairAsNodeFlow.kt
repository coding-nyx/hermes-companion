package com.hermes.companion.ui.v1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.ui.theme.DisplayMedium
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.PlexMono
import com.hermes.companion.ui.theme.StatusOk
import com.hermes.companion.ui.theme.StatusWarn

/**
 * PairAsNodeFlow — SettingsSheet → Pair as Node.
 *
 * Two-mode flow: Discover nearby / Manual entry. Discover is the default
 * because it relies on the existing CompanionDiscovery Bonjour + MagicDNS +
 * LAN sweep. Manual replaces the QR-scan step for headless / SSH-tunnel /
 * cloud cases where mDNS won't reach.
 *
 * Phase A wires the visual surface only — the actual discovery backend
 * is unchanged. The "Pair" button here is a stub that fires
 * [onPaired]; the v0.2 pair flow stays responsible for the wire.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun V1PairAsNodeFlow(
    onDismiss: () -> Unit,
    onPaired: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(PairMode.Discover) }
    var scanning by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pair as a node", style = DisplayMedium)
                    Text(
                        "on gw-home",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "1/2",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PlexMono,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Segmented control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            ) {
                SegmentedButton(
                    label = "Discover nearby",
                    selected = mode == PairMode.Discover,
                    onClick = { mode = PairMode.Discover },
                    modifier = Modifier.weight(1f),
                )
                SegmentedButton(
                    label = "Manual entry",
                    selected = mode == PairMode.Manual,
                    onClick = { mode = PairMode.Manual },
                    modifier = Modifier.weight(1f),
                )
            }

            // Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (mode) {
                    PairMode.Discover -> DiscoverBody(scanning = scanning, onToggleScan = { scanning = !scanning })
                    PairMode.Manual -> ManualBody(onPair = onPaired)
                }
            }
        }
    }
}

private enum class PairMode { Discover, Manual }

@Composable
private fun SegmentedButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) HermesColors.Primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            ),
            color = if (selected) HermesColors.OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiscoverBody(scanning: Boolean, onToggleScan: () -> Unit) {
    // Scan status row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (scanning) HermesColors.Primary else StatusOk),
        )
        Text(
            if (scanning) "Looking on this Wi-Fi and your tailnet…"
            else "3 gateways answered",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                .clickable(onClick = onToggleScan)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                if (scanning) "Stop" else "Scan again",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Candidate cards
    if (scanning) {
        repeat(2) { SkeletonCandidateCard() }
    } else {
        CandidateCard("mac-studio", "mac-studio.local:7800", "wss", "This Wi-Fi, over mDNS · _hermes._tcp.local", isBest = true)
        CandidateCard("studio-cloud", "mac-studio.tail9f2c.ts.net:7800", "wss", "Your tailnet, over wide-area DNS-SD")
        CandidateCard("pi-shed", "pi-shed.local:7800", "ws · limited", "This Wi-Fi, cleartext only — chat, never a node", isWarn = true)
    }

    // Where it looks
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "WHERE IT LOOKS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WhereRow("mDNS", "Same Wi-Fi, no configuration")
        WhereRow("DNS-SD", "Across your tailnet, wherever the phone is")
        WhereRow("manual", "Anything reachable — SSH tunnels, Cloud, odd ports")
    }

    // Trust note
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(56.dp)
                .background(StatusWarn),
        )
        Text(
            "Discovery only proves something answered on that address. Nothing is trusted until you check its fingerprint on the next screen.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WhereRow(kind: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            kind,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PlexMono,
                fontSize = 11.sp,
            ),
            color = HermesColors.Primary,
            modifier = Modifier.width(74.dp),
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
@Composable
private fun CandidateCard(
    name: String,
    host: String,
    transport: String,
    hint: String,
    isBest: Boolean = false,
    isWarn: Boolean = false,
) {
    val borderColor = if (isBest) StatusOk.copy(alpha = 0.40f) else MaterialTheme.colorScheme.outlineVariant
    val rowShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, borderColor, rowShape)
            .clickable { /* TODO: pick candidate */ }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isWarn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Badge(text = transport.uppercase(), tone = if (isWarn) StatusWarn else StatusOk)
        }
        Text(
            host,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PlexMono,
                fontSize = 12.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
        )
    }
}

@Composable
private fun SkeletonCandidateCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(width = 100.dp, height = 12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
        )
        Box(
            Modifier
                .size(width = 200.dp, height = 10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        )
        Text(
            "scanning…",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ManualBody(onPair: () -> Unit) {
    // Gateway URL
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "GATEWAY URL",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, HermesColors.Primary, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                "wss://mac-studio.local:7800",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = PlexMono,
                    fontSize = 14.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "wss:// for a trusted network node; ws:// is chat-only.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    // Setup code
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "SETUP CODE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                "amber-lattice-9042",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = PlexMono,
                    fontSize = 14.sp,
                    letterSpacing = 1.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable { /* TODO: paste from clipboard */ }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "Paste",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Single-use · expires in 4 minutes · generated on the gateway.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    // Trust info panel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StatusOk.copy(alpha = 0.08f))
            .border(1.dp, StatusOk.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "What gets exchanged",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            "Ed25519 device key (created inside Android Keystore), the setup code, and the gateway's server fingerprint. The bearer token never travels in this exchange.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Pair CTA
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(HermesColors.Primary)
            .clickable(onClick = onPair)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Pair this phone",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
            color = HermesColors.OnPrimary,
        )
    }
}

@Composable
private fun Badge(text: String, tone: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PlexMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            ),
            color = tone,
        )
    }
}
