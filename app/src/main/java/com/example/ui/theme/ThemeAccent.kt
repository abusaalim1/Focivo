package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import com.example.util.TimerThemeManager

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

/**
 * v1.4.2: content color for text drawn directly on the full-bleed theme
 * backdrop (screen titles, section labels, top-of-screen text). Follows the
 * selected theme — dark scenes get light text, the light Aurora canvas gets
 * dark text — instead of the app dark/light mode, which is what made titles
 * unreadable when the two disagreed.
 */
@Composable
fun backdropContentColor(): Color {
    val theme by TimerThemeManager.selectedTheme.collectAsState()
    return theme.onBackdropContent
}

/**
 * v1.4.2: subtle drop shadow that keeps light title text crisp on
 * photographic backdrops. Returns null for dark text (no shadow needed on
 * the light Aurora canvas).
 */
fun backdropTitleShadow(content: Color): Shadow? =
    if (content.luminance() < 0.5f)
        Shadow(color = Color(0x66000000), offset = Offset(0f, 2f), blurRadius = 8f)
    else null
