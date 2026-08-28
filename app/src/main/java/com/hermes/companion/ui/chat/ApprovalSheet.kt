package com.hermes.companion.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ApprovalRequest
import com.hermes.companion.ui.components.HermesButton
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    request: ApprovalRequest,
    onDecision: (ApprovalOption) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onDecision(ApprovalOption.Deny) },
        sheetState = state,
        containerColor = HermesColors.Surface,
        contentColor = HermesColors.Fg,
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 28.dp)) {
            Text("NEEDS APPROVAL", style = HermesType.kicker.copy(color = HermesColors.Warn))
            Spacer(Modifier.height(8.dp))
            Text("Profile @${request.profileId} on ${request.gatewayId}", style = HermesTypography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(request.command, style = HermesTypography.bodyLarge.copy(color = HermesColors.Fg))
            Text("Digest: ${request.digest}", style = HermesTypography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HermesButton("Deny", onClick = { onDecision(ApprovalOption.Deny) }, filled = false, modifier = Modifier.weight(1f))
                HermesButton("Allow", onClick = { onDecision(ApprovalOption.Once) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HermesButton("This session", onClick = { onDecision(ApprovalOption.Session) }, filled = false, modifier = Modifier.weight(1f))
                HermesButton("Always", onClick = { onDecision(ApprovalOption.Always) }, filled = false, modifier = Modifier.weight(1f))
            }
        }
    }
}
