package com.example

import com.example.util.TimerThemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.pow

/**
 * v1.3.3 ITEM 9 — caption contrast sweep (unit side).
 *
 * Guarantees the caption color token used by every caption/footnote/hint
 * drawn on a theme backdrop resolves against the token system — full-alpha
 * [TimerTheme.backdropContent] per backdrop per app mode — and that no
 * caption composable can smuggle in a hardcoded alpha-reduced color:
 *
 *  1. For EVERY theme in [TimerThemeManager.THEMES] x {dark, light}:
 *     [TimerTheme.backdropCaptionContent] == [TimerTheme.backdropContent]
 *     with alpha exactly 1.0 (any copy(alpha < 1f) at a call site would be
 *     a different Color value and is caught by code review + the sweep
 *     screenshots; the token itself can never be low-contrast).
 *  2. The resolved caption color hits >= 4.5:1 WCAG contrast against the
 *     representative mid-screen luminance of its backdrop (caption zone;
 *     ambient themes carry the 60%-black readability scrim, Aurora keeps
 *     its own canvas). Values are documented approximations — the
 *     Roborazzi sweep (V133CaptionSweepScreenshotsTest) is the visual proof.
 */
@RunWith(JUnit4::class)
class BackdropCaptionContrastTest {

    // Representative caption-zone background luminances (0..1), documented
    // from the backdrop definitions: AmbientBackdrop gradient mid-stops +
    // the ThemedScreenBackground 0x99 readability scrim; AuroraBackground
    // light/dark canvas mid-stops (no scrim on Aurora).
    private val backdropLuminance: Map<String, Double> = mapOf(
        "aurora" to 0.0, // placeholder: aurora differs per app mode (see below)
        "sakura_drift" to 0.006,
        "firefly_forest" to 0.004,
        "sunset_dunes" to 0.005,
        "aurora_veil" to 0.004,
        "monsoon_rain" to 0.006,
        "ember_rise" to 0.005
    )
    private val auroraLightLuminance = 0.93   // #F7FDF1-ish canvas
    private val auroraDarkLuminance = 0.012  // #161A15-ish canvas

    private fun luminance(color: androidx.compose.ui.graphics.Color): Double {
        fun lin(s: Double): Double =
            if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        return 0.2126 * lin(color.red.toDouble()) +
                0.7152 * lin(color.green.toDouble()) +
                0.0722 * lin(color.blue.toDouble())
    }

    private fun contrastRatio(l1: Double, l2: Double): Double {
        val (hi, lo) = if (l1 > l2) l1 to l2 else l2 to l1
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Every theme x mode: caption token == full-alpha backdrop content token. */
    @Test
    fun captionToken_resolvesToFullAlphaBackdropContent_forEveryThemeAndMode() {
        for (theme in TimerThemeManager.THEMES) {
            for (isDark in listOf(true, false)) {
                val caption = theme.backdropCaptionContent(isDark)
                val content = theme.backdropContent(isDark)
                assertEquals(
                    "caption token diverged from backdropContent for theme=${theme.id} isDark=$isDark",
                    content, caption
                )
                assertEquals(
                    "caption token must be full alpha for theme=${theme.id} isDark=$isDark",
                    1f, caption.alpha, 0.0f
                )
            }
        }
    }

    /** The resolved caption color is the designed on-backdrop token, never a hardcoded hex. */
    @Test
    fun captionToken_matchesDesignedOnBackdropTokens() {
        for (theme in TimerThemeManager.THEMES) {
            val lightCaption = theme.backdropCaptionContent(false)
            assertEquals(
                "theme=${theme.id}: light caption must be the theme's onBackdropContent token",
                theme.onBackdropContent, lightCaption
            )
            val darkCaption = theme.backdropCaptionContent(true)
            val expectedDark = theme.onBackdropContentDark ?: theme.onBackdropContent
            assertEquals(
                "theme=${theme.id}: dark caption must be the theme's onBackdropContentDark/onBackdropContent token",
                expectedDark, darkCaption
            )
        }
    }

    /** Resolved caption colors clear 4.5:1 against their backdrops (small-text floor). */
    @Test
    fun captionToken_meetsContrastFloor_onEveryBackdrop() {
        for (theme in TimerThemeManager.THEMES) {
            for (isDark in listOf(true, false)) {
                val caption = theme.backdropCaptionContent(isDark)
                val bgLum = if (theme.id == "aurora") {
                    if (isDark) auroraDarkLuminance else auroraLightLuminance
                } else {
                    backdropLuminance[theme.id]
                        ?: error("no representative luminance for theme ${theme.id}")
                }
                val ratio = contrastRatio(luminance(caption), bgLum)
                assertTrue(
                    "theme=${theme.id} isDark=$isDark: caption contrast ${"%.2f".format(ratio)}:1 < 4.5:1",
                    ratio >= 4.5
                )
            }
        }
    }
}
