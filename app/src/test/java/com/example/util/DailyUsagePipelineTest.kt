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
