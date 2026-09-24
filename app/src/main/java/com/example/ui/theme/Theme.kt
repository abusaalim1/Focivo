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
    primary = RegainLimePrimary,
    onPrimary = Color(0xFF021207),
    primaryContainer = RegainLimeContainer,
    onPrimaryContainer = RegainLimeDeepText,
    secondary = RegainLimeLight,
    onSecondary = NearBlack,
    tertiary = SoftPastelTealText,
    background = RegainBgTop,
    onBackground = NearBlack,
    surface = PureWhite,
    onSurface = NearBlack,
    surfaceVariant = RegainLimeContainer,
    onSurfaceVariant = SecondaryTextLight,
    outline = MutedBorderLight,
    outlineVariant = Color(0x0F000000)
)

private val RegainDarkThemeColorScheme = darkColorScheme(
    primary = RegainLimePrimary,
    onPrimary = PureWhite,
    primaryContainer = Color(0xFF25331A),
    onPrimaryContainer = RegainLimeLight,
    secondary = RegainLimeLight,
    onSecondary = PureWhite,
    tertiary = SoftPastelTealText,
    background = Color(0xFF111411),
    onBackground = Color(0xFFF0F4ED),
    surface = Color(0xFF1D221C),
    onSurface = Color(0xFFF0F4ED),
    surfaceVariant = Color(0xFF262C24),
    onSurfaceVariant = Color(0xFFA0A89E),
    outline = Color(0xFF333A31),
    outlineVariant = Color(0x20FFFFFF)
)

@Composable
fun FocuslyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) RegainDarkThemeColorScheme else RegainLightThemeColorScheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

