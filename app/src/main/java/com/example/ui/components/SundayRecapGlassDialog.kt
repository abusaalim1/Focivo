package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.WeeklyDailyStat
import com.example.data.model.WeeklyRecapSummary
import com.example.data.model.WeeklySessionDetail
import com.example.data.model.WeeklySubjectStat
import com.example.ui.theme.AppleLinearFontFamily
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.WeeklyRecapImageGenerator
import kotlinx.coroutines.launch

@Composable
fun SundayRecapGlassDialog(
    recap: WeeklyRecapSummary,
    onDismiss: () -> Unit,
    onShare: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = isAppInDarkTheme()
    var showAllSessions by remember { mutableStateOf(false) }
    var isSharingImage by remember { mutableStateOf(false) }

    val handleShareText = {
        WeeklyRecapImageGenerator.shareRecapText(context, recap) {
            onShare()
        }
    }

    val handleShareImage = {
        if (!isSharingImage) {
            isSharingImage = true
            coroutineScope.launch {
                try {
                    WeeklyRecapImageGenerator.shareRecapImage(context, recap) {
                        onShare()
                    }
                } finally {
                    isSharingImage = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x8A000000))
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .testTag("sunday_recap_glass_dialog"),
            contentAlignment = Alignment.Center
        ) {
            // Main Frosted Glass Card Container
            val glassBackground = if (isDark) Color(0xF2161E16) else Color(0xF8FFFFFF)
            val glassBorder = if (isDark) Color(0x408CE000) else Color(0xFFD4E6C3)
            val textPrimary = if (isDark) Color(0xFFF3F8F0) else NearBlack
            val textSecondary = if (isDark) Color(0xFFA5B2A1) else SecondaryTextLight

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        border = BorderStroke(
                            1.5.dp,
                            Brush.verticalGradient(
                                listOf(
                                    RegainLimePrimary.copy(alpha = 0.8f),
                                    glassBorder,
                                    RegainLimePrimary.copy(alpha = 0.3f)
                                )
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = glassBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Top Bar: Badge & Close Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Recap Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color(0xFF23321C) else Color(0xFFEBF7DE))
                                .border(1.dp, RegainLimePrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SUNDAY RECAP",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = AppleLinearFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                        letterSpacing = 1.sp,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0x33FFFFFF) else Color(0x14000000))
                                .testTag("close_sunday_recap_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scrollable Body Content
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        // Header Visual & Motivational Title
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                RegainMascotView(
                                    width = 100.dp,
                                    height = 110.dp,
                                    pose = MascotPose.CELEBRATING
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Weekly Focus Recap ✨",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = AppleLinearFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 22.sp,
                                        letterSpacing = (-0.4).sp
                                    )
                                )

                                Text(
                                    text = recap.weekIdentifier,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = AppleLinearFontFamily,
                                        color = textSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Quick Visual Image Card Share Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) Color(0xFF1F2D1E) else Color(0xFFE9F4DC))
                                        .border(1.dp, RegainLimePrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .clickable { handleShareImage() }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = "Share Image Card",
                                            tint = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isSharingImage) "Preparing Card..." else "Tap to Share Story Image Card 📸",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = AppleLinearFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                                fontSize = 11.5.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Top 4 Metrics Glass Cards Grid
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricGlassCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Schedule,
                                    title = "Focus Time",
                                    value = "${recap.totalHoursFormatted}h",
                                    subValue = "${recap.totalMinutesFocused} mins",
                                    isDark = isDark
                                )
                                MetricGlassCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.CheckCircle,
                                    title = "Sessions",
                                    value = "${recap.sessionCount}",
                                    subValue = "Completed",
                                    isDark = isDark
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricGlassCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.LocalFireDepartment,
                                    title = "Streak",
                                    value = "${recap.currentStreak}d",
                                    subValue = "Consistency",
                                    isDark = isDark
                                )
                                MetricGlassCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.EmojiEvents,
                                    title = "Best Day",
                                    value = recap.bestDayName.take(3),
                                    subValue = "${recap.bestDayMinutes} mins logged",
                                    isDark = isDark
                                )
                            }
                        }

                        // Daily Consistency Mini Visualizer
                        if (recap.dailyBreakdowns.isNotEmpty()) {
                            item {
                                DailyBarMiniVisualizer(
                                    dailyStats = recap.dailyBreakdowns,
                                    bestDayName = recap.bestDayName,
                                    isDark = isDark,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary
                                )
                            }
                        }

                        // Subject Breakdown ("kis session kya pdha" - Subject View)
                        if (recap.subjectBreakdowns.isNotEmpty()) {
                            item {
                                SubjectBreakdownSection(
                                    subjectStats = recap.subjectBreakdowns,
                                    isDark = isDark,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary
                                )
                            }
                        }

                        // Individual Sessions Breakdown ("kis session kya pdha" - Detailed Log View)
                        if (recap.sessionDetails.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (isDark) Color(0xFF1E281C) else Color(0xFFF3F8EC))
                                        .border(1.dp, if (isDark) Color(0x338CE000) else Color(0xFFD8E6C8), RoundedCornerShape(18.dp))
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.MenuBook,
                                                contentDescription = null,
                                                tint = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Session Log Details (${recap.sessionDetails.size})",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontFamily = AppleLinearFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textPrimary,
                                                    fontSize = 14.sp
                                                )
                                            )
                                        }

                                        if (recap.sessionDetails.size > 3) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { showAllSessions = !showAllSessions }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (showAllSessions) "Show Less" else "View All",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = AppleLinearFontFamily,
                                                        color = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 11.5.sp
                                                    )
                                                )
                                                Icon(
                                                    imageVector = if (showAllSessions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    tint = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    val visibleSessions = if (showAllSessions) recap.sessionDetails else recap.sessionDetails.take(3)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (session in visibleSessions) {
                                            SessionItemRow(
                                                session = session,
                                                isDark = isDark,
                                                textPrimary = textPrimary,
                                                textSecondary = textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // AI Motivational Coach Insight
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            if (isDark) listOf(Color(0xFF22341D), Color(0xFF1B2618))
                                            else listOf(Color(0xFFEEF9E4), Color(0xFFE5F4D7))
                                        )
                                    )
                                    .border(1.dp, RegainLimePrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Week Summary Insight",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = AppleLinearFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                                fontSize = 12.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = recap.motivationalQuote,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = AppleLinearFontFamily,
                                                color = textPrimary,
                                                fontSize = 12.5.sp,
                                                lineHeight = 18.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bottom Share Action Buttons: Share Image Card, Text & Done
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Primary Image Card Share Button
                            GlassButton(
                                text = if (isSharingImage) "Generating Card..." else "Share Image Card 🖼️",
                                onClick = handleShareImage,
                                isPrimary = true,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("share_sunday_recap_image_btn")
                            )

                            // Secondary Text Share Button
                            GlassButton(
                                text = "Text 💬",
                                onClick = handleShareText,
                                isPrimary = false,
                                modifier = Modifier
                                    .weight(0.7f)
                                    .testTag("share_sunday_recap_text_btn")
                            )
                        }

                        // Dismiss / Done Button
                        GlassButton(
                            text = "Done",
                            onClick = onDismiss,
                            isPrimary = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dismiss_sunday_recap_btn")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricGlassCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subValue: String,
    isDark: Boolean
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color(0xFF1E281C) else Color(0xFFF3F8EC))
            .border(1.dp, if (isDark) Color(0x338CE000) else Color(0xFFD8E6C8), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = AppleLinearFontFamily,
                        color = if (isDark) Color(0xFFA0ACA0) else SecondaryTextLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.5.sp
                    )
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = AppleLinearFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFF0F6EC) else NearBlack,
                    fontSize = 18.sp,
                    letterSpacing = (-0.3).sp
                )
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = AppleLinearFontFamily,
                    color = if (isDark) Color(0xFF889688) else Color(0xFF7A887A),
                    fontSize = 10.5.sp
                )
            )
        }
    }
}

@Composable
private fun DailyBarMiniVisualizer(
    dailyStats: List<WeeklyDailyStat>,
    bestDayName: String,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color
) {
    val maxMins = maxOf(1, dailyStats.maxOfOrNull { it.totalMinutes } ?: 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDark) Color(0xFF1E281C) else Color(0xFFF3F8EC))
            .border(1.dp, if (isDark) Color(0x338CE000) else Color(0xFFD8E6C8), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daily Consistency",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = AppleLinearFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    fontSize = 14.sp
                )
            )
            if (bestDayName.isNotBlank()) {
                Text(
                    text = "Best: $bestDayName 🏆",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = AppleLinearFontFamily,
                        color = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            for (stat in dailyStats) {
                val heightFraction = (stat.totalMinutes.toFloat() / maxMins.toFloat()).coerceIn(0.08f, 1f)
                val isBest = stat.isBestDay || stat.dayName.equals(bestDayName, ignoreCase = true)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Focus minutes pill
                    if (stat.totalMinutes > 0) {
                        Text(
                            text = if (stat.totalMinutes >= 60) "${stat.totalMinutes / 60}h" else "${stat.totalMinutes}m",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = AppleLinearFontFamily,
                                color = if (isBest) (if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText) else textSecondary,
                                fontSize = 8.5.sp,
                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    } else {
                        Text(text = "-", style = MaterialTheme.typography.labelSmall.copy(color = textSecondary.copy(alpha = 0.4f), fontSize = 8.5.sp))
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    val barBrush = if (isBest) {
                        Brush.verticalGradient(
                            listOf(Color(0xFFB4FB38), Color(0xFF76C400))
                        )
                    } else if (stat.totalMinutes > 0) {
                        Brush.verticalGradient(
                            listOf(Color(0xFF8CE000).copy(alpha = 0.7f), Color(0xFF65A300).copy(alpha = 0.5f))
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent)
                        )
                    }

                    // Animated Bar
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .fillMaxHeight(0.65f)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0x18000000)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFraction)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(barBrush)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Day Name Label
                    Text(
                        text = stat.dayName.take(3),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = AppleLinearFontFamily,
                            color = if (isBest) (if (isDark) Color(0xFFF0F6EC) else NearBlack) else textSecondary,
                            fontWeight = if (isBest) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectBreakdownSection(
    subjectStats: List<WeeklySubjectStat>,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDark) Color(0xFF1E281C) else Color(0xFFF3F8EC))
            .border(1.dp, if (isDark) Color(0x338CE000) else Color(0xFFD8E6C8), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Subjects Studied",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = AppleLinearFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    fontSize = 14.sp
                )
            )
            Text(
                text = "${subjectStats.size} Subjects",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = AppleLinearFontFamily,
                    color = textSecondary,
                    fontSize = 11.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (sub in subjectStats.take(5)) {
                val formattedTime = if (sub.totalMinutes >= 60) {
                    "${sub.totalMinutes / 60}h ${sub.totalMinutes % 60}m"
                } else {
                    "${sub.totalMinutes}m"
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sub.subject,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = AppleLinearFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary,
                                fontSize = 12.5.sp
                            )
                        )
                        Text(
                            text = "$formattedTime • ${sub.sessionCount} session${if (sub.sessionCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = AppleLinearFontFamily,
                                color = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Progress Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0x18000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sub.percentage.coerceIn(0.05f, 1f))
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF8CE000), Color(0xFF10B981))
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItemRow(
    session: WeeklySessionDetail,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF243021) else Color(0xFFE8F2DF))
            .border(1.dp, if (isDark) Color(0x26FFFFFF) else Color(0xFFD4E3C8), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title.ifBlank { session.subject },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = AppleLinearFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.subject,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = AppleLinearFontFamily,
                            color = if (isDark) Color(0xFFA6EB38) else RegainLimeDeepText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp
                        )
                    )
                    Text(
                        text = " • ${session.formattedTime}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = AppleLinearFontFamily,
                            color = textSecondary,
                            fontSize = 10.sp
                        )
                    )
                }
                if (session.notes.isNotBlank()) {
                    Text(
                        text = "\"${session.notes}\"",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = AppleLinearFontFamily,
                            color = textSecondary.copy(alpha = 0.85f),
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Duration badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF2E3D2A) else Color(0xFFDCEBCF))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${session.durationMinutes}m",
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
}
