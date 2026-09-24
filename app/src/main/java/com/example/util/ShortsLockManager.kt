package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ShortsLockManager {

    private const val PREFS_NAME = "focivo_shorts_lock_prefs"
    private const val KEY_SHORTS_LOCK_ENABLED = "key_shorts_lock_enabled"
    private const val KEY_REELS_LOCK_ENABLED = "key_reels_lock_enabled"

    private val _isShortsLockState = MutableStateFlow(false)
    val isShortsLockState: StateFlow<Boolean> = _isShortsLockState.asStateFlow()

    private val _isReelsLockState = MutableStateFlow(false)
    val isReelsLockState: StateFlow<Boolean> = _isReelsLockState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        val prefs = getPrefs(context)
        val hasPerm = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
        _isShortsLockState.value = hasPerm && prefs.getBoolean(KEY_SHORTS_LOCK_ENABLED, true)
        _isReelsLockState.value = hasPerm && prefs.getBoolean(KEY_REELS_LOCK_ENABLED, true)
    }

    fun isShortsLockEnabled(context: Context): Boolean {
        val hasPerm = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
        if (!hasPerm) return false
        return getPrefs(context).getBoolean(KEY_SHORTS_LOCK_ENABLED, true) || AiStudyGuardManager.isStudyPeriodActive(context)
    }

    fun setShortsLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHORTS_LOCK_ENABLED, enabled).apply()
        val hasPerm = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
        _isShortsLockState.value = hasPerm && enabled
    }

    fun isReelsLockEnabled(context: Context): Boolean {
        val hasPerm = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
        if (!hasPerm) return false
        return getPrefs(context).getBoolean(KEY_REELS_LOCK_ENABLED, true) || AiStudyGuardManager.isStudyPeriodActive(context)
    }

    fun setReelsLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REELS_LOCK_ENABLED, enabled).apply()
        val hasPerm = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
        _isReelsLockState.value = hasPerm && enabled
    }
}
