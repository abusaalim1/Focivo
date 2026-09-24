package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusSessionEntity
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

/**
 * Data representation for a single day in the weekly productivity visualization.
 */
data class DayProductivityModel(
    val dayOfWeek: Int, // 1 (Mon) to 7 (Sun)
    val dayShortName: String, // "Mon", "Tue", etc.
    val dayLabel: String, // "M", "T", "W", "T", "F", "S", "S"
    val dateFormatted: String, // "Sep 15"
    val totalSeconds: Int,
    val totalHours: Float,
    val sessionCount: Int,
    val topSubject: String,
    val isToday: Boolean,
    val isGoalMet: Boolean
)

/**
 * Summary stats for the whole week's progress over time.
 */
data class WeekProductivitySummary(
    val totalHours: Float,
    val totalSeconds: Int,
    val dailyAverageHours: Float,
    val peakDayName: String,
    val peakDayHours: Float,
    val goalsMetDaysCount: Int,
    val percentageVsLastWeek: Float, // e.g. +14.5%
    val totalSessionsCount: Int,
    val dailyList: List<DayProductivityModel>
)

/**
 * Helper to calculate weekly productivity metrics from sessions list and target daily goal.
 */
fun calculateWeeklyProductivity(
    sessions: List<FocusSessionEntity>,
    targetDailyGoalHours: Float = 2.0f
): WeekProductivitySummary {
    val cal = Calendar.getInstance()
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    val todayDayOfWeek = if (dow == Calendar.SUNDAY) 7 else (dow - Calendar.MONDAY + 1)

    // Days mapping: 1=Mon .. 7=Sun
    val dayInfo = listOf(
        Triple(1, "Mon", "M"),
        Triple(2, "Tue", "T"),
        Triple(3, "Wed", "W"),
        Triple(4, "Thu", "T"),
        Triple(5, "Fri", "F"),
        Triple(6, "Sat", "S"),
        Triple(7, "Sun", "S")
    )

    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    // Determine week start timestamp (Monday 00:00:00)
    val daysFromMonday = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY)
    cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val currentWeekStartMs = cal.timeInMillis

    // Previous week window
    val prevWeekStartMs = currentWeekStartMs - (7L * 24 * 3600 * 1000)
    val prevWeekEndMs = currentWeekStartMs

    val currentWeekSessions = sessions.filter { it.completedAt in currentWeekStartMs until (currentWeekStartMs + 7L * 24 * 3600 * 1000) && it.durationSeconds > 0 }
    val prevWeekSessions = sessions.filter { it.completedAt in prevWeekStartMs until prevWeekEndMs && it.durationSeconds > 0 }

    // Aggregate daily items precisely for each day of the current week
    val dailyModels = dayInfo.map { (dayNum, name, label) ->
        val dayStartMs = currentWeekStartMs + ((dayNum - 1) * 24L * 3600 * 1000)
        val dayEndMs = dayStartMs + (24L * 3600 * 1000)
        val dayCalendar = Calendar.getInstance().apply {
            timeInMillis = dayStartMs
        }
        val dateFormatted = dateFormat.format(dayCalendar.time)

        // Find sessions that actually finished within this calendar day
        val daySessions = sessions.filter { it.completedAt in dayStartMs until dayEndMs && it.durationSeconds > 0 }

        val totalSecs = daySessions.sumOf { it.durationSeconds }
        val hours = totalSecs / 3600.0f
        val isGoalMet = hours >= targetDailyGoalHours && hours > 0.05f

        val topSubject = if (daySessions.isNotEmpty()) {
            daySessions.groupBy { it.taskTitle.ifBlank { "General Focus" } }
                .maxByOrNull { entry -> entry.value.sumOf { it.durationSeconds } }
                ?.key ?: "General Focus"
        } else {
            "No sessions"
        }

        DayProductivityModel(
            dayOfWeek = dayNum,
            dayShortName = name,
            dayLabel = label,
            dateFormatted = dateFormatted,
            totalSeconds = totalSecs,
            totalHours = hours,
            sessionCount = daySessions.size,
            topSubject = topSubject,
            isToday = dayNum == todayDayOfWeek,
            isGoalMet = isGoalMet
        )
    }

    val totalWeekSecs = dailyModels.sumOf { it.totalSeconds }
    val totalWeekHours = totalWeekSecs / 3600.0f
    val dailyAvgHours = if (dailyModels.isNotEmpty()) totalWeekHours / 7f else 0f

    val peakDay = dailyModels.maxByOrNull { it.totalHours }
    val peakDayName = peakDay?.dayShortName ?: "None"
    val peakDayHours = peakDay?.totalHours ?: 0f

    val goalsMetCount = dailyModels.count { it.isGoalMet }

    // Calculate percentage vs last week
    val prevWeekSecs = prevWeekSessions.sumOf { it.durationSeconds }
    val prevWeekHours = prevWeekSecs / 3600.0f

    val percentageVsLastWeek = when {
        prevWeekHours == 0f && totalWeekHours > 0f -> 100f
        prevWeekHours == 0f && totalWeekHours == 0f -> 0f
        else -> ((totalWeekHours - prevWeekHours) / prevWeekHours) * 100f
    }

    val totalSessionsCount = dailyModels.sumOf { it.sessionCount }

    return WeekProductivitySummary(
        totalHours = totalWeekHours,
        totalSeconds = totalWeekSecs,
        dailyAverageHours = dailyAvgHours,
        peakDayName = peakDayName,
        peakDayHours = peakDayHours,
        goalsMetDaysCount = goalsMetCount,
        percentageVsLastWeek = percentageVsLastWeek,
        totalSessionsCount = totalSessionsCount,
        dailyList = dailyModels
    )
}

/**
 * Modern, Recharts-inspired Weekly Productivity Visualization Composable.
 * Features:
 * - Dynamic daily total focus hours bars with smooth animations
 * - Daily goal target reference line
 * - Interactive day tapping to inspect detailed daily breakdown (hours, goal progress, subject, session count)
 * - Week-over-week progress indicator and productivity summary stats
 */
@Composable
fun WeeklyProductivityChart(
    sessions: List<FocusSessionEntity>,
    targetDailyGoalHours: Float = 2.0f,
    modifier: Modifier = Modifier,
    onDaySelected: ((DayProductivityModel) -> Unit)? = null
) {
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val accentLime = if (isDark) RegainLimePrimary else RegainLimeDeepText

    val summary = remember(sessions, targetDailyGoalHours) {
        calculateWeeklyProductivity(sessions, targetDailyGoalHours)
    }

    // Default selection: Today's day of week or peak day
    val initialSelectedDay = remember(summary) {
        val today = summary.dailyList.find { it.isToday }
        today?.dayOfWeek ?: summary.dailyList.maxByOrNull { it.totalHours }?.dayOfWeek ?: 1
    }

    var selectedDayOfWeek by remember { mutableIntStateOf(initialSelectedDay) }
    val selectedDayModel = remember(summary, selectedDayOfWeek) {
        summary.dailyList.find { it.dayOfWeek == selectedDayOfWeek } ?: summary.dailyList.first()
    }

    // Determine max scale for chart Y-Axis (at least targetDailyGoalHours + 1h, or highest day rounded up)
    val maxDayHours = summary.dailyList.maxOfOrNull { it.totalHours } ?: 0f
    val chartMaxHours = remember(maxDayHours, targetDailyGoalHours) {
        val calculated = maxOf(maxDayHours * 1.25f, targetDailyGoalHours * 1.35f, 3.0f)
        // Round up to clean integer
        kotlin.math.ceil(calculated)
    }

    LiquidGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_productivity_chart"),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Title and Target Goal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "WEEKLY PRODUCTIVITY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            letterSpacing = 1.6.sp,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Focus Hours per Day",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 17.sp
                        )
                    )
                }

                // Daily Target Goal Badge
                Surface(
                    color = if (isDark) Color(0x188CE000) else RegainLimeContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x358CE000))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = accentLime,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Goal: ${String.format(Locale.US, "%.1fh", targetDailyGoalHours)}/day",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = accentLime,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Total Hours & Week-over-Week Trend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", summary.totalHours),
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = PoppinsFontFamily,
                            fontSize = 44.sp,
                            lineHeight = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HRS TOTAL",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = accentLime,
                            letterSpacing = 1.sp,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Week Progress Badge
                Surface(
                    color = if (summary.percentageVsLastWeek >= 0) {
                        if (isDark) Color(0x228CE000) else Color(0x152E7D32)
                    } else {
                        if (isDark) Color(0x22FF5252) else Color(0x15D32F2F)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (summary.percentageVsLastWeek >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (summary.percentageVsLastWeek >= 0) accentLime else Color(0xFFFF5252),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val sign = if (summary.percentageVsLastWeek >= 0) "+" else ""
                        Text(
                            text = "$sign${String.format(Locale.US, "%.0f", summary.percentageVsLastWeek)}% vs last wk",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = if (summary.percentageVsLastWeek >= 0) accentLime else Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Main Bar Chart Area with Goal Reference Line
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
            ) {
                val chartHeightPx = constraints.maxHeight.toFloat()
                val goalFraction = (targetDailyGoalHours / chartMaxHours).coerceIn(0f, 1f)

                // Background Reference Grid Lines (0h, mid, max) and Goal Line
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val strokeColor = if (isDark) Color(0x15FFFFFF) else Color(0x12000000)
                            val goalLineColor = if (isDark) Color(0x558CE000) else Color(0x664C9A00)

                            // Top boundary line
                            drawLine(
                                color = strokeColor,
                                start = Offset(0f, 10f),
                                end = Offset(size.width, 10f),
                                strokeWidth = 1f
                            )

                            // Middle grid line
                            val midY = size.height * 0.5f
                            drawLine(
                                color = strokeColor,
                                start = Offset(0f, midY),
                                end = Offset(size.width, midY),
                                strokeWidth = 1f
                            )

                            // Dashed Goal line
                            val goalY = size.height * (1f - goalFraction)
                            if (goalY in 15f..(size.height - 15f)) {
                                drawLine(
                                    color = goalLineColor,
                                    start = Offset(0f, goalY),
                                    end = Offset(size.width, goalY),
                                    strokeWidth = 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            }

                            // Bottom baseline
                            drawLine(
                                color = strokeColor,
                                start = Offset(0f, size.height - 25f),
                                end = Offset(size.width, size.height - 25f),
                                strokeWidth = 1.5f
                            )
                        }
                )

                // 7 Interactive Daily Bars Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    summary.dailyList.forEach { dayModel ->
                        val isSelected = dayModel.dayOfWeek == selectedDayOfWeek
                        val rawFraction = (dayModel.totalHours / chartMaxHours).coerceIn(0f, 1f)
                        val minVisualFraction = if (dayModel.totalHours > 0f) 0.12f else 0.04f
                        val targetFraction = maxOf(rawFraction, minVisualFraction)

                        val animatedHeight by animateFloatAsState(
                            targetValue = targetFraction,
                            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                            label = "bar_anim_${dayModel.dayOfWeek}"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedDayOfWeek = dayModel.dayOfWeek
                                    onDaySelected?.invoke(dayModel)
                                }
                                .padding(horizontal = 2.dp)
                        ) {
                            // Top Value Label on Bar
                            Box(
                                modifier = Modifier
                                    .height(18.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dayModel.totalHours > 0f) {
                                    val formattedLabel = if (dayModel.totalHours >= 1.0f) {
                                        String.format(Locale.US, "%.1fh", dayModel.totalHours)
                                    } else {
                                        "${(dayModel.totalSeconds / 60)}m"
                                    }

                                    Text(
                                        text = formattedLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) accentLime else textSecondary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Main Bar Capsule
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(115.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) {
                                            if (isDark) Color(0x30FFFFFF) else Color(0x20000000)
                                        } else {
                                            if (isDark) Color(0x12FFFFFF) else Color(0x0C000000)
                                        }
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else if (dayModel.isToday) 1.dp else 0.dp,
                                        color = if (isSelected) accentLime else if (dayModel.isToday) Color(0x668CE000) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(animatedHeight)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            when {
                                                dayModel.isGoalMet -> {
                                                    // Goal exceeded: Vibrant glowing gradient
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            RegainLimeLight,
                                                            RegainLimePrimary
                                                        )
                                                    )
                                                }
                                                dayModel.totalHours > 0f -> {
                                                    // Active hours: Soft Regain Lime
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            RegainLimePrimary.copy(alpha = 0.85f),
                                                            RegainLimeDeepText.copy(alpha = 0.5f)
                                                        )
                                                    )
                                                }
                                                else -> {
                                                    // Inactive / 0h
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            if (isDark) Color(0x20FFFFFF) else Color(0x18000000),
                                                            if (isDark) Color(0x10FFFFFF) else Color(0x0A000000)
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Day Label & Today Indicator
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = dayModel.dayShortName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = if (isSelected) textPrimary else if (dayModel.isToday) accentLime else textSecondary,
                                        fontWeight = if (isSelected || dayModel.isToday) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Day Inspector Tooltip Card
            Surface(
                color = if (isDark) Color(0xFF1B221A) else Color(0xFFF2F7EE),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDark) Color(0x258CE000) else Color(0x358CE000)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedDayModel.isGoalMet) RegainLimePrimary else Color(0xFFA0A89E))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedDayModel.dayShortName} · ${selectedDayModel.dateFormatted}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary,
                                    fontSize = 13.sp
                                )
                            )
                            if (selectedDayModel.isToday) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = RegainLimePrimary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "TODAY",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = accentLime,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Hours formatted
                        val formattedDuration = if (selectedDayModel.totalHours >= 0.1f) {
                            val h = (selectedDayModel.totalSeconds / 3600)
                            val m = (selectedDayModel.totalSeconds % 3600) / 60
                            if (h > 0) "${h}h ${m}m" else "${m} mins"
                        } else {
                            "0 mins focus"
                        }

                        Text(
                            text = formattedDuration,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = accentLime,
                                fontSize = 13.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary info row: Goal progress, sessions count, top category
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val goalPercent = if (targetDailyGoalHours > 0f) {
                            ((selectedDayModel.totalHours / targetDailyGoalHours) * 100).toInt()
                        } else 0

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedDayModel.isGoalMet) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = accentLime,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = "$goalPercent% of ${String.format(Locale.US, "%.1f", targetDailyGoalHours)}h goal",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = if (selectedDayModel.isGoalMet) accentLime else textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedDayModel.isGoalMet) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }

                        Text(
                            text = "${selectedDayModel.sessionCount} session${if (selectedDayModel.sessionCount == 1) "" else "s"} · ${selectedDayModel.topSubject}",
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

            Spacer(modifier = Modifier.height(14.dp))

            // 3-Pill Weekly Key Insights Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pill 1: Daily Average
                Surface(
                    color = if (isDark) Color(0xFF141914) else Color(0xFFF7FAF4),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DAILY AVG",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.1f", summary.dailyAverageHours)}h/day",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Pill 2: Best Day
                Surface(
                    color = if (isDark) Color(0xFF141914) else Color(0xFFF7FAF4),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "BEST DAY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (summary.peakDayHours > 0f) "${summary.peakDayName} (${String.format(Locale.US, "%.1fh", summary.peakDayHours)})" else "None",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = accentLime,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Pill 3: Goals Hit
                Surface(
                    color = if (isDark) Color(0xFF141914) else Color(0xFFF7FAF4),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "GOALS HIT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${summary.goalsMetDaysCount}/7 days",
                            style = MaterialTheme.typography.titleSmall.copy(
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
    }
}
