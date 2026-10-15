package com.example.screenshots.v123

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import com.example.data.SupabasePunishmentLogDto
import com.example.data.SupabaseScheduledBlockDto
import com.example.ui.components.FocusShieldHubSheet
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
 * v1.2.3 Focus Shield redesign verify (branch feat/v123-splash):
 *  - full-page rebuild of the owner's clean-redesign HTML
 *  - top: header + hero + quick lock + protection
 *  - scrolled: AI protection, schedules, apps to block, activity, test shield
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V123ShieldScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun seedLogs() = listOf(
        SupabasePunishmentLogDto(
            user_id = "u1",
            app_package = "com.google.android.youtube",
            status = "warned_resolved",
            reason = "Non-study content detected"
        ),
        SupabasePunishmentLogDto(
            user_id = "u1",
            app_package = "com.openai.chatgpt",
            status = "warned",
            reason = "Casual chatter during focus"
        )
    )

    private fun seedSchedules() = listOf(
        SupabaseScheduledBlockDto(
            id = "s1",
            user_id = "u1",
            label = "Evening study",
            start_time = "18:00:00",
            end_time = "21:00:00",
            days_active = "Mon-Fri",
            is_enabled = true
        )
    )

    private fun renderShield() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                FocusShieldHubSheet(
                    isShieldEnabled = true,
                    isShieldTimerRunning = false,
                    remainingSeconds = 0,
                    blockedPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
                    blockedAttemptsCount = 3,
                    installedApps = emptyList(),
                    onToggleMasterShield = {},
                    onToggleAppBlocked = { _, _ -> },
                    onBlockAllSocial = {},
                    onUnblockAll = {},
                    onStartStandaloneShield = {},
                    onStopStandaloneShield = {},
                    onTriggerTestIntercept = {},
                    scheduledBlocks = seedSchedules(),
                    punishmentLogs = seedLogs(),
                    onSaveSchedule = {},
                    onToggleSchedule = { _, _ -> },
                    onDeleteSchedule = {},
                    onOpenAppLimits = {},
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun v123_focus_shield_top() {
        renderShield()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_focus_shield_top.png")
    }

    @Test
    fun v123_focus_shield_scrolled() {
        renderShield()
        composeTestRule.onNodeWithTag("focus_shield_list").performScrollToIndex(6)
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_focus_shield_scrolled.png")
    }
}
