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
import com.example.util.MascotPose
import com.example.util.MascotWardrobe
import com.example.util.WARDROBE_LAYER_ORDER
import com.example.util.WardrobeCategory
import com.example.util.WardrobeItem
import com.example.util.wardrobeItemById

/**
 * v1.1 — SHARED layered mascot composable.
 *
 * Renders the plain base mascot (per-pose) + equipped wardrobe item overlays
 * in the correct z-order (shoes → bottoms → tops → extras → caps). Used for
 * the PRIMARY mascot presentations so the dressed buddy appears consistently:
 * Study timer (default theme), Home hero, Mascot screen, Profile avatar spot.
 *
 * Themed mascots are untouched: when a theme is active its mascot wins on
 * the themed timer — the buddy outfit only applies to default presentations.
 *
 * @param pose which base pose to render (STANDING/STUDY/CELEBRATE)
 * @param pendingOutfit when non-null, renders this outfit instead of the
 * saved one (Mascot screen live preview while browsing; unsaved browsing
 * never leaks elsewhere).
 */
@Composable
fun BuddyMascot(
    modifier: Modifier = Modifier,
    pose: MascotPose = MascotPose.STUDY,
    pendingOutfit: Map<WardrobeCategory, WardrobeItem>? = null
) {
    val equippedIds by MascotWardrobe.equipped.collectAsState()
    val outfit: Map<WardrobeCategory, WardrobeItem> = pendingOutfit
        ?: equippedIds.mapNotNull { (cat, id) -> wardrobeItemById(id)?.let { cat to it } }.toMap()

    val baseRes = when (pose) {
        MascotPose.STANDING -> R.drawable.mascot_base_standing
        MascotPose.STUDY -> R.drawable.mascot_base_study
        MascotPose.CELEBRATE -> R.drawable.mascot_base_celebrate
    }
    // Base aspect ratios: standing 1288x1810, study/celebrate 2390x3404
    val aspect = when (pose) {
        MascotPose.STANDING -> 1288f / 1810f
        else -> 2390f / 3404f
    }

    Box(
        modifier = modifier.aspectRatio(aspect),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = baseRes),
            contentDescription = "Your mascot buddy",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth()
        )
        WARDROBE_LAYER_ORDER.forEach { category ->
            val item = outfit[category] ?: return@forEach
            val overlay = item.overlayFor(pose) ?: return@forEach
            Image(
                painter = painterResource(id = overlay.resId),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(overlay.widthFrac)
                    .align(
                        BiasAlignment(
                            horizontalBias = (overlay.cxFrac * 2f - 1f).coerceIn(-1f, 1f),
                            verticalBias = (overlay.cyFrac * 2f - 1f).coerceIn(-1f, 1f)
                        )
                    )
            )
        }
    }
}
