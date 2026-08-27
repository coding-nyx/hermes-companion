package com.hermes.companion.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    @Test
    fun `stream full passes content through`() {
        val r = Redactor.apply(StreamMode.StreamFull, "Priya", "are we on for 7?")
        assertTrue(r.send)
        assertEquals("Priya", r.title)
        assertEquals("are we on for 7?", r.preview)
    }

    @Test
    fun `summarise masks digits`() {
        val r = Redactor.apply(StreamMode.Summarise, "Meter 4821", "reading 100")
        assertTrue(r.send)
        assertFalse(r.title.any { it.isDigit() })
        assertFalse(r.preview.any { it.isDigit() })
    }

    @Test
    fun `count only sends no content`() {
        val r = Redactor.apply(StreamMode.CountOnly, "Secret", "body")
        assertTrue(r.send && r.countOnly)
        assertEquals("", r.title)
    }

    @Test
    fun `ignore never leaves`() {
        assertFalse(Redactor.apply(StreamMode.Ignore, "x", "y").send)
    }

    @Test
    fun `sensitive categories are forced to metadata even when the rule says stream full`() {
        val otp = Redactor.apply(StreamMode.StreamFull, "Your OTP is 123456", "do not share")
        assertTrue(otp.send)
        assertTrue(otp.countOnly)   // lowered to count-only
        assertEquals("", otp.title) // no content leaves
        val bank = Redactor.apply(StreamMode.Summarise, "Bank alert", "payment of 500 to X")
        assertTrue(bank.countOnly)
    }

    @Test
    fun `a per-source rule can lower but never raise a sensitive event`() {
        // Ignore on a sensitive source still means nothing leaves.
        assertFalse(Redactor.apply(StreamMode.Ignore, "OTP 999", "").send)
    }
}
