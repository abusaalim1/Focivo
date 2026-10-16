package com.example.screenshots.v123

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
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MascotScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.MascotWardrobe
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
 * v1.2.3 design-unification verify (branch feat/v123-splash):
 * Home, Mascot, Profile and Settings after alignment to the owner's
 * clean-redesign language (#0A0A0B bg, #131315 surfaces, #B6F13A lime,
 * Poppins body + Instrument Serif italic page headlines).
 * PNGs land in app/src/test/screenshots/v123/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V123UnificationScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun initAll() {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, "midnight")
        MascotWardrobe.init(context)
    }

    /** Same stack as MainActivity: theme backdrop behind everything. */
    private fun themedDark(content: @Composable () -> Unit) {
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

    private fun fakePrefs() = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 1500
    )

    private fun fakePriorities() = listOf(
        TaskEntity(id = 1L, title = "Physics: Units & Measurement", category = "Deep Work", priority = 1, isTopPriority = true),
        TaskEntity(id = 2L, title = "Maths: Quadratic Equations", category = "Deep Work", priority = 1, isTopPriority = true)
    )

    private fun fakeSessions() = listOf(
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

    @Test
    fun v123_unification_home() {
        initAll()
        themedDark {
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
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_unification_home.png")
    }

    @Test
    fun v123_unification_mascot() {
        initAll()
        themedDark {
            MascotScreen(
                focusPoints = 1500,
                onBack = {}
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_unification_mascot.png")
    }

    @Test
    fun v123_unification_profile() {
        initAll()
        themedDark {
            ProfileScreen(
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                onOpenSettings = {}
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_unification_profile.png")
    }

    @Test
    fun v123_unification_settings() {
        initAll()
        themedDark {
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
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_unification_settings.png")
    }
}
