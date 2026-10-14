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
 * full-canvas overlay by its center as fractions of the base width/height.
 *
 * Celebrate-pose (share card) overrides: the celebrate Box is square and the
 * base renders at 640/889 of its width, so celebrate widths default to
 * [widthFrac] * 640/889. Per-item [celebrate*] overrides fine-tune placement;
 * [renderOnCelebrate]=false skips items that can't fit the pose (books and
 * bottoms on the arms-raised celebrate pose; glasses, which read as goggles
 * on the smaller celebrate face).
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
    val cyFrac: Float,
    val celebrateWidthFrac: Float? = null,
    val celebrateCxFrac: Float? = null,
    val celebrateCyFrac: Float? = null,
    val renderOnCelebrate: Boolean = true
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
    // Caps — sit on the head TOP (35-50% of mascot width), never over the eyes.
    // Eyes measured at y≈0.455; cap bottoms rest on the hood dome (y≈0.19-0.23).
    WardrobeItem(
        id = "cap_lime", name = "Lime Cap", category = WardrobeCategory.CAPS,
        unlockPoints = 0, overlayRes = R.drawable.wardrobe_cap_lime,
        widthFrac = 0.46f, cxFrac = 0.50f, cyFrac = 0.13f,
        celebrateWidthFrac = 0.26f, celebrateCxFrac = 0.50f, celebrateCyFrac = 0.10f
    ),
    WardrobeItem(
        id = "cap_scholar", name = "Scholar Mortarboard", category = WardrobeCategory.CAPS,
        unlockPoints = 1500, requiredLevel = MascotLevel.SCHOLAR,
        overlayRes = R.drawable.wardrobe_cap_scholar,
        widthFrac = 0.50f, cxFrac = 0.50f, cyFrac = 0.125f,
        celebrateWidthFrac = 0.26f, celebrateCxFrac = 0.50f, celebrateCyFrac = 0.10f
    ),
    // Tops — unchanged (verified to fit the torso).
    WardrobeItem(
        id = "top_sweatshirt", name = "Cream Sweatshirt", category = WardrobeCategory.TOPS,
        unlockPoints = 0, overlayRes = R.drawable.wardrobe_top_sweatshirt,
        widthFrac = 0.70f, cxFrac = 0.50f, cyFrac = 0.70f,
        celebrateWidthFrac = 0.50f, celebrateCxFrac = 0.50f, celebrateCyFrac = 0.60f
    ),
    WardrobeItem(
        id = "top_cardigan", name = "Sage Cardigan", category = WardrobeCategory.TOPS,
        unlockPoints = 2500, requiredLevel = MascotLevel.SCHOLAR,
        overlayRes = R.drawable.wardrobe_top_cardigan,
        widthFrac = 0.70f, cxFrac = 0.50f, cyFrac = 0.70f,
        celebrateWidthFrac = 0.50f, celebrateCxFrac = 0.50f, celebrateCyFrac = 0.60f
    ),
    // Bottoms — refit to the lap at sane scale; excluded on celebrate pose.
    WardrobeItem(
        id = "bottom_shorts", name = "Cloud Shorts", category = WardrobeCategory.BOTTOMS,
        unlockPoints = 0, overlayRes = R.drawable.wardrobe_bottom_shorts,
        widthFrac = 0.48f, cxFrac = 0.50f, cyFrac = 0.85f,
        renderOnCelebrate = false
    ),
    WardrobeItem(
        id = "bottom_trousers", name = "Sage Trousers", category = WardrobeCategory.BOTTOMS,
        unlockPoints = 2000, requiredLevel = MascotLevel.SCHOLAR,
        overlayRes = R.drawable.wardrobe_bottom_trousers,
        widthFrac = 0.52f, cxFrac = 0.50f, cyFrac = 0.84f,
        renderOnCelebrate = false
    ),
    // Books — only on poses that hold a book; sized to the held book.
    // Excluded on celebrate (arms raised, would cover the torso).
    WardrobeItem(
        id = "book_notebook", name = "Lime Notebook", category = WardrobeCategory.BOOKS,
        unlockPoints = 500, overlayRes = R.drawable.wardrobe_book_notebook,
        widthFrac = 0.55f, cxFrac = 0.50f, cyFrac = 0.66f,
        renderOnCelebrate = false
    ),
    WardrobeItem(
        id = "book_tome", name = "Forest Tome", category = WardrobeCategory.BOOKS,
        unlockPoints = 4500, requiredLevel = MascotLevel.SCHOLAR,
        overlayRes = R.drawable.wardrobe_book_tome,
        widthFrac = 0.55f, cxFrac = 0.50f, cyFrac = 0.66f,
        renderOnCelebrate = false
    ),
    // Extras — glasses aligned to measured eye centers (0.439/0.705, y 0.455);
    // lens span of the asset (0.487) matched to the eye span (0.266).
    // Glasses don't fit the smaller celebrate face -> study pose only.
    WardrobeItem(
        id = "extra_glasses", name = "Round Glasses", category = WardrobeCategory.EXTRAS,
        unlockPoints = 800, overlayRes = R.drawable.wardrobe_extra_glasses,
        widthFrac = 0.55f, cxFrac = 0.572f, cyFrac = 0.451f,
        renderOnCelebrate = false
    ),
    WardrobeItem(
        id = "extra_badge", name = "Star Badge", category = WardrobeCategory.EXTRAS,
        unlockPoints = 7500, requiredLevel = MascotLevel.GRANDMASTER,
        overlayRes = R.drawable.wardrobe_extra_badge,
        widthFrac = 0.15f, cxFrac = 0.70f, cyFrac = 0.55f,
        celebrateWidthFrac = 0.11f, celebrateCxFrac = 0.62f, celebrateCyFrac = 0.55f
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
