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
 * total.
 *
 * v1.1.4 — CROSS-VALIDATION: the aggregate reference sources
 * (queryUsageStats INTERVAL_DAILY and queryAndAggregateUsageStats) are no
 * longer blind max() floors. Their buckets are boundary-checked against the
 * local day (buckets that started before local midnight are dropped — the
 * 1.3.4 guard only covered the daily path and left the aggregate path
 * leaking yesterday's full-bucket totals into today), then reconciled
 * against the event-paired totals by [reconcileUsage]. Material
 * disagreements are resolved to a sane value (event-paired wins when the
 * bucket is unusable or lags; the clean bucket fills genuinely sparse event
 * logs) and logged to the Guard Hub diagnostics stream so the owner can see
 * which source disagreed and by how much.
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
            // v1.1.4: also listen to SCREEN_INTERACTIVE/NON_INTERACTIVE —
            // screen-off closes every open interval (some OEMs drop the
            // matching ACTIVITY_PAUSED when the screen goes dark) and a
            // DEVICE_STARTUP closes orphaned intervals left open by a
            // crash/power-loss that never recorded a DEVICE_SHUTDOWN.
            var eventCount = 0
            try {
                // UsageEventsQuery.Builder is API 34+; the deprecated
                // queryEvents(begin, end) covers API 21-33 (minSdk is 24).
                val usageEvents = if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) {
                    val query = UsageEventsQuery.Builder(dayStartMs, nowMs)
                        .setEventTypes(
                            UsageEvents.Event.ACTIVITY_RESUMED,
                            UsageEvents.Event.ACTIVITY_PAUSED,
                            UsageEvents.Event.DEVICE_SHUTDOWN,
                            UsageEvents.Event.DEVICE_STARTUP,
                            UsageEvents.Event.SCREEN_INTERACTIVE,
                            UsageEvents.Event.SCREEN_NON_INTERACTIVE
                        )
                        .build()
                    usageStatsManager.queryEvents(query)
                } else {
                    @Suppress("DEPRECATION")
                    usageStatsManager.queryEvents(dayStartMs, nowMs)
                }
                val events = mutableListOf<AppUsageEvent>()
                if (usageEvents != null) {
                    val ev = UsageEvents.Event()
                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(ev)
                        val type = when (ev.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED -> AppEventType.RESUMED
                            UsageEvents.Event.ACTIVITY_PAUSED -> AppEventType.PAUSED
                            UsageEvents.Event.DEVICE_SHUTDOWN -> AppEventType.SHUTDOWN
                            UsageEvents.Event.DEVICE_STARTUP -> AppEventType.STARTUP
                            UsageEvents.Event.SCREEN_INTERACTIVE -> AppEventType.SCREEN_ON
                            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> AppEventType.SCREEN_OFF
                            else -> continue
                        }
                        // System-level events (screen/shutdown/startup) may
                        // carry no package; they never use it, so "" is fine.
                        events.add(AppUsageEvent(ev.packageName ?: "", type, ev.timeStamp))
                        eventCount++
                    }
                }
                val paired = sumForegroundTime(events, dayStartMs, nowMs)

                // REFERENCE SOURCES (v1.1.4): aggregate buckets used only for
                // cross-validation — every bucket is boundary-checked in
                // reconcileUsage, so yesterday's full-bucket totals can never
                // leak into today through either source again.
                val bucketSamples = mutableListOf<DailyBucketSample>()

                // 1) Daily-interval buckets (expose firstTimeStamp).
                try {
                    val statsList = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        dayStartMs,
                        nowMs
                    )
                    if (!statsList.isNullOrEmpty()) {
                        for (stats in statsList) {
                            bucketSamples.add(
                                DailyBucketSample(
                                    stats.packageName,
                                    stats.totalTimeInForeground,
                                    stats.firstTimeStamp
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "queryUsageStats fallback failed", e)
                }

                // 2) Aggregate stats: fills packages the daily query missed.
                // UsageStats carries firstTimeStamp too, so this source gets
                // the same boundary check — 1.3.4 left this path unguarded and
                // it kept merging yesterday's bucket into today.
                try {
                    val dailyPkgs = bucketSamples.map { it.packageName }.toSet()
                    val aggregated =
                        usageStatsManager.queryAndAggregateUsageStats(dayStartMs, nowMs)
                    if (!aggregated.isNullOrEmpty()) {
                        for ((pkg, stats) in aggregated) {
                            if (pkg in dailyPkgs) continue // daily bucket wins
                            bucketSamples.add(
                                DailyBucketSample(
                                    pkg,
                                    stats.totalTimeInForeground,
                                    stats.firstTimeStamp
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "queryAndAggregateUsageStats failed", e)
                }

                val reconcile = reconcileUsage(paired, bucketSamples, dayStartMs, nowMs)
                merged.putAll(reconcile.perAppMillis)
                logReconcileDiagnostics(
                    context, dayStartMs, reconcile.discrepancies,
                    eventCount, bucketSamples.size
                )
            } catch (e: Exception) {
                Log.w(TAG, "queryEvents pairing failed", e)
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

    // --- v1.1.4 Guard Hub diagnostics throttle (one entry per 30 min per day) ---
    @Volatile
    private var lastDiagDayKey = ""

    @Volatile
    private var lastDiagLogMs = 0L

    private val cacheLock = Any()

    @Volatile
    private var cached: DeviceUsageSnapshot? = null

    /**
     * v1.1.4 — surfaces [reconcileUsage] disagreements (and the "system
     * returned nothing at all" case) in the Guard Hub diagnostics stream,
     * throttled to one entry per 30 minutes per day so the 60s snapshot TTL
     * cannot flood the hub. All strings are plain English, no emoji.
     */
    private fun logReconcileDiagnostics(
        context: Context,
        dayStartMs: Long,
        discrepancies: List<BucketDiscrepancy>,
        eventCount: Int,
        bucketCount: Int
    ) {
        val dayKey = dayKeyFor(dayStartMs)
        val now = System.currentTimeMillis()
        val entries: List<Triple<String, String, String>> =
            if (discrepancies.isNotEmpty()) {
                discrepancies
                    .sortedByDescending { kotlin.math.abs(it.bucketMs - it.pairedMs) }
                    .take(3)
                    .map { d ->
                        Triple(
                            "USAGE_RECONCILE",
                            "MISMATCH",
                            "${d.packageName}: events ${d.pairedMs / 60000}m vs " +
                                "bucket ${d.bucketMs / 60000}m; ${d.note}"
                        )
                    }
            } else if (eventCount == 0 && bucketCount == 0) {
                listOf(
                    Triple(
                        "USAGE_RECONCILE",
                        "NO_DATA",
                        "usage access granted but the system returned no usage data"
                    )
                )
            } else {
                return
            }

        synchronized(cacheLock) {
            if (dayKey == lastDiagDayKey && now - lastDiagLogMs < 30 * 60_000L) return
            lastDiagDayKey = dayKey
            lastDiagLogMs = now
        }
        for ((stage, verdict, reason) in entries) {
            Log.w(TAG, "usage diagnostics [$verdict]: $reason")
            try {
                AiStudyGuardManager.logGuardActivity(
                    context = context,
                    app = "Screen time",
                    stage = stage,
                    verdict = verdict,
                    reason = reason
                )
            } catch (_: Exception) {
                // Diagnostics must never break the usage query itself.
            }
        }
    }

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
    internal enum class AppEventType {
        RESUMED, PAUSED, SHUTDOWN, STARTUP, SCREEN_ON, SCREEN_OFF
    }

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
     * - RESUMED opens an interval (re-open is ignored while already open —
     *   activity flips and ACTIVITY_RESUMED spam never double-count).
     * - PAUSED closes it and banks the clipped duration. An unmatched PAUSED
     *   (no open interval) is ignored — OEMs can emit stray PAUSED events.
     * - DEVICE_SHUTDOWN and SCREEN_NON_INTERACTIVE close ALL open intervals
     *   at that time: the platform generates no matching PAUSED after a
     *   shutdown, and some OEMs drop the PAUSED when the screen goes dark.
     *   (Screen-off splits each session exactly like Digital Wellbeing.)
     * - DEVICE_STARTUP closes any intervals that are still open: with a
     *   SHUTDOWN they are already closed, so anything open here crossed a
     *   crash/power-loss gap with no recorded shutdown — it must not run on
     *   to nowMs.
     * - At the end of the stream, still-open intervals close at nowMs (the app
     *   is still on screen).
     * - Split-screen/PiP: two apps can be open at once; each banks its own
     *   foreground time — the total is the sum of per-app minutes, exactly
     *   like the system "Today" total.
     * Unit-tested (split-screen overlap, midnight-spanning sessions,
     * resume-without-pause, shutdown, screen-off split, startup-after-crash,
     * per-app-sum-equals-total).
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

        fun closeAll(endMs: Long) {
            for (pkg in openStart.keys.toList()) close(pkg, endMs)
        }

        for (ev in events.sortedBy { it.timeMs }) {
            val t = ev.timeMs.coerceIn(dayStartMs, nowMs)
            when (ev.type) {
                AppEventType.RESUMED ->
                    if (!openStart.containsKey(ev.packageName)) openStart[ev.packageName] = t
                AppEventType.PAUSED -> close(ev.packageName, t)
                AppEventType.SHUTDOWN, AppEventType.SCREEN_OFF -> closeAll(t)
                AppEventType.STARTUP -> closeAll(t)
                AppEventType.SCREEN_ON -> { /* the app's own RESUMED re-opens */ }
            }
        }
        // Still on screen at query time.
        closeAll(nowMs)
        return totals
    }

    /**
     * v1.1.4 — one material source disagreement between the event-paired
     * total and the aggregate-bucket total for a package, with the value the
     * pipeline actually kept and why (plain English, no emoji).
     */
    internal data class BucketDiscrepancy(
        val packageName: String,
        val pairedMs: Long,
        val bucketMs: Long,
        val chosenMs: Long,
        val note: String
    )

    /** Pure result of [reconcileUsage]: per-app millis + every material
     * disagreement found while reconciling. */
    internal data class ReconcileResult(
        val perAppMillis: Map<String, Long>,
        val discrepancies: List<BucketDiscrepancy>
    )

    /**
     * A source disagreement bigger than BOTH 5 minutes absolute and 15% of
     * the larger side is material — smaller gaps are ordinary event-log
     * sparsity / bucket flush lag and are not worth a hub entry.
     */
    internal const val MATERIAL_DISCREPANCY_MS = 5 * 60_000L
    internal const val MATERIAL_DISCREPANCY_RATIO = 0.15

    /**
     * v1.1.4 — pure cross-validation of the event-paired totals against the
     * aggregate bucket totals for the same local day.
     *
     * Why it exists: per the UsageStatsManager docs, queryUsageStats "may
     * expand the time range to the nearest whole interval period" — a bucket
     * intersecting [dayStartMs, nowMs] can hold usage from before local
     * midnight. Taking max() against such buckets (what the old floor did on
     * the aggregate path) leaks yesterday's minutes into today. So:
     *
     * - Buckets whose interval started before [dayStartMs] are DROPPED
     *   (their content cannot be subdivided into today vs yesterday).
     * - Remaining buckets act as a per-package FLOOR for genuinely sparse
     *   event logs (OEMs that drop ACTIVITY_* events): chosen = max(paired,
     *   bucket). When the events are the larger side the bucket is just
     *   lagging behind (UsageStats persists periodically), and the events
     *   win — exactly what Digital Wellbeing does.
     * - Every per-package value is clamped to [0, nowMs - dayStartMs]: one
     *   app cannot be in the foreground longer than the day has lasted.
     * - Any material disagreement (|bucket - paired| above both thresholds)
     *   is returned as a [BucketDiscrepancy] so the caller can surface it in
     *   the Guard Hub diagnostics stream.
     *
     * Unit-tested.
     */
    internal fun reconcileUsage(
        paired: Map<String, Long>,
        buckets: List<DailyBucketSample>,
        dayStartMs: Long,
        nowMs: Long
    ): ReconcileResult {
        val dayLengthMs = (nowMs - dayStartMs).coerceAtLeast(0L)
        val kept = mutableMapOf<String, Long>()
        val boundaryIgnored = mutableSetOf<String>()
        for (b in buckets) {
            if (b.totalTimeInForegroundMs <= 0L) continue
            if (b.bucketStartMs < dayStartMs) {
                boundaryIgnored.add(b.packageName)
                continue
            }
            kept[b.packageName] = (kept[b.packageName] ?: 0L) + b.totalTimeInForegroundMs
        }
        val out = mutableMapOf<String, Long>()
        val discrepancies = mutableListOf<BucketDiscrepancy>()
        for (pkg in paired.keys + kept.keys) {
            val p = paired[pkg] ?: 0L
            val b = kept[pkg] ?: 0L
            val chosen = maxOf(p, b).coerceIn(0L, dayLengthMs)
            out[pkg] = chosen
            if (p > 0L || b > 0L) {
                val diff = kotlin.math.abs(b - p)
                val materialAt =
                    maxOf(MATERIAL_DISCREPANCY_MS, (MATERIAL_DISCREPANCY_RATIO * maxOf(b, p)).toLong())
                if (diff > materialAt) {
                    val note = when {
                        chosen < p ->
                            "clamped to day length"
                        b == 0L && pkg in boundaryIgnored ->
                            "boundary bucket dropped; kept events"
                        b > p ->
                            "bucket above events; kept bucket (sparse event log?)"
                        else ->
                            "events above bucket; kept events (bucket not flushed?)"
                    }
                    discrepancies.add(BucketDiscrepancy(pkg, p, b, chosen, note))
                }
            }
        }
        return ReconcileResult(out, discrepancies)
    }

    /**
     * v1.3.4 — pure merge of the daily-interval fallback stats.
     *
     * Kept for its unit-tested contract (max-per-package with the
     * before-midnight bucket guard); the production query path now goes
     * through [reconcileUsage], which adds the aggregate-source buckets,
     * the sanity clamp, and discrepancy reporting.
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

    /**
     * v1.1.3 (FIX 2) — read-only freshness accessor for the Block hub's
     * "Screen time" readiness card: when the usage numbers were last computed
     * (null when no snapshot has been computed this app-open). Never triggers
     * a query and never requires the permission — callers combine it with
     * [hasUsagePermission] to decide what to show.
     */
    fun lastQueriedAtMs(): Long? {
        synchronized(cacheLock) { return cached?.queriedAtMs }
    }
}
