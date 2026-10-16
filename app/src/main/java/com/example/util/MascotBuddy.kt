package com.example.util

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.2 — Mascot Buddy System (data layer), OUTFIT SETS edition.
 *
 * The wardrobe is a collection of 10 complete OUTFIT SETS (approved by the
 * owner, 2026-10-15). Each set is a full fitted render of the mascot in 3
 * poses (standing / study / celebrate), generated from his own cute
 * reference — the mascot is NEVER bald: every set wears his signature
 * hood-style graduation cap in the set's colour. Picking a set IS the
 * cap-colour change — there is no separate cap item.
 *
 * All sets are free/unlocked for this test build (ALL_CONTENT_UNLOCKED).
 * Only the EQUIPPED SET ID is persisted (existing SharedPreferences
 * "focusly_local_data", key below). No new backend.
 *
 * LEVELS (kept for the buddy identity badge):
 * - L1 "Novice"      : 0+ pts
 * - L2 "Scholar"     : 1,000+ pts
 * - L3 "Grandmaster" : 5,000+ pts
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

/**
 * One outfit set: a complete fitted render of the mascot (hood-style
 * graduation cap included, in the set's colour) per pose. The renders are
 * transparent full-canvas images, so the render path is a single Image.
 *
 * v1.2.1: sets are locked behind the Focus-Points tier ladder again
 * (owner re-lock order): Novice 0 / Scholar 1,000 / Grandmaster 5,000.
 * Threshold model — points are never spent. Default set02 stays free.
 */
data class MascotOutfitSet(
    val id: String,
    val name: String,
    val accentColor: Long,
    @DrawableRes val standingRes: Int,
    @DrawableRes val studyRes: Int,
    @DrawableRes val celebrateRes: Int,
    /** Lifetime Focus Points needed to unlock this set. 0 = free. */
    val unlockPoints: Int = 0,
) {
    fun resFor(pose: MascotPose): Int = when (pose) {
        MascotPose.STANDING -> standingRes
        MascotPose.STUDY -> studyRes
        MascotPose.CELEBRATE -> celebrateRes
    }

    // v1.2.4: isAdmin (owner admin account) frees every set — single choke point.
    fun isUnlocked(userPoints: Int, isAdmin: Boolean = false): Boolean =
        isAdmin || ALL_CONTENT_UNLOCKED || userPoints >= unlockPoints
}

/**
 * v1.2.1 tier assignment (owner re-lock; Novice 0 / Scholar 1,000 /
 * Grandmaster 5,000 lifetime Focus Points, threshold model):
 * - FREE (Novice): set02 (default equipped), set01, set03 — a new user is
 *   never stuck with one look.
 * - Scholar (1,000): set05, set06, set07, set08.
 * - Grandmaster (5,000): set04, set09, set10.
 */
val OUTFIT_SETS: List<MascotOutfitSet> = listOf(
    MascotOutfitSet(
        id = "set01", name = "Sky Blue · Book + Star", accentColor = 0xFF7FB6E8,
        standingRes = R.drawable.wardrobe_set01_standing,
        studyRes = R.drawable.wardrobe_set01_study,
        celebrateRes = R.drawable.wardrobe_set01_celebrate,
        unlockPoints = 0,
    ),
    MascotOutfitSet(
        id = "set02", name = "Sage · Study Glass", accentColor = 0xFF7FA98A,
        standingRes = R.drawable.wardrobe_set02_standing,
        studyRes = R.drawable.wardrobe_set02_study,
        celebrateRes = R.drawable.wardrobe_set02_celebrate,
        unlockPoints = 0,
    ),
    MascotOutfitSet(
        id = "set03", name = "Cream · Book + Pencil", accentColor = 0xFFCBB98F,
        standingRes = R.drawable.wardrobe_set03_standing,
        studyRes = R.drawable.wardrobe_set03_study,
        celebrateRes = R.drawable.wardrobe_set03_celebrate,
        unlockPoints = 0,
    ),
    MascotOutfitSet(
        id = "set04", name = "Lavender · LEARN + Lock", accentColor = 0xFFA78BDB,
        standingRes = R.drawable.wardrobe_set04_standing,
        studyRes = R.drawable.wardrobe_set04_study,
        celebrateRes = R.drawable.wardrobe_set04_celebrate,
        unlockPoints = 5000,
    ),
    MascotOutfitSet(
        id = "set05", name = "Peach · Apple", accentColor = 0xFFF0A37E,
        standingRes = R.drawable.wardrobe_set05_standing,
        studyRes = R.drawable.wardrobe_set05_study,
        celebrateRes = R.drawable.wardrobe_set05_celebrate,
        unlockPoints = 1000,
    ),
    MascotOutfitSet(
        id = "set06", name = "Cream · Varsity", accentColor = 0xFFE3D3AE,
        standingRes = R.drawable.wardrobe_set06_standing,
        studyRes = R.drawable.wardrobe_set06_study,
        celebrateRes = R.drawable.wardrobe_set06_celebrate,
        unlockPoints = 1000,
    ),
    MascotOutfitSet(
        id = "set07", name = "Light Blue · Scholar", accentColor = 0xFF8FC3E8,
        standingRes = R.drawable.wardrobe_set07_standing,
        studyRes = R.drawable.wardrobe_set07_study,
        celebrateRes = R.drawable.wardrobe_set07_celebrate,
        unlockPoints = 1000,
    ),
    MascotOutfitSet(
        id = "set08", name = "Mint · Lightbulb", accentColor = 0xFF86CDB8,
        standingRes = R.drawable.wardrobe_set08_standing,
        studyRes = R.drawable.wardrobe_set08_study,
        celebrateRes = R.drawable.wardrobe_set08_celebrate,
        unlockPoints = 1000,
    ),
    MascotOutfitSet(
        id = "set09", name = "Yellow · Backpack", accentColor = 0xFFF2CE6B,
        standingRes = R.drawable.wardrobe_set09_standing,
        studyRes = R.drawable.wardrobe_set09_study,
        celebrateRes = R.drawable.wardrobe_set09_celebrate,
        unlockPoints = 5000,
    ),
    MascotOutfitSet(
        id = "set10", name = "Dusty Purple · Study Icons", accentColor = 0xFF8E7FA8,
        standingRes = R.drawable.wardrobe_set10_standing,
        studyRes = R.drawable.wardrobe_set10_study,
        celebrateRes = R.drawable.wardrobe_set10_celebrate,
        unlockPoints = 5000,
    ),
)

fun outfitSetById(id: String?): MascotOutfitSet? =
    OUTFIT_SETS.firstOrNull { it.id == id }

// ---------------------------------------------------------------------------
// Equipped-set persistence — existing prefs only.
// ---------------------------------------------------------------------------
private const val WARDROBE_PREFS = "focusly_local_data"
private const val KEY_EQUIPPED_SET = "mascot_equipped_outfit_set"

/**
 * The SAVED (equipped) outfit set id. The Mascot screen browses with a local
 * *pending* set id and only writes here on "Save outfit" — unsaved browsing
 * never changes what renders elsewhere.
 */
object MascotWardrobe {
    private val _equipped = MutableStateFlow("")
    val equipped: StateFlow<String> = _equipped.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        refresh()
    }

    /** The default set for fresh installs: sage — closest to the mascot's
     * signature green cap. */
    const val DEFAULT_SET_ID = "set02"

    fun refresh() {
        val ctx = appContext
        _equipped.value = try {
            val saved = ctx
                ?.getSharedPreferences(WARDROBE_PREFS, Context.MODE_PRIVATE)
                ?.getString(KEY_EQUIPPED_SET, null)
            if (outfitSetById(saved) != null) saved!! else DEFAULT_SET_ID
        } catch (e: Exception) {
            Log.e("MascotWardrobe", "refresh failed: ${e.message}")
            DEFAULT_SET_ID
        }
    }

    /** Persist the equipped set id. */
    fun saveSet(setId: String) {
        val valid = if (outfitSetById(setId) != null) setId else DEFAULT_SET_ID
        _equipped.value = valid
        try {
            appContext
                ?.getSharedPreferences(WARDROBE_PREFS, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(KEY_EQUIPPED_SET, valid)
                ?.apply()
        } catch (e: Exception) {
            Log.e("MascotWardrobe", "saveSet failed: ${e.message}")
        }
    }

    /** Resolve the saved set (always non-null — falls back to the default). */
    fun currentSet(): MascotOutfitSet =
        outfitSetById(_equipped.value) ?: outfitSetById(DEFAULT_SET_ID)!!

    /**
     * v1.2.1: the equipped set can never be a locked one. Points only grow,
     * so this is belt-and-braces — if the saved set is somehow locked for
     * the user's balance, fall back to the free default.
     */
    fun ensureEquippedUnlocked(userPoints: Int, isAdmin: Boolean = false) {
        val current = outfitSetById(_equipped.value)
        if (current != null && !current.isUnlocked(userPoints, isAdmin)) {
            saveSet(DEFAULT_SET_ID)
        }
    }
}
