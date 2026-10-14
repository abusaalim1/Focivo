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
import com.example.data.LeaderboardUser
import com.example.data.model.FocusSessionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.SettingsScreen
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
 * v1.4.3 contrast round: every capture pairs a theme backdrop with the
 * screens the owner flagged — greeting header, section headings, glass-card
 * subtitles, podium names, stat labels — in dark mode + dark theme and in
 * light mode. PNGs are eyeballed locally before the fix round is reported.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ContrastScreenshotsTest {

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
        focusPoints = 1250
    )

    private fun fakePriorities(): List<TaskEntity> = listOf(
        TaskEntity(id = 1, title = "Physics: Units & Measurement", category = "Deep Work", priority = 1, isTopPriority = true),
        TaskEntity(id = 2, title = "Maths: Quadratic Equations", category = "Deep Work", priority = 1, isTopPriority = true)
    )

    private fun fakeSessions(): List<FocusSessionEntity> = listOf(
        FocusSessionEntity(
            taskTitle = "Physics",
            mode = "Deep Work",
            durationSeconds = 3600,
            focusPointsEarned = 60,
            completedAt = System.currentTimeMillis()
        )
    )

    /** Same stack as MainActivity: backdrop behind everything. */
    private fun themed(dark: Boolean = true, content: @Composable () -> Unit) {
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ThemedScreenBackground()
                        content()
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/round6/$name.png")
    }

    private fun homeScreen() {
        themed {
            HomeScreen(
                topPriorities = fakePriorities(),
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                scheduleStatusSummary = "Evening Deep Focus · 6:00 PM",
                scheduledBlocksCount = 2,
                onToggleTask = {},
                onStartFocus = { _, _ -> },
                onCustomizeFocus = {},
                onOpenPlanner = {},
                onOpenTaskCreate = {}
            )
        }
    }

    private fun studyScreen() {
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
    fun home_sunset_dark() {
        useTheme("sunset")
        homeScreen()
        capture("home_sunset_dark")
    }

    @Test
    fun home_midnight_dark() {
        useTheme("midnight")
        homeScreen()
        capture("home_midnight_dark")
    }

    @Test
    fun home_light_default() {
        useTheme("aurora")
        themed(dark = false) {
            HomeScreen(
                topPriorities = fakePriorities(),
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                scheduleStatusSummary = "Evening Deep Focus · 6:00 PM",
                scheduledBlocksCount = 2,
                onToggleTask = {},
                onStartFocus = { _, _ -> },
                onCustomizeFocus = {},
                onOpenPlanner = {},
                onOpenTaskCreate = {}
            )
        }
        capture("home_light_default")
    }

    @Test
    fun leaderboard_royal_dark() {
        useTheme("royal")
        val users = listOf(
            LeaderboardUser(id = "1", displayName = "Abu Saalim", todayStudySeconds = 7200, weeklyStudySeconds = 36000, studySeconds = 180000, streak = 12, isCurrentUser = true),
            LeaderboardUser(id = "2", displayName = "Riya", todayStudySeconds = 5400, weeklyStudySeconds = 30000, studySeconds = 150000, streak = 9),
            LeaderboardUser(id = "3", displayName = "Arjun", todayStudySeconds = 3600, weeklyStudySeconds = 25000, studySeconds = 120000, streak = 7)
        )
        themed {
            LeaderboardScreen(
                users = users,
                currentUserName = "Abu Saalim",
                currentUserPoints = 1250,
                currentUserStreak = 12,
                sessions = fakeSessions()
            )
        }
        capture("leaderboard_royal_dark")
    }

    @Test
    fun settings_midnight_dark() {
        useTheme("midnight")
        themed {
            SettingsScreen(
                userPreferences = fakePrefs(),
                onBack = {},
                onUpdateTheme = {},
                onUpdateDailyGoal = {},
                onResetDemoData = {},
                onClearAllData = {},
                onReplayOnboarding = {}
            )
        }
        capture("settings_midnight_dark")
    }

    @Test
    fun study_midnight_dark() {
        useTheme("midnight")
        studyScreen()
        capture("study_midnight_dark")
    }
}
