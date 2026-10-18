package com.example.screenshots.guardcluster

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserPreferencesEntity
import com.example.service.AiStudyAccessibilityService
import com.example.ui.components.GuardControlCluster
import com.example.ui.components.GuardExplainerContent
import com.example.ui.components.GuardExplainerKey
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.components.guardExplainerContent
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.ScreenTimeBudgetsScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.AiStudyGuardManager
import com.example.util.ScreenTimeBudgetStore
import com.example.util.StrictModeManager
import com.example.util.TimerThemeManager
import com.example.util.YouTubeStudyGuardManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.3.3 (ITEM 5): Study-screen guard control cluster — Roborazzi PNGs at
 * real phone size (Pixel 8). The cluster, each toggle's on/off states, each
 * premium explainer sheet, the screen-time budgets entry, and the full
 * Study screen with the cluster — dark AND light. PNGs land in
 * app/src/test/screenshots/guard-cluster/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GuardClusterScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun themed(dark: Boolean, content: @Composable () -> Unit) {
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
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/guard-cluster/$name.png")
    }

    private fun fakePrefs() = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 1250
    )

    /**
     * Sets the three toggles' real manager state before composition so the
     * cluster renders the intended on/off mix. Same state sources as prod.
     */
    private fun setToggleStates(strict: Boolean, guard: Boolean, ytFilter: Boolean) {
        StrictModeManager.setStrictModeEnabled(context, strict)
        AiStudyAccessibilityService.isServiceConnected = guard
        AiStudyGuardManager.setAiGuardEnabled(context, guard)
        YouTubeStudyGuardManager.setFilterEnabled(context, ytFilter)
    }

    private fun resetToggleStates() {
        setToggleStates(strict = true, guard = false, ytFilter = false)
        AiStudyAccessibilityService.isServiceConnected = false
    }

    // -- Cluster alone ------------------------------------------------------

    @Test
    fun cluster_dark_mixedStates() {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, "midnight")
        setToggleStates(strict = true, guard = true, ytFilter = false)
        themed(dark = true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                GuardControlCluster(onOpenScreenTimeBudgets = {})
            }
        }
        capture("cluster_dark_mixed")
        resetToggleStates()
    }

    @Test
    fun cluster_light_allOff() {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, "aurora")
        setToggleStates(strict = false, guard = false, ytFilter = false)
        themed(dark = false) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                GuardControlCluster(onOpenScreenTimeBudgets = {})
            }
        }
        capture("cluster_light_all_off")
        resetToggleStates()
    }

    @Test
    fun cluster_dark_allOn() {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, "midnight")
        setToggleStates(strict = true, guard = true, ytFilter = true)
        themed(dark = true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                GuardControlCluster(onOpenScreenTimeBudgets = {})
            }
        }
        capture("cluster_dark_all_on")
        resetToggleStates()
    }

    // -- Explainer sheets (content, standalone) ------------------------------

    private fun explainerShot(key: GuardExplainerKey, dark: Boolean, name: String) {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, if (dark) "midnight" else "aurora")
        themed(dark = dark) {
            GuardExplainerContent(
                content = guardExplainerContent(key),
                onDismiss = {}
            )
        }
        capture(name)
    }

    @Test
    fun explainer_strict_dark() {
        explainerShot(GuardExplainerKey.STRICT, dark = true, "explainer_strict_dark")
    }

    @Test
    fun explainer_strict_light() {
        explainerShot(GuardExplainerKey.STRICT, dark = false, "explainer_strict_light")
    }

    @Test
    fun explainer_guard_dark() {
        explainerShot(GuardExplainerKey.AI_GUARD, dark = true, "explainer_guard_dark")
    }

    @Test
    fun explainer_guard_light() {
        explainerShot(GuardExplainerKey.AI_GUARD, dark = false, "explainer_guard_light")
    }

    @Test
    fun explainer_ytfilter_dark() {
        explainerShot(GuardExplainerKey.YT_FILTER, dark = true, "explainer_ytfilter_dark")
    }

    @Test
    fun explainer_ytfilter_light() {
        explainerShot(GuardExplainerKey.YT_FILTER, dark = false, "explainer_ytfilter_light")
    }

    // -- Screen-time budgets entry -------------------------------------------

    @Test
    fun budgets_dark() {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, "midnight")
        ScreenTimeBudgetStore.init(context)
        themed(dark = true) {
            ScreenTimeBudgetsScreen(onBack = {})
        }
        capture("budgets_dark")
    }

    @Test
    fun budgets_light() {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, "aurora")
        ScreenTimeBudgetStore.init(context)
        themed(dark = false) {
            ScreenTimeBudgetsScreen(onBack = {})
        }
        capture("budgets_light")
    }

    // -- Full Study screen with the cluster (combined render) ----------------

    private fun studyScreen(dark: Boolean, name: String) {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, if (dark) "midnight" else "aurora")
        setToggleStates(strict = true, guard = false, ytFilter = false)
        themed(dark = dark) {
            FocusScreen(
                remainingSeconds = 1500,
                targetSeconds = 1500,
                isRunning = false,
                currentMode = "Deep Work",
                currentTaskTitle = "Physics",
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
                onUpdateTaskTitle = {},
                onOpenScreenTimeBudgets = {}
            )
        }
        capture(name)
        resetToggleStates()
    }

    @Test
    fun study_with_cluster_dark() {
        studyScreen(dark = true, name = "study_with_cluster_dark")
    }

    @Test
    fun study_with_cluster_light() {
        studyScreen(dark = false, name = "study_with_cluster_light")
    }
}
