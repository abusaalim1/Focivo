package com.example.util

/**
 * v1.1.3 (FIX 2) — "Screen time" readiness: the pure decision logic behind
 * the Block hub's Screen time card.
 *
 * The readiness card reports three things:
 *  1. Usage access (PACKAGE_USAGE_STATS) — granted -> green check, missing ->
 *     one-tap route to the system grant screen.
 *  2. Battery exemption (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) — exempt ->
 *     green check, not exempt -> one-tap system request. Without the
 *     exemption OEM ROMs (vivo especially) starve the usage pipeline and
 *     budget enforcement in the background.
 *  3. Pipeline freshness — when the usage numbers were last computed
 *     ([DailyUsagePipeline.DeviceUsageSnapshot.queriedAtMs]).
 *
 * Everything here is hermetic (no Context, no Android APIs) so it is
 * unit-tested without Robolectric. The composable layer only formats and
 * forwards intents.
 */
object ScreenTimeReadiness {

    /** Point-in-time input for the readiness card. */
    data class ReadinessInput(
        /** PACKAGE_USAGE_STATS granted to Focivo. */
        val usageAccessGranted: Boolean,
        /** Battery-optimization exemption granted to Focivo. */
        val batteryExemptionGranted: Boolean,
        /** [DailyUsagePipeline.DeviceUsageSnapshot.queriedAtMs], null when the
         * pipeline has never computed a snapshot this app-open. */
        val lastComputedAtMs: Long?
    )

    /** True when the screen-time pipeline can actually produce numbers:
     * usage access granted AND the battery exemption held (background
     * computation survives). */
    fun isReady(input: ReadinessInput): Boolean =
        input.usageAccessGranted && input.batteryExemptionGranted

    /**
     * True when the cached usage numbers should be considered stale and the
     * card should nudge a refresh: never computed, or the last computation
     * is older than [STALE_AFTER_MS].
     */
    const val STALE_AFTER_MS = 10 * 60_000L

    fun isStale(lastComputedAtMs: Long?, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lastComputedAtMs == null) return true
        return nowMs - lastComputedAtMs > STALE_AFTER_MS
    }

    /**
     * Human-readable freshness line for the card, English only.
     *
     * - null -> "Never"
     * - future clock skew -> "Just now"
     * - < 1 min -> "Just now"
     * - < 1 h -> "N min ago"
     * - < 24 h -> "N h ago"
     * - else -> "N d ago"
     */
    fun formatLastUpdated(
        lastComputedAtMs: Long?,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        if (lastComputedAtMs == null) return "Never"
        val delta = (nowMs - lastComputedAtMs).coerceAtLeast(0L)
        val mins = delta / 60_000L
        val hours = delta / 3_600_000L
        val days = delta / 86_400_000L
        return when {
            mins < 1L -> "Just now"
            mins < 60L -> "$mins min ago"
            hours < 24L -> "$hours h ago"
            else -> "$days d ago"
        }
    }
}
