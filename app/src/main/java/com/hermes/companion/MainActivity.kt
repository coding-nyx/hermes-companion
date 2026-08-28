package com.hermes.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hermes.companion.device.PhoneControl
import com.hermes.companion.node.NodePrefs
import com.hermes.companion.ui.pairing.PairingScreen
import com.hermes.companion.ui.shell.Shell
import com.hermes.companion.ui.theme.HermesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PhoneControl.refresh(this)
        val activity = this
        setContent {
            HermesTheme {
                var paired by remember { mutableStateOf(!NodePrefs.session(activity).isNullOrBlank()) }
                if (!paired) {
                    PairingScreen(onPaired = { paired = true })
                } else {
                    Shell(onUnpair = { paired = false })
                }
            }
        }
    }
}
