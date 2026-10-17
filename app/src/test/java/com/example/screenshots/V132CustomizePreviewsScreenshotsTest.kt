package com.example.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.components.PomodoroDesignPreviewCard
import com.example.ui.components.TimerFaceThumbnail
import com.example.ui.theme.FocuslyTheme
import com.example.ui.timer.PomodoroStyle
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.3.2 regression set for ITEM 13: the Customize sheet's preview cards
 * cropped several clock faces (the 88dp tile shaved ~4dp per side off the
 * 96dp scaled render — FLIP's wide digits far more) and clipped the
 * Pomodoro designs (TICK_RING's NextUp row + controls overflowed the fixed
 * 360x450 render; ACTIVITY_RINGS / SESSION_STACK shaved at the bottom).
 *
 * Grid: EVERY preview (13 timer-face thumbnails + 4 pomodoro designs) in
 * app-dark AND app-light. Eyeball EVERY PNG: each face/design must render
 * FULLY inside its card — no clipping at the card bounds, no cut-off rows.
 *
 * One setContent per @Test (Compose rule restriction).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V132CustomizePreviewsScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun shot(dark: Boolean, name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (dark) Color(0xFF0B0E0B) else Color(0xFFF4F6F1)),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v132_customize_previews/$name.png"
        )
    }

    private fun faceThumb(faceId: String, dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        shot(dark, "thumb_${faceId}_$theme") {
            TimerFaceThumbnail(
                faceId = faceId,
                modifier = Modifier
                    .width(104.dp)
                    .height(138.dp)
            )
        }
    }

    private fun pomoCard(style: PomodoroStyle, dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        shot(dark, "pomo_${style.name.lowercase()}_$theme") {
            PomodoroDesignPreviewCard(
                style = style,
                isSelected = true,
                isLocked = false,
                onClick = {},
                textPrimary = if (dark) Color(0xFFF0F4ED) else Color(0xFF101410),
                cardBorder = if (dark) Color(0xFF1F1F22) else Color(0xFFE2E6DF)
            )
        }
    }

    // ── 13 timer-face thumbnails x dark/light ────────────────────────────
    @Test fun thumb_digits_dark() = faceThumb("digits", true)
    @Test fun thumb_digits_light() = faceThumb("digits", false)
    @Test fun thumb_glass_dark() = faceThumb("glass", true)
    @Test fun thumb_glass_light() = faceThumb("glass", false)
    @Test fun thumb_arc_dark() = faceThumb("arc", true)
    @Test fun thumb_arc_light() = faceThumb("arc", false)
    @Test fun thumb_editorial_dark() = faceThumb("editorial", true)
    @Test fun thumb_editorial_light() = faceThumb("editorial", false)
    @Test fun thumb_clock_orbit_dark() = faceThumb("clock_orbit", true)
    @Test fun thumb_clock_orbit_light() = faceThumb("clock_orbit", false)
    @Test fun thumb_clock_flip_dark() = faceThumb("clock_flip", true)
    @Test fun thumb_clock_flip_light() = faceThumb("clock_flip", false)
    @Test fun thumb_clock_liquid_dark() = faceThumb("clock_liquid", true)
    @Test fun thumb_clock_liquid_light() = faceThumb("clock_liquid", false)
    @Test fun thumb_clock_editorial_dark() = faceThumb("clock_editorial", true)
    @Test fun thumb_clock_editorial_light() = faceThumb("clock_editorial", false)
    @Test fun thumb_clock_dial_dark() = faceThumb("clock_dial", true)
    @Test fun thumb_clock_dial_light() = faceThumb("clock_dial", false)
    @Test fun thumb_clock_horizon_dark() = faceThumb("clock_horizon", true)
    @Test fun thumb_clock_horizon_light() = faceThumb("clock_horizon", false)
    @Test fun thumb_clock_stack_dark() = faceThumb("clock_stack", true)
    @Test fun thumb_clock_stack_light() = faceThumb("clock_stack", false)
    @Test fun thumb_clock_breathe_dark() = faceThumb("clock_breathe", true)
    @Test fun thumb_clock_breathe_light() = faceThumb("clock_breathe", false)
    @Test fun thumb_clock_bars_dark() = faceThumb("clock_bars", true)
    @Test fun thumb_clock_bars_light() = faceThumb("clock_bars", false)

    // ── 4 pomodoro designs x dark/light ──────────────────────────────────
    @Test fun pomo_tick_ring_dark() = pomoCard(PomodoroStyle.TICK_RING, true)
    @Test fun pomo_tick_ring_light() = pomoCard(PomodoroStyle.TICK_RING, false)
    @Test fun pomo_activity_rings_dark() = pomoCard(PomodoroStyle.ACTIVITY_RINGS, true)
    @Test fun pomo_activity_rings_light() = pomoCard(PomodoroStyle.ACTIVITY_RINGS, false)
    @Test fun pomo_session_stack_dark() = pomoCard(PomodoroStyle.SESSION_STACK, true)
    @Test fun pomo_session_stack_light() = pomoCard(PomodoroStyle.SESSION_STACK, false)
    @Test fun pomo_typographic_dark() = pomoCard(PomodoroStyle.TYPOGRAPHIC, true)
    @Test fun pomo_typographic_light() = pomoCard(PomodoroStyle.TYPOGRAPHIC, false)
}
