package com.hermes.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo20,
    onPrimaryContainer = Indigo90,
    secondary = Teal80,
    onSecondary = Teal20,
    secondaryContainer = Teal20,
    onSecondaryContainer = Teal80,
    tertiary = Sand80,
    onTertiary = Sand20,
    tertiaryContainer = Sand20,
    onTertiaryContainer = Sand80,
    error = Coral80,
    onError = Color(0xFF601410),
    errorContainer = CoralContainerDark,
    onErrorContainer = Coral80,
    background = NightBg,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightOnSurfaceVariant,
    surfaceContainerLowest = NightSurfaceContainerLowest,
    surfaceContainerLow = NightSurfaceContainerLow,
    surfaceContainer = NightSurfaceContainer,
    surfaceContainerHigh = NightSurfaceContainerHigh,
    surfaceContainerHighest = NightSurfaceContainerHighest,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
)

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo20,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Teal20,
    tertiary = Sand40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Sand20,
    error = Coral40,
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = Color(0xFF410002),
    background = DayBg,
    onBackground = DayOnSurface,
    surface = DaySurface,
    onSurface = DayOnSurface,
    surfaceVariant = DaySurfaceVariant,
    onSurfaceVariant = DayOnSurfaceVariant,
    surfaceContainerLowest = DaySurfaceContainerLowest,
    surfaceContainerLow = DaySurfaceContainerLow,
    surfaceContainer = DaySurfaceContainer,
    surfaceContainerHigh = DaySurfaceContainerHigh,
    surfaceContainerHighest = DaySurfaceContainerHighest,
    outline = DayOutline,
    outlineVariant = DayOutlineVariant,
)

private val HermesShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Dark-first Material 3 theme. No dynamic colour: the brand palette is the
 * identity. Follows the system by default; an explicit override arrives with
 * the appearance setting (Phase 7).
 */
@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HermesTypography,
        shapes = HermesShapes,
        content = content,
    )
}
