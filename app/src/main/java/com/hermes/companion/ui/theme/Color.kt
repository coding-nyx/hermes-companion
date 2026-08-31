package com.hermes.companion.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand accents (from design/canvas.json) ──────────────────────────────
val Indigo90 = Color(0xFFE0E1FB)
val Indigo80 = Color(0xFFB3B6F2)
val Indigo40 = Color(0xFF3F51B5)
val Indigo20 = Color(0xFF2A2C57)

val Teal80 = Color(0xFF80CBC4)
val Teal40 = Color(0xFF00897B)
val Teal20 = Color(0xFF0B3E39)

val Sand80 = Color(0xFFFFCC80)
val Sand40 = Color(0xFFEF6C00)
val Sand20 = Color(0xFF4A2E05)

val Coral80 = Color(0xFFF2B8B5)
val Coral40 = Color(0xFFBA1A1A)
val CoralContainerDark = Color(0xFF5C1D1B)
val CoralContainerLight = Color(0xFFFFDAD6)

// ── Profile accent palette (Phase B) ────────────────────────────────────
// Per-profile handoff accents that aren't already in the main Brand ramp.
val Magenta80 = Color(0xFFF8BBD0)
val Magenta40 = Color(0xFFC2185B)
val Cyan80 = Color(0xFF80DEEA)
val Cyan40 = Color(0xFF00838F)
val Lime80 = Color(0xFFDCE775)
val Lime40 = Color(0xFF827717)

// ── Dark (primary look) neutrals ─────────────────────────────────────────
val NightBg = Color(0xFF15151A)
val NightSurface = Color(0xFF15151A)
val NightSurfaceContainerLowest = Color(0xFF101015)
val NightSurfaceContainerLow = Color(0xFF1A1A20)
val NightSurfaceContainer = Color(0xFF1C1C22)
val NightSurfaceContainerHigh = Color(0xFF24242C)
val NightSurfaceContainerHighest = Color(0xFF2E2E36)
val NightSurfaceVariant = Color(0xFF2A2A32)
val NightOnSurface = Color(0xFFE8E8EC)
val NightOnSurfaceVariant = Color(0xFFB6B6C2)
val NightOutline = Color(0xFF47474F)
val NightOutlineVariant = Color(0xFF33333B)

// ── Light neutrals ───────────────────────────────────────────────────────
val DayBg = Color(0xFFFAFAFC)
val DaySurface = Color(0xFFFFFFFF)
val DaySurfaceContainerLowest = Color(0xFFFFFFFF)
val DaySurfaceContainerLow = Color(0xFFF6F6FA)
val DaySurfaceContainer = Color(0xFFF1F1F7)
val DaySurfaceContainerHigh = Color(0xFFEBEBF2)
val DaySurfaceContainerHighest = Color(0xFFE5E5EE)
val DaySurfaceVariant = Color(0xFFE7E7EF)
val DayOnSurface = Color(0xFF1A1A1F)
val DayOnSurfaceVariant = Color(0xFF45454F)
val DayOutline = Color(0xFFC4C4CE)
val DayOutlineVariant = Color(0xFFE0E0E8)

// ── Shared semantic status accents ───────────────────────────────────────
// Single source of truth for coverage/queue/lease status colours, replacing
// the per-file palette duplications that previously lived in three screens.
val StatusOk = Teal80        // working / connected
val StatusWarn = Sand80      // needs a permission or role
val StatusError = Coral80    // failed / OS-limited hard block
val StatusDim = Color(0xFF8A8A96) // idle / not-applicable

// ── Theme-aware status colours (via LocalHermesStatus) ───────────────────
data class HermesStatusColors(
    val ok: Color,
    val warn: Color,
    val error: Color,
    val dim: Color,
)

val LightStatusColors = HermesStatusColors(ok = Teal40, warn = Sand40, error = Coral40, dim = Color(0xFF8A8A96))
val DarkStatusColors = HermesStatusColors(ok = Teal80, warn = Sand80, error = Coral80, dim = Color(0xFF8A8A96))

// ── Web-app design tokens (port from origin/main, additive) ───────────────
// Mirrors src/styles.css from the web companion. Use these for new UI work
// (chips, badges, buttons, cards); the existing palette above is kept for the
// pair-flow, settings, and notification screens that were designed against it.
object HermesColors {
    val Bg = Color(0xFF080807)
    val Background = Color(0xFF0B0B0A)
    val Fg = Color(0xFFF2F0EA)
    val Surface = Color(0xFF141413)
    val Elevated = Color(0xFF1C1C1A)
    val Muted = Color(0xFF9C9890)
    val Subtle = Color(0xFF6E6B64)
    val Primary = Color(0xFFE8E4D8)
    val OnPrimary = Color(0xFF0B0B0A)
    val Ok = Color(0xFF8AA37F)
    val Warn = Color(0xFFC4A574)
    val Danger = Color(0xFFC47A6A)
    val Border = Color(0x1FF2F0EA)
    val BorderHover = Color(0x3DF2F0EA)
    val NightWash = Color(0x38C4A070)
}
