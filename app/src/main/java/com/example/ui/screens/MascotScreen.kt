package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.BuddyMascot
import com.example.ui.components.OutfitUnlockPreview
import com.example.ui.components.PendingUnlock
import com.example.ui.components.UnlockCelebrationOverlay
import com.example.ui.components.formatPoints
import com.example.ui.components.pressFeedback
import com.example.ui.components.rememberNewlyUnlockedIds
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.MascotOutfitSet
import com.example.util.MascotWardrobe
import com.example.util.OUTFIT_SETS
import com.example.util.outfitSetById

// v1.2.3 design-unification — owner's clean-redesign language:
// #0A0A0B bg, #131315 cards, #1F1F22 lines, #B6F13A lime, #76767C muted,
// Poppins body + Instrument Serif italic for the page headline.
private val MascotLime = Color(0xFFB6F13A)
private val MascotGold = Color(0xFFF5A524)

private val MascotSerifItalic = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

/**
 * v1.2 — Mascot Buddy screen, OUTFIT SETS edition.
 *
 * A calm Outfits collection: big buddy preview + Save outfit, then 10
 * complete outfit set cards (each set wears the hood-style graduation cap
 * in the set's colour — picking a set IS the cap colour change).
 *
 * Tapping a set updates the BIG preview IMMEDIATELY in-place via a local
 * *pending* set id — "Save outfit" persists it (MascotWardrobe). Leaving
 * without saving changes nothing elsewhere. Everything is free.
 */
@Composable
fun MascotScreen(
    focusPoints: Int,
    onBack: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) Color(0xFFF4F4F5) else NearBlack
    val textSecondary = if (isDark) Color(0xFF76767C) else SecondaryTextLight
    val cardBg = if (isDark) Color(0xFF131315) else Color.White
    val cardBorder = if (isDark) Color(0xFF1F1F22) else Color(0xFFE4E9DE)
    val screenBg = if (isDark) Color(0xFF0A0A0B) else Color(0xFFFAFBF7)
    val lime = MascotLime
    val context = androidx.compose.ui.platform.LocalContext.current
    // v1.2.4: owner admin account unlocks every outfit set — single choke point.
    val isAdmin = remember(context) { com.example.util.ContentLocks.isAdminUnlocked(context) }

    val savedSetId by MascotWardrobe.equipped.collectAsState()
    // v1.2.1 re-lock: the equipped set can never be a locked one.
    LaunchedEffect(focusPoints) {
        MascotWardrobe.ensureEquippedUnlocked(focusPoints, isAdmin)
    }
    // Motion Lab "Theme unlock": celebrate genuinely-new outfit-set threshold
    // crossings (threshold model — points are never spent). First observation
    // seeds silently (persisted), so only real crossings celebrate.
    val outfitUnlocks = rememberNewlyUnlockedIds(
        allIds = OUTFIT_SETS.map { it.id },
        isUnlocked = { id -> outfitSetById(id)?.isUnlocked(focusPoints, isAdmin) == true },
        focusPoints = focusPoints,
        seenKey = "unlock_seen_outfit_sets"
    )
    var outfitUnlockQueue by remember { mutableStateOf(listOf<PendingUnlock>()) }
    var activeOutfitUnlock by remember { mutableStateOf<PendingUnlock?>(null) }

    LaunchedEffect(outfitUnlocks.newlyUnlocked) {
        val fresh = outfitUnlocks.newlyUnlocked.mapNotNull { id ->
            outfitSetById(id)?.let { set ->
                PendingUnlock(
                    title = set.name,
                    subtitle = "${formatPoints(set.unlockPoints)} Focus Points reached",
                    accent = Color(set.accentColor),
                    preview = { OutfitUnlockPreview(set) }
                )
            }
        }
        if (fresh.isNotEmpty()) {
            outfitUnlockQueue = outfitUnlockQueue + fresh
            outfitUnlocks.consume()
        }
    }
    LaunchedEffect(outfitUnlockQueue, activeOutfitUnlock) {
        if (activeOutfitUnlock == null && outfitUnlockQueue.isNotEmpty()) {
            activeOutfitUnlock = outfitUnlockQueue.first()
            outfitUnlockQueue = outfitUnlockQueue.drop(1)
        }
    }
    // Pending set — local to this screen until Save is tapped.
    var pendingSetId by remember(savedSetId) { mutableStateOf(savedSetId) }
    var justSaved by remember { mutableStateOf(false) }
    // Locked-set info dialog (tap a locked card -> how many points are needed).
    var lockedInfoSet by remember { mutableStateOf<MascotOutfitSet?>(null) }

    val hasChanges = pendingSetId != savedSetId
    val pendingSet: MascotOutfitSet =
        outfitSetById(pendingSetId) ?: MascotWardrobe.currentSet()
    val pendingUnlocked = pendingSet.isUnlocked(focusPoints, isAdmin)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .pressFeedback()
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary
                )
            }
            Text(
                text = "Mascot Buddy",
                fontFamily = MascotSerifItalic,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = textPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero: big buddy preview + save
            item(span = { GridItemSpan(2) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BuddyMascot(
                        pendingOutfit = pendingSet.id,
                        modifier = Modifier.width(190.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = pendingSet.name,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cap colour follows the outfit",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    // Save outfit (v1.2.1: only for unlocked sets)
                    val canSave = hasChanges && pendingUnlocked
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (canSave) lime else cardBorder.copy(alpha = 0.6f))
                            .pressFeedback()
                            .clickable(enabled = canSave) {
                                MascotWardrobe.saveSet(pendingSetId)
                                justSaved = true
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                !pendingUnlocked -> "Locked — earn ${"%,d".format(pendingSet.unlockPoints)} pts"
                                justSaved && !hasChanges -> "Outfit saved"
                                hasChanges -> "Save outfit"
                                else -> "Outfit saved"
                            },
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (canSave) NearBlack else textSecondary
                        )
                    }
                    if (hasChanges) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Preview only — tap Save outfit to dress your buddy everywhere.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Outfits header
            item(span = { GridItemSpan(2) }) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "OUTFITS",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.76.sp,
                        color = textSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Set grid (v1.2.1: locked sets show a lock badge + required
            // points; tapping one explains the gap and never changes the
            // preview/equipped set).
            items(OUTFIT_SETS, key = { it.id }) { set ->
                val isEquipped = pendingSetId == set.id
                val isLocked = !set.isUnlocked(focusPoints, isAdmin)
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(
                            1.5.dp,
                            if (isEquipped) lime else cardBorder,
                            RoundedCornerShape(20.dp)
                        )
                        .pressFeedback()
                        .clickable {
                            justSaved = false
                            if (isLocked) {
                                lockedInfoSet = set
                            } else {
                                pendingSetId = set.id
                            }
                        }
                        .padding(12.dp)
                        .testTag("mascot_set_card_${set.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Image(
                            painter = painterResource(id = set.standingRes),
                            contentDescription = set.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(120.dp)
                                .aspectRatio(1408f / 1760f)
                                .graphicsLayer { alpha = if (isLocked) 0.45f else 1f }
                        )
                        if (isLocked) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC1A221B))
                                    .border(1.dp, MascotGold, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = MascotGold,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        } else if (isEquipped) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(lime),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = NearBlack,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = set.name,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        minLines = 2
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            isLocked -> "${"%,d".format(set.unlockPoints)} pts to unlock"
                            isEquipped -> "Wearing"
                            else -> "Tap to try"
                        },
                        fontFamily = PoppinsFontFamily,
                        fontSize = 10.5.sp,
                        color = if (isLocked) MascotGold else textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // v1.2.1: locked-set tap -> how many more Focus Points are needed.
    // Dismissing changes nothing (preview + equipped set untouched).
    lockedInfoSet?.let { locked ->
        val needed = (locked.unlockPoints - focusPoints).coerceAtLeast(0)
        androidx.compose.ui.window.Dialog(onDismissRequest = { lockedInfoSet = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MascotGold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MascotGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = locked.name,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Earn ${"%,d".format(needed)} more Focus Points to unlock this outfit (1 pt per focused minute). Your current outfit stays on.",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(lime)
                        .clickable { lockedInfoSet = null }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Got it",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = NearBlack
                    )
                }
            }
        }
    }

    // Unlock celebration overlay — draws above the screen; the locked-set
    // info dialog above is unchanged (it explains locked sets, this
    // celebrates actual unlocks).
    activeOutfitUnlock?.let { unlock ->
        UnlockCelebrationOverlay(
            title = unlock.title,
            subtitle = unlock.subtitle,
            accent = unlock.accent,
            preview = unlock.preview,
            onDone = { activeOutfitUnlock = null }
        )
    }
}
