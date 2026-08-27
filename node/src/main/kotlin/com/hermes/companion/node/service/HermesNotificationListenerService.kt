package com.hermes.companion.node.service

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The source of truth for notifications (`plan/03-android/notification-correctness.md`).
 * On connect and every restart it reconciles against getActiveNotifications(),
 * closing the restart/race gap without a timed poll. Full redaction + broker
 * forwarding + receipts arrive with the node runtime; today it exposes a live,
 * privacy-safe snapshot the read adapter serves.
 */
class HermesNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        connected.set(true)
        reconcile()
    }

    override fun onListenerDisconnected() {
        connected.set(false)
        if (instance === this) instance = null
    }

    /** Cancel/dismiss a notification by its key. */
    fun dismiss(key: String): Boolean = runCatching { cancelNotification(key); true }.getOrDefault(false)

    /** Send an inline reply into a notification that offers a RemoteInput action. */
    fun reply(key: String, text: String): Boolean {
        val sbn = runCatching { activeNotifications?.firstOrNull { it.key == key } }.getOrNull() ?: return false
        val action = sbn.notification?.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() } ?: return false
        val inputs = action.remoteInputs ?: return false
        val intent = android.content.Intent()
        val results = android.os.Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        android.app.RemoteInput.addResultsToIntent(inputs, intent, results)
        return runCatching { action.actionIntent?.send(this, 0, intent); true }.getOrDefault(false)
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
        postings.value = postings.value + 1 // wake the event pump immediately
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
        @Volatile private var instance: HermesNotificationListenerService? = null
        fun current(): HermesNotificationListenerService? = instance

        private val postings = MutableStateFlow(0)
        /** Increments on every posted/removed notification — an event-driven wake for the pump. */
        fun postings(): StateFlow<Int> = postings.asStateFlow()

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
