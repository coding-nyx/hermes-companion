package com.hermes.companion.node

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

enum class CoverageStatus { Working, Missing, Limited, Off }

data class CoverageRow(
    val id: String,
    val label: String,
    val detail: String,
    val status: CoverageStatus,
    val settingsAction: String? = null,
    val settingsData: String? = null,
)

object Coverage {
    fun snapshot(ctx: Context): List<CoverageRow> {
        val a11yOn = CompanionA11yService.isEnabled(ctx)
        val usageOn = UsageAccess.isGranted(ctx)
        val captureOn = ScreenCapture.hasGrant()
        val overlayOn = Settings.canDrawOverlays(ctx)
        val inputAllowed = NodePrefs.screenInputAllowed(ctx)

        return listOf(
            CoverageRow(
                id = "a11y",
                label = "Accessibility",
                detail = if (a11yOn) "Can read the UI tree and drive taps, swipes, type."
                else "Enable Hermes Companion in Accessibility settings. Required for screen use.",
                status = if (a11yOn) CoverageStatus.Working else CoverageStatus.Missing,
                settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
            ),
            CoverageRow(
                id = "usage",
                label = "App usage",
                detail = if (usageOn) "Foreground app and screen-on history are visible to Hermes."
                else "Grant usage access so Hermes knows which app is on screen.",
                status = if (usageOn) CoverageStatus.Working else CoverageStatus.Missing,
                settingsAction = Settings.ACTION_USAGE_ACCESS_SETTINGS,
            ),
            CoverageRow(
                id = "capture",
                label = "Screen capture",
                detail = when {
                    captureOn -> "MediaProjection grant is live for this session."
                    a11yOn -> "Accessibility screenshot is available. Optional MediaProjection grant for full frames."
                    else -> "Enable accessibility (or grant MediaProjection) for screenshots."
                },
                status = when {
                    captureOn -> CoverageStatus.Working
                    a11yOn -> CoverageStatus.Limited
                    else -> CoverageStatus.Missing
                },
            ),
            CoverageRow(
                id = "overlay",
                label = "Draw over apps",
                detail = if (overlayOn) "Status overlay allowed."
                else "Optional. Lets Companion show a live badge while Hermes drives the screen.",
                status = if (overlayOn) CoverageStatus.Working else CoverageStatus.Off,
                settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                settingsData = "package:${ctx.packageName}",
            ),
            CoverageRow(
                id = "input",
                label = "Hermes screen input",
                detail = if (inputAllowed) "Hermes may tap, swipe, and type. Toggle off to freeze input."
                else "Blocked. Hermes can still read the screen once accessibility is on.",
                status = if (inputAllowed && a11yOn) CoverageStatus.Working else CoverageStatus.Off,
            ),
        )
    }

    fun open(ctx: Context, row: CoverageRow) {
        val intent = when (row.id) {
            "a11y" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "overlay" -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}"),
            )
            else -> return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    fun a11yServiceListed(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }
    }
}

object NodePrefs {
    private const val PREF = "node"
    private const val KEY_INPUT = "screen_input"
    private const val KEY_CODE = "pairing_code"
    private const val KEY_URL = "gateway_url"
    private const val KEY_SESSION = "session"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun screenInputAllowed(ctx: Context) = p(ctx).getBoolean(KEY_INPUT, false)
    fun setScreenInputAllowed(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(KEY_INPUT, v).apply()

    fun pairingCode(ctx: Context): String {
        val existing = p(ctx).getString(KEY_CODE, null)
        if (!existing.isNullOrBlank()) return existing
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..6).map { alphabet.random() }.joinToString("")
        p(ctx).edit().putString(KEY_CODE, code).apply()
        return code
    }

    fun rotateCode(ctx: Context): String {
        p(ctx).edit().remove(KEY_CODE).apply()
        return pairingCode(ctx)
    }

    fun gatewayUrl(ctx: Context) = p(ctx).getString(KEY_URL, "") ?: ""
    fun setGatewayUrl(ctx: Context, url: String) = p(ctx).edit().putString(KEY_URL, url).apply()

    fun session(ctx: Context) = p(ctx).getString(KEY_SESSION, null)
    fun setSession(ctx: Context, session: String?) {
        p(ctx).edit().putString(KEY_SESSION, session).apply()
    }
}
