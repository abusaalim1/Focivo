package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
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
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoHeadline
import com.example.ui.theme.focivoTokens
import com.example.util.WeeklyRecapImageGenerator
import kotlinx.coroutines.launch

/**
 * v1.3.3 (item 7) — Sunday Recap restyle: rebuilt fully onto the Focivo
 * design system (FocivoTokens, Poppins body, Instrument Serif italic for
 * the headline + highlight numbers). Same stats, same week logic, same
 * share actions — visuals only.
 */
@Composable
fun SundayRecapGlassDialog(
    recap: WeeklyRecapSummary,
    onDismiss: () -> Unit,
    onShare: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
        val T = focivoTokens()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(T.scrim)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .testTag("sunday_recap_glass_dialog"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        T.accent.copy(alpha = 0.22f),
                        RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = T.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Top bar: badge + close.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(T.softAccent)
                                .border(1.dp, T.chipBorder, RoundedCornerShape(99.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = T.accentText,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SUNDAY RECAP",
                                    style = focivoEyebrow().copy(color = T.accentText)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(T.surface2)
                                .testTag("close_sunday_recap_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = T.text,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        // Header: mascot + serif headline + week range.
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                RegainMascotView(
                                    width = 96.dp,
                                    height = 104.dp,
                                    pose = MascotPose.CELEBRATING
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Weekly Focus Recap",
                                    style = focivoHeadline(),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = recap.weekIdentifier,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = T.muted,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Share story-card pill.
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(T.softAccent)
                                        .border(1.dp, T.chipBorder, RoundedCornerShape(99.dp))
                                        .clickable { handleShareImage() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isSharingImage) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = T.accentText,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                tint = T.accentText,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Text(
                                            text = if (isSharingImage) "Preparing card..." else "Share story image card",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                color = T.accentText,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Metric cards: Focus Time / Sessions / Best Day.
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Schedule,
                                    title = "Focus Time",
                                    value = "${recap.totalHoursFormatted}h",
                                    subValue = "${recap.totalMinutesFocused} mins"
                                )
                                MetricCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.CheckCircle,
                                    title = "Sessions",
                                    value = "${recap.sessionCount}",
                                    subValue = "completed"
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.EmojiEvents,
                                    title = "Best Day",
                                    value = recap.bestDayName.take(3),
                                    subValue = "${recap.bestDayMinutes} mins logged"
                                )
                                MetricCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Star,
                                    title = "Top Subject",
                                    value = recap.topSubject.take(8),
                                    subValue = "this week"
                                )
                            }
                        }

                        // Daily consistency bars.
                        if (recap.dailyBreakdowns.isNotEmpty()) {
                            item {
                                DailyBarSection(
                                    dailyStats = recap.dailyBreakdowns,
                                    bestDayName = recap.bestDayName
                                )
                            }
                        }

                        // Subject breakdown.
                        if (recap.subjectBreakdowns.isNotEmpty()) {
                            item {
                                SubjectBreakdownSection(
                                    subjectStats = recap.subjectBreakdowns
                                )
                            }
                        }

                        // Session log.
                        if (recap.sessionDetails.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(T.surface)
                                        .border(1.dp, T.line, RoundedCornerShape(18.dp))
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
                                                tint = T.accentText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Session Log (${recap.sessionDetails.size})",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = T.text,
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
                                                    text = if (showAllSessions) "Show less" else "View all",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontFamily = PoppinsFontFamily,
                                                        color = T.accentText,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 11.5.sp
                                                    )
                                                )
                                                Icon(
                                                    imageVector = if (showAllSessions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    tint = T.accentText,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    val visibleSessions = if (showAllSessions) recap.sessionDetails else recap.sessionDetails.take(3)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (session in visibleSessions) {
                                            SessionItemRow(session = session)
                                        }
                                    }
                                }
                            }
                        }

                        // Week insight card.
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(T.softAccent)
                                    .border(1.dp, T.chipBorder, RoundedCornerShape(18.dp))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = T.accentText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "WEEK INSIGHT",
                                            style = focivoEyebrow().copy(color = T.accentText)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = recap.motivationalQuote,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = T.text,
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bottom actions: share image / share text / done.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassButton(
                                text = if (isSharingImage) "Preparing..." else "Share Card",
                                onClick = handleShareImage,
                                isPrimary = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = T.onAccent,
                                        modifier = Modifier.size(17.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("share_sunday_recap_image_btn")
                            )

                            GlassButton(
                                text = "Text",
                                onClick = handleShareText,
                                isPrimary = false,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = T.accentText,
                                        modifier = Modifier.size(17.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(0.7f)
                                    .testTag("share_sunday_recap_text_btn")
                            )
                        }

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
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subValue: String
) {
    val T = focivoTokens()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(T.surface2)
            .border(1.dp, T.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    color = T.muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.8.sp
                )
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = T.accentText,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Highlight number: Instrument Serif italic.
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = SerifItalicFontFamily,
                fontStyle = FontStyle.Italic,
                color = T.text,
                fontSize = 24.sp,
                letterSpacing = (-0.3).sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subValue,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PoppinsFontFamily,
                color = T.muted,
                fontSize = 10.5.sp
            )
        )
    }
}

@Composable
private fun DailyBarSection(
    dailyStats: List<WeeklyDailyStat>,
    bestDayName: String
) {
    val T = focivoTokens()
    val maxMins = maxOf(1, dailyStats.maxOfOrNull { it.totalMinutes } ?: 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(T.surface)
            .border(1.dp, T.line, RoundedCornerShape(18.dp))
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
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = T.text,
                    fontSize = 14.sp
                )
            )
            if (bestDayName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(T.softAccent)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Best: $bestDayName",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = T.accentText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
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
                    if (stat.totalMinutes > 0) {
                        Text(
                            text = if (stat.totalMinutes >= 60) "${stat.totalMinutes / 60}h" else "${stat.totalMinutes}m",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = if (isBest) T.accentText else T.muted,
                                fontSize = 8.5.sp,
                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    } else {
                        Text(
                            text = "-",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = T.muted.copy(alpha = 0.4f),
                                fontSize = 8.5.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    val barBrush = if (isBest) {
                        Brush.verticalGradient(listOf(T.accent, T.accentText))
                    } else if (stat.totalMinutes > 0) {
                        Brush.verticalGradient(
                            listOf(
                                T.accent.copy(alpha = 0.55f),
                                T.accent.copy(alpha = 0.3f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .fillMaxHeight(0.62f)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(T.surface2),
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

                    Text(
                        text = stat.dayName.take(3),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = if (isBest) T.text else T.muted,
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
    subjectStats: List<WeeklySubjectStat>
) {
    val T = focivoTokens()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(T.surface)
            .border(1.dp, T.line, RoundedCornerShape(18.dp))
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
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = T.text,
                    fontSize = 14.sp
                )
            )
            Text(
                text = "${subjectStats.size} subjects",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    color = T.muted,
                    fontSize = 11.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = T.text,
                                fontSize = 12.5.sp
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$formattedTime",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = SerifItalicFontFamily,
                                fontStyle = FontStyle.Italic,
                                color = T.accentText,
                                fontSize = 13.sp
                            )
                        )
                    }
                    Text(
                        text = "${sub.sessionCount} session${if (sub.sessionCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = T.muted,
                            fontSize = 10.5.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(T.surface2)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sub.percentage.coerceIn(0.05f, 1f))
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(T.accent, T.accentText)
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
    session: WeeklySessionDetail
) {
    val T = focivoTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(T.surface2)
            .border(1.dp, T.line, RoundedCornerShape(12.dp))
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
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = T.text,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.subject,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = T.accentText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp
                        )
                    )
                    Text(
                        text = " · ${session.formattedTime}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = T.muted,
                            fontSize = 10.sp
                        )
                    )
                }
                if (session.notes.isNotBlank()) {
                    Text(
                        text = "\"${session.notes}\"",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = T.muted.copy(alpha = 0.85f),
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(T.softAccent)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${session.durationMinutes}m",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = T.accentText,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
