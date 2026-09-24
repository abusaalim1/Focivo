package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object YouTubeStudyGuardManager {

    private const val TAG = "YouTubeGuardManager"
    private const val PREFS_NAME = "focusly_youtube_guard_prefs"
    private const val KEY_FILTER_ENABLED = "key_yt_ai_filter_enabled"
    private const val KEY_BLOCKED_COUNT = "key_yt_blocked_count"

    private val _isFilterEnabledState = MutableStateFlow(false)
    val isFilterEnabledState: StateFlow<Boolean> = _isFilterEnabledState.asStateFlow()

    private val _blockedVideosCountState = MutableStateFlow(0)
    val blockedVideosCountState: StateFlow<Int> = _blockedVideosCountState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        val prefs = getPrefs(context)
        val hasPerm = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
        val enabled = hasPerm && prefs.getBoolean(KEY_FILTER_ENABLED, true)
        val count = prefs.getInt(KEY_BLOCKED_COUNT, 0)
        _isFilterEnabledState.value = enabled
        _blockedVideosCountState.value = count
    }

    fun isFilterEnabled(context: Context): Boolean {
        val hasPerm = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
        if (!hasPerm) return false
        val prefs = getPrefs(context)
        return prefs.getBoolean(KEY_FILTER_ENABLED, true) || AiStudyGuardManager.isStudyPeriodActive(context)
    }

    fun setFilterEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FILTER_ENABLED, enabled).apply()
        val hasPerm = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
        _isFilterEnabledState.value = hasPerm && enabled
        Log.d(TAG, "YouTube AI Content Filter set to: $enabled (effective=${hasPerm && enabled})")
    }

    fun recordBlockedVideo(context: Context, title: String, channel: String?, reason: String) {
        val prefs = getPrefs(context)
        val currentCount = prefs.getInt(KEY_BLOCKED_COUNT, 0) + 1
        prefs.edit().putInt(KEY_BLOCKED_COUNT, currentCount).apply()
        _blockedVideosCountState.value = currentCount
        Log.w(TAG, "[YouTube Guard] Blocked entertainment video: '$title' ($channel) - Reason: $reason")
    }
}
