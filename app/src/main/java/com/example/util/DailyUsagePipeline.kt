package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * v1.1.1 — the ONE source of truth for device usage stats.
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
 * Today's per-app numbers come from EVENT PAIRING ([sumForegroundTime]):
 * ACTIVITY_RESUMED/ACTIVITY_PAUSED pairs clipped to [local midnight, now] —
 * the same method Digital Wellbeing-style dashboards use, so the Study header
 * tracks the system per-app minutes (YouTube, Muse, ...) and the summed
 * total. The aggregate query survives only as a per-package FLOOR: on OEMs
 * with sparse event logs it fills under-counted packages — never the reverse.
 *
 * Pure, hermetic helpers ([sumForegroundTime], [aggregate], [dayKeyFor],
 * [shouldRefresh]) are unit-tested; the Android query path is injected via
 * [UsageSampleProvider] so tests can substitute a fake provider without a
 * UsageStatsManager.
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

    /** Production provider: event-paired per-package foreground time. */
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

            // PRIMARY (v1.1.1): pair ACTIVITY_RESUMED/ACTIVITY_PAUSED events
            // clipped to [dayStartMs, nowMs] — Digital Wellbeing fidelity.
            var eventCount = 0
            try {
                val query = UsageEventsQuery.Builder(dayStartMs, nowMs)
                    .setEventTypes(
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.DEVICE_SHUTDOWN,
                        UsageEvents.Event.DEVICE_STARTUP
                    )
                    .build()
                val usageEvents = usageStatsManager.queryEvents(query)
                val events = mutableListOf<AppUsageEvent>()
                if (usageEvents != null) {
                    val ev = UsageEvents.Event()
                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(ev)
                        events.add(
                            AppUsageEvent(
                                ev.packageName ?: continue,
                                when (ev.eventType) {
                                    UsageEvents.Event.ACTIVITY_RESUMED -> AppEventType.RESUMED
                                    UsageEvents.Event.ACTIVITY_PAUSED -> AppEventType.PAUSED
                                    UsageEvents.Event.DEVICE_SHUTDOWN -> AppEventType.SHUTDOWN
                                    UsageEvents.Event.DEVICE_STARTUP -> AppEventType.STARTUP
                                    else -> continue
                                },
                                ev.timeStamp
                            )
                        )
                        eventCount++
                    }
                }
                if (events.isNotEmpty()) {
                    merged.putAll(sumForegroundTime(events, dayStartMs, nowMs))
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryEvents pairing failed", e)
            }

            // FALLBACK/FLOOR: per-package aggregate — only RAISES a package's
            // number, never lowers it (OEMs with sparse event logs can
            // under-count the pairing). When the event list was empty this is
            // the whole answer.
            try {
                val aggregated = usageStatsManager.queryAndAggregateUsageStats(dayStartMs, nowMs)
                if (!aggregated.isNullOrEmpty()) {
                    for ((pkg, stats) in aggregated) {
                        val fg = stats.totalTimeInForeground
                        if (fg > 0L) {
                            merged[pkg] = maxOf(merged[pkg] ?: 0L, fg)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryAndAggregateUsageStats failed", e)
            }

            // Second fallback: daily-interval stats, anchored to today by
            // mergeDailyIntervalStats (v1.3.4 fix — never merges buckets that
            // started before local midnight).
            try {
                val statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    dayStartMs,
                    nowMs
                )
                if (!statsList.isNullOrEmpty()) {
                    val samples = statsList.map { stats ->
                        DailyBucketSample(
                            stats.packageName,
                            stats.totalTimeInForeground,
                            stats.firstTimeStamp
                        )
                    }
                    mergeDailyIntervalStats(merged, samples, dayStartMs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryUsageStats fallback failed", e)
            }

            Log.d(TAG, "queryTodaySamples: $eventCount events -> ${merged.size} pkgs, total=${merged.values.sum()}")
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
     * One daily-interval bucket from UsageStatsManager (hermetic view of
     * [android.app.usage.UsageStats]: package, foreground millis, and the
     * bucket's start — see [android.app.usage.UsageStats.getFirstTimeStamp]).
     */
    internal data class DailyBucketSample(
        val packageName: String,
        val totalTimeInForegroundMs: Long,
        val bucketStartMs: Long
    )

    /** Kinds of usage events the pairing cares about (hermetic). */
    internal enum class AppEventType { RESUMED, PAUSED, SHUTDOWN, STARTUP }

    /** One hermetic usage event: package + kind + wall-clock time. */
    internal data class AppUsageEvent(
        val packageName: String,
        val type: AppEventType,
        val timeMs: Long
    )

    /**
     * v1.1.1 — pure event pairing: foreground millis per package.
     *
     * Pairs ACTIVITY_RESUMED/ACTIVITY_PAUSED per package, clipping every
     * interval to [dayStartMs, nowMs]. Rules:
     * - RESUMED opens an interval (re-open is ignored while already open).
     * - PAUSED closes it and banks the clipped duration.
     * - DEVICE_SHUTDOWN closes ALL open intervals at the shutdown time (the
     *   platform generates no matching PAUSED after a shutdown; events between
     *   SHUTDOWN and STARTUP are ignored per the UsageEvents docs).
     * - At the end of the stream, still-open intervals close at nowMs (the app
     *   is still on screen).
     * - Split-screen/PiP: two apps can be open at once; each banks its own
     *   foreground time — the total is the sum of per-app minutes, exactly
     *   like the system "Today" total.
     * Unit-tested (split-screen overlap, midnight-spanning sessions,
     * resume-without-pause, shutdown, per-app-sum-equals-total).
     */
    internal fun sumForegroundTime(
        events: List<AppUsageEvent>,
        dayStartMs: Long,
        nowMs: Long
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val openStart = mutableMapOf<String, Long>()

        fun close(pkg: String, endMs: Long) {
            val start = openStart.remove(pkg) ?: return
            val s = maxOf(start, dayStartMs)
            val e = minOf(endMs, nowMs)
            if (e > s) totals[pkg] = (totals[pkg] ?: 0L) + (e - s)
        }

        for (ev in events.sortedBy { it.timeMs }) {
            val t = ev.timeMs.coerceIn(dayStartMs, nowMs)
            when (ev.type) {
                AppEventType.RESUMED ->
                    if (!openStart.containsKey(ev.packageName)) openStart[ev.packageName] = t
                AppEventType.PAUSED -> close(ev.packageName, t)
                AppEventType.SHUTDOWN -> {
                    val pkgs = openStart.keys.toList()
                    for (pkg in pkgs) close(pkg, t)
                }
                AppEventType.STARTUP -> { /* already closed at SHUTDOWN; nothing to do */ }
            }
        }
        // Still on screen at query time.
        for (pkg in openStart.keys.toList()) close(pkg, nowMs)
        return totals
    }

    /**
     * v1.3.4 — pure merge of the daily-interval fallback stats.
     *
     * Keeps the max per package (some OEMs under-report one of the two
     * UsageStats APIs), but SKIPS buckets whose interval started before
     * [dayStartMs]: queryUsageStats(INTERVAL_DAILY) returns every bucket
     * intersecting the query range, including the previous day's bucket,
     * which otherwise leaks yesterday's minutes into today's totals.
     * Unit-tested (incl. an Asia/Kolkata midnight-boundary case).
     */
    internal fun mergeDailyIntervalStats(
        merged: MutableMap<String, Long>,
        stats: List<DailyBucketSample>,
        dayStartMs: Long
    ): MutableMap<String, Long> {
        for (s in stats) {
            if (s.totalTimeInForegroundMs <= 0L) continue
            if (s.bucketStartMs < dayStartMs) continue
            val current = merged[s.packageName] ?: 0L
            merged[s.packageName] = maxOf(current, s.totalTimeInForegroundMs)
        }
        return merged
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
