package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class AppMode {
    BLOCK,  // Unconditionally blocked during focus session
    ALLOW,  // Always allowed, bypasses block and limits
    STUDY   // Monitored: YouTube allows only study channels, browsers allow only study websites, others analyzed by AI guard
}

object AppModeManager {
    private const val TAG = "AppModeManager"
    private const val PREFS_NAME = "focivo_app_mode_prefs"
    private const val KEY_APP_MODES = "custom_app_modes_json"

    private val _appModesState = MutableStateFlow<Map<String, AppMode>>(emptyMap())
    val appModesState: StateFlow<Map<String, AppMode>> = _appModesState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        val map = loadFromPrefs(context)
        _appModesState.value = map
    }

    fun getAppMode(context: Context, packageName: String): AppMode {
        val cleanPkg = packageName.lowercase().trim()

        // Essential apps (Dialer, Camera, Payment, Settings) can NEVER be blocked
        if (EssentialAppsGuard.isEssentialApp(context, cleanPkg)) {
            return AppMode.ALLOW
        }

        // Check explicit user configured mode
        val modes = _appModesState.value.ifEmpty { loadFromPrefs(context) }
        val explicit = modes[cleanPkg]
        if (explicit != null) {
            return explicit
        }

        // YouTube defaults to STUDY mode (educational content allowed)
        if (cleanPkg == "com.google.android.youtube" || cleanPkg.contains("youtube")) {
            return AppMode.STUDY
        }

        // Fallback: check if package is in traditional blocked list
        val traditionalBlocked = com.example.service.FocusShieldService.getActiveBlockedPackages(context)
        if (traditionalBlocked.any { it.equals(cleanPkg, ignoreCase = true) }) {
            return AppMode.BLOCK
        }

        return AppMode.ALLOW
    }

    fun setAppMode(context: Context, packageName: String, mode: AppMode): Boolean {
        val cleanPkg = packageName.lowercase().trim()

        // Guard setter: Essential apps cannot be blocked
        if (EssentialAppsGuard.isEssentialApp(context, cleanPkg) && mode == AppMode.BLOCK) {
            Log.w(TAG, "Cannot set essential package $cleanPkg to BLOCK mode.")
            return false
        }

        val current = _appModesState.value.toMutableMap()
        current[cleanPkg] = mode
        _appModesState.value = current
        saveToPrefs(context, current)
        Log.i(TAG, "Set $cleanPkg mode to $mode")
        return true
    }

    private fun loadFromPrefs(context: Context): Map<String, AppMode> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_APP_MODES, null) ?: return emptyMap()
        val result = mutableMapOf<String, AppMode>()
        try {
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val modeStr = obj.optString(key, AppMode.ALLOW.name)
                val mode = try {
                    AppMode.valueOf(modeStr)
                } catch (_: Exception) {
                    AppMode.ALLOW
                }
                result[key.lowercase()] = mode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading app modes: ${e.message}")
        }
        return result
    }

    private fun saveToPrefs(context: Context, map: Map<String, AppMode>) {
        try {
            val obj = JSONObject()
            for ((pkg, mode) in map) {
                obj.put(pkg, mode.name)
            }
            getPrefs(context).edit().putString(KEY_APP_MODES, obj.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving app modes: ${e.message}")
        }
    }
}
