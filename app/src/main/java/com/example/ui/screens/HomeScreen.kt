package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.collectAsState
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
import com.example.ui.components.FeatureIcon
import com.example.ui.components.PremiumButton
import com.example.ui.components.LinearButton
import com.example.ui.components.LinearButtonVariant
import com.example.ui.components.LinearGlowCard
import com.example.ui.components.PriorityItemRow
import com.example.ui.components.RegainMascotView
import com.example.ui.components.MascotPose
import com.example.ui.components.SegmentedProgressBar
import com.example.ui.components.appleCard
import com.example.ui.components.pressFeedback
import com.example.ui.theme.AppleLinearFontFamily
import com.example.ui.theme.BalooBrandFontFamily
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.LocalAppAccent
import com.example.ui.theme.themedCardBackground
import com.example.ui.theme.backdropContentColor
import com.example.ui.theme.backdropTitleShadow
import com.example.util.TimerThemeManager
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
    val appAccent = LocalAppAccent.current // v1.4.0-round2 theme accent

    // Semantic colors for Light/Dark mode - Apple-inspired minimal glass aesthetic
    val cardBg = themedCardBackground() // round-3: translucent so the theme backdrop breathes through
    val cardBorder = if (isDark) Color(0x22FFFFFF) else Color(0x12000000)
    val textPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF6C6C70)
    // v1.4.3: text drawn directly over the theme backdrop (or over
    // translucent/glass surfaces above it) must use the theme's
    // onBackdropContent color — full strength for titles, >=85% alpha for
    // subtitles. App-mode textPrimary/textSecondary are NOT used on backdrops.
    // (The greeting header above is the one exception: it sits on a fixed
    // dark scrim, so it is always light via onHeaderScrim.)
    val theme by TimerThemeManager.selectedTheme.collectAsState()
    val onBackdrop = theme.onBackdropContent
    // v1.4.3: the greeting header sits on home_header_bg under a FIXED
    // 60%-black scrim — so it is ALWAYS light text. (v1.4.2 used the theme's
    // onBackdropContent here; before TimerThemeManager.init() ran the default
    // Aurora theme made it render dark-on-dark on cold start.)
    val onHeaderScrim = Color(0xFFF7F8F4)
    val titleShadow = backdropTitleShadow(onHeaderScrim)
    val headerDateColor = onHeaderScrim.copy(alpha = 0.90f)
    val headerTitleColor = onHeaderScrim
    val headerButtonBg = if (isDark) Color(0xCC2C2C2E) else Color(0xF0FFFFFF)
    val headerButtonBorder = if (isDark) Color(0x28FFFFFF) else Color(0x14000000)

    val context = androidx.compose.ui.platform.LocalContext.current
    val hasShieldPermissions = remember(context) {
        com.example.util.PermissionUtils.hasUsageStatsPermission(context) &&
        com.example.util.PermissionUtils.hasOverlayPermission(context) &&
        com.example.util.AiStudyGuardManager.isAccessibilityPermissionGranted(context)
    }
    val isShieldActive = hasShieldPermissions && (userPreferences?.isAppBlockerEnabled == true)




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
    val dailyGoalMinutes = (userPreferences?.dailyGoalMinutes ?: 240).coerceAtLeast(60)
    val dailyProgress = if (dailyGoalMinutes > 0 && todayMinutes > 0) {
        (todayMinutes.toFloat() / dailyGoalMinutes).coerceIn(0f, 1f)
    } else {
        0f
    }

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
        // Round-3: theme backdrop is drawn once at the MainActivity shell root.

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
                                    fontFamily = AppleLinearFontFamily,
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
                                    fontFamily = BalooBrandFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = headerTitleColor,
                                    shadow = titleShadow,
                                    fontSize = 26.sp
                                )
                            )
                        }

                        // v1.4.3: the SELECTED theme's mascot, always visible in the
                        // greeting header (it was never rendered on Home — the
                        // "gayeb" mascot). Resolves via theme.mascotPose, same
                        // as the Study timer circle.
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0x22000000))
                                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                .testTag("home_theme_mascot"),
                            contentAlignment = Alignment.Center
                        ) {
                            RegainMascotView(
                                pose = theme.mascotPose,
                                size = 48.dp,
                                animate = false
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
                                    .size(40.dp)
                                    .testTag("home_auto_schedule_button")
                            ) {
                                FeatureIcon(drawableRes = R.drawable.ic3d_calendar, contentDescription = "Auto Schedule", size = 40.dp)
                            }

                            IconButton(
                                onClick = onOpenAlarmStudio,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("home_alarm_button")
                            ) {
                                FeatureIcon(drawableRes = R.drawable.ic3d_alarm, contentDescription = "Alarms", size = 40.dp)
                            }
                        }
                    }
                }
            }

            // 2. Today Hero: today focus time + one stat + single white Start Focus
            // pill (reference pattern: clean dark stat card). All data real.
            item {
                val todayTimeStr = remember(todayMinutes) {
                    val h = todayMinutes / 60
                    val m = todayMinutes % 60
                    when {
                        h > 0 && m > 0 -> "${h}h ${m}m"
                        h > 0 -> "${h}h"
                        else -> "${m}m"
                    }
                }
                val todaySessionsCount = todaySessions.size
                val animatedDailyProgress by animateFloatAsState(
                    targetValue = dailyProgress,
                    animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    label = "today_goal_progress_anim"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF141716))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(26.dp))
                        .padding(22.dp)
                        .testTag("home_today_hero_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TODAY'S FOCUS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9AA79B),
                                        letterSpacing = 1.6.sp,
                                        fontSize = 11.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = todayTimeStr,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF7F8F4),
                                        fontSize = 38.sp,
                                        letterSpacing = (-0.5).sp
                                    )
                                )
                                Text(
                                    text = "Time focused",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Color(0xFF9AA79B),
                                        fontSize = 13.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0x22FFFFFF))
                                        .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$todaySessionsCount session${if (todaySessionsCount == 1) "" else "s"} today",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = RegainLimePrimary,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                            RegainMascotView(
                                pose = theme.mascotPose,
                                size = 96.dp,
                                animate = true,
                                modifier = Modifier.testTag("home_hero_mascot")
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Slim daily-goal progress (merged from the old progress card)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DAILY GOAL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9AA79B),
                                    letterSpacing = 1.4.sp,
                                    fontSize = 10.5.sp
                                )
                            )
                            Text(
                                text = "$todayMinutes / $dailyGoalMinutes min",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF7F8F4),
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x24FFFFFF))
                        ) {
                            if (animatedDailyProgress > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedDailyProgress)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(RegainLimePrimary)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // The one primary action: clean white pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onStartFocus("Deep Work Sprint", 25) }
                                )
                                .testTag("home_start_focus_pill"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Start Focus",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0D1B05),
                                    fontSize = 16.sp
                                )
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onCustomizeFocus
                                    )
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                                    .testTag("home_customize_timer"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Customize timer",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF9AA79B),
                                        fontSize = 13.sp
                                    )
                                )
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
                        .appleCard(shape = RoundedCornerShape(22.dp), isDark = isDark)
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
                            FeatureIcon(
                                drawableRes = R.drawable.ic3d_calendar,
                                contentDescription = "Auto Schedule",
                                size = 40.dp
                            )

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
                            FeatureIcon(
                                drawableRes = R.drawable.ic3d_shield,
                                contentDescription = "Focus Shield",
                                size = 40.dp
                            )

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
                                        text = if (isShieldActive) "ON" else if (!hasShieldPermissions) "SETUP" else "OFF",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = if (isShieldActive) RegainLimeDeepText else textSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                                Text(
                                    text = if (isShieldActive) "Protected during study" else if (!hasShieldPermissions) "Permissions needed" else "Tap to enable",
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

            // 6. Today's Priorities
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        // v1.4.3: section headings sit directly on the backdrop —
                        // always the theme's onBackdrop color (not app-mode text).
                        Text(
                            text = "TODAY'S PRIORITIES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = AppleLinearFontFamily,
                                color = onBackdrop.copy(alpha = 0.85f),
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "Essential Focus",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = AppleLinearFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = onBackdrop,
                                shadow = backdropTitleShadow(onBackdrop)
                            )
                        )
                    }

                    IconButton(
                        onClick = onOpenTaskCreate,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(cardBg)
                            .border(0.8.dp, cardBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Priority",
                            tint = onBackdrop,
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
                            .appleCard(shape = RoundedCornerShape(22.dp), isDark = isDark)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RegainMascotView(
                                pose = MascotPose.EMPTY_STATE,
                                size = 92.dp,
                                animate = false,
                                modifier = Modifier.testTag("home_empty_priorities_mascot")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No priorities set for today.",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = AppleLinearFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Dedicate time to the 3 tasks that will move the needle.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = AppleLinearFontFamily,
                                    color = textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            PremiumButton(
                                text = "+ Add Priority",
                                onClick = { onOpenTaskCreate() },
                                compact = true
                            )
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
