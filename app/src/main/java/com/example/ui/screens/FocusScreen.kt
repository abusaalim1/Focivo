package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.example.ui.components.AuroraTimer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.AuroraBackground
import com.example.ui.components.DistractionDialog
import com.example.ui.components.MascotBuddySheet
import com.example.ui.components.MascotPose
import com.example.ui.components.PreStudyAppBlockSheet
import com.example.ui.components.RegainMascotView
import com.example.ui.components.RegainStudyDial
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.ui.components.DeepFocusToggleCard
import com.example.ui.components.DeepFocusActiveBanner

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
    userPreferences: UserPreferencesEntity? = null,
    isShieldActive: Boolean = false,
    isDeepFocusEnabled: Boolean = false,
    isDeepFocusSessionActive: Boolean = false,
    onToggleDeepFocus: (Boolean) -> Unit = {},
    sessionStartConfirmation: String? = null,
    sessionProtectionNote: String? = null,
    showPreStudyBlockSheet: Boolean = false,
    onConfirmPreStudyBlock: (Set<String>, Boolean) -> Unit = { _, _ -> },
    onDismissPreStudyBlock: () -> Unit = {},
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
    val isDark = isAppInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

    var showDistractionModal by remember { mutableStateOf(false) }
    var showSoundSelector by remember { mutableStateOf(false) }
    var showStudyTypeDialog by remember { mutableStateOf(false) }
    var showBuddySheet by remember { mutableStateOf(false) }
    var currentTag by remember { mutableStateOf(currentTaskTitle.ifBlank { "Deep Study" }) }
    var timerStyle by remember { mutableStateOf("aurora") }

    LaunchedEffect(currentTaskTitle) {
        if (currentTaskTitle.isNotBlank()) {
            currentTag = currentTaskTitle
        }
    }

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
            // Top Bar: Clean, minimal header
            // During active session (isRunning), we fade out secondary indicators to ensure absolute focus
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRunning) {
                    // Buddy Companion Level Pill (Encouraging companion)
                    val buddyLevel = userPreferences?.buddyGrowthStage ?: 1
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RegainLimeContainer)
                            .border(1.dp, RegainLimePrimary.copy(alpha = 0.5f), CircleShape)
                            .clickable { showBuddySheet = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("focus_buddy_pill")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = "Focus Buddy",
                                tint = RegainLimeDeepText,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Buddy",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = RegainLimeDeepText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(40.dp))
                }

                // App Brand Title / Active Topic Title
                Text(
                    text = if (isRunning) "FOCUSING · ${currentTag.uppercase()}" else "REGAIN",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        letterSpacing = 2.sp,
                        fontSize = 14.sp
                    ),
                    maxLines = 1
                )

                if (!isRunning) {
                    // Shield status indicator
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isShieldActive) RegainLimeContainer else cardBg)
                            .border(
                                1.dp,
                                if (isShieldActive) RegainLimePrimary else cardBorder,
                                CircleShape
                            )
                            .clickable { onOpenShieldHub() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield",
                                tint = if (isShieldActive) RegainLimeDeepText else textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isShieldActive) "On" else "Off",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = if (isShieldActive) RegainLimeDeepText else textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }

            // Scrollable Dial, Mascot & Controls
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (isRunning) 24.dp else 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Session Start Confirmation Banner (Only shown when not running)
                if (!isRunning && !sessionStartConfirmation.isNullOrBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF2E7D32).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("session_start_confirmation_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sessionStartConfirmation,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color(0xFF81C784) else Color(0xFF1B5E20),
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Non-blocking Inline Protection Note (Only shown when not running)
                if (!isRunning && !sessionProtectionNote.isNullOrBlank()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color(0xFF3E2723) else Color(0xFFFFF3E0))
                                .border(1.dp, if (isDark) Color(0xFFFFB74D).copy(alpha = 0.4f) else Color(0xFFFFB74D), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("session_protection_note")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFF57C00),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = sessionProtectionNote,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = if (isDark) Color(0xFFFFCC80) else Color(0xFFBF360C),
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Timer Style Selector Pill (Aurora vs Classic Dial) - only visible when idle
                if (!isRunning) {
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isDark) Color(0x2BFFFFFF) else Color(0x10000000))
                                .border(
                                    1.dp,
                                    if (isDark) Color(0x358CE000) else Color(0x208CE000),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (timerStyle == "aurora") RegainLimePrimary else Color.Transparent)
                                        .clickable { timerStyle = "aurora" }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                        .testTag("timer_style_aurora_btn")
                                ) {
                                    Text(
                                        text = "✨ Aurora Timer",
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (timerStyle == "aurora") Color.Black else textSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (timerStyle == "classic") (if (isDark) Color(0x35FFFFFF) else Color.White) else Color.Transparent)
                                        .clickable { timerStyle = "classic" }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                        .testTag("timer_style_classic_btn")
                                ) {
                                    Text(
                                        text = "Classic Dial",
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = if (timerStyle == "classic") textPrimary else textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. Timer Component (Aurora Timer or Regain Study Dial)
                item {
                    Spacer(modifier = Modifier.height(2.dp))
                    if (timerStyle == "aurora") {
                        AuroraTimer(
                            totalSeconds = targetSeconds,
                            remainingSeconds = remainingSeconds,
                            isRunning = isRunning,
                            isBreak = currentMode.contains("break", ignoreCase = true),
                            subjectTitle = currentTag,
                            onTogglePlay = {
                                if (isRunning) {
                                    onPauseTimer()
                                } else if (remainingSeconds < targetSeconds) {
                                    onResumeTimer()
                                } else {
                                    onStartTimer()
                                }
                            },
                            onReset = onFinishEarly,
                            modifier = Modifier.testTag("aurora_timer_component")
                        )
                    } else {
                        RegainStudyDial(
                            remainingSeconds = remainingSeconds,
                            totalSeconds = targetSeconds,
                            isRunning = isRunning,
                            activeTag = currentTag,
                            onTagClick = {
                                if (!isRunning) showStudyTypeDialog = true
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
                }

                // 2. Preset Study Duration Chips (ONLY visible when idle before study session starts)
                if (!isRunning) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                }

                // 3. Primary Action Button & Deep Focus Active Indicator
                if (isRunning && (isDeepFocusEnabled || isDeepFocusSessionActive)) {
                    item {
                        DeepFocusActiveBanner(
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Main Study Action Button (Sizable, prominent pill)
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .fillMaxWidth(if (isRunning) 0.72f else 0.85f)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = CircleShape,
                                    ambientColor = RegainLimePrimary.copy(alpha = 0.3f),
                                    spotColor = RegainLimePrimary.copy(alpha = 0.4f)
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
                                    text = if (isRunning) "PAUSE FOCUS" else "START STUDY",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = NearBlack,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // 4. Secondary Controls — Calm, progressive disclosure (Hidden when timer is running)
                if (!isRunning) {
                    item {
                        // Deep Focus Toggle Card (When idle)
                        DeepFocusToggleCard(
                            isEnabled = isDeepFocusEnabled,
                            onToggle = onToggleDeepFocus,
                            isSessionActive = false,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    item {
                        // When IDLE: Consolidated, quiet secondary options row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sound selector chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                                    .clickable { showSoundSelector = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = RegainLimeDeepText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = ambientSound,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = textPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            // Custom topic & timer chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                                    .clickable { showStudyTypeDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = RegainLimeDeepText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Custom Timer",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = textPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            // Distraction Logger
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(cardBg)
                                    .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
                                    .clickable { showDistractionModal = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsOff,
                                        contentDescription = "Log Distraction",
                                        tint = if (distractionsCount > 0) Color(0xFFE53935) else textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    if (distractionsCount > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$distractionsCount",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = Color(0xFFE53935),
                                                fontWeight = FontWeight.Bold,
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
                buddyGrowthStage = userPreferences?.buddyGrowthStage ?: 1,
                buddyTotalFocusMinutes = userPreferences?.buddyTotalFocusMinutes ?: 0,
                currentStreak = userPreferences?.currentStreak ?: 0,
                onDismiss = { showBuddySheet = false }
            )
        }

        if (showPreStudyBlockSheet) {
            PreStudyAppBlockSheet(
                currentBlockedList = userPreferences?.blockedAppsList ?: "",
                onConfirmAndStart = { selectedPackages, dontShowAgain ->
                    onConfirmPreStudyBlock(selectedPackages, dontShowAgain)
                },
                onDismiss = { onDismissPreStudyBlock() }
            )
        }
    }
}
