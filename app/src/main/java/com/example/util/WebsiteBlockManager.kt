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
 * Website Blocking Engine (Feature 4).
 * Enforces domain-level distraction blocking across major browsers during active study sessions.
 */
object WebsiteBlockManager {
    private const val TAG = "WebsiteBlockManager"
    private const val PREFS_NAME = "focivo_website_block_prefs"
    private const val KEY_CUSTOM_BLOCKED = "custom_blocked_domains"
    private const val KEY_UNBLOCKED_BUILTIN = "unblocked_builtin_domains"

    data class BlockedDomainEntry(val domain: String, val isCustom: Boolean)

    // Standard high-distraction social, video entertainment, and gaming domains
    val DEFAULT_BLOCKED_DOMAINS: Set<String> = setOf(
        "instagram.com",
        "facebook.com",
        "twitter.com",
        "x.com",
        "tiktok.com",
        "snapchat.com",
        "reddit.com",
        "netflix.com",
        "primevideo.com",
        "hotstar.com",
        "twitch.tv",
        "pinterest.com",
        "discord.com",
        "roblox.com",
        "steampowered.com",
        "epicgames.com",
        "ign.com",
        "gamespot.com",
        "9gag.com",
        "buzzfeed.com",
        "tmz.com",
        "dailymail.co.uk",
        "pornhub.com",
        "xvideos.com",
        "xnxx.com"
    )

    private val _blockedDomainsState = MutableStateFlow<List<BlockedDomainEntry>>(emptyList())
    val blockedDomainsState: StateFlow<List<BlockedDomainEntry>> = _blockedDomainsState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        refresh(context)
    }

    /**
     * Checks if a URL or raw text contains a blocked domain during study sessions.
     * Guaranteed to respect study allowlist (StudyAllowlistManager overrides).
     */
    fun isBlockedDomain(context: Context, rawText: String?): Boolean {
        if (rawText.isNullOrBlank()) return false
        val domain = extractDomain(rawText) ?: return false

        // Educational allowlist overrides any block rule
        if (StudyAllowlistManager.isStudyWebsite(context, domain)) {
            return false
        }

        val effectiveBlocked = getEffectiveBlockedDomains(context)
        return effectiveBlocked.any { domain == it || domain.endsWith(".$it") }
    }

    fun getEffectiveBlockedDomains(context: Context): Set<String> {
        val prefs = getPrefs(context)
        val custom = prefs.getStringSet(KEY_CUSTOM_BLOCKED, emptySet()).orEmpty()
        val unblocked = prefs.getStringSet(KEY_UNBLOCKED_BUILTIN, emptySet()).orEmpty()
        return (DEFAULT_BLOCKED_DOMAINS - unblocked) + custom
    }

    fun addCustomBlockedDomain(context: Context, rawInput: String): Boolean {
        val domain = extractDomain(rawInput) ?: return false
        val prefs = getPrefs(context)
        val custom = prefs.getStringSet(KEY_CUSTOM_BLOCKED, emptySet()).orEmpty().toMutableSet()
        if (custom.contains(domain) || DEFAULT_BLOCKED_DOMAINS.contains(domain)) return false
        custom.add(domain)
        prefs.edit().putStringSet(KEY_CUSTOM_BLOCKED, custom).apply()
        refresh(context)
        Log.i(TAG, "Added custom blocked website: $domain")
        return true
    }

    fun removeBlockedDomain(context: Context, entry: BlockedDomainEntry) {
        val prefs = getPrefs(context)
        if (entry.isCustom) {
            val custom = prefs.getStringSet(KEY_CUSTOM_BLOCKED, emptySet()).orEmpty().toMutableSet()
            custom.remove(entry.domain)
            prefs.edit().putStringSet(KEY_CUSTOM_BLOCKED, custom).apply()
        } else {
            val unblocked = prefs.getStringSet(KEY_UNBLOCKED_BUILTIN, emptySet()).orEmpty().toMutableSet()
            unblocked.add(entry.domain)
            prefs.edit().putStringSet(KEY_UNBLOCKED_BUILTIN, unblocked).apply()
        }
        refresh(context)
    }

    fun resetToDefaults(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_CUSTOM_BLOCKED)
            .remove(KEY_UNBLOCKED_BUILTIN)
            .apply()
        refresh(context)
        Log.i(TAG, "Reset blocked websites to default set.")
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

    private fun refresh(context: Context) {
        val prefs = getPrefs(context)
        val custom = prefs.getStringSet(KEY_CUSTOM_BLOCKED, emptySet()).orEmpty()
        val unblocked = prefs.getStringSet(KEY_UNBLOCKED_BUILTIN, emptySet()).orEmpty()
        val builtIn = DEFAULT_BLOCKED_DOMAINS
            .filter { it !in unblocked }
            .map { BlockedDomainEntry(it, false) }
        val customEntries = custom.map { BlockedDomainEntry(it, true) }
        _blockedDomainsState.value = (builtIn + customEntries).sortedBy { it.domain }
    }
}
