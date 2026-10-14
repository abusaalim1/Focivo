package com.example.screenshots

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.CustomizeTimerSheet
import com.example.ui.components.DeepFocusSettingsSheet
import com.example.ui.components.PreStudyAppBlockSheet
import com.example.ui.components.StudyHubSheet
import com.example.ui.screens.FocusScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
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
 * v1.5.0 study-screen redesign round: the one-button hub + 3D-minimal
 * timer/pomodoro. Every capture is eyeballed locally before the round is
 * reported. PNGs go to app/src/test/screenshots/round7/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V150StudyScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun useTheme(id: String) {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, id)
    }

    private fun fakePrefs(): UserPreferencesEntity = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 1250,
        blockedAppsList = "com.instagram.android,com.zhiliaoapp.musically,com.twitter.android"
    )

    /** Same stack as MainActivity: backdrop behind everything. */
    private fun themed(dark: Boolean = true, content: @Composable () -> Unit) {
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.ui.components.ThemedScreenBackground()
                        content()
                    }
                }
            }
        }
    }

    private fun studyScreen(
        running: Boolean,
        remainingSeconds: Int = 1500,
        mode: String = "Deep Work",
        initialShowHub: Boolean = false
    ) {
        themed {
            FocusScreen(
                remainingSeconds = if (running) remainingSeconds else 1500,
                targetSeconds = 1500,
                isRunning = running,
                currentMode = mode,
                currentTaskTitle = "Physics",
                distractionsCount = 2,
                ambientSound = "Rain",
                userStreak = 12,
                userPreferences = fakePrefs(),
                isShieldActive = true,
                isDeepFocusEnabled = true,
                initialShowHub = initialShowHub,
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
    }

    private fun hubSheet(expandedSection: String? = null) {
        themed {
            StudyHubSheet(
                onDismiss = {},
                isRunning = false,
                ambientSound = "Rain",
                onSelectAmbientSound = {},
                isDeepFocusEnabled = true,
                onToggleDeepFocus = {},
                distractionsCount = 2,
                onLogDistraction = {},
                blockedAppsCount = 3,
                isShieldActive = true,
                onOpenBlockedApps = {},
                onOpenUltraFocus = {},
                onOpenAiGuard = {},
                onOpenCustomizeTimer = {},
                initialExpandedSection = expandedSection
            )
        }
    }

    @Test
    fun study_idle_midnight() {
        useTheme("midnight")
        studyScreen(running = false)
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/round7/study_idle_midnight.png")
    }

    @Test
    fun study_running_sunset() {
        useTheme("sunset")
        studyScreen(running = true, remainingSeconds = 1234)
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/round7/study_running_sunset.png")
    }

    @Test
    fun study_hub_open() {
        useTheme("midnight")
        studyScreen(running = false, initialShowHub = true)
        composeTestRule.onNodeWithTag("study_hub_sheet", useUnmergedTree = true)
            .captureRoboImage("src/test/screenshots/round7/study_hub_open.png")
    }

    @Test
    fun hub_music_expanded() {
        useTheme("midnight")
        hubSheet(expandedSection = "music")
        composeTestRule.onNodeWithTag("study_hub_sheet", useUnmergedTree = true)
            .captureRoboImage("src/test/screenshots/round7/hub_music_expanded.png")
    }

    @Test
    fun customize_timer_sheet() {
        useTheme("midnight")
        themed {
            CustomizeTimerSheet(
                onDismiss = {},
                currentFocusMinutes = 25,
                currentSubject = "Physics",
                ambientSound = "Rain",
                onSelectAmbientSound = {},
                onSelectMode = { _, _ -> },
                onUpdateSubject = {}
            )
        }
        composeTestRule.onNodeWithTag("customize_timer_sheet", useUnmergedTree = true)
            .captureRoboImage("src/test/screenshots/round7/customize_timer_sheet.png")
    }

    @Test
    fun themes_sheet_locked() {
        useTheme("midnight")
        themed {
            // Test DB has 0 Focus Points -> premium themes render locked
            // with lock badge + price; tapping explains how to earn.
            DeepFocusSettingsSheet(
                onDismiss = {},
                initialShowThemePicker = true
            )
        }
        composeTestRule.onNodeWithTag("deep_focus_settings_sheet", useUnmergedTree = true)
            .captureRoboImage("src/test/screenshots/round7/themes_sheet_locked.png")
    }

    @Test
    fun blocked_apps_sheet() {
        useTheme("midnight")
        themed {
            PreStudyAppBlockSheet(
                currentBlockedList = "com.instagram.android,com.zhiliaoapp.musically",
                onConfirmAndStart = { _, _ -> },
                onDismiss = {}
            )
        }
        composeTestRule.onNodeWithTag("pre_study_app_block_sheet", useUnmergedTree = true)
            .captureRoboImage("src/test/screenshots/round7/blocked_apps_sheet.png")
    }
}
