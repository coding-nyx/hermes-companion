package com.hermes.companion.ui.node

import com.hermes.companion.data.repo.CapabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure-data capability status pill spec extracted from
 * NodeScreen's [StatusPill] composable.
 *
 * The pill is what the user actually sees in the right edge of the Node tab
 * coverage matrix. It used to be a long English label ("OS-limited") that
 * overflowed on narrow screens. The redesign replaces those with icon-led
 * pills. These tests pin the spec so a future "tidy" doesn't reintroduce
 * the long-text overflow.
 */
class CapabilityPillSpecTest {

    @Test
    fun `Working row has a check-circle icon and no text`() {
        val spec = capabilityPillSpec(CapabilityStatus.Working, canTap = false)
        assertEquals(StatusIcon.CheckCircle, spec.icon)
        assertNull("Working pill should have no text label", spec.label)
        assertFalse(spec.showChevron)
        assertEquals("Working", spec.contentDescription)
    }

    @Test
    fun `OS-limited row has lock icon and short OS label`() {
        val spec = capabilityPillSpec(CapabilityStatus.OsLimited, canTap = false)
        assertEquals(StatusIcon.Lock, spec.icon)
        assertEquals("OS", spec.label)
        assertFalse("OS-limited has no grant flow", spec.showChevron)
        assertEquals("OS-limited", spec.contentDescription)
    }

    @Test
    fun `MissingPermission row with canTap shows Grant plus chevron`() {
        val spec = capabilityPillSpec(CapabilityStatus.MissingPermission, canTap = true)
        assertEquals(StatusIcon.Shield, spec.icon)
        assertEquals("Grant", spec.label)
        assertTrue("canTap rows should expose a chevron to signal tappability", spec.showChevron)
        assertEquals("Permission needed", spec.contentDescription)
    }

    @Test
    fun `MissingPermission row without canTap hides the chevron`() {
        val spec = capabilityPillSpec(CapabilityStatus.MissingPermission, canTap = false)
        assertEquals(StatusIcon.Shield, spec.icon)
        assertEquals("Grant", spec.label)
        assertFalse(spec.showChevron)
    }

    @Test
    fun `Unavailable row has lock icon and no text`() {
        val spec = capabilityPillSpec(CapabilityStatus.Unavailable, canTap = false)
        assertEquals(StatusIcon.Lock, spec.icon)
        assertNull(spec.label)
        assertFalse(spec.showChevron)
    }

    /**
     * The whole point of the redesign: no pill should have a long English
     * label that overflows a 360dp row. The longest label in the new spec
     * is "Grant" (5 chars) — anything else is a regression.
     */
    @Test
    fun `no pill label exceeds five characters`() {
        val statuses = listOf(
            CapabilityStatus.Working,
            CapabilityStatus.OsLimited,
            CapabilityStatus.MissingPermission,
            CapabilityStatus.Unavailable,
        )
        for (status in statuses) {
            for (canTap in listOf(true, false)) {
                val spec = capabilityPillSpec(status, canTap)
                val label = spec.label
                assertTrue(
                    "Pill label '$label' for status=$status exceeds 5 chars (would overflow on a small phone)",
                    label == null || label.length <= 5,
                )
            }
        }
    }

    /**
     * Companion invariant to the label test: every spec must declare an
     * icon — text-only pills would be the original bug shape.
     */
    @Test
    fun `every pill spec declares an icon`() {
        val statuses = listOf(
            CapabilityStatus.Working,
            CapabilityStatus.OsLimited,
            CapabilityStatus.MissingPermission,
            CapabilityStatus.Unavailable,
        )
        for (status in statuses) {
            val spec = capabilityPillSpec(status, canTap = status == CapabilityStatus.MissingPermission)
            // The enum presence itself is the assertion; just make sure we
            // didn't accidentally map to something nullable.
            assertEquals(
                "Spec for $status should declare a non-null icon enum",
                StatusIcon::class.java,
                spec.icon::class.java,
            )
        }
    }
}