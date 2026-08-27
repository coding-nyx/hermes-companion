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
            else -> return fail(command, "unknown action: $action")
        }
        return if (ok) done(command, "did $action") else fail(command, "$action failed")
    }

    private fun done(c: NodeCommand, d: String) = Receipt(c.requestId, c.capability, ReceiptStatus.Completed, d, "{}", System.currentTimeMillis())
    private fun fail(c: NodeCommand, d: String) = Receipt(c.requestId, c.capability, ReceiptStatus.Failed, d, "{}", System.currentTimeMillis())
    private fun refuse(c: NodeCommand, d: String) = Receipt(c.requestId, c.capability, ReceiptStatus.Refused, d, "{}", System.currentTimeMillis())
}
