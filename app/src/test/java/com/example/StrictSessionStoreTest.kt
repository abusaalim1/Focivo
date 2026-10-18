package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.StrictModeManager
import com.example.util.StrictSessionStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.3.1 hotfix (owner order 2026-10-17, item A2) — durable strict-session
 * state with expiry validation.
 *
 * Rules under test:
 *  - arm() makes the session active (strict+protection default on);
 *  - an expired record NEVER resurrects: isStrictSessionActive() returns
 *    false and purges the record;
 *  - disarm() clears unconditionally.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StrictSessionStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Fresh Robolectric prefs default to strict on + protection on.
        StrictModeManager.setStrictModeEnabled(context, true)
        StrictModeManager.setUninstallProtectionEnabled(context, true)
        StrictSessionStore.disarm(context)
    }

    @Test
    fun `armed session with future end time is active`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        assertTrue(StrictSessionStore.isStrictSessionActive(context))
    }

    @Test
    fun `expired record never resurrects and is purged`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() - 1_000L)
        // Expired end time: must report inactive...
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
        // ...and must STAY inactive on a second read (purged, not revived).
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
    }

    @Test
    fun `disarm clears an armed session`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        assertTrue(StrictSessionStore.isStrictSessionActive(context))
        StrictSessionStore.disarm(context)
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
    }

    @Test
    fun `strict off reports inactive even when armed`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        StrictModeManager.setStrictModeEnabled(context, false)
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
    }

    // v1.1.3 FIX 1 — regression: the armed record must be DESTROYED when
    // Strict is turned off (not just masked by the flag check), or
    // re-enabling Strict later resurrects friction with no session running.
    @Test
    fun `turning strict off disarms the record permanently`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        assertTrue(StrictSessionStore.isStrictSessionActive(context))
        StrictModeManager.setStrictModeEnabled(context, false)
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
        // Re-enable: the dead record must NOT come back to life.
        StrictModeManager.setStrictModeEnabled(context, true)
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
    }

    // v1.1.3 FIX 1 — regression: opting out of uninstall protection clears
    // the armed record too; a later opt-in starts from a clean slate.
    @Test
    fun `opting out of protection disarms the record permanently`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        assertTrue(StrictSessionStore.isStrictSessionActive(context))
        StrictModeManager.setUninstallProtectionEnabled(context, false)
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
        StrictModeManager.setUninstallProtectionEnabled(context, true)
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
    }

    // v1.1.3 FIX 1 — elapsed-realtime expiry boundaries (pure decision,
    // no clock I/O). Same-boot records expire on the monotonic clock:
    // moving the wall clock can never extend or shorten protection.
    @Test
    fun `expiry boundary is exact on the monotonic clock`() {
        // nowElapsed == endElapsed -> expired.
        assertTrue(
            StrictSessionStore.isRecordExpired(
                nowWallMs = 1_000_000L, nowElapsedMs = 500_000L,
                endWallMs = 1_000_000L, endElapsedMs = 500_000L, armElapsedMs = 100_000L
            )
        )
        // One ms before the elapsed end -> still active.
        assertFalse(
            StrictSessionStore.isRecordExpired(
                nowWallMs = 1_000_000L, nowElapsedMs = 499_999L,
                endWallMs = 1_000_000L, endElapsedMs = 500_000L, armElapsedMs = 100_000L
            )
        )
    }

    @Test
    fun `wall clock moved back cannot extend protection`() {
        // Wall clock says "plenty of time left" but the monotonic end passed.
        assertTrue(
            StrictSessionStore.isRecordExpired(
                nowWallMs = 500_000L, nowElapsedMs = 600_000L,
                endWallMs = 9_000_000L, endElapsedMs = 500_000L, armElapsedMs = 100_000L
            )
        )
    }

    @Test
    fun `wall clock moved forward cannot cut a live session short`() {
        // Wall clock says "expired" but the monotonic end is still ahead.
        assertFalse(
            StrictSessionStore.isRecordExpired(
                nowWallMs = 9_000_000L, nowElapsedMs = 400_000L,
                endWallMs = 1_000_000L, endElapsedMs = 500_000L, armElapsedMs = 100_000L
            )
        )
    }

    @Test
    fun `rebooted record falls back to wall clock expiry`() {
        // elapsedRealtime reset (now < arm) -> the wall end decides.
        assertTrue(
            StrictSessionStore.isRecordExpired(
                nowWallMs = 2_000_000L, nowElapsedMs = 50_000L,
                endWallMs = 1_000_000L, endElapsedMs = 500_000L, armElapsedMs = 400_000L
            )
        )
        assertFalse(
            StrictSessionStore.isRecordExpired(
                nowWallMs = 500_000L, nowElapsedMs = 50_000L,
                endWallMs = 1_000_000L, endElapsedMs = 500_000L, armElapsedMs = 400_000L
            )
        )
        // Legacy record without anchors (armElapsed = -1) -> wall decides.
        assertTrue(
            StrictSessionStore.isRecordExpired(
                nowWallMs = 2_000_000L, nowElapsedMs = 50_000L,
                endWallMs = 1_000_000L, endElapsedMs = 0L, armElapsedMs = -1L
            )
        )
    }

    // v1.1.3 FIX 1 — regression: a deliberately ended session (the
    // ViewModel pauseTimer choke point covers pause/discard/skip/complete)
    // must leave the store disarmed, so the tamper guard cannot fire with
    // no session running.
    @Test
    fun `armed then disarmed session never reports active`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        assertTrue(StrictSessionStore.isStrictSessionActive(context))
        StrictSessionStore.disarm(context)
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
        // A later read still sees nothing — no zombie elapsed anchors.
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
    }
}
