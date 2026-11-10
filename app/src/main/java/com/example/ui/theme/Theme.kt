package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    outline = TitaniumBorder,
    error = CrimsonTrash,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E60D5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF00326A),
    secondary = Color(0xFF334155),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = AmberWarning,
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EEF5),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = CrimsonTrash,
    onError = Color.White
)

@Composable
fun SoyPicsTheme(
    themeMode: String = "DARK", // "DARK", "LIGHT", "SYSTEM"
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> systemInDark
        else -> true // "DARK" default
    }

    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
