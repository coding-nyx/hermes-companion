package com.hermes.companion.node

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CompanionA11yService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            NodeBus.setForeground(event.packageName?.toString(), event.className?.toString())
        }
    }

    fun dumpTree(includeSystem: Boolean = false, maxNodes: Int = 80): ScreenTree {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString() ?: NodeBus.foreground?.pkg ?: ""
        val title = root?.contentDescription?.toString()
            ?: root?.text?.toString()
            ?: NodeBus.foreground?.activity
            ?: pkg
        if (root == null) return ScreenTree(pkg, title, emptyList())
        val out = ArrayList<ScreenNode>(maxNodes)
        val bounds = Rect()
        walk(root, out, bounds, includeSystem, maxNodes, 0)
        return ScreenTree(pkg, title, out)
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        out: MutableList<ScreenNode>,
        bounds: Rect,
        includeSystem: Boolean,
        maxNodes: Int,
        depth: Int,
    ) {
        if (out.size >= maxNodes || depth > 18) return
        val pkg = node.packageName?.toString() ?: ""
        if (!includeSystem && pkg.startsWith("com.android.systemui")) return
        node.getBoundsInScreen(bounds)
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val useful = node.isClickable || node.isEditable || node.isFocused ||
            text.isNotBlank() || desc.isNotBlank()
        if (useful && bounds.width() > 0 && bounds.height() > 0) {
            out += ScreenNode(
                id = out.size,
                text = text.take(120),
                desc = desc.take(120),
                cls = node.className?.toString()?.substringAfterLast('.') ?: "",
                pkg = pkg,
                clickable = node.isClickable,
                focused = node.isFocused,
                editable = node.isEditable,
                l = bounds.left,
                t = bounds.top,
                r = bounds.right,
                b = bounds.bottom,
            )
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, out, bounds, includeSystem, maxNodes, depth + 1)
            child.recycle()
        }
    }

    fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long = 280): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, ms.coerceIn(80, 1200))
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    fun type(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: focusedEditable(root)
        if (focused == null) {
            root.recycle()
            return false
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focused.recycle()
        root.recycle()
        return ok
    }

    private fun focusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = focusedEditable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    fun global(name: String): Boolean {
        val action = when (name.lowercase()) {
            "home" -> GLOBAL_ACTION_HOME
            "back" -> GLOBAL_ACTION_BACK
            "recents", "recent" -> GLOBAL_ACTION_RECENTS
            "notifications", "shade" -> GLOBAL_ACTION_NOTIFICATIONS
            "lock" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return false
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_TAKE_SCREENSHOT else return false
            else -> return false
        }
        return performGlobalAction(action)
    }

    fun screenshotHint(): String {
        if (Build.VERSION.SDK_INT < 30) {
            return "Accessibility screenshots need Android 11+. Grant MediaProjection on the Node page."
        }
        val latch = CountDownLatch(1)
        val slot = AtomicReference<String>("screenshot failed")
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    slot.set("screenshot ok ${result.hardwareBuffer.width}x${result.hardwareBuffer.height}")
                    result.hardwareBuffer.close()
                    latch.countDown()
                }

                override fun onFailure(errorCode: Int) {
                    slot.set("screenshot failed code=$errorCode")
                    latch.countDown()
                }
            },
        )
        latch.await(4, TimeUnit.SECONDS)
        return slot.get()
    }

    private fun dispatch(gesture: GestureDescription): Boolean {
        val latch = CountDownLatch(1)
        val ok = AtomicReference(false)
        val sent = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    ok.set(true)
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    ok.set(false)
                    latch.countDown()
                }
            },
            null,
        )
        if (!sent) return false
        latch.await(2, TimeUnit.SECONDS)
        return ok.get()
    }

    companion object {
        @Volatile
        var instance: CompanionA11yService? = null

        fun isEnabled(ctx: Context): Boolean {
            val expected = ComponentName(ctx, CompanionA11yService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) } ||
                Coverage.a11yServiceListed(ctx)
        }
    }
}
