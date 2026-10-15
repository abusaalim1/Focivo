package com.example.screenshots.v122

import android.content.Context
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
}
