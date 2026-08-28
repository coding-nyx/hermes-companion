package com.hermes.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = HermesColors.Primary,
    onPrimary = HermesColors.OnPrimary,
    primaryContainer = HermesColors.Elevated,
    onPrimaryContainer = HermesColors.Fg,
    secondary = HermesColors.Ok,
    onSecondary = HermesColors.OnPrimary,
    secondaryContainer = Color(0xFF1A2618),
    onSecondaryContainer = HermesColors.Ok,
    tertiary = HermesColors.Warn,
    onTertiary = HermesColors.OnPrimary,
    tertiaryContainer = Color(0xFF3A2E18),
    onTertiaryContainer = HermesColors.Warn,
    error = HermesColors.Danger,
    onError = HermesColors.Fg,
    errorContainer = CoralContainerDark,
    onErrorContainer = HermesColors.Danger,
    background = HermesColors.Background,
    onBackground = HermesColors.Fg,
    surface = HermesColors.Surface,
    onSurface = HermesColors.Fg,
    surfaceVariant = HermesColors.Elevated,
    onSurfaceVariant = HermesColors.Muted,
    surfaceContainerLowest = HermesColors.Bg,
    surfaceContainerLow = HermesColors.Surface,
    surfaceContainer = HermesColors.Surface,
    surfaceContainerHigh = HermesColors.Elevated,
    surfaceContainerHighest = Color(0xFF242422),
    outline = HermesColors.BorderHover,
    outlineVariant = HermesColors.Border,
)

private val LightColors = lightColorScheme(
    primary = HermesColors.OnPrimary,
    onPrimary = HermesColors.Paper,
    primaryContainer = HermesColors.PaperElevated,
    onPrimaryContainer = HermesColors.PaperFg,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E0D1),
    onSecondaryContainer = Teal20,
    tertiary = Sand40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8DCC8),
    onTertiaryContainer = Sand20,
    error = Coral40,
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = Color(0xFF410002),
    background = HermesColors.Paper,
    onBackground = HermesColors.PaperFg,
    surface = HermesColors.PaperSurface,
    onSurface = HermesColors.PaperFg,
    surfaceVariant = HermesColors.PaperElevated,
    onSurfaceVariant = HermesColors.Subtle,
    surfaceContainerLowest = Color(0xFFF7F5EF),
    surfaceContainerLow = HermesColors.PaperSurface,
    surfaceContainer = HermesColors.PaperSurface,
    surfaceContainerHigh = HermesColors.PaperElevated,
    surfaceContainerHighest = Color(0xFFD8D4C8),
    outline = Color(0x33080807),
    outlineVariant = Color(0x1A080807),
)

private val HermesShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Theme-aware status colours; read via LocalHermesStatus.current. */
val LocalHermesStatus = staticCompositionLocalOf { DarkStatusColors }

@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = if (darkTheme) 0xFF0B0B0A.toInt() else 0xFFF2F0EA.toInt()
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalHermesStatus provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HermesTypography,
            shapes = HermesShapes,
            content = content,
        )
    }
}
