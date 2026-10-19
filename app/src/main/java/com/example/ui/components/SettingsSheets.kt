package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import com.example.util.ScreenTimeBudgetManager

/**
 * v1.3.4 (item 9) — Settings consolidation. Entries that serve the SAME
 * feature now live behind ONE entry that opens ONE premium sheet (the
 * Block-sheet pattern). No control was removed: every previously reachable
 * toggle/row below is reachable inside its merged sheet.
 *
 * Merged sheets in this file:
 * - [DailyTargetsSheet]: Daily Focus Target (goal chips) + Daily Screen
 *   Time (budget row opening the existing ScreenTimeBudgetSheet).
 * - [SessionFeedbackSheet]: Haptic Feedback + Local Session Chimes toggles.
 * - [StudyNotificationsSheet]: Block Notifications in Study toggle + the
 *   Do-Not-Disturb access / test-silence action.
 *
 * Design: premium design-system sheets (focivoTokens), Poppins body,
 * Instrument Serif italic titles only. English only, zero emojis.
 */

/** The design's toggle, shared by the merged settings sheets. */
@Composable
private fun MergedSheetToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = focivoTokens()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.size(width = 48.dp, height = 28.dp),
        colors = SwitchDefaults.colors(
            checkedThumbColor = t.bg,
            checkedTrackColor = t.accent,
            uncheckedThumbColor = t.thumbOff,
            uncheckedTrackColor = t.trackOff,
            uncheckedBorderColor = Color.Transparent,
            checkedBorderColor = Color.Transparent
        )
    )
}

/** One toggle row inside a merged sheet: title + subtitle + switch. */
@Composable
private fun MergedSheetToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val t = focivoTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = t.text
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    color = t.muted
                )
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        MergedSheetToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Shared premium sheet scaffold: drag handle, icon tile, serif title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergedSheetScaffold(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val t = focivoTokens()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = t.bg,
        dragHandle = null,
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetDragHandle()
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(t.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = t.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = SerifItalicFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = t.text
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    color = t.muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                ),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            content()
        }
    }
}

private fun mergedSectionLabel(text: String): @Composable () -> Unit = {
    val t = focivoTokens()
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = PoppinsFontFamily,
            color = t.muted,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.76.sp,
            fontSize = 11.sp
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Merged entry 1 — Daily Targets. The old "Daily Focus Target" card (goal
 * chips) and the old "Daily Screen Time" card (budget row) were two entries
 * for one feature: your daily time plan. Both live here now.
 */
@Composable
fun DailyTargetsSheet(
    currentGoalMinutes: Int,
    onUpdateDailyGoal: (Int) -> Unit,
    budgetMinutes: Int,
    onOpenScreenTimeBudget: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = focivoTokens()
    val goalOptions = listOf(120, 240, 360, 480) // 2h, 4h, 6h, 8h
    MergedSheetScaffold(
        icon = Icons.Default.Schedule,
        title = "Daily Targets",
        subtitle = "Your daily focus goal and your screen-time limit, in one place.",
        onDismiss = onDismiss,
        testTag = "daily_targets_sheet",
        modifier = modifier
    ) {
        mergedSectionLabel("FOCUS GOAL")()
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("daily_targets_goal_chips"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            goalOptions.forEach { minutes ->
                val hrs = minutes / 60
                val isSelected = currentGoalMinutes == minutes
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) t.accent else t.surface)
                        .clickable { onUpdateDailyGoal(minutes) }
                        .padding(vertical = 12.dp)
                        .testTag("daily_target_chip_$minutes"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$hrs h / day",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = PoppinsFontFamily,
                            color = if (isSelected) t.bg else t.text,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        mergedSectionLabel("SCREEN TIME")()
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(t.surface)
                .clickable { onOpenScreenTimeBudget() }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("daily_targets_budget_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily screen-time limit",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = t.text
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (budgetMinutes > 0)
                        "Lock non-essentials past ${ScreenTimeBudgetManager.budgetLabel(budgetMinutes)} / day"
                    else "Off — set a limit to enable 3h breaks",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        color = t.muted
                    )
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (budgetMinutes > 0) {
                    Text(
                        text = ScreenTimeBudgetManager.budgetLabel(budgetMinutes),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = t.accent,
                            fontSize = 15.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = t.muted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Merged entry 2 — Session Feedback. The old scattered "Haptic Feedback"
 * and "Local Session Chimes" toggles were two entries for one feature:
 * how Focivo signals you during sessions. Both toggles live here now.
 */
@Composable
fun SessionFeedbackSheet(
    hapticsEnabled: Boolean,
    onUpdateHaptics: (Boolean) -> Unit,
    chimesEnabled: Boolean,
    onUpdateChimes: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    MergedSheetScaffold(
        icon = Icons.Default.Vibration,
        title = "Session Feedback",
        subtitle = "How Focivo signals you while a session runs.",
        onDismiss = onDismiss,
        testTag = "session_feedback_sheet",
        modifier = modifier
    ) {
        MergedSheetToggleRow(
            title = "Haptic Feedback",
            subtitle = "Gentle vibration on session actions",
            checked = hapticsEnabled,
            onCheckedChange = onUpdateHaptics,
            testTag = "session_feedback_haptics_row"
        )
        Spacer(modifier = Modifier.height(18.dp))
        MergedSheetToggleRow(
            title = "Local Session Chimes",
            subtitle = "Soft chimes when sessions start and end",
            checked = chimesEnabled,
            onCheckedChange = onUpdateChimes,
            testTag = "session_feedback_chimes_row"
        )
    }
}

/**
 * Merged entry 3 — Study Notifications. The old "Block Notifications in
 * Study" toggle plus its Do-Not-Disturb access / test-silence action were
 * buried inside the Feedback card; they are one feature and get one entry
 * now. Behaviour (permission flows, toasts) is unchanged — the callbacks
 * are the exact ones the Settings screen used inline before.
 */
@Composable
fun StudyNotificationsSheet(
    blockEnabled: Boolean,
    onBlockChange: (Boolean) -> Unit,
    hasDndPermission: Boolean,
    onDndButtonClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = focivoTokens()
    MergedSheetScaffold(
        icon = Icons.Default.Notifications,
        title = "Study Notifications",
        subtitle = "Keep app alerts quiet while the timer is running.",
        onDismiss = onDismiss,
        testTag = "study_notifications_sheet",
        modifier = modifier
    ) {
        MergedSheetToggleRow(
            title = "Block Notifications in Study",
            subtitle = "Mutes distracting app alerts and pings while the timer is running",
            checked = blockEnabled,
            onCheckedChange = onBlockChange,
            testTag = "study_notifications_block_row"
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(t.accent)
                .clickable { onDndButtonClick() }
                .testTag("study_notifications_dnd_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (!hasDndPermission) "Enable Do Not Disturb Access" else "Block Notifications Now",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = t.bg
                )
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = t.muted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Blocking needs Do Not Disturb access. You will be taken to system settings once.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    color = t.muted
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
