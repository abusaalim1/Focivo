package com.example.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.components.FacePlayPauseButton
import com.example.ui.components.FaceResetButton
import com.example.ui.components.MascotPose
import com.example.ui.components.TimerFaceHost
import com.example.ui.components.TimerFaceParams
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
 * v1.3.2 regression set for ITEM 12: the RUNNING timer's Pause and Restart
 * buttons were three inconsistent per-face copies (72dp lime circle here,
 * 60dp borderless circle there) with dead press states — each copy created
 * its own interaction source, so pressFeedback never animated.
 *
 * Now one shared implementation (FacePlayPauseButton / FaceResetButton):
 * glass rim-light on the lime/mint play circle, frosted glass reset circle,
 * real spring press states. Eyeball: consistent styling across faces, dark
 * AND light, running AND paused.
 *
 * One setContent per @Test (Compose rule restriction).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V132TimerButtonsScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun shot(dark: Boolean, name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (dark) Color(0xFF0B0E0B) else Color(0xFFF4F6F1))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v132_timer_buttons/$name.png"
        )
    }

    // ── Shared buttons, isolated: running/paused x focus/break x dark/light
    @Test
    fun buttons_running_focus_dark() = shot(true, "buttons_running_focus_dark") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FacePlayPauseButton(isRunning = true, isBreak = false, onClick = {})
            FaceResetButton(onClick = {})
        }
    }

    @Test
    fun buttons_running_focus_light() = shot(false, "buttons_running_focus_light") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FacePlayPauseButton(isRunning = true, isBreak = false, onClick = {})
            FaceResetButton(onClick = {})
        }
    }

    @Test
    fun buttons_paused_focus_dark() = shot(true, "buttons_paused_focus_dark") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FacePlayPauseButton(isRunning = false, isBreak = false, onClick = {})
            FaceResetButton(onClick = {})
        }
    }

    @Test
    fun buttons_paused_focus_light() = shot(false, "buttons_paused_focus_light") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FacePlayPauseButton(isRunning = false, isBreak = false, onClick = {})
            FaceResetButton(onClick = {})
        }
    }

    @Test
    fun buttons_running_break_dark() = shot(true, "buttons_running_break_dark") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FacePlayPauseButton(isRunning = true, isBreak = true, onClick = {})
            FaceResetButton(onClick = {})
        }
    }

    @Test
    fun buttons_running_break_light() = shot(false, "buttons_running_break_light") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FacePlayPauseButton(isRunning = true, isBreak = true, onClick = {})
            FaceResetButton(onClick = {})
        }
    }

    // ── Full faces, RUNNING (pause icon visible), dark/light ──────────────
    private fun runningFace(faceId: String, dark: Boolean) {
        val theme = if (dark) "dark" else "light"
        shot(dark, "face_${faceId}_running_$theme") {
            TimerFaceHost(
                faceId = faceId,
                params = TimerFaceParams(
                    totalSeconds = 1500,
                    remainingSeconds = 1342,
                    isRunning = true,
                    isBreak = false,
                    subjectTitle = "Physics",
                    mascotPose = MascotPose.STUDY_HAPPY,
                    theme = null,
                    onTogglePlay = {},
                    onReset = {},
                    onTagClick = {}
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    @Test fun face_digits_running_dark() = runningFace("digits", true)
    @Test fun face_digits_running_light() = runningFace("digits", false)
    @Test fun face_glass_running_dark() = runningFace("glass", true)
    @Test fun face_glass_running_light() = runningFace("glass", false)
    @Test fun face_editorial_running_dark() = runningFace("editorial", true)
    @Test fun face_editorial_running_light() = runningFace("editorial", false)
}
