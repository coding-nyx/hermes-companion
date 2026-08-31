package com.hermes.companion.ui.v1

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import com.hermes.companion.ui.theme.PlexMono
import kotlinx.coroutines.launch

/**
 * Phase B · spec 4 — long-press action sheet for a message bubble.
 *
 * Five actions on user bubbles (decision #8 — edit/regenerate is
 * user-only):
 *   - Edit & rerun        (primary)
 *   - Copy
 *   - Branch from here
 *   - Regenerate reply
 *   - ─── separator ───
 *   - Delete              (destructive, Coral)
 *
 * Rendered as a `ModalBottomSheet` with a 220-dp anchor popover
 * pointing to the bubble's top-right corner. We don't ship the
 * anchored popover overlay yet — Phase A shell will wire the
 * long-press → open sheet hook; the sheet itself is the
 * standalone contract here.
 */
sealed class V1BMessageAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
) {
    object EditRerun : V1BMessageAction("Edit & rerun", Icons.Filled.Edit)
    object Copy : V1BMessageAction("Copy", Icons.Filled.ContentCopy)
    object Branch : V1BMessageAction("Branch from here", Icons.Filled.CallSplit)
    object Regenerate : V1BMessageAction("Regenerate reply", Icons.Filled.Refresh)
    object Delete : V1BMessageAction("Delete", Icons.Filled.Delete, destructive = true)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun V1BMessageActionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAction: (V1BMessageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Surface,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                "MESSAGE ACTIONS",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    color = HermesColors.Muted,
                    letterSpacing = 1.2.sp,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            listOf(
                V1BMessageAction.EditRerun,
                V1BMessageAction.Copy,
                V1BMessageAction.Branch,
                V1BMessageAction.Regenerate,
            ).forEach { action ->
                V1BMessageActionRow(action) {
                    scope.launch {
                        sheetState.hide()
                        onAction(action)
                        onDismiss()
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HermesColors.Border)
                    .padding(horizontal = 8.dp),
            )
            V1BMessageActionRow(V1BMessageAction.Delete) {
                scope.launch {
                    sheetState.hide()
                    onAction(V1BMessageAction.Delete)
                    onDismiss()
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun V1BMessageActionRow(
    action: V1BMessageAction,
    onClick: () -> Unit,
) {
    val tint = if (action.destructive) Coral80 else HermesColors.Fg
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            action.label,
            style = TextStyle(fontSize = 15.sp, color = tint, fontWeight = FontWeight.Normal),
            modifier = Modifier.weight(1f),
        )
        if (action is V1BMessageAction.EditRerun) {
            Text(
                "⏎",
                style = TextStyle(fontFamily = PlexMono, fontSize = 12.sp, color = HermesColors.Muted),
            )
        }
    }
}

/**
 * Anchored popover preview used by the HTML mock (220-dp wide,
 * right-aligned above the bubble). Implemented as a Box overlay
 * rather than a ModalBottomSheet so the bubble stays visible
 * underneath the scrim. Caller positions this at the bubble's
 * top-right.
 */
@Composable
fun V1BMessageActionPopover(
    onAction: (V1BMessageAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var anchorVisible by remember { mutableStateOf(true) }
    Box(modifier) {
        // Scrim (covers the full parent Box)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
        )
        // Popover card
        Column(
            Modifier
                .width(220.dp)
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(HermesColors.Surface)
                .border(1.dp, HermesColors.Border, RoundedCornerShape(14.dp))
                .padding(6.dp),
        ) {
            Text(
                "MESSAGE ACTIONS",
                style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = 10.sp,
                    color = HermesColors.Muted,
                    letterSpacing = 1.2.sp,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            listOf(
                V1BMessageAction.EditRerun,
                V1BMessageAction.Copy,
                V1BMessageAction.Branch,
                V1BMessageAction.Regenerate,
            ).forEach { action ->
                V1BMessageActionRow(action) { onAction(action) }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(HermesColors.Border)
                    .padding(horizontal = 8.dp),
            )
            V1BMessageActionRow(V1BMessageAction.Delete) { onAction(V1BMessageAction.Delete) }
        }
        // Anchor triangle (decorative)
        if (anchorVisible) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 28.dp, top = 6.dp)
                    .size(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HermesColors.Surface)
                    .border(1.dp, HermesColors.Border, RoundedCornerShape(2.dp)),
            )
        }
    }
}


