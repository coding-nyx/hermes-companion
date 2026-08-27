package com.hermes.companion.node.elevated

import android.content.Context
import com.hermes.companion.node.service.HermesAccessibilityService
import com.hermes.companion.node.service.HermesNotificationListenerService

/**
 * Uses the elevated shell to satisfy setup rungs without the user walking into
 * Settings — an opt-in convenience once an elevated route (Shizuku/root) exists.
 * Every command is allowlisted (pm/appops/settings/cmd/dumpsys) and passes
 * through ShellExecAdapter's allowlist too. Shizuku's uid is ADB (2000):
 * pm/appops/settings succeed; truly root-only writes need the Root route.
 */
class SilentGranter(private val context: Context) {
    private val pkg get() = context.packageName

    suspend fun grantRuntime(perm: String): ShellResult =
        ElevatedShell.run(listOf("pm", "grant", pkg, perm))

    suspend fun grantUsageStats(): ShellResult =
        ElevatedShell.run(listOf("appops", "set", pkg, "GET_USAGE_STATS", "allow"))

    suspend fun grantBatteryUnrestricted(): ShellResult =
        ElevatedShell.run(listOf("dumpsys", "deviceidle", "whitelist", "+$pkg"))

    suspend fun grantNotificationListener(): ShellResult =
        ElevatedShell.run(
            listOf(
                "cmd", "notification", "allow_listener",
                "$pkg/${HermesNotificationListenerService::class.java.name}",
            ),
        )

    suspend fun grantAccessibility(): ShellResult {
        val comp = "$pkg/${HermesAccessibilityService::class.java.name}"
        ElevatedShell.run(listOf("settings", "put", "secure", "enabled_accessibility_services", comp))
        return ElevatedShell.run(listOf("settings", "put", "secure", "accessibility_enabled", "1"))
    }
}
