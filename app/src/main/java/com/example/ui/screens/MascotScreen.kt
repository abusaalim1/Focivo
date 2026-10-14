package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.BuddyMascot
import com.example.ui.components.pressFeedback
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.R
import com.example.util.MascotLevel
import com.example.util.MascotPose
import com.example.util.MascotWardrobe
import com.example.util.WARDROBE_ITEMS
import com.example.util.WardrobeCategory
import com.example.util.WardrobeItem
import com.example.util.levelForPoints
import com.example.util.progressToNextLevel
import com.example.util.wardrobeItemById

/**
 * v1.1 — Mascot Buddy screen.
 *
 * Big buddy preview (level badge + points progress bar) + wardrobe below.
 * Tapping an item updates the BIG preview IMMEDIATELY in-place via a local
 * *pending* outfit — "Save outfit" persists it (MascotWardrobe). Leaving
 * without saving changes nothing elsewhere.
 */
@Composable
fun MascotScreen(
    focusPoints: Int,
    onBack: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val cardBg = if (isDark) Color(0xFF1D221C) else Color.White
    val cardBorder = if (isDark) Color(0x228CE000) else Color(0xFFDCE6D2)
    val screenBg = if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7)
    val lime = Color(0xFF8FD400)

    val savedIds by MascotWardrobe.equipped.collectAsState()
    // Pending outfit — local to this screen until Save is tapped.
    var pendingIds by remember(savedIds) {
        mutableStateOf(savedIds.toMap())
    }
    var selectedCategory by remember { mutableStateOf(WardrobeCategory.CAPS) }
    var lockedDialogItem by remember { mutableStateOf<WardrobeItem?>(null) }
    var justSaved by remember { mutableStateOf(false) }

    val level = levelForPoints(focusPoints)
    val (levelProgress, pointsNeeded) = progressToNextLevel(focusPoints)
    val hasChanges = pendingIds != savedIds
    val pendingOutfit: Map<WardrobeCategory, WardrobeItem> =
        pendingIds.mapNotNull { (cat, id) -> wardrobeItemById(id)?.let { cat to it } }.toMap()

    lockedDialogItem?.let { item ->
        WardrobeLockedDialog(item = item, focusPoints = focusPoints, onDismiss = { lockedDialogItem = null })
    }

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
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = textPrimary,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF2A331F) else Color(0xFFF1F8E4))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$focusPoints",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = textPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Hero: big buddy + level
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
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
                        pendingOutfit = pendingOutfit,
                        modifier = Modifier.width(190.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Level badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(lime)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = level.displayName.uppercase(),
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.5.sp,
                            color = NearBlack
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    // Progress to next level
                    if (pointsNeeded != null) {
                        val next = level.next()!!
                        Text(
                            text = "$pointsNeeded pts to ${next.displayName}",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = levelProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = lime,
                            trackColor = if (isDark) Color(0xFF2A331F) else Color(0xFFE8EFE0)
                        )
                    } else {
                        Text(
                            text = "Max level reached — looking sharp.",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    // Save outfit
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (hasChanges) lime else cardBorder.copy(alpha = 0.5f))
                            .pressFeedback()
                            .clickable(enabled = hasChanges) {
                                MascotWardrobe.saveOutfit(pendingIds)
                                justSaved = true
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                justSaved && !hasChanges -> "Outfit saved"
                                hasChanges -> "Save outfit"
                                else -> "Outfit saved"
                            },
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (hasChanges) NearBlack else textSecondary
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

            // Wardrobe header
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "WARDROBE",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        color = textSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WardrobeCategory.values().forEach { cat ->
                            val selected = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) lime else cardBg)
                                    .border(1.dp, if (selected) lime else cardBorder, RoundedCornerShape(14.dp))
                                    .pressFeedback()
                                    .clickable { selectedCategory = cat }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat.displayName,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (selected) NearBlack else textPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Item grid
            items(
                WARDROBE_ITEMS.filter { it.category == selectedCategory },
                key = { it.id }
            ) { item ->
                val unlocked = item.isUnlocked(focusPoints)
                val equippedId = pendingIds[item.category]
                val isEquipped = equippedId == item.id
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBg)
                        .border(
                            1.5.dp,
                            if (isEquipped) lime else cardBorder,
                            RoundedCornerShape(18.dp)
                        )
                        .pressFeedback()
                        .clickable {
                            if (!unlocked) {
                                lockedDialogItem = item
                            } else {
                                justSaved = false
                                pendingIds = if (isEquipped) {
                                    pendingIds - item.category
                                } else {
                                    pendingIds + (item.category to item.id)
                                }
                            }
                        }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Thumbnail: the item worn on the plain buddy (mini stack),
                            // so users see exactly how the item looks on the mascot.
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = R.drawable.mascot_base_standing),
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                            val thumbOverlay = item.overlayFor(MascotPose.STANDING)
                            if (thumbOverlay != null) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = thumbOverlay.resId),
                                    contentDescription = item.name,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        if (isEquipped) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(lime),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Equipped",
                                    tint = NearBlack,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else if (!unlocked) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A2E2A).copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.name,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (!unlocked) {
                        Text(
                            text = item.lockReason(focusPoints) ?: "",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp,
                            color = Color(0xFFFFD54F),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    } else {
                        Text(
                            text = if (isEquipped) "Wearing" else "Tap to wear",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 10.5.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WardrobeLockedDialog(
    item: WardrobeItem,
    focusPoints: Int,
    onDismiss: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val level = levelForPoints(focusPoints)
    val body = when {
        level < item.requiredLevel ->
            "${item.name} needs ${item.requiredLevel.displayName} level " +
                "(${item.requiredLevel.threshold} Focus Points) — you are ${level.displayName} " +
                "with $focusPoints points. Points are never spent; keep focusing to unlock it."
        else ->
            "Collect ${(item.unlockPoints - focusPoints).coerceAtLeast(1)} more Focus Points " +
                "to unlock ${item.name}. You earn 1 point per focused minute — points are never spent."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Locked item",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = textPrimary
            )
        },
        text = {
            Text(
                text = body,
                fontFamily = PoppinsFontFamily,
                fontSize = 13.sp,
                color = textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Got it",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RegainLimePrimary
                )
            }
        }
    )
}
