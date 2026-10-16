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
import com.example.ui.components.HomeNoticeOverlay
import com.example.ui.components.HomeUpdateOverlay
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.AppUpdateInfo
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
 * v1.5.0 home clean-up + consistency pass — Home in Aurora (light) and
 * Midnight (dark), Profile, Leaderboard, Insights, and the update overlay.
 * PNGs land in app/src/test/screenshots/round7/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V150HomeScreenshotsTest {

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
        TaskEntity(id = 1L, title = "Physics: Units & Measurement", category = "Deep Work", priority = 1, isTopPriority = true),
        TaskEntity(id = 2L, title = "Maths: Quadratic Equations", category = "Deep Work", priority = 1, isTopPriority = true)
    )

    private fun fakeSessions(): List<FocusSessionEntity> = listOf(
        FocusSessionEntity(
            taskTitle = "Physics",
            mode = "Deep Work",
            durationSeconds = 3600,
            focusPointsEarned = 60,
            completedAt = System.currentTimeMillis()
        ),
        FocusSessionEntity(
            taskTitle = "Maths",
            mode = "Deep Work",
            durationSeconds = 1500,
            focusPointsEarned = 25,
            completedAt = System.currentTimeMillis() - 3_600_000L
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
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/round7/$name.png")
    }

    private fun homeScreen(dark: Boolean) {
        themed(dark = dark) {
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

    @Test
    fun home_aurora_light() {
        useTheme("aurora")
        homeScreen(dark = false)
        capture("home_aurora_light")
    }

    @Test
    fun home_midnight_dark() {
        useTheme("midnight")
        homeScreen(dark = true)
        capture("home_midnight_dark")
    }

    @Test
    fun profile_midnight_dark() {
        useTheme("midnight")
        themed {
            ProfileScreen(
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                onOpenSettings = {}
            )
        }
        capture("profile_midnight_dark")
    }

    @Test
    fun leaderboard_midnight_dark() {
        useTheme("midnight")
        val users = listOf(
            LeaderboardUser(id = "1", displayName = "Abu Saalim", todayStudySeconds = 7200L, weeklyStudySeconds = 36000L, studySeconds = 180000L, isCurrentUser = true),
            LeaderboardUser(id = "2", displayName = "Riya", todayStudySeconds = 5400L, weeklyStudySeconds = 30000L, studySeconds = 150000L),
            LeaderboardUser(id = "3", displayName = "Arjun", todayStudySeconds = 3600L, weeklyStudySeconds = 25000L, studySeconds = 120000L)
        )
        themed {
            LeaderboardScreen(
                users = users,
                currentUserName = "Abu Saalim",
                currentUserPoints = 1250,
                sessions = fakeSessions()
            )
        }
        capture("leaderboard_midnight_dark")
    }

    @Test
    fun insights_midnight_dark() {
        useTheme("midnight")
        themed {
            InsightsScreen(
                sessions = fakeSessions(),
                reflections = emptyList(),
                userPreferences = fakePrefs(),
                onSaveReflection = {}
            )
        }
        capture("insights_midnight_dark")
    }

    @Test
    fun home_update_overlay() {
        useTheme("midnight")
        val info = AppUpdateInfo(
            currentVersionCode = 140,
            currentVersionName = "1.4.0",
            latestVersionCode = 150,
            latestVersionName = "1.5.0",
            releaseNotes = "A cleaner Home, a brand-new onboarding flow, and mascot touches across the app.",
            downloadUrl = "https://example.com/focivo.apk",
            isForceUpdate = false,
            isUpdateAvailable = true,
            checkSucceeded = true
        )
        themed {
            HomeUpdateOverlay(
                updateInfo = info,
                onUpdateNow = {},
                onLater = {}
            )
        }
        capture("home_update_overlay")
    }

    @Test
    fun home_notice_overlay() {
        useTheme("midnight")
        themed {
            HomeNoticeOverlay(
                announcementText = "Sunday Recap is live — check your weekly focus story in Insights every Sunday evening.",
                onGotIt = {}
            )
        }
        capture("home_notice_overlay")
    }
}
