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
 * v1.1.4c (C4) — SECOND owner device mismatch vs Digital Wellbeing. Root
 * causes found this round:
 *  1. The events query started AT local midnight. A session already in
 *     progress at midnight (opened 23:50, closed 00:30) has its RESUMED
 *     before the window, so the stream held an unmatched PAUSED and the
 *     pairing dropped the WHOLE session — including its 30 post-midnight
 *     minutes. The query now starts 48h before midnight and the pairing
 *     (already clip-safe) keeps only the [midnight, now] portion.
 *  2. The aggregate source (queryAndAggregateUsageStats over the exact
 *     [midnight, now] window) was boundary-dropped whenever the returned
 *     bucket's firstTimeStamp preceded midnight — but for the aggregate
 *     API that timestamp describes the source bucket, not the aggregated
 *     window, so the drop silently discarded the most window-accurate
 *     reference on devices where it fires (the owner's vivo). Aggregate
 *     samples are now window-trusted (still sanity-clamped to the day
 *     length); only the DAILY-interval buckets keep the boundary drop,
 *     because a daily bucket's total genuinely spans its whole bucket.
 *  3. ACTIVITY_STOPPED and KEYGUARD_SHOWN were not mapped: OEM builds
 *     (Funtouch among them) can close an activity with STOPPED and no
 *     PAUSED, leaving the paired interval open until "now" (over-count),
 *     and the keyguard is a hard foreground boundary like screen-off.
 * The per-app pick rule is documented on [reconcileUsage]; every material
 * disagreement is logged to the Guard Hub (USAGE_RECONCILE) with all three
 * raw values and the chosen source, so the owner can compare per-app
 * against Digital Wellbeing on-device.
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

    /**
     * v1.1.4c (C4) — how far BEFORE local midnight the events query reaches
     * (48h). Only intervals' post-midnight portions survive the pairing's
     * [dayStartMs, nowMs] clip; the lookback exists purely so a session
     * that was already open at midnight still has its RESUMED in the
     * stream (an unmatched PAUSED would otherwise drop the whole session).
     */
    const val EVENT_LOOKBACK_MS = 48L * 3600 * 1000

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
            // v1.1.4c (C4): the query window starts EVENT_LOOKBACK_MS BEFORE
            // midnight. A session already running at midnight has its
            // RESUMED before midnight; querying from midnight returned only
            // its (unmatched, ignored) PAUSED and dropped the session's
            // whole post-midnight portion. sumForegroundTime clips every
            // interval to [dayStartMs, nowMs], so the lookback only ever
            // SUPPLIES the missing open — it can never count yesterday.
            // ACTIVITY_STOPPED also closes an interval: some OEM builds
            // (Funtouch included) emit RESUMED/STOPPED pairs with no
            // PAUSED; an unclosed interval otherwise runs open until now.
            // KEYGUARD_SHOWN is a hard foreground boundary like screen-off.
            var eventCount = 0
            try {
                val queryStartMs = dayStartMs - EVENT_LOOKBACK_MS
                // UsageEventsQuery.Builder is API 34+; the deprecated
                // queryEvents(begin, end) covers API 21-33 (minSdk is 24).
                val usageEvents = if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ) {
                    val query = UsageEventsQuery.Builder(queryStartMs, nowMs)
                        .setEventTypes(
                            UsageEvents.Event.ACTIVITY_RESUMED,
                            UsageEvents.Event.ACTIVITY_PAUSED,
                            UsageEvents.Event.ACTIVITY_STOPPED,
                            UsageEvents.Event.DEVICE_SHUTDOWN,
                            UsageEvents.Event.DEVICE_STARTUP,
                            UsageEvents.Event.SCREEN_INTERACTIVE,
                            UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                            UsageEvents.Event.KEYGUARD_SHOWN,
                            UsageEvents.Event.KEYGUARD_HIDDEN
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
                        val type = when (ev.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED -> AppEventType.RESUMED
                            UsageEvents.Event.ACTIVITY_PAUSED -> AppEventType.PAUSED
                            // v1.1.4c: STOPPED = the activity fully left
                            // the foreground; closes like PAUSED (a STOPPED
                            // after a PAUSED is a harmless no-op close).
                            UsageEvents.Event.ACTIVITY_STOPPED -> AppEventType.PAUSED
                            UsageEvents.Event.DEVICE_SHUTDOWN -> AppEventType.SHUTDOWN
                            UsageEvents.Event.DEVICE_STARTUP -> AppEventType.STARTUP
                            UsageEvents.Event.SCREEN_INTERACTIVE -> AppEventType.SCREEN_ON
                            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> AppEventType.SCREEN_OFF
                            // v1.1.4c: keyguard up = nothing is foreground.
                            UsageEvents.Event.KEYGUARD_SHOWN -> AppEventType.SCREEN_OFF
                            UsageEvents.Event.KEYGUARD_HIDDEN -> AppEventType.SCREEN_ON
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
                // cross-validation. v1.1.4c (C4): the two sources are kept
                // SEPARATE because their boundary semantics differ (see
                // reconcileUsage): daily-interval buckets are
                // boundary-checked (a daily bucket's total spans its whole
                // bucket, so a pre-midnight bucket start means the total
                // mixes yesterday in), while the aggregate query already
                // aggregated the exact [dayStartMs, nowMs] window — its
                // firstTimeStamp describes the source bucket, NOT the
                // window, and must not drop the sample.
                val dailySamples = mutableListOf<DailyBucketSample>()
                val aggregateSamples = mutableListOf<DailyBucketSample>()

                // 1) Daily-interval buckets (expose firstTimeStamp).
                try {
                    val statsList = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        dayStartMs,
                        nowMs
                    )
                    if (!statsList.isNullOrEmpty()) {
                        for (stats in statsList) {
                            dailySamples.add(
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

                // 2) Aggregate stats over the exact window — the system's
                // own record-time pairing for [dayStartMs, nowMs].
                try {
                    val aggregated =
                        usageStatsManager.queryAndAggregateUsageStats(dayStartMs, nowMs)
                    if (!aggregated.isNullOrEmpty()) {
                        for ((pkg, stats) in aggregated) {
                            aggregateSamples.add(
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

                val reconcile = reconcileUsage(
                    paired, dailySamples, aggregateSamples, dayStartMs, nowMs
                )
                merged.putAll(reconcile.perAppMillis)
                logReconcileDiagnostics(
                    context, dayStartMs, reconcile.discrepancies,
                    eventCount, dailySamples.size + aggregateSamples.size
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
     * throttled to one batch per 30 minutes per day so the 60s snapshot TTL
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
                    .sortedByDescending {
                        kotlin.math.abs(maxOf(it.dailyMs, it.aggregateMs) - it.pairedMs)
                    }
                    .take(5)
                    .map { d ->
                        // v1.1.4c (C4): all three raw values + the chosen
                        // source, so the owner can compare per-app against
                        // Digital Wellbeing from the Guard Hub alone.
                        Triple(
                            "USAGE_RECONCILE",
                            "MISMATCH",
                            "${d.packageName}: events ${d.pairedMs / 60000}m / " +
                                "daily ${d.dailyMs / 60000}m / " +
                                "aggregate ${d.aggregateMs / 60000}m -> " +
                                "kept ${d.chosenMs / 60000}m (${d.chosenSource}); ${d.note}"
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
     * v1.1.4 — one material source disagreement for a package: the three
     * raw per-source values, the value the pipeline actually kept, which
     * source it came from, and why (plain English, no emoji). v1.1.4c (C4)
     * split the single bucketMs into the daily-interval and aggregate
     * sources so the Guard Hub entry shows all three raw values.
     */
    internal data class BucketDiscrepancy(
        val packageName: String,
        val pairedMs: Long,
        val dailyMs: Long,
        val aggregateMs: Long,
        val chosenMs: Long,
        /** "events", "daily bucket" or "aggregate". */
        val chosenSource: String,
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
     * bucket totals for the same local day. v1.1.4c (C4) — the two bucket
     * sources are reconciled SEPARATELY because their time semantics differ.
     *
     * THE PER-APP PICK RULE (documented; every choice is recorded in the
     * returned [BucketDiscrepancy]s when the sources materially disagree):
     *  1. EVENTS (paired) are the primary source — they are clipped to the
     *     local day by construction and see split-screen/multi-window use.
     *  2. DAILY buckets (queryUsageStats INTERVAL_DAILY): a daily bucket's
     *     total spans its WHOLE bucket, and per the UsageStatsManager docs
     *     the query "may expand the time range to the nearest whole interval
     *     period" — so a bucket whose interval started before [dayStartMs]
     *     is DROPPED (its content cannot be subdivided into today vs
     *     yesterday; taking it is exactly how yesterday leaked into today
     *     in 1.3.4). Surviving daily buckets are summed per package.
     *  3. AGGREGATE buckets (queryAndAggregateUsageStats over the exact
     *     [dayStartMs, nowMs] window): the system aggregated ONLY that
     *     window, using its record-time pairing (which sees context a
     *     re-pairing cannot — pre-midnight opens, OEM-dropped PAUSED
     *     events). The returned firstTimeStamp describes the source
     *     bucket, NOT the window, so these samples are NEVER
     *     boundary-dropped; they are only sanity-clamped like everything
     *     else. (v1.1.4 boundary-dropped them, silently discarding this
     *     source on devices where the timestamp precedes midnight.)
     *  4. CHOSEN = max(events, daily, aggregate), clamped to
     *     [0, nowMs - dayStartMs] (one app cannot be foreground longer
     *     than the day has lasted). When a bucket source beats the events
     *     by a wide margin, the bucket wins because the event log is
     *     genuinely sparse on that device; when the events are the larger
     *     side, the buckets are simply lagging (UsageStats persists
     *     periodically) and the events win.
     *
     * Unit-tested.
     */
    internal fun reconcileUsage(
        paired: Map<String, Long>,
        dailyBuckets: List<DailyBucketSample>,
        aggregateBuckets: List<DailyBucketSample>,
        dayStartMs: Long,
        nowMs: Long
    ): ReconcileResult {
        val dayLengthMs = (nowMs - dayStartMs).coerceAtLeast(0L)

        // Daily source: boundary-checked (rule 2).
        val dailyKept = mutableMapOf<String, Long>()
        val dailyBoundaryDropped = mutableSetOf<String>()
        for (b in dailyBuckets) {
            if (b.totalTimeInForegroundMs <= 0L) continue
            if (b.bucketStartMs < dayStartMs) {
                dailyBoundaryDropped.add(b.packageName)
                continue
            }
            dailyKept[b.packageName] =
                (dailyKept[b.packageName] ?: 0L) + b.totalTimeInForegroundMs
        }

        // Aggregate source: window-scoped, never boundary-dropped (rule 3).
        val aggregateKept = mutableMapOf<String, Long>()
        for (b in aggregateBuckets) {
            if (b.totalTimeInForegroundMs <= 0L) continue
            aggregateKept[b.packageName] =
                (aggregateKept[b.packageName] ?: 0L) + b.totalTimeInForegroundMs
        }

        val out = mutableMapOf<String, Long>()
        val discrepancies = mutableListOf<BucketDiscrepancy>()
        for (pkg in paired.keys + dailyKept.keys + aggregateKept.keys) {
            val p = paired[pkg] ?: 0L
            val d = dailyKept[pkg] ?: 0L
            val a = aggregateKept[pkg] ?: 0L
            val bestBucket = maxOf(d, a)
            val chosen = maxOf(p, bestBucket).coerceIn(0L, dayLengthMs)
            out[pkg] = chosen
            if (p > 0L || bestBucket > 0L) {
                val diff = kotlin.math.abs(bestBucket - p)
                val materialAt = maxOf(
                    MATERIAL_DISCREPANCY_MS,
                    (MATERIAL_DISCREPANCY_RATIO * maxOf(bestBucket, p)).toLong()
                )
                if (diff > materialAt || chosen < maxOf(p, bestBucket)) {
                    val chosenSource = when {
                        chosen < maxOf(p, bestBucket) -> "events (day-clamped)"
                        p >= bestBucket -> "events"
                        d >= a -> "daily bucket"
                        else -> "aggregate"
                    }
                    val note = when {
                        chosen < maxOf(p, bestBucket) ->
                            "clamped to day length"
                        bestBucket == 0L && pkg in dailyBoundaryDropped ->
                            "daily boundary bucket dropped; aggregate empty; kept events"
                        bestBucket > p && a >= d && a > 0L ->
                            "aggregate above events; kept aggregate (system window pairing; sparse event log?)"
                        bestBucket > p ->
                            "daily bucket above events; kept bucket (sparse event log?)"
                        else ->
                            "events above buckets; kept events (buckets not flushed?)"
                    }
                    discrepancies.add(
                        BucketDiscrepancy(pkg, p, d, a, chosen, chosenSource, note)
                    )
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
