package com.hermes.companion.ui.v1

import androidx.compose.ui.graphics.Color
import com.hermes.companion.ui.theme.Coral80
import com.hermes.companion.ui.theme.Indigo80
import com.hermes.companion.ui.theme.Magenta80

/**
 * Phase B · profile accent palette.
 *
 * Hard-coded by profile name (decision #3 in the locked-in spec):
 *   - coder   → Indigo (Brand)
 *   - knight  → Magenta
 *   - research→ Coral
 *   - anything else → stable hash-to-color fallback
 *
 * The fallback is stable for a given handle so a profile keeps the
 * same accent across the lifetime of the chat (no shimmer/gender).
 * No DB column — keep this a pure function.
 */
object ProfilePalette {
    private val named: Map<String, Color> = mapOf(
        "coder" to Indigo80,
        "knight" to Magenta80,
        "research" to Coral80,
    )

    // Eight additional anchors used when the profile name isn't
    // recognised. Modulo-8 keeps the wheel small and predictable.
    private val fallback = listOf(
        Indigo80,
        Magenta80,
        Color(0xFF80DEEA), // cyan-80 (matches handoff.dc.html)
        Color(0xFFDCE775), // lime-80
        Coral80,
        Color(0xFFB39DDB), // purple-80
        Color(0xFFFFAB91), // deep-orange-80
        Color(0xFFA5D6A7), // green-80
    )

    /** Accent color for a profile, keyed by profile handle / id. */
    fun accentForProfile(name: String): Color {
        val key = name.trim().trimStart('@').lowercase()
        named[key]?.let { return it }
        // Stable hash → wheel index. Simple djb2-ish sum on chars.
        var h = 5381
        for (c in key) h = ((h shl 5) + h + c.code) and 0x7FFFFFFF
        return fallback[h % fallback.size]
    }
}
