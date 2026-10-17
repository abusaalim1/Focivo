package com.example

import com.example.util.StrictPauseGate
import com.example.util.StrictPauseGate.Result.BLOCKED_STRICT
import com.example.util.StrictPauseGate.Result.PAUSED
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.3.0 (owner order 2026-10-17) — strict-mode Pause is forbidden.
 *
 * Verifies the pause gate that FocuslyViewModel.attemptPauseTimer() applies:
 * while Strict Mode is on AND the timer is running, a Pause tap must be a
 * no-op (BLOCKED_STRICT — the caller shows the strict explanation dialog
 * instead of touching the timer). Every other combination keeps the normal
 * pause path (PAUSED). Internal callers (discard, skip, completion) bypass
 * the gate by calling pauseTimer() directly — only taps are gated.
 */
class StrictPauseGateTest {

    @Test
    fun `strict on plus running blocks pause`() {
        assertEquals(
            BLOCKED_STRICT,
            StrictPauseGate.decide(strictEnabled = true, timerRunning = true)
        )
    }

    @Test
    fun `strict on plus idle keeps normal pause path`() {
        assertEquals(
            PAUSED,
            StrictPauseGate.decide(strictEnabled = true, timerRunning = false)
        )
    }

    @Test
    fun `strict off plus running keeps normal pause path`() {
        assertEquals(
            PAUSED,
            StrictPauseGate.decide(strictEnabled = false, timerRunning = true)
        )
    }

    @Test
    fun `strict off plus idle keeps normal pause path`() {
        assertEquals(
            PAUSED,
            StrictPauseGate.decide(strictEnabled = false, timerRunning = false)
        )
    }
}
