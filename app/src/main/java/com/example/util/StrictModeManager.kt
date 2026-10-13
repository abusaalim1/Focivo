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
    // v1.3.0: multi-window protections
    private const val KEY_BLOCK_SPLIT_SCREEN = "key_block_split_screen"
    private const val KEY_BLOCK_FLOATING_WINDOW = "key_block_floating_window"
    // v1.3.1: Deep Focus Settings sheet (Regain parity, all free)
    private const val KEY_UNINSTALL_PROTECTION = "key_uninstall_protection"
    private const val KEY_BLOCK_HOME_SCREEN = "key_block_home_screen"

    private val _isStrictModeState = MutableStateFlow(true)
    val isStrictModeState: StateFlow<Boolean> = _isStrictModeState.asStateFlow()

    private val _blockSplitScreenState = MutableStateFlow(false)
    val blockSplitScreenState: StateFlow<Boolean> = _blockSplitScreenState.asStateFlow()

    private val _blockFloatingWindowState = MutableStateFlow(false)
    val blockFloatingWindowState: StateFlow<Boolean> = _blockFloatingWindowState.asStateFlow()

    private val _uninstallProtectionState = MutableStateFlow(true)
    val uninstallProtectionState: StateFlow<Boolean> = _uninstallProtectionState.asStateFlow()

    private val _blockHomeScreenState = MutableStateFlow(false)
    val blockHomeScreenState: StateFlow<Boolean> = _blockHomeScreenState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        val enabled = getPrefs(context).getBoolean(KEY_STRICT_MODE_ENABLED, true)
        _isStrictModeState.value = enabled
        _blockSplitScreenState.value = getPrefs(context).getBoolean(KEY_BLOCK_SPLIT_SCREEN, false)
        _blockFloatingWindowState.value = getPrefs(context).getBoolean(KEY_BLOCK_FLOATING_WINDOW, false)
        _uninstallProtectionState.value = getPrefs(context).getBoolean(KEY_UNINSTALL_PROTECTION, true)
        _blockHomeScreenState.value = getPrefs(context).getBoolean(KEY_BLOCK_HOME_SCREEN, false)
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

    // v1.3.0: multi-window protections (default off; user opts in from Blocks)
    fun isBlockSplitScreenEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BLOCK_SPLIT_SCREEN, false)
    }

    fun setBlockSplitScreenEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BLOCK_SPLIT_SCREEN, enabled).apply()
        _blockSplitScreenState.value = enabled
    }

    fun isBlockFloatingWindowEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BLOCK_FLOATING_WINDOW, false)
    }

    fun setBlockFloatingWindowEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BLOCK_FLOATING_WINDOW, enabled).apply()
        _blockFloatingWindowState.value = enabled
    }

    // v1.3.1: Deep Focus Settings sheet toggles.

    /** Uninstall protection during focus sessions (default on). */
    fun isUninstallProtectionEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_UNINSTALL_PROTECTION, true)
    }

    fun setUninstallProtectionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_UNINSTALL_PROTECTION, enabled).apply()
        _uninstallProtectionState.value = enabled
    }

    /**
     * Block phone home screen during deep-focus sessions (default off — aggressive, opt-in).
     * Best-effort: the OS Home key itself can't be intercepted; returning to the
     * launcher while a deep-focus session runs shows the block overlay instead.
     */
    fun isBlockHomeScreenEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BLOCK_HOME_SCREEN, false)
    }

    fun setBlockHomeScreenEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BLOCK_HOME_SCREEN, enabled).apply()
        _blockHomeScreenState.value = enabled
    }

    /**
     * Determines whether uninstallation, settings opening, and package management
     * must be strictly guarded right now.
     */
    fun shouldBlockUninstallation(context: Context): Boolean {
        if (!isStrictModeEnabled(context) && !isUninstallProtectionEnabled(context)) return false
        // Only block uninstallation when an active scheduled focus session or manual study timer is genuinely running
        return AiStudyGuardManager.isStudyPeriodActive(context)
    }
}
