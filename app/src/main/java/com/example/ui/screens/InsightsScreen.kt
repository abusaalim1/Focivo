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
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import java.util.Locale

@Composable
fun InsightsScreen(
    sessions: List<FocusSessionEntity>,
    reflections: List<ReflectionEntity>,
    userPreferences: UserPreferencesEntity? = null,
    onSaveReflection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
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

    val daysLabels = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
    val maxSecondsInADay = remember(sessions) {
        (1..7).maxOfOrNull { day ->
            sessions.filter { it.dayOfWeek == day }.sumOf { it.durationSeconds }
        }?.coerceAtLeast(1) ?: 1
    }

    val weeklyBars = remember(sessions, maxSecondsInADay) {
        daysLabels.map { (dayNum, label) ->
            val daySecs = sessions.filter { it.dayOfWeek == dayNum }.sumOf { it.durationSeconds }
            val fraction = if (sessions.isEmpty()) {
                0.15f
            } else if (daySecs > 0) {
                (daySecs.toFloat() / maxSecondsInADay.toFloat()).coerceIn(0.18f, 1f)
            } else {
                0.15f
            }
            Pair(label, fraction)
        }
    }

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

    val streakText = remember(userPreferences?.currentStreak) {
        "${userPreferences?.currentStreak ?: 0}d"
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

            // Total Flow Hours Card
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "TOTAL FLOW TIME",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                letterSpacing = 1.6.sp,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = totalHours,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 54.sp,
                                    lineHeight = 60.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HOURS",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = accentLime,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Weekly Minimal Column Chart
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weeklyBars.forEach { bar ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(26.dp)
                                            .height((76 * bar.second).dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (bar.second >= 0.8f) {
                                                    RegainLimePrimary
                                                } else {
                                                    if (isDark) MaterialTheme.colorScheme.surfaceVariant else RegainLimeContainer
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = bar.first,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = if (bar.second >= 0.8f) textPrimary else textSecondary,
                                            fontWeight = if (bar.second >= 0.8f) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
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

                    // Metric 3: Streak
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
                                text = streakText,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "STREAK",
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                text = "+42% Flow",
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
