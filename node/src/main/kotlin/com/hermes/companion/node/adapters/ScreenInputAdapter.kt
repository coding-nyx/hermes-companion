package com.hermes.companion.node.adapters

import android.content.Context
import com.hermes.companion.domain.AndroidRequirement
import com.hermes.companion.domain.CapabilityHealth
import com.hermes.companion.domain.NodeCapability
import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.ReceiptStatus
import com.hermes.companion.domain.RequirementKind
import com.hermes.companion.node.CapabilityAdapter
import com.hermes.companion.node.elevated.ElevatedRoute
import com.hermes.companion.node.elevated.ElevatedShell
import com.hermes.companion.node.service.HermesAccessibilityService
import org.json.JSONObject

/**
 * Interactive input injection via the AccessibilityService: tap / swipe / text /
 * global actions (back, home, recents…). The "drive" half of remote control.
 * Exclusive (leased) so two operators cannot fight over the screen.
 */
class ScreenInputAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.ScreenInput
    override val requires = setOf(
        AndroidRequirement(RequirementKind.AccessibilityService, "accessibility"),
    )

    override fun health() =
        if (HermesAccessibilityService.isEnabled(context)) CapabilityHealth.Working
        else CapabilityHealth.PermissionMissing

    override suspend fun invoke(command: NodeCommand): Receipt {
        val svc = HermesAccessibilityService.current()
            ?: return refuse(command, "accessibility service not connected")
        val o = runCatching { JSONObject(command.params) }.getOrDefault(JSONObject())
        val action = o.optString("action")
        val ok = when (action) {
            "tap" -> svc.tap(o.optDouble("x").toFloat(), o.optDouble("y").toFloat())
            "swipe" -> svc.swipe(
                o.optDouble("x1").toFloat(), o.optDouble("y1").toFloat(),
                o.optDouble("x2").toFloat(), o.optDouble("y2").toFloat(),
                o.optLong("duration", 200L),
            )
            "text" -> svc.setText(o.optString("text"))
            "global" -> svc.globalAction(o.optString("name"))
            "key" -> return key(command, o.optString("name"), o.optInt("code", -1), svc)
            else -> return fail(command, "unknown action: $action")
        }
        return if (ok) done(command, "did $action") else fail(command, "$action failed")
    }

    // Enter/Tab/etc. Elevated route (input keyevent) is exact for any key; the
    // accessibility fallback can only submit the IME action (Enter/Go/Search).
    private suspend fun key(command: NodeCommand, name: String, explicitCode: Int, svc: HermesAccessibilityService): Receipt {
        val code = if (explicitCode >= 0) explicitCode else KEYCODES[name.lowercase()]
            ?: return fail(command, "unknown key: $name")
        if (ElevatedShell.route() != ElevatedRoute.None) {
            val r = ElevatedShell.run(listOf("input", "keyevent", code.toString()))
            return if (r.ok) done(command, "keyevent $code") else fail(command, r.err.firstOrNull() ?: "keyevent failed")
        }
        // No elevated route: only the IME action is reachable via accessibility.
        return if (code == 66 && svc.imeEnter()) done(command, "ime enter")
        else fail(command, "key '$name' needs the elevated tier (Shizuku/root)")
    }

    private fun done(c: NodeCommand, d: String) = Receipt(c.requestId, c.capability, ReceiptStatus.Completed, d, "{}", System.currentTimeMillis())
    private fun fail(c: NodeCommand, d: String) = Receipt(c.requestId, c.capability, ReceiptStatus.Failed, d, "{}", System.currentTimeMillis())
    private fun refuse(c: NodeCommand, d: String) = Receipt(c.requestId, c.capability, ReceiptStatus.Refused, d, "{}", System.currentTimeMillis())

    private companion object {
        // Android KeyEvent codes for the keys Hermes commonly needs.
        val KEYCODES = mapOf(
            "enter" to 66, "tab" to 61, "back" to 4, "home" to 3, "menu" to 82,
            "del" to 67, "backspace" to 67, "forward_del" to 112, "space" to 62,
            "search" to 84, "escape" to 111, "esc" to 111,
            "up" to 19, "down" to 20, "left" to 21, "right" to 22, "center" to 23,
            "page_up" to 92, "page_down" to 93,
        )
    }
}
