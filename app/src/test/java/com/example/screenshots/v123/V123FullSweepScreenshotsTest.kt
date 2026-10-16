package com.example.screenshots.v123

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FocusSessionEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.FloatingNavigation
import com.example.ui.components.FocusShieldHubSheet
import com.example.ui.components.NavTab
import com.example.ui.components.StrictUninstallBlockedOverlay
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
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
import java.util.Calendar

/**
 * v1.2.3 FULL SWEEP (owner 2026-10-16, branch feat/v123-splash): the missing
 * dark/light pairs at Pixel 8 for every main screen, rendered through the
 * REAL app shell (backdrop + Scaffold + simplified FloatingNavigation), plus
 * the new StrictUninstallBlockedOverlay in both themes.
 *
 * Filled gaps vs the earlier v123 tests:
 *  - Focus screen: dark with nav (light existed)
 *  - Profile: light (dark existed)
 *  - Insights top/bottom: light (dark existed)
 *  - Focus Shield hub: light (dark existed)
 *  - Onboarding start: both (missing)
 *  - Uninstall overlay: both (missing — new testStage hook pins the staged
 *    reveal; the CTA pulse is infinite so we capture without waitForIdle)
 *
 * The nav-compactness regression assertion from V123NavPillHotfixTest is
 * repeated on the dark Focus render.
 * PNGs land in app/src/test/screenshots/v123/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V123FullSweepScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun initAll() {
        TimerThemeManager.init(context)
        TimerFaceManager.init(context)
        MascotWardrobe.init(context)
        TimerThemeManager.selectTheme(context, "aurora")
    }

    /** Real app shell: full-bleed backdrop + Scaffold + simplified nav bottomBar. */
    private fun shell(dark: Boolean, tab: NavTab, content: @Composable () -> Unit) {
        initAll()
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ThemedScreenBackground()
                        Scaffold(
                            containerColor = Color.Transparent,
                            bottomBar = {
                                FloatingNavigation(
                                    selectedTab = tab,
                                    onTabSelected = {},
                                    hapticsEnabled = false
                                )
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) { content() }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun assertNavBarCompact() {
        val rootH = composeTestRule.onRoot().fetchSemanticsNode().size.height.toFloat()
        val navH = composeTestRule.onNodeWithTag("floating_nav").fetchSemanticsNode().size.height.toFloat()
        org.junit.Assert.assertTrue(
            "REGRESSION: bottom nav bar is ${navH}px of ${rootH}px tall " +
                "(>= 30% of the screen) — the simplified nav blew up.",
            navH < rootH * 0.30f
        )
    }

    private fun fakePrefs() = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 1500
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

    // ── Focus screen with simplified nav (dark pair) ─────────────────────

    @Test
    fun sweep_focus_dark_with_nav() {
        shell(dark = true, tab = NavTab.FOCUS) {
            FocusScreen(
                remainingSeconds = 1500,
                targetSeconds = 1500,
                isRunning = false,
                currentMode = "focus",
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
        assertNavBarCompact()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_sweep_focus_dark.png")
    }

    // ── Profile (light pair) ─────────────────────────────────────────────

    @Test
    fun sweep_profile_light() {
        shell(dark = false, tab = NavTab.PROFILE) {
            ProfileScreen(
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                onOpenSettings = {}
            )
        }
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_sweep_profile_light.png")
    }

    // ── Insights (light pair) ────────────────────────────────────────────

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
            mk(1, 10, 30, "Deep Work", "Physics", prevWeekStart),
            mk(4, 15, 20, "Classic", "Math", prevWeekStart),
        )
        sessions += mk(daysFromMonday, 9, 35, "Deep Work", "Physics")
        sessions += mk(daysFromMonday, 20, 25, "Classic", "Math")
        return sessions
    }

    private fun insightsLight(initialScroll: Int) {
        initAll()
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ThemedScreenBackground()
                        InsightsScreen(
                            sessions = seedInsightsSessions(),
                            reflections = emptyList(),
                            userPreferences = fakePrefs(),
                            onSaveReflection = {},
                            onOpenSessionHistory = {},
                            testInitialScroll = initialScroll,
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun sweep_insights_top_light() {
        insightsLight(initialScroll = 0)
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_sweep_insights_top_light.png")
    }

    @Test
    fun sweep_insights_bottom_light() {
        insightsLight(initialScroll = 700)
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_sweep_insights_bottom_light.png")
    }

    // ── Focus Shield hub (light pair) ────────────────────────────────────

    @Test
    fun sweep_shield_hub_light() {
        initAll()
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    FocusShieldHubSheet(
                        isShieldEnabled = true,
                        isShieldTimerRunning = false,
                        remainingSeconds = 0,
                        blockedPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
                        blockedAttemptsCount = 3,
                        installedApps = emptyList(),
                        onToggleMasterShield = {},
                        onToggleAppBlocked = { _, _ -> },
                        onStartStandaloneShield = {},
                        onStopStandaloneShield = {},
                        onTriggerTestIntercept = {},
                        onDismiss = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_sweep_shield_hub_light.png")
    }

    // ── Onboarding start (both) ──────────────────────────────────────────

    private fun onboarding(dark: Boolean) {
        initAll()
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ThemedScreenBackground()
                        OnboardingScreen(onCompleteOnboarding = {})
                    }
                }
            }
        }
        // No waitForIdle(): CivoChatBubble runs a typewriter (45ms/char over a
        // ~150-char message) plus an infinite cursor blink, so idleness is
        // timing-dependent. Advance the test clock past the typing duration
        // for a deterministic end state (full text, Continue enabled).
        composeTestRule.mainClock.advanceTimeBy(12_000)
    }

    @Test
    fun sweep_onboarding_dark() {
        onboarding(dark = true)
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_sweep_onboarding_dark.png")
    }

    @Test
    fun sweep_onboarding_light() {
        onboarding(dark = false)
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_sweep_onboarding_light.png")
    }

    // ── Uninstall overlay (both; staged reveal pinned, no waitForIdle) ────

    private fun uninstallOverlay(dark: Boolean) {
        initAll()
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    StrictUninstallBlockedOverlay(
                        remainingSeconds = 1439,
                        onDismiss = {},
                        testStage = 4
                    )
                }
            }
        }
        // No waitForIdle(): the CTA pulse is an infinite transition by design.
        // testStage=4 pins the staged reveal; the enter animation (380ms) must
        // still finish, so idle-wait briefly on the main clock instead.
        composeTestRule.mainClock.advanceTimeBy(600)
    }

    @Test
    fun sweep_uninstall_overlay_dark() {
        uninstallOverlay(dark = true)
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_sweep_uninstall_overlay_dark.png")
    }

    @Test
    fun sweep_uninstall_overlay_light() {
        uninstallOverlay(dark = false)
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v123/v123_sweep_uninstall_overlay_light.png")
    }
}
