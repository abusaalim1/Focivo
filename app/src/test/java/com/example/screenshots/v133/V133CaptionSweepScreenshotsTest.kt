package com.example.screenshots.v133

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.FloatingNavigation
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
 * v1.3.3 ITEM 9 — caption contrast sweep.
 *
 * Renders the REAL FocusScreen (idle: START STUDY + the hub-hint caption
 * underneath, plus timer-face hint captions) on every backdrop the sweep
 * covers — the default Aurora + all 6 ambient themes — in dark AND light
 * app mode, at Pixel 8. The caption zones sit directly on the backdrop,
 * so each PNG is the visual proof of the contrast fix.
 *
 * Backdrops: aurora (default), sakura_drift, firefly_forest, sunset_dunes,
 * aurora_veil, monsoon_rain, ember_rise.
 * PNGs land in app/src/test/screenshots/v133/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V133CaptionSweepScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun initAll(themeId: String) {
        TimerThemeManager.init(context)
        TimerFaceManager.init(context)
        MascotWardrobe.init(context)
        TimerThemeManager.selectTheme(context, themeId)
    }

    private fun fakePrefs() = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 1500
    )

    /** Real app shell: full-bleed backdrop + Scaffold + simplified nav. */
    private fun shell(dark: Boolean, content: @Composable () -> Unit) {
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

    private fun captionShot(themeId: String, dark: Boolean) {
        initAll(themeId)
        shell(dark) {
            FocusScreen(
                remainingSeconds = 1500,
                targetSeconds = 1500,
                isRunning = false,
                currentMode = "focus",
                currentTaskTitle = "Physics: Units and Measurement",
                distractionsCount = 0,
                ambientSound = "Rain",
                userPreferences = fakePrefs(),
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
        val mode = if (dark) "dark" else "light"
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/v133_caption_${themeId}_${mode}.png")
    }

    @Test fun sweep_aurora_dark() = captionShot("aurora", true)
    @Test fun sweep_aurora_light() = captionShot("aurora", false)
    @Test fun sweep_sakura_drift_dark() = captionShot("sakura_drift", true)
    @Test fun sweep_sakura_drift_light() = captionShot("sakura_drift", false)
    @Test fun sweep_firefly_forest_dark() = captionShot("firefly_forest", true)
    @Test fun sweep_firefly_forest_light() = captionShot("firefly_forest", false)
    @Test fun sweep_sunset_dunes_dark() = captionShot("sunset_dunes", true)
    @Test fun sweep_sunset_dunes_light() = captionShot("sunset_dunes", false)
    @Test fun sweep_aurora_veil_dark() = captionShot("aurora_veil", true)
    @Test fun sweep_aurora_veil_light() = captionShot("aurora_veil", false)
    @Test fun sweep_monsoon_rain_dark() = captionShot("monsoon_rain", true)
    @Test fun sweep_monsoon_rain_light() = captionShot("monsoon_rain", false)
    @Test fun sweep_ember_rise_dark() = captionShot("ember_rise", true)
    @Test fun sweep_ember_rise_light() = captionShot("ember_rise", false)
}
