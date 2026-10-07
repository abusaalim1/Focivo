package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusSessionEntity
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.MascotPose
import com.example.ui.components.RegainMascotView
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun FocusSessionLogScreen(
    sessions: List<FocusSessionEntity>,
    onBack: () -> Unit,
    onStartNewSession: () -> Unit = {},
    onDeleteSession: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) Color(0xFFF2F7EE) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA5B2A2) else Color(0xFF6B7280)
    val limeAccent = RegainLimePrimary

    var selectedFilter by remember { mutableStateOf("All") }
    var sessionToDelete by remember { mutableStateOf<FocusSessionEntity?>(null) }

    // Summary calculations
    val totalSeconds = remember(sessions) { sessions.sumOf { it.durationSeconds } }
    val totalHoursFormatted = remember(totalSeconds) {
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
    }
    val avgMinutes = remember(sessions) {
        if (sessions.isNotEmpty()) {
            (totalSeconds / sessions.size) / 60
        } else 0
    }
    val totalPoints = remember(sessions) { sessions.sumOf { it.focusPointsEarned } }

    // Filter sessions
    val filteredSessions = remember(sessions, selectedFilter) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val weekStart = calendar.timeInMillis

        when (selectedFilter) {
            "Today" -> sessions.filter { it.completedAt >= todayStart }
            "This Week" -> sessions.filter { it.completedAt >= weekStart }
            "Deep Work" -> sessions.filter { it.mode.contains("Deep", ignoreCase = true) }
            "Classic" -> sessions.filter { it.mode.contains("Classic", ignoreCase = true) || it.mode.contains("Pomodoro", ignoreCase = true) }
            else -> sessions
        }.sortedByDescending { it.completedAt }
    }

    // Group sessions by date
    val groupedSessions = remember(filteredSessions) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        filteredSessions.groupBy { session ->
            val sessionDate = Date(session.completedAt)
            val todayStr = sdf.format(Date())
            val sessionStr = sdf.format(sessionDate)

            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = sdf.format(cal.time)

            when (sessionStr) {
                todayStr -> "Today"
                yesterdayStr -> "Yesterday"
                else -> {
                    val displaySdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                    displaySdf.format(sessionDate)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x20FFFFFF) else Color(0x10000000))
                            .testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Past Focus Sessions",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 19.sp
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = limeAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Firestore Cloud Storage Log",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = limeAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
                    .testTag("focus_session_log_list"),
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Statistics Card
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SESSION SUMMARY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = limeAccent,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        fontSize = 10.5.sp
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(RegainLimeContainer)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${sessions.size} LOGGED",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = RegainLimeDeepText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SummaryStatItem(
                                    label = "Total Focus",
                                    value = totalHoursFormatted,
                                    icon = Icons.Default.Schedule,
                                    iconTint = limeAccent
                                )
                                SummaryStatItem(
                                    label = "Avg Session",
                                    value = "${avgMinutes}m",
                                    icon = Icons.Default.HourglassBottom,
                                    iconTint = Color(0xFF60A5FA)
                                )
                                SummaryStatItem(
                                    label = "Focus Points",
                                    value = "+$totalPoints",
                                    icon = Icons.Default.Star,
                                    iconTint = Color(0xFFFBBF24)
                                )
                            }
                        }
                    }
                }

                // Filter Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Today", "This Week", "Deep Work", "Classic").forEach { filter ->
                            val isSelected = selectedFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = filter },
                                label = {
                                    Text(
                                        text = filter,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = limeAccent,
                                    selectedLabelColor = NearBlack,
                                    containerColor = if (isDark) Color(0x18FFFFFF) else Color(0x0A000000),
                                    labelColor = textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) limeAccent else (if (isDark) Color(0x20FFFFFF) else Color(0x15000000)),
                                    selectedBorderColor = limeAccent,
                                    enabled = true,
                                    selected = isSelected
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // Empty state or grouped list
                if (filteredSessions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                RegainMascotView(
                                    pose = MascotPose.IDLE,
                                    size = 110.dp,
                                    animate = false
                                )
                                Text(
                                    text = if (selectedFilter == "All") "No Focus Sessions Recorded Yet" else "No Sessions for '$selectedFilter'",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 16.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Start a study session to track your start time, end time, and duration in Firestore cloud.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Button(
                                    onClick = onStartNewSession,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = limeAccent,
                                        contentColor = NearBlack
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Start Focus Session",
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = PoppinsFontFamily
                                    )
                                }
                            }
                        }
                    }
                } else {
                    groupedSessions.forEach { (dateHeader, sessionGroup) ->
                        item {
                            Text(
                                text = dateHeader.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                            )
                        }

                        items(sessionGroup, key = { it.id }) { session ->
                            FocusSessionLogCard(
                                session = session,
                                isDark = isDark,
                                limeAccent = limeAccent,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onDelete = { sessionToDelete = session }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(
                    text = "Delete Session Log?",
                    fontWeight = FontWeight.Bold,
                    fontFamily = PoppinsFontFamily
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove this focus session log? It will be deleted from your Firestore history.",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        sessionToDelete?.id?.let { onDeleteSession(it) }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PoppinsFontFamily,
                color = Color(0xFF8899A6),
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun FocusSessionLogCard(
    session: FocusSessionEntity,
    isDark: Boolean,
    limeAccent: Color,
    textPrimary: Color,
    textSecondary: Color,
    onDelete: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF141D16) else Color.White
    val cardBorder = if (isDark) Color(0x25FFFFFF) else Color(0x18000000)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_log_item_${session.id}"),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Task Title & Mode Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(limeAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = limeAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = session.taskTitle.ifBlank { "Deep Focus Session" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 14.5.sp
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = session.mode,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = limeAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Total Duration Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(RegainLimeContainer)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = session.getFormattedDuration(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = RegainLimeDeepText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Middle Box: Start Time and End Time Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF0F1711) else Color(0xFFF3F6F1))
                    .border(1.dp, if (isDark) Color(0x18FFFFFF) else Color(0x10000000), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Column {
                            Text(
                                text = "STARTED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 8.5.sp,
                                    color = textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = session.getFormattedStartTime(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    // Divider Arrow
                    Text(
                        text = "→",
                        color = textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    // End Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = limeAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Column {
                            Text(
                                text = "COMPLETED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 8.5.sp,
                                    color = textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = session.getFormattedEndTime(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // Bottom Row: Stats and Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Points badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "+${session.focusPointsEarned} FP",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // Distraction Count
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (session.distractionsCount == 0) limeAccent else Color(0xFFFF5252),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (session.distractionsCount == 0) "0 Distractions" else "${session.distractionsCount} Blocked",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                // Delete log action
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Session",
                        tint = textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
