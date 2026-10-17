package com.example.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.components.FloatingTimerMiniPill
import com.example.ui.components.FloatingTimerPill
import com.example.ui.theme.FocuslyTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.3.1 — floating timer pill assembly verify: the expanded pill and the
 * new docked mini-pill, each in dark and light theme at Pixel 8 size.
 * PNGs land in app/src/test/screenshots/floating-pill/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class FloatingPillScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun shot(dark: Boolean, name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/floating-pill/$name.png")
    }

    // ── Expanded pill (design-system styling must stay identical) ────────
    @Test
    fun pill_expanded_dark() = shot(true, "pill_expanded_dark") {
        FloatingTimerPill(
            remainingSeconds = 12 * 60 + 34,
            onReturnToApp = {},
            onDismiss = {}
        )
    }

    @Test
    fun pill_expanded_light() = shot(false, "pill_expanded_light") {
        FloatingTimerPill(
            remainingSeconds = 12 * 60 + 34,
            onReturnToApp = {},
            onDismiss = {}
        )
    }

    // ── Docked mini-pill (compact circle, lime ring, MM:SS) ─────────────
    @Test
    fun pill_docked_dark() = shot(true, "pill_docked_dark") {
        FloatingTimerMiniPill(
            remainingSeconds = 12 * 60 + 34,
            onExpand = {}
        )
    }

    @Test
    fun pill_docked_light() = shot(false, "pill_docked_light") {
        FloatingTimerMiniPill(
            remainingSeconds = 12 * 60 + 34,
            onExpand = {}
        )
    }
}
