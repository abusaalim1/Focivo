package com.example.service

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.util.AiStudyGuardManager
import com.example.util.StrictModeManager

class FocivoDeviceAdminReceiver : DeviceAdminReceiver() {

    /**
     * v1.4.0-round2: device admin is INERT. It performs no action on enable —
     * its only job is [onDisableRequested], which warns while a strict
     * session is genuinely running. Every callback is defensively guarded so
     * a system broadcast can never crash the app or loop services.
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
                AiStudyGuardManager.logGuardActivity(
                    context = context,
                    app = "Device Admin",
                    stage = "STRICT_MODE",
                    verdict = "TAMPER_PREVENTED",
                    reason = "Attempted to deactivate Device Admin while strict study session is active."
                )
                "Strict Focus Session in progress! Focivo uninstall protection remains active until your study timer ends."
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
