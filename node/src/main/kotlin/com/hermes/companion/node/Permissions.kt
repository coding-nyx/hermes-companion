package com.hermes.companion.node

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/** Small runtime-permission helpers shared by the adapters. */
fun Context.hasPermission(permission: String): Boolean =
    checkPermission(permission, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED

fun Context.hasUsageStatsAccess(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = @Suppress("DEPRECATION")
    appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}
