package com.example.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.receiver.FocivoDeviceAdminReceiver

/**
 * v1.1 — Anti-Uninstall Shield (Device Admin) manager.
 *
 * While admin is active, the app cannot be uninstalled. The user can
 * deactivate anytime in Settings — the app never traps users.
 */
object DeviceAdminManager {
    private const val TAG = "DeviceAdminManager"
    private const val PREFS = "focusly_local_data"
    private const val KEY_ENABLED = "device_admin_enabled"

    fun isAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, FocivoDeviceAdminReceiver::class.java)
            dpm.isAdminActive(admin)
        } catch (e: Exception) {
            Log.e(TAG, "isAdminActive failed", e)
            false
        }
    }

    /** Returns the activation intent (opens system Device Admin settings). */
    fun activationIntent(context: Context): Intent {
        val admin = ComponentName(context, FocivoDeviceAdminReceiver::class.java)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Focivo's Anti-Uninstall Shield prevents uninstalling the app mid-study. " +
                "You can turn this off anytime in Settings."
            )
        }
    }

    /** Deactivates admin (the OFF path — always available, never traps users). */
    fun deactivate(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, FocivoDeviceAdminReceiver::class.java)
            dpm.removeActiveAdmin(admin)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, false)
                .apply()
            Log.i(TAG, "Device admin deactivated by user")
        } catch (e: Exception) {
            Log.e(TAG, "deactivate failed", e)
        }
    }
}
