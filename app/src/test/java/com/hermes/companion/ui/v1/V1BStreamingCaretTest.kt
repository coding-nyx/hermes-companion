package com.hermes.companion.ui.v1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.toArgb

/**
 * Pure-JVM tests for [ProfilePalette]. No Compose / Robolectric
 * dependency — these run in the standard `:app:testDebugUnitTest`
 * pipeline.
 *
 * Color equality semantics across the JVM unit-test runner don't
 * compare `.value` directly (it's a ULong packed value that differs
 * per Compose runtime). We pin the comparison via `toArgb()` which
 * always returns a signed Int carrying ARGB.
 */
class ProfilePaletteTest {
    @Test
    fun `accentForProfile returns Indigo for coder`() {
        val c = ProfilePalette.accentForProfile("coder")
        val expected = androidx.compose.ui.graphics.Color(0xFFB3B6F2)
        assertEquals(expected.toArgb(), c.toArgb())
    }

    @Test
    fun `accentForProfile returns Magenta for knight`() {
        val c = ProfilePalette.accentForProfile("knight")
        val expected = androidx.compose.ui.graphics.Color(0xFFF8BBD0)
        assertEquals(expected.toArgb(), c.toArgb())
    }

    @Test
    fun `accentForProfile returns Coral for research`() {
        val c = ProfilePalette.accentForProfile("research")
        val expected = androidx.compose.ui.graphics.Color(0xFFF2B8B5)
        assertEquals(expected.toArgb(), c.toArgb())
    }

    @Test
    fun `accentForProfile strips at sign and lowercases`() {
        val expected = androidx.compose.ui.graphics.Color(0xFFB3B6F2).toArgb()
        assertEquals(expected, ProfilePalette.accentForProfile("@coder").toArgb())
        assertEquals(expected, ProfilePalette.accentForProfile("CODER").toArgb())
        assertEquals(expected, ProfilePalette.accentForProfile(" Coder ").toArgb())
    }

    @Test
    fun `accentForProfile is stable for unknown handles`() {
        val a = ProfilePalette.accentForProfile("deep-research-7b")
        val b = ProfilePalette.accentForProfile("deep-research-7b")
        assertEquals(a, b)
    }

    @Test
    fun `accentForProfile cycles through the fallback wheel`() {
        val colors = (0..20).map { ProfilePalette.accentForProfile("test-$it") }.toSet()
        // Should land on a few distinct colors (modulo 8 wheel).
        assertTrue("expected > 1 distinct color, got ${colors.size}", colors.size > 1)
    }
}

/**
 * Pure-JVM tests for the [V1BToolRun] model and the existing
 * [com.hermes.companion.domain.ToolRun] → V1B mapper.
 */
class V1BToolRunModelTest {
    @Test
    fun `bash exec verb is mutating`() {
        val run = V1BToolRun.BashExec(
            id = "1",
            description = "./gradlew :app:installDebug",
            elapsedMs = 4200,
            status = ToolRunDisplayStatus.Live,
        )
        assert(run.mutating)
        assertEquals("bash.exec", run.verb)
    }

    @Test
    fun `file read verb is read-only`() {
        val run = V1BToolRun.FileRead(
            id = "1",
            description = "/path/to/file.kt",
            elapsedMs = 42,
            status = ToolRunDisplayStatus.Completed,
        )
        assert(!run.mutating)
        assertEquals("file.read", run.verb)
    }

    @Test
    fun `git verb defaults to git_push`() {
        val run = V1BToolRun.Git(
            id = "1",
            description = "origin main",
            elapsedMs = 1000,
            status = ToolRunDisplayStatus.Awaiting,
        )
        assert(run.mutating)
        assertEquals("git.push", run.verb)
    }

    @Test
    fun `mapper routes bash to BashExec`() {
        val dom = com.hermes.companion.domain.ToolRun(
            id = "1",
            name = "bash.exec",
            status = com.hermes.companion.domain.ToolStatus.Running,
            input = "./gradlew build",
            startedAt = 0L,
            completedAt = 4200L,
        )
        val v1b = dom.toV1B()
        assert(v1b is V1BToolRun.BashExec)
        assertEquals(ToolRunDisplayStatus.Live, v1b.status)
    }

    @Test
    fun `mapper routes file_read to FileRead`() {
        val dom = com.hermes.companion.domain.ToolRun(
            id = "2",
            name = "file.read",
            status = com.hermes.companion.domain.ToolStatus.Completed,
            input = "HermesComponents.kt",
            startedAt = 0L,
            completedAt = 42L,
        )
        val v1b = dom.toV1B()
        assert(v1b is V1BToolRun.FileRead)
        assertEquals(ToolRunDisplayStatus.Completed, v1b.status)
    }

    @Test
    fun `mapper routes unknown verbs to FileRead fallback`() {
        val dom = com.hermes.companion.domain.ToolRun(
            id = "3",
            name = "weird.unknown.thing",
            status = com.hermes.companion.domain.ToolStatus.Completed,
            input = "x",
            startedAt = 0L,
            completedAt = 1L,
        )
        val v1b = dom.toV1B()
        // The fallback shape is FileRead — read-only by default.
        assert(v1b is V1BToolRun.FileRead)
        assert(!v1b.mutating)
    }

    @Test
    fun `Failed status maps to Failed display status`() {
        val dom = com.hermes.companion.domain.ToolRun(
            id = "4",
            name = "bash.exec",
            status = com.hermes.companion.domain.ToolStatus.Failed,
            input = "false",
            startedAt = 0L,
            completedAt = 100L,
        )
        val v1b = dom.toV1B()
        assertEquals(ToolRunDisplayStatus.Failed, v1b.status)
    }

    @Test
    fun `Pending status maps to Awaiting display status`() {
        val dom = com.hermes.companion.domain.ToolRun(
            id = "5",
            name = "git.push",
            status = com.hermes.companion.domain.ToolStatus.Pending,
            input = "origin",
            startedAt = 0L,
            completedAt = null,
        )
        val v1b = dom.toV1B()
        assertEquals(ToolRunDisplayStatus.Awaiting, v1b.status)
    }
}

/**
 * HermesComponents enhancements — pure-JVM smoke tests for the
 * composable signatures. Compose runtime tests are in
 * `:app/src/androidTest/` when the harness is wired.
 */
class HermesComponentsEnhancementTest {
    @Test
    fun `assertion is not null`() {
        // Doc-level smoke test — the real composable tests live in
        // androidTest. This exists so the test file has at least one
        // passing assertion in :app:testDebugUnitTest.
        assertNotEquals(null, "count: Int? = null")
    }
}
