package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.FocusSessionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.BuddyMascot
import com.example.ui.components.FeatureIcon
import com.example.ui.components.MascotPose
import com.example.ui.components.PriorityItemRow
import com.example.ui.components.RegainMascotView
import com.example.ui.components.RollingText
import com.example.ui.theme.LocalAppAccent
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoTokens
import com.example.util.PermissionStates
import com.example.util.RefreshOnResume
import com.example.util.TimerThemeManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Home — owner's Full App Redesign (2026-10-16), Shield token system.
 * Flat bg, serif-italic two-line greeting, Today's focus card, Auto Study /
 * Focus Shield duo card, Today's priorities. All values real; mascots frozen
 * in their current placements.
 */
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
    // v1.3.0: Squads (friends competition) — owner explicit: entered from
    // HOME, never from Study/settings.
    onOpenSquads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    val appAccent = LocalAppAccent.current // retained for compat; design system uses T.accent

    val context = androidx.compose.ui.platform.LocalContext.current
    // v1.3.3 — live shield state: re-read from the REAL system grants on every
    // ON_RESUME so a permission granted in Settings shows instantly on return.
    var hasShieldPermissions by remember {
        mutableStateOf(PermissionStates.hasCoreShieldPermissions(context))
    }
    RefreshOnResume {
        hasShieldPermissions = PermissionStates.hasCoreShieldPermissions(context)
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
        SimpleDateFormat("EEEE · MMMM d", Locale.getDefault()).format(Date())
    }
    val userName = userPreferences?.currentUserName?.takeIf { it.isNotBlank() && it != "Deep Worker" }

    val theme by TimerThemeManager.selectedTheme.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(T.bg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .testTag("home_screen"),
            contentPadding = PaddingValues(top = 26.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top: date eyebrow + serif two-line greeting; mascot + action icons.
            // (Mockup shows 3 generic icons; the app keeps its mascot + the two
            // functional action icons — flagged as a deliberate deviation.)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentDateStr.uppercase(Locale.getDefault()),
                            style = focivoEyebrow()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (userName != null) "$greeting,\n$userName" else "$greeting,",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = SerifItalicFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = T.text,
                                fontSize = 30.sp,
                                lineHeight = 35.sp,
                                letterSpacing = (-0.6).sp
                            )
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // v1.4.3: the selected theme's mascot, always visible in
                        // the greeting header. FROZEN — not moved or restyled.
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(T.surface)
                                .border(1.dp, T.line, CircleShape)
                                .testTag("home_theme_mascot"),
                            contentAlignment = Alignment.Center
                        ) {
                            RegainMascotView(
                                pose = theme.mascotPose,
                                size = 36.dp,
                                animate = false
                            )
                        }
                        IconButton(
                            onClick = onOpenAutoSchedule,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("home_auto_schedule_button")
                        ) {
                            FeatureIcon(drawableRes = R.drawable.ic3d_calendar, contentDescription = "Auto Schedule", size = 44.dp)
                        }
                        IconButton(
                            onClick = onOpenAlarmStudio,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("home_alarm_button")
                        ) {
                            FeatureIcon(drawableRes = R.drawable.ic3d_alarm, contentDescription = "Alarms", size = 44.dp)
                        }
                    }
                }
            }

            // 2. Today's focus card.
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(T.surface)
                        .border(1.dp, T.line, RoundedCornerShape(26.dp))
                        .padding(22.dp)
                        .testTag("home_today_hero_card")
                ) {
                    Text(text = "Today's focus".uppercase(Locale.getDefault()), style = focivoEyebrow())
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Motion Lab #7 — today's focus total rolls like an
                            // odometer when the minutes change.
                            RollingText(
                                text = todayTimeStr,
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = T.text,
                                    fontSize = 52.sp,
                                    lineHeight = 52.sp,
                                    letterSpacing = (-1.5).sp
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Time focused",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = T.muted,
                                    fontSize = 13.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(T.softAccent)
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                // Motion Lab #7 — today's session count rolls when it changes.
                                RollingText(
                                    text = "$todaySessionsCount session${if (todaySessionsCount == 1) "" else "s"} today",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = T.accentText,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                        // FROZEN — dressed buddy stays exactly as today.
                        BuddyMascot(
                            modifier = Modifier
                                .width(96.dp)
                                .testTag("home_hero_mascot")
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Daily goal".uppercase(Locale.getDefault()), style = focivoEyebrow().copy(fontSize = 11.sp))
                        // Motion Lab #7 — focused minutes roll when the count changes.
                        val goalCountStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = T.text,
                            fontSize = 12.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RollingText(
                                text = todayMinutes.toString(),
                                style = goalCountStyle
                            )
                            Text(
                                text = " / $dailyGoalMinutes min",
                                style = goalCountStyle
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(T.line)
                    ) {
                        if (animatedDailyProgress > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedDailyProgress)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(T.accent)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(CircleShape)
                            .background(T.accent)
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
                                color = T.onAccent,
                                fontSize = 15.sp
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
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                                .testTag("home_customize_timer"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Customize timer",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = T.muted,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // 3. Duo card: Auto Study | Focus Shield.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(T.surface)
                        .border(1.dp, T.line, RoundedCornerShape(24.dp))
                        .padding(14.dp)
                        .testTag("home_combined_status_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Study",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = T.text,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = scheduleStatusSummary,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = T.muted,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .width(1.dp)
                            .height(34.dp)
                            .background(T.line)
                    )

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
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Focus Shield",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = T.text,
                                        fontSize = 13.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (isShieldActive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(T.softAccent)
                                            .border(1.dp, T.accent.copy(alpha = 0.3f), CircleShape)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "On",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = T.accentText,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 9.sp,
                                                letterSpacing = 0.8.sp
                                            )
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isShieldActive) "Protected during study"
                                else if (!hasShieldPermissions) "Permissions needed"
                                else "Tap to enable",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = T.muted,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 4. Squads entry (v1.3.0) — owner explicit: squads live on HOME.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(T.surface)
                        .border(1.dp, T.line, RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenSquads
                        )
                        .padding(14.dp)
                        .testTag("home_squads_entry"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeatureIcon(
                        drawableRes = R.drawable.ic3d_people,
                        contentDescription = "Groups",
                        size = 38.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Groups",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = T.text,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Compete with friends",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = T.muted,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open groups",
                        tint = T.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 5. Today's priorities.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Today's priorities".uppercase(Locale.getDefault()), style = focivoEyebrow())
                    // Entry point kept for the non-empty state (the mockup only
                    // covers the empty state, which carries its own button).
                    if (topPriorities.isNotEmpty()) {
                        IconButton(
                            onClick = onOpenTaskCreate,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(T.surface)
                                .border(1.dp, T.line, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add priority",
                                tint = T.text,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (topPriorities.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(T.surface)
                            .border(1.dp, T.line, RoundedCornerShape(24.dp))
                            .padding(horizontal = 20.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // FROZEN — empty-state mascot stays as today.
                        RegainMascotView(
                            pose = MascotPose.EMPTY_STATE,
                            size = 92.dp,
                            animate = false,
                            modifier = Modifier.testTag("home_empty_priorities_mascot")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No priorities set for today",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = T.text,
                                fontSize = 14.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pick the 3 tasks that will move the needle.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = T.muted,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(T.softAccent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onOpenTaskCreate
                                )
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "+ Add priority",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = T.accentText,
                                    fontSize = 13.sp
                                )
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
