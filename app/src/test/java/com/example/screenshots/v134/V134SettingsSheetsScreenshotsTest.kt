package com.example.screenshots.v134

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.DailyTargetsSheet
import com.example.ui.components.SessionFeedbackSheet
import com.example.ui.components.StudyNotificationsSheet
import com.example.ui.theme.FocuslyTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.3.4 ITEM 9 — Settings consolidation: the three merged sheets.
 *
 * Each merged entry opens ONE premium sheet (the Block-sheet pattern):
 * - DailyTargetsSheet: focus-goal chips + screen-time budget row
 *   (merges the old "Daily Focus Target" + "Daily Screen Time" cards).
 * - SessionFeedbackSheet: haptics + chimes toggles
 *   (merges the old scattered "Haptic Feedback" + "Local Session Chimes").
 * - StudyNotificationsSheet: block toggle + DND/test-silence action
 *   (the old "Block Notifications in Study" block, now its own entry).
 *
 * Eyeball EVERY PNG: premium design-system sheet, Poppins body, Instrument
 * Serif italic title, English only, no emojis; every previously reachable
 * control present. The merged Settings screen itself is captured by the
 * existing V132UiSweepScreenshotsTest settings/settings_light tests
 * (re-record after this round).
 *
 * PNGs land in app/src/test/screenshots/v134/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V134SettingsSheetsScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun capture(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v134/$name.png"
        )
    }

    private fun shot(dark: Boolean, name: String, sheet: @Composable () -> Unit) {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) { sheet() }
        }
        capture(name)
    }

    @Test
    fun dailyTargets_dark() = shot(dark = true, name = "sheet_daily_targets_dark") {
        DailyTargetsSheet(
            currentGoalMinutes = 360,
            onUpdateDailyGoal = {},
            budgetMinutes = 180,
            onOpenScreenTimeBudget = {},
            onDismiss = {}
        )
    }

    @Test
    fun dailyTargets_light() = shot(dark = false, name = "sheet_daily_targets_light") {
        DailyTargetsSheet(
            currentGoalMinutes = 360,
            onUpdateDailyGoal = {},
            budgetMinutes = 0,
            onOpenScreenTimeBudget = {},
            onDismiss = {}
        )
    }

    @Test
    fun sessionFeedback_dark() = shot(dark = true, name = "sheet_session_feedback_dark") {
        SessionFeedbackSheet(
            hapticsEnabled = true,
            onUpdateHaptics = {},
            chimesEnabled = false,
            onUpdateChimes = {},
            onDismiss = {}
        )
    }

    @Test
    fun sessionFeedback_light() = shot(dark = false, name = "sheet_session_feedback_light") {
        SessionFeedbackSheet(
            hapticsEnabled = true,
            onUpdateHaptics = {},
            chimesEnabled = false,
            onUpdateChimes = {},
            onDismiss = {}
        )
    }

    @Test
    fun studyNotifications_dark() = shot(dark = true, name = "sheet_study_notifications_dark") {
        StudyNotificationsSheet(
            blockEnabled = true,
            onBlockChange = {},
            hasDndPermission = true,
            onDndButtonClick = {},
            onDismiss = {}
        )
    }

    @Test
    fun studyNotifications_light() = shot(dark = false, name = "sheet_study_notifications_light") {
        StudyNotificationsSheet(
            blockEnabled = false,
            onBlockChange = {},
            hasDndPermission = false,
            onDndButtonClick = {},
            onDismiss = {}
        )
    }
}
