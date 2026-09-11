package com.omnitex.twinlab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TwinLabColors = lightColorScheme(
    primary = TwinLabTeal,
    onPrimary = Color.White,
    secondary = TwinLabNavy,
    onSecondary = Color.White,
    tertiary = TwinLabTeal,
    background = TwinLabBackground,
    onBackground = TwinLabOnBackground,
    surface = TwinLabSurface,
    onSurface = TwinLabOnBackground,
    surfaceVariant = TwinLabSurfaceVariant,
    onSurfaceVariant = TwinLabOutline,
    outline = TwinLabOutline,
    error = StatusCritical,
)

@Composable
fun TwinLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TwinLabColors,
        typography = TwinLabTypography,
        content = content,
    )
}
