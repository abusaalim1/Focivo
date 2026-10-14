package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * v1.4.0-round2: full-screen theme accent.
 *
 * Provided once at the main shell from TimerThemeManager.selectedTheme.
 * Screens read LocalAppAccent.current for primary buttons, active nav,
 * selected chips. Aurora's accent == RegainLimePrimary, so the default
 * look is unchanged.
 */
val LocalAppAccent = compositionLocalOf { RegainLimePrimary }

/** Dark content color that stays readable on every theme accent. */
val OnAccentDark = Color(0xFF061505)

/**
 * Round-3: translucent card surface for the themed main screens.
 *
 * In dark mode the cards become translucent dark so the full-bleed theme
 * backdrop breathes through, while white text keeps its contrast.
 * Light mode keeps opaque white.
 */
@Composable
fun themedCardBackground(): Color {
    val isDark = isAppInDarkTheme()
    return if (isDark) Color(0xB3121A16) else Color.White
}
