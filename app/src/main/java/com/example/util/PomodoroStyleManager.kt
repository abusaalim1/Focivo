package com.example.util

import android.content.Context
import android.util.Log
import com.example.ui.timer.PomodoroStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.2.3 — "Pomodoro design" picker persistence (Customize Timer sheet).
 *
 * All four designs are free (no locks). The selection is stored as the
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
}
