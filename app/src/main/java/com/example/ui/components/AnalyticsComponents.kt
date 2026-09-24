package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val isDark = isAppInDarkTheme()

    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp
                    )
                )
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = RegainLimePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp
                )
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun WeeklyFocusChart(
    dailyMinutes: List<Pair<String, Int>>, // e.g. ("Mon", 120), ("Tue", 150), ...
    maxMinutes: Int = 180,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()

    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
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
                Column {
                    Text(
                        text = "WEEKLY FOCUS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.4.sp
                        )
                    )
                    Text(
                        text = "Focus Flow per Day",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Average badge
                val totalMins = dailyMinutes.sumOf { it.second }
                val avgMins = if (dailyMinutes.isNotEmpty()) totalMins / dailyMinutes.size else 0
                val avgHours = avgMins / 60
                val avgRemainder = avgMins % 60
                val formattedAvg = if (avgHours > 0) "${avgHours}h ${avgRemainder}m" else "${avgRemainder}m"

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0x228B7CFF) else Color(0x148B7CFF))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Avg: $formattedAvg/day",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = RegainLimePrimary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Bars Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                dailyMinutes.forEach { (day, minutes) ->
                    val fraction = (minutes.toFloat() / maxMinutes.coerceAtLeast(60).toFloat()).coerceIn(0.08f, 1f)
                    val animatedHeight by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(1000, easing = FastOutSlowInEasing),
                        label = "bar_height_$day"
                    )

                    val isToday = day == "Wed" // Wednesday

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Value tooltip on top
                        if (minutes > 0) {
                            val hrs = minutes / 60
                            val m = minutes % 60
                            val label = if (hrs > 0) "${hrs}h" else "${m}m"
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    color = if (isToday) RegainLimePrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        } else {
                            Text(text = "-", fontSize = 9.sp, color = Color.Transparent)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Bar Capsule
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(90.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) Color(0x14FFFFFF) else Color(0x10000000)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(animatedHeight)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isToday) {
                                            Brush.verticalGradient(
                                                colors = listOf(RegainLimePrimary, Color(0xFFAEF72A))
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    RegainLimePrimary.copy(alpha = 0.55f),
                                                    RegainLimePrimary.copy(alpha = 0.35f)
                                                )
                                            )
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isToday) RegainLimePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductivityHeatmap(
    strongestWindow: String = "9:00 AM – 11:00 AM",
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
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
                Text(
                    text = "PRODUCTIVITY HEATMAP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.4.sp
                    )
                )
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = RegainLimePrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Highlight: Your strongest focus window
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color(0x1A8CE000) else Color(0x108CE000))
                    .border(
                        1.dp,
                        RegainLimePrimary.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RegainLimePrimary)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Your strongest focus window",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = strongestWindow,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = RegainLimePrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time of Day distribution
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val periods = listOf(
                    Triple("Morning", "8 AM - 12 PM", 0.85f),
                    Triple("Afternoon", "12 PM - 5 PM", 0.55f),
                    Triple("Evening", "5 PM - 10 PM", 0.30f)
                )

                periods.forEach { (name, hours, score) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0x18FFFFFF) else Color(0x0C000000))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            )
                            Text(
                                text = hours,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Intensity meter
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0x22FFFFFF) else Color(0x18000000))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(score)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(RegainLimePrimary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreakCalendarCard(
    currentStreak: Int = 12,
    bestStreak: Int = 18,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
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
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = RegainLimePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$currentStreak DAY STREAK",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = RegainLimePrimary,
                            fontSize = 16.sp
                        )
                    )
                }

                Text(
                    text = "Best: $bestStreak days",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Calendar dots (last 14 days)
            Text(
                text = "Recent Daily Consistency",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val pastDays = (1..14).map { it <= 12 } // 12 active days
                pastDays.forEachIndexed { idx, completed ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (completed) RegainLimePrimary else (if (isDark) Color(0x22FFFFFF) else Color(0x18000000))
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyReflectionCard(
    totalHours: String = "18h 42m",
    bestDay: String = "Wednesday",
    completionRate: Int = 86,
    initialReflection: String = "",
    onSaveReflection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    var reflectionText by remember { mutableStateOf(initialReflection) }
    var isSaved by remember { mutableStateOf(initialReflection.isNotBlank()) }

    LiquidGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
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
                Text(
                    text = "WEEKLY REFLECTION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.4.sp
                    )
                )

                Text(
                    text = "$completionRate% Complete",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = RegainLimePrimary,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "You focused for $totalHours this week.",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            )
            Text(
                text = "Your most productive day was $bestDay. You completed $completionRate% of your planned sessions.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "What helped you focus?",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = reflectionText,
                onValueChange = {
                    reflectionText = it
                    isSaved = false
                },
                placeholder = {
                    Text(
                        "e.g. Ambient rain noise & blocking notifications helped me achieve deep flow...",
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RegainLimePrimary,
                    unfocusedBorderColor = if (isDark) Color(0x22FFFFFF) else Color(0x18000000)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                GlassButton(
                    text = if (isSaved) "Saved ✓" else "Save Reflection",
                    onClick = {
                        onSaveReflection(reflectionText)
                        isSaved = true
                    },
                    isPrimary = !isSaved
                )
            }
        }
    }
}
