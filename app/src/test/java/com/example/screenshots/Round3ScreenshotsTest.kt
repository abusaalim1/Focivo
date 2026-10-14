package com.example.screenshots

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FocusSessionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.DeepFocusSettingsSheet
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
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
 * v1.4.0-round3: owner-review screenshots for the rebuilt theme system.
 * Each theme must render FULL-SCREEN (backdrop fills the whole screen,
 * translucent cards, themed accent + mascot) — not just a colour swap.
 * Screens are wrapped in the same stack MainActivity uses:
 * Box { ThemedScreenBackground(); <Screen/> }.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class Round3ScreenshotsTest {

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
        FocusSessionEntity(taskTitle = "Maths", durationSeconds = 1500, targetDurationSeconds = 1500, mode = "Classic", dayOfWeek = 3, hourOfDay = 14)
    )

    private fun fakeTasks(): List<TaskEntity> = listOf(
        TaskEntity(title = "Physics numericals", category = "Deep Work", priority = 1, durationMinutes = 50, isTopPriority = true),
        TaskEntity(title = "Revise formulas", category = "Classic", priority = 2, durationMinutes = 25, isTopPriority = true)
    )

    private fun fakePrefs(): UserPreferencesEntity = UserPreferencesEntity(
        currentUserName = "Scholar",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 128
    )

    /** Same stack as MainActivity: backdrop behind everything. */
    private fun themed(content: @Composable () -> Unit) {
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ThemedScreenBackground()
                        content()
                    }
                }
            }
        }
    }

    private fun focusScreen() {
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
    }

    @Test
    fun study_timer_forest() {
        useTheme("forest")
        focusScreen()
        capture("study_timer_forest")
    }

    @Test
    fun study_timer_crimson() {
        useTheme("crimson")
        focusScreen()
        capture("study_timer_crimson")
    }

    @Test
    fun study_timer_royal() {
        useTheme("royal")
        focusScreen()
        capture("study_timer_royal")
    }

    @Test
    fun study_timer_aurora_happy() {
        useTheme("aurora")
        focusScreen()
        capture("study_timer_aurora_happy")
    }

    @Test
    fun home_midnight() {
        useTheme("midnight")
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
        capture("home_midnight")
    }

    @Test
    fun theme_picker_cards() {
        useTheme("aurora")
        themed {
            DeepFocusSettingsSheet(
                onDismiss = {},
                initialShowThemePicker = true
            )
        }
        capture("theme_picker_cards")
    }

    private fun capture(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/round3/$name.png"
        )
    }
}
