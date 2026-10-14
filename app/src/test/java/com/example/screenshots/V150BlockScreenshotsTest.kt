package com.example.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.components.BlockedAppOverlay
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
 * v1.5.0 block-screen redesign + floating timer pill screenshot round.
 *
 * PNGs land in app/src/test/screenshots/round7/ and are eyeballed for:
 * premium minimal layout, readable type, mascot present, zero emoji.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V150BlockScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun capture(name: String) {
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/round7/$name.png")
    }

    private fun blockScreen(
        appName: String,
        blockedPackage: String,
        nextPriority: String?,
        focusTip: String?,
        isPunishment: Boolean = false
    ) {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                BlockedAppOverlay(
                    appName = appName,
                    blockedPackage = blockedPackage,
                    initialDurationSec = 1500,
                    isPunishment = isPunishment,
                    mascotRes = R.drawable.mascot_blocked,
                    nextPriority = nextPriority,
                    focusTip = focusTip,
                    onExitApp = {},
                    onOpenFocusly = {}
                )
            }
        }
    }

    @Test
    fun block_screen_with_next_priority() {
        // In Robolectric the package icon lookup fails, so the neutral
        // generic tile renders (no trademarked logo assets used anywhere).
        blockScreen(
            appName = "YouTube",
            blockedPackage = "com.google.android.youtube",
            nextPriority = "Physics: Units and Measurement — numerical practice",
            focusTip = null
        )
        capture("block_screen_with_next_priority")
    }

    @Test
    fun block_screen_with_focus_tip() {
        blockScreen(
            appName = "Instagram",
            blockedPackage = "com.instagram.android",
            nextPriority = "",
            focusTip = "One task, one tab, one timer. Multitasking is just switching with extra steps."
        )
        capture("block_screen_with_focus_tip")
    }

    @Test
    fun block_screen_punishment_variant() {
        blockScreen(
            appName = "YouTube",
            blockedPackage = "com.google.android.youtube",
            nextPriority = "",
            focusTip = "Put your phone in another room for the next 25 minutes — distance beats willpower.",
            isPunishment = true
        )
        capture("block_screen_punishment_variant")
    }

    @Test
    fun floating_pill_on_dark() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0D0B))
                        .padding(top = 72.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    FloatingTimerPill(
                        initialRemainingSeconds = 24 * 60 + 3,
                        onReturnToApp = {},
                        onDismiss = {}
                    )
                }
            }
        }
        capture("floating_pill_on_dark")
    }
}
