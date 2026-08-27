package com.hermes.companion.node

import android.content.Context
import com.hermes.companion.domain.RequirementKind
import com.hermes.companion.node.elevated.RootDetector
import com.hermes.companion.node.elevated.ShizukuGateway
import com.hermes.companion.node.service.HermesAccessibilityService
import com.hermes.companion.node.service.HermesNotificationListenerService

/**
 * One rung of the Full Node Mode setup ladder: a distinct OS requirement, its
 * live satisfied state, and enough metadata for the UI to offer the right grant
 * action. Derived from the adapters' declared requirements plus a few setup-only
 * rungs, so the checklist and the coverage matrix can never disagree.
 */
data class NodeRequirementStatus(
    val id: String,
    val kind: RequirementKind,
    val label: String,
    val detail: String,
    val satisfied: Boolean,
    /** e.g. a runtime permission string, or a settings-action for special access. */
    val target: String,
    val enablesCount: Int,
)

private data class RungSpec(val id: String, val kind: RequirementKind, val label: String, val detail: String, val target: String)

fun nodeRequirements(context: Context): List<NodeRequirementStatus> {
    val registry = defaultAdapterRegistry(context)
    // Count how many capabilities each requirement unlocks.
    val enables = mutableMapOf<String, Int>()
    registry.all().forEach { a -> a.requires.forEach { r -> enables[r.detail] = (enables[r.detail] ?: 0) + 1 } }

    val rungs = listOf(
        RungSpec("notif", RequirementKind.NotificationListener, "Notification access",
            "The source of truth for notifications, replies and dismissals.", "notification-listener"),
        RungSpec("contacts", RequirementKind.RuntimePermission, "Contacts",
            "Resolve callers and message senders to names.", "android.permission.READ_CONTACTS"),
        RungSpec("calllog", RequirementKind.RuntimePermission, "Call log",
            "Missed / received / dialed outcomes that survive process death.", "android.permission.READ_CALL_LOG"),
        RungSpec("location", RequirementKind.RuntimePermission, "Location",
            "Last-known location for location.read.", "android.permission.ACCESS_FINE_LOCATION"),
        RungSpec("a11y", RequirementKind.AccessibilityService, "Accessibility (remote control)",
            "Screen inspection + input injection for interactive remote control.", "accessibility"),
        RungSpec("usage", RequirementKind.SystemSetting, "Usage access",
            "Foreground app usage over the last day.", "usage-access"),
        RungSpec("battery", RequirementKind.SystemSetting, "Battery unrestricted",
            "Keeps the node broker alive overnight (plus Samsung autostart).", "battery-unrestricted"),
        RungSpec("shizuku", RequirementKind.ElevatedTier, "Elevated tier (Shizuku / root)",
            "Allowlisted elevated shell for system.shell and silent permission grants.", "shizuku"),
    )

    return rungs.map { r ->
        NodeRequirementStatus(
            id = r.id,
            kind = r.kind,
            label = r.label,
            detail = r.detail,
            satisfied = isSatisfied(context, r),
            target = r.target,
            enablesCount = when (r.id) {
                "notif" -> enables["notification access"] ?: 0
                "a11y" -> enables["accessibility"] ?: 0
                "usage" -> enables["usage access"] ?: 0
                else -> enables[r.target] ?: 0
            },
        )
    }
}

private fun isSatisfied(context: Context, r: RungSpec): Boolean = when (r.id) {
    "notif" -> HermesNotificationListenerService.isEnabled(context)
    "a11y" -> HermesAccessibilityService.isEnabled(context)
    "usage" -> context.hasUsageStatsAccess()
    "battery" -> isIgnoringBatteryOptimizations(context)
    "shizuku" -> ShizukuGateway.isGranted() || RootDetector.isRootGranted()
    else -> context.hasPermission(r.target)
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
