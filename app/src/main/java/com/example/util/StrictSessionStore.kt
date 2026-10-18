package com.example.util

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log

/**
 * v1.3.1 hotfix (owner order 2026-10-17, item A2) — durable strict-session
 * state.
 *
 * Why this exists: the uninstall-protection gate must not be dodgeable by
 * killing the app process or rebooting the phone mid-session. The study
 * engine already persists timer/shield end times, but nothing recorded a
 * *strict-session* record with expiry validation in one place. This store
 * does exactly that:
 *
 *  - [arm]: called at every strict-session start (FocusTimerService +
 *    FocusShieldService choke points) with the session's REAL end time.
 *  - [disarm]: called at every stop/completion path — a session that ended
 *    can never resurrect.
 *  - [isStrictSessionActive]: true only when strict+protection are enabled,
 *    the armed flag is set, AND the session end has not passed on the
 *    monotonic elapsed-realtime clock (wall-clock fallback after a reboot).
 *    An expired record is purged on read (with a Guard Hub skip entry) so a
 *    session whose end time has passed can NEVER resurrect as active — a
 *    crashed process or a changed system clock cannot extend protection.
 *  - [validateOnBoot]: called from the BOOT_COMPLETED receiver and from
 *    StrictModeManager.init() — expired records are purged; live ones are
 *    left armed (the engine's own restore path re-starts the session).
 *
 * setUninstallBlocked (A3): DevicePolicyManager.setUninstallBlocked()
 * requires DEVICE OWNER or PROFILE OWNER authority. A normal device admin
 * (what Focivo has) gets a SecurityException if it tries. We therefore:
 *  - arm/disarm the block ONLY when dpm.isDeviceOwnerApp()/isProfileOwnerApp()
 *    reports true for our package;
 *  - otherwise skip SILENTLY (never pretend it worked).
 * The result is logged to Guard Hub so the owner's device test can see
 * which layer actually engaged.
 */
object StrictSessionStore {

    private const val TAG = "StrictSessionStore"
    private const val PREFS_NAME = "focivo_strict_session"
    private const val KEY_RUNNING = "strict_session_running"
    private const val KEY_END_MS = "strict_session_end_ms"
    // v1.1.3 FIX 1: monotonic anchors. Wall clock is user-adjustable, so
    // expiry is anchored on SystemClock.elapsedRealtime() too: a crashed app
    // (process death mid-session) can never leave a forever-"protected"
    // record that outlives the session's real end, and moving the system
    // clock can never extend protection. elapsedRealtime resets at boot, so
    // [KEY_ARM_ELAPSED_MS] detects "rebooted since arming" and the read falls
    // back to the wall-clock end time for those records.
    private const val KEY_END_ELAPSED_MS = "strict_session_end_elapsed_ms"
    private const val KEY_ARM_ELAPSED_MS = "strict_session_arm_elapsed_ms"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Arms a strict session record. Call at every session start with the
     * session's real end time. Extends the existing record if already armed.
     */
    fun arm(context: Context, endTimeMs: Long) {
        if (endTimeMs <= 0L) return
        try {
            val nowElapsed = SystemClock.elapsedRealtime()
            // Remaining wall-clock ms converted to the monotonic clock.
            val endElapsed = endTimeMs - System.currentTimeMillis() + nowElapsed
            prefs(context).edit()
                .putBoolean(KEY_RUNNING, true)
                .putLong(KEY_END_MS, endTimeMs)
                .putLong(KEY_END_ELAPSED_MS, endElapsed)
                .putLong(KEY_ARM_ELAPSED_MS, nowElapsed)
                .apply()
        } catch (_: Exception) { }
        applyUninstallBlockedIfOwner(context, blocked = true)
    }

    /** Disarms unconditionally. Call at every stop/completion path. */
    fun disarm(context: Context) {
        try {
            purgeRecord(prefs(context))
        } catch (_: Exception) { }
        // v1.3.3 (item 6b): a finished session auto-cancels the tamper
        // alert notification — the alert is strictly session-scoped.
        try {
            com.example.service.FocivoNotifications.cancelStrictUninstallAlert(context)
        } catch (_: Exception) { }
        applyUninstallBlockedIfOwner(context, blocked = false)
    }

    /**
     * v1.3.3 (item 6b) — reads the armed session's real end time, 0L when
     * no record is armed. Used for the uninstall-attempt alert body so the
     * user sees exactly when protection lifts. Pure read.
     */
    fun getEndTimeMs(context: Context): Long {
        return try {
            prefs(context).getLong(KEY_END_MS, 0L)
        } catch (_: Exception) { 0L }
    }

    /**
     * True only when the strict+protection flags are on, a session record is
     * armed, and the end time is still in the future. Expired records are
     * purged on read — they can never resurrect.
     *
     * v1.1.3 FIX 1: expiry is decided by [isRecordExpired], which prefers
     * the monotonic elapsed-realtime anchor (immune to wall-clock changes)
     * and falls back to the wall-clock end time only when the device
     * rebooted since arming (elapsedRealtime resets at boot) or the record
     * predates the anchors.
     */
    fun isStrictSessionActive(context: Context): Boolean {
        if (!StrictModeManager.isStrictModeEnabled(context)) return false
        if (!StrictModeManager.isUninstallProtectionEnabled(context)) return false
        val armed: Boolean
        val endMs: Long
        val endElapsedMs: Long
        val armElapsedMs: Long
        try {
            val p = prefs(context)
            armed = p.getBoolean(KEY_RUNNING, false)
            endMs = p.getLong(KEY_END_MS, 0L)
            endElapsedMs = p.getLong(KEY_END_ELAPSED_MS, 0L)
            armElapsedMs = p.getLong(KEY_ARM_ELAPSED_MS, -1L)
        } catch (_: Exception) { return false }
        if (!armed || endMs <= 0L) return false
        if (isRecordExpired(
                nowWallMs = System.currentTimeMillis(),
                nowElapsedMs = SystemClock.elapsedRealtime(),
                endWallMs = endMs,
                endElapsedMs = endElapsedMs,
                armElapsedMs = armElapsedMs
            )
        ) {
            // Expired while the process was dead — purge, never resurrect.
            try {
                purgeRecord(prefs(context))
            } catch (_: Exception) { }
            // v1.3.3 (item 6b): an expired-while-dead session must not leave
            // a stale tamper alert in the shade.
            try {
                com.example.service.FocivoNotifications.cancelStrictUninstallAlert(context)
            } catch (_: Exception) { }
            applyUninstallBlockedIfOwner(context, blocked = false)
            try {
                AiStudyGuardManager.logGuardSkip(
                    context, "strict-session", "expiry", "expired-purged"
                )
            } catch (_: Exception) { }
            Log.i(TAG, "Strict session record expired; purged (never resurrects).")
            return false
        }
        return true
    }

    /**
     * v1.1.3 FIX 1 — pure expiry decision (unit-testable; no Android I/O
     * beyond the clock values passed in).
     *
     * Same-boot records (nowElapsed >= armElapsed and a valid elapsed end
     * anchor exists) expire on the MONOTONIC clock: setting the system clock
     * back can never keep protection alive, and setting it forward can never
     * cut a live session's protection short.
     *
     * Records armed before a reboot (elapsedRealtime reset, so nowElapsed <
     * armElapsed) or legacy records without anchors fall back to the
     * wall-clock end time. Either way a crashed process can never leave a
     * forever-"protected" record — expiry is always bounded by the session's
     * real end.
     */
    internal fun isRecordExpired(
        nowWallMs: Long,
        nowElapsedMs: Long,
        endWallMs: Long,
        endElapsedMs: Long,
        armElapsedMs: Long
    ): Boolean {
        val rebootedSinceArm = armElapsedMs < 0L || nowElapsedMs < armElapsedMs
        return if (!rebootedSinceArm && endElapsedMs > 0L) {
            nowElapsedMs >= endElapsedMs
        } else {
            nowWallMs >= endWallMs
        }
    }

    /** Clears every key of the armed record. A purged record is gone. */
    private fun purgeRecord(p: SharedPreferences) {
        p.edit()
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_END_MS, 0L)
            .putLong(KEY_END_ELAPSED_MS, 0L)
            .putLong(KEY_ARM_ELAPSED_MS, -1L)
            .apply()
    }

    /**
     * Call from the BOOT_COMPLETED receiver and from StrictModeManager.init().
     * Purges expired records; live records stay armed. Returns whether a live
     * strict session record survived boot.
     */
    fun validateOnBoot(context: Context): Boolean {
        val active = isStrictSessionActive(context)
        try {
            AiStudyGuardManager.logGuardActivity(
                context, "strict-session", "boot-validate",
                if (active) "armed" else "disarmed",
                "boot re-arm check; expired records purged"
            )
        } catch (_: Exception) { }
        return active
    }

    /**
     * Applies DevicePolicyManager.setUninstallBlocked() ONLY when this app
     * holds Device Owner or Profile Owner authority. A plain device admin
     * (Focivo's case) must NOT call it — it would throw SecurityException —
     * so we skip silently and log the honest reason.
     */
    private fun applyUninstallBlockedIfOwner(context: Context, blocked: Boolean) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as? DevicePolicyManager ?: return
            val pkg = context.packageName
            val hasOwnerAuthority = try {
                dpm.isDeviceOwnerApp(pkg) || dpm.isProfileOwnerApp(pkg)
            } catch (_: Exception) { false }
            if (!hasOwnerAuthority) {
                // Normal device admin: setUninstallBlocked is NOT available.
                // Skip silently; the gate + accessibility tamper guard carry
                // the protection instead. Never fake it.
                Log.d(TAG, "setUninstallBlocked skipped: no device/profile-owner authority " +
                        "(normal device admin).")
                return
            }
            val admin = DeviceAdminGuard.componentName(context)
            dpm.setUninstallBlocked(admin, pkg, blocked)
            Log.i(TAG, "setUninstallBlocked($blocked) applied via owner authority.")
            AiStudyGuardManager.logGuardActivity(
                context, "strict-session", "uninstall-blocked-api",
                if (blocked) "applied" else "cleared",
                "setUninstallBlocked via device/profile-owner authority"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "setUninstallBlocked failed: ${t.message}")
        }
    }
}
