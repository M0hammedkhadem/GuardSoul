package com.agon.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = background,
    surface = surface,
    surfaceVariant = surfaceLight,
    primary = primary,
    secondary = accent,
    error = danger,
    onBackground = text,
    onSurface = text,
    onPrimary = text,
    onSecondary = text,
    onError = text
)

@Composable
fun AgonAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
