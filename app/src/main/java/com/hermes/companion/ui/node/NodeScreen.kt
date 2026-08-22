package com.hermes.companion.ui.node

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NodeScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Node", style = MaterialTheme.typography.titleLarge)
        Box(Modifier.padding(top = 8.dp))
        Text(
            "Pairing, capability grants, and the coverage matrix will live here. " +
                "See plan/03-android/full-node-mode.md for the setup checklist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}
