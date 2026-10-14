package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.util.MascotWardrobe
import com.example.util.WARDROBE_LAYER_ORDER
import com.example.util.WardrobeCategory
import com.example.util.WardrobeItem
import com.example.util.wardrobeItemById

/**
 * v1.1 — Celebration-pose dressed buddy for the share card.
 *
 * Same equipped wardrobe items + level styling as the study-pose BuddyMascot,
 * composited onto the celebration pose (mascot_celebrate_transparent).
 * The outfit overlays use the same assets; positions are adjusted for the
 * celebrate pose's proportions (arms raised, head tilted).
 *
 * This keeps the ONE buddy identity consistent: the dressed buddy the user
 * sees on Study/Home/Profile/Mascot screen is the same buddy on the share
 * card, just in a different pose.
 */
@Composable
fun CelebrateBuddyMascot(
    modifier: Modifier = Modifier,
    pendingOutfit: Map<WardrobeCategory, WardrobeItem>? = null
) {
    val equippedIds by MascotWardrobe.equipped.collectAsState()
    val outfit: Map<WardrobeCategory, WardrobeItem> = pendingOutfit
        ?: equippedIds.mapNotNull { (cat, id) -> wardrobeItemById(id)?.let { cat to it } }.toMap()

    // Celebrate pose is squarer than the study pose.
    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.mascot_celebrate_transparent),
            contentDescription = "Your celebrating mascot buddy",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth()
        )
        // Same items, repositioned for the celebrate pose.
        // The celebrate pose has raised arms and a tilted head; caps sit
        // higher, tops/bottoms center on the torso, extras near the face.
        // Items flagged renderOnCelebrate=false (books, bottoms, glasses) are
        // skipped — they can't fit this pose (per-item overrides carry the
        // eye-verified geometry).
        WARDROBE_LAYER_ORDER.forEach { category ->
            val item = outfit[category] ?: return@forEach
            val (wFrac, cx, cy) = celebratePosePosition(item, category) ?: return@forEach
            Image(
                painter = painterResource(id = item.overlayRes),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(wFrac)
                    .align(
                        BiasAlignment(
                            horizontalBias = (cx * 2f - 1f).coerceIn(-1f, 1f),
                            verticalBias = (cy * 2f - 1f).coerceIn(-1f, 1f)
                        )
                    )
            )
        }
    }
}

/**
 * Maps a wardrobe item's study-pose fractions to celebrate-pose fractions.
 * Celebrate pose: head higher and tilted, torso centered, arms raised.
 *
 * Returns null when the item must not render on the celebrate pose
 * ([WardrobeItem.renderOnCelebrate] == false). Per-item celebrate overrides
 * win; the fallback scales study widths by 640/889 (celebrate base width in
 * the square Box) and re-anchors by category.
 */
private fun celebratePosePosition(
    item: WardrobeItem,
    category: WardrobeCategory
): Triple<Float, Float, Float>? {
    if (!item.renderOnCelebrate) return null
    val wFrac = item.celebrateWidthFrac ?: (item.widthFrac * 640f / 889f)
    val cx = item.celebrateCxFrac ?: 0.5f
    val cy = item.celebrateCyFrac ?: when (category) {
        WardrobeCategory.CAPS -> 0.18f // top of head
        WardrobeCategory.TOPS -> 0.52f // torso
        WardrobeCategory.BOTTOMS -> 0.78f // lower body
        WardrobeCategory.BOOKS -> 0.55f
        WardrobeCategory.EXTRAS -> if (item.id.contains("glasses")) 0.30f else 0.45f
    }
    return Triple(wFrac, cx, cy)
}
