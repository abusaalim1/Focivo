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
 * as fractions of the base image (widthFrac/cxFrac/cyFrac).
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

/** Per-pose overlay: drawable + placement as fractions of the base image. */
data class PoseOverlay(
    @DrawableRes val resId: Int,
    val widthFrac: Float,
    val cxFrac: Float,
    val cyFrac: Float
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
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_cap_lime_standing,
                widthFrac = 0.3608f, cxFrac = 0.4260f, cyFrac = 0.3451f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_cap_lime_study,
                widthFrac = 0.4094f, cxFrac = 0.4624f, cyFrac = 0.3236f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_cap_lime_celebrate,
                widthFrac = 0.3396f, cxFrac = 0.4840f, cyFrac = 0.3454f
            ),
        )
    ),
    WardrobeItem(
        id = "cap_scholar", name = "Scholar Mortarboard",
        category = WardrobeCategory.CAPS,
        unlockPoints = 1500, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_cap_scholar_standing,
                widthFrac = 0.3356f, cxFrac = 0.4609f, cyFrac = 0.3121f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_cap_scholar_study,
                widthFrac = 0.3507f, cxFrac = 0.4693f, cyFrac = 0.3030f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_cap_scholar_celebrate,
                widthFrac = 0.3436f, cxFrac = 0.4942f, cyFrac = 0.3171f
            ),
        )
    ),
    WardrobeItem(
        id = "top_sweatshirt", name = "Cream Sweatshirt",
        category = WardrobeCategory.TOPS,
        unlockPoints = 300, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_top_sweatshirt_standing,
                widthFrac = 0.3661f, cxFrac = 0.5365f, cyFrac = 0.5304f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_top_sweatshirt_study,
                widthFrac = 0.2775f, cxFrac = 0.5074f, cyFrac = 0.5796f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_top_sweatshirt_celebrate,
                widthFrac = 0.2645f, cxFrac = 0.5249f, cyFrac = 0.6289f
            ),
        )
    ),
    WardrobeItem(
        id = "top_cardigan", name = "Sage Cardigan",
        category = WardrobeCategory.TOPS,
        unlockPoints = 2500, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_top_cardigan_standing,
                widthFrac = 0.4371f, cxFrac = 0.4701f, cyFrac = 0.5770f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_top_cardigan_study,
                widthFrac = 0.3671f, cxFrac = 0.4974f, cyFrac = 0.5924f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_top_cardigan_celebrate,
                widthFrac = 0.4501f, cxFrac = 0.5199f, cyFrac = 0.5776f
            ),
        )
    ),
    WardrobeItem(
        id = "top_tee_mint", name = "Mint Tee",
        category = WardrobeCategory.TOPS,
        unlockPoints = 800, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_top_tee_mint_standing,
                widthFrac = 0.3420f, cxFrac = 0.4889f, cyFrac = 0.5820f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_top_tee_mint_study,
                widthFrac = 0.3407f, cxFrac = 0.5101f, cyFrac = 0.5897f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_top_tee_mint_celebrate,
                widthFrac = 0.3387f, cxFrac = 0.5129f, cyFrac = 0.5737f
            ),
        )
    ),
    WardrobeItem(
        id = "top_hoodie", name = "Mint Hoodie",
        category = WardrobeCategory.TOPS,
        unlockPoints = 1200, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_top_hoodie_standing,
                widthFrac = 0.4175f, cxFrac = 0.5065f, cyFrac = 0.5920f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_top_hoodie_study,
                widthFrac = 0.3691f, cxFrac = 0.5063f, cyFrac = 0.5949f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_top_hoodie_celebrate,
                widthFrac = 0.3894f, cxFrac = 0.5252f, cyFrac = 0.5796f
            ),
        )
    ),
    WardrobeItem(
        id = "top_jacket_denim", name = "Denim Jacket",
        category = WardrobeCategory.TOPS,
        unlockPoints = 2000, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_top_jacket_denim_standing,
                widthFrac = 0.4049f, cxFrac = 0.4651f, cyFrac = 0.5798f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_top_jacket_denim_study,
                widthFrac = 0.3778f, cxFrac = 0.4882f, cyFrac = 0.5966f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_top_jacket_denim_celebrate,
                widthFrac = 0.4583f, cxFrac = 0.5184f, cyFrac = 0.5818f
            ),
        )
    ),
    WardrobeItem(
        id = "top_sweater", name = "Navy Sweater",
        category = WardrobeCategory.TOPS,
        unlockPoints = 4000, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_top_sweater_standing,
                widthFrac = 0.4072f, cxFrac = 0.4456f, cyFrac = 0.6416f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_top_sweater_study,
                widthFrac = 0.4097f, cxFrac = 0.4791f, cyFrac = 0.6485f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_top_sweater_celebrate,
                widthFrac = 0.3136f, cxFrac = 0.4797f, cyFrac = 0.6468f
            ),
        )
    ),
    WardrobeItem(
        id = "top_blazer", name = "Royal Blazer",
        category = WardrobeCategory.TOPS,
        unlockPoints = 6000, requiredLevel = MascotLevel.GRANDMASTER,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_top_blazer_standing,
                widthFrac = 0.2478f, cxFrac = 0.4712f, cyFrac = 0.5214f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_top_blazer_study,
                widthFrac = 0.2758f, cxFrac = 0.5278f, cyFrac = 0.5587f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_top_blazer_celebrate,
                widthFrac = 0.2537f, cxFrac = 0.5035f, cyFrac = 0.5299f
            ),
        )
    ),
    WardrobeItem(
        id = "bottom_shorts_navy", name = "Navy Shorts",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 0, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_navy_standing,
                widthFrac = 0.2807f, cxFrac = 0.4731f, cyFrac = 0.6855f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_navy_study,
                widthFrac = 0.3516f, cxFrac = 0.4802f, cyFrac = 0.6933f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_navy_celebrate,
                widthFrac = 0.2478f, cxFrac = 0.5065f, cyFrac = 0.6849f
            ),
        )
    ),
    WardrobeItem(
        id = "bottom_shorts_beige", name = "Beige Shorts",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 500, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_beige_standing,
                widthFrac = 0.2558f, cxFrac = 0.4835f, cyFrac = 0.6806f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_beige_study,
                widthFrac = 0.3673f, cxFrac = 0.4902f, cyFrac = 0.6767f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_beige_celebrate,
                widthFrac = 0.2526f, cxFrac = 0.5006f, cyFrac = 0.6793f
            ),
        )
    ),
    WardrobeItem(
        id = "bottom_shorts_dark", name = "Dark Shorts",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 1000, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_dark_standing,
                widthFrac = 0.2678f, cxFrac = 0.4734f, cyFrac = 0.6895f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_dark_study,
                widthFrac = 0.3820f, cxFrac = 0.4921f, cyFrac = 0.6525f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_dark_celebrate,
                widthFrac = 0.3097f, cxFrac = 0.5208f, cyFrac = 0.7101f
            ),
        )
    ),
    WardrobeItem(
        id = "bottom_trousers_grey", name = "Grey Trousers",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 1800, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_trousers_grey_standing,
                widthFrac = 0.2593f, cxFrac = 0.4726f, cyFrac = 0.6865f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_trousers_grey_study,
                widthFrac = 0.3797f, cxFrac = 0.4833f, cyFrac = 0.6772f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_trousers_grey_celebrate,
                widthFrac = 0.3368f, cxFrac = 0.5206f, cyFrac = 0.7385f
            ),
        )
    ),
    WardrobeItem(
        id = "bottom_trousers_charcoal", name = "Charcoal Trousers",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 3000, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_trousers_charcoal_standing,
                widthFrac = 0.3145f, cxFrac = 0.4853f, cyFrac = 0.7527f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_trousers_charcoal_study,
                widthFrac = 0.3991f, cxFrac = 0.4740f, cyFrac = 0.6802f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_trousers_charcoal_celebrate,
                widthFrac = 0.3136f, cxFrac = 0.4997f, cyFrac = 0.7750f
            ),
        )
    ),
    WardrobeItem(
        id = "bottom_shorts_black", name = "Black Shorts",
        category = WardrobeCategory.BOTTOMS,
        unlockPoints = 3500, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_black_standing,
                widthFrac = 0.3891f, cxFrac = 0.4626f, cyFrac = 0.6905f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_black_study,
                widthFrac = 0.4072f, cxFrac = 0.4627f, cyFrac = 0.7147f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_bottom_shorts_black_celebrate,
                widthFrac = 0.3989f, cxFrac = 0.4969f, cyFrac = 0.7271f
            ),
        )
    ),
    WardrobeItem(
        id = "shoes_lime", name = "Lime Sneakers",
        category = WardrobeCategory.SHOES,
        unlockPoints = 700, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_shoes_sneakers_lime_standing,
                widthFrac = 0.2885f, cxFrac = 0.4820f, cyFrac = 0.6951f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_shoes_sneakers_lime_celebrate,
                widthFrac = 0.2729f, cxFrac = 0.4874f, cyFrac = 0.6931f
            ),
        )
    ),
    WardrobeItem(
        id = "shoes_black", name = "Black Shoes",
        category = WardrobeCategory.SHOES,
        unlockPoints = 2500, requiredLevel = MascotLevel.SCHOLAR,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_shoes_black_standing,
                widthFrac = 0.3009f, cxFrac = 0.4787f, cyFrac = 0.7181f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_shoes_black_celebrate,
                widthFrac = 0.2656f, cxFrac = 0.4894f, cyFrac = 0.7035f
            ),
        )
    ),
    WardrobeItem(
        id = "extra_glasses", name = "Round Glasses",
        category = WardrobeCategory.EXTRAS,
        unlockPoints = 800, requiredLevel = MascotLevel.NOVICE,
        overlays = mapOf(
            MascotPose.STANDING to PoseOverlay(
                resId = R.drawable.wardrobe_extra_glasses_standing,
                widthFrac = 0.3877f, cxFrac = 0.4532f, cyFrac = 0.3669f
            ),
            MascotPose.STUDY to PoseOverlay(
                resId = R.drawable.wardrobe_extra_glasses_study,
                widthFrac = 0.4057f, cxFrac = 0.4584f, cyFrac = 0.3975f
            ),
            MascotPose.CELEBRATE to PoseOverlay(
                resId = R.drawable.wardrobe_extra_glasses_celebrate,
                widthFrac = 0.3976f, cxFrac = 0.4801f, cyFrac = 0.3637f
            ),
        )
    ),
    WardrobeItem(
        id = "extra_badge", name = "Gold Star Badge",
        category = WardrobeCategory.EXTRAS,
        unlockPoints = 7500, requiredLevel = MascotLevel.GRANDMASTER,
        overlays = mapOf(
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
