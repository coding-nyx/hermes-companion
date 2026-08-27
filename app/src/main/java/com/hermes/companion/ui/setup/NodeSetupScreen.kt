package com.hermes.companion.ui.setup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.data.repo.SetupRung
import com.hermes.companion.domain.RequirementKind
import com.hermes.companion.ui.components.HermesCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeSetupScreen(
    onBack: () -> Unit,
    vm: SetupViewModel = hiltViewModel(),
) {
    val rungs by vm.rungs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* coverage refreshes on the repo ticker */ }

    fun openSettings(action: String, withData: Boolean = false) = runCatching {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (withData) intent.data = Uri.parse("package:" + context.packageName)
        context.startActivity(intent)
    }

    fun act(rung: SetupRung) = when (rung.kind) {
        RequirementKind.RuntimePermission -> {
            val perms = if (rung.target.endsWith("ACCESS_FINE_LOCATION")) {
                arrayOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")
            } else arrayOf(rung.target)
            permLauncher.launch(perms)
        }
        RequirementKind.NotificationListener ->
            openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).let {}
        RequirementKind.AccessibilityService ->
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS).let {}
        RequirementKind.SystemSetting -> when (rung.target) {
            "usage-access" -> openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS).let {}
            "battery-unrestricted" -> openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, withData = true).let {}
            else -> openSettings(Settings.ACTION_SETTINGS).let {}
        }
        else -> openSettings(Settings.ACTION_SETTINGS).let {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Full Node Mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${rungs.count { it.satisfied }} of ${rungs.size} rungs granted",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Setup is a trust ladder, not a wizard to rush. Each rung adds exactly one power. " +
                    "Nothing streams off this phone until a capability is granted to a named profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("TRUST TIER", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Standard", "Accessibility", "Shizuku / ADB").forEach {
                    AssistChip(onClick = {}, label = { Text(it) })
                }
            }

            Text("READINESS", style = MaterialTheme.typography.labelMedium)
            rungs.forEach { rung -> RungCard(rung) { act(rung) } }
        }
    }
}

@Composable
private fun RungCard(rung: SetupRung, onGrant: () -> Unit) {
    HermesCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    rung.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    rung.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rung.enablesCount > 0) {
                    Text(
                        "Unlocks ${rung.enablesCount} capabilit${if (rung.enablesCount == 1) "y" else "ies"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (rung.satisfied) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                } else {
                    Button(onClick = onGrant) { Text("Grant") }
                }
            }
        }
    }
}
