package com.example.screenshots.v123

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.TimerFacePicker
import com.example.ui.theme.PoppinsFontFamily
import kotlinx.coroutines.runBlocking
import com.example.data.model.FocusSessionEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.splash.FocivoSplash
import com.example.ui.splash.SplashVariant
import com.example.ui.splash.WelcomeInfo
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.ui.timer.PomodoroStyle
import com.example.util.MascotWardrobe
import com.example.util.PomodoroConfig
import com.example.util.PomodoroPhase
import com.example.util.PomodoroState
import com.example.util.PomodoroStyleManager
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
import java.util.Calendar

/**
 * v1.2.3 splash verify (branch feat/v123-splash, NEVER merged blindly):
 *  - splash NEW_USER mid-frame: radar WITHOUT the lime glow and WITHOUT the
 *    filled disc body (the 1.2.1 owner removal stays in effect)
 *  - splash RETURNING: the NEW WelcomeScene (aurora + greeting + real-data
 *    today pill) with seeded WelcomeInfo(name="Abu Saalim",
 *    todayMinutes=95), captured late in the animation
 * PNGs land in app/src/test/screenshots/v123/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V123ScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun v123_splash_new_user_midframe() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                FocivoSplash(
                    variant = SplashVariant.NEW_USER,
                    onFinished = {},
                    testProgress = 0.45f
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_splash_new_user_midframe.png")
    }

    @Test
    fun v123_splash_returning_welcome() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                FocivoSplash(
                    variant = SplashVariant.RETURNING,
                    welcome = WelcomeInfo(name = "Abu Saalim", todayMinutes = 95),
                    onFinished = {},
                    testProgress = 0.85f
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_splash_returning_welcome.png")
    }

    // ------------------------------------------------------------------
    // v1.2.3 timer UI round (branch feat/v123-splash, NEVER merged blindly):
    //  - Customize Timer sheet: "Timer Style" picker scrolled to the nine
    //    new FocivoClock faces; Horizon (250 pts) renders LOCKED at 0 pts.
    //  - Focus screen with the Flip clock face applied.
    //  - Focus screen with the Liquid clock face applied.
    //  - Focus screen during an active pomodoro FOCUS phase (TICK_RING).
    //  - Focus screen during a pomodoro SHORT break (ACTIVITY_RINGS).
    // PNGs land in app/src/test/screenshots/v123/.
    // ------------------------------------------------------------------

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun initManagers() {
        TimerThemeManager.init(context)
        TimerFaceManager.init(context)
        MascotWardrobe.init(context)
        PomodoroStyleManager.init(context)
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

    private fun focusPrefs() = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 0
    )

    private fun focusScreen(
        faceId: String,
        pomoState: PomodoroState?,
        remainingSeconds: Int,
        targetSeconds: Int,
        isRunning: Boolean,
        currentMode: String
    ) {
        initManagers()
        TimerThemeManager.selectTheme(context, "aurora")
        TimerFaceManager.selectFace(context, faceId)
        themed(dark = true) {
            FocusScreen(
                remainingSeconds = remainingSeconds,
                targetSeconds = targetSeconds,
                isRunning = isRunning,
                currentMode = currentMode,
                currentTaskTitle = "Physics: Units & Measurement",
                distractionsCount = 0,
                ambientSound = "Rain",
                userPreferences = focusPrefs(),
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
                pomodoroState = pomoState
            )
        }
    }

    @Test
    fun v123_customize_timer_new_faces() {
        initManagers()
        TimerThemeManager.selectTheme(context, "aurora")
        // The real TimerFacePicker from the Customize Timer sheet, pre-
        // scrolled to the nine new clock faces (touch injection is
        // unreliable under Robolectric, so the row exposes a test-hook
        // ScrollState). Fresh prefs carry 0 Focus Points, so Horizon
        // (250 pts) renders with the gold lock + price.
        val pickerScroll = ScrollState(0)
        val textPrimary = Color(0xFFF0F4ED)
        val cardBorder = Color(0x228CE000)
        themed(dark = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1410))
                    .padding(20.dp)
                    .testTag("timer_style_section")
            ) {
                Text(
                    text = "TIMER STYLE",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    color = Color(0xFFA0A89E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TimerFacePicker(
                    focusPoints = 0,
                    selectedFaceId = "clock_flip",
                    onFaceTap = { _, _ -> },
                    textPrimary = textPrimary,
                    cardBorder = cardBorder,
                    scrollState = pickerScroll
                )
            }
        }
        composeTestRule.waitForIdle()
        // Cards are 104dp wide + 12dp spacing; Horizon is index 10.
        val targetPx = with(composeTestRule.density) { (10 * 116).dp.roundToPx() }
        runBlocking { pickerScroll.scrollTo(targetPx) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("timer_style_section", useUnmergedTree = true)
            .captureRoboImage("src/test/screenshots/v123/v123_customize_timer_new_faces.png")
    }

    @Test
    fun v123_focus_flip_face() {
        focusScreen(
            faceId = "clock_flip",
            pomoState = null,
            remainingSeconds = 25 * 60,
            targetSeconds = 25 * 60,
            isRunning = false,
            currentMode = "Deep Work"
        )
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_focus_flip_face.png")
    }

    @Test
    fun v123_focus_liquid_face() {
        focusScreen(
            faceId = "clock_liquid",
            pomoState = null,
            remainingSeconds = 18 * 60 + 24,
            targetSeconds = 25 * 60,
            isRunning = true,
            currentMode = "Deep Work"
        )
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_focus_liquid_face.png")
    }

    @Test
    fun v123_pomodoro_focus_tickring() {
        initManagers()
        PomodoroStyleManager.selectStyle(context, PomodoroStyle.TICK_RING)
        focusScreen(
            faceId = "ring",
            pomoState = PomodoroState(
                config = PomodoroConfig(25, 5, 15, 4, null),
                phase = PomodoroPhase.FOCUS,
                focusCompleted = 0
            ),
            remainingSeconds = 17 * 60,
            targetSeconds = 25 * 60,
            isRunning = true,
            currentMode = "Focus"
        )
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_pomodoro_focus_tickring.png")
    }

    @Test
    fun v123_pomodoro_short_activityrings() {
        initManagers()
        PomodoroStyleManager.selectStyle(context, PomodoroStyle.ACTIVITY_RINGS)
        focusScreen(
            faceId = "ring",
            pomoState = PomodoroState(
                config = PomodoroConfig(25, 5, 15, 4, null),
                phase = PomodoroPhase.SHORT_BREAK,
                focusCompleted = 1
            ),
            remainingSeconds = 3 * 60,
            targetSeconds = 5 * 60,
            isRunning = true,
            currentMode = "Short Break"
        )
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_pomodoro_short_activityrings.png")
    }

    // ------------------------------------------------------------------
    // v1.2.3 Insights redesign (branch feat/v123-splash, NEVER merged blindly):
    //  - Supplied Claude design (Header/Hero ring/WeekCard/StatsRow/Peak row/
    //    Session history/By subject/TimeOfDay) rebuilt on the app font pair:
    //    Poppins body, Instrument Serif italic highlight text only.
    //  - All numbers come from REAL aggregates via buildInsightsState()
    //    (seeded FocusSessionEntity list below).
    //  - FloatingNav is intentionally NOT rendered (app keeps its own nav).
    // PNGs land in app/src/test/screenshots/v123/.
    // ------------------------------------------------------------------

    private fun seedInsightsSessions(): List<FocusSessionEntity> {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY)
        val dayMs = 24L * 3600 * 1000
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekStart = todayStart - daysFromMonday * dayMs
        val prevWeekStart = weekStart - 7 * dayMs

        fun mk(dayIdx: Int, hour: Int, minutes: Int, mode: String, title: String, base: Long = weekStart): FocusSessionEntity {
            val end = base + dayIdx * dayMs + hour * 3600_000L + 30 * 60_000L
            return FocusSessionEntity(
                id = base / 1000 + dayIdx * 100 + hour,
                taskTitle = title,
                durationSeconds = minutes * 60,
                targetDurationSeconds = 25 * 60,
                mode = mode,
                startTime = end - minutes * 60_000L,
                endTime = end,
                completedAt = end,
                dayOfWeek = dayIdx + 1,
                hourOfDay = hour,
            )
        }
        val sessions = mutableListOf(
            mk(0, 9, 50, "Deep Work", "Physics"),
            mk(0, 19, 25, "Classic", "Math"),
            mk(1, 10, 45, "Deep Work", "Physics"),
            mk(2, 8, 30, "Classic", "Chemistry"),
            mk(3, 14, 40, "Deep Work", "Math"),
            mk(3, 21, 55, "Deep Work", "Physics"),
            mk(4, 7, 20, "Classic", "English"),
            mk(5, 11, 60, "Deep Work", "Physics"),
            // Previous week (smaller, so "vs last week" shows a real gain)
            mk(1, 10, 30, "Deep Work", "Physics", prevWeekStart),
            mk(4, 15, 20, "Classic", "Math", prevWeekStart),
        )
        // Today always has two real sessions no matter which weekday CI runs on
        sessions += mk(daysFromMonday, 9, 35, "Deep Work", "Physics")
        sessions += mk(daysFromMonday, 20, 25, "Classic", "Math")
        return sessions
    }

    private fun insightsContent(initialScroll: Int) {
        themed(dark = true) {
            InsightsScreen(
                sessions = seedInsightsSessions(),
                reflections = emptyList(),
                userPreferences = focusPrefs(),
                onSaveReflection = {},
                onOpenSessionHistory = {},
                testInitialScroll = initialScroll,
            )
        }
    }

    @Test
    fun v123_insights_top() {
        insightsContent(initialScroll = 0)
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_insights_top.png")
    }

    @Test
    fun v123_insights_bottom() {
        insightsContent(initialScroll = 700)
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_insights_bottom.png")
    }
}
