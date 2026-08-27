package com.hermes.companion.domain

/**
 * T5A: How a notification is routed to the active Hermes profile.
 *
 * Five actions locked by the v0.2 grill:
 *   - Off                 - drop on device, no Hermes involvement
 *   - All                 - POST every notification as-is
 *   - ImportantOnly       - POST only when package is in the comms/system allowlist
 *   - Mute                - same as Off but explicit per-app mute (per-package row)
 *   - ReplyWithRules      - POST only if title or text matches any user-defined regex
 *
 * [Off] and [Mute] have the same behavior at the router - they exist as
 * distinct enum values so the UI can distinguish "globally disabled" from
 * "muted just this app" without inspecting state shape.
 */
enum class NotificationAction {
    Off,
    All,
    ImportantOnly,
    Mute,
    ReplyWithRules,
}
