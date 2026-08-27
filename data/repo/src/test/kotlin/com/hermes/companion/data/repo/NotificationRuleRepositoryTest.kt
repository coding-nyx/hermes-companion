package com.hermes.companion.data.repo

import com.hermes.companion.domain.NotificationAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T5B: NotificationRuleRepository contract.
 *
 * 6 cases: empty default, set+remove, alphabetical ordering on observe,
 * multiple rules preserved, reply-rules add/remove by index.
 */
class NotificationRuleRepositoryTest {

    @Test
    fun `empty repository has no rules`() = runTest {
        val repo = InMemoryNotificationRuleRepository()
        assertEquals(emptyList<PackageRule>(), repo.rules().first())
        assertEquals(emptyList<Regex>(), repo.replyRules().first())
    }

    @Test
    fun `set then observe returns the rule`() = runTest {
        val repo = InMemoryNotificationRuleRepository()
        repo.setPackageAction("com.whatsapp", NotificationAction.Mute)
        val rules = repo.rules().first()
        assertEquals(1, rules.size)
        assertEquals("com.whatsapp", rules.first().packageName)
        assertEquals(NotificationAction.Mute, rules.first().action)
    }

    @Test
    fun `rules observe is sorted alphabetically by packageName`() = runTest {
        val repo = InMemoryNotificationRuleRepository()
        repo.setPackageAction("org.telegram", NotificationAction.Mute)
        repo.setPackageAction("com.whatsapp", NotificationAction.Mute)
        repo.setPackageAction("android", NotificationAction.All)
        val names = repo.rules().first().map { it.packageName }
        assertEquals(listOf("android", "com.whatsapp", "org.telegram"), names)
    }

    @Test
    fun `remove a non-existent rule is a no-op`() = runTest {
        val repo = InMemoryNotificationRuleRepository()
        repo.removePackageAction("com.does.not.exist")
        assertEquals(emptyList<PackageRule>(), repo.rules().first())
    }

    @Test
    fun `set replaces prior action for the same package`() = runTest {
        val repo = InMemoryNotificationRuleRepository()
        repo.setPackageAction("com.whatsapp", NotificationAction.Mute)
        repo.setPackageAction("com.whatsapp", NotificationAction.All)
        val rules = repo.rules().first()
        assertEquals(1, rules.size)
        assertEquals(NotificationAction.All, rules.first().action)
    }

    @Test
    fun `reply rules can be added and removed by index`() = runTest {
        val repo = InMemoryNotificationRuleRepository()
        // Use IGNORE_CASE so the test text doesn't have to mirror regex case.
        repo.addReplyRule(Regex("urgent", RegexOption.IGNORE_CASE))
        repo.addReplyRule(Regex("invoice", RegexOption.IGNORE_CASE))
        val before = repo.replyRules().first()
        assertEquals(2, before.size)
        assertTrue(before[0].containsMatchIn("URGENT please pay"))
        assertTrue(before[1].containsMatchIn("see attached invoice"))

        repo.removeReplyRule(0)
        val after = repo.replyRules().first()
        assertEquals(1, after.size)
        assertFalse(after[0].containsMatchIn("URGENT please pay"))
        assertTrue(after[0].containsMatchIn("see attached invoice"))
    }
}

