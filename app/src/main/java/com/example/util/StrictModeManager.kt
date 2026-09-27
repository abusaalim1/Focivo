package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.service.FocusShieldService
import com.example.service.ScheduledBlockScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object StrictModeManager {

    private const val PREFS_NAME = "focivo_strict_mode_prefs"
    private const val KEY_STRICT_MODE_ENABLED = "key_strict_mode_enabled"
    private const val KEY_SESSION_ACTIVE = "key_session_active"

    private val _isStrictModeState = MutableStateFlow(true)
    val isStrictModeState: StateFlow<Boolean> = _isStrictModeState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        val enabled = getPrefs(context).getBoolean(KEY_STRICT_MODE_ENABLED, true)
        _isStrictModeState.value = enabled
    }

    fun isStrictModeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_STRICT_MODE_ENABLED, true)
    }

    fun setStrictModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_STRICT_MODE_ENABLED, enabled).apply()
        _isStrictModeState.value = enabled
    }

    fun setSessionActive(context: Context, active: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SESSION_ACTIVE, active).apply()
    }

    fun isSessionActive(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SESSION_ACTIVE, false)
    }

    /**
     * Determines whether uninstallation, settings opening, and package management
     * must be strictly guarded right now.
     */
    fun shouldBlockUninstallation(context: Context): Boolean {
        if (!isStrictModeEnabled(context)) return false
        // Only block uninstallation when an active scheduled focus session or manual study timer is genuinely running
        return AiStudyGuardManager.isStudyPeriodActive(context)
    }
}
