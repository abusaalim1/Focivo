package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun isAppInDarkTheme(): Boolean {
    return LocalIsDarkTheme.current
}

private val RegainLightThemeColorScheme = lightColorScheme(
    primary = FocivoLightTokens.accent,
    onPrimary = FocivoLightTokens.onAccent,
    primaryContainer = FocivoLightTokens.softAccent,
    onPrimaryContainer = FocivoLightTokens.accentText,
    secondary = FocivoLightTokens.accentText,
    onSecondary = FocivoLightTokens.onAccent,
    tertiary = FocivoLightTokens.muted,
    background = FocivoLightTokens.bg,
    onBackground = FocivoLightTokens.text,
    surface = FocivoLightTokens.surface,
    onSurface = FocivoLightTokens.text,
    surfaceVariant = FocivoLightTokens.surface2,
    onSurfaceVariant = FocivoLightTokens.muted,
    surfaceContainerLowest = FocivoLightTokens.bg,
    surfaceContainerLow = FocivoLightTokens.surface,
    surfaceContainer = FocivoLightTokens.surface2,
    surfaceContainerHigh = FocivoLightTokens.surface2,
    outline = FocivoLightTokens.line,
    outlineVariant = FocivoLightTokens.line,
    error = FocivoLightTokens.danger,
    onError = Color.White
)

private val RegainDarkThemeColorScheme = darkColorScheme(
    primary = FocivoDarkTokens.accent,
    onPrimary = FocivoDarkTokens.onAccent,
    primaryContainer = FocivoDarkTokens.softAccent,
    onPrimaryContainer = FocivoDarkTokens.accentText,
    secondary = FocivoDarkTokens.accentText,
    onSecondary = FocivoDarkTokens.onAccent,
    tertiary = FocivoDarkTokens.muted,
    background = FocivoDarkTokens.bg,
    onBackground = FocivoDarkTokens.text,
    surface = FocivoDarkTokens.surface,
    onSurface = FocivoDarkTokens.text,
    surfaceVariant = FocivoDarkTokens.surface2,
    onSurfaceVariant = FocivoDarkTokens.muted,
    surfaceContainerLowest = FocivoDarkTokens.bg,
    surfaceContainerLow = FocivoDarkTokens.surface,
    surfaceContainer = FocivoDarkTokens.surface2,
    surfaceContainerHigh = FocivoDarkTokens.surface2,
    outline = FocivoDarkTokens.line,
    outlineVariant = FocivoDarkTokens.line,
    error = FocivoDarkTokens.danger,
    onError = FocivoDarkTokens.bg
)

@Composable
fun FocuslyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) RegainDarkThemeColorScheme else RegainLightThemeColorScheme

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalFocivoTokens provides if (darkTheme) FocivoDarkTokens else FocivoLightTokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

