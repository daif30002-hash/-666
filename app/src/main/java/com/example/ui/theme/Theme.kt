package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MinimalEmerald600,
    secondary = MinimalAmber400,
    tertiary = MinimalBlue600,
    background = MinimalSlate950,
    surface = MinimalSlate900,
    onPrimary = Color.White,
    onSecondary = MinimalSlate950,
    onBackground = MinimalSlate50,
    onSurface = MinimalSlate50,
    primaryContainer = Color(0xFF064E3B),
    secondaryContainer = MinimalAmber900,
    tertiaryContainer = Color(0xFF1E3A8A),
    outlineVariant = MinimalSlate800
)

private val LightColorScheme = lightColorScheme(
    primary = MinimalEmerald800,
    secondary = MinimalEmerald600,
    tertiary = MinimalBlue600,
    background = MinimalSlate50,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = MinimalSlate900,
    onSurface = MinimalSlate900,
    primaryContainer = MinimalEmerald50,
    secondaryContainer = MinimalAmber100,
    tertiaryContainer = MinimalBlue50,
    outlineVariant = MinimalSlate200
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
