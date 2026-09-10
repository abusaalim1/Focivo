package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SupabaseScheduledBlockDto
import com.example.service.ScheduledBlockScheduler
import com.example.ui.components.AuroraBackground
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassSwitch
import com.example.ui.components.ScheduleEditSheet
import com.example.ui.components.pressFeedback
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.util.AppIconView
import com.example.util.DeviceAppInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutoStudyScheduleScreen(
    scheduledBlocks: List<SupabaseScheduledBlockDto>,
    installedApps: List<DeviceAppInfo>,
    statusSummary: String,
    onSaveSchedule: (SupabaseScheduledBlockDto) -> Unit,
    onToggleSchedule: (String, Boolean) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

    var activeSheetSchedule by remember { mutableStateOf<SupabaseScheduledBlockDto?>(null) }
    var isSheetOpen by remember { mutableStateOf(false) }
    var scheduleToDeleteId by remember { mutableStateOf<String?>(null) }

    fun openNewScheduleSheet() {
        activeSheetSchedule = null
        isSheetOpen = true
    }

    fun openEditScheduleSheet(schedule: SupabaseScheduledBlockDto) {
        activeSheetSchedule = schedule
        isSheetOpen = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openNewScheduleSheet() },
                containerColor = RegainLimePrimary,
                contentColor = NearBlack,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 80.dp, end = 8.dp)
                    .testTag("add_schedule_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Schedule")
                    Text(
                        text = "Add Schedule",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AuroraBackground(modifier = Modifier.fillMaxSize())

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .testTag("auto_study_schedule_screen"),
                contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("schedule_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Auto Study Schedule",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 22.sp
                                    )
                                )
                                Text(
                                    text = "Automated daily focus enforcement",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = { openNewScheduleSheet() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RegainLimeContainer)
                                .testTag("header_add_schedule")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Schedule",
                                tint = RegainLimeDeepText
                            )
                        }
                    }
                }

                // Feature One-Line Explanation Banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(RegainLimeContainer)
                            .border(1.dp, RegainLimePrimary, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = RegainLimeDeepText,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AUTOMATED & HANDS-FREE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = RegainLimeDeepText,
                                        letterSpacing = 1.2.sp,
                                        fontSize = 10.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Configure custom study and break windows once. Focivo automatically locks distracting apps during study hours and lifts blocking during breaks — every day.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = RegainLimeDeepText,
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Live Status Indicator Card
                item {
                    val isActive = statusSummary.contains("active", ignoreCase = true)
                    val isBreak = statusSummary.contains("Break", ignoreCase = true)

                    val statusBadgeBg = when {
                        isActive -> RegainLimeContainer
                        isBreak -> Color(0xFFFEF3C7) // Amber
                        else -> if (isDark) Color(0xFF1E241E) else Color(0xFFF1F5F9)
                    }

                    val statusBadgeText = when {
                        isActive -> RegainLimeDeepText
                        isBreak -> Color(0xFF92400E)
                        else -> textSecondary
                    }

                    val statusIcon = when {
                        isActive -> Icons.Default.Security
                        isBreak -> Icons.Default.Coffee
                        else -> Icons.Default.Schedule
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(cardBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                            .testTag("schedule_live_status_card")
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(statusBadgeBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = statusIcon,
                                            contentDescription = null,
                                            tint = statusBadgeText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "CURRENT SCHEDULE STATUS",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = textSecondary,
                                                letterSpacing = 1.2.sp,
                                                fontSize = 10.sp
                                            )
                                        )
                                        Text(
                                            text = statusSummary,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = textPrimary,
                                                fontSize = 15.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section Title: Configured Routines
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Study Schedules (${scheduledBlocks.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 16.sp
                            )
                        )
                        if (scheduledBlocks.isNotEmpty()) {
                            TextButton(onClick = { openNewScheduleSheet() }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Empty State if no schedules configured
                if (scheduledBlocks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                                .padding(32.dp)
                                .testTag("schedule_empty_state"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(RegainLimeContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = RegainLimeDeepText,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "No Study Schedules Created Yet",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 16.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Set up recurring study & break windows. Focivo will automatically block distracting apps during study hours every day.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textSecondary,
                                        fontSize = 13.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) Color(0xFF1E241E) else Color(0xFFF8FAFC))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "Example: Evening Study 6:45 PM – 9:30 PM\nDinner Break: 7:20 PM – 8:00 PM (blocking automatically lifts)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = { openNewScheduleSheet() },
                                    colors = ButtonDefaults.buttonColors(containerColor = RegainLimePrimary),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = NearBlack,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Create Your First Schedule",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = NearBlack
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(scheduledBlocks, key = { it.id }) { schedule ->
                        ScheduleCardItem(
                            schedule = schedule,
                            installedApps = installedApps,
                            onToggle = { isEnabled -> onToggleSchedule(schedule.id, isEnabled) },
                            onEdit = { openEditScheduleSheet(schedule) },
                            onDelete = { scheduleToDeleteId = schedule.id },
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                }
            }
        }
    }

    // Schedule Edit/Create Bottom Sheet
    if (isSheetOpen) {
        ScheduleEditSheet(
            schedule = activeSheetSchedule,
            installedApps = installedApps,
            onSave = { saved ->
                onSaveSchedule(saved)
                isSheetOpen = false
            },
            onDelete = { id ->
                onDeleteSchedule(id)
                isSheetOpen = false
            },
            onDismiss = { isSheetOpen = false }
        )
    }

    // Confirm Delete Dialog
    scheduleToDeleteId?.let { delId ->
        val targetSchedule = scheduledBlocks.firstOrNull { it.id == delId }
        AlertDialog(
            onDismissRequest = { scheduleToDeleteId = null },
            title = {
                Text(
                    text = "Delete Schedule?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${targetSchedule?.label ?: "this schedule"}'? Automated enforcement for this routine will stop immediately.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = PoppinsFontFamily
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSchedule(delId)
                        scheduleToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDeleteId = null }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCardItem(
    schedule: SupabaseScheduledBlockDto,
    installedApps: List<DeviceAppInfo>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val allDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val activeDays = remember(schedule.days_active) {
        schedule.days_active.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    val blockedPackages = remember(schedule.blocked_apps_list) {
        schedule.blocked_apps_list.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    val blockedAppsList = remember(blockedPackages, installedApps) {
        installedApps.filter { blockedPackages.contains(it.packageName) }
    }

    val timeWindowDisplay = remember(schedule.start_time, schedule.end_time) {
        "${ScheduledBlockScheduler.formatDisplayTime(schedule.start_time)} – ${ScheduledBlockScheduler.formatDisplayTime(schedule.end_time)}"
    }

    val hasBreak = !schedule.break_start_time.isNullOrBlank() && !schedule.break_end_time.isNullOrBlank()
    val breakWindowDisplay = remember(schedule.break_start_time, schedule.break_end_time) {
        if (hasBreak) {
            "Break: ${ScheduledBlockScheduler.formatDisplayTime(schedule.break_start_time!!)} – ${ScheduledBlockScheduler.formatDisplayTime(schedule.break_end_time!!)}"
        } else null
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_item_${schedule.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = schedule.label.ifBlank { "Study Routine" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontSize = 16.sp
                        )
                    )

                    if (schedule.is_strict_mode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RegainLimeContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "STRICT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = RegainLimeDeepText,
                                    fontSize = 9.sp
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                GlassSwitch(
                    checked = schedule.is_enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("schedule_toggle_${schedule.id}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time Window Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = RegainLimeDeepText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeWindowDisplay,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 15.sp
                    )
                )
            }

            // Break Window Row (if set)
            if (breakWindowDisplay != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Coffee,
                        contentDescription = null,
                        tint = Color(0xFFD97706), // Warm Amber
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = breakWindowDisplay,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD97706),
                            fontSize = 13.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(Blocking lifts)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Days Active Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                allDays.forEach { day ->
                    val isSelected = activeDays.contains(day)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) RegainLimeContainer else (if (isSystemInDarkTheme()) Color(0xFF1E241E) else Color(0xFFF1F5F9))
                            )
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) RegainLimeDeepText else textSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Blocked Apps & Edit Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (blockedAppsList.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                            blockedAppsList.take(4).forEach { app ->
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Color.White, CircleShape)
                                ) {
                                    AppIconView(
                                        packageName = app.packageName,
                                        contentDescription = app.appName,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = "${blockedPackages.size} apps blocked",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_schedule_${schedule.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Schedule",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_schedule_${schedule.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Schedule",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
