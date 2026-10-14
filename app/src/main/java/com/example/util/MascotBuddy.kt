package com.example.util

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.1 — Mascot Buddy System (data layer).
 *
 * LEVELS (professional names, derived from total Focus Points — the same
 * balance as themes/timer faces, never spent, never decreasing):
 * - L1 "Novice"      : 0+ pts
 * - L2 "Scholar"     : 1,000+ pts
 * - L3 "Grandmaster" : 5,000+ pts
 *
 * WARDROBE: threshold-based PERMANENT unlocks (no spending). A few premium
 * items additionally require Scholar/Grandmaster level. Owned state is
 * DERIVED from points — only the EQUIPPED outfit is persisted (existing
 * SharedPreferences "focusly_local_data", keys below). No new backend.
 */
enum class MascotLevel(val displayName: String, val threshold: Int) {
    NOVICE("Novice", 0),
    SCHOLAR("Scholar", 1000),
    GRANDMASTER("Grandmaster", 5000);

    fun next(): MascotLevel? = when (this) {
        NOVICE -> SCHOLAR
        SCHOLAR -> GRANDMASTER
        GRANDMASTER -> null
    }
}

fun levelForPoints(points: Int): MascotLevel = when {
    points >= MascotLevel.GRANDMASTER.threshold -> MascotLevel.GRANDMASTER
    points >= MascotLevel.SCHOLAR.threshold -> MascotLevel.SCHOLAR
    else -> MascotLevel.NOVICE
}

/** (progress fraction 0..1, points still needed) — needed is null at max level. */
fun progressToNextLevel(points: Int): Pair<Float, Int?> {
    val level = levelForPoints(points)
    val next = level.next() ?: return 1f to null
    val span = (next.threshold - level.threshold).coerceAtLeast(1)
    val done = (points - level.threshold).coerceIn(0, span)
    return (done.toFloat() / span) to (next.threshold - points)
}

enum class WardrobeCategory(val displayName: String) {
    CAPS("Caps"),
    TOPS("Tops"),
    BOTTOMS("Bottoms"),
    BOOKS("Books"),
    EXTRAS("Extras")
}

/**
 * A wardrobe item. Overlay art is fitted to the mascot's main study pose
 * (mascot_study_transparent, 640x889); [widthFrac]/[cxFrac]/[cyFrac] place the
 * trimmed overlay by its center as fractions of the base width/height.
 */
data class WardrobeItem(
    val id: String,
    val name: String,
    val category: WardrobeCategory,
    val unlockPoints: Int,
    val requiredLevel: MascotLevel = MascotLevel.NOVICE,
    @DrawableRes val overlayRes: Int,
    val widthFrac: Float,
    val cxFrac: Float,
    val cyFrac: Float
) {
    fun isUnlocked(focusPoints: Int): Boolean {
        if (focusPoints < unlockPoints) return false
        return levelForPoints(focusPoints) >= requiredLevel
    }

    /** Human-readable lock reason, or null when unlocked. */
    fun lockReason(focusPoints: Int): String? {
        if (isUnlocked(focusPoints)) return null
        val needLevel = levelForPoints(focusPoints) < requiredLevel
        return when {
            needLevel && focusPoints < unlockPoints ->
                "Reach ${requiredLevel.displayName} level and $unlockPoints pts"
            needLevel -> "Reach ${requiredLevel.displayName} level"
            else -> "Earn $unlockPoints Focus Points"
        }
    }
}

val WARDROBE_ITEMS: List<WardrobeItem> = listOf(
    // Caps
    WardrobeItem(
        id = "cap_lime", name = "Lime Cap", category = WardrobeCategory.CAPS,
        unlockPoints = 0, overlayRes = R.drawable.wardrobe_cap_lime,
        widthFrac = 0.82f, cxFrac = 0.50f, cyFrac = 0.19f
    ),
    WardrobeItem(
        id = "cap_scholar", name = "Scholar Mortarboard", category = WardrobeCategory.CAPS,
        unlockPoints = 1500, requiredLevel = MascotLevel.SCHOLAR,
        overlayRes = R.drawable.wardrobe_cap_scholar,
        widthFrac = 0.88f, cxFrac = 0.50f, cyFrac = 0.18f
    ),
    // Tops
    WardrobeItem(
        id = "top_sweatshirt", name = "Cream Sweatshirt", category = WardrobeCategory.TOPS,
        unlockPoints = 0, overlayRes = R.drawable.wardrobe_top_sweatshirt,
        widthFrac = 0.70f, cxFrac = 0.50f, cyFrac = 0.70f
    ),
    WardrobeItem(
        id = "top_cardigan", name = "Sage Cardigan", category = WardrobeCategory.TOPS,
        unlockPoints = 2500, requiredLevel = MascotLevel.SCHOLAR,
        overlayRes = R.drawable.wardrobe_top_cardigan,
        widthFrac = 0.70f, cxFrac = 0.50f, cyFrac = 0.70f
    ),
    // Bottoms
    WardrobeItem(
        id = "bottom_shorts", name = "Cloud Shorts", category = WardrobeCategory.BOTTOMS,
        unlockPoints = 0, overlayRes = R.drawable.wardrobe_bottom_shorts,
        widthFrac = 0.60f, cxFrac = 0.50f, cyFrac = 0.83f
    ),
    WardrobeItem(
        id = "bottom_trousers", name = "Sage Trousers", category = WardrobeCategory.BOTTOMS,
        unlockPoints = 2000, requiredLevel = MascotLevel.SCHOLAR,
        overlayRes = R.drawable.wardrobe_bottom_trousers,
        widthFrac = 0.60f, cxFrac = 0.50f, cyFrac = 0.83f
    ),
    // Books
    WardrobeItem(
        id = "book_notebook", name = "Lime Notebook", category = WardrobeCategory.BOOKS,
        unlockPoints = 500, overlayRes = R.drawable.wardrobe_book_notebook,
        widthFrac = 0.55f, cxFrac = 0.50f, cyFrac = 0.66f
    ),
    WardrobeItem(
        id = "book_tome", name = "Forest Tome", category = WardrobeCategory.BOOKS,
        unlockPoints = 4500, requiredLevel = MascotLevel.SCHOLAR,
        overlayRes = R.drawable.wardrobe_book_tome,
        widthFrac = 0.55f, cxFrac = 0.50f, cyFrac = 0.66f
    ),
    // Extras
    WardrobeItem(
        id = "extra_glasses", name = "Round Glasses", category = WardrobeCategory.EXTRAS,
        unlockPoints = 800, overlayRes = R.drawable.wardrobe_extra_glasses,
        widthFrac = 0.44f, cxFrac = 0.50f, cyFrac = 0.42f
    ),
    WardrobeItem(
        id = "extra_badge", name = "Star Badge", category = WardrobeCategory.EXTRAS,
        unlockPoints = 7500, requiredLevel = MascotLevel.GRANDMASTER,
        overlayRes = R.drawable.wardrobe_extra_badge,
        widthFrac = 0.15f, cxFrac = 0.70f, cyFrac = 0.55f
    )
)

/** Paint order: bottom garments first, face/head details last. */
val WARDROBE_LAYER_ORDER: List<WardrobeCategory> = listOf(
    WardrobeCategory.BOTTOMS,
    WardrobeCategory.TOPS,
    WardrobeCategory.BOOKS,
    WardrobeCategory.EXTRAS,
    WardrobeCategory.CAPS
)

fun wardrobeItemById(id: String?): WardrobeItem? =
    WARDROBE_ITEMS.firstOrNull { it.id == id }

// ---------------------------------------------------------------------------
// Equipped-outfit persistence — existing prefs only.
// ---------------------------------------------------------------------------
private const val WARDROBE_PREFS = "focusly_local_data"
private fun equippedKey(category: WardrobeCategory) = "mascot_equipped_${category.name.lowercase()}"

/**
 * The SAVED (equipped) outfit. The Mascot screen browses with a local
 * *pending* outfit and only writes here on "Save outfit" — unsaved browsing
 * never changes what renders elsewhere.
 */
object MascotWardrobe {
    private val _equipped = MutableStateFlow<Map<WardrobeCategory, String>>(emptyMap())
    val equipped: StateFlow<Map<WardrobeCategory, String>> = _equipped.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        refresh()
    }

    fun refresh() {
        val ctx = appContext ?: return
        try {
            val p = ctx.getSharedPreferences(WARDROBE_PREFS, Context.MODE_PRIVATE)
            _equipped.value = WardrobeCategory.values().mapNotNull { cat ->
                val id = p.getString(equippedKey(cat), null)
                if (id != null && wardrobeItemById(id) != null) cat to id else null
            }.toMap()
        } catch (e: Exception) {
            Log.e("MascotWardrobe", "refresh failed: ${e.message}")
        }
    }

    /** Persist a full outfit (category -> item id). */
    fun saveOutfit(outfit: Map<WardrobeCategory, String>) {
        val ctx = appContext
        val valid = outfit.mapNotNull { (cat, id) ->
            if (wardrobeItemById(id)?.category == cat) cat to id else null
        }.toMap()
        _equipped.value = valid
        if (ctx == null) return
        try {
            val e = ctx.getSharedPreferences(WARDROBE_PREFS, Context.MODE_PRIVATE).edit()
            WardrobeCategory.values().forEach { cat ->
                val id = valid[cat]
                if (id == null) e.remove(equippedKey(cat)) else e.putString(equippedKey(cat), id)
            }
            e.apply()
        } catch (ex: Exception) {
            Log.e("MascotWardrobe", "saveOutfit failed: ${ex.message}")
        }
    }

    /** Resolve the saved outfit to items (for rendering). */
    fun currentOutfitItems(): Map<WardrobeCategory, WardrobeItem> =
        _equipped.value.mapNotNull { (cat, id) ->
            wardrobeItemById(id)?.let { cat to it }
        }.toMap()
}
