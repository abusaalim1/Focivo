package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
 * Renders the transparent plain base mascot (per-pose) + equipped wardrobe
 * item overlays as a PURE STACK: every image is a transparent full-canvas
 * render on the same canvas (1408x1760), drawn with ContentScale.FillBounds,
 * so item pixels land at their original coordinates — no placement math.
 *
 * Z-order: base → shoes → bottoms → tops → extras → caps
 * (WARDROBE_LAYER_ORDER).
 *
 * Used for the PRIMARY mascot presentations so the dressed buddy appears
 * consistently: Study timer (default theme), Home hero, Mascot screen,
 * Profile avatar spot.
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

    // All mascot art shares one canvas: 1408x1760.
    Box(
        modifier = modifier.aspectRatio(1408f / 1760f),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = baseRes),
            contentDescription = "Your mascot buddy",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        WARDROBE_LAYER_ORDER.forEach { category ->
            val item = outfit[category] ?: return@forEach
            val overlay = item.overlayFor(pose) ?: return@forEach
            Image(
                painter = painterResource(id = overlay.resId),
                contentDescription = item.name,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
