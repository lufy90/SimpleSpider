package com.simplespider.dy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DyColorScheme = darkColorScheme(
    primary = DyAccent,
    onPrimary = Color.White,
    secondary = DyAccent2,
    onSecondary = Color.Black,
    background = DyBlack,
    surface = DySurface,
    onBackground = DyTextPrimary,
    onSurface = DyTextPrimary,
    surfaceVariant = DyCard,
    onSurfaceVariant = DyTextSecondary,
)

@Composable
fun SimpleSpiderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DyColorScheme,
        content = content,
    )
}
