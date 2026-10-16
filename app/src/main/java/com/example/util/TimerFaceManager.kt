package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.1 — switchable timer face templates (Apple/Notion minimal aesthetic).
 *
 * Lock model mirrors the theme unlocks exactly: threshold-based PERMANENT
 * unlock with Focus Points, NO spending, same [UserPreferencesEntity.focusPoints]
 * source of truth. Ring + Minimal Digits are free; the rest sit on the
 * theme price ladder.
 */
data class TimerFace(
    val id: String,
    val name: String,
    val tagline: String,
    val unlockPoints: Int = 0
) {
    // Content lock model (points never spent): ALL_CONTENT_UNLOCKED frees every timer face.
    fun isUnlocked(userPoints: Int): Boolean =
        ALL_CONTENT_UNLOCKED || userPoints >= unlockPoints
}

object TimerFaceManager {
    private const val TAG = "TimerFaceManager"
    private const val PREFS_NAME = "focusly_timer_face"
    private const val KEY_SELECTED_FACE = "selected_face_id"

    val FACES = listOf(
        TimerFace(
            id = "ring",
            name = "Aurora Ring",
            tagline = "The classic glowing ring",
            unlockPoints = 0
        ),
        TimerFace(
            id = "digits",
            name = "Minimal Digits",
            tagline = "Huge type, no ring",
            unlockPoints = 0
        ),
        TimerFace(
            id = "glass",
            name = "Glass Card",
            tagline = "Frosted centered card",
            unlockPoints = 500
        ),
        TimerFace(
            id = "arc",
            name = "Half Arc",
            tagline = "Half-donut progress arc",
            unlockPoints = 1500
        ),
        TimerFace(
            id = "editorial",
            name = "Editorial",
            tagline = "Oversized numerals, thin line",
            unlockPoints = 3500
        ),
        // v1.2.3 — nine new clock styles (FocivoClock). Ids are prefixed
        // because "editorial" is already taken above. Locks follow the
        // same threshold model: Liquid 150, Breathe 200, Horizon 250.
        TimerFace(
            id = "clock_orbit",
            name = "Orbit",
            tagline = "Thin ring, glowing orbiting dot",
            unlockPoints = 500
        ),
        TimerFace(
            id = "clock_flip",
            name = "Flip",
            tagline = "Soft flip-clock cards",
            unlockPoints = 1500
        ),
        TimerFace(
            id = "clock_liquid",
            name = "Liquid",
            tagline = "Water level slowly drains",
            unlockPoints = 150
        ),
        TimerFace(
            id = "clock_editorial",
            name = "Editorial Clock",
            tagline = "Huge serif, hairline progress",
            unlockPoints = 7000
        ),
        TimerFace(
            id = "clock_dial",
            name = "Dial",
            tagline = "Sweeping dial with live wedge",
            unlockPoints = 2500
        ),
        TimerFace(
            id = "clock_horizon",
            name = "Horizon",
            tagline = "Sun travels across a calm arc",
            unlockPoints = 250
        ),
        TimerFace(
            id = "clock_stack",
            name = "Stack",
            tagline = "Minutes over seconds, vertical meter",
            unlockPoints = 3500
        ),
        TimerFace(
            id = "clock_breathe",
            name = "Breathe",
            tagline = "Soft glowing orb that breathes",
            unlockPoints = 200
        ),
        TimerFace(
            id = "clock_bars",
            name = "Bars",
            tagline = "Quiet equalizer bars fill up",
            unlockPoints = 5000
        )
    )

    private val _selectedFace = MutableStateFlow<TimerFace>(FACES[0])
    val selectedFace: StateFlow<TimerFace> = _selectedFace.asStateFlow()

    fun init(context: Context) {
        val id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_FACE, "ring")
        _selectedFace.value = getFace(id)
    }

    fun getFace(id: String?): TimerFace =
        FACES.find { it.id == id } ?: FACES[0]

    fun selectFace(context: Context, id: String) {
        val face = getFace(id)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED_FACE, face.id).apply()
        _selectedFace.value = face
        Log.i(TAG, "Timer face selected: ${face.name}")
    }

    /**
     * Guarded selection — applies only when the user's Focus Points balance
     * meets the face's threshold. Returns false (changes nothing) when locked.
     */
    fun trySelectFace(context: Context, id: String, userPoints: Int): Boolean {
        val face = getFace(id)
        if (!face.isUnlocked(userPoints)) {
            Log.i(TAG, "Face ${face.name} is locked ($userPoints/${face.unlockPoints} pts)")
            return false
        }
        selectFace(context, id)
        return true
    }

    /**
     * Automatic fallback: if the persisted face is somehow locked
     * (rules changed), fall back to the free Aurora Ring.
     */
    fun validateSelection(context: Context, userPoints: Int) {
        if (!_selectedFace.value.isUnlocked(userPoints)) {
            selectFace(context, "ring")
        }
    }
}
