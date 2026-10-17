package com.example.util

/**
 * v1.3.0 (owner order 2026-10-17) — strict-mode Pause gate.
 *
 * Stop and Restart were already blocked under Strict Mode; Pause joins them:
 * while Strict Mode is on and the timer is running, a Pause tap must be a
 * NO-OP — the timer keeps running and the caller shows the strict
 * explanation dialog (StrictLockDialog, pause variant) instead.
 *
 * Pure decision unit (unit-testable): [decide] never touches the timer.
 * FocuslyViewModel.attemptPauseTimer() applies the decision — PAUSED runs
 * the normal pauseTimer(), BLOCKED_STRICT returns without touching it.
 * Internal callers (discard, skip, completion) keep using pauseTimer()
 * directly; only Pause TAPS are gated.
 */
object StrictPauseGate {

    enum class Result {
        /** Normal path: the timer was (or may be) paused. */
        PAUSED,

        /** Strict lock-in: the tap is a no-op, timer keeps running. */
        BLOCKED_STRICT
    }

    fun decide(strictEnabled: Boolean, timerRunning: Boolean): Result =
        if (strictEnabled && timerRunning) Result.BLOCKED_STRICT else Result.PAUSED
}
