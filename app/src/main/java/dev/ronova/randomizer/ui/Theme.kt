package dev.ronova.randomizer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB9A2FF),
    onPrimary = Color(0xFF2A0F6B),
    primaryContainer = Color(0xFF4A2FA0),
    secondary = Color(0xFFFF8FBF),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF14101F),
    surface = Color(0xFF14101F),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6A3FE0),
    secondary = Color(0xFFD6336C),
    tertiary = Color(0xFFB7791F),
    background = Color(0xFFFAF7FF),
    surface = Color(0xFFFAF7FF),
)

@Composable
fun RandomizerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
