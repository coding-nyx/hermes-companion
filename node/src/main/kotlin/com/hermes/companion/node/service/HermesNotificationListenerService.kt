package com.hermes.companion.node.service

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hermes.companion.common.ActiveGatewayConfig
import com.hermes.companion.domain.NotificationAction
import com.hermes.companion.node.routing.Decision
import com.hermes.companion.node.routing.NotificationRouter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
        refreshForwarder()
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: run { reconcile(); return }
        val title = sbn?.notification?.extras?.getCharSequence("android.title")?.toString().orEmpty()
        val text  = sbn?.notification?.extras?.getCharSequence("android.text")?.toString().orEmpty()
        val postedAt = sbn?.postTime ?: System.currentTimeMillis()

        // Reconcile first - keeps the existing UI surface coherent even if the
        // forward path fails.
        reconcile()

        // T7 default action: ImportantOnly with the built-in allowlist. Per-package
        // overrides and reply-rules come from [InMemoryNotificationRuleRepository]
        // in the app process; we can't read those from the OS-instantiated NLS,
        // so v0.2 ships the default action only and lets the gateway/agent
        // decide per-package routing via webhooks. Future T7.x: have the NLS
        // read the rules via a SharedPreferences export path written by :app.
        val decision = router.decide(
            defaultAction = NotificationAction.ImportantOnly,
            perPackageOverride = null,
            packageName = pkg,
            title = title,
            text = text,
            rules = emptyList(),
        )
        if (decision == Decision.Post) {
            scope.launch { forwarder.postIncoming(pkg, title, text, postedAt) }
        }
    }
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
        // T7: a static forwarder + router held at companion-object scope; the
        // NLS is OS-instantiated and can't take constructor args. SettingsViewModel
        // populates [ActiveGatewayConfig] on every active change; we mirror it
        // here on listener connect.
        private val router = NotificationRouter()
        // The forwarder's URL/nodeId are sourced from the file written by
        // :app's SettingsViewModel.setActive (see companion-gateway-routing.md).
        // Read once at construction; refreshForwarder() below re-reads on reconnect.
        @Volatile private var forwarder: NotificationForwarder =
            NotificationForwarder(activeUrl = null, nodeId = null)
        // Background scope for the HTTP POST so the NLS thread is never blocked.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Re-read the active gateway URL + nodeId from the file written by
         * :app's SettingsViewModel.setActive. Called on listener connect and
         * could be called again on reconnect (rare; OS may unbind the service).
         */
        private fun refreshForwarder() {
            // NLS extends Service extends ContextWrapper so `filesDir` is
            // available on the instance; we may be called before [instance]
            // is set during cold boot.
            val dir = instance?.filesDir ?: return
            val (url, node) = ActiveGatewayConfig.readSync(dir)
            forwarder = NotificationForwarder(
                activeUrl = url,
                nodeId = node,
            )
        }

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
