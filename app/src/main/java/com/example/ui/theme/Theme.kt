package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = StadiumGreenPrimary,
    onPrimary = StadiumGreenOnPrimary,
    primaryContainer = StadiumGreenContainer,
    onPrimaryContainer = StadiumGreenOnContainer,
    secondary = StadiumCyanSecondary,
    onSecondary = StadiumCyanOnSecondary,
    secondaryContainer = StadiumCyanContainer,
    onSecondaryContainer = StadiumCyanOnContainer,
    tertiary = StadiumAccentYellow,
    error = StadiumAccentRed,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = StadiumGreenContainer,
    onPrimary = Color.White,
    primaryContainer = StadiumGreenOnContainer,
    onPrimaryContainer = StadiumGreenOnPrimary,
    secondary = StadiumCyanContainer,
    onSecondary = Color.White,
    secondaryContainer = StadiumCyanOnContainer,
    onSecondaryContainer = StadiumCyanOnSecondary,
    tertiary = StadiumAccentYellow,
    error = StadiumAccentRed,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek stadium dark theme for video/sports
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

