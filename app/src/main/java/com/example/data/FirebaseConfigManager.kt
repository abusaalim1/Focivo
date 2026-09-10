package com.example.data

import android.content.Context
import android.content.SharedPreferences

object FirebaseConfigManager {
    private const val TAG = "FirebaseConfigManager"
    private const val PREFS_NAME = "focusly_firebase_config"

    fun isRealApiKey(key: String?): Boolean = false
    fun isLiveConfigured(context: Context): Boolean = false
    fun getApiKey(context: Context): String = "local-only"
    fun getProjectId(context: Context): String = "local-only"
    fun getAppId(context: Context): String = "local-only"
    fun saveConfig(context: Context, apiKey: String, projectId: String, appId: String? = null): Boolean = true
    fun clearCustomConfig(context: Context) {}
    fun initializeFirebase(context: Context, force: Boolean = false) {}
    fun reinitializeFirebase(context: Context): Boolean = true
}
