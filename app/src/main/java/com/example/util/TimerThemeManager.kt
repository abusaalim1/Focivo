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
/**
 * Ambient themes (v1.5.0): code-drawn living backdrops — see
 * [com.example.ui.components.AmbientBackdrop]. No drawable assets, so
 * these themes add zero bytes of image data and keep the APK small.
 */
enum class AmbientKind {
    SAKURA_DRIFT,
    FIREFLY_FOREST,
    SUNSET_DUNES,
    AURORA_VEIL,
    MONSOON_RAIN,
    EMBER_RISE
}

data class TimerTheme(
    val id: String,
    val name: String,
    @DrawableRes val backdropRes: Int?,
    /**
     * v1.5.0: when set, the backdrop is a live code-drawn canvas
     * (AmbientBackdrop) instead of an image/Aurora asset.
     * ThemedScreenBackground checks this FIRST.
     */
    val ambientKind: AmbientKind? = null,
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
    val unlockPoints: Int = 0,
    /**
     * v1.4.2: content color for text drawn directly on the full-bleed
     * backdrop (screen titles, section labels, top-of-screen text).
     * Dark scenes get light text; the light Aurora canvas keeps dark text.
     * Card-internal text is unaffected (cards are light surfaces).
     */
    val onBackdropContent: Color = Color(0xFFF7F8F4),
    /**
     * v1.3.2: override used when the app is in dark mode and this theme's
     * backdrop is ALSO dark in dark mode (the Aurora canvas: light in light
     * app mode, dark in dark app mode). Null = backdrop is the same in both
     * modes, so [onBackdropContent] is used everywhere.
     */
    val onBackdropContentDark: Color? = null
) {
    // Content lock model (points never spent): ALL_CONTENT_UNLOCKED frees every timer theme.
    // v1.2.4: isAdmin (owner admin account) frees everything — single choke point, no scattered conditionals.
    fun isUnlocked(userPoints: Int, isAdmin: Boolean = false): Boolean =
        isAdmin || ALL_CONTENT_UNLOCKED || userPoints >= unlockPoints

    /**
     * v1.3.2: backdrop-aware content color that is correct in BOTH app modes.
     * Fixes the Aurora theme rendering dark digits on its dark-mode canvas
     * (owner screenshot: "25:00" nearly invisible). Every caller drawing text
     * on a theme backdrop must use this, never [onBackdropContent] directly.
     */
    fun backdropContent(isAppDark: Boolean): Color =
        if (isAppDark) onBackdropContentDark ?: onBackdropContent else onBackdropContent
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
            arcColors = emptyList(), // unused — the aurora canvas is code-drawn
            glowColor = Color.Transparent,
            accent = Color(0xFF8CE000),
            unlockPoints = 0,
            onBackdropContent = Color(0xFF161A16), // light aurora canvas -> dark text
            // v1.3.2: the aurora canvas is DARK in dark app mode (AuroraBackground
            // dark gradient) — dark text there rendered "25:00" invisible.
            onBackdropContentDark = Color(0xFFF7F8F4)
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
            unlockPoints = 80
        ),
        TimerTheme(
            id = "sakura",
            name = "Sakura",
            backdropRes = R.drawable.theme_backdrop_sakura,
            mascotPose = MascotPose.SAKURA,
            arcColors = listOf(Color(0xFFF48FB1), Color(0xFFF8BBD0), Color(0xFFEC407A)),
            glowColor = Color(0xFFF48FB1),
            accent = Color(0xFFF48FB1),
            unlockPoints = 200
        ),
        TimerTheme(
            id = "midnight",
            name = "Midnight",
            backdropRes = R.drawable.theme_backdrop_midnight,
            mascotPose = MascotPose.MIDNIGHT,
            arcColors = listOf(Color(0xFFB39DDB), Color(0xFF9575CD), Color(0xFF7E57C2)),
            glowColor = Color(0xFF9575CD),
            accent = Color(0xFF9575CD),
            unlockPoints = 400
        ),
        TimerTheme(
            id = "desert",
            name = "Desert",
            backdropRes = R.drawable.theme_backdrop_desert,
            mascotPose = MascotPose.DESERT,
            arcColors = listOf(Color(0xFFFFD54F), Color(0xFFFFE082), Color(0xFFFF8F00)),
            glowColor = Color(0xFFFFD54F),
            accent = Color(0xFFFFD54F),
            unlockPoints = 600
        ),
        TimerTheme(
            id = "royal",
            name = "Royal",
            backdropRes = R.drawable.theme_backdrop_royal,
            mascotPose = MascotPose.ROYAL,
            arcColors = listOf(Color(0xFFFFC107), Color(0xFFFFECB3), Color(0xFFFFA000)),
            glowColor = Color(0xFFFFC107),
            accent = Color(0xFFFFC107),
            unlockPoints = 750
        ),
        TimerTheme(
            id = "crimson",
            name = "Crimson",
            backdropRes = null, // resolved at runtime: theme_backdrop_crimson
            mascotPose = MascotPose.CRIMSON,
            arcColors = listOf(Color(0xFFFF5252), Color(0xFFFF8A80), Color(0xFFC62828)),
            glowColor = Color(0xFFFF5252),
            accent = Color(0xFFFF5252),
            unlockPoints = 1000
        ),
        TimerTheme(
            id = "lavender",
            name = "Lavender",
            backdropRes = null, // resolved at runtime: theme_backdrop_lavender
            mascotPose = MascotPose.LAVENDER,
            arcColors = listOf(Color(0xFFCE93D8), Color(0xFFE1BEE7), Color(0xFFAB47BC)),
            glowColor = Color(0xFFCE93D8),
            accent = Color(0xFFCE93D8),
            unlockPoints = 1500
        ),
        TimerTheme(
            id = "amber",
            name = "Amber",
            backdropRes = null, // resolved at runtime: theme_backdrop_amber
            mascotPose = MascotPose.AMBER,
            arcColors = listOf(Color(0xFFFFB300), Color(0xFFFFD54F), Color(0xFFFF8F00)),
            glowColor = Color(0xFFFFB300),
            accent = Color(0xFFFFB300),
            unlockPoints = 1800
        ),
        TimerTheme(
            id = "mint",
            name = "Mint",
            backdropRes = null, // resolved at runtime: theme_backdrop_mint
            mascotPose = MascotPose.MINT,
            arcColors = listOf(Color(0xFF69F0AE), Color(0xFFB9F6CA), Color(0xFF00C853)),
            glowColor = Color(0xFF69F0AE),
            accent = Color(0xFF69F0AE),
            unlockPoints = 2000
        ),
        // Round-4b: 12 new distinct premium themes (runtime-resolved assets).
        TimerTheme(
            id = "lagoon",
            name = "Lagoon",
            backdropRes = null,
            mascotPose = MascotPose.LAGOON,
            arcColors = listOf(Color(0xFF4DD0E1), Color(0xFF80DEEA), Color(0xFF00ACC1)),
            glowColor = Color(0xFF4DD0E1),
            accent = Color(0xFF4DD0E1),
            unlockPoints = 2200
        ),
        TimerTheme(
            id = "rain",
            name = "Rain",
            backdropRes = null,
            mascotPose = MascotPose.RAIN,
            arcColors = listOf(Color(0xFF90A4AE), Color(0xFFB0BEC5), Color(0xFF607D8B)),
            glowColor = Color(0xFF90A4AE),
            accent = Color(0xFF90A4AE),
            unlockPoints = 2500
        ),
        TimerTheme(
            id = "bamboo",
            name = "Bamboo",
            backdropRes = null,
            mascotPose = MascotPose.BAMBOO,
            arcColors = listOf(Color(0xFF9CCC65), Color(0xFFC5E1A5), Color(0xFF689F38)),
            glowColor = Color(0xFF9CCC65),
            accent = Color(0xFF9CCC65),
            unlockPoints = 3000
        ),
        TimerTheme(
            id = "autumn",
            name = "Autumn",
            backdropRes = null,
            mascotPose = MascotPose.AUTUMN,
            arcColors = listOf(Color(0xFFFF7043), Color(0xFFFFAB91), Color(0xFFD84315)),
            glowColor = Color(0xFFFF7043),
            accent = Color(0xFFFF7043),
            unlockPoints = 3200
        ),
        TimerTheme(
            id = "coffee",
            name = "Coffee",
            backdropRes = null,
            mascotPose = MascotPose.COFFEE,
            arcColors = listOf(Color(0xFFA1887F), Color(0xFFD7CCC8), Color(0xFF795548)),
            glowColor = Color(0xFFA1887F),
            accent = Color(0xFFA1887F),
            unlockPoints = 3500
        ),
        TimerTheme(
            id = "winter",
            name = "Winter",
            backdropRes = null,
            mascotPose = MascotPose.WINTER,
            arcColors = listOf(Color(0xFF81D4FA), Color(0xFFB3E5FC), Color(0xFF039BE5)),
            glowColor = Color(0xFF81D4FA),
            accent = Color(0xFF81D4FA),
            unlockPoints = 4500
        ),
        TimerTheme(
            id = "rose",
            name = "Rose",
            backdropRes = null,
            mascotPose = MascotPose.ROSE,
            arcColors = listOf(Color(0xFFF06292), Color(0xFFF8BBD0), Color(0xFFC2185B)),
            glowColor = Color(0xFFF06292),
            accent = Color(0xFFF06292),
            unlockPoints = 5000
        ),
        TimerTheme(
            id = "ember",
            name = "Ember",
            backdropRes = null,
            mascotPose = MascotPose.EMBER,
            arcColors = listOf(Color(0xFFFF6E40), Color(0xFFFFAB91), Color(0xFFBF360C)),
            glowColor = Color(0xFFFF6E40),
            accent = Color(0xFFFF6E40),
            unlockPoints = 6000
        ),
        TimerTheme(
            id = "storm",
            name = "Storm",
            backdropRes = null,
            mascotPose = MascotPose.STORM,
            arcColors = listOf(Color(0xFF7E8BB8), Color(0xFFAEB9D6), Color(0xFF455182)),
            glowColor = Color(0xFF7E8BB8),
            accent = Color(0xFF7E8BB8),
            unlockPoints = 6500
        ),
        TimerTheme(
            id = "crystal",
            name = "Crystal",
            backdropRes = null,
            mascotPose = MascotPose.CRYSTAL,
            arcColors = listOf(Color(0xFF4FC3F7), Color(0xFFB3E5FC), Color(0xFF0288D1)),
            glowColor = Color(0xFF4FC3F7),
            accent = Color(0xFF4FC3F7),
            unlockPoints = 7500
        ),
        TimerTheme(
            id = "galaxy",
            name = "Galaxy",
            backdropRes = null,
            mascotPose = MascotPose.GALAXY,
            arcColors = listOf(Color(0xFFBA68C8), Color(0xFFE1BEE7), Color(0xFF7B1FA2)),
            glowColor = Color(0xFFBA68C8),
            accent = Color(0xFFBA68C8),
            unlockPoints = 9000
        ),
        TimerTheme(
            id = "lantern",
            name = "Lantern",
            backdropRes = null,
            mascotPose = MascotPose.LANTERN,
            arcColors = listOf(Color(0xFFFFB74D), Color(0xFFFFE0B2), Color(0xFFEF6C00)),
            glowColor = Color(0xFFFFB74D),
            accent = Color(0xFFFFB74D),
            unlockPoints = 11000
        ),
        // v1.5.0: Ambient round — living code-drawn backdrops (no image
        // assets, zero APK weight). Each reuses an existing mascot pose so
        // the theme_mascot_<pose> assets resolve; backdrop is a live canvas.
        TimerTheme(
            id = "sakura_drift",
            name = "Sakura Drift",
            backdropRes = null,
            ambientKind = AmbientKind.SAKURA_DRIFT,
            mascotPose = MascotPose.SAKURA,
            arcColors = listOf(Color(0xFFF8BBD0), Color(0xFFF48FB1), Color(0xFFD81B60)),
            glowColor = Color(0xFFF48FB1),
            accent = Color(0xFFF48FB1),
            unlockPoints = 13000
        ),
        TimerTheme(
            id = "firefly_forest",
            name = "Firefly Forest",
            backdropRes = null,
            ambientKind = AmbientKind.FIREFLY_FOREST,
            mascotPose = MascotPose.FOREST,
            arcColors = listOf(Color(0xFFC5E1A5), Color(0xFF9CCC65), Color(0xFF558B2F)),
            glowColor = Color(0xFF9CCC65),
            accent = Color(0xFF9CCC65),
            unlockPoints = 16000
        ),
        TimerTheme(
            id = "sunset_dunes",
            name = "Sunset Dunes",
            backdropRes = null,
            ambientKind = AmbientKind.SUNSET_DUNES,
            mascotPose = MascotPose.SUNSET,
            arcColors = listOf(Color(0xFFFFCC80), Color(0xFFFFB74D), Color(0xFFE65100)),
            glowColor = Color(0xFFFFB74D),
            accent = Color(0xFFFFB74D),
            unlockPoints = 19000
        ),
        TimerTheme(
            id = "aurora_veil",
            name = "Aurora Veil",
            backdropRes = null,
            ambientKind = AmbientKind.AURORA_VEIL,
            mascotPose = MascotPose.MIDNIGHT,
            arcColors = listOf(Color(0xFFB39DDB), Color(0xFF4DD0E1), Color(0xFF5E35B1)),
            glowColor = Color(0xFF7C4DFF),
            accent = Color(0xFF7C4DFF),
            unlockPoints = 22000
        ),
        TimerTheme(
            id = "monsoon_rain",
            name = "Monsoon Rain",
            backdropRes = null,
            ambientKind = AmbientKind.MONSOON_RAIN,
            mascotPose = MascotPose.RAIN,
            arcColors = listOf(Color(0xFFB3D5F0), Color(0xFF90CAF9), Color(0xFF4A6E8C)),
            glowColor = Color(0xFF90CAF9),
            accent = Color(0xFF90CAF9),
            unlockPoints = 25000
        ),
        TimerTheme(
            id = "ember_rise",
            name = "Ember Rise",
            backdropRes = null,
            ambientKind = AmbientKind.EMBER_RISE,
            mascotPose = MascotPose.EMBER,
            arcColors = listOf(Color(0xFFFFCC80), Color(0xFFFF6E40), Color(0xFFBF360C)),
            glowColor = Color(0xFFFF6E40),
            accent = Color(0xFFFF6E40),
            unlockPoints = 30000
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
    fun trySelectTheme(context: Context, id: String, userPoints: Int, isAdmin: Boolean = ContentLocks.isAdminUnlocked(context)): Boolean {
        val theme = getTheme(id)
        if (!theme.isUnlocked(userPoints, isAdmin)) {
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
    fun validateSelection(context: Context, userPoints: Int, isAdmin: Boolean = ContentLocks.isAdminUnlocked(context)) {
        if (!_selectedTheme.value.isUnlocked(userPoints, isAdmin)) {
            selectTheme(context, "aurora")
        }
    }
}
