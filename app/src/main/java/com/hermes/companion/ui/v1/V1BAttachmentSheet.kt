package com.hermes.companion.ui.v1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.companion.ui.theme.Coral80
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.Indigo80
import com.hermes.companion.ui.theme.PlexMono
import com.hermes.companion.ui.theme.Sand80
import com.hermes.companion.ui.theme.Teal80
import kotlinx.coroutines.launch

/**
 * Phase B · spec 6 — "+" attachment sheet.
 *
 * 4-up grid: File / Photo / Camera / Location. Tiles render in the
 * accent colors from the mock:
 *   File    → Indigo
 *   Photo   → Teal
 *   Camera  → Sand
 *   Location→ Coral
 *
 * When a permission-gated tile (Camera, Location) is tapped without
 * permission the caller dims the tile to 35% opacity. The current
 * composable just receives a [granted] flag per tile.
 */
enum class V1BAttachmentKind { File, Photo, Camera, Location }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun V1BAttachmentSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPick: (V1BAttachmentKind) -> Unit,
    fileGranted: Boolean = true,
    photoGranted: Boolean = true,
    cameraGranted: Boolean = true,
    locationGranted: Boolean = true,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ATTACH",
                    style = TextStyle(
                        fontFamily = PlexMono,
                        fontSize = 10.sp,
                        color = HermesColors.Muted,
                        letterSpacing = 1.2.sp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.dp, HermesColors.Border, CircleShape)
                        .clickable {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = HermesColors.Fg, modifier = Modifier.size(14.dp))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 18.dp),
            ) {
                AttachmentTile(V1BAttachmentKind.File, granted = fileGranted) {
                    scope.launch { sheetState.hide(); onPick(V1BAttachmentKind.File); onDismiss() }
                }
                AttachmentTile(V1BAttachmentKind.Photo, granted = photoGranted) {
                    scope.launch { sheetState.hide(); onPick(V1BAttachmentKind.Photo); onDismiss() }
                }
                AttachmentTile(V1BAttachmentKind.Camera, granted = cameraGranted) {
                    scope.launch { sheetState.hide(); onPick(V1BAttachmentKind.Camera); onDismiss() }
                }
                AttachmentTile(V1BAttachmentKind.Location, granted = locationGranted) {
                    scope.launch { sheetState.hide(); onPick(V1BAttachmentKind.Location); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun AttachmentTile(
    kind: V1BAttachmentKind,
    granted: Boolean,
    onClick: () -> Unit,
) {
    val accent = when (kind) {
        V1BAttachmentKind.File -> Indigo80
        V1BAttachmentKind.Photo -> Teal80
        V1BAttachmentKind.Camera -> Sand80
        V1BAttachmentKind.Location -> Coral80
    }
    val alpha = if (granted) 1f else 0.35f
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(HermesColors.Surface)
            .border(1.dp, HermesColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp)
            .size(width = 78.dp, height = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Per-tile glyph (rendered as the colored Text emoji-style
            // SVG equivalent — Compose path on a Canvas).
            TileGlyph(kind = kind, color = accent.copy(alpha = alpha), modifier = Modifier.size(22.dp))
        }
        Text(
            kind.name,
            style = TextStyle(fontSize = 11.sp, color = HermesColors.Fg.copy(alpha = alpha)),
        )
    }
}

@Composable
private fun TileGlyph(kind: V1BAttachmentKind, color: Color, modifier: Modifier = Modifier) {
    // Simple emoji-style glyphs without material-icons-extended
    // imports. Avoids extra dep weight.
    val glyph = when (kind) {
        V1BAttachmentKind.File -> "📄"  // Material has icons for these but
        V1BAttachmentKind.Photo -> "🖼" // we keep ASCII-friendly here.
        V1BAttachmentKind.Camera -> "📷"
        V1BAttachmentKind.Location -> "📍"
    }
    Text(
        glyph,
        style = TextStyle(fontSize = 22.sp, color = color, fontWeight = FontWeight.Normal),
        modifier = modifier,
    )
}
