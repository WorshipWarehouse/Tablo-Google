package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TvDarkColorScheme = darkColorScheme(
    primary = TabloTeal,
    onPrimary = Color.Black,
    primaryContainer = TabloTealDark,
    onPrimaryContainer = Color.White,
    secondary = TvFocusHighlight,
    onSecondary = Color.Black,
    background = TvBackground,
    onBackground = TextPrimary,
    surface = TvSurface,
    onSurface = TextPrimary,
    surfaceVariant = TvSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = TvBorder
)

@Composable
fun TabloTvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = Typography,
        content = content
    )
}
