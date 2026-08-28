package com.hermes.companion.node

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import kotlinx.serialization.Serializable

@Serializable
data class UsageSlice(
    val pkg: String,
    val activity: String = "",
    val lastUsed: Long = 0,
    val totalMs: Long = 0,
)

object UsageAccess {
    fun isGranted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun current(ctx: Context): UsageSlice? {
        if (!isGranted(ctx)) return NodeBus.foreground
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return NodeBus.foreground
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 15 * 60_000L, now)
        val event = UsageEvents.Event()
        var last: UsageSlice? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val resume = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (resume && !event.packageName.isNullOrBlank()) {
                last = UsageSlice(
                    pkg = event.packageName,
                    activity = event.className ?: "",
                    lastUsed = event.timeStamp,
                )
            }
        }
        return last ?: NodeBus.foreground
    }

    fun recent(ctx: Context, hours: Int = 24, limit: Int = 12): List<UsageSlice> {
        if (!isGranted(ctx)) return emptyList()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - hours * 3600_000L, now)
            ?: return emptyList()
        return stats
            .filter { it.totalTimeInForeground > 0 && !it.packageName.isNullOrBlank() }
            .sortedByDescending { it.lastTimeUsed }
            .take(limit)
            .map {
                UsageSlice(
                    pkg = it.packageName,
                    lastUsed = it.lastTimeUsed,
                    totalMs = it.totalTimeInForeground,
                )
            }
    }
}

object NodeBus {
    @Volatile
    var foreground: UsageSlice? = null

    fun setForeground(pkg: String?, activity: String?) {
        if (pkg.isNullOrBlank()) return
        foreground = UsageSlice(pkg = pkg, activity = activity ?: "", lastUsed = System.currentTimeMillis())
    }
}
