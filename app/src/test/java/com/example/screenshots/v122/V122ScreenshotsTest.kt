package com.example.screenshots.v122

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FocusSessionEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.AiStudyWarningDialog
import com.example.ui.components.DonationDialog
import com.example.ui.components.ShortsBlockedOverlay
import com.example.ui.components.StrictUninstallBlockedDialog
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.SessionCompleteScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.ActiveAiWarning
import com.example.util.MascotWardrobe
import com.example.util.TimerFaceManager
import com.example.util.TimerThemeManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.screens.ShareBlockedApp
import com.example.ui.screens.ShareCard
import com.example.ui.screens.ShareScreenData
import org.json.JSONArray
import org.json.JSONObject

/**
 * v1.2.2 fix-round verify (branch feat/v122-fixes, NEVER merged blindly):
 *  - AiStudyWarningDialog: new premium-minimal guard warning (G)
 *  - DonationDialog: wired automatic popup (E)
 *  - StrictUninstallBlockedDialog: in-app strict-mode popup (A)
 *  - ShortsBlockedOverlay: new standalone surface block (C)
 *  - SessionCompleteScreen: real WhatsApp logo + confetti celebration (F/D)
 *  - FocusScreen idle: regression sanity
 * PNGs land in app/src/test/screenshots/v122/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V122ScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun initManagers() {
        TimerThemeManager.init(context)
        TimerFaceManager.init(context)
        MascotWardrobe.init(context)
    }

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

    // -- G: premium-minimal guard warning ------------------------------------

    @Test
    fun v122_guard_warning_minimal() {
        initManagers()
        val warning = ActiveAiWarning(
            logId = "test-1",
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            reason = "Non-study video detected",
            warnedAtMillis = System.currentTimeMillis(),
            warnedAtIso = "2026-10-15T18:04:00",
            graceExpiresAtMillis = System.currentTimeMillis() + (2 * 60 + 48) * 1000L
        )
        themed(dark = true) {
            AiStudyWarningDialog(
                warning = warning,
                onReturnToStudy = {},
                onDismiss = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v122/v122_guard_warning_minimal.png")
    }

    // -- E: donation popup ----------------------------------------------------

    @Test
    fun v122_donation_popup() {
        initManagers()
        themed(dark = true) {
            DonationDialog(onDismiss = {})
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v122/v122_donation_popup.png")
    }

    // -- A: strict-mode uninstall popup ---------------------------------------

    @Test
    fun v122_strict_uninstall_dialog() {
        initManagers()
        themed(dark = true) {
            StrictUninstallBlockedDialog(onDismiss = {})
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v122/v122_strict_uninstall_dialog.png")
    }

    // -- C: shorts blocked overlay ---------------------------------------------

    @Test
    fun v122_shorts_blocked() {
        initManagers()
        themed(dark = true) {
            ShortsBlockedOverlay(
                surfaceLabel = "YouTube Shorts",
                onReturn = {},
                onOpenFocivo = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v122/v122_shorts_blocked.png")
    }

    // -- F/D: session complete with real WhatsApp logo + confetti --------------

    @Test
    fun v122_session_complete_whatsapp() {
        initManagers()
        val session = FocusSessionEntity(
            taskTitle = "Physics: Units & Measurement",
            mode = "Deep Work",
            durationSeconds = 25 * 60,
            targetDurationSeconds = 25 * 60,
            distractionsCount = 1,
            focusPointsEarned = 25,
            completedAt = System.currentTimeMillis()
        )
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                SessionCompleteScreen(
                    session = session,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v122/v122_session_complete_whatsapp.png")
    }

    // -- Sanity: focus idle still renders ---------------------------------------

    @Test
    fun v122_focus_idle() {
        initManagers()
        TimerThemeManager.selectTheme(context, "aurora")
        val prefs = UserPreferencesEntity(
            currentUserName = "Abu Saalim",
            currentUserEmail = "scholar@example.com",
            dailyGoalMinutes = 360,
            focusPoints = 0
        )
        themed(dark = true) {
            FocusScreen(
                remainingSeconds = 25 * 60,
                targetSeconds = 25 * 60,
                isRunning = false,
                currentMode = "Deep Work",
                currentTaskTitle = "Physics: Units & Measurement",
                distractionsCount = 0,
                ambientSound = "Rain",
                userStreak = 7,
                userPreferences = prefs,
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
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v122/v122_focus_idle.png")
    }

    // -- Owner's share-screen design (native Compose port, v1.2.2) -------------

    private fun seedSharePrefs(sessionStartMs: Long) {
        val prefs = context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(
                "user_preferences",
                JSONObject().put("currentUserName", "Abu Saalim").put("currentStreak", 7).toString()
            )
            .apply()
        // focus_sessions: one earlier session today + 6 previous days
        val now = System.currentTimeMillis()
        val sessions = JSONArray()
        fun add(mins: Int, atMs: Long) {
            sessions.put(
                JSONObject()
                    .put("id", atMs)
                    .put("completedAt", atMs)
                    .put("durationSeconds", mins * 60)
            )
        }
        add(45, now - 3 * 3600 * 1000L)
        val past = listOf(30, 50, 20, 65, 40, 15)
        past.forEachIndexed { i, mins ->
            add(mins, now - (i + 1) * 24L * 3600 * 1000 - 2 * 3600 * 1000L)
        }
        prefs.edit().putString("focus_sessions", sessions.toString()).apply()
        // session_blocked_apps_json: 3 real intercepts inside the session window
        val blocked = JSONArray()
        listOf(
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube",
            "com.zhiliaoapp.musically" to "TikTok"
        ).forEachIndexed { i, (pkg, name) ->
            blocked.put(
                JSONObject()
                    .put("ts", sessionStartMs + (i + 1) * 5 * 60 * 1000L)
                    .put("pkg", pkg)
                    .put("name", name)
            )
        }
        prefs.edit().putString("session_blocked_apps_json", blocked.toString()).apply()
    }

    private fun shareTestSession(): FocusSessionEntity {
        val now = System.currentTimeMillis()
        return FocusSessionEntity(
            taskTitle = "Physics: Units & Measurement",
            mode = "Deep Work",
            durationSeconds = 25 * 60,
            targetDurationSeconds = 25 * 60,
            distractionsCount = 1,
            focusPointsEarned = 25,
            startTime = now - 25 * 60 * 1000L,
            endTime = now,
            completedAt = now
        )
    }

    private fun shareTestData(): ShareScreenData = ShareScreenData(
        minutes = 25,
        distractions = 1,
        dateLabel = "Oct 15",
        userName = "Abu Saalim",
        todayTotalMinutes = 95,
        todaySessions = 3,
        streakDays = 7,
        weekMinutes = listOf(30, 50, 20, 65, 40, 15, 95),
        weekLabels = listOf("F", "S", "S", "M", "T", "W", "T"),
        blockedApps = listOf(
            ShareBlockedApp("com.instagram.android", "Instagram"),
            ShareBlockedApp("com.google.android.youtube", "YouTube"),
            ShareBlockedApp("com.zhiliaoapp.musically", "TikTok")
        )
    )

    @Test
    fun v122_share_screen_full() {
        initManagers()
        val session = shareTestSession()
        seedSharePrefs(session.startTime)
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                SessionCompleteScreen(
                    session = session,
                    onDismiss = {},
                    animateEntrance = false
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v122/v122_share_screen_full.png")
    }

    @Test
    fun v122_share_card_ring() {
        val data = shareTestData()
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .size(360.dp, 450.dp)
                        .background(Color(0xFF0A0C0B))
                ) {
                    ShareCard(index = 0, data = data, scale = 1f)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v122/v122_share_card_ring.png")
    }

    @Test
    fun v122_share_card_today() {
        val data = shareTestData()
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .size(360.dp, 450.dp)
                        .background(Color(0xFF0A0C0B))
                ) {
                    ShareCard(index = 1, data = data, scale = 1f)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v122/v122_share_card_today.png")
    }

    @Test
    fun v122_share_card_shield() {
        val data = shareTestData()
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .size(360.dp, 450.dp)
                        .background(Color(0xFF0A0C0B))
                ) {
                    ShareCard(index = 3, data = data, scale = 1f)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v122/v122_share_card_shield.png")
    }
}
