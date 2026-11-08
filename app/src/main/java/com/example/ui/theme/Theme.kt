package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SteelBlueAccent,
    onPrimary = ObsidianBlack,
    primaryContainer = GunmetalElevated,
    onPrimaryContainer = ChromeWhite,
    secondary = ChromeWhite,
    onSecondary = ObsidianBlack,
    secondaryContainer = GunmetalSurface,
    onSecondaryContainer = ChromeWhite,
    tertiary = AmberWarning,
    background = ObsidianBlack,
    onBackground = ChromeWhite,
    surface = GunmetalSurface,
    onSurface = ChromeWhite,
    surfaceVariant = GunmetalElevated,
    onSurfaceVariant = MutedSlate,
    error = CrimsonTrash,
    onError = Color.White
)

@Composable
fun SoyPicsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
