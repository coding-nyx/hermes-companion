package com.hermes.companion.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Phase 1 RED tests for VoiceConfig. The file-backed JSON store is the same
 * pattern as ActiveGatewayConfig — :app writes voice.json from Settings,
 * :node voice services read on every reconnect. Defaults must match the
 * design: Android STT, Android TTS, no wake-word yet.
 *
 * readSync/writeSync are synchronous file I/O — no runTest needed since
 * :core:common doesn't pull in kotlinx-coroutines-test.
 */
class VoiceConfigTest {

    @Test
    fun `readSync returns defaults when no file exists`() {
        val dir = Files.createTempDirectory("voice-cfg-test").toFile()
        val snap = VoiceConfig.readSync(dir)
        assertFalse("wake-word should be off by default in Phase 1", snap.wakeEnabled)
        assertEquals("hey hermes", snap.wakePhrase)
        assertEquals("android", snap.sttEngine)
        assertEquals("android", snap.ttsEngine)
        assertEquals("", snap.ttsVoice)
        assertEquals("none", snap.pttHotkey)
    }

    @Test
    fun `writeSync then readSync returns the same snapshot`() {
        val dir = Files.createTempDirectory("voice-cfg-test").toFile()
        val out = VoiceConfig.VoiceSnapshot(
            wakeEnabled = true,
            wakePhrase = "yo hermes",
            sttEngine = "android",
            ttsEngine = "minimax",
            ttsVoice = "ash",
            pttHotkey = "headset",
        )
        VoiceConfig.writeSync(dir, out)
        val read = VoiceConfig.readSync(dir)
        assertTrue(read.wakeEnabled)
        assertEquals("yo hermes", read.wakePhrase)
        assertEquals("minimax", read.ttsEngine)
        assertEquals("ash", read.ttsVoice)
        assertEquals("headset", read.pttHotkey)
    }

    @Test
    fun `escaped special characters in wake phrase round-trip cleanly`() {
        val dir = Files.createTempDirectory("voice-cfg-test").toFile()
        val tricky = "hi \"hermes\"\nnewline"
        VoiceConfig.writeSync(
            dir,
            VoiceConfig.VoiceSnapshot(
                wakeEnabled = false,
                wakePhrase = tricky,
                sttEngine = "android",
                ttsEngine = "android",
                ttsVoice = "",
                pttHotkey = "none",
            ),
        )
        val read = VoiceConfig.readSync(dir)
        assertEquals(tricky, read.wakePhrase)
    }

    @Test
    fun `malformed file returns defaults, does not throw`() {
        val dir = Files.createTempDirectory("voice-cfg-test").toFile()
        File(dir, "voice.json").writeText("{not really json")
        val snap = VoiceConfig.readSync(dir)
        assertEquals(VoiceConfig.DEFAULT_VOICE, snap)
    }
}
