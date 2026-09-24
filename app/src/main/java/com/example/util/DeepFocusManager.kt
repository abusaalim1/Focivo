package com.example.util

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeepFocusManager {

    private const val TAG = "DeepFocusManager"
    private const val PREFS_NAME = "focivo_deep_focus_prefs"
    private const val KEY_DEEP_FOCUS_ENABLED = "key_deep_focus_enabled"
    private const val KEY_DEEP_FOCUS_SESSION_ACTIVE = "key_deep_focus_session_active"

    private val _isDeepFocusEnabledState = MutableStateFlow(false)
    val isDeepFocusEnabledState: StateFlow<Boolean> = _isDeepFocusEnabledState.asStateFlow()

    private val _isDeepFocusActiveState = MutableStateFlow(false)
    val isDeepFocusActiveState: StateFlow<Boolean> = _isDeepFocusActiveState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        val prefs = getPrefs(context)
        val isEnabled = prefs.getBoolean(KEY_DEEP_FOCUS_ENABLED, false)
        val isActive = prefs.getBoolean(KEY_DEEP_FOCUS_SESSION_ACTIVE, false)
        _isDeepFocusEnabledState.value = isEnabled
        _isDeepFocusActiveState.value = isActive
    }

    fun isDeepFocusEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEEP_FOCUS_ENABLED, false)
    }

    fun setDeepFocusEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DEEP_FOCUS_ENABLED, enabled).apply()
        _isDeepFocusEnabledState.value = enabled
    }

    fun isDeepFocusSessionActive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEEP_FOCUS_SESSION_ACTIVE, false)
    }

    fun setDeepFocusSessionActive(context: Context, active: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DEEP_FOCUS_SESSION_ACTIVE, active).apply()
        _isDeepFocusActiveState.value = active
    }

    /**
     * Attempts to start Android Screen Pinning / Lock Task Mode on the hosting activity.
     */
    fun startScreenPinning(activity: Activity): Boolean {
        return try {
            activity.startLockTask()
            Log.i(TAG, "Screen Pinning / LockTask started successfully for Deep Focus")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to startLockTask: ${e.message}")
            false
        }
    }

    /**
     * Releases Android Screen Pinning / Lock Task Mode on the hosting activity.
     */
    fun stopScreenPinning(activity: Activity): Boolean {
        return try {
            activity.stopLockTask()
            Log.i(TAG, "Screen Pinning / LockTask stopped successfully")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to stopLockTask: ${e.message}")
            false
        }
    }

    /**
     * Checks if screen pinning / lock task mode is currently active on the device.
     */
    fun isScreenPinned(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            } else {
                @Suppress("DEPRECATION")
                am?.isInLockTaskMode == true
            }
        } catch (e: Throwable) {
            false
        }
    }
}
