package com.example.util

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.ui.components.MascotPose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Timer Themes (v1.4.0 UI polish).
 *
 * A theme = an optional ambience backdrop image + a themed mascot pose +
 * matching progress-arc colors. Aurora (default) keeps the code-drawn
 * aurora canvas and must remain pixel-identical to v1.3.1.
 *
 * Data-driven: adding a theme = one entry in THEMES + its two assets.
 */
data class TimerTheme(
    val id: String,
    val name: String,
    @DrawableRes val backdropRes: Int?,
    val mascotPose: MascotPose,
    val arcColors: List<Color>,
    val glowColor: Color,
    /**
     * v1.4.0-round2: full-screen accent — primary buttons, active nav pill,
     * selected chips on the main screens. Aurora keeps RegainLimePrimary so
     * the default look is pixel-identical.
     */
    val accent: Color,
    /**
     * Round-4: points lock. Focus Points are earned 1 per focused minute
     * (FocusSessionEntity.focusPointsEarned). A theme becomes permanently
     * usable when the user's lifetime balance reaches this threshold.
     * 0 = free forever.
     */
    val unlockPoints: Int = 0
) {
    fun isUnlocked(userPoints: Int): Boolean = userPoints >= unlockPoints
}

object TimerThemeManager {
    private const val TAG = "TimerThemeManager"
    private const val PREFS_NAME = "focivo_timer_theme_prefs"
    private const val KEY_SELECTED_THEME = "selected_timer_theme_id"

    val THEMES: List<TimerTheme> = listOf(
        TimerTheme(
            id = "aurora",
            name = "Aurora",
            backdropRes = null, // code-drawn aurora canvas (unchanged from v1.3.1)
            mascotPose = MascotPose.STUDY_HAPPY, // round-3: happy default study face
            arcColors = emptyList(), // unused — AuroraTimer uses its built-in palette
            glowColor = Color.Transparent,
            accent = Color(0xFF8CE000),
            unlockPoints = 0
        ),
        TimerTheme(
            id = "forest",
            name = "Forest",
            backdropRes = R.drawable.theme_backdrop_forest,
            mascotPose = MascotPose.FOREST,
            arcColors = listOf(Color(0xFF66BB6A), Color(0xFFA5D6A7), Color(0xFF2E7D32)),
            glowColor = Color(0xFF66BB6A),
            accent = Color(0xFF66BB6A),
            unlockPoints = 0
        ),
        TimerTheme(
            id = "sunset",
            name = "Sunset",
            backdropRes = R.drawable.theme_backdrop_sunset,
            mascotPose = MascotPose.SUNSET,
            arcColors = listOf(Color(0xFFFF8A65), Color(0xFFFFB74D), Color(0xFFE64A19)),
            glowColor = Color(0xFFFF8A65),
            accent = Color(0xFFFF8A65),
            unlockPoints = 100
        ),
        TimerTheme(
            id = "sakura",
            name = "Sakura",
            backdropRes = R.drawable.theme_backdrop_sakura,
            mascotPose = MascotPose.SAKURA,
            arcColors = listOf(Color(0xFFF48FB1), Color(0xFFF8BBD0), Color(0xFFEC407A)),
            glowColor = Color(0xFFF48FB1),
            accent = Color(0xFFF48FB1),
            unlockPoints = 250
        ),
        TimerTheme(
            id = "midnight",
            name = "Midnight",
            backdropRes = R.drawable.theme_backdrop_midnight,
            mascotPose = MascotPose.MIDNIGHT,
            arcColors = listOf(Color(0xFFB39DDB), Color(0xFF9575CD), Color(0xFF7E57C2)),
            glowColor = Color(0xFF9575CD),
            accent = Color(0xFF9575CD),
            unlockPoints = 500
        ),
        TimerTheme(
            id = "desert",
            name = "Desert",
            backdropRes = R.drawable.theme_backdrop_desert,
            mascotPose = MascotPose.DESERT,
            arcColors = listOf(Color(0xFFFFD54F), Color(0xFFFFE082), Color(0xFFFF8F00)),
            glowColor = Color(0xFFFFD54F),
            accent = Color(0xFFFFD54F),
            unlockPoints = 750
        ),
        TimerTheme(
            id = "royal",
            name = "Royal",
            backdropRes = R.drawable.theme_backdrop_royal,
            mascotPose = MascotPose.ROYAL,
            arcColors = listOf(Color(0xFFFFC107), Color(0xFFFFECB3), Color(0xFFFFA000)),
            glowColor = Color(0xFFFFC107),
            accent = Color(0xFFFFC107),
            unlockPoints = 1000
        ),
        TimerTheme(
            id = "crimson",
            name = "Crimson",
            backdropRes = null, // resolved at runtime: theme_backdrop_crimson
            mascotPose = MascotPose.CRIMSON,
            arcColors = listOf(Color(0xFFFF5252), Color(0xFFFF8A80), Color(0xFFC62828)),
            glowColor = Color(0xFFFF5252),
            accent = Color(0xFFFF5252),
            unlockPoints = 1500
        ),
        TimerTheme(
            id = "lavender",
            name = "Lavender",
            backdropRes = null, // resolved at runtime: theme_backdrop_lavender
            mascotPose = MascotPose.LAVENDER,
            arcColors = listOf(Color(0xFFCE93D8), Color(0xFFE1BEE7), Color(0xFFAB47BC)),
            glowColor = Color(0xFFCE93D8),
            accent = Color(0xFFCE93D8),
            unlockPoints = 2000
        ),
        TimerTheme(
            id = "amber",
            name = "Amber",
            backdropRes = null, // resolved at runtime: theme_backdrop_amber
            mascotPose = MascotPose.AMBER,
            arcColors = listOf(Color(0xFFFFB300), Color(0xFFFFD54F), Color(0xFFFF8F00)),
            glowColor = Color(0xFFFFB300),
            accent = Color(0xFFFFB300),
            unlockPoints = 2500
        ),
        TimerTheme(
            id = "mint",
            name = "Mint",
            backdropRes = null, // resolved at runtime: theme_backdrop_mint
            mascotPose = MascotPose.MINT,
            arcColors = listOf(Color(0xFF69F0AE), Color(0xFFB9F6CA), Color(0xFF00C853)),
            glowColor = Color(0xFF69F0AE),
            accent = Color(0xFF69F0AE),
            unlockPoints = 0
        )
    )

    private val _selectedTheme = MutableStateFlow<TimerTheme>(THEMES[0])
    val selectedTheme: StateFlow<TimerTheme> = _selectedTheme.asStateFlow()

    fun init(context: Context) {
        val id = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_THEME, "aurora")
        _selectedTheme.value = getTheme(id)
    }

    fun getTheme(id: String?): TimerTheme =
        THEMES.find { it.id == id } ?: THEMES[0]

    fun selectTheme(context: Context, id: String) {
        val theme = getTheme(id)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED_THEME, theme.id).apply()
        _selectedTheme.value = theme
        Log.i(TAG, "Timer theme selected: ${theme.name}")
    }

    /**
     * Round-4: guarded selection. Returns true and applies the theme only
     * when the user's Focus Points balance meets the theme's threshold.
     * Returns false (and changes nothing) when locked.
     */
    fun trySelectTheme(context: Context, id: String, userPoints: Int): Boolean {
        val theme = getTheme(id)
        if (!theme.isUnlocked(userPoints)) {
            Log.i(TAG, "Theme ${theme.name} is locked (${userPoints}/${theme.unlockPoints} pts)")
            return false
        }
        selectTheme(context, id)
        return true
    }

    /**
     * Round-4: call when the balance is (re)loaded. If the persisted theme is
     * no longer affordable (rules changed), fall back to Aurora gracefully.
     */
    fun validateSelection(context: Context, userPoints: Int) {
        if (!_selectedTheme.value.isUnlocked(userPoints)) {
            selectTheme(context, "aurora")
        }
    }
}
