package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.HallOfFameItem
import com.example.data.LeaderboardDateUtils
import com.example.data.LeaderboardUser
import com.example.data.model.FocusSessionEntity
import com.example.ui.components.AuroraBackground
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainGoldStar
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import java.util.Calendar

@Composable
fun LeaderboardAvatar(
    avatarUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    isCurrentUser: Boolean = false,
    textSizeSp: Int = 15
) {
    val bitmap = remember(avatarUrl) {
        if (!avatarUrl.isNullOrBlank() && !avatarUrl.startsWith("http://") && !avatarUrl.startsWith("https://")) {
            try {
                val clean = if (avatarUrl.contains(",")) avatarUrl.substringAfter(",") else avatarUrl
                val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    val initials = remember(displayName) {
        val trimmed = displayName.trim()
        val parts = trimmed.split(" ").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
        } else if (trimmed.isNotEmpty()) {
            trimmed.take(1).uppercase()
        } else {
            "U"
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = displayName,
            modifier = modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = displayName,
            modifier = modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (isCurrentUser) Color(0xFF0D1B05) else Color.White,
                fontSize = textSizeSp.sp
            )
        )
    }
}

@Composable
fun LeaderboardScreen(
    users: List<LeaderboardUser>,
    hallOfFame: List<HallOfFameItem> = emptyList(),
    currentUserName: String,
    currentUserPhotoUrl: String? = null,
    currentUserPoints: Int,
    currentUserStreak: Int,
    sessions: List<FocusSessionEntity> = emptyList(),
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val cardBg = if (isDark) Color(0xFF1B221B) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0x358CE000) else Color(0xFFDCE6D2)
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight

    var selectedFilter by remember { mutableStateOf("This Week") }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    // Time boundaries for Today and This Week
    val todayStart = remember {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    }

    val weekStartTimestamp = remember {
        val cal = Calendar.getInstance().apply {
            val dayOfWeek = get(Calendar.DAY_OF_WEEK)
            val daysToSubtract = (dayOfWeek - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_MONTH, -daysToSubtract)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    }

    val currentMonday = remember {
        LeaderboardDateUtils.getCurrentWeekMonday()
    }

    // Calculate current user's local study seconds based strictly on real sessions
    val myTodayStudySeconds = remember(sessions, todayStart) {
        sessions.filter { it.completedAt >= todayStart }.sumOf { it.durationSeconds.toLong() }
    }

    val myWeeklyStudySeconds = remember(sessions, weekStartTimestamp) {
        sessions.filter { it.completedAt >= weekStartTimestamp }.sumOf { it.durationSeconds.toLong() }
    }

    val myAllTimeStudySeconds = remember(sessions) {
        sessions.sumOf { it.durationSeconds.toLong() }
    }

    val currentEntry = remember(
        currentUserName,
        currentUserPhotoUrl,
        currentUserPoints,
        currentUserStreak,
        myTodayStudySeconds,
        myWeeklyStudySeconds,
        myAllTimeStudySeconds,
        users
    ) {
        val remoteMe = users.firstOrNull { it.isCurrentUser || it.displayName.equals(currentUserName, ignoreCase = true) }
        val effectiveAllTime = if (myAllTimeStudySeconds > 0) myAllTimeStudySeconds else (remoteMe?.studySeconds ?: 0L)
        val effectiveWeekly = if (myWeeklyStudySeconds > 0) myWeeklyStudySeconds else (remoteMe?.weeklyStudySeconds ?: 0L)
        val effectiveToday = if (myTodayStudySeconds > 0) myTodayStudySeconds else (remoteMe?.todayStudySeconds ?: 0L)

        LeaderboardUser(
            id = remoteMe?.id ?: "current_user",
            displayName = currentUserName.ifBlank { "You" },
            studySeconds = effectiveAllTime,
            weeklyStudySeconds = effectiveWeekly,
            todayStudySeconds = effectiveToday,
            currentWeekStart = currentMonday,
            streak = maxOf(remoteMe?.streak ?: 1, currentUserStreak.coerceAtLeast(1)),
            subjectTag = remoteMe?.subjectTag ?: "Active Focus",
            avatarUrl = currentUserPhotoUrl ?: remoteMe?.avatarUrl,
            isCurrentUser = true
        )
    }

    val effectiveUsers = remember(users, currentEntry, selectedFilter) {
        val remoteWithoutMe = users.filterNot { it.isCurrentUser || it.displayName.equals(currentUserName, ignoreCase = true) }
        val allUsers = listOf(currentEntry) + remoteWithoutMe

        when (selectedFilter) {
            "Today" -> allUsers.sortedByDescending { it.todayStudySeconds }
            "This Week" -> allUsers.sortedByDescending { it.weeklyStudySeconds }
            "All Time" -> allUsers.sortedByDescending { it.studySeconds }
            else -> allUsers.sortedByDescending { it.weeklyStudySeconds }
        }
    }

    val top1 = effectiveUsers.getOrNull(0)
    val top2 = effectiveUsers.getOrNull(1)
    val top3 = effectiveUsers.getOrNull(2)
    val restOfUsers = effectiveUsers.drop(3)

    Box(
        modifier = modifier.fillMaxSize()
    ) {
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
                        text = if (selectedFilter == "Hall of Fame") "Weekly champions enshrined in history" else "Study together & stay disciplined",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Refresh button
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(cardBg)
                            .border(1.dp, cardBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Leaderboard",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
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
            }

            // Time Filter Pills: "Today", "This Week", "All Time", "Hall of Fame"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Today", "This Week", "All Time", "Hall of Fame").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val pillBg = if (isSelected) {
                        RegainLimePrimary
                    } else {
                        if (isDark) Color(0xFF1D241C) else Color(0xFFEFF5EA)
                    }
                    val pillBorder = if (isSelected) {
                        RegainLimePrimary
                    } else {
                        if (isDark) Color(0x358CE000) else Color(0xFFD6E2CB)
                    }
                    val pillTextColor = if (isSelected) {
                        Color(0xFF0D1B05)
                    } else {
                        if (isDark) Color(0xFFA0A89E) else Color(0xFF4A5546)
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(pillBg)
                            .border(1.dp, pillBorder, CircleShape)
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (filter == "Hall of Fame") {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = pillTextColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = pillTextColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content Area
            if (selectedFilter == "Hall of Fame") {
                HallOfFameSection(
                    hallOfFame = hallOfFame,
                    isDark = isDark,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            } else {
                // Podium + Ranked List for Today, This Week, All Time
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Podium Section (Top 3)
                    item {
                        PodiumSection(
                            top1 = top1,
                            top2 = top2,
                            top3 = top3,
                            selectedFilter = selectedFilter
                        )
                    }

                    // Explanatory note if brand new week and top 1 is at 0
                    if (selectedFilter == "This Week" && (top1?.weeklyStudySeconds ?: 0L) == 0L) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF1F271D) else Color(0xFFF2F8EC)
                                ),
                                border = BorderStroke(1.dp, if (isDark) Color(0x408CE000) else Color(0xFFD4E7C3))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "A new week has begun! Study this week to claim the #1 spot.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "RANKINGS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Ranked List for rank 4+
                    itemsIndexed(restOfUsers) { index, user ->
                        val userSeconds = when (selectedFilter) {
                            "Today" -> user.todayStudySeconds
                            "This Week" -> user.weeklyStudySeconds
                            "All Time" -> user.studySeconds
                            else -> user.weeklyStudySeconds
                        }
                        LeaderboardUserRow(
                            rank = index + 4,
                            user = user,
                            displaySeconds = userSeconds,
                            isCurrentUser = user.isCurrentUser,
                            selectedFilter = selectedFilter
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumSection(
    top1: LeaderboardUser?,
    top2: LeaderboardUser?,
    top3: LeaderboardUser?,
    selectedFilter: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Rank 2 (Left)
            top2?.let { user ->
                val seconds = when (selectedFilter) {
                    "Today" -> user.todayStudySeconds
                    "This Week" -> user.weeklyStudySeconds
                    "All Time" -> user.studySeconds
                    else -> user.weeklyStudySeconds
                }
                PodiumColumn(
                    user = user,
                    rank = 2,
                    displaySeconds = seconds,
                    podiumHeight = 115.dp,
                    selectedFilter = selectedFilter,
                    modifier = Modifier.weight(1f)
                )
            }

            // Rank 1 (Center - Tallest)
            top1?.let { user ->
                val seconds = when (selectedFilter) {
                    "Today" -> user.todayStudySeconds
                    "This Week" -> user.weeklyStudySeconds
                    "All Time" -> user.studySeconds
                    else -> user.weeklyStudySeconds
                }
                PodiumColumn(
                    user = user,
                    rank = 1,
                    displaySeconds = seconds,
                    podiumHeight = 150.dp,
                    selectedFilter = selectedFilter,
                    modifier = Modifier.weight(1.15f)
                )
            }

            // Rank 3 (Right)
            top3?.let { user ->
                val seconds = when (selectedFilter) {
                    "Today" -> user.todayStudySeconds
                    "This Week" -> user.weeklyStudySeconds
                    "All Time" -> user.studySeconds
                    else -> user.weeklyStudySeconds
                }
                PodiumColumn(
                    user = user,
                    rank = 3,
                    displaySeconds = seconds,
                    podiumHeight = 95.dp,
                    selectedFilter = selectedFilter,
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
    displaySeconds: Long,
    podiumHeight: androidx.compose.ui.unit.Dp,
    selectedFilter: String,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight

    // Celebratory badge for current week's leader while week is in progress
    val isLeadingThisWeek = selectedFilter == "This Week" && rank == 1 && displaySeconds > 0

    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Crown for 1st place
        if (rank == 1) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
        } else {
            Spacer(modifier = Modifier.height(18.dp))
        }

        // Avatar with rich gradient ring
        Box(
            modifier = Modifier
                .size(if (rank == 1) 52.dp else 44.dp)
                .clip(CircleShape)
                .background(
                    when (rank) {
                        1 -> Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFFFFA000)))
                        2 -> Brush.linearGradient(listOf(Color(0xFFCFD8DC), Color(0xFF78909C)))
                        else -> Brush.linearGradient(listOf(Color(0xFFFFCC80), Color(0xFFE65100)))
                    }
                )
                .border(2.dp, if (isDark) Color(0xFF1B221B) else Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            LeaderboardAvatar(
                avatarUrl = user.avatarUrl,
                displayName = user.displayName,
                isCurrentUser = user.isCurrentUser,
                textSizeSp = if (rank == 1) 18 else 15
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

        // Subject Tag
        Text(
            text = user.subjectTag,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PoppinsFontFamily,
                color = textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )

        // Time
        Text(
            text = formatHoursMins(displaySeconds),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = PoppinsFontFamily,
                color = if (isDark) RegainLimePrimary else Color(0xFF2E6300),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )

        // Real-time Celebratory acknowledgment for current week leader
        if (isLeadingThisWeek) {
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0x35FFA000) else Color(0xFFFFF3CD),
                border = BorderStroke(1.dp, Color(0xFFFFB300)),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👑 Leader",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = if (isDark) Color(0xFFFFD54F) else Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Distinct Podium Pedestal with High-Contrast Finish
        val pedestalGradient = when (rank) {
            1 -> if (isDark) {
                Brush.verticalGradient(listOf(Color(0x40FFD54F), Color(0xFF1E251D)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFFFFF7DB), Color(0xFFFFECC0)))
            }
            2 -> if (isDark) {
                Brush.verticalGradient(listOf(Color(0x35CFD8DC), Color(0xFF1E251D)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0)))
            }
            else -> if (isDark) {
                Brush.verticalGradient(listOf(Color(0x35FFCC80), Color(0xFF1E251D)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFFFFF0DC), Color(0xFFFFDFC0)))
            }
        }

        val pedestalBorderColor = when (rank) {
            1 -> if (isDark) Color(0xFFFFD54F).copy(alpha = 0.6f) else Color(0xFFFFB300)
            2 -> if (isDark) Color(0xFFCFD8DC).copy(alpha = 0.5f) else Color(0xFF94A3B8)
            else -> if (isDark) Color(0xFFFFCC80).copy(alpha = 0.5f) else Color(0xFFFF9800)
        }

        val pedestalRankNumberColor = when (rank) {
            1 -> if (isDark) Color(0xFFFFD54F) else Color(0xFF996B00)
            2 -> if (isDark) Color(0xFFCFD8DC) else Color(0xFF334155)
            else -> if (isDark) Color(0xFFFFCC80) else Color(0xFFB43B08)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(podiumHeight)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(pedestalGradient)
                .border(
                    width = 1.5.dp,
                    color = pedestalBorderColor,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Color Indicator Line
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .fillMaxWidth(0.5f)
                        .clip(CircleShape)
                        .background(
                            when (rank) {
                                1 -> Color(0xFFFFB300)
                                2 -> Color(0xFF78909C)
                                else -> Color(0xFFE65100)
                            }
                        )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = pedestalRankNumberColor,
                        fontSize = if (rank == 1) 24.sp else 20.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun LeaderboardUserRow(
    rank: Int,
    user: LeaderboardUser,
    displaySeconds: Long,
    isCurrentUser: Boolean,
    selectedFilter: String
) {
    val isDark = isAppInDarkTheme()

    val rowBg = if (isCurrentUser) {
        if (isDark) Color(0xFF1D2A1B) else Color(0xFFF3FBE8)
    } else {
        if (isDark) Color(0xFF1B221B) else Color(0xFFFFFFFF)
    }

    val rowBorderColor = if (isCurrentUser) {
        if (isDark) RegainLimePrimary else Color(0xFF76C400)
    } else {
        if (isDark) Color(0x30FFFFFF) else Color(0xFFE0EAD4)
    }

    val rankTextColor = if (isCurrentUser) {
        if (isDark) RegainLimePrimary else Color(0xFF2E6300)
    } else {
        if (isDark) Color(0xFFA0A89E) else Color(0xFF556050)
    }

    val nameTextColor = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val subjectTextColor = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val timeTextColor = if (isCurrentUser) {
        if (isDark) RegainLimePrimary else Color(0xFF2E6300)
    } else {
        if (isDark) Color(0xFFF0F4ED) else NearBlack
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("leaderboard_row_$rank"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = rowBg),
        border = BorderStroke(
            width = if (isCurrentUser) 1.5.dp else 1.dp,
            color = rowBorderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
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
                        color = rankTextColor,
                        fontSize = 15.sp
                    ),
                    modifier = Modifier.width(26.dp)
                )

                // Avatar Circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCurrentUser) {
                                RegainLimePrimary
                            } else {
                                if (isDark) Color(0xFF2B3628) else Color(0xFFE8EFE2)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    LeaderboardAvatar(
                        avatarUrl = user.avatarUrl,
                        displayName = user.displayName,
                        isCurrentUser = isCurrentUser,
                        textSizeSp = 14
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isCurrentUser) "${user.displayName} (You)" else user.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = nameTextColor,
                                fontSize = 14.sp
                            )
                        )
                    }
                    Text(
                        text = user.subjectTag,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = subjectTextColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Formatted Study Time
            Text(
                text = formatHoursMins(displaySeconds),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = timeTextColor,
                    fontSize = 14.sp
                )
            )
        }
    }
}

@Composable
private fun HallOfFameSection(
    hallOfFame: List<HallOfFameItem>,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Banner card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF22291E) else Color(0xFFF4F9EE)
                ),
                border = BorderStroke(1.5.dp, if (isDark) Color(0x608CE000) else Color(0xFFD3E7BF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFFD54F), Color(0xFFFFA000))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hall of Fame",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = "Past weekly champions permanently recorded in glory every Monday.",
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

        if (hallOfFame.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MilitaryTech,
                            contentDescription = null,
                            tint = if (isDark) Color(0x80FFA000) else Color(0xFFE0A800),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "No Past Champions Yet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = "The current week's champion will be enshrined here automatically when the week resets on Monday. Stay focused to claim the first title!",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "PAST WEEKLY CHAMPIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        fontSize = 11.sp
                    )
                )
            }

            itemsIndexed(hallOfFame) { _, item ->
                HallOfFameCard(
                    item = item,
                    isDark = isDark,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
        }
    }
}

@Composable
private fun HallOfFameCard(
    item: HallOfFameItem,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color
) {
    val cardBg = if (isDark) Color(0xFF1B221B) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0x40FFD54F) else Color(0xFFFFE082)
    val weekLabel = remember(item.weekStart, item.weekEnd) {
        LeaderboardDateUtils.formatWeekRange(item.weekStart, item.weekEnd)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hall_of_fame_item_${item.weekStart}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.2.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Gold trophy badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFD54F), Color(0xFFFFA000))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    // Week range
                    Text(
                        text = weekLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = if (isDark) Color(0xFFFFD54F) else Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Winner Display Name
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            // Winning Time Badge
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0x308CE000) else Color(0xFFE8F5E9),
                    border = BorderStroke(1.dp, if (isDark) Color(0x508CE000) else Color(0xFFC8E6C9))
                ) {
                    Text(
                        text = formatHoursMins(item.winningStudySeconds),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) RegainLimePrimary else Color(0xFF2E6300),
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Weekly Champion",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = textSecondary,
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}

private fun formatHoursMins(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val totalMins = seconds / 60
    val hours = totalMins / 60
    val mins = totalMins % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}

