package com.example.screenshots.v121

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FocusSessionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.PermissionGateSheet
import com.example.ui.components.TimerThemePicker
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MascotScreen
import com.example.ui.screens.PermissionGrantContent
import com.example.ui.screens.PermissionSpec
import com.example.ui.screens.SessionCompleteScreen
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.splash.FocivoSplash
import com.example.ui.splash.SplashVariant
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
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
 * v1.2.1 fix-round verify (branch feat/v121-fixes, NEVER merged blindly):
 *  - splash NEW_USER / RETURNING mid-frame: lime glow gone, extra outer
 *    circle behind the radar disc gone (radar rings + sweep stay)
 *  - onboarding permission step (Allow + Later)
 *  - Start-Study permission gate sheet
 *  - Focus idle + ACTIVE study state with the dressed (equipped-set) mascot
 *  - SessionComplete seeded with a real 25-min / 25-pt session
 *  - MascotScreen + themes picker WITH lock badges (re-lock proof)
 * PNGs land in app/src/test/screenshots/v121/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V121ScreenshotsTest {

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

    private fun fakePrefs(points: Int = 0): UserPreferencesEntity = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = points
    )

    private fun fakePriorities(): List<TaskEntity> = listOf(
        TaskEntity(id = 1L, title = "Physics: Units & Measurement", category = "Deep Work", priority = 1, isTopPriority = true),
        TaskEntity(id = 2L, title = "Maths: Quadratic Equations", category = "Deep Work", priority = 1, isTopPriority = true)
    )

    private fun usageSpec() = PermissionSpec(
        id = "usage",
        rowLabel = "Usage access",
        bubbleLead = "Focivo needs to see ",
        bubbleAccent = "which app is open",
        bubbleTail = " so it can block distractions the moment they appear.",
        privacyLine = "Your app usage never leaves your phone.",
        whyBody = "Android only lets apps read the foreground app with your explicit permission. Focivo uses it purely on-device to intercept blocked apps during focus sessions.",
        pose = com.example.ui.components.MascotPose.GUARD
    )

    // -- Fix #3: splash mid-frames (glow gone) --------------------------------

    @Test
    fun v121_splash_new_user_midframe() {
        initManagers()
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                FocivoSplash(
                    variant = SplashVariant.NEW_USER,
                    onFinished = {},
                    testProgress = 0.45f
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v121/v121_splash_new_user_midframe.png")
    }

    @Test
    fun v121_splash_returning_midframe() {
        initManagers()
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                FocivoSplash(
                    variant = SplashVariant.RETURNING,
                    onFinished = {},
                    testProgress = 0.55f
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v121/v121_splash_returning_midframe.png")
    }

    // -- Fix #2: onboarding permission step -----------------------------------

    @Test
    fun v121_onboarding_permission_step() {
        initManagers()
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                PermissionGrantContent(
                    spec = usageSpec(),
                    progress = 0.8f,
                    onAllow = {},
                    onLater = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v121/v121_onboarding_permission_step.png")
    }

    // -- Fix #1: permission gate sheet ----------------------------------------

    @Test
    fun v121_permission_gate_sheet() {
        initManagers()
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                PermissionGateSheet(
                    missingIds = listOf("usage", "overlay", "accessibility"),
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v121/v121_permission_gate_sheet.png")
    }

    // -- Fix #4: dressed mascot idle + active ----------------------------------

    @Test
    fun v121_focus_idle_dressed() {
        initManagers()
        TimerThemeManager.selectTheme(context, "aurora")
        themed(dark = true) {
            FocusScreen(
                remainingSeconds = 25 * 60,
                targetSeconds = 25 * 60,
                isRunning = false,
                currentMode = "Deep Work",
                currentTaskTitle = "Physics: Units & Measurement",
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
                onUpdateTaskTitle = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v121/v121_focus_idle_dressed.png")
    }

    @Test
    fun v121_focus_active_studying_dressed() {
        initManagers()
        TimerThemeManager.selectTheme(context, "aurora")
        themed(dark = true) {
            FocusScreen(
                remainingSeconds = 18 * 60 + 24,
                targetSeconds = 25 * 60,
                isRunning = true,
                currentMode = "Deep Work",
                currentTaskTitle = "Physics: Units & Measurement",
                distractionsCount = 1,
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
                onUpdateTaskTitle = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v121/v121_focus_active_studying_dressed.png")
    }

    // -- Fix #6: session complete with REAL duration + points -------------------

    @Test
    fun v121_session_complete_25min() {
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
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v121/v121_session_complete_25min.png")
    }

    // -- Fix #9: re-lock proof --------------------------------------------------

    @Test
    fun v121_mascot_screen_locked() {
        initManagers()
        themed(dark = true) {
            MascotScreen(
                focusPoints = 0,
                onBack = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v121/v121_mascot_screen_locked.png")
    }

    @Test
    fun v121_themes_picker_locked() {
        initManagers()
        TimerThemeManager.selectTheme(context, "aurora")
        themed(dark = true) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                TimerThemePicker(
                    focusPoints = 0,
                    selectedThemeId = "aurora",
                    onThemeTap = { _, _ -> },
                    textPrimary = androidx.compose.ui.graphics.Color(0xFFF0F4ED),
                    cardBorder = androidx.compose.ui.graphics.Color(0xFFDCE6D2)
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v121/v121_themes_picker_locked.png")
    }

    // -- Sanity: home still renders --------------------------------------------

    @Test
    fun v121_home() {
        initManagers()
        TimerThemeManager.selectTheme(context, "aurora")
        themed(dark = true) {
            HomeScreen(
                topPriorities = fakePriorities(),
                userPreferences = fakePrefs(),
                sessions = emptyList(),
                scheduleStatusSummary = "Evening Deep Focus · 6:00 PM",
                scheduledBlocksCount = 2,
                onToggleTask = {},
                onStartFocus = { _, _ -> },
                onCustomizeFocus = {},
                onOpenPlanner = {},
                onOpenTaskCreate = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v121/v121_home.png")
    }
}
