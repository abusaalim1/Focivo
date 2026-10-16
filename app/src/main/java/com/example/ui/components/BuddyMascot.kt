package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.util.MascotPose
import com.example.util.MascotWardrobe
import com.example.util.outfitSetById

/**
 * v1.2 — SHARED mascot composable, OUTFIT SETS edition.
 *
 * Renders the EQUIPPED OUTFIT SET's full fitted render for the requested
 * pose — a single transparent image. The mascot is never bald: every set
 * render wears his signature hood-style graduation cap in the set's colour.
 *
 * Used for the PRIMARY mascot presentations so the buddy appears
 * consistently: Study timer (default theme), Home hero, Mascot screen,
 * Profile avatar spot, share cards (via [CelebrateBuddyMascot]).
 *
 * Themed mascots are untouched: when a theme is active its mascot wins on
 * the themed timer — the buddy outfit only applies to default presentations.
 *
 * @param pose which set render to show (STANDING/STUDY/CELEBRATE)
 * @param pendingOutfit when non-null, renders this outfit SET ID instead of
 * the saved one (Mascot screen live preview while browsing; unsaved browsing
 * never leaks elsewhere).
 * @param idleAnimation when true (default) the buddy gently "breathes":
 * an infinite transform-only loop (scale 1.0<->1.025 + tiny Y bob, ~3s
 * cycle) applied to the equipped outfit's own render, so EVERY outfit set
 * inherits it with zero per-outfit work. Transform-only: layout size and
 * placement never change (owner rule: mascot placement FROZEN).
 */
@Composable
fun BuddyMascot(
    modifier: Modifier = Modifier,
    pose: MascotPose = MascotPose.STUDY,
    pendingOutfit: String? = null,
    idleAnimation: Boolean = true
) {
    val equippedId by MascotWardrobe.equipped.collectAsState()
    val set = MascotWardrobe.currentSet().let { saved ->
        if (pendingOutfit != null) outfitSetById(pendingOutfit) ?: saved
        else outfitSetById(equippedId) ?: saved
    }

    // v1.2.3: alive-by-code idle loop (owner order 2026-10-16). A video would
    // freeze ONE outfit and break the outfit system; this animates the
    // equipped set's existing image, so all 10 sets inherit it automatically.
    val idleTransition = rememberInfiniteTransition(label = "buddy_idle")
    val breatheScale by idleTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val bobOffset by idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    // All set renders share one canvas (819x1024, transparent background,
    // same 0.8 aspect ratio as the original 1408x1760 art).
    Box(
        modifier = modifier
            .aspectRatio(1408f / 1760f)
            .then(
                if (idleAnimation) {
                    Modifier.graphicsLayer {
                        scaleX = breatheScale
                        scaleY = breatheScale
                        translationY = bobOffset
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = set.resFor(pose)),
            contentDescription = "Your mascot buddy",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
    }
}
