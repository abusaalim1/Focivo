package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SuppressedNotificationItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis()
)

object SessionNotificationDigestManager {
    private const val TAG = "SessionNotifDigest"
    private const val PREFS_NAME = "focivo_session_notif_digest"
    private const val KEY_DIGEST = "suppressed_notifications_json"

    private val _digestList = MutableStateFlow<List<SuppressedNotificationItem>>(emptyList())
    val digestList: StateFlow<List<SuppressedNotificationItem>> = _digestList.asStateFlow()

    fun init(context: Context) {
        loadFromPrefs(context)
    }

    @Synchronized
    fun recordSuppressedNotification(
        context: Context,
        packageName: String,
        appName: String,
        title: String,
        text: String
    ) {
        val item = SuppressedNotificationItem(
            packageName = packageName,
            appName = appName,
            title = title,
            text = text
        )
        val current = _digestList.value.toMutableList()
        current.add(0, item) // most recent first
        if (current.size > 100) {
            current.removeAt(current.size - 1)
        }
        _digestList.value = current
        saveToPrefs(context, current)
        Log.i(TAG, "Suppressed & recorded notification from $appName ($packageName): $title")
    }

    @Synchronized
    fun clearDigest(context: Context) {
        _digestList.value = emptyList()
        saveToPrefs(context, emptyList())
    }

    fun getDigestCount(): Int = _digestList.value.size

    private fun loadFromPrefs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_DIGEST, null) ?: return
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SuppressedNotificationItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SuppressedNotificationItem(
                        id = obj.optString("id"),
                        packageName = obj.optString("packageName"),
                        appName = obj.optString("appName"),
                        title = obj.optString("title"),
                        text = obj.optString("text"),
                        timestampMs = obj.optLong("timestampMs")
                    )
                )
            }
            _digestList.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading notification digest: ${e.message}")
        }
    }

    private fun saveToPrefs(context: Context, list: List<SuppressedNotificationItem>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("packageName", item.packageName)
                    put("appName", item.appName)
                    put("title", item.title)
                    put("text", item.text)
                    put("timestampMs", item.timestampMs)
                }
                array.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_DIGEST, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving notification digest: ${e.message}")
        }
    }
}
