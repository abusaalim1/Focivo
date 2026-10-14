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
 *
 * Item overlays are FITTED per-pose via diff-extraction onto the plain base.
 * Each item has a per-pose overlay (standing/study/celebrate) with placement
 * as full-canvas transparent images on the same canvas as the pose's base.
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

enum class MascotPose(val displayName: String) {
    STANDING("Standing"),
    STUDY("Study"),
    CELEBRATE("Celebrate")
}

enum class WardrobeCategory(val displayName: String) {
    CAPS("Caps"),
    TOPS("Tops"),
    BOTTOMS("Bottoms"),
    SHOES("Shoes"),
    EXTRAS("Extras")
}

/** Per-pose overlay: a full-canvas transparent image on the SAME canvas as the
 * pose's plain base — item pixels sit at their original coordinates, so the
 * render path is a pure stack (no placement math). */
data class PoseOverlay(
    @DrawableRes val resId: Int
)

data class WardrobeItem(
    val id: String,
    val name: String,
    val category: WardrobeCategory,
    val unlockPoints: Int,
    val requiredLevel: MascotLevel = MascotLevel.NOVICE,
    val overlays: Map<MascotPose, PoseOverlay>
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

    fun overlayFor(pose: MascotPose): PoseOverlay? = overlays[pose]
}

val WARDROBE_ITEMS: List<WardrobeItem> = listOf(
    WardrobeItem(
        id = "cap_lime", name = "Lime Cap",
        category = WardrobeCategory.CAPS,
        unlockPoints = 0, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_cap_lime_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_cap_lime_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_cap_lime_celebrate),
        )
    ),
    WardrobeItem(
        id = "cap_scholar", name = "Scholar Mortarboard",
        category = WardrobeCategory.CAPS,
        unlockPoints = 1500, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_cap_scholar_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_cap_scholar_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_cap_scholar_celebrate),
        )
    ),
    WardrobeItem(
        id = "top_tee_white", name = "White Tee",
        category = WardrobeCategory.TOPS,
        unlockPoints = 0, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_top_tee_white_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_top_tee_white_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_top_tee_white_celebrate)
        )
    ),
    WardrobeItem(
        id = "top_sweatshirt", name = "Cream Sweatshirt",
        category = WardrobeCategory.TOPS,
        unlockPoints = 300, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_top_sweatshirt_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_top_sweatshirt_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_top_sweatshirt_celebrate),
        )
    ),
    WardrobeItem(
        id = "top_cardigan", name = "Sage Cardigan",
        category = WardrobeCategory.TOPS,
        unlockPoints = 2500, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_top_cardigan_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_top_cardigan_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_top_cardigan_celebrate),
        )
    ),
    WardrobeItem(
        id = "top_tee_mint", name = "Mint Tee",
        category = WardrobeCategory.TOPS,
        unlockPoints = 800, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_top_tee_mint_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_top_tee_mint_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_top_tee_mint_celebrate),
        )
    ),
    WardrobeItem(
        id = "top_hoodie", name = "Mint Hoodie",
        category = WardrobeCategory.TOPS,
        unlockPoints = 1200, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_top_hoodie_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_top_hoodie_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_top_hoodie_celebrate),
        )
    ),
    WardrobeItem(
        id = "top_jacket_denim", name = "Denim Jacket",
        category = WardrobeCategory.TOPS,
        unlockPoints = 2000, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_top_jacket_denim_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_top_jacket_denim_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_top_jacket_denim_celebrate),
        )
    ),
    WardrobeItem(
        id = "top_sweater", name = "Navy Sweater",
        category = WardrobeCategory.TOPS,
        unlockPoints = 4000, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_top_sweater_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_top_sweater_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_top_sweater_celebrate),
        )
    ),
    WardrobeItem(
        id = "top_blazer", name = "Royal Blazer",
        category = WardrobeCategory.TOPS,
        unlockPoints = 6000, requiredLevel = MascotLevel.GRANDMASTER,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_top_blazer_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_top_blazer_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_top_blazer_celebrate),
        )
    ),
    WardrobeItem(
        id = "bottom_shorts_navy", name = "Navy Shorts",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 0, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_bottom_shorts_navy_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_bottom_shorts_navy_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_bottom_shorts_navy_celebrate),
        )
    ),
    WardrobeItem(
        id = "bottom_shorts_beige", name = "Beige Shorts",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 500, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_bottom_shorts_beige_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_bottom_shorts_beige_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_bottom_shorts_beige_celebrate),
        )
    ),
    WardrobeItem(
        id = "bottom_shorts_dark", name = "Dark Shorts",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 1000, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_bottom_shorts_dark_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_bottom_shorts_dark_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_bottom_shorts_dark_celebrate),
        )
    ),
    WardrobeItem(
        id = "bottom_trousers_grey", name = "Grey Trousers",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 1800, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_bottom_trousers_grey_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_bottom_trousers_grey_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_bottom_trousers_grey_celebrate),
        )
    ),
    WardrobeItem(
        id = "bottom_trousers_charcoal", name = "Charcoal Trousers",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 3000, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_bottom_trousers_charcoal_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_bottom_trousers_charcoal_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_bottom_trousers_charcoal_celebrate),
        )
    ),
    WardrobeItem(
        id = "bottom_shorts_black", name = "Black Shorts",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 3500, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_bottom_shorts_black_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_bottom_shorts_black_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_bottom_shorts_black_celebrate),
        )
    ),
    WardrobeItem(
        id = "shoes_lime", name = "Lime Sneakers",
        category = WardrobeCategory.SHOES,
        unlockPoints = 700, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_shoes_sneakers_lime_standing),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_shoes_sneakers_lime_celebrate),
        )
    ),
    WardrobeItem(
        id = "shoes_black", name = "Black Shoes",
        category = WardrobeCategory.SHOES,
        unlockPoints = 2500, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_shoes_black_standing),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_shoes_black_celebrate),
        )
    ),
    WardrobeItem(
        id = "extra_glasses", name = "Round Glasses",
        category = WardrobeCategory.EXTRAS,
        unlockPoints = 800, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_extra_glasses_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_extra_glasses_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_extra_glasses_celebrate),
        )
    ),
    WardrobeItem(
        id = "extra_badge", name = "Gold Star Badge",
        category = WardrobeCategory.EXTRAS,
        unlockPoints = 7500, requiredLevel = MascotLevel.GRANDMASTER,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(R.drawable.wardrobe_extra_badge_standing),
            MascotPose.STUDY to PoseOverlay(R.drawable.wardrobe_extra_badge_study),
            MascotPose.CELEBRATE to PoseOverlay(R.drawable.wardrobe_extra_badge_celebrate),
        )
    ),
)
/** Paint order: shoes first (feet), then bottoms, tops, extras, caps last. */
val WARDROBE_LAYER_ORDER: List<WardrobeCategory> = listOf(
    WardrobeCategory.SHOES,
    WardrobeCategory.BOTTOMS,
    WardrobeCategory.TOPS,
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

    /** The free Classic look every new user starts with. */
    val DEFAULT_OUTFIT: Map<WardrobeCategory, String> = mapOf(
        WardrobeCategory.TOPS to "top_tee_white",
        WardrobeCategory.BOTTOMS to "bottom_shorts_navy"
    )

    fun refresh() {
        val ctx = appContext ?: return
        try {
            val p = ctx.getSharedPreferences(WARDROBE_PREFS, Context.MODE_PRIVATE)
            val saved = WardrobeCategory.values().mapNotNull { cat ->
                val id = p.getString(equippedKey(cat), null)
                if (id != null && wardrobeItemById(id) != null) cat to id else null
            }.toMap()
            _equipped.value = saved.ifEmpty { DEFAULT_OUTFIT }
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
