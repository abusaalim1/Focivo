package com.example.screenshots.v11

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.BuddyMascot
import com.example.ui.components.CustomizeTimerSheet
import com.example.ui.components.MascotPose
import com.example.ui.components.TimerFaceHost
import com.example.ui.components.TimerFaceParams
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.MascotScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.MascotWardrobe
import com.example.util.PomodoroConfig
import com.example.util.PomodoroPhase
import com.example.util.PomodoroState
import com.example.util.TimerFaceManager
import com.example.util.TimerThemeManager
import com.example.util.WardrobeCategory
import com.example.util.wardrobeItemById
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1 screenshot round: new features (timer faces, pomodoro, mascot buddy,
 * blocked apps). Every capture is eyeballed before the round is reported.
 * PNGs go to app/src/test/screenshots/v11/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V11NewFeaturesScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun initManagers() {
        TimerThemeManager.init(context)
        TimerFaceManager.init(context)
        MascotWardrobe.init(context)
    }

    private fun fakePrefs(): UserPreferencesEntity = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 1250,
        blockedAppsList = "com.instagram.android,com.zhiliaoapp.musically,com.twitter.android"
    )

    private fun themed(dark: Boolean = true, content: @Composable () -> Unit) {
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.ui.components.ThemedScreenBackground()
                        content()
                    }
                }
            }
        }
    }

    private fun faceParams(isBreak: Boolean = false): TimerFaceParams {
        val theme = TimerThemeManager.selectedTheme.value
        return TimerFaceParams(
            totalSeconds = 1500,
            remainingSeconds = 899,
            isRunning = false,
            isBreak = isBreak,
            subjectTitle = "Deep Work Session",
            mascotPose = MascotPose.STUDYING,
            theme = theme,
            onTogglePlay = {},
            onReset = {},
            onTagClick = {},
            onTimeClick = {}
        )
    }

    @Test
    fun v11_face_ring_dark() {
        initManagers()
        themed(dark = true) {
            TimerFaceHost(faceId = "ring", params = faceParams())
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/face_ring_dark.png")
    }

    @Test
    fun v11_face_digits_dark() {
        initManagers()
        themed(dark = true) {
            TimerFaceHost(faceId = "digits", params = faceParams())
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/face_digits_dark.png")
    }

    @Test
    fun v11_face_glass_dark() {
        initManagers()
        themed(dark = true) {
            TimerFaceHost(faceId = "glass", params = faceParams())
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/face_glass_dark.png")
    }

    @Test
    fun v11_face_arc_dark() {
        initManagers()
        themed(dark = true) {
            TimerFaceHost(faceId = "arc", params = faceParams())
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/face_arc_dark.png")
    }

    @Test
    fun v11_face_editorial_light() {
        initManagers()
        themed(dark = false) {
            TimerFaceHost(faceId = "editorial", params = faceParams())
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/face_editorial_light.png")
    }

    @Test
    fun v11_face_break_phase() {
        initManagers()
        themed(dark = true) {
            TimerFaceHost(faceId = "ring", params = faceParams(isBreak = true))
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/face_ring_break_phase.png")
    }

    @Test
    fun v11_customize_timer_pomodoro_showcase() {
        initManagers()
        themed(dark = true) {
            CustomizeTimerSheet(
                onDismiss = {},
                currentFocusMinutes = 25,
                currentSubject = "Deep Study",
                ambientSound = "Rain",
                onSelectAmbientSound = {},
                onSelectMode = { _, _ -> },
                onUpdateSubject = {},
                onStartPomodoro = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/customize_timer_pomodoro_showcase.png")
    }

    @Test
    fun v11_study_timer_dressed_buddy() {
        initManagers()
        // Equip a full outfit for the buddy.
        MascotWardrobe.saveOutfit(
            mapOf(
                WardrobeCategory.CAPS to "cap_lime",
                WardrobeCategory.TOPS to "top_sweatshirt",
                WardrobeCategory.EXTRAS to "extra_glasses"
            )
        )
        themed(dark = true) {
            FocusScreen(
                remainingSeconds = 1500,
                targetSeconds = 1500,
                isRunning = false,
                currentMode = "Deep Work",
                currentTaskTitle = "Physics",
                distractionsCount = 0,
                ambientSound = "Rain",
                userStreak = 12,
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
                onUpdateTaskTitle = {},
                onOpenMascot = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/study_timer_dressed_buddy.png")
    }

    @Test
    fun v11_study_pomodoro_phase_pill() {
        initManagers()
        themed(dark = true) {
            FocusScreen(
                remainingSeconds = 300,
                targetSeconds = 300,
                isRunning = true,
                currentMode = "Short Break",
                currentTaskTitle = "Physics",
                distractionsCount = 0,
                ambientSound = "Rain",
                userStreak = 12,
                userPreferences = fakePrefs(),
                pomodoroState = PomodoroState(
                    config = PomodoroConfig(25, 5, 15, 4, "classic"),
                    phase = PomodoroPhase.SHORT_BREAK,
                    focusCompleted = 2
                ),
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
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/study_pomodoro_break_phase.png")
    }

    @Test
    fun v11_mascot_screen_default() {
        initManagers()
        MascotWardrobe.saveOutfit(emptyMap())
        themed(dark = true) {
            MascotScreen(focusPoints = 1250, onBack = {})
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/mascot_screen_default.png")
    }

    @Test
    fun v11_mascot_screen_dressed() {
        initManagers()
        MascotWardrobe.saveOutfit(
            mapOf(
                WardrobeCategory.CAPS to "cap_scholar",
                WardrobeCategory.TOPS to "top_cardigan",
                WardrobeCategory.BOTTOMS to "bottom_trousers",
                WardrobeCategory.BOOKS to "book_tome",
                WardrobeCategory.EXTRAS to "extra_glasses"
            )
        )
        themed(dark = true) {
            MascotScreen(focusPoints = 5200, onBack = {})
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/mascot_screen_dressed.png")
    }

    @Test
    fun v11_buddy_mascot_closeup() {
        initManagers()
        MascotWardrobe.saveOutfit(
            mapOf(
                WardrobeCategory.CAPS to "cap_lime",
                WardrobeCategory.TOPS to "top_sweatshirt",
                WardrobeCategory.BOTTOMS to "bottom_shorts",
                WardrobeCategory.BOOKS to "book_notebook",
                WardrobeCategory.EXTRAS to "extra_badge"
            )
        )
        themed(dark = true) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                BuddyMascot(modifier = Modifier.width(280.dp))
            }
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/buddy_mascot_dressed_closeup.png")
    }
}
