package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.R

/**
 * v1.2.3 (Task 11) — alarm-tone picker for the timer-end alarm (owner order
 * 2026-10-16).
 *
 * The first three tones are the owner's own supplied MP3s; the other five
 * are original tones synthesized for Focivo (CC0, no third-party source).
 * The old Kenney `timer_end_alarm` is retired — this list replaces it.
 *
 * Selection persists in SharedPreferences; [SoundFx.playTimerEnd] reads the
 * selected tone so the alarm plays it even when the UI is dead.
 */
object AlarmToneManager {

    data class AlarmTone(
        val id: String,
        val displayName: String,
        val resId: Int
    )

    val TONES: List<AlarmTone> = listOf(
        // Owner-supplied (~/workspace/user/files, Oct 2026)
        AlarmTone("classic_clock", "Classic Alarm Clock", R.raw.alarm_classic_clock),
        AlarmTone("funny_alarm", "Funny Alarm", R.raw.alarm_funny_alarm),
        AlarmTone("digital_alarm", "Digital Alarm", R.raw.alarm_digital_alarm),
        // Original Focivo syntheses (CC0 — generated for this app, no source)
        AlarmTone("bell_classic", "Classic Bell", R.raw.alarm_bell_classic),
        AlarmTone("digital_beep", "Digital Beep", R.raw.alarm_digital_beep),
        AlarmTone("gentle_chime", "Gentle Chime", R.raw.alarm_gentle_chime),
        AlarmTone("urgent_pulse", "Urgent Pulse", R.raw.alarm_urgent_pulse),
        AlarmTone("soft_wake", "Soft Wake", R.raw.alarm_soft_wake)
    )

    /** Default = first of the owner's files. */
    const val DEFAULT_TONE_ID = "classic_clock"

    private const val PREFS_NAME = "focivo_alarm_tone_prefs"
    private const val KEY_TONE_ID = "key_alarm_tone_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedId(context: Context): String {
        val id = getPrefs(context).getString(KEY_TONE_ID, DEFAULT_TONE_ID)
        return if (TONES.any { it.id == id }) id!! else DEFAULT_TONE_ID
    }

    fun setSelectedId(context: Context, id: String) {
        if (TONES.none { it.id == id }) return
        getPrefs(context).edit().putString(KEY_TONE_ID, id).apply()
    }

    fun getSelectedTone(context: Context): AlarmTone {
        val id = getSelectedId(context)
        return TONES.first { it.id == id }
    }

    fun toneById(id: String): AlarmTone? = TONES.firstOrNull { it.id == id }
}
