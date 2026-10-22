package com.example.screenshots.v134

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.AppLimitsScreen
import com.example.ui.screens.FocusScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.AiStudyGuardManager
import com.example.util.DailyUsagePipeline
import com.example.util.DeviceAppInfo
import com.example.util.TimerThemeManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1.4f — screen-time OVERCOUNT fix (owner device report 2026-10-22:
 * the app showed 19h against ~7h of real use, permission granted).
 *
 * Diagnosis pinned in code: the merged pipeline chose, per app,
 * MAX(events, INTERVAL_DAILY bucket, aggregate) and summed across apps.
 * On the owner's vivo the bucket/aggregate sources are not window-scoped
 * (they can claim more than the day itself) and MAX imported that
 * inflation wholesale; per-package interval merging also let two
 * overlapping apps each bank the full span.
 *
 * The fix, proven here hermetically (this class EXECUTES in the v134 CI
 * workflow, it is not merely compiled):
 *  (a) two fully overlapping apps share the wall clock (union timeline);
 *  (b) a pre-midnight session contributes only its post-midnight part;
 *  (c) a bucket claiming 30h in an 8h day is DISTRUSTED, never chosen;
 *  (d) a normal single-app day sums exactly;
 *  (e) buckets are fallback-only for event-less apps, capped by headroom.
 * Plus screenshot evidence: Study usage card + per-app list, seeded
 * through the pipeline's provider seam with plausible values.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V114fUsageAttributionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val MIN = 60_000L
    private val HOUR = 60 * MIN

    @Before
    fun setUp() {
        DailyUsagePipeline.testProvider = null
        DailyUsagePipeline.testPermissionOverride = null
        DailyUsagePipeline.invalidate()
    }

    @After
    fun tearDown() {
        DailyUsagePipeline.testProvider = null
        DailyUsagePipeline.testPermissionOverride = null
        DailyUsagePipeline.invalidate()
    }

    private fun resumed(pkg: String, t: Long) =
        DailyUsagePipeline.AppUsageEvent(pkg, DailyUsagePipeline.AppEventType.RESUMED, t)

    private fun paused(pkg: String, t: Long) =
        DailyUsagePipeline.AppUsageEvent(pkg, DailyUsagePipeline.AppEventType.PAUSED, t)

    // ---------------- (a) overlapping apps share the wall clock ----------------

    @Test
    fun attribution_two_overlapping_apps_share_the_wall_clock() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 3 * HOUR
        val events = listOf(
            resumed("com.a", dayStart),
            resumed("com.b", dayStart + 30 * MIN), // B takes over at +30m
            paused("com.b", dayStart + 120 * MIN),
            paused("com.a", dayStart + 120 * MIN)
        )
        val attributed = DailyUsagePipeline.attributeForegroundTime(events, dayStart, now)
        assertEquals(30 * MIN, attributed["com.a"])
        assertEquals(90 * MIN, attributed["com.b"])
        // The union total is the 2h of wall clock — NOT 2h + 1.5h.
        assertEquals(120 * MIN, attributed.values.sum())

        // Contrast (the bug): per-package interval union banked both spans.
        val perPackage = DailyUsagePipeline.sumForegroundTime(events, dayStart, now)
        assertEquals(120 * MIN, perPackage["com.a"])
        assertEquals(90 * MIN, perPackage["com.b"])
        assertEquals(210 * MIN, perPackage.values.sum())
    }

    @Test
    fun attribution_ownership_falls_back_when_the_owner_pauses() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 3 * HOUR
        val events = listOf(
            resumed("com.a", dayStart),
            resumed("com.b", dayStart + 30 * MIN),
            paused("com.b", dayStart + 60 * MIN), // B leaves; A still open -> A owns again
            paused("com.a", dayStart + 120 * MIN)
        )
        val attributed = DailyUsagePipeline.attributeForegroundTime(events, dayStart, now)
        assertEquals(90 * MIN, attributed["com.a"]) // 30m before + 60m after
        assertEquals(30 * MIN, attributed["com.b"])
        assertEquals(120 * MIN, attributed.values.sum())
    }

    // ---------------- (b) pre-midnight session ----------------

    @Test
    fun attribution_pre_midnight_session_contributes_only_post_midnight() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 8 * HOUR
        val events = listOf(
            resumed("com.a", dayStart - 4 * HOUR), // yesterday 20:00
            paused("com.a", dayStart + 2 * HOUR) // today 02:00
        )
        val attributed = DailyUsagePipeline.attributeForegroundTime(events, dayStart, now)
        assertEquals(2 * HOUR, attributed["com.a"])
        assertEquals(2 * HOUR, attributed.values.sum())
    }

    // ---------------- (d) normal day sums exactly ----------------

    @Test
    fun attribution_normal_single_app_day_sums_exactly() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 20 * HOUR
        val events = listOf(
            resumed("com.a", dayStart + 9 * HOUR),
            paused("com.a", dayStart + 10 * HOUR + 30 * MIN), // 90m
            resumed("com.a", dayStart + 11 * HOUR),
            paused("com.a", dayStart + 12 * HOUR) // 60m
        )
        val attributed = DailyUsagePipeline.attributeForegroundTime(events, dayStart, now)
        assertEquals(150 * MIN, attributed["com.a"])
        assertEquals(150 * MIN, attributed.values.sum())
    }

    // ---------------- (c) lying buckets are distrusted ----------------

    @Test
    fun reconcile_distrusts_sources_claiming_more_than_the_elapsed_day() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 8 * HOUR // 8h elapsed so far
        val events = listOf(
            resumed("com.x", dayStart + 1 * HOUR),
            paused("com.x", dayStart + 2 * HOUR) // 1h of real use
        )
        val attributed = DailyUsagePipeline.attributeForegroundTime(events, dayStart, now)
        val perPackage = DailyUsagePipeline.sumForegroundTime(events, dayStart, now)
        val entries = DailyUsagePipeline.reconcileAttributedUsage(
            attributedMap = attributed,
            eventsMap = perPackage,
            // The vivo failure shape: bucket/aggregate claim multi-day totals.
            dailyMap = mapOf("com.x" to 30 * HOUR, "com.y" to 30 * HOUR),
            aggregateMap = mapOf("com.x" to 25 * HOUR, "com.y" to 10 * HOUR),
            dayStartMs = dayStart,
            nowMs = now
        )
        val byPkg = entries.associateBy { it.packageName }
        // Real event time wins for com.x; both inflated sources rejected.
        assertEquals(1 * HOUR, byPkg["com.x"]?.chosenMs)
        assertEquals(DailyUsagePipeline.PATH_EVENTS, byPkg["com.x"]?.path)
        assertEquals("daily,agg", byPkg["com.x"]?.distrusted)
        assertEquals(1 * HOUR, byPkg["com.x"]?.attributedMs)
        // com.y has ONLY lying sources: contributes nothing.
        assertEquals(0L, byPkg["com.y"]?.chosenMs)
        assertEquals("daily,agg", byPkg["com.y"]?.distrusted)
        // The device total stays inside the elapsed day — never 19h-style sums.
        assertTrue(entries.sumOf { it.chosenMs } <= 8 * HOUR)
        assertEquals(1 * HOUR, entries.sumOf { it.chosenMs })
    }

    // ---------------- (e) fallback-only, headroom-capped ----------------

    @Test
    fun reconcile_fallback_fills_eventless_app_within_headroom() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 8 * HOUR // 480m elapsed
        val entries = DailyUsagePipeline.reconcileAttributedUsage(
            attributedMap = mapOf("com.a" to 180 * MIN), // union total 180m
            eventsMap = mapOf("com.a" to 180 * MIN),
            dailyMap = mapOf("com.z" to 60 * MIN), // event-less, honest bucket
            aggregateMap = emptyMap(),
            dayStartMs = dayStart,
            nowMs = now
        )
        val byPkg = entries.associateBy { it.packageName }
        assertEquals(180 * MIN, byPkg["com.a"]?.chosenMs)
        assertEquals(60 * MIN, byPkg["com.z"]?.chosenMs)
        assertEquals(DailyUsagePipeline.PATH_FALLBACK, byPkg["com.z"]?.path)
        assertEquals(240 * MIN, entries.sumOf { it.chosenMs })
    }

    @Test
    fun reconcile_fallback_is_capped_by_remaining_headroom() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 8 * HOUR // 480m elapsed
        val entries = DailyUsagePipeline.reconcileAttributedUsage(
            attributedMap = mapOf("com.a" to 450 * MIN), // headroom = 30m
            eventsMap = mapOf("com.a" to 450 * MIN),
            dailyMap = mapOf("com.z" to 120 * MIN, "com.w" to 20 * MIN),
            aggregateMap = emptyMap(),
            dayStartMs = dayStart,
            nowMs = now
        )
        val byPkg = entries.associateBy { it.packageName }
        // Largest candidate first: com.z takes all 30m of headroom, com.w none.
        assertEquals(30 * MIN, byPkg["com.z"]?.chosenMs)
        assertEquals(0L, byPkg["com.w"]?.chosenMs)
        assertEquals(480 * MIN, entries.sumOf { it.chosenMs }) // == elapsed, never above
    }

    @Test
    fun diagnostics_log_distrusted_entries_with_rejected_claims() {
        AiStudyGuardManager.clearGuardActivity(context)
        val entries = listOf(
            DailyUsagePipeline.ReconcileEntry(
                packageName = "com.y",
                eventsMs = 0L,
                dailyMs = 30 * HOUR,
                aggregateMs = 25 * HOUR,
                chosenMs = 0L,
                attributedMs = 0L,
                path = DailyUsagePipeline.PATH_EVENTS,
                distrusted = "daily,agg"
            )
        )
        DailyUsagePipeline.recordReconcileDiagnostics(context, entries)
        val logged = AiStudyGuardManager.guardActivity.value
        assertTrue(logged.any {
            it.stage == DailyUsagePipeline.STAGE_USAGE_RECONCILE &&
                it.app == "com.y" &&
                it.verdict == "DISTRUSTED" &&
                it.reason.contains("daily=${30 * HOUR}ms") &&
                it.reason.contains("agg=${25 * HOUR}ms") &&
                it.reason.contains("attr=0ms") &&
                it.snippet.contains("chosen=0ms") &&
                it.snippet.contains("distrusted=daily,agg")
        })
    }

    // ---------------- screenshot evidence (seeded, plausible) ----------------

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

    /** Plausible mid-day usage: 4h38m total, YouTube on top. */
    private fun seedPlausibleUsage() {
        DailyUsagePipeline.testPermissionOverride = true
        DailyUsagePipeline.testProvider =
            object : DailyUsagePipeline.UsageSampleProvider {
                override fun queryTodaySamples(
                    context: Context,
                    dayStartMs: Long,
                    nowMs: Long
                ): List<DailyUsagePipeline.UsageSample> = listOf(
                    DailyUsagePipeline.UsageSample("com.google.android.youtube", 130 * MIN),
                    DailyUsagePipeline.UsageSample("com.instagram.android", 65 * MIN),
                    DailyUsagePipeline.UsageSample("com.android.chrome", 45 * MIN),
                    DailyUsagePipeline.UsageSample("com.whatsapp", 38 * MIN)
                )
            }
        DailyUsagePipeline.invalidate()
    }

    @Test
    fun study_usage_card_shows_attributed_total_dark() {
        seedPlausibleUsage()
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
                onRestartSession = {},
                onAddFiveMinutes = {},
                onSkipBreak = {},
                onSelectMode = { _, _ -> },
                onSelectAmbientSound = {},
                onLogDistraction = {},
                onUpdateTaskTitle = {},
                onOpenScreenTimeBudgets = {}
            )
        }
        // 130 + 65 + 45 + 38 = 278m -> "4h 38m" (formatStatDuration).
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            try {
                composeTestRule.onNodeWithTag("study_usage_time_value", useUnmergedTree = true)
                    .assertTextEquals("4h 38m")
                true
            } catch (_: Throwable) {
                false
            }
        }
        capture("v114f_study_usage_card_dark")
    }

    @Test
    fun per_app_list_ranks_apps_with_top_strip_dark() {
        seedPlausibleUsage()
        val apps = listOf(
            DeviceAppInfo(packageName = "com.google.android.youtube", appName = "YouTube", icon = null, category = "Entertainment", isBlocked = false),
            DeviceAppInfo(packageName = "com.instagram.android", appName = "Instagram", icon = null, category = "Social", isBlocked = false),
            DeviceAppInfo(packageName = "com.android.chrome", appName = "Chrome", icon = null, category = "Productivity", isBlocked = false),
            DeviceAppInfo(packageName = "com.whatsapp", appName = "WhatsApp", icon = null, category = "Social", isBlocked = false)
        )
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                AppLimitsScreen(
                    limits = emptyList(),
                    installedApps = apps,
                    onBack = {},
                    onSaveLimit = {},
                    onDeleteLimit = {},
                    onToggleEnabled = { _, _ -> }
                )
            }
        }
        // Overview total from the SAME snapshot + the day's top app strip.
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            try {
                composeTestRule.onNodeWithText("4h 38m logged", substring = true)
                    .fetchSemanticsNode() != null
            } catch (_: Throwable) {
                false
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            try {
                composeTestRule.onNodeWithText("MOST TIME TODAY", substring = true)
                    .fetchSemanticsNode() != null
            } catch (_: Throwable) {
                false
            }
        }
        capture("v114f_per_app_list_dark")
    }
}
