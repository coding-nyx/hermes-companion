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
import com.hermes.companion.node.service.HermesNotificationListenerService
import org.json.JSONObject

private val listenerReq = setOf(
    AndroidRequirement(RequirementKind.NotificationListener, "notification access"),
)

private fun receipt(cmd: NodeCommand, status: ReceiptStatus, detail: String) =
    Receipt(cmd.requestId, cmd.capability, status, detail, "{}", System.currentTimeMillis())

private fun NodeCommand.field(name: String): String =
    runCatching { JSONObject(params).optString(name) }.getOrDefault("")

private fun listenerHealth(context: Context): CapabilityHealth =
    if (HermesNotificationListenerService.isEnabled(context)) CapabilityHealth.Working
    else CapabilityHealth.PermissionMissing

/** Dismiss a posted notification by key. */
class NotificationsDismissAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.NotificationsDismiss
    override val requires = listenerReq
    override fun health() = listenerHealth(context)
    override suspend fun invoke(command: NodeCommand): Receipt {
        val svc = HermesNotificationListenerService.current()
            ?: return receipt(command, ReceiptStatus.Refused, "listener not connected")
        val key = command.field("key")
        if (key.isBlank()) return receipt(command, ReceiptStatus.Failed, "key required")
        return receipt(
            command,
            if (svc.dismiss(key)) ReceiptStatus.Completed else ReceiptStatus.Failed,
            "dismiss $key",
        )
    }
}

/** Inline-reply into a notification that offers a RemoteInput action. */
class NotificationsReplyAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.NotificationsReply
    override val requires = listenerReq
    override fun health() = listenerHealth(context)
    override suspend fun invoke(command: NodeCommand): Receipt {
        val svc = HermesNotificationListenerService.current()
            ?: return receipt(command, ReceiptStatus.Refused, "listener not connected")
        val key = command.field("key")
        val text = command.field("text")
        if (key.isBlank() || text.isBlank()) return receipt(command, ReceiptStatus.Failed, "key and text required")
        return receipt(
            command,
            if (svc.reply(key, text)) ReceiptStatus.Completed else ReceiptStatus.Failed,
            "reply into $key",
        )
    }
}
