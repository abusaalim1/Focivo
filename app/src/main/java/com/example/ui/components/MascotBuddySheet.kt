package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import java.util.Locale

private data class BuddyStageInfo(
    val stage: Int,
    val name: String,
    val imageResId: Int,
    val thresholdHours: Int,
    val thresholdMinutes: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MascotBuddySheet(
    buddyGrowthStage: Int = 1,
    buddyTotalFocusMinutes: Int = 0,
    currentStreak: Int = 0,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = isAppInDarkTheme()

    val cardBg = if (isDark) Color(0xFF1B221B) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0x358CE000) else Color(0xFFE2EBD6)
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight

    val currentStage = buddyGrowthStage.coerceIn(1, 3)

    val stages = listOf(
        BuddyStageInfo(1, "Sprout", R.drawable.mascot_level1, 0, 0),
        BuddyStageInfo(2, "Growing", R.drawable.mascot_level2, 2, 120),
        BuddyStageInfo(3, "Master", R.drawable.mascot_level3, 10, 600)
    )

    // Growth stage calculation (strictly cumulative and never regressing)
    val effectiveStage = when {
        buddyTotalFocusMinutes >= 600 -> 3
        buddyTotalFocusMinutes >= 120 -> 2
        else -> 1
    }
    val displayStage = maxOf(currentStage, effectiveStage)
    val currentStageInfo = stages.first { it.stage == displayStage }

    // Total hours calculated
    val totalHours = buddyTotalFocusMinutes / 60.0
    val totalHoursStr = if (totalHours % 1.0 == 0.0) {
        "${totalHours.toInt()} hours together."
    } else {
        String.format(Locale.US, "%.1f hours together.", totalHours)
    }

    // Progress calculation toward next stage
    val (progress, hoursUntilNext) = when (displayStage) {
        1 -> {
            val prog = (buddyTotalFocusMinutes / 120f).coerceIn(0f, 1f)
            val remainingHours = maxOf(0.0, (120 - buddyTotalFocusMinutes) / 60.0)
            Pair(prog, remainingHours)
        }
        2 -> {
            val prog = ((buddyTotalFocusMinutes - 120f) / 480f).coerceIn(0f, 1f)
            val remainingHours = maxOf(0.0, (600 - buddyTotalFocusMinutes) / 60.0)
            Pair(prog, remainingHours)
        }
        else -> Pair(1.0f, 0.0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) Color(0xFF16181D) else Color(0xFFF8FAFC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RegainLimeContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = RegainLimeDeepText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "BUDDY LEVEL",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            color = RegainLimeDeepText,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Mascot Level Image Showcase
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(RegainLimeContainer.copy(alpha = 0.4f))
                    .border(3.dp, RegainLimePrimary, CircleShape)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = currentStageInfo.imageResId),
                    contentDescription = "Buddy Level ${currentStageInfo.stage} - ${currentStageInfo.name}",
                    modifier = Modifier.size(126.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title & Stage Name
            Text(
                text = "${currentStageInfo.name} · Study Companion",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    fontSize = 22.sp
                )
            )

            Text(
                text = totalHoursStr,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    color = textSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Bar to Next Growth Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (displayStage < 3) "Progress to Stage ${displayStage + 1}" else "Peak Growth Reached",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 13.sp
                            )
                        )

                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = RegainLimeDeepText,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = RegainLimePrimary,
                        trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (displayStage < 3) {
                            val remStr = if (hoursUntilNext % 1.0 == 0.0) "${hoursUntilNext.toInt()}" else String.format(Locale.US, "%.1f", hoursUntilNext)
                            "$remStr hours of focus until next growth stage"
                        } else {
                            "Your Study Buddy is fully evolved and at peak growth!"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stage Previews (Locked / Unlocked)
            Text(
                text = "GROWTH STAGES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    color = textSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                textAlign = TextAlign.Start
            )

            stages.forEach { stageInfo ->
                val isUnlocked = stageInfo.stage <= displayStage
                val isCurrent = stageInfo.stage == displayStage

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isCurrent) RegainLimeContainer.copy(alpha = 0.35f) else cardBg
                        )
                        .border(
                            width = if (isCurrent) 1.5.dp else 1.dp,
                            color = if (isCurrent) RegainLimePrimary else cardBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Stage Image
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isUnlocked) RegainLimeContainer.copy(alpha = 0.5f)
                                    else if (isDark) Color(0xFF1E293B)
                                    else Color(0xFFE2E8F0)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUnlocked) {
                                Image(
                                    painter = painterResource(id = stageInfo.imageResId),
                                    contentDescription = "Stage ${stageInfo.stage}",
                                    modifier = Modifier.size(46.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(id = stageInfo.imageResId),
                                        contentDescription = "Locked Stage ${stageInfo.stage}",
                                        modifier = Modifier.size(46.dp),
                                        contentScale = ContentScale.Fit,
                                        colorFilter = ColorFilter.tint(
                                            if (isDark) Color(0xFF334155) else Color(0xFF94A3B8),
                                            BlendMode.SrcIn
                                        )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.65f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Stage ${stageInfo.stage} · ${stageInfo.name}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 14.sp
                                    )
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(RegainLimePrimary)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = if (isUnlocked) {
                                    if (stageInfo.stage == 1) "Initial companion level" else "Unlocked at ${stageInfo.thresholdHours} focus hours"
                                } else {
                                    "Reach ${stageInfo.thresholdHours} hours to unlock"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Close Button
            GlassButton(
                text = "Back to Study",
                onClick = onDismiss,
                isPrimary = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
