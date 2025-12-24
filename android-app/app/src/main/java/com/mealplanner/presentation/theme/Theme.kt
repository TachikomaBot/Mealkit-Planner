package com.mealplanner.presentation.theme

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
    primary = Tomato500,
    onPrimary = OnPrimary,
    primaryContainer = Tomato100,
    onPrimaryContainer = Tomato800,
    secondary = Pacific500,
    onSecondary = OnSecondary,
    secondaryContainer = Pacific100,
    onSecondaryContainer = Pacific800,
    tertiary = Mustard500,
    onTertiary = Alabaster900,
    tertiaryContainer = Mustard100,
    onTertiaryContainer = Mustard800,
    background = Ghost,
    onBackground = Alabaster900,
    surface = Surface,
    onSurface = Alabaster900,
    surfaceVariant = Alabaster100,
    onSurfaceVariant = Alabaster600,
    error = Tomato600,
    onError = OnError,
    outline = Alabaster400
)

private val DarkColorScheme = darkColorScheme(
    primary = Tomato400,
    onPrimary = Tomato900,
    primaryContainer = Tomato800,
    onPrimaryContainer = Tomato100,
    secondary = Pacific400,
    onSecondary = Pacific900,
    secondaryContainer = Pacific800,
    onSecondaryContainer = Pacific100,
    tertiary = Mustard400,
    onTertiary = Mustard900,
    tertiaryContainer = Mustard800,
    onTertiaryContainer = Mustard100,
    background = BackgroundDark,
    onBackground = Alabaster100,
    surface = SurfaceDark,
    onSurface = Alabaster100,
    surfaceVariant = Alabaster800,
    onSurfaceVariant = Alabaster400,
    error = Tomato400,
    onError = Tomato900,
    outline = Alabaster600
)

@Composable
fun MealPlannerTheme(
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
