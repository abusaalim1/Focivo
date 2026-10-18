package com.example.util

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * v1.3.3 — the ONE source of truth for device usage stats.
 *
 * Every surface that needs screen-time data (Study-top header, per-app
 * budgets, the global screen-time budget, the existing per-app daily-limit
 * enforcement) reads from here, so the numbers are identical everywhere.
 *
 * - [getSnapshot] returns today's per-app foreground millis + the device
 *   total (sum of all apps). UsageStatsManager queries are expensive, so the
 *   snapshot is cached in-memory per app-open with a short TTL and only
 *   re-queried on demand ([invalidate] / forceRefresh) or when the day rolls
 *   over.
 * - [hasUsagePermission] exposes the PACKAGE_USAGE_STATS grant check; when
 *   it is false every read returns null/0 and callers must show the
 *   permission-gated UX instead of numbers.
 *
 * Pure, hermetic helpers ([aggregate], [dayKeyFor], [shouldRefresh]) are
 * unit-tested; the Android query path is injected via [UsageSampleProvider]
 * so tests can substitute a fake provider without a UsageStatsManager.
 */
object DailyUsagePipeline {

    private const val TAG = "DailyUsagePipeline"

    /** In-memory cache TTL — UsageStatsManager queries are expensive. */
    const val CACHE_TTL_MS = 60_000L

    /** One app's foreground time for today. */
    data class UsageSample(
        val packageName: String,
        val totalTimeInForegroundMs: Long
    )

    /** Injects the Android query path so unit tests can substitute fakes. */
    interface UsageSampleProvider {
        fun queryTodaySamples(
            context: Context,
            dayStartMs: Long,
            nowMs: Long
        ): List<UsageSample>
    }

    /** Today's full usage picture: per-app millis + device total. */
    data class DeviceUsageSnapshot(
        val dayKey: String,
        val perAppMillis: Map<String, Long>,
        val totalMillis: Long,
        val queriedAtMs: Long
    )

    /** Production provider: UsageStatsManager, aggregated per package. */
    private val systemProvider = object : UsageSampleProvider {
        override fun queryTodaySamples(
            context: Context,
            dayStartMs: Long,
            nowMs: Long
        ): List<UsageSample> {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return emptyList()
            val merged = mutableMapOf<String, Long>()

            // Primary: one aggregated call for the whole day.
            try {
                val aggregated = usageStatsManager.queryAndAggregateUsageStats(dayStartMs, nowMs)
                if (!aggregated.isNullOrEmpty()) {
                    for ((pkg, stats) in aggregated) {
                        if (stats.totalTimeInForeground > 0L) {
                            merged[pkg] = stats.totalTimeInForeground
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryAndAggregateUsageStats failed", e)
            }

            // Fallback/supplement: daily-interval stats, keep the max per
            // package (some OEMs under-report one of the two APIs).
            try {
                val statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    dayStartMs,
                    nowMs
                )
                if (!statsList.isNullOrEmpty()) {
                    for (stats in statsList) {
                        if (stats.totalTimeInForeground > 0L) {
                            val current = merged[stats.packageName] ?: 0L
                            merged[stats.packageName] =
                                maxOf(current, stats.totalTimeInForeground)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryUsageStats fallback failed", e)
            }

            return merged.map { (pkg, ms) -> UsageSample(pkg, ms) }
        }
    }

    // --- Test seams (internal; production always uses the system provider) ---
    @Volatile
    internal var testProvider: UsageSampleProvider? = null

    @Volatile
    internal var testPermissionOverride: Boolean? = null

    private val cacheLock = Any()

    @Volatile
    private var cached: DeviceUsageSnapshot? = null

    /** True when PACKAGE_USAGE_STATS is granted to Focivo. */
    fun hasUsagePermission(context: Context): Boolean =
        testPermissionOverride ?: PermissionUtils.hasUsageStatsPermission(context)

    /** "yyyy-MM-dd" in the device timezone — the day bucket key. */
    fun dayKeyFor(timeMs: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timeMs))
    }

    /** Start of the day (00:00:00.000) in the device timezone. */
    fun dayStartMillis(timeMs: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Pure aggregation: merges samples per package (keeping the max when a
     * package appears twice) and sums the device total. Unit-tested.
     */
    fun aggregate(samples: List<UsageSample>): Pair<Map<String, Long>, Long> {
        val perApp = mutableMapOf<String, Long>()
        for (s in samples) {
            if (s.totalTimeInForegroundMs <= 0L) continue
            val current = perApp[s.packageName] ?: 0L
            perApp[s.packageName] = maxOf(current, s.totalTimeInForegroundMs)
        }
        return perApp to perApp.values.sum()
    }

    /** Pure cache-freshness decision. Unit-tested. */
    internal fun shouldRefresh(
        cachedSnapshot: DeviceUsageSnapshot?,
        todayKey: String,
        nowMs: Long
    ): Boolean {
        if (cachedSnapshot == null) return true
        if (cachedSnapshot.dayKey != todayKey) return true
        return nowMs - cachedSnapshot.queriedAtMs > CACHE_TTL_MS
    }

    /**
     * Returns today's snapshot, or null when usage permission is missing.
     * Cached per app-open (TTL [CACHE_TTL_MS]); pass [forceRefresh] to bypass
     * the cache (e.g. when the user returns from system settings).
     */
    fun getSnapshot(context: Context, forceRefresh: Boolean = false): DeviceUsageSnapshot? {
        if (!hasUsagePermission(context)) return null
        val now = System.currentTimeMillis()
        val todayKey = dayKeyFor(now)

        synchronized(cacheLock) {
            val existing = cached
            if (!forceRefresh && existing != null && !shouldRefresh(existing, todayKey, now)) {
                return existing
            }

            val dayStart = dayStartMillis(now)
            val provider = testProvider ?: systemProvider
            val samples = try {
                provider.queryTodaySamples(context, dayStart, now)
            } catch (e: Exception) {
                Log.w(TAG, "provider query failed", e)
                emptyList()
            }
            val (perApp, total) = aggregate(samples)
            val snapshot = DeviceUsageSnapshot(
                dayKey = todayKey,
                perAppMillis = perApp,
                totalMillis = total,
                queriedAtMs = now
            )
            cached = snapshot
            return snapshot
        }
    }

    /** Today's foreground millis for one package (0 when unknown). */
    fun usageMillisForPackage(context: Context, packageName: String): Long {
        val snapshot = getSnapshot(context) ?: return 0L
        return snapshot.perAppMillis[packageName] ?: 0L
    }

    /** Today's total device usage millis (sum of all apps). */
    fun deviceTotalMillis(context: Context): Long {
        return getSnapshot(context)?.totalMillis ?: 0L
    }

    /** Drops the in-memory cache; the next read re-queries UsageStats. */
    fun invalidate() {
        synchronized(cacheLock) { cached = null }
    }
}
