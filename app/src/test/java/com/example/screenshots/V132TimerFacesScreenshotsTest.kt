package com.example.screenshots

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.components.TimerFaceHost
import com.example.ui.components.TimerFaceParams
import com.example.ui.theme.FocuslyTheme
import com.example.util.TimerTheme
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
 * v1.3.2 regression set for ITEM 1: the Aurora theme's dark-mode canvas rendered
 * the big timer digits nearly black-on-black ("25:00" invisible, owner
 * screenshot 2026-10-17).
 *
 * Every timer face x (darkest backdrop "midnight" + lightest backdrop "aurora")
 * x (app dark mode + app light mode), idle state. The reported bug lived in
 * aurora + app-dark; midnight exercises the darkest image backdrop. Eyeball
 * EVERY PNG: the main time text must be readable against the backdrop.
 *
 * Running-state is additionally captured for the two most-used faces on the
 * exact reported configuration (aurora, app-dark).
 *
 * One setContent per @Test (Compose rule restriction) — tests are generated
 * per combination below.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V132TimerFacesScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun theme(id: String): TimerTheme =
        TimerThemeManager.THEMES.first { it.id == id }

    private fun render(face: String, themeId: String, appDark: Boolean, running: Boolean) {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, themeId)
        val t = theme(themeId)
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = appDark) {
                Box(Modifier.fillMaxSize()) {
                    ThemedScreenBackground()
                    TimerFaceHost(
                        params = TimerFaceParams(
                            totalSeconds = 1500,
                            remainingSeconds = if (running) 1342 else 1500,
                            isRunning = running,
                            isBreak = false,
                            subjectTitle = "Physics",
                            mascotPose = t.mascotPose,
                            theme = t,
                            onTogglePlay = {},
                            onReset = {},
                            onTagClick = {}
                        ),
                        faceId = face,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v132_timerfaces/${face}_${themeId}_${if (appDark) "dark" else "light"}_${if (running) "running" else "idle"}.png"
        )
    }

    // GENERATED — 13 faces x {midnight, aurora} x {dark, light} + 2 running shots.
    @Test fun digits_midnight_dark_idle() = render("digits", "midnight", true, false)
    @Test fun digits_midnight_light_idle() = render("digits", "midnight", false, false)
    @Test fun digits_aurora_dark_idle() = render("digits", "aurora", true, false)
    @Test fun digits_aurora_light_idle() = render("digits", "aurora", false, false)
    @Test fun digits_aurora_dark_running() = render("digits", "aurora", true, true)
    @Test fun glass_midnight_dark_idle() = render("glass", "midnight", true, false)
    @Test fun glass_midnight_light_idle() = render("glass", "midnight", false, false)
    @Test fun glass_aurora_dark_idle() = render("glass", "aurora", true, false)
    @Test fun glass_aurora_light_idle() = render("glass", "aurora", false, false)
    @Test fun glass_aurora_dark_running() = render("glass", "aurora", true, true)
    @Test fun arc_midnight_dark_idle() = render("arc", "midnight", true, false)
    @Test fun arc_midnight_light_idle() = render("arc", "midnight", false, false)
    @Test fun arc_aurora_dark_idle() = render("arc", "aurora", true, false)
    @Test fun arc_aurora_light_idle() = render("arc", "aurora", false, false)
    @Test fun editorial_midnight_dark_idle() = render("editorial", "midnight", true, false)
    @Test fun editorial_midnight_light_idle() = render("editorial", "midnight", false, false)
    @Test fun editorial_aurora_dark_idle() = render("editorial", "aurora", true, false)
    @Test fun editorial_aurora_light_idle() = render("editorial", "aurora", false, false)
    @Test fun clock_orbit_midnight_dark_idle() = render("clock_orbit", "midnight", true, false)
    @Test fun clock_orbit_midnight_light_idle() = render("clock_orbit", "midnight", false, false)
    @Test fun clock_orbit_aurora_dark_idle() = render("clock_orbit", "aurora", true, false)
    @Test fun clock_orbit_aurora_light_idle() = render("clock_orbit", "aurora", false, false)
    @Test fun clock_flip_midnight_dark_idle() = render("clock_flip", "midnight", true, false)
    @Test fun clock_flip_midnight_light_idle() = render("clock_flip", "midnight", false, false)
    @Test fun clock_flip_aurora_dark_idle() = render("clock_flip", "aurora", true, false)
    @Test fun clock_flip_aurora_light_idle() = render("clock_flip", "aurora", false, false)
    @Test fun clock_liquid_midnight_dark_idle() = render("clock_liquid", "midnight", true, false)
    @Test fun clock_liquid_midnight_light_idle() = render("clock_liquid", "midnight", false, false)
    @Test fun clock_liquid_aurora_dark_idle() = render("clock_liquid", "aurora", true, false)
    @Test fun clock_liquid_aurora_light_idle() = render("clock_liquid", "aurora", false, false)
    @Test fun clock_editorial_midnight_dark_idle() = render("clock_editorial", "midnight", true, false)
    @Test fun clock_editorial_midnight_light_idle() = render("clock_editorial", "midnight", false, false)
    @Test fun clock_editorial_aurora_dark_idle() = render("clock_editorial", "aurora", true, false)
    @Test fun clock_editorial_aurora_light_idle() = render("clock_editorial", "aurora", false, false)
    @Test fun clock_dial_midnight_dark_idle() = render("clock_dial", "midnight", true, false)
    @Test fun clock_dial_midnight_light_idle() = render("clock_dial", "midnight", false, false)
    @Test fun clock_dial_aurora_dark_idle() = render("clock_dial", "aurora", true, false)
    @Test fun clock_dial_aurora_light_idle() = render("clock_dial", "aurora", false, false)
    @Test fun clock_horizon_midnight_dark_idle() = render("clock_horizon", "midnight", true, false)
    @Test fun clock_horizon_midnight_light_idle() = render("clock_horizon", "midnight", false, false)
    @Test fun clock_horizon_aurora_dark_idle() = render("clock_horizon", "aurora", true, false)
    @Test fun clock_horizon_aurora_light_idle() = render("clock_horizon", "aurora", false, false)
    @Test fun clock_stack_midnight_dark_idle() = render("clock_stack", "midnight", true, false)
    @Test fun clock_stack_midnight_light_idle() = render("clock_stack", "midnight", false, false)
    @Test fun clock_stack_aurora_dark_idle() = render("clock_stack", "aurora", true, false)
    @Test fun clock_stack_aurora_light_idle() = render("clock_stack", "aurora", false, false)
    @Test fun clock_breathe_midnight_dark_idle() = render("clock_breathe", "midnight", true, false)
    @Test fun clock_breathe_midnight_light_idle() = render("clock_breathe", "midnight", false, false)
    @Test fun clock_breathe_aurora_dark_idle() = render("clock_breathe", "aurora", true, false)
    @Test fun clock_breathe_aurora_light_idle() = render("clock_breathe", "aurora", false, false)
    @Test fun clock_bars_midnight_dark_idle() = render("clock_bars", "midnight", true, false)
    @Test fun clock_bars_midnight_light_idle() = render("clock_bars", "midnight", false, false)
    @Test fun clock_bars_aurora_dark_idle() = render("clock_bars", "aurora", true, false)
    @Test fun clock_bars_aurora_light_idle() = render("clock_bars", "aurora", false, false)
}
