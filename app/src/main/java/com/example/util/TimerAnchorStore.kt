package com.example.util

import android.content.Context
import android.os.SystemClock

/**
 * v1.3.2 (owner order 2026-10-17, items 10+11) — SINGLE source of truth for
 * focus-timer remaining time.
 *
 * Problem: the timer notification (FocusTimerService) and the floating timer
 * pill (FloatingTimerPillManager) each kept their own clock. The pill ran an
 * independent decrement counter (delay(1000) + remaining -= 1) that stalls
 * under Doze/screen-lock, so after unlock the pill and the notification
 * showed DIFFERENT times (tested offline by the owner).
 *
 * Fix: both consumers compute remaining time from ONE persisted anchor —
 * the session end time anchored on SystemClock.elapsedRealtime() (monotonic,
 * immune to Doze stalls and wall-clock jumps), plus the existing wall-clock
 * end timestamp (manual_timer_end_time_ms) for process-restore across reboot
 * (elapsedRealtime resets at boot, so a reboot is detected and the wall
 * clock is used as fallback).
 *
 * Anchors live in the same "focusly_local_data" SharedPreferences file the
 * service already uses for restore, written at the same choke points:
 *   - arm(): called from FocusTimerService.persistTimerPrefs()
 *   - disarm(): called from FocusTimerService.clearTimerPrefs()
 * Reads always gate on the existing is_manual_timer_running /
 * manual_timer_end_time_ms flags, so a stale anchor can never resurrect a
 * dead session (pause/discard paths clear those flags).
 *
 * Offline-safe: pure clock math, zero network.
 */
object TimerAnchorStore {

    private const val PREFS_LOCAL = "focusly_local_data"
    private const val PREFS_TIMER_STATE = "focusly_timer_state"
    private const val KEY_MANUAL_RUNNING = "is_manual_timer_running"
    private const val KEY_TIMER_STATE_RUNNING = "is_timer_running"
    private const val KEY_MANUAL_END_WALL = "manual_timer_end_time_ms"

    // v1.3.2 anchors (new keys; legacy sessions simply lack them and fall
    // back to the wall-clock end time).
    private const val KEY_MANUAL_END_ELAPSED = "manual_timer_end_elapsed_ms"
    private const val KEY_ANCHOR_ELAPSED_AT_WRITE = "manual_timer_anchor_elapsed_at_write_ms"

    /**
     * Canonical running-timer notification id. Item 10: EXACTLY ONE
     * notification exists while a focus-timer session runs — the timer
     * service's foreground notification. Must stay in sync with
     * FocusTimerService's private NOTIFICATION_ID (covered by unit test).
     */
    const val RUNNING_TIMER_NOTIFICATION_ID = 7100

    /**
     * Persist both anchors for a session ending at [endTimeWallMs] (a
     * System.currentTimeMillis() value, e.g. the EXTRA_END_TIME_MS the
     * service was started with).
     */
    fun arm(context: Context, endTimeWallMs: Long) {
        try {
            val nowElapsed = SystemClock.elapsedRealtime()
            val endElapsed = endTimeWallMs - System.currentTimeMillis() + nowElapsed
            context.getSharedPreferences(PREFS_LOCAL, Context.MODE_PRIVATE).edit()
                .putLong(KEY_MANUAL_END_ELAPSED, endElapsed)
                .putLong(KEY_ANCHOR_ELAPSED_AT_WRITE, nowElapsed)
                .apply()
        } catch (_: Exception) {
        }
    }

    /** Clear the anchors. Safe to call any time. */
    fun disarm(context: Context) {
        try {
            context.getSharedPreferences(PREFS_LOCAL, Context.MODE_PRIVATE).edit()
                .remove(KEY_MANUAL_END_ELAPSED)
                .remove(KEY_ANCHOR_ELAPSED_AT_WRITE)
                .apply()
        } catch (_: Exception) {
        }
    }

    /**
     * True when the persisted flags describe a genuinely running manual
     * timer: the running flag is set AND the single persisted anchor pair
     * still has time left.
     *
     * v1.1.4-item5: the liveness decision now goes through the same anchor
     * math as [getRemainingSeconds] instead of the raw wall-clock end time.
     * The elapsed-realtime anchor decides whenever the device has not
     * rebooted since arming, so a wall-clock jump can neither resurrect an
     * expired session (clock set back) nor kill a live one early (clock set
     * forward). After a reboot (elapsedRealtime reset) or for legacy
     * sessions without anchors, the wall-clock end time decides, exactly
     * like before.
     */
    fun isManualTimerRunning(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_LOCAL, Context.MODE_PRIVATE)
            val tPrefs = context.getSharedPreferences(PREFS_TIMER_STATE, Context.MODE_PRIVATE)
            val flagged = prefs.getBoolean(KEY_MANUAL_RUNNING, false) ||
                    tPrefs.getBoolean(KEY_TIMER_STATE_RUNNING, false)
            if (!flagged) return false
            val endWall = prefs.getLong(KEY_MANUAL_END_WALL, 0L)
            val endElapsed = prefs.getLong(KEY_MANUAL_END_ELAPSED, 0L)
            val elapsedAtWrite = prefs.getLong(KEY_ANCHOR_ELAPSED_AT_WRITE, 0L)
            computeRemainingSeconds(
                endElapsed, elapsedAtWrite, endWall,
                SystemClock.elapsedRealtime(), System.currentTimeMillis()
            ) > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * v1.1.4-item5: the persisted wall-clock session end, 0L when none is
     * armed. Restore paths use this (together with the elapsed anchor) so
     * every one of them re-arms the SAME session end — a restore can never
     * invent a new full-duration end time.
     */
    fun getEndWallMs(context: Context): Long {
        return try {
            context.getSharedPreferences(PREFS_LOCAL, Context.MODE_PRIVATE)
                .getLong(KEY_MANUAL_END_WALL, 0L)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * v1.1.4-item5: call from the BOOT_COMPLETED receiver. Reports the
     * persisted timer state after a reboot and logs it to Guard Hub so a
     * post-reboot anomaly is traceable. Never completes, records, or
     * re-arms anything: an expired session's flags are left intact so the
     * normal app-open restore path can still complete it properly (record
     * the session once); a live session keeps its wall-clock end so the
     * same restore path continues it. Returns "live", "expired", or "none".
     */
    fun validateOnBoot(context: Context): String {
        val state = try {
            val prefs = context.getSharedPreferences(PREFS_LOCAL, Context.MODE_PRIVATE)
            val flagged = prefs.getBoolean(KEY_MANUAL_RUNNING, false)
            if (!flagged) {
                "none"
            } else if (isManualTimerRunning(context)) {
                "live"
            } else {
                "expired"
            }
        } catch (_: Exception) {
            "none"
        }
        try {
            AiStudyGuardManager.logGuardActivity(
                context, "Focus Timer", "boot-validate", state,
                "reboot seen; restore continues the same session on next app open"
            )
        } catch (_: Exception) { }
        return state
    }

    /**
     * Remaining whole seconds of the running timer, computed from the single
     * persisted anchor. Returns 0 when no timer is running.
     *
     * The [nowElapsedMs]/[nowWallMs] parameters default to the real clocks;
     * they exist so tests can simulate lock gaps without waiting.
     */
    fun getRemainingSeconds(
        context: Context,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        nowWallMs: Long = System.currentTimeMillis()
    ): Int {
        return try {
            if (!isManualTimerRunning(context)) return 0
            val prefs = context.getSharedPreferences(PREFS_LOCAL, Context.MODE_PRIVATE)
            val endWall = prefs.getLong(KEY_MANUAL_END_WALL, 0L)
            val endElapsed = prefs.getLong(KEY_MANUAL_END_ELAPSED, 0L)
            val elapsedAtWrite = prefs.getLong(KEY_ANCHOR_ELAPSED_AT_WRITE, 0L)
            computeRemainingSeconds(endElapsed, elapsedAtWrite, endWall, nowElapsedMs, nowWallMs)
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Pure remaining-time math (unit-testable, no Android prefs needed).
     *
     * Prefers the elapsed-realtime anchor (immune to Doze stalls and
     * wall-clock jumps). Falls back to the wall-clock end time when the
     * device rebooted since the anchor was written (elapsedRealtime resets
     * at boot, so nowElapsedMs < elapsedAtWriteMs proves a reboot) or when
     * no elapsed anchor exists (sessions armed before v1.3.2).
     */
    fun computeRemainingSeconds(
        endElapsedMs: Long,
        elapsedAtWriteMs: Long,
        endWallMs: Long,
        nowElapsedMs: Long,
        nowWallMs: Long
    ): Int {
        val rebooted = elapsedAtWriteMs > 0L && nowElapsedMs < elapsedAtWriteMs
        val remainingMs = if (!rebooted && endElapsedMs > 0L) {
            endElapsedMs - nowElapsedMs
        } else {
            endWallMs - nowWallMs
        }
        return (remainingMs / 1000L).toInt().coerceAtLeast(0)
    }
}
