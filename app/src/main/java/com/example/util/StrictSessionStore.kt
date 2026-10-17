package com.example.util

import android.app.admin.DevicePolicyManager
import android.content.Context
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
 *    the armed flag is set, AND now < end time. An expired record is
 *    purged on read (with a Guard Hub skip entry) so a session whose end
 *    time has passed can NEVER resurrect as active.
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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Arms a strict session record. Call at every session start with the
     * session's real end time. Extends the existing record if already armed.
     */
    fun arm(context: Context, endTimeMs: Long) {
        if (endTimeMs <= 0L) return
        try {
            prefs(context).edit()
                .putBoolean(KEY_RUNNING, true)
                .putLong(KEY_END_MS, endTimeMs)
                .apply()
        } catch (_: Exception) { }
        applyUninstallBlockedIfOwner(context, blocked = true)
    }

    /** Disarms unconditionally. Call at every stop/completion path. */
    fun disarm(context: Context) {
        try {
            prefs(context).edit()
                .putBoolean(KEY_RUNNING, false)
                .putLong(KEY_END_MS, 0L)
                .apply()
        } catch (_: Exception) { }
        applyUninstallBlockedIfOwner(context, blocked = false)
    }

    /**
     * True only when the strict+protection flags are on, a session record is
     * armed, and the end time is still in the future. Expired records are
     * purged on read — they can never resurrect.
     */
    fun isStrictSessionActive(context: Context): Boolean {
        if (!StrictModeManager.isStrictModeEnabled(context)) return false
        if (!StrictModeManager.isUninstallProtectionEnabled(context)) return false
        val armed: Boolean
        val endMs: Long
        try {
            armed = prefs(context).getBoolean(KEY_RUNNING, false)
            endMs = prefs(context).getLong(KEY_END_MS, 0L)
        } catch (_: Exception) { return false }
        if (!armed || endMs <= 0L) return false
        if (System.currentTimeMillis() >= endMs) {
            // Expired while the process was dead — purge, never resurrect.
            try {
                prefs(context).edit()
                    .putBoolean(KEY_RUNNING, false)
                    .putLong(KEY_END_MS, 0L)
                    .apply()
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
