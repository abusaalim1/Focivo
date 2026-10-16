package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * v1.2.3 (B) — user-curated YouTube channel BLOCKLIST.
 *
 * The companion to the study-channel allowlist ([StudyChannelsManager]):
 * any video whose extracted channel matches an entry here is blocked
 * outright whenever it plays during a study period — no verdict scoring,
 * no warning flow. Explicit user intent beats the classifier.
 *
 * Matching is the same contains() semantics as the allowlist (channel names
 * are short, so contains() is safe and typo-tolerant).
 */
object BlockedChannelsManager {

    private const val TAG = "BlockedChannelsManager"
    private const val PREFS_NAME = "focivo_blocked_channels_prefs"
    private const val KEY_CHANNELS = "blocked_channels_json"

    data class ChannelEntry(val name: String)

    private val _channelsState = MutableStateFlow<List<ChannelEntry>>(emptyList())
    val channelsState: StateFlow<List<ChannelEntry>> = _channelsState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        _channelsState.value = getChannels(context)
    }

    fun getChannels(context: Context): List<ChannelEntry> {
        val raw = getPrefs(context).getString(KEY_CHANNELS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            raw.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .map { ChannelEntry(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** @return true when the channel was newly added. */
    fun addChannel(context: Context, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        val current = getChannels(context).map { it.name }.toMutableList()
        if (current.any { it.equals(clean, ignoreCase = true) }) return false
        current.add(clean)
        persist(context, current)
        return true
    }

    fun removeChannel(context: Context, entry: ChannelEntry) {
        val current = getChannels(context).map { it.name }
            .filter { !it.equals(entry.name, ignoreCase = true) }
        persist(context, current)
    }

    private fun persist(context: Context, names: List<String>) {
        getPrefs(context).edit().putString(KEY_CHANNELS, names.joinToString("\n")).apply()
        _channelsState.value = names.map { ChannelEntry(it) }
        Log.d(TAG, "Blocked channels now: ${names.size}")
    }

    /**
     * True when the extracted channel matches a blocklist entry.
     * A blank channel never matches (no evidence → no block).
     */
    fun isBlockedChannel(context: Context, channelName: String?): Boolean {
        if (channelName.isNullOrBlank()) return false
        val clean = channelName.trim().lowercase(Locale.ROOT)
        return getChannels(context).any { clean.contains(it.name.lowercase(Locale.ROOT)) }
    }
}
