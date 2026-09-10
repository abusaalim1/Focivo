package com.example.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AuroraBackground
import com.example.ui.components.DistractionDialog
import com.example.ui.components.MascotBuddySheet
import com.example.ui.components.MascotPose
import com.example.ui.components.RegainMascotView
import com.example.ui.components.RegainStudyDial
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    remainingSeconds: Int,
    targetSeconds: Int,
    isRunning: Boolean,
    currentMode: String,
    currentTaskTitle: String,
    distractionsCount: Int,
    ambientSound: String,
    userStreak: Int = 0,
    isShieldActive: Boolean = false,
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onFinishEarly: () -> Unit,
    onAddFiveMinutes: () -> Unit,
    onSkipBreak: () -> Unit,
    onSelectMode: (mode: String, durationMinutes: Int) -> Unit,
    onSelectAmbientSound: (sound: String) -> Unit,
    onLogDistraction: (type: String) -> Unit,
    onUpdateTaskTitle: (String) -> Unit,
    onOpenShieldHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

    var showDistractionModal by remember { mutableStateOf(false) }
    var showSoundSelector by remember { mutableStateOf(false) }
    var showStudyTypeDialog by remember { mutableStateOf(false) }
    var showBuddySheet by remember { mutableStateOf(false) }
    var currentTag by remember { mutableStateOf(currentTaskTitle.ifBlank { "Deep Study" }) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("focus_screen")
    ) {
        AuroraBackground(
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar: Real Streak Flame + Regain Brand Title + Shield Hub
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Streak Pill (Dynamic Real-Time Streak)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(cardBg)
                        .border(1.dp, cardBorder, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Streak",
                            tint = Color(0xFFFF7043),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${userStreak}d Streak",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // App Brand Title
                Text(
                    text = "FOCIVO",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        letterSpacing = 2.5.sp,
                        fontSize = 16.sp
                    )
                )

                // Shield Blocker Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isShieldActive) RegainLimeContainer else cardBg
                        )
                        .border(
                            1.dp,
                            if (isShieldActive) RegainLimePrimary else cardBorder,
                            CircleShape
                        )
                        .clickable { onOpenShieldHub() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield Hub",
                            tint = if (isShieldActive) RegainLimeDeepText else textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isShieldActive) "Shielded" else "Shield",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = if (isShieldActive) RegainLimeDeepText else textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Scrollable Dial, Mascot & Controls
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Regain Study Dial (Includes Mascot Video Inside Timer Circle Box)
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    RegainStudyDial(
                        remainingSeconds = remainingSeconds,
                        totalSeconds = targetSeconds,
                        isRunning = isRunning,
                        activeTag = currentTag,
                        onTagClick = {
                            showStudyTypeDialog = true
                        },
                        onTakeBreakClick = {
                            onSkipBreak()
                        },
                        onMascotClick = {
                            showBuddySheet = true
                        },
                        mascotPose = if (isRunning) MascotPose.STUDYING else MascotPose.IDLE
                    )
                }

                // 3. Main Action Controls
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shield Toggle
                        IconButton(
                            onClick = onOpenShieldHub,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(1.dp, cardBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield Settings",
                                tint = if (isShieldActive) RegainLimeDeepText else textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Main Study Action Button (Vivid Lime Pill)
                        Box(
                            modifier = Modifier
                                .height(58.dp)
                                .width(180.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    ambientColor = RegainLimePrimary.copy(alpha = 0.4f),
                                    spotColor = RegainLimePrimary.copy(alpha = 0.5f)
                                )
                                .clip(CircleShape)
                                .background(RegainLimePrimary)
                                .clickable {
                                    if (isRunning) onPauseTimer() else onResumeTimer()
                                }
                                .testTag("play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isRunning) "Pause Study" else "Start Study",
                                    tint = NearBlack,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isRunning) "PAUSE" else "START STUDY",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = NearBlack,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }

                        // Distraction Logger
                        IconButton(
                            onClick = { showDistractionModal = true },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(1.dp, cardBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = "Log Distraction",
                                tint = if (distractionsCount > 0) Color(0xFFE53935) else textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 4. Time Adder Controls (+5m, +10m, +15m)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                                .clickable { onAddFiveMinutes() }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+5 min",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                                .clickable { showStudyTypeDialog = true }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Custom Timer & Subject",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // 5. Preset Study Duration Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            Triple("Sprint", 15, "15m"),
                            Triple("Pomodoro", 25, "25m"),
                            Triple("Deep Study", 50, "50m"),
                            Triple("Marathon", 90, "90m")
                        ).forEach { (modeName, mins, label) ->
                            val isSelected = targetSeconds == mins * 60
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) RegainLimeContainer else cardBg
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) RegainLimePrimary else cardBorder,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        onSelectMode(modeName, mins)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = if (isSelected) RegainLimeDeepText else textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = modeName,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = textSecondary,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Sound & Finish Early Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ambient sound trigger
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(1.dp, cardBorder, CircleShape)
                                .clickable { showSoundSelector = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = ambientSound,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        // Finish early button (only active if timer is running)
                        if (isRunning) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFEBEE))
                                    .border(1.dp, Color(0xFFFFCDD2), CircleShape)
                                    .clickable { onFinishEarly() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Complete Early",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = Color(0xFFD32F2F),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Modals
        if (showDistractionModal) {
            DistractionDialog(
                onDismiss = { showDistractionModal = false },
                onLogDistraction = { type: String ->
                    onLogDistraction(type)
                    showDistractionModal = false
                }
            )
        }

        // Custom Study Category & Duration Dialog
        if (showStudyTypeDialog) {
            var selectedSubject by remember { mutableStateOf(currentTag) }
            var customSubjectInput by remember { mutableStateOf("") }
            var customDurationMinutes by remember { mutableStateOf((targetSeconds / 60).toString()) }

            val presetSubjects = listOf(
                "Deep Study",
                "General Study",
                "Exam Prep",
                "Coding & Dev",
                "Reading & Research",
                "Maths & Logic",
                "Revision & Practice"
            )

            AlertDialog(
                onDismissRequest = { showStudyTypeDialog = false },
                title = {
                    Text(
                        text = "Study Focus & Custom Timer",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Select what you are studying for:",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary
                            )
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetSubjects.forEach { subject ->
                                val isChosen = selectedSubject == subject
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isChosen) RegainLimeContainer else cardBg)
                                        .border(1.dp, if (isChosen) RegainLimePrimary else cardBorder, RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedSubject = subject
                                            customSubjectInput = ""
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = subject,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = if (isChosen) RegainLimeDeepText else textPrimary,
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customSubjectInput,
                            onValueChange = {
                                customSubjectInput = it
                                if (it.isNotBlank()) selectedSubject = it
                            },
                            label = { Text("Or type custom topic") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Set Custom Timer (Minutes):",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary
                            )
                        )

                        OutlinedTextField(
                            value = customDurationMinutes,
                            onValueChange = { customDurationMinutes = it.filter { char -> char.isDigit() } },
                            label = { Text("Duration in minutes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalSubject = if (customSubjectInput.isNotBlank()) customSubjectInput.trim() else selectedSubject
                            val mins = customDurationMinutes.toIntOrNull()?.coerceIn(1, 480) ?: 25

                            currentTag = finalSubject
                            onUpdateTaskTitle(finalSubject)
                            onSelectMode(finalSubject, mins)
                            showStudyTypeDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RegainLimePrimary, contentColor = NearBlack)
                    ) {
                        Text("Apply Timer & Subject", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStudyTypeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showBuddySheet) {
            MascotBuddySheet(
                onDismiss = { showBuddySheet = false }
            )
        }
    }
}
