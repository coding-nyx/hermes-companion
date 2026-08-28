package com.hermes.companion.ui.theme

import androidx.compose.ui.graphics.Color

/** Tokens mirrored from the web companion `src/styles.css`. */
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
    val Paper = Color(0xFFF2F0EA)
    val PaperFg = Color(0xFF080807)
    val PaperSurface = Color(0xFFEBE8E0)
    val PaperElevated = Color(0xFFE2DED4)
}

// ── Brand accents (aliased onto the web palette so existing screens compile) ──
val Indigo90 = HermesColors.Primary
val Indigo80 = HermesColors.Primary
val Indigo40 = HermesColors.OnPrimary
val Indigo20 = HermesColors.Elevated

val Teal80 = HermesColors.Ok
val Teal40 = Color(0xFF4F6A46)
val Teal20 = Color(0xFF1A2618)

val Sand80 = HermesColors.Warn
val Sand40 = Color(0xFF8A6B3D)
val Sand20 = Color(0xFF3A2E18)

val Coral80 = HermesColors.Danger
val Coral40 = Color(0xFF8A4A3E)
val CoralContainerDark = Color(0xFF3A221E)
val CoralContainerLight = Color(0xFFE8D4CE)

// ── Dark (primary look) neutrals ─────────────────────────────────────────
val NightBg = HermesColors.Background
val NightSurface = HermesColors.Surface
val NightSurfaceContainerLowest = HermesColors.Bg
val NightSurfaceContainerLow = HermesColors.Surface
val NightSurfaceContainer = HermesColors.Surface
val NightSurfaceContainerHigh = HermesColors.Elevated
val NightSurfaceContainerHighest = Color(0xFF242422)
val NightSurfaceVariant = HermesColors.Elevated
val NightOnSurface = HermesColors.Fg
val NightOnSurfaceVariant = HermesColors.Muted
val NightOutline = HermesColors.BorderHover
val NightOutlineVariant = HermesColors.Border

// ── Light neutrals (paper cream, not Material grey) ──────────────────────
val DayBg = HermesColors.Paper
val DaySurface = HermesColors.PaperSurface
val DaySurfaceContainerLowest = Color(0xFFF7F5EF)
val DaySurfaceContainerLow = HermesColors.PaperSurface
val DaySurfaceContainer = HermesColors.PaperSurface
val DaySurfaceContainerHigh = HermesColors.PaperElevated
val DaySurfaceContainerHighest = Color(0xFFD8D4C8)
val DaySurfaceVariant = HermesColors.PaperElevated
val DayOnSurface = HermesColors.PaperFg
val DayOnSurfaceVariant = HermesColors.Subtle
val DayOutline = Color(0x33080807)
val DayOutlineVariant = Color(0x1A080807)

// ── Shared semantic status accents ───────────────────────────────────────
val StatusOk = HermesColors.Ok
val StatusWarn = HermesColors.Warn
val StatusError = HermesColors.Danger
val StatusDim = HermesColors.Subtle

data class HermesStatusColors(
    val ok: Color,
    val warn: Color,
    val error: Color,
    val dim: Color,
)

val LightStatusColors = HermesStatusColors(
    ok = Teal40,
    warn = Sand40,
    error = Coral40,
    dim = HermesColors.Subtle,
)
val DarkStatusColors = HermesStatusColors(
    ok = HermesColors.Ok,
    warn = HermesColors.Warn,
    error = HermesColors.Danger,
    dim = HermesColors.Subtle,
)
