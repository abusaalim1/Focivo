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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.LeaderboardDateUtils
import com.example.data.LeaderboardUser
import com.example.data.model.FocusSessionEntity
import com.example.R
import com.example.ui.components.FeatureIcon
import com.example.ui.components.MascotPose
import com.example.ui.components.RegainMascotView
import com.example.ui.theme.BottomNavClearance
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainGoldStar
import com.example.ui.theme.LocalAppAccent
import com.example.ui.theme.themedCardBackground
import com.example.ui.theme.backdropContentColor
import com.example.ui.theme.backdropTitleShadow
import com.example.util.TimerThemeManager
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import java.util.Calendar

// v1.2.3 design-unification — owner's clean-redesign language:
// Poppins body, Instrument Serif italic for the page headline,
// lime accent #B6F13A, #131315 surfaces, #1F1F22 lines, #76767C muted.
private val BoardLime = Color(0xFFB6F13A)
private val BoardSurface = Color(0xFF131315)
private val BoardLine = Color(0xFF1F1F22)
private val BoardMuted = Color(0xFF76767C)
private val BoardText = Color(0xFFF4F4F5)

private val BoardSerifItalic = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

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

    // v1.1 final: initials are ALWAYS drawn as the background layer, with the
    // avatar image stacked on top. If the URL is blank, broken, or fails to
    // load, the initials show through instead of a blank circle — so every
    // user gets a visible avatar identity.
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
                color = if (isCurrentUser) Color(0xFF0D1B05) else Color.White,
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

@Composable
fun LeaderboardScreen(
    users: List<LeaderboardUser>,
    currentUserName: String,
    currentUserPhotoUrl: String? = null,
    currentUserPoints: Int,
    currentUserStreak: Int,
    sessions: List<FocusSessionEntity> = emptyList(),
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val appAccent = LocalAppAccent.current // v1.4.0-round2 theme accent
    val cardBg = themedCardBackground() // round-3: translucent so the theme backdrop breathes through
    val cardBorder = if (isDark) BoardLine else Color(0xFFDCE6D2)
    val textPrimary = if (isDark) BoardText else NearBlack
    val textSecondary = if (isDark) BoardMuted else SecondaryTextLight
    // v1.4.2: header text sits directly on the full-bleed theme backdrop, so it
    // follows the selected theme (not the app dark/light mode).
    val theme by TimerThemeManager.selectedTheme.collectAsState()
    val onBackdrop = theme.onBackdropContent
    val titleShadow = backdropTitleShadow(onBackdrop)

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
        // Round-3: theme backdrop is drawn once at the MainActivity shell root.

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
                            fontFamily = BoardSerifItalic,
                            fontWeight = FontWeight.Bold,
                            // v1.1: title uses fixed light text — theme onBackdropContent
                            // resolves dark against this screen's dark surface (contrast fix).
                            color = textPrimary,
                            shadow = titleShadow,
                            fontSize = 24.sp
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
                                        FeatureIcon(
                        drawableRes = R.drawable.ic3d_trophy,
                        contentDescription = "Leaderboard",
                        size = 52.dp
                    )
                }
            }

            // Time Filter Pills: "Today", "This Week", "All Time" (v1.1 final: minimal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Today", "This Week", "All Time").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val pillBg = if (isSelected) {
                        appAccent
                    } else {
                        if (isDark) BoardSurface else Color(0xFFEFF5EA)
                    }
                    val pillBorder = if (isSelected) {
                        appAccent
                    } else {
                        if (isDark) BoardLine else Color(0xFFD6E2CB)
                    }
                    val pillTextColor = if (isSelected) {
                        Color(0xFF0D1B05)
                    } else {
                        if (isDark) BoardMuted else Color(0xFF4A5546)
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

            // Main Content Area — v1.1 final: only essentials (podium + ranked list)
            run {
                // Podium + Ranked List for Today, This Week, All Time
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomNavClearance),
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
                                    containerColor = if (isDark) BoardSurface else Color(0xFFF2F8EC)
                                ),
                                border = BorderStroke(1.dp, if (isDark) Color(0x2EB6F13A) else Color(0xFFD4E7C3))
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
                                // v1.1: fixed muted light text (contrast fix, see header title).
                                color = textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.76.sp,
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
    val textPrimary = if (isDark) BoardText else NearBlack
    val textSecondary = if (isDark) BoardMuted else SecondaryTextLight
    // v1.4.2: podium names sit on the theme backdrop — follow the theme.
    val podiumTheme by TimerThemeManager.selectedTheme.collectAsState()
    val podiumOnBackdrop = podiumTheme.onBackdropContent
    val podiumShadow = backdropTitleShadow(podiumOnBackdrop)
    val podiumTimeColor = if (podiumOnBackdrop.luminance() < 0.5f) BoardLime else Color(0xFF2E6300)

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
                .border(2.dp, if (isDark) BoardSurface else Color.White, CircleShape),
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
                // v1.1: fixed light text — theme onBackdropContent resolves dark
                // against this screen's dark surface (contrast fix).
                color = textPrimary,
                shadow = podiumShadow,
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
                color = podiumTimeColor,
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
                        text = "Leader",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = if (isDark) Color(0xFFFFD54F) else Color(0xFFB45309),
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

        Spacer(modifier = Modifier.height(6.dp))

        // Distinct Podium Pedestal with High-Contrast Finish
        val pedestalGradient = when (rank) {
            1 -> if (isDark) {
                Brush.verticalGradient(listOf(Color(0x40FFD54F), BoardSurface))
            } else {
                Brush.verticalGradient(listOf(Color(0xFFFFF7DB), Color(0xFFFFECC0)))
            }
            2 -> if (isDark) {
                Brush.verticalGradient(listOf(Color(0x35CFD8DC), BoardSurface))
            } else {
                Brush.verticalGradient(listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0)))
            }
            else -> if (isDark) {
                Brush.verticalGradient(listOf(Color(0x35FFCC80), BoardSurface))
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
        if (isDark) BoardSurface else Color(0xFFF3FBE8)
    } else {
        if (isDark) BoardSurface else Color(0xFFFFFFFF)
    }

    val rowBorderColor = if (isCurrentUser) {
        if (isDark) BoardLime else Color(0xFF76C400)
    } else {
        if (isDark) BoardLine else Color(0xFFE0EAD4)
    }

    val rankTextColor = if (isCurrentUser) {
        if (isDark) BoardLime else Color(0xFF2E6300)
    } else {
        if (isDark) BoardMuted else Color(0xFF556050)
    }

    val nameTextColor = if (isDark) BoardText else NearBlack
    val subjectTextColor = if (isDark) BoardMuted else SecondaryTextLight
    val timeTextColor = if (isCurrentUser) {
        if (isDark) BoardLime else Color(0xFF2E6300)
    } else {
        if (isDark) BoardText else NearBlack
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
                                BoardLime
                            } else {
                                if (isDark) BoardLine else Color(0xFFE8EFE2)
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

