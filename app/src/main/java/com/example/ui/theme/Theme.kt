package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SceneCastDarkColorScheme = darkColorScheme(
    primary = StudioAccentViolet,
    onPrimary = StudioTextPrimary,
    primaryContainer = StudioElevatedBg,
    onPrimaryContainer = StudioTextPrimary,
    secondary = StudioAccentSky,
    onSecondary = StudioMainBg,
    secondaryContainer = StudioCardBg,
    onSecondaryContainer = StudioAccentSky,
    tertiary = StudioSuccessGreen,
    onTertiary = StudioMainBg,
    error = StudioLiveRed,
    onError = StudioTextPrimary,
    background = StudioMainBg,
    onBackground = StudioTextPrimary,
    surface = StudioSecondaryBg,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioCardBg,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioBorder
)

@Composable
fun ApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SceneCastDarkColorScheme,
        typography = Typography,
        content = content
    )
}
