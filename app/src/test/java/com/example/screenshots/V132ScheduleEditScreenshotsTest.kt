package com.example.screenshots

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.data.SupabaseScheduledBlockDto
import com.example.ui.components.ScheduleEditSheet
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.DeviceAppInfo
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
 * v1.3.2 ITEM 9 — schedule edit blocked-apps picker parity.
 *
 * Captures ScheduleEditSheet (the single sheet behind both create and edit)
 * at real phone size (Pixel 8 qualifiers) in app-dark ("midnight") and
 * app-light ("aurora"), scrolled to the Blocked Apps picker section:
 * - edit sheet with a fake schedule whose blocked_apps_list is pre-filled
 *   (3 apps checked, "Blocked Apps (3/8)" header)
 * - create sheet for parity comparison (distracting apps pre-selected)
 *
 * Investigation finding: the sheet already renders the picker identically in
 * both modes and persists blocked_apps_list on save — this test locks that
 * parity in so it can be eyeballed. No feature changes; captures only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V132ScheduleEditScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun fakeApps(): List<DeviceAppInfo> = listOf(
        DeviceAppInfo("com.instagram.android", "Instagram", null, category = "Social", isBlocked = false),
        DeviceAppInfo("com.zhiliaoapp.musically", "TikTok", null, category = "Social", isBlocked = false),
        DeviceAppInfo("com.google.android.youtube", "YouTube", null, category = "Video", isBlocked = false),
        DeviceAppInfo("com.snapchat.android", "Snapchat", null, category = "Social", isBlocked = false),
        DeviceAppInfo("com.reddit.frontpage", "Reddit", null, category = "Social", isBlocked = false),
        DeviceAppInfo("com.supercell.clashofclans", "Clash of Clans", null, category = "Game", isBlocked = false),
        DeviceAppInfo("com.whatsapp", "WhatsApp", null, category = "Social", isBlocked = false),
        DeviceAppInfo("com.spotify.music", "Spotify", null, category = "Music", isBlocked = false)
    )

    private fun fakeSchedule(): SupabaseScheduledBlockDto = SupabaseScheduledBlockDto(
        id = "sched-test-001",
        user_id = "local_user",
        label = "Evening Deep Study",
        subject = "Physics",
        start_time = "18:00",
        end_time = "22:00",
        break_start_time = "20:00",
        break_end_time = "20:30",
        days_active = "Mon,Tue,Wed,Thu,Fri",
        is_enabled = true,
        blocked_apps_list = "com.instagram.android,com.zhiliaoapp.musically,com.google.android.youtube",
        is_strict_mode = true,
        created_at = null
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

    private fun shotPickerSection(dark: Boolean, name: String, schedule: SupabaseScheduledBlockDto?) {
        themed(dark, if (dark) "midnight" else "aurora") {
            ScheduleEditSheet(
                schedule = schedule,
                existingSchedules = listOf(fakeSchedule()),
                installedApps = fakeApps(),
                onSave = {},
                onDelete = {},
                onDismiss = {}
            )
        }
        composeTestRule.waitForIdle()
        // Scroll the sheet's LazyColumn down to the Blocked Apps picker section.
        composeTestRule.onRoot().performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v132_schedule_edit/$name.png"
        )
    }

    @Test
    fun editSheet_blockedApps_dark() {
        shotPickerSection(dark = true, name = "schedule_edit_blocked_apps_dark", schedule = fakeSchedule())
    }

    @Test
    fun editSheet_blockedApps_light() {
        shotPickerSection(dark = false, name = "schedule_edit_blocked_apps_light", schedule = fakeSchedule())
    }

    @Test
    fun createSheet_blockedApps_dark() {
        shotPickerSection(dark = true, name = "schedule_create_blocked_apps_dark", schedule = null)
    }

    @Test
    fun createSheet_blockedApps_light() {
        shotPickerSection(dark = false, name = "schedule_create_blocked_apps_light", schedule = null)
    }
}
