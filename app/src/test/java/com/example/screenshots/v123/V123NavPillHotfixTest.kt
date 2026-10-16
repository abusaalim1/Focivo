package com.example.screenshots.v123

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.FloatingNavigation
import com.example.ui.components.LockInStartOverlay
import com.example.ui.components.NavTab
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FocusScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.MascotWardrobe
import com.example.util.TimerFaceManager
import com.example.util.TimerThemeManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.2.3 HOTFIX regression — the Motion Lab liquid nav pill blew the bottom
 * bar up into a screen-tall capsule on real devices (owner screenshot
 * 2026-10-16: giant white oval + full-height lime bar over the Study
 * screen). Root cause: the pill was a `fillMaxHeight()` layout CHILD inside
 * a wrap-content Box; the Scaffold bottomBar slot measures with loose
 * full-height constraints, so the pill resolved to full screen height and
 * the wrapping parent sized itself to the tallest child. The pill is now
 * painted with drawBehind and can never participate in measurement.
 *
 * - The height assertions FAIL on the old geometry (bar ~= full height).
 * - Screenshots: real shell (Scaffold + nav) light + dark, the Focus
 *   screen in light mode, and the Lock-in Start overlay at phone size.
 * PNGs land in app/src/test/screenshots/v123/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V123NavPillHotfixTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    /** The real app shell shape: full-bleed backdrop + Scaffold + nav bottomBar. */
    private fun shell(dark: Boolean, content: @Composable () -> Unit) {
        TimerThemeManager.init(context)
        TimerFaceManager.init(context)
        MascotWardrobe.init(context)
        TimerThemeManager.selectTheme(context, "aurora")
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ThemedScreenBackground()
                        Scaffold(
                            containerColor = Color.Transparent,
                            bottomBar = {
                                FloatingNavigation(
                                    selectedTab = NavTab.FOCUS,
                                    onTabSelected = {},
                                    hapticsEnabled = false
                                )
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) { content() }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /** The bottom bar must stay a slim dock — never a screen-tall capsule. */
    private fun assertNavBarCompact() {
        val rootH = composeTestRule.onRoot().fetchSemanticsNode().size.height.toFloat()
        val navH = composeTestRule.onNodeWithTag("floating_nav").fetchSemanticsNode().size.height.toFloat()
        org.junit.Assert.assertTrue(
            "REGRESSION: bottom nav bar is ${navH}px of ${rootH}px tall " +
                "(>= 30% of the screen) — the liquid-pill geometry bug is back.",
            navH < rootH * 0.30f
        )
    }

    @Test
    fun nav_bar_stays_compact_light() {
        shell(dark = false) {
            Text("shell", modifier = Modifier.testTag("shell_content"))
        }
        assertNavBarCompact()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_hotfix_nav_shell_light.png")
    }

    @Test
    fun nav_bar_stays_compact_dark() {
        shell(dark = true) {
            Text("shell", modifier = Modifier.testTag("shell_content"))
        }
        assertNavBarCompact()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_hotfix_nav_shell_dark.png")
    }

    @Test
    fun focus_screen_light_with_nav() {
        shell(dark = false) {
            FocusScreen(
                remainingSeconds = 1500,
                targetSeconds = 1500,
                isRunning = false,
                currentMode = "focus",
                currentTaskTitle = "Physics: Units & Measurement",
                distractionsCount = 0,
                ambientSound = "Rain",
                userPreferences = UserPreferencesEntity(
                    currentUserName = "Abu Saalim",
                    currentUserEmail = "scholar@example.com",
                    dailyGoalMinutes = 360,
                    focusPoints = 0
                ),
                onStartTimer = {},
                onPauseTimer = {},
                onResumeTimer = {},
                onFinishEarly = {},
                onAddFiveMinutes = {},
                onSkipBreak = {},
                onSelectMode = { _, _ -> },
                onSelectAmbientSound = {},
                onLogDistraction = {},
                onUpdateTaskTitle = {}
            )
        }
        assertNavBarCompact()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_hotfix_focus_light.png")
    }

    @Test
    fun lockin_start_overlay_renders() {
        TimerThemeManager.init(context)
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    LockInStartOverlay(
                        blockedPackages = listOf(
                            "com.instagram.android",
                            "com.zhiliaoapp.musically",
                            "com.google.android.youtube"
                        ),
                        onComplete = {},
                        onDismiss = {}
                    )
                }
            }
        }
        // No waitForIdle(): the overlay runs an infinite pulse animation by
        // design, so idleness never arrives — capture the frame directly.
        // The overlay is a dark full-screen transition with a 300dp ring —
        // it must never paint a giant white capsule or full-height lime bar.
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_hotfix_lockin_overlay.png")
    }
}
