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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusSessionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.AuroraBackground
import com.example.ui.components.FocusScoreRing
import com.example.ui.components.PriorityItemRow
import com.example.ui.components.SegmentedProgressBar
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    topPriorities: List<TaskEntity>,
    userPreferences: UserPreferencesEntity?,
    sessions: List<FocusSessionEntity> = emptyList(),
    scheduleStatusSummary: String = "No active schedule",
    scheduledBlocksCount: Int = 0,
    onToggleTask: (TaskEntity) -> Unit,
    onStartFocus: (taskTitle: String?, durationMinutes: Int) -> Unit,
    onCustomizeFocus: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenTaskCreate: () -> Unit,
    onOpenAlarmStudio: () -> Unit = {},
    onOpenShieldHub: () -> Unit = {},
    onOpenAutoSchedule: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    // Semantic colors for Light/Dark mode
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

    // Dynamic score calculation based on real local database sessions
    val todayCal = Calendar.getInstance()
    val todayDayOfWeek = if (todayCal.get(Calendar.DAY_OF_WEEK) == 1) 7 else todayCal.get(Calendar.DAY_OF_WEEK) - 1
    val todaySessions = sessions.filter { it.dayOfWeek == todayDayOfWeek }
    val todayMinutes = todaySessions.sumOf { it.durationSeconds } / 60
    val dailyGoalMinutes = (userPreferences?.dailyGoalMinutes ?: 240).coerceAtLeast(60)

    val score = if (sessions.isEmpty()) {
        0
    } else {
        ((todayMinutes.toFloat() / dailyGoalMinutes) * 100).toInt().coerceIn(10, 100)
    }

    val scoreSubtitle = if (sessions.isEmpty()) {
        "No focus sessions yet · Start your first timer below!"
    } else if (todayMinutes > 0) {
        "$todayMinutes min logged today of $dailyGoalMinutes min daily goal"
    } else {
        "Ready to begin today's first deep work session"
    }

    // Dynamic greeting based on current time
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val currentDateStr = remember {
        val sdf = SimpleDateFormat("EEEE · MMMM d", Locale.getDefault())
        sdf.format(Date())
    }

    val currentStreak = remember(sessions, userPreferences?.currentStreak) {
        calculateConsecutiveStudyStreak(sessions, userPreferences?.currentStreak ?: 0)
    }
    val bestStreak = remember(sessions, userPreferences?.bestStreak, currentStreak) {
        maxOf(userPreferences?.bestStreak ?: 0, currentStreak)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize())

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .testTag("home_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentDateStr.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                letterSpacing = 1.6.sp,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val userName = userPreferences?.currentUserName?.takeIf { it.isNotBlank() && it != "Deep Worker" }
                        val displayGreeting = if (userName != null) "$greeting, $userName." else "$greeting."
                        Text(
                            text = displayGreeting,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 24.sp
                            )
                        )
                        Text(
                            text = "Ready to focus?",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RegainLimeDeepText,
                                fontSize = 18.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Prominent Auto Study Schedule Pill Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(RegainLimeContainer)
                                .border(1.5.dp, RegainLimePrimary, RoundedCornerShape(18.dp))
                                .clickable { onOpenAutoSchedule() }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("home_auto_schedule_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Auto Schedule",
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Schedule",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = RegainLimeDeepText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        // Prominent Alarm Studio Pill Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isDark) Color(0xFF232136) else Color(0xFFF1F5F9))
                                .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                                .clickable { onOpenAlarmStudio() }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("home_alarm_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = "Alarm Studio",
                                    tint = textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Alarms",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Focus Score Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
                        .padding(vertical = 24.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FocusScoreRing(
                            score = score,
                            size = 180.dp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = scoreSubtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            // 2b. Focus Streak Widget (Activity Logs / Supabase consecutive study days)
            item {
                FocusStreakWidget(
                    currentStreak = currentStreak,
                    bestStreak = bestStreak,
                    sessions = sessions,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    isDark = isDark
                )
            }

            // 3. Auto Study Schedule Card (Standalone AI Planner Section)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                        .padding(20.dp)
                        .testTag("home_auto_study_schedule_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(RegainLimeContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Auto Study Schedule",
                                        tint = RegainLimeDeepText,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto Study Schedule",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 15.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = scheduleStatusSummary,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = textSecondary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(RegainLimeContainer)
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "AUTOMATED",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = RegainLimeDeepText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(RegainLimePrimary)
                                        .clickable { onOpenAutoSchedule() }
                                        .padding(horizontal = 13.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Manage",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = NearBlack,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        if (scheduledBlocksCount > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$scheduledBlocksCount active study routines · Automated app blocking during study & break windows",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 4. Focus Shield Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                        .padding(20.dp)
                        .testTag("focus_shield_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(RegainLimeContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Focus Shield",
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Focus Shield",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (userPreferences?.isAppBlockerEnabled == true) RegainLimeContainer else (if (isDark) Color(0x30FFFFFF) else Color(0xFFF0F4EC)))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (userPreferences?.isAppBlockerEnabled == true) "ARMED" else "OFF",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = if (userPreferences?.isAppBlockerEnabled == true) RegainLimeDeepText else textSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            ),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Blocks Reels, Shorts & distracting apps",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RegainLimeContainer)
                                .clickable { onOpenShieldHub() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Manage",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = RegainLimeDeepText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // 4. Quick Focus Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
                        .padding(22.dp)
                        .testTag("quick_focus_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(RegainLimeContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = RegainLimeDeepText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "QUICK FOCUS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = RegainLimeDeepText,
                                        letterSpacing = 1.4.sp,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Text(
                                text = "25 min",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Ready for a focused session?",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = textPrimary
                            )
                        )
                        Text(
                            text = "Clear distractions and enter your zone with classic 25m cadence.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 12.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .clip(CircleShape)
                                    .background(RegainLimePrimary)
                                    .clickable { onStartFocus("Deep Work Sprint", 25) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = NearBlack,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Start Focus",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = NearBlack,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(CircleShape)
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, CircleShape)
                                    .clickable { onCustomizeFocus() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = textPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Customize",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = textPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Today's Progress Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    val goal = userPreferences?.dailyGoalMinutes ?: 360
                    SegmentedProgressBar(
                        focusedMinutes = todayMinutes,
                        goalMinutes = goal
                    )
                }
            }

            // 6. Today's Priorities
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TODAY'S PRIORITIES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "Essential Focus",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        )
                    }

                    IconButton(
                        onClick = onOpenTaskCreate,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(cardBg)
                            .border(1.dp, cardBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Priority",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (topPriorities.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No priorities set for today.",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Dedicate time to the 3 tasks that will move the needle.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(RegainLimePrimary)
                                    .clickable { onOpenTaskCreate() }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "+ Add Priority",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = NearBlack,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(topPriorities, key = { _, item -> item.id }) { index, task ->
                    PriorityItemRow(
                        index = index,
                        task = task,
                        onToggleComplete = { onToggleTask(task) },
                        onStartFocus = { onStartFocus(task.title, task.durationMinutes) }
                    )
                }
            }
        }
    }
}

@Composable
fun FocusStreakWidget(
    currentStreak: Int,
    bestStreak: Int,
    sessions: List<FocusSessionEntity>,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    cardBorder: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
            .padding(20.dp)
            .testTag("focus_streak_widget")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = if (currentStreak > 0) listOf(
                                        Color(0xFFFF6D00),
                                        Color(0xFFFFAB00)
                                    ) else listOf(
                                        Color(0xFF8E8E93),
                                        Color(0xFFAEAEC0)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Focus Streak Flame",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "FOCUS STREAK",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = RegainLimeDeepText,
                                    letterSpacing = 1.3.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(RegainLimeContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (currentStreak > 0) "ACTIVE 🔥" else "START TODAY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = RegainLimeDeepText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (currentStreak == 1) "1 Day Streak" else "$currentStreak Days Streak",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDark) Color(0x20FFFFFF) else Color(0xFFF1F5F9))
                        .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Best Streak",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Best: ${maxOf(bestStreak, currentStreak)}d",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (currentStreak > 0) {
                    "You've maintained study consistency for $currentStreak consecutive day${if (currentStreak > 1) "s" else ""}! Keep the flame alive."
                } else {
                    "No consecutive study streak yet. Complete a focus session today to light the flame!"
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = PoppinsFontFamily,
                    color = textSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            val past7Days = remember(sessions) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dayFormat = SimpleDateFormat("E", Locale.getDefault())
                val datesWithStudy = sessions.filter { it.completedAt > 0 }.map { sdf.format(Date(it.completedAt)) }.toSet()

                (6 downTo 0).map { daysAgo ->
                    val c = Calendar.getInstance()
                    c.add(Calendar.DAY_OF_YEAR, -daysAgo)
                    val dateStr = sdf.format(c.time)
                    val label = dayFormat.format(c.time).take(1)
                    val isToday = daysAgo == 0
                    val hasStudied = datesWithStudy.contains(dateStr)
                    Triple(label, hasStudied, isToday)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                past7Days.forEach { (dayLabel, hasStudied, isToday) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = if (isToday) RegainLimeDeepText else textSecondary,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        hasStudied -> Color(0xFFFF6D00)
                                        isToday -> RegainLimeContainer
                                        else -> if (isDark) Color(0x1AFFFFFF) else Color(0xFFE2E8F0)
                                    }
                                )
                                .border(
                                    width = if (isToday) 1.5.dp else 0.dp,
                                    color = if (isToday) RegainLimePrimary else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasStudied) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Studied",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else if (isToday) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Today",
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(textSecondary.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun calculateConsecutiveStudyStreak(sessions: List<FocusSessionEntity>, fallbackStreak: Int): Int {
    if (sessions.isEmpty()) {
        return fallbackStreak
    }
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val studyDates = sessions
        .filter { it.completedAt > 0 }
        .map { sdf.format(Date(it.completedAt)) }
        .toSet()

    if (studyDates.isEmpty()) {
        return fallbackStreak
    }

    val cal = Calendar.getInstance()
    val todayStr = sdf.format(cal.time)

    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStr = sdf.format(cal.time)

    val startCal = Calendar.getInstance()
    if (studyDates.contains(todayStr)) {
        startCal.time = Date()
    } else if (studyDates.contains(yesterdayStr)) {
        startCal.add(Calendar.DAY_OF_YEAR, -1)
    } else {
        return maxOf(0, fallbackStreak)
    }

    var streak = 0
    while (true) {
        val dateStr = sdf.format(startCal.time)
        if (studyDates.contains(dateStr)) {
            streak++
            startCal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            break
        }
    }
    return maxOf(streak, fallbackStreak)
}
