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
 * Timer) and captures it with the middle card of each trio centered, so ALL
 * NINE clock styles appear FULLY in frame across the shots:
 *   start: Orbit / Flip / Liquid (Flip centered)
 *   mid:   Editorial Clock / Dial / Horizon (Dial centered)
 *   end:   Stack / Breathe / Bars (Breathe centered)
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

    /**
     * Scrolls so the card carrying [label] is centered in the viewport —
     * exact regardless of per-card pitch (the tagline under each card makes
     * every card column a different width, so fixed-dp offsets drift).
     */
    private fun centerCard(label: String, name: String) {
        val root = composeTestRule.onRoot().fetchSemanticsNode()
        val viewportCenterX = root.size.width / 2f
        val node = composeTestRule
            .onNodeWithText(label, useUnmergedTree = true)
            .fetchSemanticsNode()
        val labelCenterX = node.positionInRoot.x + node.size.width / 2f
        val target = (scrollState.value + labelCenterX - viewportCenterX)
            .toInt()
            .coerceIn(0, scrollState.maxValue)
        runBlocking { scrollState.scrollTo(target) }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v134/$name.png"
        )
    }

    private fun runAll(prefix: String) {
        // Center the middle card of each trio: its neighbours land fully
        // in frame on both sides (~3.1 cards fit the Pixel 8 viewport).
        centerCard("Flip", "${prefix}_picker_start") // Orbit / Flip / Liquid
        centerCard("Dial", "${prefix}_picker_mid") // Editorial Clock / Dial / Horizon
        centerCard("Breathe", "${prefix}_picker_end") // Stack / Breathe / Bars
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
