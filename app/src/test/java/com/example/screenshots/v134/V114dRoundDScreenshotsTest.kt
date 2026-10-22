package com.example.screenshots.v134

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.StrictAdminDisableSheet
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.AppRankAndLimitCard
import com.example.ui.screens.AppUsageRankItem
import com.example.ui.screens.FocusScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.DailyUsagePipeline
import com.example.util.DeviceAppInfo
import com.example.util.TimerThemeManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1.4d — Round D evidence (owner morning report, 2026-10-22):
 *  D1: the admin-disable sheet offers a one-tap in-app release ONLY when
 *      no strict session is blocking; while blocking, no release exists.
 *  D2: the Study page action cluster sits BELOW the timer again — idle
 *      shows Block + START STUDY there; a running session shows Reset +
 *      Stop and nothing else (no PAUSE pill, no in-face buttons).
 *  D3: the Usage Time box — without the grant it shows "--" and tapping
 *      opens the permission sheet; with the grant, tapping routes to the
 *      per-app usage view (onOpenScreenTimeBudgets). The per-app card
 *      renders the MOST TIME TODAY badge for the day's top app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V114dRoundDScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun resetUsageOverride() {
        DailyUsagePipeline.testPermissionOverride = null
    }

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
            filePath = "src/test/screenshots/v134/$name.png"
        )
    }

    @Composable
    private fun focusScreenContent(
        isRunning: Boolean,
        remainingSeconds: Int,
        targetSeconds: Int,
        onOpenScreenTimeBudgets: () -> Unit = {}
    ) {
        FocusScreen(
            remainingSeconds = remainingSeconds,
            targetSeconds = targetSeconds,
            isRunning = isRunning,
            currentMode = "Deep Work",
            currentTaskTitle = "Physics",
            distractionsCount = 0,
            ambientSound = "Rain",
            userPreferences = UserPreferencesEntity(
                currentUserName = "Abu Saalim",
                currentUserEmail = "scholar@example.com",
                dailyGoalMinutes = 360,
                focusPoints = 1250
            ),
            isShieldActive = true,
            isDeepFocusEnabled = true,
            onStartTimer = {},
            onPauseTimer = {},
            onResumeTimer = {},
            onFinishEarly = {},
            onRestartSession = {},
            onAddFiveMinutes = {},
            onSkipBreak = {},
            onSelectMode = { _, _ -> },
            onSelectAmbientSound = {},
            onLogDistraction = {},
            onUpdateTaskTitle = {},
            onOpenScreenTimeBudgets = onOpenScreenTimeBudgets
        )
    }

    // ---------------- D1: admin release sheet ----------------

    @Test
    fun admin_disable_sheet_offers_in_app_release_when_not_blocking() {
        themed(dark = true, themeId = "midnight") {
            StrictAdminDisableSheet(
                isCurrentlyBlocking = false,
                onOpenSettings = {},
                onDismiss = {},
                onDeactivateInApp = {}
            )
        }
        composeTestRule.onNodeWithTag("strict_admin_deactivate_cta").assertExists()
        capture("v114d_admin_disable_sheet_dark")
    }

    @Test
    fun admin_disable_sheet_offers_no_release_while_blocking() {
        themed(dark = true, themeId = "midnight") {
            StrictAdminDisableSheet(
                isCurrentlyBlocking = true,
                onOpenSettings = {},
                onDismiss = {},
                onDeactivateInApp = {}
            )
        }
        composeTestRule.onNodeWithTag("strict_admin_deactivate_cta").assertDoesNotExist()
        composeTestRule.onNodeWithTag("strict_admin_open_settings_cta").assertExists()
    }

    // ---------------- D2: cluster below the timer ----------------

    @Test
    fun study_idle_cluster_below_timer_dark() {
        DailyUsagePipeline.testPermissionOverride = false
        themed(dark = true, themeId = "midnight") {
            focusScreenContent(isRunning = false, remainingSeconds = 1500, targetSeconds = 1500)
        }
        composeTestRule.waitForIdle()
        // The running-state actions must not exist while idle.
        composeTestRule.onNodeWithTag("session_reset_button").assertDoesNotExist()
        // Scroll the page column until the cluster is composed, then prove
        // it is on screen with its START STUDY pill (it lives below the
        // timer now — the capture shows the timer above it).
        composeTestRule.onNodeWithTag("study_scroll_column")
            .performScrollToNode(hasTestTag("play_pause_button"))
        composeTestRule.onNodeWithTag("play_pause_button").assertIsDisplayed()
        capture("v114d_focus_idle_cluster_dark")
    }

    @Test
    fun study_idle_cluster_below_timer_light() {
        DailyUsagePipeline.testPermissionOverride = false
        themed(dark = false, themeId = "midnight") {
            focusScreenContent(isRunning = false, remainingSeconds = 1500, targetSeconds = 1500)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("study_scroll_column")
            .performScrollToNode(hasTestTag("play_pause_button"))
        composeTestRule.onNodeWithTag("play_pause_button").assertIsDisplayed()
        capture("v114d_focus_idle_cluster_light")
    }

    @Test
    fun study_running_shows_only_reset_and_stop() {
        DailyUsagePipeline.testPermissionOverride = false
        themed(dark = true, themeId = "midnight") {
            focusScreenContent(isRunning = true, remainingSeconds = 1400, targetSeconds = 1500)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("study_scroll_column")
            .performScrollToNode(hasTestTag("session_reset_button"))
        composeTestRule.onNodeWithTag("session_reset_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("session_stop_button").assertIsDisplayed()
        // Owner: "reset and stop ka 2 option show honge bas" — the old
        // PAUSE/START pill is gone from the running state.
        composeTestRule.onNodeWithTag("play_pause_button").assertDoesNotExist()
        capture("v114d_focus_running_reset_stop_dark")
    }

    // ---------------- D3: usage box behaviour ----------------

    @Test
    fun usage_box_without_grant_shows_dashes_and_tap_opens_permission_sheet() {
        DailyUsagePipeline.testPermissionOverride = false
        themed(dark = true, themeId = "midnight") {
            focusScreenContent(isRunning = false, remainingSeconds = 1500, targetSeconds = 1500)
        }
        composeTestRule.waitForIdle()
        // Never a number without the grant.
        composeTestRule.onNodeWithTag("study_usage_time_value").assertTextEquals("--")
        // Tap -> the permission ask (premium sheet), not a usage view.
        composeTestRule.onNodeWithTag("study_usage_time_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("focus_shield_permission_sheet").assertExists()
        capture("v114d_usage_nogrant_sheet_dark")
    }

    @Test
    fun usage_box_with_grant_tap_opens_per_app_view() {
        DailyUsagePipeline.testPermissionOverride = true
        var budgetsOpened = false
        themed(dark = true, themeId = "midnight") {
            focusScreenContent(
                isRunning = false,
                remainingSeconds = 1500,
                targetSeconds = 1500,
                onOpenScreenTimeBudgets = { budgetsOpened = true }
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("study_usage_time_card").performClick()
        composeTestRule.waitForIdle()
        assertTrue("granted tap must route to the per-app usage view", budgetsOpened)
        // ...and must NOT open the permission sheet.
        composeTestRule.onNodeWithTag("focus_shield_permission_sheet").assertDoesNotExist()
    }

    @Test
    fun top_app_card_renders_most_time_badge() {
        val item = AppUsageRankItem(
            appInfo = DeviceAppInfo(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                icon = null,
                category = "Entertainment",
                isBlocked = false
            ),
            usageMinutesToday = 95,
            limit = null
        )
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                Box(Modifier.background(Color(0xFF0A0C0B))) {
                    AppRankAndLimitCard(
                        item = item,
                        hasUsagePermission = true,
                        isTopApp = true,
                        onSetLimit = {},
                        onEditLimit = {},
                        onDeleteLimit = {},
                        onToggleEnabled = { _, _ -> }
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("top_app_badge").assertExists()
        capture("v114d_top_app_card_dark")
    }
}
