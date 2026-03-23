package com.itdcsystems.color.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = SplashPurple,
    secondary = SplashPink,
    tertiary = SplashYellow,
    background = SplashBackground,
    surface = SplashSurface,
    onPrimary = SplashOnPrimary,
    onBackground = SplashOnBackground,
    onSurface = SplashOnBackground,
)

private val DarkColorScheme = darkColorScheme(
    primary = SplashPurpleDark,
    secondary = SplashPinkDark,
    tertiary = SplashYellow,
    background = SplashBackgroundDark,
    surface = SplashSurfaceDark,
    onPrimary = SplashOnBackgroundDark,
    onBackground = SplashOnBackgroundDark,
    onSurface = SplashOnBackgroundDark,
)

@Composable
fun ColorSplashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
