package com.example.screenshots.v134

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.ui.components.TimerFacePicker
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.focivoTokens
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.3.4 ITEM 7 — clock-style picker zoom check.
 *
 * Renders the REAL TimerFacePicker (the "Timer Style" row from Customize
 * Timer) pre-scrolled to the clock styles (Orbit + Flip — Flip has the
 * widest digits), in app-dark and app-light at Pixel 8.
 *
 * Eyeball EVERY PNG: each clock thumbnail must show the FULL clock with
 * clear breathing room inside its tile — no face cropped/zoomed at the
 * tile bounds. The v1.3.4 fix renders the 300dp clock at 88dp inside the
 * 96dp tile (was an edge-to-edge 96dp exact fit that kissed the
 * rounded-corner clip).
 *
 * PNGs land in app/src/test/screenshots/v134/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V134ClockPickerScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun content(): @Composable () -> Unit = {
        val t = focivoTokens()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(t.bg)
        ) {
            ThemedScreenBackground()
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Timer Style",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = t.text
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                // isAdmin = true: every face unlocked, so the clock styles
                // render without lock overlays.
                TimerFacePicker(
                    focusPoints = 0,
                    selectedFaceId = "clock_orbit",
                    onFaceTap = { _, _ -> },
                    textPrimary = t.text,
                    cardBorder = t.line,
                    isAdmin = true
                )
            }
        }
    }

    private fun capture(name: String) {
        composeTestRule.waitForIdle()
        // Bring the clock styles into view (they sit after the 4 classic faces).
        composeTestRule
            .onNodeWithText("Orbit", useUnmergedTree = true)
            .performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v134/$name.png"
        )
    }

    @Test
    fun clockPicker_dark() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) { content()() }
        }
        capture("clock_picker_dark")
    }

    @Test
    fun clockPicker_light() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = false) { content()() }
        }
        capture("clock_picker_light")
    }
}
