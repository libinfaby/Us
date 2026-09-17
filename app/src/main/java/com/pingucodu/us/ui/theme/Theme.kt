package com.pingucodu.us.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.ui.settings.SettingsViewModel

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
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val fontScaleLevel by settingsViewModel.fontScaleLevel.collectAsState()
    val baseDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(density = baseDensity.density, fontScale = fontScaleLevel.scale),
    ) {
        MaterialTheme(
            colorScheme = PinguCoduColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
