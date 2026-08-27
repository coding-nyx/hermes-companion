package com.hermes.companion.node.adapters

import android.content.Context
import com.hermes.companion.domain.AndroidRequirement
import com.hermes.companion.domain.CapabilityHealth
import com.hermes.companion.domain.NodeCapability
import com.hermes.companion.domain.NodeCommand
import com.hermes.companion.domain.ReceiptStatus
import com.hermes.companion.domain.Receipt
import com.hermes.companion.domain.RequirementKind
import com.hermes.companion.node.CapabilityAdapter
import com.hermes.companion.node.service.HermesNotificationListenerService

/** Active/incoming notifications via the NotificationListenerService. */
class NotificationsReadAdapter(private val context: Context) : CapabilityAdapter {
    override val capability = NodeCapability.NotificationsRead
    override val requires = setOf(
        AndroidRequirement(RequirementKind.NotificationListener, "notification access"),
    )

    override fun health(): CapabilityHealth =
        if (!HermesNotificationListenerService.isEnabled(context)) CapabilityHealth.PermissionMissing
        else CapabilityHealth.Working

    override suspend fun invoke(command: NodeCommand): Receipt {
        if (health() != CapabilityHealth.Working) {
            return Receipt(
                requestId = command.requestId,
                capability = capability.family,
                status = ReceiptStatus.Refused,
                detail = "notification access not granted",
                at = System.currentTimeMillis(),
            )
        }
        val items = HermesNotificationListenerService.activeSnapshot()
        // Titles only; bodies never leave the device at this level.
        val json = items.joinToString(prefix = "[", postfix = "]") { n ->
            """{"package":"${n.packageName}","title":${quote(n.title)},"postedAt":${n.postedAt}}"""
        }
        return Receipt(
            requestId = command.requestId,
            capability = capability.family,
            status = ReceiptStatus.Completed,
            detail = "${items.size} active",
            payload = """{"count":${items.size},"active":$json}""",
            at = System.currentTimeMillis(),
        )
    }

    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""
}
