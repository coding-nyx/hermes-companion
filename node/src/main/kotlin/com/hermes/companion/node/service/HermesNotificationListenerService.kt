package com.hermes.companion.node.service

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The source of truth for notifications (`plan/03-android/notification-correctness.md`).
 * On connect and every restart it reconciles against getActiveNotifications(),
 * closing the restart/race gap without a timed poll. Full redaction + broker
 * forwarding + receipts arrive with the node runtime; today it exposes a live,
 * privacy-safe snapshot the read adapter serves.
 */
class HermesNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        connected.set(true)
        reconcile()
    }

    override fun onListenerDisconnected() {
        connected.set(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = reconcile()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = reconcile()

    private fun reconcile() {
        val snapshot = runCatching { activeNotifications?.toList() ?: emptyList() }.getOrDefault(emptyList())
        active.set(
            snapshot
                .filter { it.packageName != packageName } // structural self-loop guard
                .map { sbn ->
                    val extras = sbn.notification?.extras
                    ActiveNotification(
                        key = sbn.key ?: "",
                        packageName = sbn.packageName ?: "",
                        title = extras?.getCharSequence("android.title")?.toString().orEmpty(),
                        postedAt = sbn.postTime,
                    )
                },
        )
    }

    /** A minimal, already-safe view. Bodies never enter this snapshot. */
    data class ActiveNotification(
        val key: String,
        val packageName: String,
        val title: String,
        val postedAt: Long,
    )

    companion object {
        private val connected = AtomicBoolean(false)
        private val active = AtomicReference<List<ActiveNotification>>(emptyList())

        fun isConnected(): Boolean = connected.get()
        fun activeSnapshot(): List<ActiveNotification> = active.get()

        /** Whether the OS has this listener enabled — the real permission gate. */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners",
            ) ?: return false
            val pkg = context.packageName
            return flat.split(":").any { it.startsWith("$pkg/") }
        }
    }
}
