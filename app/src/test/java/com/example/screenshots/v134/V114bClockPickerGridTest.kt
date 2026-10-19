package com.example.screenshots.v134

import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.test.onRoot
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1.4b ITEM A — full picker evidence for the true scale-to-fit thumbnails.
 *
 * Renders the REAL TimerFacePicker (the "Timer Style" row from Customize
 * Timer) and captures it at three EXACT scroll offsets so ALL NINE clock
 * styles appear FULLY in frame across the shots:
 *   start (464dp): Orbit / Flip / Liquid
 *   mid   (812dp): Editorial Clock / Dial / Horizon
 *   end   (1117dp, clamped max): Stack / Breathe / Bars
 * (Card pitch = 104dp card + 12dp spacing = 116dp; Orbit is face index 4.)
 * Dark + light at Pixel 8.
 *
 * Eyeball EVERY tile in EVERY PNG: the FULL clock (time + ring/cards/
 * digits) must be visible inside its tile with breathing room — no face
 * clipped, cropped, wrapped or overflowing the tile bounds.
 *
 * PNGs land in app/src/test/screenshots/v134/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V114bClockPickerGridTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val scrollState = ScrollState(0)

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
                    scrollState = scrollState,
                    isAdmin = true
                )
            }
        }
    }

    private fun captureAt(name: String, offsetDp: Int) {
        val px = with(composeTestRule.density) { offsetDp.dp.roundToPx() }
        runBlocking { scrollState.scrollTo(px) }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v134/$name.png"
        )
    }

    private fun runAll(prefix: String) {
        captureAt("${prefix}_picker_start", 464)
        captureAt("${prefix}_picker_mid", 812)
        captureAt("${prefix}_picker_end", 1160)
    }

    @Test
    fun grid_dark() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) { content()() }
        }
        runAll("v114b_dark")
    }

    @Test
    fun grid_light() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = false) { content()() }
        }
        runAll("v114b_light")
    }
}
