package com.hermes.companion.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.companion.domain.ToolRun
import com.hermes.companion.domain.ToolStatus
import com.hermes.companion.ui.components.SurfaceCard
import com.hermes.companion.ui.theme.HermesColors
import com.hermes.companion.ui.theme.HermesType
import com.hermes.companion.ui.theme.HermesTypography

@Composable
fun ToolRunCard(run: ToolRun) {
    val tone = when (run.status) {
        ToolStatus.Failed -> HermesColors.Danger
        ToolStatus.Completed -> HermesColors.Ok
        ToolStatus.Pending -> HermesColors.Warn
        ToolStatus.Running -> HermesColors.Muted
    }
    SurfaceCard(Modifier.padding(top = 8.dp), radius = 8.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "${run.name} · ${run.status.name.lowercase().replace('_', ' ')}",
                style = HermesType.kicker.copy(color = tone),
            )
            run.output?.let {
                Text(it, style = HermesTypography.bodySmall, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
