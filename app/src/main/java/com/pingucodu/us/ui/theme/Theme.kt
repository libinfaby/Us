package com.pingucodu.us.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// This is a deliberately branded, flat-color design (not Material You) - no dynamic color,
// no dark theme for v1. The prototype only designs a single light look.
private val PinguCoduColorScheme = lightColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Ink,
    tertiary = Yellow,
    onTertiary = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = PinkTint,
    onSurfaceVariant = Ink,
    error = Coral,
    onError = Color.White,
    outline = Ink,
)

@Composable
fun UsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PinguCoduColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
