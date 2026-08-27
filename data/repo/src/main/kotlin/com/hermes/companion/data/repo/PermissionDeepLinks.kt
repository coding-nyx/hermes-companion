package com.hermes.companion.data.repo

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.hermes.companion.domain.AndroidRequirement
import com.hermes.companion.domain.RequirementKind

/**
 * Map an [AndroidRequirement] to an [Intent] that opens the right Android
 * settings page (or for runtime permissions, is null — the caller should use
 * [androidx.activity.result.contract.ActivityResultContracts.RequestPermission]).
 *
 * Lives in `:data:repo` so `:app` can import it without a direct `:node`
 * dependency — :app talks only to :data:repo by design (see README §Modules).
 */
fun deepLinkFor(requirement: AndroidRequirement): Intent? = when (requirement.kind) {
    RequirementKind.RuntimePermission -> null // request via system runtime perm dialog
    RequirementKind.NotificationListener ->
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    RequirementKind.AccessibilityService ->
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    RequirementKind.SystemSetting -> when {
        requirement.detail.contains("usage access", ignoreCase = true) ->
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        requirement.detail.contains("role", ignoreCase = true) ->
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else -> null
    }
    RequirementKind.AppRole ->
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    RequirementKind.MediaProjectionConsent -> null // handled at invoke-time via MediaProjectionManager
    RequirementKind.ElevatedTier -> when (requirement.detail.lowercase()) {
        "shizuku" -> Intent().apply {
            setComponent(ComponentName(
                "moe.shizuku.privileged.api",
                "moe.shizuku.manager.MainActivity",
            ))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        else -> null
    }
}

/** Whether this requirement can be granted via the system runtime permission dialog (vs deep-link only). */
fun isRuntimePermissionRequest(requirement: AndroidRequirement): Boolean =
    requirement.kind == RequirementKind.RuntimePermission

/** Read a runtime permission's current state. */
fun Context.isPermissionGranted(permission: String): Boolean =
    checkPermission(permission, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED

/** Read whether the app has been granted the usage-stats AppOpsManager op. */
fun Context.isUsageAccessGranted(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = @Suppress("DEPRECATION")
    appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

/** Read whether the app is in the system notification-listener allowlist. */
fun Context.isNotificationListenerGranted(): Boolean {
    val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
    return flat?.split(":")?.any { it.startsWith(packageName) } == true
}

/** Read whether the HermesAccessibilityService is the currently-enabled accessibility service. */
fun Context.isAccessibilityServiceGranted(serviceClass: Class<*>): Boolean {
    val flat = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return flat?.split(":")?.any { it.endsWith("/" + serviceClass.name) } == true
}

/** Build a `market://` deep-link to an app's Play Store listing. */
fun playStoreIntent(pkg: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)