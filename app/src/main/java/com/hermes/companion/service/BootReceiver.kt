package com.hermes.companion.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the connection service after boot / app update so the phone acts as a
 * real node without the user tap-launching the app. The service's
 * foregroundServiceType (connectedDevice) is in the set Android allows to start
 * from BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> runCatching { CompanionConnectionService.start(context) }
        }
    }
}
