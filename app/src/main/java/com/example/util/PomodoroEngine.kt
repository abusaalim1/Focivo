package com.example.util

import android.content.Context
import android.util.Log

/**
 * v1.1 — Pomodoro cycle engine (data + pure transitions + persistence).
 *
 * The phase machine lives in [FocuslyViewModel]; this file holds the pure
 * parts so they are unit-testable without Android:
 * - [PomodoroConfig]: durations + cycles (persisted, no new backend)
 * - [PomodoroPhase] / [PomodoroState]
 * - [nextPomodoroPhase]: the pure transition function
 *
 * Accounting rules (enforced in the ViewModel):
 * - ONLY Focus phases record sessions / earn points — exactly like a single
 *   focus session today. Break phases record nothing.
 * - Phase notifications reuse the existing system: Session Complete fires for
 *   focus phases, Break Over fires when a break ends.
 * - Stopping mid-pomodoro discards like finishing a session early today.
 */
data class PomodoroConfig(
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val cycles: Int,
    val presetId: String? = null
)

enum class PomodoroPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

data class PomodoroState(
    val config: PomodoroConfig,
    val phase: PomodoroPhase,
    /** Number of focus phases fully completed in this run. */
    val focusCompleted: Int = 0
)

/**
 * Pure phase transition. Returns the next state, or null when the pomodoro
 * is complete (long break finished).
 */
fun nextPomodoroPhase(state: PomodoroState): PomodoroState? {
    return when (state.phase) {
        PomodoroPhase.FOCUS -> {
            val done = state.focusCompleted + 1
            if (done >= state.config.cycles.coerceAtLeast(1)) {
                state.copy(phase = PomodoroPhase.LONG_BREAK, focusCompleted = done)
            } else {
                state.copy(phase = PomodoroPhase.SHORT_BREAK, focusCompleted = done)
            }
        }
        PomodoroPhase.SHORT_BREAK -> state.copy(phase = PomodoroPhase.FOCUS)
        PomodoroPhase.LONG_BREAK -> null
    }
}

fun PomodoroState.phaseMinutes(): Int = when (phase) {
    PomodoroPhase.FOCUS -> config.focusMinutes
    PomodoroPhase.SHORT_BREAK -> config.shortBreakMinutes
    PomodoroPhase.LONG_BREAK -> config.longBreakMinutes
}

/** Mode label driving the timer's break presentation (contains "break"). */
fun PomodoroState.phaseModeLabel(): String = when (phase) {
    PomodoroPhase.FOCUS -> "Focus"
    PomodoroPhase.SHORT_BREAK -> "Short Break"
    PomodoroPhase.LONG_BREAK -> "Long Break"
}

fun PomodoroState.phaseDisplayLabel(): String = when (phase) {
    PomodoroPhase.FOCUS -> "FOCUS ${focusCompleted + 1}/${config.cycles}"
    PomodoroPhase.SHORT_BREAK -> "SHORT BREAK"
    PomodoroPhase.LONG_BREAK -> "LONG BREAK"
}

// ---------------------------------------------------------------------------
// Persistence — existing SharedPreferences mechanism (focusly_local_data),
// no new backend, no new tables.
// ---------------------------------------------------------------------------
private const val PREFS = "focusly_local_data"
private const val KEY_FOCUS_MINS = "pomodoro_focus_mins"
private const val KEY_SHORT_MINS = "pomodoro_short_break_mins"
private const val KEY_LONG_MINS = "pomodoro_long_break_mins"
private const val KEY_CYCLES = "pomodoro_cycles"
private const val KEY_PRESET_ID = "pomodoro_last_preset_id"

fun savePomodoroConfig(context: Context, config: PomodoroConfig) {
    try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_FOCUS_MINS, config.focusMinutes)
            .putInt(KEY_SHORT_MINS, config.shortBreakMinutes)
            .putInt(KEY_LONG_MINS, config.longBreakMinutes)
            .putInt(KEY_CYCLES, config.cycles)
            .putString(KEY_PRESET_ID, config.presetId)
            .apply()
    } catch (e: Exception) {
        Log.e("PomodoroEngine", "savePomodoroConfig failed: ${e.message}")
    }
}

fun loadPomodoroConfig(context: Context): PomodoroConfig {
    return try {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        PomodoroConfig(
            focusMinutes = p.getInt(KEY_FOCUS_MINS, 25).coerceIn(1, 480),
            shortBreakMinutes = p.getInt(KEY_SHORT_MINS, 5).coerceIn(1, 120),
            longBreakMinutes = p.getInt(KEY_LONG_MINS, 15).coerceIn(1, 180),
            cycles = p.getInt(KEY_CYCLES, 4).coerceIn(2, 12),
            presetId = p.getString(KEY_PRESET_ID, null)
        )
    } catch (_: Exception) {
        PomodoroConfig(25, 5, 15, 4, null)
    }
}

/** Last-used preset id (for the showcase's selected highlight). */
fun loadLastPomodoroPresetId(context: Context): String? {
    return try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PRESET_ID, null)
    } catch (_: Exception) {
        null
    }
}

// ---------------------------------------------------------------------------
// Active-run survival — so a process death mid-pomodoro can't corrupt the
// accounting (e.g. a break phase must never record as a study session).
// The state is cleared when the run completes or is stopped.
// ---------------------------------------------------------------------------
private const val KEY_ACTIVE_PHASE = "pomodoro_active_phase"
private const val KEY_ACTIVE_FOCUS_DONE = "pomodoro_active_focus_done"
private const val KEY_ACTIVE_CFG = "pomodoro_active_cfg"

fun saveActivePomodoroState(context: Context, state: PomodoroState?) {
    try {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (state == null) {
            e.remove(KEY_ACTIVE_PHASE).remove(KEY_ACTIVE_FOCUS_DONE).remove(KEY_ACTIVE_CFG)
        } else {
            val c = state.config
            e.putString(KEY_ACTIVE_PHASE, state.phase.name)
            e.putInt(KEY_ACTIVE_FOCUS_DONE, state.focusCompleted)
            e.putString(
                KEY_ACTIVE_CFG,
                listOf(c.focusMinutes, c.shortBreakMinutes, c.longBreakMinutes, c.cycles).joinToString(",") +
                    "|" + (c.presetId ?: "")
            )
        }
        e.apply()
    } catch (ex: Exception) {
        Log.e("PomodoroEngine", "saveActivePomodoroState failed: ${ex.message}")
    }
}

fun loadActivePomodoroState(context: Context): PomodoroState? {
    return try {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val phaseName = p.getString(KEY_ACTIVE_PHASE, null) ?: return null
        val cfgRaw = p.getString(KEY_ACTIVE_CFG, null) ?: return null
        val parts = cfgRaw.split("|")
        val nums = parts[0].split(",").mapNotNull { it.toIntOrNull() }
        if (nums.size != 4) return null
        val phase = PomodoroPhase.valueOf(phaseName)
        PomodoroState(
            config = PomodoroConfig(
                focusMinutes = nums[0].coerceIn(1, 480),
                shortBreakMinutes = nums[1].coerceIn(1, 120),
                longBreakMinutes = nums[2].coerceIn(1, 180),
                cycles = nums[3].coerceIn(2, 12),
                presetId = parts.getOrNull(1)?.ifBlank { null }
            ),
            phase = phase,
            focusCompleted = p.getInt(KEY_ACTIVE_FOCUS_DONE, 0).coerceAtLeast(0)
        )
    } catch (_: Exception) {
        null
    }
}
