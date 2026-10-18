package com.example.screenshots.v133

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppDailyLimitEntity
import com.example.ui.components.BlockedAppOverlay
import com.example.ui.components.BreakOverlayInfo
import com.example.ui.components.ScreenTimeBudgetSheet
import com.example.ui.components.StudyTopStatsHeader
import com.example.ui.screens.AppRankAndLimitCard
import com.example.ui.screens.AppUsageRankItem
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.focivoTokens
import com.example.util.DeviceAppInfo
import com.example.util.InstalledAppsManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.3.3 (items 1 + 8) — usage pipeline surfaces verify, Pixel 8 size:
 *  - Study-top header cards (permission granted + denied) dark + light
 *  - Per-app budget row (usage + limit setter UI)
 *  - Screen-time break overlay (break explanation + live countdown) dark + light
 *  - Global-limit settings sheet dark + light
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V133UsageBudgetScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fakeApp() = DeviceAppInfo(
        packageName = "com.instagram.android",
        appName = "Instagram",
        icon = null,
        category = InstalledAppsManager.CATEGORY_SOCIAL,
        isBlocked = false
    )

    private fun fakeLimit() = AppDailyLimitEntity(
        appPackage = "com.instagram.android",
        appName = "Instagram",
        dailyLimitMinutes = 60,
        minutesUsedToday = 47,
        isEnabled = true,
        strictModeEnabled = false
    )

    // ---------- Study-top header ----------

    @Test
    fun studyTopHeader_dark_granted() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(focivoTokens().bg)
                        .padding(20.dp)
                ) {
                    StudyTopStatsHeader(
                        usageMillis = (3 * 60 + 12) * 60_000L,
                        hasUsagePermission = true,
                        focusMinutesToday = 95,
                        onUsageCardClick = {}
                    )
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/v133_study_header_dark.png")
    }

    @Test
    fun studyTopHeader_light_granted() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(focivoTokens().bg)
                        .padding(20.dp)
                ) {
                    StudyTopStatsHeader(
                        usageMillis = (3 * 60 + 12) * 60_000L,
                        hasUsagePermission = true,
                        focusMinutesToday = 95,
                        onUsageCardClick = {}
                    )
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/v133_study_header_light.png")
    }

    @Test
    fun studyTopHeader_dark_noPermission() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(focivoTokens().bg)
                        .padding(20.dp)
                ) {
                    StudyTopStatsHeader(
                        usageMillis = null,
                        hasUsagePermission = false,
                        focusMinutesToday = 40,
                        onUsageCardClick = {}
                    )
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/v133_study_header_no_permission_dark.png")
    }

    // ---------- Per-app budget row ----------

    @Test
    fun perAppBudgetRow_dark() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(focivoTokens().bg)
                        .padding(16.dp)
                ) {
                    Column {
                        AppRankAndLimitCard(
                            item = AppUsageRankItem(
                                appInfo = fakeApp(),
                                usageMinutesToday = 47,
                                limit = fakeLimit()
                            ),
                            onSetLimit = {},
                            onEditLimit = {},
                            onDeleteLimit = {},
                            onToggleEnabled = { _, _ -> }
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/v133_per_app_budget_row_dark.png")
    }

    @Test
    fun perAppBudgetRow_light() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(focivoTokens().bg)
                        .padding(16.dp)
                ) {
                    Column {
                        AppRankAndLimitCard(
                            item = AppUsageRankItem(
                                appInfo = fakeApp(),
                                usageMinutesToday = 47,
                                limit = fakeLimit()
                            ),
                            onSetLimit = {},
                            onEditLimit = {},
                            onDeleteLimit = {},
                            onToggleEnabled = { _, _ -> }
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/v133_per_app_budget_row_light.png")
    }

    // ---------- Break overlay ----------

    private fun renderBreakOverlay(darkTheme: Boolean) {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = darkTheme) {
                BlockedAppOverlay(
                    appName = "Instagram",
                    blockedPackage = "com.instagram.android",
                    // 2h 41m remaining on the break.
                    initialDurationSec = 2 * 3600 + 41 * 60,
                    reason = null,
                    isPunishment = false,
                    breakInfo = BreakOverlayInfo(budgetLabel = "4h"),
                    mascotRes = R.drawable.mascot_blocked,
                    nextPriority = "",
                    focusTip = "A short walk resets attention better than scrolling ever will.",
                    onExitApp = {},
                    onOpenFocusly = {}
                )
            }
        }
    }

    @Test
    fun breakOverlay_dark() {
        renderBreakOverlay(darkTheme = true)
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/v133_break_overlay_dark.png")
    }

    @Test
    fun breakOverlay_light() {
        renderBreakOverlay(darkTheme = false)
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/v133_break_overlay_light.png")
    }

    // ---------- Global-limit budget sheet ----------

    @Test
    fun budgetSheet_dark() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                ScreenTimeBudgetSheet(
                    currentBudgetMinutes = 240,
                    onBudgetSelected = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/v133_budget_sheet_dark.png")
    }

    @Test
    fun budgetSheet_light() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = false) {
                ScreenTimeBudgetSheet(
                    currentBudgetMinutes = 0,
                    onBudgetSelected = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/v133_budget_sheet_light.png")
    }
}
