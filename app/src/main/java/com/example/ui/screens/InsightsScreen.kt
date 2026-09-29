package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusSessionEntity
import com.example.data.model.ReflectionEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.WeeklyProductivityChart
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
import java.util.Date
import java.util.Locale

@Composable
fun InsightsScreen(
    sessions: List<FocusSessionEntity>,
    reflections: List<ReflectionEntity>,
    userPreferences: UserPreferencesEntity? = null,
    onSaveReflection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight
    val accentLime = if (isDark) RegainLimePrimary else RegainLimeDeepText

    val totalSeconds = remember(sessions) { sessions.sumOf { it.durationSeconds } }
    val totalHours = remember(totalSeconds) {
        if (totalSeconds > 0) String.format(Locale.US, "%.1f", totalSeconds / 3600.0) else "0.0"
    }
    val sessionCount = sessions.size

    val completionRate = remember(sessions) {
        if (sessions.isNotEmpty()) {
            val completed = sessions.count { it.durationSeconds >= it.targetDurationSeconds * 0.8 }
            "${(completed * 100) / sessions.size}%"
        } else {
            "0%"
        }
    }

    val morningSecs = remember(sessions) {
        sessions.filter { it.hourOfDay in 5..11 }.sumOf { it.durationSeconds }
    }
    val afternoonSecs = remember(sessions) {
        sessions.filter { it.hourOfDay in 12..16 }.sumOf { it.durationSeconds }
    }
    val eveningSecs = remember(sessions) {
        sessions.filter { it.hourOfDay in 17..23 || it.hourOfDay in 0..4 }.sumOf { it.durationSeconds }
    }
    val totalCadenceSecs = morningSecs + afternoonSecs + eveningSecs

    val (morningPct, afternoonPct, eveningPct) = remember(totalCadenceSecs, morningSecs, afternoonSecs, eveningSecs) {
        if (totalCadenceSecs > 0) {
            val m = ((morningSecs.toFloat() / totalCadenceSecs) * 100).toInt()
            val a = ((afternoonSecs.toFloat() / totalCadenceSecs) * 100).toInt()
            val e = (100 - m - a).coerceAtLeast(0)
            Triple(m, a, e)
        } else {
            Triple(0, 0, 0)
        }
    }

    val activeDaysText = remember(sessions) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val days = sessions.filter { it.completedAt > 0 }.map { sdf.format(Date(it.completedAt)) }.toSet().size
        "${days}d"
    }

    val flowPercentageText = remember(sessions) {
        if (sessions.isEmpty()) {
            "0% Flow"
        } else {
            val peakHour = sessions.groupBy { it.hourOfDay }.maxByOrNull { it.value.size }?.key ?: 9
            val peakSessions = sessions.filter { it.hourOfDay in peakHour..(peakHour + 2) }
            val peakDuration = peakSessions.sumOf { it.durationSeconds }
            val totalDuration = sessions.sumOf { it.durationSeconds }
            if (totalDuration > 0) {
                val pct = ((peakDuration.toFloat() / totalDuration.toFloat()) * 100).toInt().coerceIn(1, 100)
                "+$pct% Flow"
            } else {
                val completedRatio = ((sessions.count { it.durationSeconds >= it.targetDurationSeconds * 0.8 }.toFloat() / sessions.size) * 100).toInt().coerceIn(1, 100)
                "+$completedRatio% Flow"
            }
        }
    }

    val peakFlowText = remember(sessions) {
        if (sessions.isEmpty()) {
            "First session unlocks peak window"
        } else {
            val peakHour = sessions.groupBy { it.hourOfDay }.maxByOrNull { it.value.size }?.key ?: 9
            val startAmPm = if (peakHour < 12) "AM" else "PM"
            val displayStart = if (peakHour % 12 == 0) 12 else peakHour % 12
            val endHour = (peakHour + 2) % 24
            val endAmPm = if (endHour < 12) "AM" else "PM"
            val displayEnd = if (endHour % 12 == 0) 12 else endHour % 12
            String.format(Locale.US, "%02d:00 %s – %02d:00 %s", displayStart, startAmPm, displayEnd, endAmPm)
        }
    }

    val subjectBreakdown = remember(sessions) {
        if (sessions.isEmpty()) {
            emptyList()
        } else {
            sessions.groupBy { it.taskTitle.ifBlank { "General" } }
                .map { (subject, sessionList) ->
                    val totalSecs = sessionList.sumOf { it.durationSeconds }
                    val hrs = totalSecs / 3600.0
                    val formatted = if (hrs >= 0.1) {
                        String.format(Locale.US, "%.1f hrs", hrs)
                    } else {
                        "${(totalSecs / 60).coerceAtLeast(1)} mins"
                    }
                    Triple(subject, totalSecs, formatted)
                }
                .sortedByDescending { it.second }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AuroraBackground(modifier = Modifier.fillMaxSize())

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .testTag("insights_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Minimalist Header
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "WEEKLY METRICS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            letterSpacing = 2.sp,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Insights",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 24.sp
                        )
                    )
                }
            }

            // Weekly Productivity Visualization Chart
            item {
                val dailyGoalHours = remember(userPreferences) {
                    (userPreferences?.dailyGoalMinutes?.toFloat() ?: 120f) / 60f
                }
                WeeklyProductivityChart(
                    sessions = sessions,
                    targetDailyGoalHours = dailyGoalHours,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Clean 3-Metric Horizontal Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Metric 1: Sessions
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$sessionCount",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "SESSIONS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Metric 2: Completion
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = completionRate,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = accentLime
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "COMPLETION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Metric 3: Active Days
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = activeDaysText,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ACTIVE DAYS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // Peak Flow Window Pill
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(RegainLimeContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = null,
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "PEAK FLOW WINDOW",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.2.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = peakFlowText,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = textPrimary
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RegainLimeContainer)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = flowPercentageText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = RegainLimeDeepText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // NEW FEATURE 2: Focus Time by Subject
            if (subjectBreakdown.isNotEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "FOCUS TIME BY SUBJECT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                subjectBreakdown.forEach { (subject, _, formatted) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(accentLime)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = subject,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = textPrimary
                                                )
                                            )
                                        }
                                        Text(
                                            text = formatted,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = accentLime
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Daily Time Distribution Bar
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "FOCUS TIME BY CADENCE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (totalCadenceSecs == 0) {
                            Text(
                                text = "No study sessions recorded yet. Start your focus timer to track real-time morning, afternoon & evening distribution.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = textSecondary,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            val safeMorningWeight = (morningPct / 100f).coerceAtLeast(0.01f)
                            val safeAfternoonWeight = (afternoonPct / 100f).coerceAtLeast(0.01f)
                            val safeEveningWeight = (eveningPct / 100f).coerceAtLeast(0.01f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(safeMorningWeight)
                                        .fillMaxHeight()
                                        .background(RegainLimePrimary)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(safeAfternoonWeight)
                                        .fillMaxHeight()
                                        .background(RegainLimeLight)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(safeEveningWeight)
                                        .fillMaxHeight()
                                        .background(cardBorder)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RegainLimePrimary))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Morning $morningPct%", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Medium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RegainLimeLight))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Afternoon $afternoonPct%", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Medium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(cardBorder))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Evening $eveningPct%", fontFamily = PoppinsFontFamily, fontSize = 11.sp, color = textSecondary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
