package com.hermes.companion.node.routing

import com.hermes.companion.domain.NotificationAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T5A: NotificationRouter contract.
 *
 * The router is a pure function: action + package + title + text -> Decision.
 * 5 cases, one per [NotificationAction]. Per-package overrides always win.
 */
class NotificationRouterTest {

    private val router = NotificationRouter()

    @Test
    fun `Off mutes everything regardless of package`() {
        val d = router.decide(
            defaultAction = NotificationAction.Off,
            perPackageOverride = null,
            packageName = "com.whatsapp",
            title = "Alice",
            text = "Hey",
        )
        assertEquals(Decision.Mute, d)
    }

    @Test
    fun `All posts everything regardless of package`() {
        val d = router.decide(
            defaultAction = NotificationAction.All,
            perPackageOverride = null,
            packageName = "com.random.game",
            title = "Level up",
            text = "You won!",
        )
        assertEquals(Decision.Post, d)
    }

    @Test
    fun `ImportantOnly posts for allowlisted packages`() {
        val post = router.decide(
            defaultAction = NotificationAction.ImportantOnly,
            perPackageOverride = null,
            packageName = "com.whatsapp",
            title = "Alice",
            text = "Hey",
        )
        assertEquals(Decision.Post, post)

        val mute = router.decide(
            defaultAction = NotificationAction.ImportantOnly,
            perPackageOverride = null,
            packageName = "com.random.game",
            title = "Level up",
            text = "You won!",
        )
        assertEquals(Decision.Mute, mute)
    }

    @Test
    fun `Mute is per-package and wins over a permissive default`() {
        val d = router.decide(
            defaultAction = NotificationAction.All,
            perPackageOverride = NotificationAction.Mute,
            packageName = "com.anything",
            title = "x",
            text = "y",
        )
        assertEquals(Decision.Mute, d)
    }

    @Test
    fun `ReplyWithRules posts when title or text matches a rule`() {
        val rules = listOf(Regex("invoice", RegexOption.IGNORE_CASE), Regex("urgent", RegexOption.IGNORE_CASE))

        // Title case matches - regex is case-sensitive so we use the same case.
        val matchTitle = router.decide(
            defaultAction = NotificationAction.ReplyWithRules,
            perPackageOverride = null,
            packageName = "com.anything",
            title = "Urgent: please pay",
            text = "...",
            rules = rules,
        )
        assertEquals(Decision.Post, matchTitle)

        // Text contains one of the rule keywords.
        val matchText = router.decide(
            defaultAction = NotificationAction.ReplyWithRules,
            perPackageOverride = null,
            packageName = "com.anything",
            title = "Fwd: fwd: fwd",
            text = "Please see attached invoice",
            rules = rules,
        )
        assertEquals(Decision.Post, matchText)

        val noMatch = router.decide(
            defaultAction = NotificationAction.ReplyWithRules,
            perPackageOverride = null,
            packageName = "com.anything",
            title = "Lunch?",
            text = "Thinking about Thai today",
            rules = rules,
        )
        assertEquals(Decision.Mute, noMatch)
    }

    @Test
    fun `per-package override wins over default`() {
        // Default = All, but this app is muted.
        val muted = router.decide(
            defaultAction = NotificationAction.All,
            perPackageOverride = NotificationAction.Mute,
            packageName = "com.spam",
            title = "x",
            text = "y",
        )
        assertEquals(Decision.Mute, muted)

        // Default = Off, but this app gets everything.
        val pinned = router.decide(
            defaultAction = NotificationAction.Off,
            perPackageOverride = NotificationAction.All,
            packageName = "com.priority",
            title = "x",
            text = "y",
        )
        assertEquals(Decision.Post, pinned)
    }
}
