package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Focivo unified design-system tokens (owner's Full App Redesign, 2026-10-16).
 *
 * Dark values are the exact Shield-redesign tokens from the owner's mockup:
 * bg #0A0A0B, surface #131315, line #1F1F22, text #F4F4F5, muted #76767C,
 * accent #B6F13A. Light values are the owner-approved counterpart set.
 *
 * Provided automatically by [FocuslyTheme] via [LocalFocivoTokens]; read with
 * [focivoTokens] inside any @Composable. Poppins body + Instrument Serif
 * italic for display headlines only — zero emojis, English-only UI.
 */
data class FocivoTokens(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    /** Accent used as *text* on surfaces — lime in dark, deep lime in light (contrast-safe). */
    val accentText: Color,
    /** Lime wash used behind chips / tags / soft highlights. */
    val softAccent: Color,
    val chipBorder: Color,
    val trackOff: Color,
    val thumbOff: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val scrim: Color,
    val isDark: Boolean
)

val FocivoDarkTokens = FocivoTokens(
    bg = Color(0xFF0A0A0B),
    surface = Color(0xFF131315),
    surface2 = Color(0xFF1B1B1F),
    line = Color(0xFF1F1F22),
    text = Color(0xFFF4F4F5),
    muted = Color(0xFF76767C),
    accent = Color(0xFFB6F13A),
    onAccent = Color(0xFF0A0A0B),
    accentText = Color(0xFFB6F13A),
    softAccent = Color(0x1EB6F13A),
    chipBorder = Color(0xFF2C2C31),
    trackOff = Color(0xFF26262B),
    thumbOff = Color(0xFF8B8B92),
    danger = Color(0xFFFF6B6B),
    warning = Color(0xFFF5A524),
    success = Color(0xFF34D17B),
    scrim = Color(0x99000000),
    isDark = true
)

val FocivoLightTokens = FocivoTokens(
    bg = Color(0xFFF7F7F5),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF0F0EB),
    line = Color(0xFFE7E7E3),
    text = Color(0xFF141416),
    muted = Color(0xFF76767C),
    accent = Color(0xFFB6F13A),
    onAccent = Color(0xFF0A0A0B),
    accentText = Color(0xFF4E7A00),
    softAccent = Color(0xFFEDF4D2),
    chipBorder = Color(0xFFDDE3CB),
    trackOff = Color(0xFFD8D8D2),
    thumbOff = Color(0xFFFFFFFF),
    danger = Color(0xFFE5484D),
    warning = Color(0xFFB25E09),
    success = Color(0xFF1F9D57),
    scrim = Color(0x66000000),
    isDark = false
)

val LocalFocivoTokens = compositionLocalOf { FocivoDarkTokens }

/** Read the active design-system tokens (follows app dark/light mode). */
@Composable
@ReadOnlyComposable
fun focivoTokens(): FocivoTokens = LocalFocivoTokens.current

/**
 * Instrument Serif italic — the ONLY serif in the system. Use for display
 * headlines / greeting headlines only; everything else is Poppins.
 */
val SerifItalicFontFamily = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

/** Uppercase section eyebrow: 11sp, semibold, 1.76sp tracking, muted. */
@Composable
@ReadOnlyComposable
fun focivoEyebrow(): TextStyle = MaterialTheme.typography.labelSmall.copy(
    fontFamily = PoppinsFontFamily,
    fontWeight = FontWeight.SemiBold,
    color = LocalFocivoTokens.current.muted,
    letterSpacing = 1.76.sp,
    fontSize = 11.sp
)

/** Page headline: 30sp bold, tight tracking — pair with [SerifItalicFontFamily]. */
@Composable
@ReadOnlyComposable
fun focivoHeadline(): TextStyle = MaterialTheme.typography.headlineLarge.copy(
    fontFamily = SerifItalicFontFamily,
    fontWeight = FontWeight.Bold,
    color = LocalFocivoTokens.current.text,
    fontSize = 30.sp,
    lineHeight = 35.sp,
    letterSpacing = (-0.6).sp
)
