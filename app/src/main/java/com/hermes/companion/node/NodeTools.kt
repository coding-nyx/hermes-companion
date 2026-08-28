package com.hermes.companion.node

import android.content.Context
import android.content.Intent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object NodeTools {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun execute(ctx: Context, name: String, args: JsonObject = JsonObject(emptyMap())): String {
        val tool = name.removePrefix("companion_")
        val svc = CompanionA11yService.instance
        return when (tool) {
            "read_screen" -> {
                if (svc == null) return err("accessibility off")
                val include = args["includeSystem"]?.jsonPrimitive?.booleanOrNull == true
                json.encodeToString(svc.dumpTree(includeSystem = include))
            }
            "tap" -> {
                if (!NodePrefs.screenInputAllowed(ctx)) return err("screen input blocked")
                if (svc == null) return err("accessibility off")
                val tree = svc.dumpTree()
                val text = args["text"]?.jsonPrimitive?.contentOrNull
                val node = when {
                    !text.isNullOrBlank() -> ScreenQuery.findByText(tree.nodes, text)
                    else -> {
                        val x = args["x"]?.jsonPrimitive?.intOrNull
                        val y = args["y"]?.jsonPrimitive?.intOrNull
                        if (x == null || y == null) return err("need text or x,y")
                        ScreenQuery.findAt(tree.nodes, x, y) ?: ScreenNode(id = -1, l = x, t = y, r = x + 1, b = y + 1)
                    }
                } ?: return err("no node matching $text")
                val ok = svc.tap(node.cx, node.cy)
                if (ok) "tapped ${node.label.ifBlank { "${node.cx},${node.cy}" }}" else err("tap cancelled")
            }
            "swipe" -> {
                if (!NodePrefs.screenInputAllowed(ctx)) return err("screen input blocked")
                if (svc == null) return err("accessibility off")
                val x1 = args["x1"]?.jsonPrimitive?.intOrNull ?: return err("need x1,y1,x2,y2")
                val y1 = args["y1"]?.jsonPrimitive?.intOrNull ?: return err("need x1,y1,x2,y2")
                val x2 = args["x2"]?.jsonPrimitive?.intOrNull ?: return err("need x1,y1,x2,y2")
                val y2 = args["y2"]?.jsonPrimitive?.intOrNull ?: return err("need x1,y1,x2,y2")
                val ok = svc.swipe(x1, y1, x2, y2)
                if (ok) "swiped $x1,$y1 → $x2,$y2" else err("swipe cancelled")
            }
            "type" -> {
                if (!NodePrefs.screenInputAllowed(ctx)) return err("screen input blocked")
                if (svc == null) return err("accessibility off")
                val text = args["text"]?.jsonPrimitive?.contentOrNull ?: return err("text required")
                val ok = svc.type(text)
                if (ok) "typed ${text.take(80)}" else err("no focused field")
            }
            "press_key" -> {
                if (svc == null) return err("accessibility off")
                val key = args["key"]?.jsonPrimitive?.contentOrNull ?: "home"
                val ok = svc.global(key)
                if (ok) "pressed $key" else err("unknown key $key")
            }
            "app_usage" -> {
                val current = UsageAccess.current(ctx)
                val recent = UsageAccess.recent(ctx)
                buildString {
                    append("{")
                    append("\"granted\":").append(UsageAccess.isGranted(ctx))
                    append(",\"current\":\"").append(current?.pkg ?: "")
                    append("\",\"activity\":\"").append(current?.activity ?: "")
                    append("\",\"recent\":[")
                    append(recent.joinToString(",") { "{\"pkg\":\"${it.pkg}\",\"totalMs\":${it.totalMs},\"lastUsed\":${it.lastUsed}}" })
                    append("]}")
                }
            }
            "get_device_state" -> {
                val current = UsageAccess.current(ctx)
                buildString {
                    append("{")
                    append("\"accessibility\":").append(CompanionA11yService.isEnabled(ctx))
                    append(",\"usageAccess\":").append(UsageAccess.isGranted(ctx))
                    append(",\"screenInput\":").append(NodePrefs.screenInputAllowed(ctx))
                    append(",\"screenCapture\":").append(ScreenCapture.hasGrant())
                    append(",\"currentApp\":\"").append(current?.pkg ?: "")
                    append("\",\"activity\":\"").append(current?.activity ?: "")
                    append("\"}")
                }
            }
            "open_app" -> {
                val q = args["name"]?.jsonPrimitive?.contentOrNull ?: return err("name required")
                val launch = ctx.packageManager.getLaunchIntentForPackage(q)
                    ?: ctx.packageManager.queryIntentActivities(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                        0,
                    ).firstOrNull {
                        val label = it.loadLabel(ctx.packageManager).toString()
                        label.equals(q, true) || label.contains(q, true) || it.activityInfo.packageName.contains(q, true)
                    }?.activityInfo?.let {
                        ctx.packageManager.getLaunchIntentForPackage(it.packageName)
                    }
                if (launch == null) return err("app not found: $q")
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(launch)
                "opened $q"
            }
            "capture_screenshot" -> {
                if (svc == null) return err("accessibility off")
                svc.screenshotHint()
            }
            else -> err("unknown tool $name")
        }
    }

    private fun err(msg: String) = """{"ok":false,"error":"$msg"}"""
}
