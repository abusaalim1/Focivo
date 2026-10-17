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
import com.example.ui.components.AiStudyGuardHubSheet
import com.example.ui.components.CustomizeTimerSheet
import com.example.ui.components.FocusShieldHubSheet
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.FocusSessionLogScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SessionCompleteScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SquadsFlowScreen
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
 * v1.3.2 ITEM 4 — UI glitch sweep. Every main screen at real phone size
 * (Pixel 8 qualifiers) in app-dark ("midnight" backdrop) and app-light
 * ("aurora" backdrop), so contrast/overflow/clipping/misalignment defects
 * can be eyeballed. No redesign, no feature changes — this test only captures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V132UiSweepScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun fakePrefs(): UserPreferencesEntity = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 1250
    )

    private fun fakePriorities(): List<TaskEntity> = listOf(
        TaskEntity(id = 1L, title = "Physics: Units & Measurement", category = "Deep Work", priority = 1, isTopPriority = true),
        TaskEntity(id = 2L, title = "Maths: Quadratic Equations", category = "Deep Work", priority = 1, isTopPriority = true)
    )

    private fun fakeSessions(): List<FocusSessionEntity> = listOf(
        FocusSessionEntity(
            id = 101L,
            taskTitle = "Physics", mode = "Deep Work", durationSeconds = 3600,
            focusPointsEarned = 60, completedAt = System.currentTimeMillis()
        ),
        FocusSessionEntity(
            id = 102L,
            taskTitle = "Maths", mode = "Deep Work", durationSeconds = 1500,
            focusPointsEarned = 25, completedAt = System.currentTimeMillis() - 86_400_000L
        )
    )

    /** Same stack as MainActivity: backdrop behind everything. */
    private fun themed(dark: Boolean, themeId: String, content: @Composable () -> Unit) {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, themeId)
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
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v132_uisweep/$name.png"
        )
    }

    private fun shotDark(name: String, screen: @Composable () -> Unit) {
        themed(dark = true, themeId = "midnight") { screen() }
        capture("${name}_dark")
    }

    private fun shotLight(name: String, screen: @Composable () -> Unit) {
        themed(dark = false, themeId = "aurora") { screen() }
        capture("${name}_light")
    }

    private fun focusScreen(running: Boolean): @Composable () -> Unit = {
        FocusScreen(
            remainingSeconds = if (running) 1342 else 1500,
            targetSeconds = 1500,
            isRunning = running,
            currentMode = "Deep Work",
            currentTaskTitle = "Physics",
            distractionsCount = 2,
            ambientSound = "Rain",
            userPreferences = fakePrefs(),
            isShieldActive = true,
            isDeepFocusEnabled = true,
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

    private fun leaderboardUsers(): List<LeaderboardUser> = listOf(
        LeaderboardUser(id = "1", displayName = "Abu Saalim", todayStudySeconds = 7200L, weeklyStudySeconds = 36000L, studySeconds = 180000L, isCurrentUser = true),
        LeaderboardUser(id = "2", displayName = "Riya", todayStudySeconds = 5400L, weeklyStudySeconds = 30000L, studySeconds = 150000L),
        LeaderboardUser(id = "3", displayName = "Kabir", todayStudySeconds = 3600L, weeklyStudySeconds = 24000L, studySeconds = 120000L),
        LeaderboardUser(id = "4", displayName = "Meera", todayStudySeconds = 1800L, weeklyStudySeconds = 12000L, studySeconds = 60000L)
    )

    @Test fun home() = shotDark(name = "home") {
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
    @Test fun home_light() = shotLight(name = "home") {
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

    @Test fun focus_idle() = shotDark(name = "focus_idle") { focusScreen(false)() }
    @Test fun focus_idle_light() = shotLight(name = "focus_idle") { focusScreen(false)() }
    @Test fun focus_running() = shotDark(name = "focus_running") { focusScreen(true)() }
    @Test fun focus_running_light() = shotLight(name = "focus_running") { focusScreen(true)() }

    @Test fun customize_sheet() = shotDark(name = "customize") {
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
    @Test fun customize_sheet_light() = shotLight(name = "customize") {
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

    @Test fun shield_hub_sheet() = shotDark(name = "shield_hub") {
        FocusShieldHubSheet(
            isShieldEnabled = true,
            isShieldTimerRunning = false,
            remainingSeconds = 0,
            blockedPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
            blockedAttemptsCount = 7,
            onToggleMasterShield = {},
            onToggleAppBlocked = { _, _ -> },
            onStartStandaloneShield = {},
            onStopStandaloneShield = {},
            onTriggerTestIntercept = {},
            onDismiss = {}
        )
    }
    @Test fun shield_hub_sheet_light() = shotLight(name = "shield_hub") {
        FocusShieldHubSheet(
            isShieldEnabled = true,
            isShieldTimerRunning = false,
            remainingSeconds = 0,
            blockedPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
            blockedAttemptsCount = 7,
            onToggleMasterShield = {},
            onToggleAppBlocked = { _, _ -> },
            onStartStandaloneShield = {},
            onStopStandaloneShield = {},
            onTriggerTestIntercept = {},
            onDismiss = {}
        )
    }

    @Test fun guard_hub_sheet() = shotDark(name = "guard_hub") {
        AiStudyGuardHubSheet(onDismiss = {})
    }
    @Test fun guard_hub_sheet_light() = shotLight(name = "guard_hub") {
        AiStudyGuardHubSheet(onDismiss = {})
    }

    @Test fun insights() = shotDark(name = "insights") {
        InsightsScreen(
            sessions = fakeSessions(),
            reflections = emptyList(),
            userPreferences = fakePrefs(),
            onSaveReflection = {}
        )
    }
    @Test fun insights_light() = shotLight(name = "insights") {
        InsightsScreen(
            sessions = fakeSessions(),
            reflections = emptyList(),
            userPreferences = fakePrefs(),
            onSaveReflection = {}
        )
    }

    @Test fun past_sessions() = shotDark(name = "past_sessions") {
        FocusSessionLogScreen(
            sessions = fakeSessions(),
            onBack = {}
        )
    }
    @Test fun past_sessions_light() = shotLight(name = "past_sessions") {
        FocusSessionLogScreen(
            sessions = fakeSessions(),
            onBack = {}
        )
    }

    @Test fun profile() = shotDark(name = "profile") {
        ProfileScreen(
            userPreferences = fakePrefs(),
            sessions = fakeSessions(),
            onOpenSettings = {}
        )
    }
    @Test fun profile_light() = shotLight(name = "profile") {
        ProfileScreen(
            userPreferences = fakePrefs(),
            sessions = fakeSessions(),
            onOpenSettings = {}
        )
    }

    @Test fun groups_hub() = shotDark(name = "groups_hub") {
        SquadsFlowScreen(
            onBack = {},
            myUserId = "test-user",
            myDisplayName = "Abu Saalim",
            myAvatarUrl = null
        )
    }
    @Test fun groups_hub_light() = shotLight(name = "groups_hub") {
        SquadsFlowScreen(
            onBack = {},
            myUserId = "test-user",
            myDisplayName = "Abu Saalim",
            myAvatarUrl = null
        )
    }

    @Test fun leaderboard() = shotDark(name = "leaderboard") {
        LeaderboardScreen(
            users = leaderboardUsers(),
            currentUserName = "Abu Saalim",
            currentUserPoints = 1250,
            sessions = fakeSessions()
        )
    }
    @Test fun leaderboard_light() = shotLight(name = "leaderboard") {
        LeaderboardScreen(
            users = leaderboardUsers(),
            currentUserName = "Abu Saalim",
            currentUserPoints = 1250,
            sessions = fakeSessions()
        )
    }

    @Test fun session_complete() = shotDark(name = "session_complete") {
        SessionCompleteScreen(
            session = fakeSessions().first(),
            onDismiss = {}
        )
    }
    @Test fun session_complete_light() = shotLight(name = "session_complete") {
        SessionCompleteScreen(
            session = fakeSessions().first(),
            onDismiss = {}
        )
    }

    @Test fun settings() = shotDark(name = "settings") {
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
    @Test fun settings_light() = shotLight(name = "settings") {
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
}
