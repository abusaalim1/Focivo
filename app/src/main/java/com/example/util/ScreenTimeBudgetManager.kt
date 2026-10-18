package com.example.util

import android.content.Context
import android.util.Log

/**
 * v1.3.3 (item 8b) — the GLOBAL daily screen-time budget.
 *
 * When total device usage today exceeds the configured budget, ALL
 * non-essential apps are blocked for a 3-hour "screen-time break":
 * opening any of them shows the premium block overlay explaining the break
 * with the live remaining time. Essential apps (phone, camera, contacts,
 * maps, YouTube, Google app, Chrome, clock, settings, SMS, emergency dialer,
 * Focivo itself) are NEVER locked and the user cannot remove them.
 *
 * Break state persists in SharedPrefs across reboot; every read validates
 * the expiry — an expired break is dropped and never resurrected. After a
 * break ends, if usage still exceeds the budget that day, the break
 * re-triggers at most ONCE more (no infinite nag — max 2 breaks / day).
 *
 * Usage totals come from [DailyUsagePipeline], the single source of truth.
 */
object ScreenTimeBudgetManager {

    private const val TAG = "ScreenTimeBudgetManager"
    private const val PREFS = "screen_time_budget_prefs"

    private const val KEY_BUDGET_MINUTES = "global_budget_minutes"
    private const val KEY_BREAK_ENDS_AT_MS = "break_ends_at_ms"
    private const val KEY_BREAK_DAY = "break_day"
    private const val KEY_TRIGGERS_DAY = "break_triggers_day"
    private const val KEY_TRIGGERS_COUNT = "break_triggers_count"

    /** One screen-time break locks non-essentials for 3 hours. */
    const val BREAK_DURATION_MS = 3L * 60 * 60 * 1000

    /** Max breaks per day: the first trigger + at most one re-trigger. */
    const val MAX_BREAKS_PER_DAY = 2

    /**
     * Break-specific essential packages, ON TOP of
     * [EssentialAppsGuard.isEssentialApp] (which already covers phone/dialer,
     * camera, contacts, clock, settings, SMS, emergency, Focivo itself and
     * the Google app). The task's essentials list additionally demands Maps,
     * YouTube and Chrome never be locked.
     */
    val BREAK_ESSENTIAL_PACKAGES: Set<String> = setOf(
        // Maps
        "com.google.android.apps.maps",
        // Chrome
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.chromium.chrome",
        // YouTube (+ Music — study whitelisted elsewhere, never break-locked)
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music"
    )

    sealed interface BreakDecision {
        data object Allow : BreakDecision
        data class Block(val remainingMs: Long) : BreakDecision
    }

    /** Pure evaluation result — the guard persists + logs on StartBreak. */
    data class BreakEvaluation(
        val decision: BreakDecision,
        val startNewBreak: Boolean
    )

    // ------------------------------------------------------------------
    // Budget store
    // ------------------------------------------------------------------

    /** 0 (or negative) = global budget is OFF. */
    fun getGlobalBudgetMinutes(context: Context): Int {
        return prefs(context).getInt(KEY_BUDGET_MINUTES, 0)
    }

    fun setGlobalBudgetMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_BUDGET_MINUTES, minutes.coerceAtLeast(0)).apply()
        Log.d(TAG, "global budget set to ${minutes.coerceAtLeast(0)}m")
    }

    // ------------------------------------------------------------------
    // Essentials predicate
    // ------------------------------------------------------------------

    /**
     * Pure, unit-testable: extra essentials beyond [EssentialAppsGuard].
     * Matches the named packages + conservative name heuristics so OEM
     * variants (e.g. a vendor Maps/Chrome build) stay unlocked too.
     */
    fun isExtraEssentialPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase().trim()
        if (BREAK_ESSENTIAL_PACKAGES.contains(lower)) return true
        return lower.contains("maps") ||
            lower.contains("navigation") ||
            lower == "chrome" ||
            lower.contains(".chrome") ||
            lower.contains("chromium") ||
            lower.contains("youtube")
    }

    /**
     * True when the package must NEVER be break-locked. The user cannot
     * remove essentials: the per-app budget UI filters them out and this
     * predicate short-circuits the guard before any lock.
     */
    fun isEssentialForBreak(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return EssentialAppsGuard.isEssentialApp(context, packageName) ||
            isExtraEssentialPackage(packageName)
    }

    // ------------------------------------------------------------------
    // Break state (persisted, expiry-validated)
    // ------------------------------------------------------------------

    private data class StoredBreak(
        val endsAtMs: Long,
        val dayKey: String,
        val triggersDayKey: String?,
        val triggersCount: Int
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Reads the stored break; an expired (or day-stale) break is dropped and
     * cleared from prefs — it is never resurrected, including across reboot.
     */
    private fun readStoredBreak(context: Context, nowMs: Long): StoredBreak? {
        val p = prefs(context)
        val endsAt = p.getLong(KEY_BREAK_ENDS_AT_MS, 0L)
        val dayKey = p.getString(KEY_BREAK_DAY, null)
        if (endsAt <= 0L || dayKey.isNullOrBlank()) return null
        val todayKey = DailyUsagePipeline.dayKeyFor(nowMs)
        if (endsAt <= nowMs || dayKey != todayKey) {
            // Expired or from a previous day: drop it permanently.
            p.edit()
                .remove(KEY_BREAK_ENDS_AT_MS)
                .remove(KEY_BREAK_DAY)
                .apply()
            return null
        }
        return StoredBreak(
            endsAtMs = endsAt,
            dayKey = dayKey,
            triggersDayKey = p.getString(KEY_TRIGGERS_DAY, null),
            triggersCount = p.getInt(KEY_TRIGGERS_COUNT, 0)
        )
    }

    private fun persistBreakStart(context: Context, nowMs: Long, todayKey: String) {
        val p = prefs(context)
        val triggersDay = p.getString(KEY_TRIGGERS_DAY, null)
        val triggers = if (triggersDay == todayKey) p.getInt(KEY_TRIGGERS_COUNT, 0) else 0
        p.edit()
            .putLong(KEY_BREAK_ENDS_AT_MS, nowMs + BREAK_DURATION_MS)
            .putString(KEY_BREAK_DAY, todayKey)
            .putString(KEY_TRIGGERS_DAY, todayKey)
            .putInt(KEY_TRIGGERS_COUNT, triggers + 1)
            .apply()
    }

    // ------------------------------------------------------------------
    // Decision
    // ------------------------------------------------------------------

    /**
     * Pure break decision. Unit-tested.
     *
     * @param storedEndsAtMs active break's end time, or null when no live break
     * @param triggersToday how many breaks already triggered today
     */
    fun evaluateBreak(
        nowMs: Long,
        budgetMinutes: Int,
        totalUsageMs: Long,
        isEssential: Boolean,
        storedEndsAtMs: Long?,
        triggersToday: Int
    ): BreakEvaluation {
        if (budgetMinutes <= 0) return BreakEvaluation(BreakDecision.Allow, false)
        if (isEssential) return BreakEvaluation(BreakDecision.Allow, false)

        // Live break -> keep blocking with the remaining time.
        if (storedEndsAtMs != null && storedEndsAtMs > nowMs) {
            return BreakEvaluation(BreakDecision.Block(storedEndsAtMs - nowMs), false)
        }

        // No live break: trigger only while usage exceeds the budget, and at
        // most MAX_BREAKS_PER_DAY times per day (no infinite nag).
        val budgetMs = budgetMinutes * 60_000L
        if (totalUsageMs >= budgetMs && triggersToday < MAX_BREAKS_PER_DAY) {
            return BreakEvaluation(BreakDecision.Block(BREAK_DURATION_MS), true)
        }
        return BreakEvaluation(BreakDecision.Allow, false)
    }

    /**
     * Guard entry point: call on every app-open check. Returns [BreakDecision.Block]
     * when the package must be intercepted for the screen-time break.
     * Persists + Guard-Hub-logs break starts (transition-only, never spammed).
     */
    fun checkOnAppOpen(context: Context, packageName: String?): BreakDecision {
        if (packageName.isNullOrBlank()) return BreakDecision.Allow
        // Fast path: essentials never query usage and never lock.
        if (isEssentialForBreak(context, packageName)) return BreakDecision.Allow

        val budgetMinutes = getGlobalBudgetMinutes(context)
        if (budgetMinutes <= 0) return BreakDecision.Allow

        val now = System.currentTimeMillis()
        val todayKey = DailyUsagePipeline.dayKeyFor(now)
        val stored = readStoredBreak(context, now)
        val triggersToday =
            if (stored?.triggersDayKey == todayKey) stored.triggersCount
            else prefs(context).let {
                val d = it.getString(KEY_TRIGGERS_DAY, null)
                if (d == todayKey) it.getInt(KEY_TRIGGERS_COUNT, 0) else 0
            }

        val totalUsageMs = DailyUsagePipeline.deviceTotalMillis(context)
        val eval = evaluateBreak(
            nowMs = now,
            budgetMinutes = budgetMinutes,
            totalUsageMs = totalUsageMs,
            isEssential = false,
            storedEndsAtMs = stored?.endsAtMs,
            triggersToday = triggersToday
        )

        if (eval.startNewBreak) {
            persistBreakStart(context, now, todayKey)
            try {
                AiStudyGuardManager.logGuardActivity(
                    context = context,
                    app = packageName,
                    stage = "SCREEN_TIME_BUDGET",
                    verdict = "BREAK_START",
                    reason = "daily ${budgetLabel(budgetMinutes)} budget hit; 3h break #${triggersToday + 1}"
                )
            } catch (_: Exception) { }
        }
        return eval.decision
    }

    /** True while a break is live (expiry-validated). */
    fun isBreakActive(context: Context): Boolean =
        readStoredBreak(context, System.currentTimeMillis()) != null

    /** Remaining break millis, 0 when no live break. */
    fun remainingBreakMs(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val stored = readStoredBreak(context, nowMs) ?: return 0L
        return (stored.endsAtMs - nowMs).coerceAtLeast(0L)
    }

    /** "2h 13m" / "41m" — English-only duration copy for the overlay. */
    fun formatRemaining(remainingMs: Long): String {
        val totalMins = (remainingMs / 60_000L).coerceAtLeast(0L)
        val h = totalMins / 60
        val m = totalMins % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    /** "4h" / "90m" budget label. */
    fun budgetLabel(minutes: Int): String {
        return if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60}h"
        else if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m"
        else "${minutes}m"
    }

    /** Overlay reason copy for a break intercept (remaining time live). */
    fun breakReason(context: Context, budgetMinutes: Int): String {
        val remaining = formatRemaining(remainingBreakMs(context))
        return "Daily screen-time limit reached (${budgetLabel(budgetMinutes)}). " +
            "Taking a 3-hour break — apps reopen in $remaining. " +
            "Essential apps stay available."
    }
}
