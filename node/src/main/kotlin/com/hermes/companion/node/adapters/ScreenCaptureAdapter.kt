package com.hermes.companion.node.adapters

import android.content.Context
import android.os.Build
import android.util.Base64
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
 * Live screen frames via AccessibilityService.takeScreenshot (Android 11+). The
 * "view" half of interactive remote control; exclusive (leased) with a
 * per-session holder. Below API 30 it is OS-limited (MediaProjection fallback is
 * a later optimisation).
 */
class ScreenCaptureAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.ScreenCapture
    override val requires = setOf(
        AndroidRequirement(RequirementKind.AccessibilityService, "accessibility"),
    )

    override fun health(): CapabilityHealth = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> CapabilityHealth.OsLimited
        !HermesAccessibilityService.isEnabled(context) -> CapabilityHealth.PermissionMissing
        else -> CapabilityHealth.Working
    }

    override suspend fun invoke(command: NodeCommand): Receipt {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return receipt(command, ReceiptStatus.Refused, "screen capture needs Android 11+", "{}")
        }
        val svc = HermesAccessibilityService.current()
            ?: return receipt(command, ReceiptStatus.Refused, "accessibility not connected", "{}")
        val jpeg = svc.screenshot()
            ?: return receipt(command, ReceiptStatus.Failed, "capture failed", "{}")
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val payload = JSONObject().put("format", "jpeg").put("bytes", jpeg.size).put("data", b64).toString()
        return receipt(command, ReceiptStatus.Completed, "captured ${jpeg.size} bytes", payload)
    }

    private fun receipt(c: NodeCommand, status: ReceiptStatus, detail: String, payload: String) =
        Receipt(c.requestId, c.capability, status, detail, payload, System.currentTimeMillis())
}
