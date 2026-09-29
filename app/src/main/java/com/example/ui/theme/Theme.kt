package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = Color(0xFF1E1300),
    primaryContainer = Color(0xFF452B00),
    onPrimaryContainer = GoldLight,
    secondary = EmeraldAccent,
    onSecondary = Color(0xFF003822),
    secondaryContainer = EmeraldDark,
    onSecondaryContainer = EmeraldGlow,
    tertiary = CyanAccent,
    onTertiary = Color(0xFF00363D),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = DarkNavySurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = CrimsonError,
    onError = Color.White
)

@Composable
fun HashGridTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // HashGrid Pro is designed as a royal dark theme experience
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
