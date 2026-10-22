package com.example.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.service.FocivoDeviceAdminReceiver

/**
 * v1.3.0 follow-up (owner order 2026-10-17, item 2) — device-admin layer for
 * strict-session uninstall protection (the Regain-style guarantee).
 *
 * HOW ANDROID PROTECTS UNINSTALL WITH AN ACTIVE DEVICE ADMIN:
 *  - While our DeviceAdminReceiver is an active device admin, the
 *    PackageManager REFUSES to uninstall Focivo. Every uninstall path —
 *    launcher long-press, Settings → Apps → Uninstall, `adb uninstall` —
 *    is rerouted to "Deactivate & uninstall".
 *  - On deactivation Android shows the system warning dialog containing
 *    whatever [FocivoDeviceAdminReceiver.onDisableRequested] returns — the
 *    user reads our strict explanation there and can back out.
 *
 * v1.1.4d (ROUND D, item D1) — CORRECTION of a long-standing false claim
 * in this file: DevicePolicyManager DOES expose a self-deactivate API.
 * [DevicePolicyManager.removeActiveAdmin] "can only be called by the
 * application that owns the administration component" (Android
 * Developers reference) — Focivo can release its OWN admin, and only its
 * own. The persistent admin was the actual mechanism behind the owner's
 * "biggest bug": with Strict ON and NO session running, uninstall still
 * silently failed on every path (no Focivo popup — the OS refusal is
 * invisible), because the admin stays active 24/7 once granted. Regain-
 * class apps protect only during the session; outside it, deactivation
 * must proceed normally. [deactivateOwnAdmin] is that normal path,
 * in-app: it refuses ONLY while the gate is PROTECTED (a live strict
 * session) and releases immediately otherwise.
 *
 * POLICY MINIMALITY (read the XML, not just this comment):
 *  res/xml/device_admin_policies.xml declares an EMPTY <uses-policies/>.
 *  No force-lock, no wipe, no password rules — the admin exists ONLY so
 *  onDisableRequested() can warn during strict sessions.
 */
object DeviceAdminGuard {

    private const val TAG = "DeviceAdminGuard"

    fun componentName(context: Context): ComponentName =
        FocivoDeviceAdminReceiver.getComponentName(context)

    /** True when Focivo is currently an active device admin. v1.3.3 — live
     *  query via PermissionStates; verifies OUR receiver is really active. */
    fun isAdminActive(context: Context): Boolean =
        PermissionStates.isDeviceAdminActive(context)

    /**
     * Launches the SYSTEM device-admin activation screen for Focivo.
     * The system — not us — shows the permission list and takes the user's
     * explicit consent. Call this only after the premium explainer sheet so
     * the user already knows why we ask.
     */
    fun launchActivation(context: Context): Boolean {
        return try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Protects your focus: while a strict study session runs, " +
                        "Android will not let Focivo be uninstalled. Focivo never " +
                        "locks your screen, wipes data, or changes passwords — " +
                        "you can remove this admin anytime from Settings."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "launchActivation failed: ${t.message}")
            false
        }
    }

    /**
     * Pure release rule (unit-tested): the in-app admin release is allowed
     * in every gate state EXCEPT [UninstallProtectionGate.State.PROTECTED]
     * — a live strict session. In every other state (strict off, opted
     * out, no session, admin somehow inactive) nothing may hold the admin
     * hostage.
     */
    fun canReleaseAdmin(state: UninstallProtectionGate.State): Boolean =
        state != UninstallProtectionGate.State.PROTECTED

    /**
     * v1.1.4d (item D1) — in-app one-tap release of Focivo's OWN device
     * admin via [DevicePolicyManager.removeActiveAdmin]. This is the fix
     * for "Strict ON + no session → uninstall silently impossible": the
     * user no longer has to dig through OEM Settings (on vivo the
     * hardcoded AOSP DeviceAdminSettings component does not resolve to
     * the device-admin list at all, which stranded the owner).
     *
     * Defence in depth: the gate is re-decided HERE with live inputs —
     * even if a caller mis-wires its button, a PROTECTED (live strict
     * session) state refuses the release and logs the refusal to Guard
     * Hub. The operation is asynchronous at the OS level; callers should
     * re-read [isAdminActive] on resume. Returns true when the release
     * was requested (or there was nothing to release is NOT success —
     * false means: not active, refused, or failed, all logged).
     */
    fun deactivateOwnAdmin(context: Context): Boolean {
        if (!isAdminActive(context)) {
            Log.i(TAG, "deactivateOwnAdmin: admin not active — nothing to release.")
            return false
        }
        val state = UninstallProtectionGate.decide(
            strictEnabled = StrictModeManager.isStrictModeEnabled(context),
            protectionEnabled = StrictModeManager.isUninstallProtectionEnabled(context),
            sessionActive = AiStudyGuardManager.isStudyPeriodActive(context) ||
                StrictSessionStore.isStrictSessionActive(context),
            adminActive = true
        )
        if (!canReleaseAdmin(state)) {
            Log.w(TAG, "deactivateOwnAdmin REFUSED: gate is PROTECTED (live strict session).")
            try {
                AiStudyGuardManager.logGuardActivity(
                    context, "Device Admin", "admin-release", "REFUSED_PROTECTED",
                    "In-app admin release refused — a strict session is live."
                )
            } catch (_: Exception) { }
            return false
        }
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as? DevicePolicyManager ?: return false
            dpm.removeActiveAdmin(componentName(context))
            Log.i(TAG, "deactivateOwnAdmin: removeActiveAdmin requested (gate=$state).")
            try {
                AiStudyGuardManager.logGuardActivity(
                    context, "Device Admin", "admin-release", "RELEASED",
                    "Device admin released in-app outside a protected session (gate=$state); uninstall is fully normal."
                )
            } catch (_: Exception) { }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "deactivateOwnAdmin failed: ${t.message}")
            false
        }
    }

    /**
     * Opens the system's device-admin list so the user can deactivate
     * Focivo themselves — kept as the fallback route next to the in-app
     * one-tap release ([deactivateOwnAdmin]).
     *
     * Returns true when a settings screen was launched. OEMs rename this
     * screen (vivo: Security → More security settings → Device admin apps),
     * so we try the direct component first and fall back to the generic
     * Security settings page with a toast describing the path.
     */
    fun openDeactivateScreen(context: Context): Boolean {
        val direct = try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.DeviceAdminSettings"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "direct DeviceAdminSettings unavailable: ${t.message}")
            false
        }
        if (direct) return true
        return try {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Security → Device admin apps → turn off Focivo",
                Toast.LENGTH_LONG
            ).show()
            true
        } catch (t: Throwable) {
            Log.e(TAG, "openDeactivateScreen failed: ${t.message}")
            Toast.makeText(
                context,
                "Open Settings → Security → Device admin apps to remove Focivo's protection",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }
}
