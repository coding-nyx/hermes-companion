package com.hermes.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.hermes.companion.common.BiometricGate
import com.hermes.companion.settings.ThemeMode
import com.hermes.companion.settings.ThemePrefs
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermes.companion.service.CompanionConnectionService
import com.hermes.companion.ui.shell.Shell
import com.hermes.companion.ui.theme.HermesTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var gate: BiometricGate
    @Inject lateinit var themePrefs: ThemePrefs

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotifications()
        CompanionConnectionService.start(this)
        enableEdgeToEdge()
        setContent {
            val mode by themePrefs.mode.collectAsStateWithLifecycle(ThemeMode.System)
            val dynamic by themePrefs.dynamic.collectAsStateWithLifecycle(false)
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            HermesTheme(darkTheme = dark, dynamicColor = dynamic) {
                var unlocked by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                if (unlocked) {
                    val size = calculateWindowSizeClass(this)
                    Shell(size = size)
                } else {
                    LockScreen(onUnlock = { scope.launch { unlocked = gate.require(BiometricGate.Gate.APP_LAUNCH) } })
                    // Auto-prompt once on entry; AllowAllGate returns immediately.
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        unlocked = gate.require(BiometricGate.Gate.APP_LAUNCH)
                    }
                }
            }
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@androidx.compose.runtime.Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.hermes.companion.ui.components.HermesMark(48.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(10.dp))
            Text("Hermes", style = MaterialTheme.typography.titleLarge)
            Text(
                "COMPANION",
                style = com.hermes.companion.ui.theme.HermesType.kicker,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}
