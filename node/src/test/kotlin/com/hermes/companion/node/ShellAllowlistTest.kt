package com.hermes.companion.node

import com.hermes.companion.node.elevated.ShellAllowlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-JVM guard on the elevated shell: only vetted binaries, no metacharacters. */
class ShellAllowlistTest {

    @Test
    fun `allowlisted binaries with plain args pass`() {
        assertNull(ShellAllowlist.check(listOf("pm", "grant", "com.x", "android.permission.READ_CONTACTS")))
        assertNull(ShellAllowlist.check(listOf("dumpsys", "battery")))
        assertNull(ShellAllowlist.check(listOf("settings", "put", "secure", "k", "1")))
    }

    @Test
    fun `denied binaries are refused`() {
        assertNotNull(ShellAllowlist.check(listOf("su", "-c", "id")))
        assertNotNull(ShellAllowlist.check(listOf("rm", "-rf", "/")))
        assertNotNull(ShellAllowlist.check(listOf("reboot")))
    }

    @Test
    fun `non-allowlisted binary is refused`() {
        assertNotNull(ShellAllowlist.check(listOf("curl", "http://x")))
    }

    @Test
    fun `shell metacharacters are refused even on an allowlisted binary`() {
        for (arg in listOf("a;b", "a|b", "a&&b", "a\$(id)", "a`id`", "a>b", "a\nb")) {
            assertNotNull("expected refusal for '$arg'", ShellAllowlist.check(listOf("pm", arg)))
        }
    }

    @Test
    fun `empty argv is refused`() {
        assertEquals("empty command", ShellAllowlist.check(emptyList()))
    }
}
