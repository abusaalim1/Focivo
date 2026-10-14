package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Unified Study Allowlist Manager (Feature 10).
 * Single source of truth for:
 *  - YouTube educational channels (delegates & unifies with StudyChannelsManager)
 *  - Educational/academic websites
 *  - Trusted educational keywords/phrases
 */
object StudyAllowlistManager {
    private const val TAG = "StudyAllowlistManager"
    private const val PREFS_NAME = "focivo_unified_allowlist_prefs"
    private const val KEY_CUSTOM_WEBSITES = "custom_study_websites"
    private const val KEY_REMOVED_BUILTIN_WEBSITES = "removed_builtin_study_websites"

    data class WebsiteEntry(val domain: String, val isCustom: Boolean)

    // Curated high-reputation educational websites allowed during focus sessions
    val BUILTIN_STUDY_DOMAINS: Set<String> = setOf(
        "wikipedia.org",
        "khanacademy.org",
        "coursera.org",
        "edx.org",
        "udemy.com",
        "leetcode.com",
        "hackerrank.com",
        "github.com",
        "stackoverflow.com",
        "stackexchange.com",
        "geeksforgeeks.org",
        "w3schools.com",
        "developer.android.com",
        "developer.mozilla.org",
        "kotlinlang.org",
        "python.org",
        "docs.oracle.com",
        "ncert.nic.in",
        "cbse.gov.in",
        "upsc.gov.in",
        "nta.ac.in",
        "physicswallah.live",
        "pw.live",
        "unacademy.com",
        "byjus.com",
        "duolingo.com",
        "wolframalpha.com",
        "britannica.com",
        "jstor.org",
        "researchgate.net",
        "arxiv.org",
        "scholar.google.com",
        "quizlet.com",
        "chegg.com"
    )

    private val _studyWebsitesState = MutableStateFlow<List<WebsiteEntry>>(emptyList())
    val studyWebsitesState: StateFlow<List<WebsiteEntry>> = _studyWebsitesState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        StudyChannelsManager.init(context)
        refreshWebsites(context)
    }

    /**
     * Checks if a YouTube channel is allowlisted as educational.
     */
    fun isStudyChannel(context: Context, channelName: String?): Boolean {
        if (channelName.isNullOrBlank()) return false
        val clean = channelName.trim().lowercase(Locale.ROOT)
        val channels = StudyChannelsManager.getChannels(context)
        return channels.any { clean.contains(it.name.lowercase(Locale.ROOT)) }
    }

    /**
     * Checks if a website or domain is recognized as an educational resource.
     */
    fun isStudyWebsite(context: Context, urlOrDomain: String?): Boolean {
        if (urlOrDomain.isNullOrBlank()) return false
        val domain = extractDomain(urlOrDomain) ?: return false
        val allowed = getEffectiveStudyWebsites(context)
        return allowed.any { domain == it || domain.endsWith(".$it") }
    }

    fun getEffectiveStudyWebsites(context: Context): Set<String> {
        val prefs = getPrefs(context)
        val custom = prefs.getStringSet(KEY_CUSTOM_WEBSITES, emptySet()).orEmpty()
        val removed = prefs.getStringSet(KEY_REMOVED_BUILTIN_WEBSITES, emptySet()).orEmpty()
        return (BUILTIN_STUDY_DOMAINS - removed) + custom
    }

    fun addCustomWebsite(context: Context, rawInput: String): Boolean {
        val domain = extractDomain(rawInput) ?: return false
        val prefs = getPrefs(context)
        val custom = prefs.getStringSet(KEY_CUSTOM_WEBSITES, emptySet()).orEmpty().toMutableSet()
        if (custom.contains(domain) || BUILTIN_STUDY_DOMAINS.contains(domain)) return false
        custom.add(domain)
        prefs.edit().putStringSet(KEY_CUSTOM_WEBSITES, custom).apply()
        refreshWebsites(context)
        Log.i(TAG, "Added custom study website: $domain")
        return true
    }

    fun removeWebsite(context: Context, entry: WebsiteEntry) {
        val prefs = getPrefs(context)
        if (entry.isCustom) {
            val custom = prefs.getStringSet(KEY_CUSTOM_WEBSITES, emptySet()).orEmpty().toMutableSet()
            custom.remove(entry.domain)
            prefs.edit().putStringSet(KEY_CUSTOM_WEBSITES, custom).apply()
        } else {
            val removed = prefs.getStringSet(KEY_REMOVED_BUILTIN_WEBSITES, emptySet()).orEmpty().toMutableSet()
            removed.add(entry.domain)
            prefs.edit().putStringSet(KEY_REMOVED_BUILTIN_WEBSITES, removed).apply()
        }
        refreshWebsites(context)
    }

    fun resetToDefaults(context: Context) {
        StudyChannelsManager.resetToDefaults(context)
        getPrefs(context).edit()
            .remove(KEY_CUSTOM_WEBSITES)
            .remove(KEY_REMOVED_BUILTIN_WEBSITES)
            .apply()
        refreshWebsites(context)
        Log.i(TAG, "Reset study allowlists to defaults.")
    }

    fun extractDomain(raw: String): String? {
        val trimmed = raw.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return null
        val url = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        return try {
            val host = Uri.parse(url).host ?: trimmed
            host.removePrefix("www.").lowercase(Locale.ROOT)
        } catch (_: Exception) {
            trimmed.removePrefix("www.").split("/").firstOrNull()
        }
    }

    private fun refreshWebsites(context: Context) {
        val prefs = getPrefs(context)
        val custom = prefs.getStringSet(KEY_CUSTOM_WEBSITES, emptySet()).orEmpty()
        val removed = prefs.getStringSet(KEY_REMOVED_BUILTIN_WEBSITES, emptySet()).orEmpty()
        val builtInEntries = BUILTIN_STUDY_DOMAINS
            .filter { it !in removed }
            .map { WebsiteEntry(it, false) }
        val customEntries = custom.map { WebsiteEntry(it, true) }
        _studyWebsitesState.value = (builtInEntries + customEntries).sortedBy { it.domain }
    }
}
