package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusSessionEntity
import com.example.ui.theme.AppleLinearFontFamily
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme

/**
 * Returns a warm, conversational copy variation for the post-session donation prompt.
 * Rotates through 6 natural variations, incorporating live user focus stats.
 */
fun getSessionDonationCopy(
    rotationIndex: Int,
    sessionMinutes: Int,
    currentStreak: Int = 0,
    totalFocusMinutes: Long
): String {
    val totalHours = maxOf(1, (totalFocusMinutes / 60).toInt())
    val mins = maxOf(1, sessionMinutes)

    return when (rotationIndex % 6) {
        0 -> "That's $mins minutes of real focus. 🌱 Focivo was built by one person who wanted a tool like this to exist — if it's helping you, a small bit of support keeps it growing."
        1 -> "Session complete! You're building something real here. This app is a solo project — every bit of support helps keep it alive and improving for students like you."
        2 -> "$mins minutes closer to your goals. If Focivo's been part of that journey, consider leaving something behind to help it keep going — totally up to you."
        3 -> "Nice work today. Building this app has been a one-person mission — if it's earned a place in your routine, a little support means a lot."
        4 -> "$mins minutes of deep focus logged! ✨ With $totalHours hours completed so far, you're making steady progress. Focivo is run independently by a solo dev — if you find value in it, any small contribution helps it stay ad-free."
        else -> "Another session done! $totalHours hours of focus completed so far. If Focivo is helping you stay on track, consider supporting its solo creator to keep updates coming."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDonationPromptSheet(
    session: FocusSessionEntity,
    rotationIndex: Int,
    currentStreak: Int,
    totalFocusMinutes: Long,
    onSupportClick: () -> Unit,
    onDismiss: () -> Unit,
    onNeverShowAgain: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = isAppInDarkTheme()

    val bgSurface = if (isDark) Color(0xFF1D221C) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val cardBorder = if (isDark) Color(0x30FFFFFF) else Color(0xFFDCE6D2)

    val minsSpent = maxOf(1, session.durationSeconds / 60)
    val promptCopy = getSessionDonationCopy(
        rotationIndex = rotationIndex,
        sessionMinutes = minsSpent,
        currentStreak = currentStreak,
        totalFocusMinutes = totalFocusMinutes
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 8.dp)
                .testTag("session_donation_prompt_sheet"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Visual: Grateful Mascot Pose
            RegainMascotView(
                width = 110.dp,
                height = 150.dp,
                pose = MascotPose.GRATEFUL
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Keep Focivo Growing 🌱",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = AppleLinearFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    fontSize = 20.sp,
                    letterSpacing = (-0.3).sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Stat Highlight Chips (Session duration + Streak)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF262C24) else Color(0xFFEFF8E6))
                        .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "$minsSpent min session",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = AppleLinearFontFamily,
                                color = textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                val totalHours = (totalFocusMinutes / 60).toInt()
                if (totalHours > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF262C24) else Color(0xFFEFF8E6))
                        .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${totalHours}h total focused",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = AppleLinearFontFamily,
                                    color = textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Copy Text (Warm, conversational message)
            Text(
                text = promptCopy,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = AppleLinearFontFamily,
                    color = textPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: Equal visual weight & prominence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Secondary Action: Low pressure dismiss option
                GlassButton(
                    text = "Maybe later",
                    onClick = onDismiss,
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )

                // Primary Action: Support Focivo
                GlassButton(
                    text = "Support Focivo 💚",
                    onClick = onSupportClick,
                    isPrimary = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Don't show again option
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNeverShowAgain() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("donation_dont_show_again_btn"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Don't show this again",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = AppleLinearFontFamily,
                        color = textSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

