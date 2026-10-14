package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * v1.1 — Anti-Uninstall Shield (Device Admin).
 *
 * While admin is active, Focivo cannot be uninstalled mid-study. The user
 * must first turn the shield off in Settings to uninstall — the app NEVER
 * traps users; the off path is always available.
 */
class FocivoDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        private const val TAG = "FocivoDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
        context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("device_admin_enabled", true)
            .apply()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device admin disabled")
        context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("device_admin_enabled", false)
            .apply()
    }
}
