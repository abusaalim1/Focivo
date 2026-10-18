package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.3.4 — update-notification delivery policy.
 *
 * - Automatic checks run at most once per 60 minutes (network throttle);
 *   manual Settings checks always hit the network.
 * - A new version notifies exactly once (the 1.3.2 claim gate, unchanged).
 * - FORCE versions additionally re-notify at most once per 24h until the
 *   user updates or a newer version replaces them; non-force versions never
 *   re-notify.
 *
 * Pure decisions are tested with a fake clock; the SharedPreferences-backed
 * gates are tested with Robolectric against real prefs.
 */
@RunWith(RobolectricTestRunner::class)
class AppUpdateNotifyPolicyTest {

    private val H = 3_600_000L
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun prefs() =
        context.getSharedPreferences("focivo_app_update_prefs", Context.MODE_PRIVATE)

    // ---------- throttle ----------

    @Test
    fun `non-manual check throttles to one network check per 60 minutes`() {
        val now = 10_000_000_000L
        assertTrue(AppUpdateManager.shouldPerformCheck(now, 0L, false))
        assertFalse(AppUpdateManager.shouldPerformCheck(now, now - 10 * 60_000L, false))
        assertFalse(AppUpdateManager.shouldPerformCheck(now, now - 59 * 60_000L, false))
        assertTrue(AppUpdateManager.shouldPerformCheck(now, now - 61 * 60_000L, false))
    }

    @Test
    fun `manual check always hits the network`() {
        val now = 10_000_000_000L
        assertTrue(AppUpdateManager.shouldPerformCheck(now, now, true))
        assertTrue(AppUpdateManager.shouldPerformCheck(now, now - 1L, true))
    }

    // ---------- first notification (unchanged 1.3.2 gate) ----------

    @Test
    fun `fresh version notifies once`() {
        assertTrue(AppUpdateManager.initialNotifyDecision(0, 31))
        assertTrue(AppUpdateManager.claimNotifyForVersion(context, 31))
        // Second ask: silent — the version was already claimed.
        assertFalse(AppUpdateManager.initialNotifyDecision(31, 31))
        assertFalse(AppUpdateManager.claimNotifyForVersion(context, 31))
    }

    @Test
    fun `newer version notifies again`() {
        prefs().edit().putInt("last_notified_version_code", 31).apply()
        assertTrue(AppUpdateManager.initialNotifyDecision(31, 32))
        assertTrue(AppUpdateManager.claimNotifyForVersion(context, 32))
    }

    // ---------- force re-notify ----------

    @Test
    fun `force version does not re-notify before 24 hours`() {
        val now = 10_000_000_000L
        // Armed at `now` for version 31…
        prefs().edit()
            .putInt(AppUpdateManager.KEY_FORCE_NOTIFIED_VERSION, 31)
            .putLong(AppUpdateManager.KEY_FORCE_NOTIFIED_AT, now)
            .apply()
        // …23h later: still silent.
        assertFalse(AppUpdateManager.forceRenotifyDue(now + 23 * H, 31, 31, now))
        assertFalse(AppUpdateManager.claimForceRenotify(context, 31))
    }

    @Test
    fun `force version re-notifies once after 24 hours, then is gated again`() {
        prefs().edit()
            .putInt(AppUpdateManager.KEY_FORCE_NOTIFIED_VERSION, 31)
            .putLong(AppUpdateManager.KEY_FORCE_NOTIFIED_AT, System.currentTimeMillis() - 25 * H)
            .apply()
        // 25h later: one re-notification is allowed…
        assertTrue(AppUpdateManager.claimForceRenotify(context, 31))
        // …and the next ask is silent until another 24h pass.
        assertFalse(AppUpdateManager.claimForceRenotify(context, 31))
    }

    @Test
    fun `force re-notify is for the same version only`() {
        val now = 10_000_000_000L
        // The armed version was 31; a check now sees version 32 pending.
        assertFalse(AppUpdateManager.forceRenotifyDue(now + 30 * H, 32, 31, now))
    }

    @Test
    fun `force re-notify requires a prior first notification`() {
        val now = 10_000_000_000L
        // Never armed (forceNotifiedAtMs = 0) -> never nags on its own.
        assertFalse(AppUpdateManager.forceRenotifyDue(now + 48 * H, 31, 31, 0L))
    }

    @Test
    fun `recordForceUpdateNotified arms the gate for that version`() {
        AppUpdateManager.recordForceUpdateNotified(context, 31)
        val e = prefs()
        assertEquals(31, e.getInt(AppUpdateManager.KEY_FORCE_NOTIFIED_VERSION, 0))
        assertTrue(e.getLong(AppUpdateManager.KEY_FORCE_NOTIFIED_AT, 0L) > 0L)
    }
}
