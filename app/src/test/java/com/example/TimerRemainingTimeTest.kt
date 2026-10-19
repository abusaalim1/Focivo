package com.example

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.service.FocusTimerService
import com.example.util.AiStudyGuardManager
import com.example.util.TimerAnchorStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLegacySystemClock

/**
 * v1.3.2 (owner order 2026-10-17, items 10+11) — timer notification fixes.
 *
 * Item 11: the timer notification and the floating overlay pill must compute
 * remaining time from ONE persisted anchor (elapsedRealtime-based), so they
 * can never drift apart across a screen-lock / Doze gap.
 *
 * Item 10: exactly ONE notification exists while a focus-timer session runs —
 * the timer service's foreground notification. The guard status notification
 * must be suppressed while the timer runs.
 */
class TimerRemainingTimeMathTest {

    private val minuteMs = 60_000L
    private val nowElapsed = 10_000_000L
    private val nowWall = 1_760_000_000_000L
    private val sessionSecs = 25 * 60
    private val endWall = nowWall + sessionSecs * 1000L
    // What TimerAnchorStore.arm() persists: endWall - nowWall + nowElapsed.
    private val endElapsed = endWall - nowWall + nowElapsed
    private val elapsedAtWrite = nowElapsed

    @Test
    fun `remaining time has zero drift across a simulated 10-minute lock gap`() {
        val before = TimerAnchorStore.computeRemainingSeconds(
            endElapsed, elapsedAtWrite, endWall, nowElapsed, nowWall
        )
        assertEquals(sessionSecs, before)

        // Simulate 10 minutes passing with NO ticks at all (Doze / screen
        // locked — the old pill counter simply stalled here).
        val gapMs = 10 * minuteMs
        val after = TimerAnchorStore.computeRemainingSeconds(
            endElapsed, elapsedAtWrite, endWall,
            nowElapsed + gapMs, nowWall + gapMs
        )

        // The drop is EXACTLY the gap — recomputation never depends on how
        // many ticks happened in between.
        assertEquals(sessionSecs - 600, after)
        assertEquals(600, before - after)
    }

    @Test
    fun `two reads at the same instant always agree regardless of tick history`() {
        val gapMs = 7 * minuteMs
        val first = TimerAnchorStore.computeRemainingSeconds(
            endElapsed, elapsedAtWrite, endWall, nowElapsed + gapMs, nowWall + gapMs
        )
        val second = TimerAnchorStore.computeRemainingSeconds(
            endElapsed, elapsedAtWrite, endWall, nowElapsed + gapMs, nowWall + gapMs
        )
        assertEquals(first, second)
        assertEquals(sessionSecs - 420, first)
    }

    @Test
    fun `wall clock fallback is used after a reboot resets elapsedRealtime`() {
        // Anchor written before the reboot (elapsedAtWrite is huge); after the
        // reboot elapsedRealtime starts near zero again.
        val rebootedElapsed = 120_000L
        val wallNowAfterReboot = nowWall + 5 * minuteMs
        val remaining = TimerAnchorStore.computeRemainingSeconds(
            endElapsedMs = endElapsed,
            elapsedAtWriteMs = elapsedAtWrite,
            endWallMs = endWall,
            nowElapsedMs = rebootedElapsed,
            nowWallMs = wallNowAfterReboot
        )
        assertEquals(((endWall - wallNowAfterReboot) / 1000L).toInt(), remaining)
        assertEquals(sessionSecs - 300, remaining)
    }

    @Test
    fun `legacy sessions without an elapsed anchor fall back to the wall clock`() {
        val remaining = TimerAnchorStore.computeRemainingSeconds(
            endElapsedMs = 0L, // armed before v1.3.2
            elapsedAtWriteMs = 0L,
            endWallMs = endWall,
            nowElapsedMs = nowElapsed + 3 * minuteMs,
            nowWallMs = nowWall + 3 * minuteMs
        )
        assertEquals(sessionSecs - 180, remaining)
    }

    @Test
    fun `remaining time never goes negative`() {
        val remaining = TimerAnchorStore.computeRemainingSeconds(
            endElapsed, elapsedAtWrite, endWall,
            nowElapsed + 60 * minuteMs, nowWall + 60 * minuteMs
        )
        assertEquals(0, remaining)
    }

    @Test
    fun `canonical running-timer notification id matches the timer service id`() {
        val companionField =
            FocusTimerService.Companion::class.java.getDeclaredField("NOTIFICATION_ID")
        companionField.isAccessible = true
        val serviceId = companionField.getInt(FocusTimerService.Companion)
        assertEquals(
            "TimerAnchorStore.RUNNING_TIMER_NOTIFICATION_ID must stay in sync with " +
                    "FocusTimerService's foreground notification id",
            TimerAnchorStore.RUNNING_TIMER_NOTIFICATION_ID,
            serviceId
        )

        val guardField =
            AiStudyGuardManager::class.java.getDeclaredField("NOTIFICATION_ID_GUARD_STATUS")
        guardField.isAccessible = true
        val guardId = guardField.getInt(null)
        assertTrue(
            "guard status notification id ($guardId) must differ from the single " +
                    "running-timer notification id ($serviceId)",
            guardId != serviceId
        )
    }
}

/**
 * Robolectric half: prefs-backed anchor behaviour and the item-10
 * single-notification rule, using the shadow clocks to simulate lock gaps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerRemainingTimeStoreTest {

    private lateinit var context: Context
    private val wallStart = 1_760_000_000_000L
    private val elapsedStart = 10_000_000L
    private val sessionSecs = 25 * 60

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ShadowLegacySystemClock.setNanoTime(wallStart * 1_000_000L)
        // v1.3.3 (item 3, CI fix): SystemClock.setCurrentTimeMillis is hidden
        // API — uncompilable against android.jar. In LEGACY looper mode
        // SystemClock.elapsedRealtime() follows the master scheduler, so
        // advance it directly (this is the elapsed half of what the hidden
        // call did; the wall-clock half is already set via setNanoTime above).
        RuntimeEnvironment.getMasterScheduler().advanceTo(elapsedStart)
        // Clean slate: no stale timer flags or anchors leak between tests.
        context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
            .edit().clear().apply()
        context.getSharedPreferences("focusly_timer_state", Context.MODE_PRIVATE)
            .edit().clear().apply()
        TimerAnchorStore.disarm(context)
    }

    private fun armTimerSession() {
        val endWall = wallStart + sessionSecs * 1000L
        context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE).edit()
            .putBoolean("is_manual_timer_running", true)
            .putLong("manual_timer_end_time_ms", endWall)
            .apply()
        TimerAnchorStore.arm(context, endWall)
    }

    @Test
    fun `anchor survives a simulated 10-minute lock gap with zero drift`() {
        armTimerSession()
        assertTrue(TimerAnchorStore.isManualTimerRunning(context))
        val before = TimerAnchorStore.getRemainingSeconds(context)
        assertEquals(sessionSecs, before)

        // 10 minutes pass with no ticks (Doze / screen locked).
        ShadowLegacySystemClock.setNanoTime((wallStart + 10 * 60_000L) * 1_000_000L)
        // v1.3.3 (item 3, CI fix): see setUp — advance the scheduler instead
        // of the hidden SystemClock.setCurrentTimeMillis.
        RuntimeEnvironment.getMasterScheduler().advanceTo(elapsedStart + 10 * 60_000L)

        val after = TimerAnchorStore.getRemainingSeconds(context)
        assertEquals(sessionSecs - 600, after)
        assertEquals(600, before - after)
    }

    @Test
    fun `no remaining time is reported once the session flags are cleared`() {
        armTimerSession()
        assertTrue(TimerAnchorStore.getRemainingSeconds(context) > 0)

        // pauseTimer()/discard paths clear these flags without touching the
        // service — a stale anchor must never resurrect remaining time.
        context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE).edit()
            .putBoolean("is_manual_timer_running", false)
            .putLong("manual_timer_end_time_ms", 0L)
            .apply()

        assertFalse(TimerAnchorStore.isManualTimerRunning(context))
        assertEquals(0, TimerAnchorStore.getRemainingSeconds(context))
    }

    @Test
    fun `guard status notification is suppressed while a timer session runs`() {        // Guard enabled: register the accessibility service as enabled in
        // Settings.Secure (the path isAccessibilityPermissionGranted falls
        // through to when the live service is not connected in tests).
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.example/com.example.service.AiStudyAccessibilityService"
        )
        assertTrue(AiStudyGuardManager.isAiGuardEnabled(context))
        armTimerSession()

        // Sanity: the study period IS active, so without the item-10
        // suppression this call would have posted the guard notification.
        assertTrue(AiStudyGuardManager.isStudyPeriodActive(context))

        AiStudyGuardManager.updateGuardStatusNotification(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val guardField =
            AiStudyGuardManager::class.java.getDeclaredField("NOTIFICATION_ID_GUARD_STATUS")
        guardField.isAccessible = true
        val guardId = guardField.getInt(null)
        assertTrue(
            "guard status notification (id $guardId) must not be posted while the " +
                    "timer notification is the single running notification",
            shadowOf(nm).getNotification(guardId) == null
        )
    }

    /**
     * v1.1.4-item5: liveness is decided by the elapsed-realtime anchor, not
     * the wall clock. A wall-clock jump FORWARD past the wall end must not
     * kill a live session early — the session continues on the monotonic
     * anchor (the old wall-only check reported not-running here).
     */
    @Test
    fun `session survives a wall-clock jump forward via the elapsed anchor`() {
        armTimerSession()

        // Wall clock jumps 30 min forward (past the 25-min wall end); only
        // 5 real minutes elapsed on the monotonic clock.
        ShadowLegacySystemClock.setNanoTime((wallStart + 30 * 60_000L) * 1_000_000L)
        RuntimeEnvironment.getMasterScheduler().advanceTo(elapsedStart + 5 * 60_000L)

        assertTrue(
            "elapsed anchor still has 20 min left; the session must stay live",
            TimerAnchorStore.isManualTimerRunning(context)
        )
        assertEquals(20 * 60, TimerAnchorStore.getRemainingSeconds(context))
    }

    /**
     * v1.1.4-item5: a wall-clock jump BACKWARD must not extend a session
     * whose elapsed anchor has expired — the session ends on time and can
     * never be resurrected by moving the clock.
     */
    @Test
    fun `session expires on the elapsed anchor despite a wall-clock rollback`() {
        armTimerSession()

        // 30 real minutes elapsed (past the 25-min session); wall clock
        // rolled 30 min backward so the wall end still looks "future".
        RuntimeEnvironment.getMasterScheduler().advanceTo(elapsedStart + 30 * 60_000L)
        ShadowLegacySystemClock.setNanoTime((wallStart - 30 * 60_000L) * 1_000_000L)

        assertFalse(
            "elapsed anchor expired; a clock rollback must not keep the session alive",
            TimerAnchorStore.isManualTimerRunning(context)
        )
        assertEquals(0, TimerAnchorStore.getRemainingSeconds(context))
    }

    /**
     * v1.1.4-item5: boot validation only reports (live/expired/none) and
     * logs to Guard Hub — it never clears the timer flags, so the normal
     * app-open restore can still complete an expired session exactly once.
     */
    @Test
    fun `validateOnBoot reports state without touching the persisted flags`() {
        assertEquals("none", TimerAnchorStore.validateOnBoot(context))

        armTimerSession()
        assertEquals("live", TimerAnchorStore.validateOnBoot(context))

        RuntimeEnvironment.getMasterScheduler().advanceTo(elapsedStart + 30 * 60_000L)
        ShadowLegacySystemClock.setNanoTime((wallStart + 30 * 60_000L) * 1_000_000L)
        assertEquals("expired", TimerAnchorStore.validateOnBoot(context))

        val prefs = context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
        assertTrue(
            "expired-session flags must survive boot validation for the restore path",
            prefs.getBoolean("is_manual_timer_running", false)
        )
        assertTrue(
            "expired-session end time must survive boot validation for the restore path",
            prefs.getLong("manual_timer_end_time_ms", 0L) > 0L
        )
    }
}
