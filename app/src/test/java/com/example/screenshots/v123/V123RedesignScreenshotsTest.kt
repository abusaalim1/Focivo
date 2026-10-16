package com.example.screenshots.v123

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.ui.components.AiStudyWarningDialog
import com.example.ui.components.CustomizeTimerSheet
import com.example.ui.components.DeepFocusSettingsSheet
import com.example.ui.components.SupportLockZenSheet
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.AuthMode
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FocusSessionLogScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SessionCompleteScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.ActiveAiWarning
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
 * v1.2.3 full-app redesign verify (branch feat/v123-splash):
 * Every redesigned surface rendered in BOTH dark and light theme so the
 * owner can verify the token system (#0A0A0B/#131315/#B6F13A dark,
 * #F7F7F5/#FFFFFF/#B6F13A light) — contrast and clipping checked per shot.
 * PNGs land in app/src/test/screenshots/v123/.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V123RedesignScreenshotsTest {

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

    private fun fakePrefs() = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 240,
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

    private fun fakeLeaderboard() = listOf(
        LeaderboardUser(displayName = "Aarav", weeklyStudySeconds = 19200, streak = 12, isCurrentUser = false),
        LeaderboardUser(displayName = "Abu Saalim", weeklyStudySeconds = 17100, streak = 9, isCurrentUser = true),
        LeaderboardUser(displayName = "Zoya", weeklyStudySeconds = 14400, streak = 7, isCurrentUser = false),
        LeaderboardUser(displayName = "Kabir", weeklyStudySeconds = 10800, streak = 5, isCurrentUser = false),
        LeaderboardUser(displayName = "Meera", weeklyStudySeconds = 7200, streak = 3, isCurrentUser = false)
    )

    private fun shot(dark: Boolean, name: String, content: @Composable () -> Unit) {
        initAll()
        themed(dark, content)
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/$name.png")
    }

    // ── Smoke test: theme setup ───────────────────────────────────────────
    @Test
    fun v123_redesign_smoke() = shot(true, "v123_redesign_smoke") {
        Box(modifier = Modifier.fillMaxSize())
    }

    // ── Home ──────────────────────────────────────────────────────────────
    @Test
    fun v123_redesign_home_dark() = shot(true, "v123_redesign_home_dark") {
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

    @Test
    fun v123_redesign_home_light() = shot(false, "v123_redesign_home_light") {
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

    // ── Leaderboard ───────────────────────────────────────────────────────
    @Test
    fun v123_redesign_leaderboard_dark() = shot(true, "v123_redesign_leaderboard_dark") {
        LeaderboardScreen(
            users = fakeLeaderboard(),
            currentUserName = "Abu Saalim",
            currentUserPoints = 285,
            currentUserStreak = 9,
            sessions = fakeSessions()
        )
    }

    @Test
    fun v123_redesign_leaderboard_light() = shot(false, "v123_redesign_leaderboard_light") {
        LeaderboardScreen(
            users = fakeLeaderboard(),
            currentUserName = "Abu Saalim",
            currentUserPoints = 285,
            currentUserStreak = 9,
            sessions = fakeSessions()
        )
    }

    // ── Auth ──────────────────────────────────────────────────────────────
    @Test
    fun v123_redesign_auth_signin_dark() = shot(true, "v123_redesign_auth_signin_dark") {
        AuthScreen(
            initialMode = AuthMode.SignIn,
            isLoading = false,
            errorMessage = null,
            onLogin = { _, _ -> },
            onRegister = { _, _, _ -> },
            onClearError = {}
        )
    }

    @Test
    fun v123_redesign_auth_signin_light() = shot(false, "v123_redesign_auth_signin_light") {
        AuthScreen(
            initialMode = AuthMode.SignIn,
            isLoading = false,
            errorMessage = null,
            onLogin = { _, _ -> },
            onRegister = { _, _, _ -> },
            onClearError = {}
        )
    }

    @Test
    fun v123_redesign_auth_create_dark() = shot(true, "v123_redesign_auth_create_dark") {
        AuthScreen(
            initialMode = AuthMode.CreateAccount,
            isLoading = false,
            errorMessage = null,
            onLogin = { _, _ -> },
            onRegister = { _, _, _ -> },
            onClearError = {}
        )
    }

    @Test
    fun v123_redesign_auth_create_light() = shot(false, "v123_redesign_auth_create_light") {
        AuthScreen(
            initialMode = AuthMode.CreateAccount,
            isLoading = false,
            errorMessage = null,
            onLogin = { _, _ -> },
            onRegister = { _, _, _ -> },
            onClearError = {}
        )
    }

    // ── Theme picker (timer faces) ────────────────────────────────────────
    @Test
    fun v123_redesign_themes_dark() = shot(true, "v123_redesign_themes_dark") {
        DeepFocusSettingsSheet(
            onDismiss = {},
            initialShowThemePicker = true
        )
    }

    @Test
    fun v123_redesign_themes_light() = shot(false, "v123_redesign_themes_light") {
        DeepFocusSettingsSheet(
            onDismiss = {},
            initialShowThemePicker = true
        )
    }

    // ── Customize timer ───────────────────────────────────────────────────
    @Test
    fun v123_redesign_customize_dark() = shot(true, "v123_redesign_customize_dark") {
        CustomizeTimerSheet(
            onDismiss = {},
            currentFocusMinutes = 25,
            currentSubject = "Physics",
            ambientSound = "Rain",
            onSelectAmbientSound = {},
            onSelectMode = { _, _ -> },
            onUpdateSubject = {}
        )
    }

    @Test
    fun v123_redesign_customize_light() = shot(false, "v123_redesign_customize_light") {
        CustomizeTimerSheet(
            onDismiss = {},
            currentFocusMinutes = 25,
            currentSubject = "Physics",
            ambientSound = "Rain",
            onSelectAmbientSound = {},
            onSelectMode = { _, _ -> },
            onUpdateSubject = {}
        )
    }

    // ── Profile ───────────────────────────────────────────────────────────
    @Test
    fun v123_redesign_profile_dark() = shot(true, "v123_redesign_profile_dark") {
        ProfileScreen(
            userPreferences = fakePrefs(),
            sessions = fakeSessions(),
            onOpenSettings = {}
        )
    }

    @Test
    fun v123_redesign_profile_light() = shot(false, "v123_redesign_profile_light") {
        ProfileScreen(
            userPreferences = fakePrefs(),
            sessions = fakeSessions(),
            onOpenSettings = {}
        )
    }

    // ── Guard warning dialog (smaller surface) ────────────────────────────
    @Test
    fun v123_redesign_guard_warning_dark() = shot(true, "v123_redesign_guard_warning_dark") {
        AiStudyWarningDialog(
            warning = ActiveAiWarning(
                logId = "test",
                packageName = "com.instagram.android",
                appName = "Instagram",
                reason = "Non-study activity detected.",
                warnedAtMillis = System.currentTimeMillis(),
                warnedAtIso = "",
                graceExpiresAtMillis = System.currentTimeMillis() + 90_000L
            ),
            onReturnToStudy = {},
            onDismiss = {}
        )
    }

    @Test
    fun v123_redesign_guard_warning_light() = shot(false, "v123_redesign_guard_warning_light") {
        AiStudyWarningDialog(
            warning = ActiveAiWarning(
                logId = "test",
                packageName = "com.instagram.android",
                appName = "Instagram",
                reason = "Non-study activity detected.",
                warnedAtMillis = System.currentTimeMillis(),
                warnedAtIso = "",
                graceExpiresAtMillis = System.currentTimeMillis() + 90_000L
            ),
            onReturnToStudy = {},
            onDismiss = {}
        )
    }

    // ── Donation / support sheet (smaller surface) ────────────────────────
    @Test
    fun v123_redesign_donate_dark() = shot(true, "v123_redesign_donate_dark") {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        SupportLockZenSheet(sheetState = sheetState, onDismiss = {})
    }

    @Test
    fun v123_redesign_donate_light() = shot(false, "v123_redesign_donate_light") {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        SupportLockZenSheet(sheetState = sheetState, onDismiss = {})
    }

    // ── Session complete (dark-always share artwork) ──────────────────────
    @Test
    fun v123_redesign_session_complete() = shot(true, "v123_redesign_session_complete") {
        SessionCompleteScreen(
            session = FocusSessionEntity(
                taskTitle = "Physics",
                mode = "Deep Work",
                durationSeconds = 1500,
                focusPointsEarned = 25,
                distractionsCount = 1,
                completedAt = System.currentTimeMillis()
            ),
            onDismiss = {},
            animateEntrance = false
        )
    }

    // ── Past Focus Sessions log (owner report 2026-10-16: was still old design)
    @Test
    fun v123_redesign_session_log_dark() = shot(true, "v123_redesign_session_log_dark") {
        FocusSessionLogScreen(
            sessions = fakeSessions(),
            onBack = {},
            onStartNewSession = {},
            onDeleteSession = {}
        )
    }

    @Test
    fun v123_redesign_session_log_light() = shot(false, "v123_redesign_session_log_light") {
        FocusSessionLogScreen(
            sessions = fakeSessions(),
            onBack = {},
            onStartNewSession = {},
            onDeleteSession = {}
        )
    }

    @Test
    fun v123_redesign_session_log_empty_dark() = shot(true, "v123_redesign_session_log_empty_dark") {
        FocusSessionLogScreen(
            sessions = emptyList(),
            onBack = {},
            onStartNewSession = {},
            onDeleteSession = {}
        )
    }

    @Test
    fun v123_redesign_session_log_empty_light() = shot(false, "v123_redesign_session_log_empty_light") {
        FocusSessionLogScreen(
            sessions = emptyList(),
            onBack = {},
            onStartNewSession = {},
            onDeleteSession = {}
        )
    }
}
