package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.FocusSessionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.AuroraBackground
import com.example.ui.components.LinearButton
import com.example.ui.components.LinearButtonVariant
import com.example.ui.components.LinearGlowCard
import com.example.ui.components.PriorityItemRow
import com.example.ui.components.SegmentedProgressBar
import com.example.ui.components.pressFeedback
import com.example.ui.theme.AppleLinearFontFamily
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
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
    val isDark = isAppInDarkTheme()

    // Semantic colors for Light/Dark mode
    val cardBg = if (isDark) Color(0xD91B221C) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0x358CE000) else Color(0xFFE2EBD6)
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else Color(0xFF121814)
    val textSecondary = if (isDark) Color(0xFFA0A89E) else Color(0xFF4A564C)
    val headerDateColor = Color.White.copy(alpha = 0.88f)
    val headerTitleColor = Color.White
    val headerButtonBg = if (isDark) cardBg else Color(0xEEFFFFFF)
    val headerButtonBorder = if (isDark) cardBorder else Color(0xFFD0DCC4)

    val totalFocusSeconds = remember(sessions) { sessions.sumOf { it.durationSeconds } }
    val totalFocusHours = totalFocusSeconds / 3600.0
    val totalFocusHoursStr = remember(totalFocusSeconds) {
        if (totalFocusHours >= 10.0) {
            "${totalFocusHours.toInt()}h"
        } else if (totalFocusHours >= 1.0) {
            String.format(Locale.US, "%.1fh", totalFocusHours)
        } else if (totalFocusSeconds > 0) {
            "${totalFocusSeconds / 60}m"
        } else {
            "0h"
        }
    }
    val totalSessionsCount = sessions.size

    // Weekly Study Time Calculations
    val currentWeekStart = remember {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY)
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val lastWeekStart = remember(currentWeekStart) {
        currentWeekStart - (7 * 24 * 3600 * 1000L)
    }
    val thisWeekSessions = remember(sessions, currentWeekStart) {
        sessions.filter { it.completedAt >= currentWeekStart }
    }
    val lastWeekSessions = remember(sessions, currentWeekStart, lastWeekStart) {
        sessions.filter { it.completedAt >= lastWeekStart && it.completedAt < currentWeekStart }
    }
    val thisWeekSeconds = remember(thisWeekSessions) { thisWeekSessions.sumOf { it.durationSeconds } }
    val lastWeekSeconds = remember(lastWeekSessions) { lastWeekSessions.sumOf { it.durationSeconds } }

    val thisWeekHours = thisWeekSeconds / 3600.0
    val lastWeekHours = lastWeekSeconds / 3600.0
    val weekDiffHours = thisWeekHours - lastWeekHours

    val weeklyHoursHeadlineStr = remember(thisWeekSeconds, thisWeekHours) {
        if (thisWeekSeconds == 0) {
            "0.0 hrs"
        } else {
            String.format(Locale.US, "%.1f hrs", thisWeekHours)
        }
    }

    val weeklyTrendText = remember(thisWeekSeconds, weekDiffHours, thisWeekSessions) {
        if (thisWeekSeconds == 0) {
            "No sessions yet this week — let's start!"
        } else if (kotlin.math.abs(weekDiffHours) < 0.1) {
            "${thisWeekSessions.size} session${if (thisWeekSessions.size != 1) "s" else ""} completed this week"
        } else if (weekDiffHours > 0) {
            String.format(Locale.US, "+%.1f hrs vs last week", weekDiffHours)
        } else {
            String.format(Locale.US, "%.1f hrs vs last week", weekDiffHours)
        }
    }

    // Weekly Study Goal & Daily Active Distribution calculations
    val weeklyGoalMinutes = remember(userPreferences?.dailyGoalMinutes) {
        val daily = (userPreferences?.dailyGoalMinutes ?: 240).coerceAtLeast(60)
        daily * 7
    }
    val weeklyGoalHours = remember(weeklyGoalMinutes) {
        weeklyGoalMinutes / 60.0
    }
    val thisWeekMinutes = remember(thisWeekSeconds) {
        thisWeekSeconds / 60
    }
    val weeklyGoalProgress = remember(thisWeekMinutes, weeklyGoalMinutes) {
        if (weeklyGoalMinutes > 0) {
            (thisWeekMinutes.toFloat() / weeklyGoalMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }
    val weeklyGoalProgressPercent = remember(weeklyGoalProgress) {
        (weeklyGoalProgress * 100).toInt()
    }
    val remainingWeeklyMinutes = remember(thisWeekMinutes, weeklyGoalMinutes) {
        (weeklyGoalMinutes - thisWeekMinutes).coerceAtLeast(0)
    }
    val remainingWeeklyHoursStr = remember(remainingWeeklyMinutes) {
        val hrs = remainingWeeklyMinutes / 60.0
        if (hrs >= 1.0) String.format(Locale.US, "%.1fh", hrs) else "${remainingWeeklyMinutes}m"
    }

    val weekDaysStatus = remember(thisWeekSessions, currentWeekStart) {
        val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
        val cal = Calendar.getInstance()
        val todayDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val todayIdx = if (todayDayOfWeek == Calendar.SUNDAY) 6 else (todayDayOfWeek - Calendar.MONDAY)

        (0..6).map { dayOffset ->
            val dayStart = currentWeekStart + (dayOffset * 24 * 3600 * 1000L)
            val dayEnd = dayStart + (24 * 3600 * 1000L)
            val daySecs = thisWeekSessions
                .filter { it.completedAt >= dayStart && it.completedAt < dayEnd }
                .sumOf { it.durationSeconds }
            val isToday = dayOffset == todayIdx
            val isCompleted = daySecs > 0
            Triple(dayNames[dayOffset], isCompleted, isToday)
        }
    }

    // Dynamic study metrics based on real local database sessions
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todaySessions = remember(sessions, todayStart) {
        sessions.filter { it.completedAt >= todayStart && it.durationSeconds > 0 }
    }
    val todayMinutes = remember(todaySessions) {
        todaySessions.sumOf { it.durationSeconds } / 60
    }
    val totalCompletedSessions = remember(sessions) {
        sessions.count { it.durationSeconds > 0 }
    }
    val dailyGoalMinutes = (userPreferences?.dailyGoalMinutes ?: 240).coerceAtLeast(60)

    val dailyProgress = if (dailyGoalMinutes > 0 && todayMinutes > 0) {
        (todayMinutes.toFloat() / dailyGoalMinutes).coerceIn(0f, 1f)
    } else {
        0f
    }

    val scoreSubtitle = if (totalCompletedSessions == 0) {
        "No focus sessions yet · Start your first timer below!"
    } else if (todayMinutes > 0) {
        "${todaySessions.size} session${if (todaySessions.size > 1) "s" else ""} today · $todayMinutes of $dailyGoalMinutes min daily goal"
    } else {
        "$totalCompletedSessions total completed sessions · Ready for your next session"
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

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize())

        // Top Atmospheric Header Backdrop Image Layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.TopCenter)
        ) {
            Image(
                painter = painterResource(id = R.drawable.home_header_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // High-contrast scrim overlay to ensure white greeting text pops crisply in both dark & light modes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x99000000), // ~60% dark overlay at top for crisp white text legibility
                                Color(0x50000000),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Smooth vertical gradient fade into background surface before cards
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7)
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .testTag("home_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 220.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header with clean greeting and quiet action icons
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentDateStr.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = headerDateColor,
                                    letterSpacing = 1.6.sp,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val userName = userPreferences?.currentUserName?.takeIf { it.isNotBlank() && it != "Deep Worker" }
                            val displayGreeting = if (userName != null) "$greeting, $userName" else greeting
                            Text(
                                text = displayGreeting,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = headerTitleColor,
                                    fontSize = 24.sp
                                )
                            )
                        }

                        // Compact quiet action icons for Schedule and Alarms
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onOpenAutoSchedule,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(headerButtonBg)
                                    .border(1.dp, headerButtonBorder, CircleShape)
                                    .testTag("home_auto_schedule_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Auto Schedule",
                                    tint = if (isDark) RegainLimeDeepText else Color(0xFF2E6800),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onOpenAlarmStudio,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(headerButtonBg)
                                    .border(1.dp, headerButtonBorder, CircleShape)
                                    .testTag("home_alarm_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = "Alarms",
                                    tint = if (isDark) textPrimary else Color(0xFF151916),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Consolidated Primary Hero: Weekly Study Time Card with Integrated Focus Score Badge
            item {
                LinearGlowCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_weekly_study_hero_card"),
                    shape = RoundedCornerShape(24.dp),
                    glowColor = RegainLimePrimary,
                    glowAlpha = 0.2f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Top Row: Weekly Study Time Label + Integrated Focus Score Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(RegainLimeContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = RegainLimeDeepText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "WEEKLY STUDY TIME",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = AppleLinearFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = textSecondary,
                                        letterSpacing = 1.4.sp,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }

                            // Compact integrated Sessions badge with mini mascot
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0x30FFFFFF) else Color(0xFFF0F4EC))
                                    .border(1.dp, cardBorder, CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("home_integrated_sessions_badge")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.mascot_focus_score),
                                        contentDescription = "Mascot",
                                        modifier = Modifier.size(20.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Text(
                                        text = "$totalCompletedSessions sessions",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = AppleLinearFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Large Headline Number
                        Text(
                            text = "$weeklyHoursHeadlineStr this week",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = AppleLinearFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 30.sp,
                                letterSpacing = (-0.6).sp
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Subtitle / Trend Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (thisWeekSeconds > 0 && weekDiffHours > 0) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = weeklyTrendText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = AppleLinearFontFamily,
                                    color = if (thisWeekSeconds == 0) textSecondary else if (weekDiffHours >= 0) RegainLimeDeepText else textSecondary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Visual Weekly Goal Progress Bar Header
                        val animatedWeeklyProgress by animateFloatAsState(
                            targetValue = weeklyGoalProgress,
                            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                            label = "weekly_goal_progress_anim"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "WEEKLY GOAL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = AppleLinearFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = textSecondary,
                                        fontSize = 10.5.sp,
                                        letterSpacing = 1.1.sp
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(RegainLimePrimary.copy(alpha = 0.16f))
                                        .border(1.dp, RegainLimePrimary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$weeklyGoalProgressPercent%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = AppleLinearFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = RegainLimeDeepText,
                                            fontSize = 10.5.sp
                                        )
                                    )
                                }
                            }

                            Text(
                                text = String.format(Locale.US, "%.1fh / %.0fh", thisWeekHours, weeklyGoalHours),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = AppleLinearFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // High-Precision Visual Progress Bar Track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isDark) Color(0x24FFFFFF) else Color(0xFFE5EED9))
                                .border(1.dp, if (isDark) Color(0x18FFFFFF) else Color(0x12000000), RoundedCornerShape(5.dp))
                                .testTag("home_weekly_goal_progress_bar")
                        ) {
                            if (animatedWeeklyProgress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedWeeklyProgress)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    RegainLimePrimary,
                                                    Color(0xFFAEF72A),
                                                    RegainLimeLight
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Weekly Active Days Continuity Tracker (M T W T F S S)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            weekDaysStatus.forEach { (dayLabel, isCompleted, isToday) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isCompleted -> RegainLimePrimary
                                                    isToday -> if (isDark) Color(0x35FFFFFF) else Color(0xFFE2EBD6)
                                                    else -> if (isDark) Color(0x14FFFFFF) else Color(0xFFF1F5EB)
                                                }
                                            )
                                            .border(
                                                width = if (isToday) 1.5.dp else 1.dp,
                                                color = when {
                                                    isCompleted -> RegainLimePrimary
                                                    isToday -> RegainLimePrimary.copy(alpha = 0.8f)
                                                    else -> if (isDark) Color(0x18FFFFFF) else Color(0xFFE2EBD6)
                                                },
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCompleted) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Day active",
                                                tint = NearBlack,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else if (isToday) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(RegainLimePrimary)
                                            )
                                        }
                                    }

                                    Text(
                                        text = dayLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = AppleLinearFontFamily,
                                            fontWeight = if (isToday || isCompleted) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isToday) textPrimary else textSecondary,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Combined Compact Status Row: Auto Study Routine + Focus Shield
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("home_combined_status_row")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Item: Auto Study Routine
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenAutoSchedule() }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RegainLimeContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Auto Schedule",
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto Study",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = scheduleStatusSummary,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .width(1.dp)
                                .height(32.dp)
                                .background(cardBorder)
                        )

                        // Right Item: Focus Shield
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenShieldHub() }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (userPreferences?.isAppBlockerEnabled == true) RegainLimeContainer else (if (isDark) Color(0x30FFFFFF) else Color(0xFFF0F4EC))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Focus Shield",
                                    tint = if (userPreferences?.isAppBlockerEnabled == true) RegainLimeDeepText else textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Focus Shield",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (userPreferences?.isAppBlockerEnabled == true) "ON" else "OFF",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = if (userPreferences?.isAppBlockerEnabled == true) RegainLimeDeepText else textSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                                Text(
                                    text = if (userPreferences?.isAppBlockerEnabled == true) "12 apps blocked" else "Tap to enable",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 5. Streamlined Quick Focus Card
            item {
                LinearGlowCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_focus_card"),
                    shape = RoundedCornerShape(24.dp),
                    glowColor = RegainLimePrimary,
                    glowAlpha = 0.15f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(RegainLimeContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = RegainLimeDeepText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "QUICK FOCUS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = AppleLinearFontFamily,
                                        color = RegainLimeDeepText,
                                        letterSpacing = 1.4.sp,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Text(
                                text = "25 min",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = AppleLinearFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Start a 25m Focus Sprint",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = AppleLinearFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = textPrimary
                            )
                        )
                        Text(
                            text = "Clear distractions and enter flow state with classic cadence.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppleLinearFontFamily,
                                color = textSecondary,
                                fontSize = 12.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LinearButton(
                                text = "Start Focus",
                                icon = Icons.Default.PlayArrow,
                                onClick = { onStartFocus("Deep Work Sprint", 25) },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp),
                                variant = LinearButtonVariant.PRIMARY
                            )

                            LinearButton(
                                text = "Customize",
                                icon = Icons.Default.Tune,
                                onClick = onCustomizeFocus,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                variant = LinearButtonVariant.SECONDARY
                            )
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
