package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.util.MascotPose

/**
 * v1.2 — Celebration-pose buddy for the share card.
 *
 * Delegates to [BuddyMascot] with [MascotPose.CELEBRATE]: the equipped outfit
 * set's full fitted render (never bald — the set wears the hood-style
 * graduation cap). This keeps the ONE buddy identity consistent: the buddy
 * the user sees on Study/Home/Profile/Mascot screen is the same buddy on
 * the share card, just in a different pose.
 */
@Composable
fun CelebrateBuddyMascot(
    modifier: Modifier = Modifier,
    pendingOutfit: String? = null
) {
    BuddyMascot(
        modifier = modifier,
        pose = MascotPose.CELEBRATE,
        pendingOutfit = pendingOutfit
    )
}
