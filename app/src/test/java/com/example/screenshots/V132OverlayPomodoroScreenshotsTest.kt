package com.example.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.components.FloatingTimerMiniPill
import com.example.ui.components.FloatingTimerPill
import com.example.ui.theme.FocuslyTheme
import com.example.ui.timer.PomoPhase
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
 * v1.3.2 regression set for ITEM 14: the floating overlay ignored the
 * Pomodoro design — it always showed the lime "Time remaining" pill even
 * mid-pomodoro. Now the pill reflects the selected design's phase language
 * in real time:
 *  - accent circle + subtitle in the CURRENT phase color
 *    (Focus = lime, Short break = mint, Long break = sand)
 *  - phase-appropriate label: "FOCUS · SESSION 2 OF 4" /
 *    "SHORT BREAK · 1 OF 4 DONE"
 *  - slim whole-cycle progress bar under the label
 *  - Typographic renders its phase label in Instrument Serif italic
 *  - the docked mini-pill's ring takes the phase color
 *
 * The overlay host is always dark (FocuslyTheme(darkTheme = true)), so this
 * grid is dark-only by design. The pill's countdown + phase state are fed
 * from the same params the manager passes after its per-second prefs
 * re-read; phase flips flow through automatically.
 *
 * One setContent per @Test (Compose rule restriction).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V132OverlayPomodoroScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0E0B)),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v132_overlay_pomodoro/$name.png"
        )
    }

    private fun expandedPill(
        phase: PomoPhase?,
        subtitle: String?,
        progress: Float?,
        style: PomodoroStyle,
        name: String
    ) = shot(name) {
        FloatingTimerPill(
            remainingSeconds = 17 * 60 + 42,
            onReturnToApp = {},
            onDismiss = {},
            pomoPhase = phase,
            pomoSubtitle = subtitle,
            pomoCycleProgress = progress,
            pomoStyle = style
        )
    }

    // ── Classic timer (unchanged control) ─────────────────────────────────
    @Test
    fun pill_classic() = expandedPill(
        phase = null, subtitle = null, progress = null,
        style = PomodoroStyle.TICK_RING, name = "pill_classic"
    )

    // ── Pomodoro phases, expanded pill ────────────────────────────────────
    @Test
    fun pill_focus() = expandedPill(
        phase = PomoPhase.FOCUS,
        subtitle = "FOCUS · SESSION 2 OF 4",
        progress = 0.31f,
        style = PomodoroStyle.TICK_RING,
        name = "pill_pomo_focus"
    )

    @Test
    fun pill_short_break() = expandedPill(
        phase = PomoPhase.SHORT,
        subtitle = "SHORT BREAK · 1 OF 4 DONE",
        progress = 0.38f,
        style = PomodoroStyle.ACTIVITY_RINGS,
        name = "pill_pomo_short_break"
    )

    @Test
    fun pill_long_break() = expandedPill(
        phase = PomoPhase.LONG,
        subtitle = "LONG BREAK · 4 OF 4 DONE",
        progress = 0.97f,
        style = PomodoroStyle.SESSION_STACK,
        name = "pill_pomo_long_break"
    )

    @Test
    fun pill_typographic_style() = expandedPill(
        phase = PomoPhase.FOCUS,
        subtitle = "FOCUS · SESSION 3 OF 4",
        progress = 0.56f,
        style = PomodoroStyle.TYPOGRAPHIC,
        name = "pill_pomo_typographic"
    )

    // ── Docked mini-pill: classic ring + phase-colored rings ──────────────
    private fun miniPill(phase: PomoPhase?, name: String) = shot(name) {
        FloatingTimerMiniPill(
            remainingSeconds = 17 * 60 + 42,
            onExpand = {},
            phaseColor = phase?.color
        )
    }

    @Test fun mini_classic() = miniPill(null, "mini_classic")
    @Test fun mini_focus() = miniPill(PomoPhase.FOCUS, "mini_pomo_focus")
    @Test fun mini_short_break() = miniPill(PomoPhase.SHORT, "mini_pomo_short_break")
    @Test fun mini_long_break() = miniPill(PomoPhase.LONG, "mini_pomo_long_break")
}
