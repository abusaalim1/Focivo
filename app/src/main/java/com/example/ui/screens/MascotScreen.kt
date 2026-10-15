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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.MascotOutfitSet
import com.example.util.MascotWardrobe
import com.example.util.OUTFIT_SETS
import com.example.util.outfitSetById

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
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val cardBg = if (isDark) Color(0xFF1D221C) else Color.White
    val cardBorder = if (isDark) Color(0x228CE000) else Color(0xFFE4E9DE)
    val screenBg = if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7)
    val lime = Color(0xFF8FD400)

    val savedSetId by MascotWardrobe.equipped.collectAsState()
    // Pending set — local to this screen until Save is tapped.
    var pendingSetId by remember(savedSetId) { mutableStateOf(savedSetId) }
    var justSaved by remember { mutableStateOf(false) }

    val hasChanges = pendingSetId != savedSetId
    val pendingSet: MascotOutfitSet =
        outfitSetById(pendingSetId) ?: MascotWardrobe.currentSet()

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
                    // Save outfit
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (hasChanges) lime else cardBorder.copy(alpha = 0.6f))
                            .pressFeedback()
                            .clickable(enabled = hasChanges) {
                                MascotWardrobe.saveSet(pendingSetId)
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

            // Outfits header
            item(span = { GridItemSpan(2) }) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "OUTFITS",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        color = textSecondary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Set grid
            items(OUTFIT_SETS, key = { it.id }) { set ->
                val isEquipped = pendingSetId == set.id
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
                            pendingSetId = set.id
                        }
                        .padding(12.dp),
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
                        )
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
                        text = if (isEquipped) "Wearing" else "Tap to try",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 10.5.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
