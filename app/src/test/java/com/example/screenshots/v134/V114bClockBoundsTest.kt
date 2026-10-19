package com.example.screenshots.v134

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.timer.ClockStyle
import com.example.ui.timer.FocivoClock
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1.4b ITEM A — diagnostic: measures each FocivoClock style's TRUE
 * visible content bounds at its authoring layout size.
 *
 * Background: the 300dp face is centered on a magenta field using
 * Modifier.requiredSize (NOT Modifier.size — size() gets clamped to the
 * parent constraints, which is exactly why the v1.1.4 item-7 "88dp fit"
 * failed: the face was laid out at 96dp, wrapped/cramped, then the
 * graphicsLayer shrank the broken drawing).
 *
 * Offline analysis (PIL): the 300dp box is the central non-magenta square;
 * px-per-dp = boxPx / 300. Content = pixels differing from the tile bg
 * (0xFF0D110E) by > 10/255. EDITORIAL additionally renders at 600dp so its
 * 112sp numerals lay out on one line (they wrap inside a 300dp box).
 *
 * PNGs land in app/src/test/screenshots/v134/ and are uploaded by the
 * v134-screenshots workflow. These tests are diagnostic only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V114bClockBoundsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tileBg = Color(0xFF0D110E)
    private val fieldBg = Color(0xFFFF00FF)
    private val lime = Color(0xFFB6F13A)

    private fun renderBox(
        name: String,
        boxDp: Int,
        content: @Composable () -> Unit
    ) {
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fieldBg),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(boxDp.dp)
                        .background(tileBg),
                    contentAlignment = Alignment.Center,
                    content = { content() }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v134/$name.png"
        )
    }

    private fun renderStyle(name: String, style: ClockStyle, boxDp: Int = 300) {
        renderBox(name, boxDp) {
            FocivoClock(
                style = style,
                remainingSec = 1500,
                totalSec = 1500,
                accent = lime
            )
        }
    }

    @Test fun bounds_orbit() = renderStyle("v114b_bounds_orbit", ClockStyle.ORBIT)
    @Test fun bounds_flip() = renderStyle("v114b_bounds_flip", ClockStyle.FLIP)
    @Test fun bounds_liquid() = renderStyle("v114b_bounds_liquid", ClockStyle.LIQUID)
    @Test fun bounds_editorial_300() = renderStyle("v114b_bounds_editorial_300", ClockStyle.EDITORIAL)
    @Test fun bounds_editorial_600() = renderStyle("v114b_bounds_editorial_600", ClockStyle.EDITORIAL, 600)
    @Test fun bounds_dial() = renderStyle("v114b_bounds_dial", ClockStyle.DIAL)
    @Test fun bounds_horizon() = renderStyle("v114b_bounds_horizon", ClockStyle.HORIZON)
    @Test fun bounds_stack() = renderStyle("v114b_bounds_stack", ClockStyle.STACK)
    @Test fun bounds_breathe() = renderStyle("v114b_bounds_breathe", ClockStyle.BREATHE)
    @Test fun bounds_bars() = renderStyle("v114b_bounds_bars", ClockStyle.BARS)
}
