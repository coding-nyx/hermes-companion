package com.hermes.companion.node.routing

import com.hermes.companion.domain.NotificationAction

/**
 * T5A: The decision function for what to do with a posted notification.
 *
 * Pure: takes the action, the package name, title, text, allowlist, and rules;
 * returns a [Decision]. No I/O, no DB, no logging - callers (the NLS and the
 * WS send coroutine) do those.
 *
 * Why pure: this is the only file that decides whether a notification goes to
 * Hermes. Keeping it pure means the test suite can cover all 5 actions without
 * Room, OkHttp, or any coroutine machinery.
 */
class NotificationRouter(
    private val importantAllowlist: Set<String> = DEFAULT_IMPORTANT_ALLOWLIST,
) {

    /**
     * Resolve the action for a specific package. If [perPackageOverride] is
     * non-null, it wins over the [defaultAction] - this is how "Mute this app"
     * is modelled: the per-package row carries [NotificationAction.Mute] and
     * the global default is irrelevant for that package.
     */
    fun decide(
        defaultAction: NotificationAction,
        perPackageOverride: NotificationAction?,
        packageName: String,
        title: String,
        text: String,
        rules: List<Regex> = emptyList(),
    ): Decision {
        val effective = perPackageOverride ?: defaultAction
        return when (effective) {
            NotificationAction.Off -> Decision.Mute
            NotificationAction.All -> Decision.Post
            NotificationAction.ImportantOnly ->
                if (importantAllowlist.contains(packageName)) Decision.Post
                else Decision.Mute
            NotificationAction.Mute -> Decision.Mute
            NotificationAction.ReplyWithRules ->
                if (rules.any { it.containsMatchIn(title) || it.containsMatchIn(text) })
                    Decision.Post
                else Decision.Mute
        }
    }

    companion object {
        /**
         * The default allowlist for [NotificationAction.ImportantOnly].
         *
         * WhatsApp, Telegram, Slack, Discord, Signal, Google Messages, Phone,
         * and the system shell (for genuine system notifications). This is
         * a v0.2 best-effort list; per-app rules (T5B) let users add more.
         */
        val DEFAULT_IMPORTANT_ALLOWLIST: Set<String> = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.Slack",
            "com.discord",
            "org.thoughtcrime.securesms",
            "com.google.android.apps.messaging",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.systemui",
            "android",
        )
    }
}

enum class Decision { Post, Mute }
