package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.ScreenTimeBudgetManager.BreakDecision
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.3.3 (item 8b) — global screen-time budget.
 *
 * Pure state-machine tests (trigger / re-arm / expiry / no-double-trigger /
 * essentials predicate) plus a Robolectric end-to-end pass through
 * [ScreenTimeBudgetManager.checkOnAppOpen] with a fake usage provider.
 */
class ScreenTimeBudgetManagerTest {

    private val now = 1_790_000_000_000L // fixed clock for pure tests
    private val budgetMins = 120

    // ---------------- pure evaluateBreak ----------------

    @Test
    fun `budget off never blocks`() {
        val eval = ScreenTimeBudgetManager.evaluateBreak(
            nowMs = now, budgetMinutes = 0, totalUsageMs = 99_999_999L,
            isEssential = false, storedEndsAtMs = null, triggersToday = 0
        )
        assertEquals(BreakDecision.Allow, eval.decision)
        assertFalse(eval.startNewBreak)
    }

    @Test
    fun `essential app is never blocked`() {
        val eval = ScreenTimeBudgetManager.evaluateBreak(
            nowMs = now, budgetMinutes = budgetMins, totalUsageMs = 99_999_999L,
            isEssential = true, storedEndsAtMs = null, triggersToday = 0
        )
        assertEquals(BreakDecision.Allow, eval.decision)
        assertFalse(eval.startNewBreak)
    }

    @Test
    fun `usage under budget allows`() {
        val eval = ScreenTimeBudgetManager.evaluateBreak(
            nowMs = now, budgetMinutes = budgetMins,
            totalUsageMs = (budgetMins * 60_000L) - 1,
            isEssential = false, storedEndsAtMs = null, triggersToday = 0
        )
        assertEquals(BreakDecision.Allow, eval.decision)
        assertFalse(eval.startNewBreak)
    }

    @Test
    fun `usage at budget triggers a break`() {
        val eval = ScreenTimeBudgetManager.evaluateBreak(
            nowMs = now, budgetMinutes = budgetMins,
            totalUsageMs = budgetMins * 60_000L,
            isEssential = false, storedEndsAtMs = null, triggersToday = 0
        )
        assertTrue(eval.startNewBreak)
        val block = eval.decision as BreakDecision.Block
        assertEquals(ScreenTimeBudgetManager.BREAK_DURATION_MS, block.remainingMs)
    }

    @Test
    fun `live break keeps blocking with remaining time`() {
        val endsAt = now + 7_000_000L
        val eval = ScreenTimeBudgetManager.evaluateBreak(
            nowMs = now, budgetMinutes = budgetMins, totalUsageMs = 0L,
            isEssential = false, storedEndsAtMs = endsAt, triggersToday = 1
        )
        assertFalse(eval.startNewBreak)
        val block = eval.decision as BreakDecision.Block
        assertEquals(7_000_000L, block.remainingMs)
    }

    @Test
    fun `expired break is dropped and usage still over re-arms once`() {
        val eval = ScreenTimeBudgetManager.evaluateBreak(
            nowMs = now, budgetMinutes = budgetMins,
            totalUsageMs = budgetMins * 60_000L + 5_000L,
            isEssential = false,
            storedEndsAtMs = now - 1, // expired
            triggersToday = 1
        )
        assertTrue(eval.startNewBreak)
        assertTrue(eval.decision is BreakDecision.Block)
    }

    @Test
    fun `no infinite nag - third trigger is refused`() {
        val eval = ScreenTimeBudgetManager.evaluateBreak(
            nowMs = now, budgetMinutes = budgetMins,
            totalUsageMs = 99_999_999L,
            isEssential = false, storedEndsAtMs = now - 1,
            triggersToday = ScreenTimeBudgetManager.MAX_BREAKS_PER_DAY
        )
        assertFalse(eval.startNewBreak)
        assertEquals(BreakDecision.Allow, eval.decision)
    }

    @Test
    fun `usage dropped under budget after break ends - no re-arm`() {
        val eval = ScreenTimeBudgetManager.evaluateBreak(
            nowMs = now, budgetMinutes = budgetMins, totalUsageMs = 1_000L,
            isEssential = false, storedEndsAtMs = now - 1, triggersToday = 1
        )
        assertFalse(eval.startNewBreak)
        assertEquals(BreakDecision.Allow, eval.decision)
    }

    // ---------------- essentials predicate ----------------

    @Test
    fun `maps chrome youtube are extra-essential`() {
        assertTrue(ScreenTimeBudgetManager.isExtraEssentialPackage("com.google.android.apps.maps"))
        assertTrue(ScreenTimeBudgetManager.isExtraEssentialPackage("com.android.chrome"))
        assertTrue(ScreenTimeBudgetManager.isExtraEssentialPackage("com.google.android.youtube"))
        assertTrue(ScreenTimeBudgetManager.isExtraEssentialPackage("com.google.android.apps.youtube.music"))
        assertTrue(ScreenTimeBudgetManager.isExtraEssentialPackage("com.vendor.MapsGo"))
        assertTrue(ScreenTimeBudgetManager.isExtraEssentialPackage("org.chromium.chrome"))
    }

    @Test
    fun `ordinary apps are not extra-essential`() {
        assertFalse(ScreenTimeBudgetManager.isExtraEssentialPackage("com.instagram.android"))
        assertFalse(ScreenTimeBudgetManager.isExtraEssentialPackage("com.zhiliaoapp.musically"))
        assertFalse(ScreenTimeBudgetManager.isExtraEssentialPackage(null))
        assertFalse(ScreenTimeBudgetManager.isExtraEssentialPackage("  "))
    }

    // ---------------- formatting ----------------

    @Test
    fun `formatRemaining renders h and m`() {
        assertEquals("2h 13m", ScreenTimeBudgetManager.formatRemaining((2 * 60 + 13) * 60_000L))
        assertEquals("41m", ScreenTimeBudgetManager.formatRemaining(41 * 60_000L))
        assertEquals("3h", ScreenTimeBudgetManager.formatRemaining(3 * 60 * 60_000L))
    }

    @Test
    fun `budgetLabel renders compact labels`() {
        assertEquals("2h", ScreenTimeBudgetManager.budgetLabel(120))
        assertEquals("90m", ScreenTimeBudgetManager.budgetLabel(90))
        assertEquals("2h 30m", ScreenTimeBudgetManager.budgetLabel(150))
    }

    // ---------------- Robolectric: checkOnAppOpen ----------------

    @RunWith(RobolectricTestRunner::class)
    class GuardPathTest {

        private val context: Context
            get() = ApplicationProvider.getApplicationContext()

        private var fakeSamples: List<DailyUsagePipeline.UsageSample> = emptyList()

        @Before
        fun setUp() {
            DailyUsagePipeline.testPermissionOverride = true
            DailyUsagePipeline.testProvider =
                object : DailyUsagePipeline.UsageSampleProvider {
                    override fun queryTodaySamples(
                        context: Context,
                        dayStartMs: Long,
                        nowMs: Long
                    ) = fakeSamples
                }
            DailyUsagePipeline.invalidate()
            // Break runtime state (my prefs) + the canonical budget value
            // (ScreenTimeBudgetStore, shared with the ITEM 5 editor).
            context.getSharedPreferences("screen_time_budget_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()
            ScreenTimeBudgetStore.setGlobalLimitEnabled(context, false)
            ScreenTimeBudgetStore.setGlobalLimitMinutes(context, 0)
        }

        @After
        fun tearDown() {
            DailyUsagePipeline.testProvider = null
            DailyUsagePipeline.testPermissionOverride = null
            DailyUsagePipeline.invalidate()
        }

        private fun overBudgetSamples() {
            // 3h total device usage vs a 2h budget.
            fakeSamples = listOf(
                DailyUsagePipeline.UsageSample("com.instagram.android", 10_800_000L)
            )
            DailyUsagePipeline.invalidate()
        }

        @Test
        fun `budget off allows everything`() {
            ScreenTimeBudgetManager.setGlobalBudgetMinutes(context, 0)
            overBudgetSamples()
            assertEquals(
                BreakDecision.Allow,
                ScreenTimeBudgetManager.checkOnAppOpen(context, "com.instagram.android")
            )
        }

        @Test
        fun `essential app allowed even over budget`() {
            ScreenTimeBudgetManager.setGlobalBudgetMinutes(context, 120)
            overBudgetSamples()
            assertEquals(
                BreakDecision.Allow,
                ScreenTimeBudgetManager.checkOnAppOpen(context, "com.google.android.apps.maps")
            )
            assertEquals(
                BreakDecision.Allow,
                ScreenTimeBudgetManager.checkOnAppOpen(context, "com.android.chrome")
            )
            assertFalse(ScreenTimeBudgetManager.isBreakActive(context))
        }

        @Test
        fun `over budget starts break and persists across reads`() {
            ScreenTimeBudgetManager.setGlobalBudgetMinutes(context, 120)
            overBudgetSamples()

            val first = ScreenTimeBudgetManager.checkOnAppOpen(context, "com.instagram.android")
            assertTrue(first is BreakDecision.Block)
            assertTrue(ScreenTimeBudgetManager.isBreakActive(context))

            // Second app-open during the live break keeps blocking.
            val second = ScreenTimeBudgetManager.checkOnAppOpen(context, "com.zhiliaoapp.musically")
            assertTrue(second is BreakDecision.Block)
            assertTrue(
                (second as BreakDecision.Block).remainingMs <= ScreenTimeBudgetManager.BREAK_DURATION_MS
            )
        }

        @Test
        fun `expired break re-arms once then stops`() {
            ScreenTimeBudgetManager.setGlobalBudgetMinutes(context, 120)
            overBudgetSamples()

            // First trigger.
            assertTrue(
                ScreenTimeBudgetManager.checkOnAppOpen(context, "com.instagram.android")
                    is BreakDecision.Block
            )

            // Expire the break manually (simulates 3h passing).
            val prefs = context.getSharedPreferences("screen_time_budget_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("break_ends_at_ms", System.currentTimeMillis() - 1).apply()

            // Usage still over -> re-arm exactly once.
            val rearmed = ScreenTimeBudgetManager.checkOnAppOpen(context, "com.instagram.android")
            assertTrue(rearmed is BreakDecision.Block)

            // Expire again -> max reached -> allowed (no infinite nag).
            prefs.edit().putLong("break_ends_at_ms", System.currentTimeMillis() - 1).apply()
            assertEquals(
                BreakDecision.Allow,
                ScreenTimeBudgetManager.checkOnAppOpen(context, "com.instagram.android")
            )
        }

        @Test
        fun `under budget never triggers`() {
            ScreenTimeBudgetManager.setGlobalBudgetMinutes(context, 600)
            overBudgetSamples() // 3h total < 10h budget
            assertEquals(
                BreakDecision.Allow,
                ScreenTimeBudgetManager.checkOnAppOpen(context, "com.instagram.android")
            )
            assertFalse(ScreenTimeBudgetManager.isBreakActive(context))
        }
    }
}
