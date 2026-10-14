package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.util.MascotPose
import com.example.util.WardrobeCategory
import com.example.util.WardrobeItem

/**
 * v1.1 — Celebration-pose dressed buddy for the share card.
 *
 * Delegates to [BuddyMascot] with [MascotPose.CELEBRATE]: the same equipped
 * wardrobe items, composited onto the celebration pose base with per-pose
 * fitted overlays. This keeps the ONE buddy identity consistent: the dressed
 * buddy the user sees on Study/Home/Profile/Mascot screen is the same buddy
 * on the share card, just in a different pose.
 */
@Composable
fun CelebrateBuddyMascot(
    modifier: Modifier = Modifier,
    pendingOutfit: Map<WardrobeCategory, WardrobeItem>? = null
) {
    BuddyMascot(
        modifier = modifier,
        pose = MascotPose.CELEBRATE,
        pendingOutfit = pendingOutfit
    )
}
