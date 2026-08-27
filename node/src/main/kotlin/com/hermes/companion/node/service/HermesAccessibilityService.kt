package com.hermes.companion.node.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.view.Display
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The input-injection tier of remote control. Enabled separately, on purpose:
 * it can inspect and drive the UI. Exposes tap / swipe / text / global actions
 * for the screen.input adapter (`plan/03-android/full-node-mode.md` — the
 * Accessibility trust tier).
 */
class HermesAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onInterrupt() {}
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    fun tap(x: Float, y: Float): Boolean = gesture(Path().apply { moveTo(x, y) }, 1, 50)

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean =
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, 0, durationMs)

    fun globalAction(name: String): Boolean {
        val action = when (name.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "lock" -> if (android.os.Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return false
            else -> return false
        }
        return performGlobalAction(action)
    }

    /** Press the IME action (Enter/Go/Search) on the focused field. API 30+. */
    fun imeEnter(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return runCatching {
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }.getOrDefault(false)
    }

    /** Set text into the currently focused editable field. */
    fun setText(text: String): Boolean {
        val node: AccessibilityNodeInfo = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** A JPEG of the current screen (Android 11+). The "view" half of remote control. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    suspend fun screenshot(quality: Int = 55): ByteArray? = suspendCancellableCoroutine { cont ->
        runCatching {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bytes = runCatching {
                        val hb = result.hardwareBuffer
                        val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                        hb.close()
                        val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        soft?.let {
                            val out = ByteArrayOutputStream()
                            it.compress(Bitmap.CompressFormat.JPEG, quality, out)
                            it.recycle()
                            out.toByteArray()
                        }
                    }.getOrNull()
                    if (cont.isActive) cont.resume(bytes)
                }
                override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(null) }
            })
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    private fun gesture(path: Path, startDelay: Long, duration: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, startDelay, duration.coerceAtLeast(1))
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    companion object {
        @Volatile private var instance: HermesAccessibilityService? = null
        fun current(): HermesAccessibilityService? = instance

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = context.packageName + "/" + HermesAccessibilityService::class.java.name
            return flat.split(":").any { it.equals(me, ignoreCase = true) }
        }
    }
}
