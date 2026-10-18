package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * v1.3.3 — DailyUsagePipeline: the single source of truth for usage stats.
 *
 * Covers the pure aggregation math, cache freshness decisions, and the
 * provider-injected snapshot path (permission absent -> null; caching per
 * app-open; force refresh; invalidate).
 */
@RunWith(RobolectricTestRunner::class)
class DailyUsagePipelineTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private var queryCount = 0
    private var fakeSamples: List<DailyUsagePipeline.UsageSample> = emptyList()

    @Before
    fun setUp() {
        queryCount = 0
        fakeSamples = emptyList()
        DailyUsagePipeline.testPermissionOverride = true
        DailyUsagePipeline.testProvider =
            object : DailyUsagePipeline.UsageSampleProvider {
                override fun queryTodaySamples(
                    context: Context,
                    dayStartMs: Long,
                    nowMs: Long
                ): List<DailyUsagePipeline.UsageSample> {
                    queryCount++
                    return fakeSamples
                }
            }
        DailyUsagePipeline.invalidate()
    }

    @After
    fun tearDown() {
        DailyUsagePipeline.testProvider = null
        DailyUsagePipeline.testPermissionOverride = null
        DailyUsagePipeline.invalidate()
    }

    // ---------- pure aggregation ----------

    @Test
    fun `aggregate sums per-app millis and device total`() {
        val (perApp, total) = DailyUsagePipeline.aggregate(
            listOf(
                DailyUsagePipeline.UsageSample("com.a", 60_000L),
                DailyUsagePipeline.UsageSample("com.b", 120_000L)
            )
        )
        assertEquals(60_000L, perApp["com.a"])
        assertEquals(120_000L, perApp["com.b"])
        assertEquals(180_000L, total)
    }

    @Test
    fun `aggregate keeps max when a package appears twice`() {
        val (perApp, total) = DailyUsagePipeline.aggregate(
            listOf(
                DailyUsagePipeline.UsageSample("com.a", 60_000L),
                DailyUsagePipeline.UsageSample("com.a", 90_000L)
            )
        )
        assertEquals(90_000L, perApp["com.a"])
        assertEquals(90_000L, total)
    }

    @Test
    fun `aggregate ignores zero and negative samples`() {
        val (perApp, total) = DailyUsagePipeline.aggregate(
            listOf(
                DailyUsagePipeline.UsageSample("com.a", 0L),
                DailyUsagePipeline.UsageSample("com.b", -5L),
                DailyUsagePipeline.UsageSample("com.c", 30_000L)
            )
        )
        assertFalse(perApp.containsKey("com.a"))
        assertFalse(perApp.containsKey("com.b"))
        assertEquals(30_000L, total)
    }

    @Test
    fun `aggregate of empty list is empty`() {
        val (perApp, total) = DailyUsagePipeline.aggregate(emptyList())
        assertTrue(perApp.isEmpty())
        assertEquals(0L, total)
    }

    // ---------- day key / cache freshness ----------

    @Test
    fun `dayKeyFor formats yyyy-MM-dd`() {
        // 2026-10-18 06:30 IST == 2026-10-18 01:00 UTC
        val key = DailyUsagePipeline.dayKeyFor(1_790_000_000_000L)
        assertTrue(key.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `shouldRefresh is true when nothing cached`() {
        assertTrue(
            DailyUsagePipeline.shouldRefresh(
                null, DailyUsagePipeline.dayKeyFor(), System.currentTimeMillis()
            )
        )
    }

    @Test
    fun `shouldRefresh is true on day rollover`() {
        val stale = DailyUsagePipeline.DeviceUsageSnapshot(
            dayKey = "2000-01-01",
            perAppMillis = emptyMap(),
            totalMillis = 0L,
            queriedAtMs = System.currentTimeMillis()
        )
        assertTrue(
            DailyUsagePipeline.shouldRefresh(
                stale, DailyUsagePipeline.dayKeyFor(), System.currentTimeMillis()
            )
        )
    }

    @Test
    fun `shouldRefresh is true when cache older than TTL`() {
        val now = System.currentTimeMillis()
        val stale = DailyUsagePipeline.DeviceUsageSnapshot(
            dayKey = DailyUsagePipeline.dayKeyFor(now),
            perAppMillis = emptyMap(),
            totalMillis = 0L,
            queriedAtMs = now - DailyUsagePipeline.CACHE_TTL_MS - 1
        )
        assertTrue(DailyUsagePipeline.shouldRefresh(stale, DailyUsagePipeline.dayKeyFor(now), now))
    }

    @Test
    fun `shouldRefresh is false for a fresh same-day snapshot`() {
        val now = System.currentTimeMillis()
        val fresh = DailyUsagePipeline.DeviceUsageSnapshot(
            dayKey = DailyUsagePipeline.dayKeyFor(now),
            perAppMillis = emptyMap(),
            totalMillis = 0L,
            queriedAtMs = now - 1_000L
        )
        assertFalse(DailyUsagePipeline.shouldRefresh(fresh, DailyUsagePipeline.dayKeyFor(now), now))
    }

    // ---------- snapshot path (provider-injected) ----------

    @Test
    fun `getSnapshot returns null when permission is missing`() {
        DailyUsagePipeline.testPermissionOverride = false
        fakeSamples = listOf(DailyUsagePipeline.UsageSample("com.a", 60_000L))
        assertNull(DailyUsagePipeline.getSnapshot(context))
        assertEquals(0, queryCount)
    }

    @Test
    fun `getSnapshot aggregates provider samples and caches per app-open`() {
        fakeSamples = listOf(
            DailyUsagePipeline.UsageSample("com.instagram.android", 3_600_000L),
            DailyUsagePipeline.UsageSample("com.zhiliaoapp.musically", 1_800_000L)
        )
        val first = DailyUsagePipeline.getSnapshot(context)
        assertNotNull(first)
        assertEquals(3_600_000L, first!!.perAppMillis["com.instagram.android"])
        assertEquals(5_400_000L, first.totalMillis)
        assertEquals(1, queryCount)

        // Second read hits the cache — no new UsageStats query.
        val second = DailyUsagePipeline.getSnapshot(context)
        assertNotNull(second)
        assertEquals(5_400_000L, second!!.totalMillis)
        assertEquals(1, queryCount)
    }

    @Test
    fun `getSnapshot forceRefresh bypasses the cache`() {
        fakeSamples = listOf(DailyUsagePipeline.UsageSample("com.a", 60_000L))
        DailyUsagePipeline.getSnapshot(context)
        DailyUsagePipeline.getSnapshot(context, forceRefresh = true)
        assertEquals(2, queryCount)
    }

    @Test
    fun `invalidate drops the cache`() {
        fakeSamples = listOf(DailyUsagePipeline.UsageSample("com.a", 60_000L))
        DailyUsagePipeline.getSnapshot(context)
        DailyUsagePipeline.invalidate()
        DailyUsagePipeline.getSnapshot(context)
        assertEquals(2, queryCount)
    }

    // ---------- v1.3.4: daily-interval fallback must stay inside today ----------

    private val MIN = 60_000L

    /** 2026-10-18 10:09 IST (the owner's report moment) -> local-midnight anchor. */
    private fun istDayStart(): Long {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val cal = Calendar.getInstance(tz, Locale.US)
        cal.set(2026, Calendar.OCTOBER, 18, 10, 9, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return DailyUsagePipeline.dayStartMillis(cal.timeInMillis)
    }

    @Test
    fun `mergeDailyIntervalStats drops the previous-day bucket even when larger`() {
        val defaultTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            val dayStart = istDayStart()
            val merged = mutableMapOf("com.a" to 235 * MIN) // today so far: 3h55m
            DailyUsagePipeline.mergeDailyIntervalStats(
                merged,
                listOf(
                    // yesterday's full-day bucket: 4h — must NOT leak into today
                    DailyUsagePipeline.DailyBucketSample("com.a", 240 * MIN, dayStart - 86_400_000L)
                ),
                dayStart
            )
            assertEquals(235 * MIN, merged["com.a"])
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }

    @Test
    fun `mergeDailyIntervalStats keeps max for today's own bucket`() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val merged = mutableMapOf("com.a" to 600 * MIN)
        DailyUsagePipeline.mergeDailyIntervalStats(
            merged,
            listOf(DailyUsagePipeline.DailyBucketSample("com.a", 900 * MIN, dayStart)),
            dayStart
        )
        assertEquals(900 * MIN, merged["com.a"])
    }

    @Test
    fun `mergeDailyIntervalStats does not double-count overlapping buckets`() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val merged = mutableMapOf("com.a" to 600 * MIN)
        DailyUsagePipeline.mergeDailyIntervalStats(
            merged,
            listOf(DailyUsagePipeline.DailyBucketSample("com.a", 600 * MIN, dayStart)),
            dayStart
        )
        assertEquals(600 * MIN, merged["com.a"]) // NOT 1200
    }

    @Test
    fun `mergeDailyIntervalStats drops buckets starting one millisecond before midnight`() {
        val defaultTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            val dayStart = istDayStart()
            val merged = mutableMapOf("com.a" to 10 * MIN)
            DailyUsagePipeline.mergeDailyIntervalStats(
                merged,
                listOf(
                    // bucket that ENDS exactly at midnight starts 1ms before it
                    DailyUsagePipeline.DailyBucketSample("com.a", 500 * MIN, dayStart - 1L)
                ),
                dayStart
            )
            assertEquals(10 * MIN, merged["com.a"])
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }

    @Test
    fun `mergeDailyIntervalStats skips zero and negative buckets`() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val merged = mutableMapOf("com.a" to 10 * MIN)
        DailyUsagePipeline.mergeDailyIntervalStats(
            merged,
            listOf(
                DailyUsagePipeline.DailyBucketSample("com.a", 0L, dayStart),
                DailyUsagePipeline.DailyBucketSample("com.a", -5L, dayStart)
            ),
            dayStart
        )
        assertEquals(10 * MIN, merged["com.a"])
    }

    @Test
    fun `merged per-app numbers aggregate to a consistent header total`() {
        // The Study header total is sum(perApp); the same merged map feeds
        // the per-app rows and the budgets — this pins that invariant.
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val merged = mutableMapOf(
            "com.a" to 68 * MIN, // like YouTube at 1h08m on the owner's device
            "com.b" to 60 * MIN,
            "com.c" to 57 * MIN
        )
        DailyUsagePipeline.mergeDailyIntervalStats(
            merged,
            listOf(
                DailyUsagePipeline.DailyBucketSample("com.a", 240 * MIN, dayStart - 86_400_000L),
                DailyUsagePipeline.DailyBucketSample("com.b", 120 * MIN, dayStart)
            ),
            dayStart
        )
        val samples = merged.map { (pkg, ms) -> DailyUsagePipeline.UsageSample(pkg, ms) }
        val (perApp, total) = DailyUsagePipeline.aggregate(samples)
        assertEquals(68 * MIN, perApp["com.a"]) // yesterday's 240m did NOT leak
        assertEquals(120 * MIN, perApp["com.b"]) // today's fallback took the max
        assertEquals(68 * MIN + 120 * MIN + 57 * MIN, total)
        assertEquals(perApp.values.sum(), total)
    }

    @Test
    fun `dayStartMillis is local midnight in Asia Kolkata`() {
        val defaultTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            val dayStart = istDayStart()
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            assertEquals("00:00:00", fmt.format(Date(dayStart)))
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }

    // ---------- v1.1.1: event-pair foreground pairing ----------

    private fun resumed(pkg: String, t: Long) =
        DailyUsagePipeline.AppUsageEvent(pkg, DailyUsagePipeline.AppEventType.RESUMED, t)

    private fun paused(pkg: String, t: Long) =
        DailyUsagePipeline.AppUsageEvent(pkg, DailyUsagePipeline.AppEventType.PAUSED, t)

    private fun shutdown(t: Long) =
        DailyUsagePipeline.AppUsageEvent("", DailyUsagePipeline.AppEventType.SHUTDOWN, t)

    @Test
    fun `sumForegroundTime pairs resumed and paused per app`() {
        val defaultTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            val dayStart = istDayStart()
            val now = dayStart + 12 * 60 * MIN
            val totals = DailyUsagePipeline.sumForegroundTime(
                listOf(
                    resumed("com.youtube", dayStart + 60 * MIN),
                    paused("com.youtube", dayStart + 128 * MIN), // 1h08m — owner's YouTube
                    resumed("com.muse", dayStart + 130 * MIN),
                    paused("com.muse", dayStart + 190 * MIN) // 1h00m
                ),
                dayStart, now
            )
            assertEquals(68 * MIN, totals["com.youtube"])
            assertEquals(60 * MIN, totals["com.muse"])
            assertEquals(128 * MIN, totals.values.sum())
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }

    @Test
    fun `sumForegroundTime counts both apps in split-screen`() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 2 * 60 * MIN
        val totals = DailyUsagePipeline.sumForegroundTime(
            listOf(
                resumed("com.a", dayStart + 10 * MIN),
                resumed("com.b", dayStart + 20 * MIN), // split-screen: both on screen
                paused("com.a", dayStart + 50 * MIN),
                paused("com.b", dayStart + 60 * MIN)
            ),
            dayStart, now
        )
        assertEquals(40 * MIN, totals["com.a"])
        assertEquals(40 * MIN, totals["com.b"])
        assertEquals(80 * MIN, totals.values.sum()) // sum of per-app, like the system total
    }

    @Test
    fun `sumForegroundTime clips sessions spanning midnight`() {
        val defaultTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            val dayStart = istDayStart()
            val now = dayStart + 3 * 60 * MIN
            val totals = DailyUsagePipeline.sumForegroundTime(
                listOf(
                    resumed("com.a", dayStart - 30 * MIN), // opened yesterday
                    paused("com.a", dayStart + 10 * MIN)
                ),
                dayStart, now
            )
            assertEquals(10 * MIN, totals["com.a"]) // only today's part
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }

    @Test
    fun `sumForegroundTime closes still-open apps at now`() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 5 * 60 * MIN
        val totals = DailyUsagePipeline.sumForegroundTime(
            listOf(resumed("com.a", dayStart + 4 * 60 * MIN)), // never paused — on screen now
            dayStart, now
        )
        assertEquals(60 * MIN, totals["com.a"])
    }

    @Test
    fun `sumForegroundTime treats shutdown as closing all apps`() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 8 * 60 * MIN
        val totals = DailyUsagePipeline.sumForegroundTime(
            listOf(
                resumed("com.a", dayStart + 1 * 60 * MIN),
                shutdown(dayStart + 2 * 60 * MIN),
                resumed("com.a", dayStart + 5 * 60 * MIN),
                paused("com.a", dayStart + 6 * 60 * MIN)
            ),
            dayStart, now
        )
        assertEquals(2 * 60 * MIN, totals["com.a"]) // 1h before shutdown + 1h after
    }

    @Test
    fun `sumForegroundTime ignores duplicate resumes while open`() {
        val dayStart = DailyUsagePipeline.dayStartMillis()
        val now = dayStart + 3 * 60 * MIN
        val totals = DailyUsagePipeline.sumForegroundTime(
            listOf(
                resumed("com.a", dayStart + 10 * MIN),
                resumed("com.a", dayStart + 20 * MIN), // activity flip; still one interval
                paused("com.a", dayStart + 70 * MIN)
            ),
            dayStart, now
        )
        assertEquals(60 * MIN, totals["com.a"])
    }

    @Test
    fun `usageMillisForPackage and deviceTotalMillis read the snapshot`() {
        fakeSamples = listOf(
            DailyUsagePipeline.UsageSample("com.a", 90_000L),
            DailyUsagePipeline.UsageSample("com.b", 30_000L)
        )
        assertEquals(90_000L, DailyUsagePipeline.usageMillisForPackage(context, "com.a"))
        assertEquals(0L, DailyUsagePipeline.usageMillisForPackage(context, "com.unknown"))
        assertEquals(120_000L, DailyUsagePipeline.deviceTotalMillis(context))
    }
}
