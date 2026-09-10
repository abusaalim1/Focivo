package com.example.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SupabaseScheduledBlockDto
import com.example.service.ScheduledBlockScheduler
import com.example.ui.components.pressFeedback
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.util.AppIconView
import com.example.util.DeviceAppInfo
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditSheet(
    schedule: SupabaseScheduledBlockDto?,
    installedApps: List<DeviceAppInfo>,
    onSave: (SupabaseScheduledBlockDto) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isEditMode = schedule != null

    var label by remember { mutableStateOf(schedule?.label ?: "Deep Study Routine") }
    var startTime by remember { mutableStateOf(schedule?.start_time ?: "18:00") }
    var endTime by remember { mutableStateOf(schedule?.end_time ?: "22:00") }

    var hasBreak by remember {
        mutableStateOf(!schedule?.break_start_time.isNullOrBlank() && !schedule?.break_end_time.isNullOrBlank())
    }
    var breakStartTime by remember { mutableStateOf(schedule?.break_start_time ?: "20:00") }
    var breakEndTime by remember { mutableStateOf(schedule?.break_end_time ?: "20:30") }

    val allDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var selectedDays by remember {
        val initial = if (schedule != null) {
            schedule.days_active.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        } else {
            setOf("Mon", "Tue", "Wed", "Thu", "Fri")
        }
        mutableStateOf(initial)
    }

    var selectedBlockedApps by remember {
        val initial = if (schedule != null && schedule.blocked_apps_list.isNotBlank()) {
            schedule.blocked_apps_list.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        } else {
            installedApps.filter { it.category.contains("Social") || it.category.contains("Game") }.map { it.packageName }.toSet()
        }
        mutableStateOf(initial)
    }

    var isStrictMode by remember { mutableStateOf(schedule?.is_strict_mode ?: true) }

    var appSearchQuery by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val surfaceDark = Color(0xFF121412)
    val cardBackground = Color(0xFF1B221B)
    val cardBorderColor = Color(0x358CE000)
    val accentViolet = RegainLimePrimary
    val accentGreen = Color(0xFF10B981)
    val accentRed = Color(0xFFEF4444)
    val textPrimary = Color(0xFFF0F4ED)
    val textSecondary = Color(0xFFA0A89E)
    val chipBackground = Color(0xFF242C23)

    fun openTimePicker(initialTime: String, onTimePicked: (String) -> Unit) {
        val parts = initialTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 18
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val formatted = String.format("%02d:%02d", hourOfDay, minute)
                onTimePicked(formatted)
            },
            h,
            m,
            false
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surfaceDark,
        contentColor = textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditMode) "Edit Study Schedule" else "Create Study Schedule",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Label
                item {
                    Column {
                        Text(
                            text = "Schedule Name",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            placeholder = { Text("e.g. Evening Study Routine", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentViolet,
                                unfocusedBorderColor = cardBorderColor,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Section 2: Study Time Window
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBackground)
                            .border(1.dp, cardBorderColor, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Study Hours (Standard TimePicker)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Start Time Card
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(chipBackground)
                                    .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
                                    .clickable {
                                        openTimePicker(startTime) { startTime = it }
                                    }
                                    .padding(12.dp)
                            ) {
                                Text("Start Time", fontSize = 11.sp, color = textSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = accentViolet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ScheduledBlockScheduler.formatDisplayTime(startTime),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                }
                            }

                            // End Time Card
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(chipBackground)
                                    .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
                                    .clickable {
                                        openTimePicker(endTime) { endTime = it }
                                    }
                                    .padding(12.dp)
                            ) {
                                Text("End Time", fontSize = 11.sp, color = textSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = accentViolet,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = ScheduledBlockScheduler.formatDisplayTime(endTime),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 3: Break Window
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBackground)
                            .border(1.dp, cardBorderColor, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Coffee,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Scheduled Break Window",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    )
                                    Text(
                                        text = "Unblock apps temporarily during break",
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                }
                            }

                            Switch(
                                checked = hasBreak,
                                onCheckedChange = { hasBreak = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFF59E0B),
                                    uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB),
                                    uncheckedThumbColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                                )
                            )
                        }

                        if (hasBreak) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Break Start
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(chipBackground)
                                        .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
                                        .clickable {
                                            openTimePicker(breakStartTime) { breakStartTime = it }
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text("Break Start", fontSize = 11.sp, color = textSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = ScheduledBlockScheduler.formatDisplayTime(breakStartTime),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B)
                                    )
                                }

                                // Break End
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(chipBackground)
                                        .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
                                        .clickable {
                                            openTimePicker(breakEndTime) { breakEndTime = it }
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text("Break End", fontSize = 11.sp, color = textSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = ScheduledBlockScheduler.formatDisplayTime(breakEndTime),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 4: Days Active (7-day selector chips)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBackground)
                            .border(1.dp, cardBorderColor, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Active Days",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            allDays.forEach { day ->
                                val isSelected = selectedDays.contains(day)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedDays = if (isSelected) {
                                            selectedDays - day
                                        } else {
                                            selectedDays + day
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = day,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentViolet,
                                        selectedLabelColor = NearBlack,
                                        containerColor = chipBackground,
                                        labelColor = textSecondary
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) accentViolet else cardBorderColor
                                    )
                                )
                            }
                        }
                    }
                }

                // Section 5: App Selector (Reusing installed apps with search)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBackground)
                            .border(1.dp, cardBorderColor, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Blocked Apps (${selectedBlockedApps.size}/${installedApps.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TextButton(
                                    onClick = {
                                        selectedBlockedApps = installedApps.map { it.packageName }.toSet()
                                    }
                                ) {
                                    Text("Select All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentViolet)
                                }
                                TextButton(
                                    onClick = {
                                        val social = installedApps.filter {
                                            it.category.contains("Social") ||
                                            it.category.contains("Game") ||
                                            it.category.contains("Video") ||
                                            it.category.contains("Entertainment")
                                        }.map { it.packageName }.toSet()
                                        selectedBlockedApps = selectedBlockedApps + social
                                    }
                                ) {
                                    Text("Distracting", fontSize = 11.sp, color = textSecondary)
                                }
                                TextButton(onClick = { selectedBlockedApps = emptySet() }) {
                                    Text("Clear All", fontSize = 11.sp, color = accentRed)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // App Search Field
                        OutlinedTextField(
                            value = appSearchQuery,
                            onValueChange = { appSearchQuery = it },
                            placeholder = { Text("Search installed apps...", color = Color.Gray, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentViolet,
                                unfocusedBorderColor = cardBorderColor,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Apps List
                val filteredApps = installedApps.filter {
                    appSearchQuery.isBlank() ||
                    it.appName.contains(appSearchQuery, ignoreCase = true) ||
                    it.packageName.contains(appSearchQuery, ignoreCase = true)
                }

                items(filteredApps) { app ->
                    val isChecked = selectedBlockedApps.contains(app.packageName)
                    val isDistracting = app.category.contains("Social") || app.category.contains("Game") || app.category.contains("Video")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(chipBackground)
                            .clickable {
                                selectedBlockedApps = if (isChecked) {
                                    selectedBlockedApps - app.packageName
                                } else {
                                    selectedBlockedApps + app.packageName
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconView(packageName = app.packageName, contentDescription = app.appName, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isDistracting) "Distraction Risk" else app.category,
                                fontSize = 11.sp,
                                color = if (isDistracting) Color(0xFFF87171) else textSecondary
                            )
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                selectedBlockedApps = if (checked) {
                                    selectedBlockedApps + app.packageName
                                } else {
                                    selectedBlockedApps - app.packageName
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = accentViolet,
                                checkmarkColor = Color.White
                            )
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = accentRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons (Save & Delete)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isEditMode) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentRed),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(accentRed)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                }

                Button(
                    onClick = {
                        if (label.isBlank()) {
                            errorMessage = "Please enter a schedule name"
                            return@Button
                        }
                        if (selectedDays.isEmpty()) {
                            errorMessage = "Please select at least one active day"
                            return@Button
                        }
                        if (startTime == endTime) {
                            errorMessage = "Start time and end time cannot be identical"
                            return@Button
                        }

                        val scheduleToSave = SupabaseScheduledBlockDto(
                            id = schedule?.id ?: UUID.randomUUID().toString(),
                            user_id = schedule?.user_id ?: "anonymous",
                            label = label.trim(),
                            start_time = startTime,
                            end_time = endTime,
                            break_start_time = if (hasBreak) breakStartTime else null,
                            break_end_time = if (hasBreak) breakEndTime else null,
                            days_active = selectedDays.joinToString(","),
                            is_enabled = schedule?.is_enabled ?: true,
                            blocked_apps_list = selectedBlockedApps.joinToString(","),
                            is_strict_mode = isStrictMode,
                            created_at = schedule?.created_at
                        )
                        onSave(scheduleToSave)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentViolet,
                        contentColor = NearBlack
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(2f)
                        .pressFeedback()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save",
                        modifier = Modifier.size(16.dp),
                        tint = NearBlack
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEditMode) "Save Changes" else "Create Schedule",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = NearBlack
                    )
                }
            }
        }
    }

    // Confirmation dialog for deleting schedule
    if (showDeleteConfirmDialog && schedule != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Schedule?") },
            text = { Text("Are you sure you want to delete '${schedule.label}'? This will cancel any active alarms for this block.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(schedule.id)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            },
            containerColor = cardBackground,
            titleContentColor = textPrimary,
            textContentColor = textSecondary
        )
    }
}
