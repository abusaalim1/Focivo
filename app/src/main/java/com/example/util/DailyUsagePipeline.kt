package com.example.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Single source of truth for device screen-time detection.
 *
 * Every surface that needs screen-time data (Study-top header, Insights,
 * per-app list, and budgets) reads from [getSnapshot], keeping numbers
 * identical everywhere.
 *
 * 1. Permission: always checks PACKAGE_USAGE_STATS via AppOpsManager
 *    (OPSTR_GET_USAGE_STATS). When not granted, clears any cached snapshot
 *    and returns null so callers display "--" (never 0 or stale values).
 * 2. Data: queries UsageEvents from (local midnight - 48 hours) to now,
 *    pairs ACTIVITY_RESUMED / MOVE_TO_FOREGROUND with ACTIVITY_PAUSED /
 *    MOVE_TO_BACKGROUND and ACTIVITY_STOPPED (plus DEVICE_SHUTDOWN /
 *    DEVICE_STARTUP) into foreground intervals per package, clips intervals
 *    to [today 00:00, now], and coalesces overlapping intervals per package
 *    so overlapping intervals are never double-counted.
 * 3. Cross-check: reconciles per app against queryUsageStats(INTERVAL_DAILY)
 *    for today and queryAndAggregateUsageStats(midnight, now), choosing the
 *    largest of the three values per app (clamped to the elapsed day length).
 *    The header total is strictly the sum of the per-app values from this
 *    single snapshot.
 * 4. Diagnostics: records a Guard Hub diagnostic log entry (USAGE_RECONCILE)
 *    listing, per app, the three raw values plus the chosen one.
 */
object DailyUsagePipeline {

    private const val TAG = "DailyUsagePipeline"

    /** In-memory cache TTL — UsageStatsManager queries are expensive. */
    const val CACHE_TTL_MS = 60_000L

    /** 48-hour lookback window before local midnight for UsageEvents queries. */
    const val LOOKBACK_BEFORE_MIDNIGHT_MS = 48L * 60L * 60L * 1000L

    /** Upper bound on a calendar day's length in milliseconds (24 hours). */
    const val MAX_DAY_LENGTH_MS = 24L * 60L * 60L * 1000L

    /** Guard Hub stage tag for screen-time reconciliation diagnostics. */
    const val STAGE_USAGE_RECONCILE = "USAGE_RECONCILE"

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

    /** Per-package three-way reconciliation record for USAGE_RECONCILE logs. */
    internal data class ReconcileEntry(
        val packageName: String,
        val eventsMs: Long,
        val dailyMs: Long,
        val aggregateMs: Long,
        val chosenMs: Long
    )

    /** Production provider: 48h event-paired intervals + 3-way reconciliation. */
    private val systemProvider = object : UsageSampleProvider {
        override fun queryTodaySamples(
            context: Context,
            dayStartMs: Long,
            nowMs: Long
        ): List<UsageSample> {
            if (!checkAppOpsUsageStats(context)) return emptyList()

            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return emptyList()

            val queryStartMs = (dayStartMs - LOOKBACK_BEFORE_MIDNIGHT_MS).coerceAtLeast(0L)
            var eventsMap: Map<String, Long> = emptyMap()
            val dailyMap = mutableMapOf<String, Long>()
            val aggregateMap = mutableMapOf<String, Long>()

            // 1. Query UsageEvents from (local midnight - 48h) to now.
            // Note: MOVE_TO_FOREGROUND == ACTIVITY_RESUMED (1) and
            // MOVE_TO_BACKGROUND == ACTIVITY_PAUSED (2) in UsageEvents.Event.
            var eventCount = 0
            try {
                val usageEvents = if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) {
                    val query = UsageEventsQuery.Builder(queryStartMs, nowMs)
                        .setEventTypes(
                            UsageEvents.Event.ACTIVITY_RESUMED,
                            UsageEvents.Event.ACTIVITY_PAUSED,
                            UsageEvents.Event.ACTIVITY_STOPPED,
                            UsageEvents.Event.DEVICE_SHUTDOWN,
                            UsageEvents.Event.DEVICE_STARTUP
                        )
                        .build()
                    usageStatsManager.queryEvents(query)
                } else {
                    @Suppress("DEPRECATION")
                    usageStatsManager.queryEvents(queryStartMs, nowMs)
                }
                val events = mutableListOf<AppUsageEvent>()
                if (usageEvents != null) {
                    val ev = UsageEvents.Event()
                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(ev)
                        val pkg = ev.packageName
                        val mappedType = when (ev.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED,
                            UsageEvents.Event.MOVE_TO_FOREGROUND -> AppEventType.RESUMED
                            UsageEvents.Event.ACTIVITY_PAUSED,
                            UsageEvents.Event.MOVE_TO_BACKGROUND -> AppEventType.PAUSED
                            UsageEvents.Event.ACTIVITY_STOPPED -> AppEventType.STOPPED
                            UsageEvents.Event.DEVICE_SHUTDOWN -> AppEventType.SHUTDOWN
                            UsageEvents.Event.DEVICE_STARTUP -> AppEventType.STARTUP
                            else -> null
                        } ?: continue

                        if (mappedType != AppEventType.SHUTDOWN &&
                            mappedType != AppEventType.STARTUP &&
                            pkg.isNullOrBlank()
                        ) {
                            continue
                        }

                        events.add(
                            AppUsageEvent(
                                packageName = pkg ?: "",
                                type = mappedType,
                                timeMs = ev.timeStamp,
                                className = ev.className
                            )
                        )
                        eventCount++
                    }
                }
                if (events.isNotEmpty()) {
                    eventsMap = sumForegroundTime(events, dayStartMs, nowMs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryEvents pairing failed", e)
            }

            // 2. Cross-check source A: queryUsageStats(INTERVAL_DAILY) for today.
            try {
                val statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    dayStartMs,
                    nowMs
                )
                if (!statsList.isNullOrEmpty()) {
                    val samples = statsList.mapNotNull { stats ->
                        val pkg = stats.packageName ?: return@mapNotNull null
                        DailyBucketSample(
                            packageName = pkg,
                            totalTimeInForegroundMs = stats.totalTimeInForeground,
                            bucketStartMs = stats.firstTimeStamp
                        )
                    }
                    mergeDailyIntervalStats(dailyMap, samples, dayStartMs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryUsageStats(INTERVAL_DAILY) failed", e)
            }

            // 3. Cross-check source B: queryAndAggregateUsageStats(midnight, now).
            try {
                val aggregated = usageStatsManager.queryAndAggregateUsageStats(dayStartMs, nowMs)
                if (!aggregated.isNullOrEmpty()) {
                    for ((pkg, stats) in aggregated) {
                        if (pkg.isBlank()) continue
                        val fg = stats.totalTimeInForeground
                        if (fg > 0L) {
                            aggregateMap[pkg] = maxOf(aggregateMap[pkg] ?: 0L, fg)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryAndAggregateUsageStats failed", e)
            }

            // 4. Reconcile per app: largest of the three values, clamped to day length.
            val reconciled = reconcilePerAppUsage(
                eventsMap = eventsMap,
                dailyMap = dailyMap,
                aggregateMap = aggregateMap,
                dayStartMs = dayStartMs,
                nowMs = nowMs
            )

            // 5. Emit Guard Hub diagnostic log entry (USAGE_RECONCILE) per app.
            recordReconcileDiagnostics(context, reconciled)

            Log.d(
                TAG,
                "queryTodaySamples: $eventCount events -> ${reconciled.size} pkgs, " +
                    "total=${reconciled.sumOf { it.chosenMs }}"
            )
            return reconciled.map { UsageSample(it.packageName, it.chosenMs) }
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

    /**
     * Direct AppOpsManager check for OPSTR_GET_USAGE_STATS.
     */
    internal fun checkAppOpsUsageStats(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /** True when PACKAGE_USAGE_STATS is granted via AppOpsManager. */
    fun hasUsagePermission(context: Context): Boolean {
        val granted = testPermissionOverride ?: checkAppOpsUsageStats(context)
        if (!granted) {
            synchronized(cacheLock) { cached = null }
        }
        return granted
    }

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
    internal enum class AppEventType { RESUMED, PAUSED, STOPPED, SHUTDOWN, STARTUP }

    /** One hermetic usage event: package + kind + wall-clock time + optional activity class. */
    internal data class AppUsageEvent(
        val packageName: String,
        val type: AppEventType,
        val timeMs: Long,
        val className: String? = null
    )

    /**
     * Coalesces a list of [startMs, endMs] intervals so overlapping or adjacent
     * intervals are merged and never double-counted.
     */
    internal fun mergeIntervals(intervals: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        val valid = intervals.filter { (s, e) -> e > s }.sortedWith(
            compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second }
        )
        if (valid.isEmpty()) return emptyList()

        val merged = mutableListOf<Pair<Long, Long>>()
        var curStart = valid[0].first
        var curEnd = valid[0].second

        for (i in 1 until valid.size) {
            val (nextStart, nextEnd) = valid[i]
            if (nextStart <= curEnd) {
                if (nextEnd > curEnd) curEnd = nextEnd
            } else {
                merged.add(curStart to curEnd)
                curStart = nextStart
                curEnd = nextEnd
            }
        }
        merged.add(curStart to curEnd)
        return merged
    }

    /**
     * Pure event pairing: foreground millis per package.
     *
     * Pairs ACTIVITY_RESUMED / MOVE_TO_FOREGROUND with ACTIVITY_PAUSED /
     * MOVE_TO_BACKGROUND and ACTIVITY_STOPPED (plus DEVICE_SHUTDOWN /
     * DEVICE_STARTUP) into foreground intervals per package, clips every
     * interval to [dayStartMs, nowMs], and merges overlapping intervals per
     * package so overlapping transitions are never double-counted and sessions
     * starting before midnight contribute their after-midnight portion.
     */
    internal fun sumForegroundTime(
        events: List<AppUsageEvent>,
        dayStartMs: Long,
        nowMs: Long
    ): Map<String, Long> {
        if (nowMs <= dayStartMs || events.isEmpty()) return emptyMap()

        // Track open intervals per (packageName, activityKey) so an intra-app
        // ActivityA -> ActivityB handoff where ActivityA emits STOPPED shortly
        // after ActivityB emits RESUMED closes only ActivityA and never drops
        // ActivityB, while merging intervals per package prevents any overlap
        // from being double-counted.
        val openStartByComponent = mutableMapOf<Pair<String, String>, Long>()
        val rawIntervalsByPkg = mutableMapOf<String, MutableList<Pair<Long, Long>>>()

        fun recordInterval(pkg: String, startMs: Long, endMs: Long) {
            if (pkg.isBlank()) return
            val clippedStart = maxOf(startMs, dayStartMs)
            val clippedEnd = minOf(endMs, nowMs)
            if (clippedEnd > clippedStart) {
                rawIntervalsByPkg.getOrPut(pkg) { mutableListOf() }
                    .add(clippedStart to clippedEnd)
            }
        }

        fun closeComponent(pkg: String, clsKey: String, endMs: Long) {
            val exactKey = pkg to clsKey
            val start = openStartByComponent.remove(exactKey)
            if (start != null) {
                recordInterval(pkg, start, endMs)
                return
            }
            // If the closing event has no className (or only a package-level
            // open entry exists), close matching open entries for this package.
            if (clsKey.isEmpty()) {
                val matchingKeys = openStartByComponent.keys.filter { it.first == pkg }
                for (k in matchingKeys) {
                    val s = openStartByComponent.remove(k) ?: continue
                    recordInterval(pkg, s, endMs)
                }
            } else {
                val fallbackKey = pkg to ""
                val fallbackStart = openStartByComponent.remove(fallbackKey)
                if (fallbackStart != null) {
                    recordInterval(pkg, fallbackStart, endMs)
                }
            }
        }

        val sortedEvents = events.sortedBy { it.timeMs }
        for (ev in sortedEvents) {
            val t = ev.timeMs
            if (t > nowMs) continue
            val pkg = ev.packageName
            val clsKey = ev.className?.trim().orEmpty()

            when (ev.type) {
                AppEventType.RESUMED -> {
                    if (pkg.isNotBlank()) {
                        val compKey = pkg to clsKey
                        if (!openStartByComponent.containsKey(compKey)) {
                            openStartByComponent[compKey] = t
                        }
                    }
                }
                AppEventType.PAUSED,
                AppEventType.STOPPED -> {
                    if (pkg.isNotBlank()) {
                        closeComponent(pkg, clsKey, t)
                    }
                }
                AppEventType.SHUTDOWN -> {
                    val allOpenKeys = openStartByComponent.keys.toList()
                    for (k in allOpenKeys) {
                        val s = openStartByComponent.remove(k) ?: continue
                        recordInterval(k.first, s, t)
                    }
                }
                AppEventType.STARTUP -> {
                    // Already closed at SHUTDOWN.
                }
            }
        }

        // Still on screen at query time: close at nowMs.
        val remainingKeys = openStartByComponent.keys.toList()
        for (k in remainingKeys) {
            val s = openStartByComponent.remove(k) ?: continue
            recordInterval(k.first, s, nowMs)
        }

        val totals = mutableMapOf<String, Long>()
        for ((pkg, intervals) in rawIntervalsByPkg) {
            val coalesced = mergeIntervals(intervals)
            val totalMs = coalesced.sumOf { (s, e) -> e - s }
            if (totalMs > 0L) {
                totals[pkg] = totalMs
            }
        }
        return totals
    }

    /**
     * Pure merge of the daily-interval stats.
     *
     * Keeps the max per package, skipping buckets whose interval started before
     * [dayStartMs]: queryUsageStats(INTERVAL_DAILY) returns every bucket
     * intersecting the query range, including the previous day's bucket,
     * which otherwise leaks yesterday's minutes into today's totals.
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
     * Cross-checks per-app foreground time across the three UsageStatsManager
     * sources (48h-clipped UsageEvents intervals, today's INTERVAL_DAILY stats,
     * and queryAndAggregateUsageStats(midnight, now)) and chooses the largest
     * of the three values per app, clamped to the elapsed day length.
     */
    internal fun reconcilePerAppUsage(
        eventsMap: Map<String, Long>,
        dailyMap: Map<String, Long>,
        aggregateMap: Map<String, Long>,
        dayStartMs: Long,
        nowMs: Long
    ): List<ReconcileEntry> {
        val dayElapsedCapMs = (nowMs - dayStartMs).coerceIn(0L, MAX_DAY_LENGTH_MS)
        val allPackages = LinkedHashSet<String>().apply {
            addAll(eventsMap.keys)
            addAll(dailyMap.keys)
            addAll(aggregateMap.keys)
        }
        val results = mutableListOf<ReconcileEntry>()
        for (pkg in allPackages) {
            if (pkg.isBlank()) continue
            val evMs = (eventsMap[pkg] ?: 0L).coerceAtLeast(0L)
            val dailyMs = (dailyMap[pkg] ?: 0L).coerceAtLeast(0L)
            val aggMs = (aggregateMap[pkg] ?: 0L).coerceAtLeast(0L)
            val rawMax = maxOf(evMs, dailyMs, aggMs)
            val chosenMs = if (dayElapsedCapMs > 0L) {
                rawMax.coerceAtMost(dayElapsedCapMs)
            } else {
                0L
            }
            if (chosenMs > 0L) {
                results.add(
                    ReconcileEntry(
                        packageName = pkg,
                        eventsMs = evMs,
                        dailyMs = dailyMs,
                        aggregateMs = aggMs,
                        chosenMs = chosenMs
                    )
                )
            }
        }
        return results.sortedByDescending { it.chosenMs }
    }

    /**
     * Records per-app USAGE_RECONCILE diagnostic entries in the Guard Hub log
     * with the three raw values plus the chosen one.
     */
    internal fun recordReconcileDiagnostics(
        context: Context,
        entries: List<ReconcileEntry>
    ) {
        for (entry in entries) {
            if (entry.chosenMs <= 0L) continue
            try {
                AiStudyGuardManager.logGuardActivity(
                    context = context,
                    app = entry.packageName,
                    stage = STAGE_USAGE_RECONCILE,
                    verdict = "RECONCILED",
                    reason = "ev=${entry.eventsMs}ms daily=${entry.dailyMs}ms agg=${entry.aggregateMs}ms",
                    snippet = "chosen=${entry.chosenMs}ms"
                )
            } catch (_: Throwable) {
            }
        }
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
     * Invalidates the cached snapshot if the local calendar day has rolled over.
     * Returns true when a rollover was detected and cache was cleared.
     */
    fun refreshIfDayChanged(nowMs: Long = System.currentTimeMillis()): Boolean {
        val todayKey = dayKeyFor(nowMs)
        synchronized(cacheLock) {
            val existing = cached ?: return false
            if (existing.dayKey != todayKey) {
                cached = null
                return true
            }
            return false
        }
    }

    /**
     * Returns today's snapshot, or null when usage permission is missing.
     * Cached per app-open (TTL [CACHE_TTL_MS]); pass [forceRefresh] to bypass
     * the cache (e.g. when the user returns from system settings or on resume).
     */
    fun getSnapshot(context: Context, forceRefresh: Boolean = false): DeviceUsageSnapshot? {
        if (!hasUsagePermission(context)) {
            synchronized(cacheLock) { cached = null }
            return null
        }
        val now = System.currentTimeMillis()
        val todayKey = dayKeyFor(now)
        val dayStart = dayStartMillis(now)

        synchronized(cacheLock) {
            val existing = cached
            if (!forceRefresh && existing != null && !shouldRefresh(existing, todayKey, now)) {
                return existing
            }

            val provider = testProvider ?: systemProvider
            val rawSamples = try {
                provider.queryTodaySamples(context, dayStart, now)
            } catch (e: Exception) {
                Log.w(TAG, "provider query failed", e)
                emptyList()
            }
            val clampedSamples = rawSamples.map { s ->
                if (s.totalTimeInForegroundMs > MAX_DAY_LENGTH_MS) {
                    s.copy(totalTimeInForegroundMs = MAX_DAY_LENGTH_MS)
                } else {
                    s
                }
            }
            val (perApp, total) = aggregate(clampedSamples)
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

    /** Today's foreground millis for one package (0 when unknown or ungranted). */
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

    /**
     * Read-only freshness accessor for the Block hub's "Screen time" readiness
     * card: when the usage numbers were last computed (null when no snapshot
     * has been computed this app-open or permission is revoked).
     */
    fun lastQueriedAtMs(): Long? {
        synchronized(cacheLock) { return cached?.queriedAtMs }
    }
}
