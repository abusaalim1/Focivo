package com.example.service

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.util.AiStudyGuardManager
import com.example.util.StrictModeManager
import com.example.util.StrictUninstallGuard

class FocivoDeviceAdminReceiver : DeviceAdminReceiver() {

    /**
     * v1.4.0-round2: device admin is INERT. It performs no action on enable —
     * its only job is [onDisableRequested], which warns while a strict
     * session is genuinely running. Every callback is defensively guarded so
     * a system broadcast can never crash the app or loop services.
     *
     * v1.2.2: the disable-requested warning now carries the owner's exact
     * uninstall-blocked copy, and the launcher entry is defensively
     * re-asserted at attempt time (OEM launchers have dropped the icon after
     * a failed device-admin uninstall).
     */
    override fun onEnabled(context: Context, intent: Intent) {
        try {
            super.onEnabled(context, intent)
            Log.i(TAG, "Focivo Device Admin enabled for uninstall protection.")
            AiStudyGuardManager.logGuardActivity(
                context = context,
                app = "Device Admin",
                stage = "STRICT_MODE",
                verdict = "ADMIN_ENABLED",
                reason = "Device admin activated for strict session uninstall protection."
            )
        } catch (t: Throwable) {
            Log.e(TAG, "onEnabled failed (guarded): ${t.message}")
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        try {
            super.onDisabled(context, intent)
            Log.i(TAG, "Focivo Device Admin disabled.")
            AiStudyGuardManager.logGuardActivity(
                context = context,
                app = "Device Admin",
                stage = "STRICT_MODE",
                verdict = "ADMIN_DISABLED",
                reason = "Device admin deactivated."
            )
        } catch (t: Throwable) {
            Log.e(TAG, "onDisabled failed (guarded): ${t.message}")
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        return try {
            val isStrictActive = StrictModeManager.shouldBlockUninstallation(context)
            if (isStrictActive) {
                Log.w(TAG, "Device Admin disable attempted during active strict focus session!")
                // v1.2.2: defensive — a blocked uninstall attempt is exactly
                // when OEM launchers have been seen dropping our icon, so
                // re-assert the launcher entry right here.
                StrictUninstallGuard.reassertLauncherEntryEnabled(context)
                // v1.2.3: also surface the in-app premium overlay on this
                // route (Settings -> Apps -> Uninstall), not just the
                // installer-intercept route.
                StrictUninstallGuard.notifyStrictUninstallBlocked(context)
                AiStudyGuardManager.logGuardActivity(
                    context = context,
                    app = "Device Admin",
                    stage = "STRICT_MODE",
                    verdict = "TAMPER_PREVENTED",
                    reason = "Attempted to deactivate Device Admin while strict study session is active."
                )
                // v1.2.2: this text is shown by the SYSTEM in its device-admin
                // deactivation warning at the moment of the uninstall attempt.
                "Strict Mode is ON. Focivo can't be uninstalled during an active focus lock. Turn off Strict Mode in Settings after your session to uninstall."
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onDisableRequested failed (guarded): ${t.message}")
            null
        }
    }

    companion object {
        private const val TAG = "FocivoDeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, FocivoDeviceAdminReceiver::class.java)
        }
    }
}
