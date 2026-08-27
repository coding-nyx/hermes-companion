package com.hermes.companion.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.companion.domain.ApprovalOption
import com.hermes.companion.domain.ApprovalRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    request: ApprovalRequest,
    onDecision: (ApprovalOption) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { onDecision(ApprovalOption.Deny) }, sheetState = state) {
        Column(Modifier.padding(20.dp)) {
            Text("Approval required", style = MaterialTheme.typography.titleLarge)
            Box(Modifier.size(8.dp))
            Text(
                "Profile @${request.profileId} on gateway ${request.gatewayId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Box(Modifier.size(12.dp))
            Text("Command", style = MaterialTheme.typography.labelLarge)
            Text(request.command, style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.size(4.dp))
            Text("Digest: ${request.digest}", style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.size(20.dp))
            // Only the choices Hermes offered for THIS request — never a hardcoded set.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                request.options.filter { it != ApprovalOption.Deny }.forEach { opt ->
                    Button(onClick = { onDecision(opt) }) { Text(opt.label) }
                }
                if (ApprovalOption.Deny in request.options) {
                    OutlinedButton(onClick = { onDecision(ApprovalOption.Deny) }) {
                        Text(ApprovalOption.Deny.label)
                    }
                }
            }
            Box(Modifier.size(12.dp))
        }
    }
}
