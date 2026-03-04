package com.coinlab.app.ui.theme

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

// ═══════════════════════════════════════════════════════════════════════
//  v8.9.2 — GOLD-ORANGE GLASSMORPHISM THEME
// ═══════════════════════════════════════════════════════════════════════

private val DarkColorScheme = darkColorScheme(
    primary = CoinLabGreen,                              // Bitcoin Gold
    onPrimary = Color(0xFF1A0E00),                       // Dark on gold
    primaryContainer = CoinLabGreen.copy(alpha = 0.2f),
    onPrimaryContainer = CoinLabGold,
    secondary = CoinLabAqua,                             // Warm Amber
    onSecondary = Color(0xFF1A0E00),
    secondaryContainer = CoinLabAqua.copy(alpha = 0.2f),
    onSecondaryContainer = CoinLabAqua,
    tertiary = CoinLabNeon,                              // Neon Orange
    onTertiary = Color(0xFF1A0E00),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = CoinLabRed,
    onError = Color(0xFF1A0E00),
    inverseSurface = Color(0xFFFFE0B2),
    inverseOnSurface = Color(0xFF1A0E00),
    inversePrimary = Color(0xFF8B5E00)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE68A00),                         // Slightly darker gold for light theme
    onPrimary = LightSurface,
    primaryContainer = CoinLabGreen.copy(alpha = 0.12f),
    onPrimaryContainer = Color(0xFF8B5E00),
    secondary = Color(0xFFE09000),
    onSecondary = LightSurface,
    secondaryContainer = CoinLabAqua.copy(alpha = 0.12f),
    onSecondaryContainer = Color(0xFF8B5E00),
    tertiary = CoinLabNeon,
    onTertiary = LightSurface,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = CoinLabRed,
    onError = LightSurface
)

@Composable
fun CoinLabTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

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
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CoinLabTypography,
        content = content
    )
}
