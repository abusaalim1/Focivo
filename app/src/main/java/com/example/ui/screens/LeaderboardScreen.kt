package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LeaderboardUser
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainGoldStar
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight

@Composable
fun LeaderboardScreen(
    users: List<LeaderboardUser>,
    currentUserName: String,
    currentUserPoints: Int,
    currentUserStreak: Int,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

    var selectedFilter by remember { mutableStateOf("This Week") }

    val currentEntry = remember(currentUserName, currentUserPoints, currentUserStreak) {
        LeaderboardUser(
            id = "current_user",
            displayName = currentUserName.ifBlank { "You" },
            studySeconds = (currentUserPoints * 60L).coerceAtLeast(0L),
            streak = currentUserStreak.coerceAtLeast(1),
            subjectTag = "Active Focus",
            isCurrentUser = true
        )
    }

    val effectiveUsers = remember(users, currentEntry) {
        val hasCurrent = users.any { it.isCurrentUser || it.displayName.equals(currentUserName, ignoreCase = true) }
        val combined = if (hasCurrent) {
            users.map { if (it.displayName.equals(currentUserName, ignoreCase = true)) it.copy(isCurrentUser = true) else it }
        } else {
            listOf(currentEntry) + users
        }
        combined.sortedByDescending { it.studySeconds }
    }

    val top1 = effectiveUsers.getOrNull(0)
    val top2 = effectiveUsers.getOrNull(1)
    val top3 = effectiveUsers.getOrNull(2)
    val restOfUsers = effectiveUsers.drop(3)

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Regain Soft Pale Lime/White Gradient Background
        AuroraBackground(
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Leaderboard",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 24.sp
                        )
                    )
                    Text(
                        text = "Study together & stay disciplined",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                // Global rank pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(cardBg)
                        .border(1.dp, cardBorder, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = RegainGoldStar,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Global",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Time Filter Pills: "Today", "This Week", "All Time"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("Today", "This Week", "All Time").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) RegainLimePrimary else cardBg
                            )
                            .border(
                                1.dp,
                                if (isSelected) RegainLimePrimary else cardBorder,
                                CircleShape
                            )
                            .clickable { selectedFilter = filter }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                color = if (isSelected) NearBlack else textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Content: Podium + Ranks List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Podium Section (Top 3 with Crowns)
                item {
                    PodiumSection(top1 = top1, top2 = top2, top3 = top3)
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "RANKINGS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = SecondaryTextLight,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            fontSize = 11.sp
                        )
                    )
                }

                // Ranked List
                itemsIndexed(restOfUsers) { index, user ->
                    LeaderboardUserRow(
                        rank = index + 4,
                        user = user,
                        isCurrentUser = user.isCurrentUser
                    )
                }
            }
        }
    }
}

@Composable
private fun PodiumSection(
    top1: LeaderboardUser?,
    top2: LeaderboardUser?,
    top3: LeaderboardUser?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // Rank 2 (Left)
            top2?.let { user ->
                PodiumColumn(
                    user = user,
                    rank = 2,
                    podiumHeight = 110.dp,
                    color = Color(0xFF78909C),
                    modifier = Modifier.weight(1f)
                )
            }

            // Rank 1 (Center - Tallest)
            top1?.let { user ->
                PodiumColumn(
                    user = user,
                    rank = 1,
                    podiumHeight = 145.dp,
                    color = RegainGoldStar,
                    modifier = Modifier.weight(1.15f)
                )
            }

            // Rank 3 (Right)
            top3?.let { user ->
                PodiumColumn(
                    user = user,
                    rank = 3,
                    podiumHeight = 90.dp,
                    color = Color(0xFFFB8C00),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    user: LeaderboardUser,
    rank: Int,
    podiumHeight: androidx.compose.ui.unit.Dp,
    color: Color,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Crown for 1st place
        if (rank == 1) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = RegainGoldStar,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Avatar
        Box(
            modifier = Modifier
                .size(if (rank == 1) 54.dp else 46.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = if (rank == 1) 18.sp else 15.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Name
        Text(
            text = if (user.isCurrentUser) "You" else user.displayName,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                fontSize = 12.sp
            ),
            maxLines = 1
        )

        // Time
        Text(
            text = formatHoursMins(user.studySeconds),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PoppinsFontFamily,
                color = textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Podium Pillar with Glass Card styling
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(podiumHeight),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .fillMaxWidth(0.6f)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 22.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardUserRow(
    rank: Int,
    user: LeaderboardUser,
    isCurrentUser: Boolean
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_row_$rank"),
        shape = RoundedCornerShape(20.dp),
        border = if (isCurrentUser) androidx.compose.foundation.BorderStroke(1.5.dp, RegainLimePrimary) else androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isCurrentUser) RegainLimeContainer.copy(alpha = 0.35f) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Rank number
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentUser) RegainLimeDeepText else textPrimary,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier.width(28.dp)
                )

                // Avatar
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrentUser) RegainLimePrimary else (if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF0F4EC))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentUser) NearBlack else textPrimary,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isCurrentUser) "${user.displayName} (You)" else user.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCurrentUser) RegainLimeDeepText else textPrimary,
                                fontSize = 14.sp
                            )
                        )
                        if (user.streak > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFFF7043),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${user.streak}d",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Color(0xFFFF7043),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                    Text(
                        text = user.subjectTag,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = if (isCurrentUser) RegainLimeDeepText.copy(alpha = 0.8f) else textSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Total Study Time
            Text(
                text = formatHoursMins(user.studySeconds),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentUser) RegainLimeDeepText else textPrimary,
                    fontSize = 14.sp
                )
            )
        }
    }
}
}

private fun formatHoursMins(seconds: Long): String {
    val totalMins = seconds / 60
    val hours = totalMins / 60
    val mins = totalMins % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}
