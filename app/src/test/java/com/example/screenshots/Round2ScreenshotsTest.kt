package com.example.screenshots

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FocusSessionEntity
import com.example.data.model.ReflectionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.AiStudyGuardHubSheet
import com.example.ui.components.DeepFocusSettingsSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.ProfileScreen
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
 * v1.4.0-round2: owner-review screenshots. Real composables with fake data —
 * nothing is mocked at the UI layer. Run via the screenshots workflow
 * (workflow_dispatch) which uploads PNGs as an artifact.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class Round2ScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun useTheme(id: String) {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, id)
    }

    private fun fakeSessions(): List<FocusSessionEntity> = listOf(
        FocusSessionEntity(taskTitle = "Physics", durationSeconds = 3000, targetDurationSeconds = 3000, mode = "Deep Work", dayOfWeek = 3, hourOfDay = 9),
        FocusSessionEntity(taskTitle = "Maths", durationSeconds = 1500, targetDurationSeconds = 1500, mode = "Classic", dayOfWeek = 3, hourOfDay = 14),
        FocusSessionEntity(taskTitle = "Chemistry", durationSeconds = 3600, targetDurationSeconds = 3600, mode = "Deep Work", dayOfWeek = 2, hourOfDay = 10)
    )

    private fun fakeTasks(): List<TaskEntity> = listOf(
        TaskEntity(title = "Physics numericals", category = "Deep Work", priority = 1, durationMinutes = 50, isTopPriority = true),
        TaskEntity(title = "Revise formulas", category = "Classic", priority = 2, durationMinutes = 25, isTopPriority = true),
        TaskEntity(title = "Read chapter 4", category = "Deep Work", priority = 2, durationMinutes = 30)
    )

    private fun fakePrefs(): UserPreferencesEntity = UserPreferencesEntity(
        currentUserName = "Scholar",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 128
    )

    private fun themed(content: @Composable () -> Unit) {
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            // Dark theme forced: matches the owner's device for review.
            FocuslyTheme(darkTheme = true) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    content()
                }
            }
        }
    }

    @Test
    fun home_aurora() {
        useTheme("aurora")
        themed {
            HomeScreen(
                topPriorities = fakeTasks(),
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                onToggleTask = {},
                onStartFocus = { _, _ -> },
                onCustomizeFocus = {},
                onOpenPlanner = {},
                onOpenTaskCreate = {}
            )
        }
        capture("home_aurora")
    }

    @Test
    fun study_timer_aurora() {
        useTheme("aurora")
        themed {
            FocusScreen(
                remainingSeconds = 1500,
                targetSeconds = 1500,
                isRunning = false,
                currentMode = "Deep Work",
                currentTaskTitle = "Physics",
                distractionsCount = 0,
                ambientSound = "Silent",
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
        capture("study_timer_aurora")
    }

    @Test
    fun study_timer_midnight() {
        useTheme("midnight")
        themed {
            FocusScreen(
                remainingSeconds = 1500,
                targetSeconds = 1500,
                isRunning = false,
                currentMode = "Deep Work",
                currentTaskTitle = "Physics",
                distractionsCount = 0,
                ambientSound = "Silent",
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
        capture("study_timer_midnight")
    }

    @Test
    fun deep_focus_settings_theme_picker() {
        useTheme("aurora")
        themed {
            DeepFocusSettingsSheet(
                onDismiss = {},
                initialShowThemePicker = true
            )
        }
        capture("deep_focus_settings_theme_picker")
    }

    @Test
    fun stats() {
        useTheme("aurora")
        themed {
            InsightsScreen(
                sessions = fakeSessions(),
                reflections = listOf(ReflectionEntity(weekLabel = "This week", totalMinutesFocused = 135, sessionsCompleted = 3)),
                userPreferences = fakePrefs(),
                onSaveReflection = {}
            )
        }
        capture("stats")
    }

    @Test
    fun profile_milestones() {
        useTheme("aurora")
        themed {
            ProfileScreen(
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                onOpenSettings = {}
            )
        }
        // Scroll milestones into view
        composeTestRule.onNodeWithText("MILESTONES", useUnmergedTree = true).performScrollTo()
        capture("profile_milestones")
    }

    @Test
    fun achievement_detail_dialog() {
        useTheme("aurora")
        themed {
            ProfileScreen(
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                onOpenSettings = {}
            )
        }
        composeTestRule.onNodeWithText("MILESTONES", useUnmergedTree = true).performScrollTo()
        composeTestRule.onNodeWithText("First Flow", useUnmergedTree = true).performScrollTo()
        composeTestRule.onNodeWithText("First Flow", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        capture("achievement_detail_dialog")
    }

    @Test
    fun auth_signin_forgot_password() {
        useTheme("aurora")
        themed {
            AuthScreen(
                isLoading = false,
                errorMessage = null,
                onLogin = { _, _ -> },
                onRegister = { _, _, _ -> },
                onClearError = {}
            )
        }
        capture("auth_signin_forgot_password")
    }

    @Test
    fun guard_hub() {
        useTheme("aurora")
        themed {
            AiStudyGuardHubSheet(onDismiss = {})
        }
        capture("guard_hub")
    }

    private fun capture(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/round2/$name.png"
        )
    }
}
