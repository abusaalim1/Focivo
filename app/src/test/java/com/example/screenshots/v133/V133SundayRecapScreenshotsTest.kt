package com.example.screenshots.v133

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.WeeklyDailyStat
import com.example.data.model.WeeklyRecapSummary
import com.example.data.model.WeeklySessionDetail
import com.example.data.model.WeeklySubjectStat
import com.example.ui.components.SundayRecapGlassDialog
import com.example.ui.components.ThemedScreenBackground
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
 * v1.3.3 ITEM 7 — Sunday Recap restyle (design-system rebuild).
 *
 * Renders the real [SundayRecapGlassDialog] (the Insights-area weekly
 * review) with a fully populated [WeeklyRecapSummary] in dark + light at
 * Pixel 8, over the real themed background — the same stack MainActivity
 * uses. PNGs land in app/src/test/screenshots/v133/.
 *
 * The dialog data is identical for both shots; only the app theme changes,
 * so before/after restyle comparisons are apples-to-apples.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V133SundayRecapScreenshotsTest {

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

    /** Rich fake recap exercising every section of the dialog. */
    private fun fakeRecap() = WeeklyRecapSummary(
        weekIdentifier = "Oct 11 - Oct 17, 2026",
        weekKey = "2026-W42",
        weekStartTimestamp = 0L,
        weekEndTimestamp = 0L,
        totalMinutesFocused = 512,
        totalHoursFormatted = "8.5",
        sessionCount = 14,
        pointsEarned = 512,
        bestDayName = "Wednesday",
        bestDayMinutes = 145,
        topSubject = "Physics",
        subjectBreakdowns = listOf(
            WeeklySubjectStat("Physics", 210, 5, 0.41f),
            WeeklySubjectStat("Maths", 140, 4, 0.27f),
            WeeklySubjectStat("Chemistry", 95, 3, 0.19f),
            WeeklySubjectStat("English", 67, 2, 0.13f)
        ),
        dailyBreakdowns = listOf(
            WeeklyDailyStat("Mon", 60),
            WeeklyDailyStat("Tue", 95),
            WeeklyDailyStat("Wed", 145, isBestDay = true),
            WeeklyDailyStat("Thu", 80),
            WeeklyDailyStat("Fri", 45),
            WeeklyDailyStat("Sat", 87),
            WeeklyDailyStat("Sun", 0)
        ),
        sessionDetails = listOf(
            WeeklySessionDetail(1, "Units and Measurement", "Physics", 50, 0L, "6:00 PM", "Dimensional analysis", "Deep Work"),
            WeeklySessionDetail(2, "Quadratic Equations", "Maths", 45, 0L, "4:30 PM", "", "Deep Work"),
            WeeklySessionDetail(3, "Chemical Bonding", "Chemistry", 40, 0L, "11:00 AM", "Hybridisation notes", "Pomodoro"),
            WeeklySessionDetail(4, "Reading Comprehension", "English", 35, 0L, "9:00 AM", "", "Deep Work"),
            WeeklySessionDetail(5, "Motion in a Plane", "Physics", 55, 0L, "7:15 PM", "Projectile problems", "Deep Work")
        ),
        motivationalQuote = "Your Wednesday peak shows what consistent deep work looks like. Keep that rhythm."
    )

    private fun shot(dark: Boolean, name: String, content: @Composable () -> Unit) {
        initAll()
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
        composeTestRule.waitForIdle()
        composeTestRule.onRoot()
            .captureRoboImage("src/test/screenshots/v133/$name.png")
    }

    @Test
    fun recap_dark() = shot(true, "v133_recap_dark") {
        SundayRecapGlassDialog(recap = fakeRecap(), onDismiss = {})
    }

    @Test
    fun recap_light() = shot(false, "v133_recap_light") {
        SundayRecapGlassDialog(recap = fakeRecap(), onDismiss = {})
    }
}
