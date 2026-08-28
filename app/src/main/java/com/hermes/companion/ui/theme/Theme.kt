package com.hermes.companion.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val HermesScheme = darkColorScheme(
    primary = HermesColors.Primary,
    onPrimary = HermesColors.OnPrimary,
    secondary = HermesColors.Elevated,
    onSecondary = HermesColors.Fg,
    tertiary = HermesColors.Warn,
    onTertiary = HermesColors.OnPrimary,
    background = HermesColors.Background,
    onBackground = HermesColors.Fg,
    surface = HermesColors.Surface,
    onSurface = HermesColors.Fg,
    surfaceVariant = HermesColors.Elevated,
    onSurfaceVariant = HermesColors.Muted,
    outline = HermesColors.Border,
    outlineVariant = HermesColors.Border,
    error = HermesColors.Danger,
    onError = HermesColors.Fg,
    inverseSurface = HermesColors.Fg,
    inverseOnSurface = HermesColors.OnPrimary,
)

@Composable
fun HermesTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = 0xFF0B0B0A.toInt()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = HermesScheme,
        typography = HermesTypography,
        content = content,
    )
}
