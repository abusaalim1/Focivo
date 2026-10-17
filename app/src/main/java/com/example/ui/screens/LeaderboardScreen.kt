package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.LeaderboardDateUtils
import com.example.data.LeaderboardUser
import com.example.data.model.FocusSessionEntity
import com.example.ui.theme.BottomNavClearance
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoTokens
import java.util.Calendar
import java.util.Locale

// Medal colors are categorical (same in both modes).
private val MedalGold = Color(0xFFFFB400)
private val MedalSilver = Color(0xFF9AA4AE)
private val MedalBronze = Color(0xFFF2762E)

@Composable
fun LeaderboardAvatar(
    avatarUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    isCurrentUser: Boolean = false,
    textSizeSp: Int = 15,
    initialsColor: Color? = null
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

    // Initials are always the background layer; the avatar image stacks on top.
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                color = initialsColor ?: if (isCurrentUser) Color(0xFF0D1B05) else Color.White,
                fontSize = textSizeSp.sp
            )
        )
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Leaderboard — owner's Full App Redesign (2026-10-16).
 * Serif headline, segmented Today / This week / All time control, mockup
 * podium (medal blocks), grouped ranking rows. All values real.
 */
@Composable
fun LeaderboardScreen(
    users: List<LeaderboardUser>,
    currentUserName: String,
    currentUserPhotoUrl: String? = null,
    currentUserPoints: Int,
    sessions: List<FocusSessionEntity> = emptyList(),
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()

    var selectedFilter by remember { mutableStateOf("This week") }

    LaunchedEffect(Unit) {
        onRefresh()
    }

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

    val currentMonday = remember { LeaderboardDateUtils.getCurrentWeekMonday() }

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
        currentUserName, currentUserPhotoUrl, currentUserPoints,
        myTodayStudySeconds, myWeeklyStudySeconds, myAllTimeStudySeconds, users
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
            streak = remoteMe?.streak ?: 1,
            subjectTag = remoteMe?.subjectTag ?: "Active Focus",
            avatarUrl = currentUserPhotoUrl ?: remoteMe?.avatarUrl,
            isCurrentUser = true
        )
    }

    val effectiveUsers = remember(users, currentEntry, selectedFilter) {
        val remoteWithoutMe = users
            .filterNot { it.isCurrentUser || it.displayName.equals(currentUserName, ignoreCase = true) }
            // v1.3.0: private profiles are hidden from other users' surfaces.
            // The current user always sees their own entry (built locally above).
            .filter { it.isPublic }
        val allUsers = listOf(currentEntry) + remoteWithoutMe
        when (selectedFilter) {
            "Today" -> allUsers.sortedByDescending { it.todayStudySeconds }
            "This week" -> allUsers.sortedByDescending { it.weeklyStudySeconds }
            "All time" -> allUsers.sortedByDescending { it.studySeconds }
            else -> allUsers.sortedByDescending { it.weeklyStudySeconds }
        }
    }

    val top1 = effectiveUsers.getOrNull(0)
    val top2 = effectiveUsers.getOrNull(1)
    val top3 = effectiveUsers.getOrNull(2)
    val restOfUsers = effectiveUsers.drop(3)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(T.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header: serif headline + refresh.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Leaderboard",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = SerifItalicFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = T.text,
                        fontSize = 30.sp,
                        lineHeight = 35.sp,
                        letterSpacing = (-0.6).sp
                    )
                )
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(T.surface)
                        .border(1.dp, T.line, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh leaderboard",
                        tint = T.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Segmented control: Today / This week / All time.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .clip(CircleShape)
                    .background(T.surface)
                    .border(1.dp, T.line, CircleShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Today", "This week", "All time").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (isSelected) T.accent else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { selectedFilter = filter }
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                color = if (isSelected) T.onAccent else T.muted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = BottomNavClearance),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    PodiumSection(top1 = top1, top2 = top2, top3 = top3, selectedFilter = selectedFilter)
                }

                if (selectedFilter == "This week" && (top1?.weeklyStudySeconds ?: 0L) == 0L) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = T.surface),
                            border = BorderStroke(1.dp, T.line)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.emoji3d_crown),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "A new week has begun! Study this week to claim the #1 spot.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = T.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Rankings".uppercase(Locale.getDefault()),
                        style = focivoEyebrow()
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(T.surface)
                            .border(1.dp, T.line, RoundedCornerShape(24.dp))
                            .padding(horizontal = 18.dp)
                    ) {
                        restOfUsers.forEachIndexed { index, user ->
                            val userSeconds = when (selectedFilter) {
                                "Today" -> user.todayStudySeconds
                                "This week" -> user.weeklyStudySeconds
                                "All time" -> user.studySeconds
                                else -> user.weeklyStudySeconds
                            }
                            LeaderboardUserRow(
                                rank = index + 4,
                                user = user,
                                displaySeconds = userSeconds,
                                isCurrentUser = user.isCurrentUser,
                                showDivider = index < restOfUsers.lastIndex
                            )
                        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        top2?.let { user ->
            PodiumColumn(
                user = user, rank = 2,
                displaySeconds = secondsFor(user, selectedFilter),
                selectedFilter = selectedFilter,
                modifier = Modifier.weight(1f)
            )
        }
        top1?.let { user ->
            PodiumColumn(
                user = user, rank = 1,
                displaySeconds = secondsFor(user, selectedFilter),
                selectedFilter = selectedFilter,
                modifier = Modifier.weight(1f)
            )
        }
        top3?.let { user ->
            PodiumColumn(
                user = user, rank = 3,
                displaySeconds = secondsFor(user, selectedFilter),
                selectedFilter = selectedFilter,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun secondsFor(user: LeaderboardUser, filter: String): Long = when (filter) {
    "Today" -> user.todayStudySeconds
    "This week" -> user.weeklyStudySeconds
    "All time" -> user.studySeconds
    else -> user.weeklyStudySeconds
}

@Composable
private fun PodiumColumn(
    user: LeaderboardUser,
    rank: Int,
    displaySeconds: Long,
    selectedFilter: String,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    val medal = when (rank) {
        1 -> MedalGold
        2 -> MedalSilver
        else -> MedalBronze
    }
    val blockHeight = when (rank) {
        1 -> 128.dp
        2 -> 84.dp
        else -> 64.dp
    }
    val isLeadingThisWeek = selectedFilter == "This week" && rank == 1 && displaySeconds > 0

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(if (rank == 1) 62.dp else 54.dp)
                .clip(CircleShape)
                .background(if (user.isCurrentUser) T.accent else medal),
            contentAlignment = Alignment.Center
        ) {
            LeaderboardAvatar(
                avatarUrl = user.avatarUrl,
                displayName = user.displayName,
                isCurrentUser = user.isCurrentUser,
                textSizeSp = 20,
                initialsColor = T.onAccent
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (user.isCurrentUser) "You" else user.displayName,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = T.text,
                fontSize = 13.sp
            ),
            maxLines = 1
        )
        Text(
            text = user.subjectTag,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PoppinsFontFamily,
                color = T.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 13.sp
            ),
            maxLines = 2,
            modifier = Modifier.heightIn(min = 26.dp)
        )
        Text(
            text = formatHoursMins(displaySeconds),
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = PoppinsFontFamily,
                color = T.accentText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(vertical = 2.dp)
        )
        if (isLeadingThisWeek) {
            Surface(
                shape = CircleShape,
                color = T.softAccent,
                border = BorderStroke(1.dp, medal.copy(alpha = 0.5f)),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Leader",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = T.accentText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Image(
                        painter = painterResource(id = R.drawable.emoji3d_crown),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                .background(T.surface)
                .border(
                    width = 1.dp,
                    color = T.line,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(medal)
            )
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = medal,
                    fontSize = 22.sp
                )
            )
        }
    }
}

@Composable
private fun LeaderboardUserRow(
    rank: Int,
    user: LeaderboardUser,
    displaySeconds: Long,
    isCurrentUser: Boolean,
    showDivider: Boolean
) {
    val T = focivoTokens()
    Column(modifier = Modifier.testTag("leaderboard_row_$rank")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = T.muted,
                    fontSize = 14.sp
                ),
                modifier = Modifier.width(18.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isCurrentUser) T.accent else T.surface2)
                    .then(
                        if (isCurrentUser) Modifier.border(1.dp, T.accent, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                LeaderboardAvatar(
                    avatarUrl = user.avatarUrl,
                    displayName = user.displayName,
                    isCurrentUser = isCurrentUser,
                    textSizeSp = 15,
                    initialsColor = if (isCurrentUser) T.onAccent else T.text
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCurrentUser) "${user.displayName} (You)" else user.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = T.text,
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = user.subjectTag,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = T.muted,
                        fontSize = 11.sp
                    )
                )
            }
            Text(
                text = formatHoursMins(displaySeconds),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrentUser) T.accentText else T.text,
                    fontSize = 14.sp
                )
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(T.line)
            )
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
