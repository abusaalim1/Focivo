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
    val accent: Color
)

object TimerThemeManager {
    private const val TAG = "TimerThemeManager"
    private const val PREFS_NAME = "focivo_timer_theme_prefs"
    private const val KEY_SELECTED_THEME = "selected_timer_theme_id"

    val THEMES: List<TimerTheme> = listOf(
        TimerTheme(
            id = "aurora",
            name = "Aurora",
            backdropRes = null, // code-drawn aurora canvas (unchanged from v1.3.1)
            mascotPose = MascotPose.STUDY_POSE,
            arcColors = emptyList(), // unused — AuroraTimer uses its built-in palette
            glowColor = Color.Transparent,
            accent = Color(0xFF8CE000)
        ),
        TimerTheme(
            id = "forest",
            name = "Forest",
            backdropRes = R.drawable.theme_backdrop_forest,
            mascotPose = MascotPose.FOREST,
            arcColors = listOf(Color(0xFF66BB6A), Color(0xFFA5D6A7), Color(0xFF2E7D32)),
            glowColor = Color(0xFF66BB6A),
            accent = Color(0xFF66BB6A)
        ),
        TimerTheme(
            id = "sunset",
            name = "Sunset",
            backdropRes = R.drawable.theme_backdrop_sunset,
            mascotPose = MascotPose.SUNSET,
            arcColors = listOf(Color(0xFFFF8A65), Color(0xFFFFB74D), Color(0xFFE64A19)),
            glowColor = Color(0xFFFF8A65),
            accent = Color(0xFFFF8A65)
        ),
        TimerTheme(
            id = "sakura",
            name = "Sakura",
            backdropRes = R.drawable.theme_backdrop_sakura,
            mascotPose = MascotPose.SAKURA,
            arcColors = listOf(Color(0xFFF48FB1), Color(0xFFF8BBD0), Color(0xFFEC407A)),
            glowColor = Color(0xFFF48FB1),
            accent = Color(0xFFF48FB1)
        ),
        TimerTheme(
            id = "midnight",
            name = "Midnight",
            backdropRes = R.drawable.theme_backdrop_midnight,
            mascotPose = MascotPose.MIDNIGHT,
            arcColors = listOf(Color(0xFFB39DDB), Color(0xFF9575CD), Color(0xFF7E57C2)),
            glowColor = Color(0xFF9575CD),
            accent = Color(0xFF9575CD)
        ),
        TimerTheme(
            id = "desert",
            name = "Desert",
            backdropRes = R.drawable.theme_backdrop_desert,
            mascotPose = MascotPose.DESERT,
            arcColors = listOf(Color(0xFFFFD54F), Color(0xFFFFE082), Color(0xFFFF8F00)),
            glowColor = Color(0xFFFFD54F),
            accent = Color(0xFFFFD54F)
        ),
        TimerTheme(
            id = "royal",
            name = "Royal",
            backdropRes = R.drawable.theme_backdrop_royal,
            mascotPose = MascotPose.ROYAL,
            arcColors = listOf(Color(0xFFFFC107), Color(0xFFFFECB3), Color(0xFFFFA000)),
            glowColor = Color(0xFFFFC107),
            accent = Color(0xFFFFC107)
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
}
