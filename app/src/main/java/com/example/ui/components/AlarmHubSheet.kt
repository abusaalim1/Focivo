package com.example.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.RingtoneCatalog
import com.example.data.model.AlarmItem
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmHubSheet(
    sheetState: SheetState,
    alarms: List<AlarmItem>,
    currentlyPreviewingRingtone: String?,
    defaultRingtone: String,
    onDismiss: () -> Unit,
    onToggleAlarm: (String) -> Unit,
    onDeleteAlarm: (String) -> Unit,
    onAddAlarm: (hour: Int, minute: Int, label: String, ringtone: String, vibrate: Boolean) -> Unit,
    onPreviewRingtone: (String) -> Unit,
    onSetDefaultRingtone: (String) -> Unit,
    onTestAlarm: () -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // High-contrast Theme Tokens
    val sheetBg = if (isDark) Color(0xFF13151C) else Color(0xFFFAF9F6)
    val cardBg = if (isDark) Color(0xFF1E212B) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0x35FFFFFF) else MutedBorderLight

    val textPrimary = if (isDark) Color(0xFFFFFFFF) else NearBlack
    val textSecondary = if (isDark) Color(0xFFE2E8F0) else SecondaryTextLight
    val textMuted = if (isDark) Color(0xFFA0AEC0) else Color(0xFF718096)

    val limeAccent = RegainLimePrimary
    val limeText = if (isDark) RegainLimePrimary else RegainLimeDeepText

    var isCreatingAlarm by remember { mutableStateOf(false) }

    // Add alarm form state
    var selectedHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }
    var alarmLabel by remember { mutableStateOf("") }
    var selectedRingtone by remember { mutableStateOf(defaultRingtone) }
    var vibrateEnabled by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0x40FFFFFF) else Color(0x20000000))
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("alarm_hub_sheet"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SOUND & ALARMS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = textMuted,
                                letterSpacing = 1.4.sp,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Alarm Studio",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Test Alarm Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(limeAccent.copy(alpha = 0.15f))
                                .border(1.dp, limeAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .clickable { onTestAlarm() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = limeText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Test Alarm",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = limeText,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0x28FFFFFF) else Color(0x10000000))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Alarms Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SCHEDULED ALARMS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textSecondary,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Text(
                        text = if (isCreatingAlarm) "Cancel" else "+ New Alarm",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = limeText,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .clickable { isCreatingAlarm = !isCreatingAlarm }
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    )
                }
            }

            // Inline New Alarm Creator
            if (isCreatingAlarm) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val context = LocalContext.current
                            val isAm = selectedHour < 12
                            val display12Hour = if (selectedHour % 12 == 0) 12 else selectedHour % 12

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Set Time & Ringtone",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )

                                // Clock Picker Button triggering standard TimePickerDialog
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(limeAccent.copy(alpha = 0.15f))
                                        .clickable {
                                            TimePickerDialog(
                                                context,
                                                { _, hourOfDay, minute ->
                                                    selectedHour = hourOfDay
                                                    selectedMinute = minute
                                                },
                                                selectedHour,
                                                selectedMinute,
                                                false // 12-hour view with AM/PM
                                            ).show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Clock Picker",
                                        tint = limeText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Clock Picker",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = limeText,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            // Interactive Time Picker Box (Exact 1-minute stepper & direct tap)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 12-Hour selector
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Hour (1-12)",
                                        style = MaterialTheme.typography.labelSmall.copy(color = textSecondary)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isDark) Color(0x28FFFFFF) else Color(0x0F000000))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "-",
                                            modifier = Modifier
                                                .clickable {
                                                    val new12 = if (display12Hour == 1) 12 else display12Hour - 1
                                                    selectedHour = if (isAm) (new12 % 12) else (new12 % 12) + 12
                                                }
                                                .padding(horizontal = 10.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = String.format("%02d", display12Hour),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = limeAccent
                                            ),
                                            modifier = Modifier.clickable {
                                                TimePickerDialog(
                                                    context,
                                                    { _, h, m ->
                                                        selectedHour = h
                                                        selectedMinute = m
                                                    },
                                                    selectedHour,
                                                    selectedMinute,
                                                    false
                                                ).show()
                                            }
                                        )
                                        Text(
                                            text = "+",
                                            modifier = Modifier
                                                .clickable {
                                                    val new12 = if (display12Hour == 12) 1 else display12Hour + 1
                                                    selectedHour = if (isAm) (new12 % 12) else (new12 % 12) + 12
                                                }
                                                .padding(horizontal = 10.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = textPrimary
                                        )
                                    }
                                }

                                Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textPrimary)

                                // Minute selector (Exact 1-minute stepper)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Minute (0-59)",
                                        style = MaterialTheme.typography.labelSmall.copy(color = textSecondary)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isDark) Color(0x28FFFFFF) else Color(0x0F000000))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "-",
                                            modifier = Modifier
                                                .clickable { selectedMinute = (selectedMinute - 1 + 60) % 60 }
                                                .padding(horizontal = 10.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = String.format("%02d", selectedMinute),
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = limeAccent
                                            ),
                                            modifier = Modifier.clickable {
                                                TimePickerDialog(
                                                    context,
                                                    { _, h, m ->
                                                        selectedHour = h
                                                        selectedMinute = m
                                                    },
                                                    selectedHour,
                                                    selectedMinute,
                                                    false
                                                ).show()
                                            }
                                        )
                                        Text(
                                            text = "+",
                                            modifier = Modifier
                                                .clickable { selectedMinute = (selectedMinute + 1) % 60 }
                                                .padding(horizontal = 10.dp),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = textPrimary
                                        )
                                    }
                                }

                                // Explicit AM / PM Toggle Switch
                                Column {
                                    Text(
                                        text = "Period",
                                        style = MaterialTheme.typography.labelSmall.copy(color = textSecondary)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isDark) Color(0x28FFFFFF) else Color(0x0F000000))
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isAm) limeAccent else Color.Transparent)
                                                .clickable {
                                                    if (!isAm) selectedHour = (selectedHour - 12).coerceAtLeast(0)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "AM",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isAm) NearBlack else textSecondary
                                                )
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (!isAm) limeAccent else Color.Transparent)
                                                .clickable {
                                                    if (isAm) selectedHour = (selectedHour + 12).coerceAtMost(23)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "PM",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (!isAm) NearBlack else textSecondary
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Label input
                            TextField(
                                value = alarmLabel,
                                onValueChange = { alarmLabel = it },
                                placeholder = { Text("Alarm label (e.g. Focus Cadence)", color = textMuted) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp)),
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary,
                                    focusedContainerColor = if (isDark) Color(0x28FFFFFF) else Color(0x0F000000),
                                    unfocusedContainerColor = if (isDark) Color(0x28FFFFFF) else Color(0x0F000000),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )

                            // Save Button
                            GlassButton(
                                text = "Save Alarm",
                                onClick = {
                                    onAddAlarm(
                                        selectedHour,
                                        selectedMinute,
                                        alarmLabel,
                                        selectedRingtone,
                                        vibrateEnabled
                                    )
                                    isCreatingAlarm = false
                                    alarmLabel = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                isPrimary = true
                            )
                        }
                    }
                }
            }

            // Alarms List
            if (alarms.isEmpty()) {
                item {
                    Text(
                        text = "No alarms scheduled. Tap '+ New Alarm' to add one.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textSecondary
                        )
                    )
                }
            } else {
                items(alarms, key = { it.id }) { alarm ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = alarm.formattedTime,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 24.sp,
                                                color = limeAccent
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = alarm.amPm,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = alarm.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        Text(
                                            text = " · ${alarm.ringtone}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = limeText,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onDeleteAlarm(alarm.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Switch(
                                    checked = alarm.isEnabled,
                                    onCheckedChange = { onToggleAlarm(alarm.id) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NearBlack,
                                        checkedTrackColor = limeAccent,
                                        uncheckedThumbColor = textMuted,
                                        uncheckedTrackColor = if (isDark) Color(0x30FFFFFF) else Color(0x18000000)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 10 Ringtones Showcase Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "10 ALARM RINGTONES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textSecondary,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Tap to preview",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textMuted,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // List of all 10 ringtones
            items(RingtoneCatalog.RINGTONES, key = { it.id }) { item ->
                val isPreviewing = currentlyPreviewingRingtone == item.title
                val isDefault = defaultRingtone == item.title

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPreviewRingtone(item.title)
                        },
                    shape = RoundedCornerShape(16.dp),
                    accentBorder = isDefault
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Play / Stop circular indicator
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isPreviewing) limeAccent else (if (isDark) Color(0x28FFFFFF) else Color(0x0C000000))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPreviewing) "Stop" else "Preview",
                                    tint = if (isPreviewing) NearBlack else limeText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = if (isDefault) limeText else textPrimary,
                                            fontWeight = if (isDefault) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                    )
                                    if (isDefault) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(limeAccent.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = limeText,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        // Set as default button
                        if (!isDefault) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDark) Color(0x25FFFFFF) else Color(0x10000000))
                                    .clickable { onSetDefaultRingtone(item.title) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Set",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
