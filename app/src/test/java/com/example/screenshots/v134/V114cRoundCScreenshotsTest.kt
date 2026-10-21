package com.example.screenshots.v134

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FocusSessionEntity
import com.example.data.model.UserPreferencesEntity
import com.example.service.SessionBlockRecorder
import com.example.ui.components.FocusShieldPermissionSheet
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.FocivoShareCardPng
import com.example.ui.screens.SessionCompleteScreen
import com.example.ui.screens.ShareBlockedApp
import com.example.ui.screens.ShareScreenData
import com.example.ui.screens.loadShareScreenData
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.DailyUsagePipeline
import com.example.util.TimerThemeManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1.4c — Round C evidence (owner device-test follow-ups, 2026-10-21):
 *  C1: Session Complete shows the TRUE duration (1-minute session -> 1m,
 *      25-minute session -> 25m) on the header + all four cards.
 *  C2: Shield card lists the DISTINCT apps really blocked, with real icons
 *      and attempt counts; the launcher/installer pseudo-entries the old
 *      recorder stored (the gray HOME tile) are filtered at record time;
 *      distractions on the cards align with recorded attempts.
 *  C3: scrolling the Study page must NOT open themes; the hub opens by TAP
 *      (gear button) and the Themes row expands by TAP.
 *  C4: reconcileUsage three-source pick rule (aggregate is window-scoped
 *      and never boundary-dropped; daily buckets still are).
 *  C5: the permission sheet carries a 5th row — Usage Access (Screen Time).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V114cRoundCScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

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

    /** Seeds the exact prefs loadShareScreenData + SessionBlockRecorder read. */
    private fun seedSharePrefs(blockedJson: String) {
        val prefs = context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(
                "user_preferences",
                JSONObject()
                    .put("currentUserName", "Abu Saalim")
                    .put("currentUserEmail", "scholar@example.com")
                    .toString()
            )
            .putString("focus_sessions", "[]")
            .putString("session_blocked_apps_json", blockedJson)
            .commit()
    }

    private fun blockedJson(nowMs: Long): String {
        val arr = JSONArray()
        arr.put(JSONObject().put("ts", nowMs - 20_000).put("pkg", "com.instagram.android").put("name", "Instagram"))
        arr.put(JSONObject().put("ts", nowMs - 10_000).put("pkg", "com.instagram.android").put("name", "Instagram"))
        arr.put(JSONObject().put("ts", nowMs - 5_000).put("pkg", "com.google.android.youtube").put("name", "YouTube"))
        return arr.toString()
    }

    // ---------------- C1 + C2: Session Complete carousel ----------------

    @Test
    fun session_complete_1min() {
        val now = System.currentTimeMillis()
        seedSharePrefs(blockedJson(now))
        themed(dark = true, themeId = "midnight") {
            SessionCompleteScreen(
                session = FocusSessionEntity(
                    id = 114_001L,
                    taskTitle = "Physics",
                    durationSeconds = 60,
                    targetDurationSeconds = 60,
                    mode = "Deep Work",
                    startTime = now - 60_000L,
                    endTime = now,
                    completedAt = now,
                    distractionsCount = 0,
                    focusPointsEarned = 1
                ),
                onDismiss = {},
                animateEntrance = false
            )
        }
        capture("v114c_session_complete_1min_dark")
    }

    @Test
    fun session_complete_25min() {
        val now = System.currentTimeMillis()
        seedSharePrefs("[]")
        themed(dark = true, themeId = "midnight") {
            SessionCompleteScreen(
                session = FocusSessionEntity(
                    id = 114_002L,
                    taskTitle = "Physics",
                    durationSeconds = 1500,
                    targetDurationSeconds = 1500,
                    mode = "Deep Work",
                    startTime = now - 1_500_000L,
                    endTime = now,
                    completedAt = now,
                    distractionsCount = 0,
                    focusPointsEarned = 25
                ),
                onDismiss = {},
                animateEntrance = false
            )
        }
        capture("v114c_session_complete_25min_dark")
    }

    // ---------------- C1/C2: single share cards (PNG path) ----------------

    private fun cardData(minutes: Int, blocked: List<ShareBlockedApp>): ShareScreenData =
        ShareScreenData(
            minutes = minutes,
            distractions = blocked.sumOf { it.attempts },
            dateLabel = "Oct 21",
            userName = "Abu Saalim",
            todayTotalMinutes = minutes,
            todaySessions = 1,
            weekMinutes = listOf(0, 0, 0, 0, 0, 0, minutes),
            weekLabels = listOf("T", "F", "S", "S", "M", "T", "W"),
            blockedApps = blocked
        )

    @Test
    fun share_card_ring_1min() {
        composeTestRule.setContent {
            Box(Modifier.background(Color(0xFF0A0C0B))) {
                FocivoShareCardPng(index = 0, data = cardData(1, emptyList()))
            }
        }
        capture("v114c_share_ring_1min")
    }

    @Test
    fun share_card_shield_apps() {
        // Instagram + YouTube resolve via the curated official icons; the
        // third (unresolvable) package exercises the C2 letter-tile fallback
        // — never the system/launcher glyph.
        val apps = listOf(
            ShareBlockedApp("com.instagram.android", "Instagram", attempts = 2),
            ShareBlockedApp("com.google.android.youtube", "YouTube", attempts = 1),
            ShareBlockedApp("com.example.mysteryapp", "Mystery App", attempts = 1)
        )
        composeTestRule.setContent {
            Box(Modifier.background(Color(0xFF0A0C0B))) {
                FocivoShareCardPng(index = 3, data = cardData(25, apps))
            }
        }
        capture("v114c_share_shield_apps")
    }

    // ---------------- C5: permission sheet ----------------

    @Test
    fun permission_sheet_five_rows() {
        themed(dark = true, themeId = "midnight") {
            FocusShieldPermissionSheet(onDismiss = {}, onAllGranted = {})
        }
        capture("v114c_permission_sheet_dark")
    }

    // ---------------- C3: tap-only themes ----------------

    @Test
    fun study_swipe_does_not_open_themes_but_tap_does() {
        themed(dark = true, themeId = "midnight") {
            FocusScreen(
                remainingSeconds = 1500,
                targetSeconds = 1500,
                isRunning = false,
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
                onAddFiveMinutes = {},
                onSkipBreak = {},
                onSelectMode = { _, _ -> },
                onSelectAmbientSound = {},
                onLogDistraction = {},
                onUpdateTaskTitle = {}
            )
        }
        composeTestRule.waitForIdle()
        // A vertical swipe across the timer region (the old themes trigger):
        // the hub must NOT open.
        composeTestRule.onNodeWithTag("aurora_timer_component")
            .performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("study_hub_sheet").assertDoesNotExist()
        // Explicit TAP on the hub button opens the hub...
        composeTestRule.onNodeWithTag("study_hub_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("study_hub_sheet").assertExists()
        // ...and a TAP on the Themes row expands the Themes section.
        composeTestRule.onNodeWithTag("hub_themes_row").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Your Focus Points:", substring = true).assertExists()
    }

    // ---------------- C2: recorder truth ----------------

    @Test
    fun recorder_filters_non_app_entries_and_counts_attempts() {
        val now = System.currentTimeMillis()
        SessionBlockRecorder.record(context, "com.instagram.android", "Instagram")
        SessionBlockRecorder.record(context, "com.instagram.android", "Instagram")
        SessionBlockRecorder.record(context, "com.google.android.youtube", "YouTube")
        // Guard-internal pseudo-entries — must never reach the Shield card:
        SessionBlockRecorder.record(context, "com.bbk.launcher2", "Home Screen")
        SessionBlockRecorder.record(context, "com.android.packageinstaller", "App Installer")
        SessionBlockRecorder.record(context, context.packageName, "Focivo")

        val list = SessionBlockRecorder.forWindow(context, now - 60_000L, now + 5_000L)
        assertEquals(2, list.size)
        val insta = list.first { it.packageName == "com.instagram.android" }
        assertEquals(2, insta.attempts)
        val yt = list.first { it.packageName == "com.google.android.youtube" }
        assertEquals(1, yt.attempts)
        assertTrue(list.none { it.packageName.contains("launcher") })
        assertTrue(list.none { it.packageName.contains("packageinstaller") })
    }

    // ---------------- C1 + C2: share-data assertions ----------------

    @Test
    fun share_data_shows_true_minutes_and_aligned_distractions() {
        val now = System.currentTimeMillis()
        seedSharePrefs(blockedJson(now))
        // The owner's exact failure shape: a session RECORDED as 59s
        // (pre-C1-fix row) with a stored distractions counter of 0, while
        // the shield really intercepted 3 attempts in the window.
        val session = FocusSessionEntity(
            id = 114_003L,
            taskTitle = "Physics",
            durationSeconds = 59,
            targetDurationSeconds = 60,
            mode = "Deep Work",
            startTime = now - 59_000L,
            endTime = now,
            completedAt = now,
            distractionsCount = 0,
            focusPointsEarned = 1
        )
        val data = loadShareScreenData(context, session)
        assertEquals(1, data.minutes) // never 0m for a completed 1-minute timer
        assertEquals(1, data.todayTotalMinutes)
        assertEquals(1, data.todaySessions)
        assertEquals(2, data.blockedApps.size)
        assertEquals(2, data.blockedApps.first { it.packageName == "com.instagram.android" }.attempts)
        assertEquals(3, data.distractions) // aligned with recorded attempts

        // And the stored counter still wins when it is the larger truth.
        seedSharePrefs("[]")
        val counted = loadShareScreenData(
            context,
            session.copy(id = 114_004L, distractionsCount = 5)
        )
        assertEquals(5, counted.distractions)
        assertEquals(0, counted.blockedApps.size)
    }

    // ---------------- C4: pick rule (executes in CI) ----------------

    @Test
    fun reconcile_pick_rule_three_sources() {
        val min = 60_000L
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 12 * 60 * min

        // Aggregate is window-scoped: a pre-midnight bucket stamp must NOT
        // drop it (the silent vivo failure), and it wins when it is the
        // largest source; the discrepancy carries all three raw values.
        val agg = DailyUsagePipeline.reconcileUsage(
            paired = mapOf("com.a" to 10 * min),
            dailyBuckets = emptyList(),
            aggregateBuckets = listOf(
                DailyUsagePipeline.DailyBucketSample("com.a", 95 * min, dayStart - 6 * 60 * min)
            ),
            dayStartMs = dayStart,
            nowMs = now
        )
        assertEquals(95 * min, agg.perAppMillis["com.a"])
        assertEquals(1, agg.discrepancies.size)
        assertEquals("aggregate", agg.discrepancies[0].chosenSource)
        assertEquals(95 * min, agg.discrepancies[0].aggregateMs)

        // Daily buckets still get the boundary drop; events win over a
        // lagging aggregate.
        val mixed = DailyUsagePipeline.reconcileUsage(
            paired = mapOf("com.a" to 130 * min),
            dailyBuckets = listOf(
                DailyUsagePipeline.DailyBucketSample("com.a", 500 * min, dayStart - 1L)
            ),
            aggregateBuckets = listOf(
                DailyUsagePipeline.DailyBucketSample("com.a", 100 * min, dayStart)
            ),
            dayStartMs = dayStart,
            nowMs = now
        )
        assertEquals(130 * min, mixed.perAppMillis["com.a"])
    }
}
