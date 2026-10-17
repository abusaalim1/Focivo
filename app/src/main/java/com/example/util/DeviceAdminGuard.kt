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
 *  - This helper only READS admin state and LAUNCHES the system screens.
 *    Focivo can never deactivate itself: [android.app.admin.DevicePolicyManager]
 *    exposes no self-deactivate API — that absence is deliberate, and it is
 *    the user's guarantee that the app cannot secretly drop its own shield.
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

    /** True when Focivo is currently an active device admin. */
    fun isAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.isAdminActive(componentName(context)) == true
        } catch (t: Throwable) {
            Log.e(TAG, "isAdminActive failed: ${t.message}")
            false
        }
    }

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
     * Opens the system's device-admin list so the user can deactivate Focivo
     * themselves. Android gives apps NO API to deactivate their own admin —
     * so the disable path is always the system Settings screen, never an
     * in-app switch. Honest and trap-free by construction.
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
