package com.hermes.companion.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hermes.companion.R

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
)

val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
    Font(R.font.instrument_serif_regular, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/** Metadata (routes, digests, seq numbers) render in mono, per the design. */
val HermesMono: FontFamily = PlexMono

val HermesTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 38.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1.5).sp,
        color = HermesColors.Fg,
    ),
    displayMedium = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.8).sp,
        color = HermesColors.Fg,
    ),
    titleLarge = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.6).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp,
    ),
)

object HermesType {
    val kicker = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.8.sp,
        color = HermesColors.Muted,
    )
    val kickerSubtle = kicker.copy(color = HermesColors.Subtle)
    val mono = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = HermesColors.Fg,
    )
    val code = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 2.4.sp,
        color = HermesColors.Fg,
    )
    val tab = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
    )
}
