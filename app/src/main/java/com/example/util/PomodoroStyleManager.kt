package com.example.util

import android.content.Context
import android.util.Log
import com.example.ui.timer.PomodoroStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.2.3+ — "Pomodoro design" picker persistence (Customize Timer sheet).
 *
 * v1.2.4 laddered the four designs: every design has an unlock cost
 * (ContentLocks), so the picker must gate selection behind the same
 * point-ladder used by timer faces. The selection is stored as the
 * [PomodoroStyle] enum name in SharedPreferences, exactly like the timer
 * face id. TICK_RING is the default.
 */
object PomodoroStyleManager {
    private const val TAG = "PomodoroStyleManager"
    private const val PREFS_NAME = "focusly_pomodoro_style"
    private const val KEY_STYLE = "pomodoro_design"

    private val _selectedStyle = MutableStateFlow(PomodoroStyle.TICK_RING)
    val selectedStyle: StateFlow<PomodoroStyle> = _selectedStyle.asStateFlow()

    fun init(context: Context) {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, PomodoroStyle.TICK_RING.name)
        _selectedStyle.value = getStyle(name)
    }

    fun getStyle(name: String?): PomodoroStyle =
        try {
            if (name == null) PomodoroStyle.TICK_RING
            else PomodoroStyle.valueOf(name)
        } catch (_: IllegalArgumentException) {
            PomodoroStyle.TICK_RING
        }

    fun selectStyle(context: Context, style: PomodoroStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_STYLE, style.name).apply()
        _selectedStyle.value = style
        Log.i(TAG, "Pomodoro design selected: ${style.label}")
    }

    /**
     * v1.2.4 — guarded selection. Returns true and applies only when the
     * design is unlocked for the user's Focus Points (or the admin account).
     * Returns false (changes nothing) when locked.
     */
    fun trySelectStyle(
        context: Context,
        style: PomodoroStyle,
        userPoints: Int,
        isAdmin: Boolean = com.example.util.ContentLocks.isAdminUnlocked(context)
    ): Boolean {
        if (!style.isUnlocked(userPoints, isAdmin)) {
            Log.i(TAG, "Pomodoro design ${style.label} is locked ($userPoints/${style.unlockPoints} pts)")
            return false
        }
        selectStyle(context, style)
        return true
    }

    /**
     * v1.2.4 — call when the balance is (re)loaded. If the persisted design
     * is no longer unlocked (rules changed), fall back to Tick Ring.
     */
    fun validateSelection(
        context: Context,
        userPoints: Int,
        isAdmin: Boolean = com.example.util.ContentLocks.isAdminUnlocked(context)
    ) {
        if (!_selectedStyle.value.isUnlocked(userPoints, isAdmin)) {
            selectStyle(context, PomodoroStyle.TICK_RING)
        }
    }
}
