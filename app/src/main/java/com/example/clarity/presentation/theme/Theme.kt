package com.example.clarity.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Primary,
    onPrimaryContainer = Secondary,
    secondary = Secondary,
    onSecondary = Neutral,
    secondaryContainer = Tertiary,
    onSecondaryContainer = Secondary,
    tertiary = Tertiary,
    onTertiary = Color.White,
    tertiaryContainer = Primary,
    onTertiaryContainer = Secondary,
    background = Neutral,
    onBackground = Secondary,
    surface = Neutral,
    onSurface = Secondary,
    surfaceVariant = Primary.copy(alpha = 0.25f),
    onSurfaceVariant = Secondary
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Secondary,
    onPrimaryContainer = Neutral,
    secondary = Secondary,
    onSecondary = Neutral,
    secondaryContainer = Secondary,
    onSecondaryContainer = Neutral,
    tertiary = Tertiary,
    onTertiary = Color.White,
    tertiaryContainer = Secondary,
    onTertiaryContainer = Neutral,
    onBackground = Neutral,
    onSurface = Neutral,
    surfaceVariant = Secondary,
    onSurfaceVariant = Neutral
)

@Composable
fun ClarityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
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
