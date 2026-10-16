package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.MainActivity

/**
 * v1.2.2 — strict-mode uninstall hardening.
 *
 * Background: on-device testing showed that after a correctly-blocked
 * uninstall in strict mode, the launcher icon can vanish (the app stays
 * installed and reachable via recents/notification). A full code audit
 * (2026-10-15) found the app NEVER hides its own launcher entry — zero
 * `setApplicationHidden` / `setComponentEnabledSetting` /
 * `setPackagesSuspended` calls anywhere, a single launcher activity, no
 * activity-alias — so the disappearance is a launcher-side artifact of the
 * failed device-admin uninstall (the OEM launcher drops the icon
 * optimistically and never restores it), not something the app does.
 *
 * Defenses:
 *  - [reassertLauncherEntryEnabled]: no-op-safe re-enable of our launcher
 *    activity. Called on app start ([StrictModeManager.init]) and at the
 *    moment of a blocked uninstall attempt
 *    ([com.example.service.FocivoDeviceAdminReceiver.onDisableRequested]).
 *    Note: `DevicePolicyManager.setApplicationHidden` is deliberately NOT
 *    used — it requires device/profile-owner rights and would throw for a
 *    regular device-admin app.
 *  - [notifyStrictUninstallBlocked]: brings Focivo to the foreground with
 *    [ACTION_STRICT_UNINSTALL_BLOCKED] so the host Activity can show
 *    [com.example.ui.components.StrictUninstallBlockedOverlay]. Intended to
 *    be called from the uninstaller-interception path when an uninstall is
 *    attempted while strict mode is active.
 */
object StrictUninstallGuard {
    private const val TAG = "StrictUninstallGuard"

    /** Intent action delivered to MainActivity to show the blocked-uninstall notice. */
    const val ACTION_STRICT_UNINSTALL_BLOCKED = "com.example.action.STRICT_UNINSTALL_BLOCKED"

    fun reassertLauncherEntryEnabled(context: Context) {
        try {
            val pm = context.packageManager
            val launcher = ComponentName(context, MainActivity::class.java)
            pm.setComponentEnabledSetting(
                launcher,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (t: Throwable) {
            Log.w(TAG, "reassertLauncherEntryEnabled failed: ${t.message}")
        }
    }

    fun notifyStrictUninstallBlocked(context: Context) {
        // v1.2.3: FIRST re-assert the launcher entry — a blocked uninstall
        // attempt is exactly when OEM launchers have been seen dropping our
        // icon (zombie state: icon gone, package present, services running).
        reassertLauncherEntryEnabled(context)
        try {
            // v1.2.3: NO FLAG_ACTIVITY_CLEAR_TOP here — the previous build
            // yanked MainActivity with CLEAR_TOP while the package installer
            // was mid-uninstall-flow, aborting the installer task AFTER the
            // launcher had optimistically dropped the icon (the zombie
            // trigger). SINGLE_TOP + NEW_TASK only: the window-safe a11y
            // overlay (BlockOverlayManager.intercept) is the first responder
            // over the installer; the installer task stays intact so backing
            // out restores the launcher state instead of aborting it.
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_STRICT_UNINSTALL_BLOCKED
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "notifyStrictUninstallBlocked failed: ${t.message}")
        }
    }
}
