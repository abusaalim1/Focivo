package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.3.3 (ITEM 5 — Study-page guard control cluster): durable store for the
 * screen-time budget settings' GLOBAL daily limit.
 *
 * Coordination contract (HANDOFF — screen-time budget settings editor, built
 * by another worker in this round):
 * - Route / screen id: "screen_time_budgets" ([SCREEN_TIME_BUDGETS_ROUTE_ID]).
 * - Entry point: [com.example.ui.screens.ScreenTimeBudgetsScreen], already
 *   wired in MainActivity (isScreenTimeBudgetsOpen) and reachable from the
 *   Study screen's guard control cluster ("Limits" icon).
 * - This store owns the GLOBAL daily limit (minutes + enabled flag) — the
 *   other worker's full editor (per-app limits + global limit UI) should
 *   drive THIS store for the global limit rather than creating parallel
 *   prefs. Per-app limits continue to live in AppDailyLimitsManager /
 *   FocuslyRepository (AppLimitsScreen).
 *
 * No parallel state: every reader/writer goes through these functions and
 * the StateFlows below.
 */
object ScreenTimeBudgetStore {

    /** Navigation/screen id for the screen-time budget settings destination. */
    const val SCREEN_TIME_BUDGETS_ROUTE_ID = "screen_time_budgets"

    private const val PREFS_NAME = "focivo_screen_time_budget_prefs"
    private const val KEY_GLOBAL_LIMIT_ENABLED = "key_global_daily_limit_enabled"
    private const val KEY_GLOBAL_LIMIT_MINUTES = "key_global_daily_limit_minutes"

    /** 0 = no global limit set. */
    const val NO_LIMIT_MINUTES = 0

    /** Hard clamp: a "daily" limit can never exceed one full day. */
    const val MAX_LIMIT_MINUTES = 24 * 60

    private val _globalLimitEnabledState = MutableStateFlow(false)
    val globalLimitEnabledState: StateFlow<Boolean> = _globalLimitEnabledState.asStateFlow()

    private val _globalLimitMinutesState = MutableStateFlow(NO_LIMIT_MINUTES)
    val globalLimitMinutesState: StateFlow<Int> = _globalLimitMinutesState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        val prefs = getPrefs(context)
        _globalLimitEnabledState.value = prefs.getBoolean(KEY_GLOBAL_LIMIT_ENABLED, false)
        _globalLimitMinutesState.value =
            prefs.getInt(KEY_GLOBAL_LIMIT_MINUTES, NO_LIMIT_MINUTES).coerceIn(NO_LIMIT_MINUTES, MAX_LIMIT_MINUTES)
    }

    fun isGlobalLimitEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_GLOBAL_LIMIT_ENABLED, false)
    }

    fun setGlobalLimitEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_GLOBAL_LIMIT_ENABLED, enabled).apply()
        _globalLimitEnabledState.value = enabled
    }

    /**
     * The global daily screen-time limit in minutes (0 = not set).
     * Out-of-range values are coerced to [NO_LIMIT_MINUTES]..[MAX_LIMIT_MINUTES].
     */
    fun getGlobalLimitMinutes(context: Context): Int {
        return getPrefs(context).getInt(KEY_GLOBAL_LIMIT_MINUTES, NO_LIMIT_MINUTES)
            .coerceIn(NO_LIMIT_MINUTES, MAX_LIMIT_MINUTES)
    }

    fun setGlobalLimitMinutes(context: Context, minutes: Int) {
        val coerced = minutes.coerceIn(NO_LIMIT_MINUTES, MAX_LIMIT_MINUTES)
        getPrefs(context).edit().putInt(KEY_GLOBAL_LIMIT_MINUTES, coerced).apply()
        _globalLimitMinutesState.value = coerced
    }
}
