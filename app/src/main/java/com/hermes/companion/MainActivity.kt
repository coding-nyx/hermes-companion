package com.hermes.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.hermes.companion.service.CompanionConnectionService
import com.hermes.companion.ui.shell.Shell
import com.hermes.companion.ui.theme.HermesTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Started from a visible Activity: Android 12+ refuses a background
        // foreground-service start.
        CompanionConnectionService.start(this)
        enableEdgeToEdge()
        setContent {
            HermesTheme {
                val size = calculateWindowSizeClass(this)
                Shell(size = size)
            }
        }
    }
}
