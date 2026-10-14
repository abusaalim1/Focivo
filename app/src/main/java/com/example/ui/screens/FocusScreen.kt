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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.DistractionDialog
import com.example.ui.components.MascotPose
import com.example.ui.components.PreStudyAppBlockSheet
import com.example.ui.components.RegainMascotView
import com.example.ui.components.RegainStudyDial
import com.example.ui.components.resolveThemeBackdropRes
import com.example.R
import com.example.ui.theme.LocalAppAccent
import com.example.ui.theme.themedCardBackground
import com.example.ui.theme.backdropContentColor
import com.example.ui.theme.backdropTitleShadow
import com.example.ui.theme.BottomNavClearance
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.TimerThemeManager
import com.example.ui.components.DeepFocusToggleCard
import com.example.ui.components.DeepFocusActiveBanner
import com.example.ui.components.DeepFocusSettingsSheet

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
    onOpenAiGuardHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val selectedTheme by TimerThemeManager.selectedTheme.collectAsState()
    // v1.4.0-round2: interactive accents follow the full-screen theme.
    val appAccent = LocalAppAccent.current
    val cardBg = themedCardBackground() // round-3: translucent so the theme backdrop breathes through
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight
    // v1.4.2: explicit text for dark card surfaces (scheme colors render dark on the owner's device)
    val tPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val tSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight

    var showDistractionModal by remember { mutableStateOf(false) }
    var showSoundSelector by remember { mutableStateOf(false) }
    var showStudyTypeDialog by remember { mutableStateOf(false) }
    // v1.3.1: Deep Focus Settings sheet (Regain parity).
    var showDeepFocusSettings by remember { mutableStateOf(false) }
    var currentTag by remember { mutableStateOf(currentTaskTitle.ifBlank { "Deep Study" }) }
    // v1.3.1: Aurora is the flagship timer — default to it.
    var timerStyle by remember { mutableStateOf("aurora") }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        com.example.util.InstalledAppsManager.preloadApps(context)
    }

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
        // Round-3: the full-bleed theme backdrop is drawn once at the
        // MainActivity shell root; nothing per-screen here.

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
                    // Current streak pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x28FFFFFF) else Color(0x10000000))
                            .border(1.dp, if (isDark) Color(0x358CE000) else Color(0x208CE000), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("focus_streak_pill")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${userStreak}d",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = selectedTheme.onBackdropContent,
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
                // v1.4.2: the idle "REGAIN" brand header is removed; the title
                // only shows while a session runs, in the theme's backdrop color.
                Text(
                    text = if (isRunning) "FOCUSING · ${currentTag.uppercase()}" else "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = selectedTheme.onBackdropContent,
                        shadow = backdropTitleShadow(selectedTheme.onBackdropContent),
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (isRunning) 24.dp else BottomNavClearance),
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
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isDark) Color(0xFF3E2723) else Color(0xFFFFF3E0))
                                .border(1.dp, if (isDark) Color(0xFFFFB74D).copy(alpha = 0.4f) else Color(0xFFFFB74D), RoundedCornerShape(14.dp))
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
                                        .background(if (timerStyle == "aurora") appAccent else Color.Transparent)
                                        .clickable { timerStyle = "aurora" }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                        .testTag("timer_style_aurora_btn")
                                ) {
                                    Text(
                                        text = "Aurora Timer",
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
                    val isBreakMode = currentMode.contains("break", ignoreCase = true)
                    // v1.3.1: mascot pose follows the timer state (ready / focusing / break).
                    // v1.4.0: ready-state mascot follows the selected timer theme.
                    val timerMascotPose = when {
                        isBreakMode -> MascotPose.BREAK
                        isRunning -> MascotPose.STUDYING
                        else -> selectedTheme.mascotPose
                    }
                    if (timerStyle == "aurora") {
                        // v1.4.0: themed backdrop ambience behind the timer dial.
                        // Round-3: backdrop resolved at runtime so new themes work too.
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val backdropRes = remember(selectedTheme.id) {
                                resolveThemeBackdropRes(context, selectedTheme)
                            }
                            if (backdropRes != null) {
                                Image(
                                    painter = painterResource(id = backdropRes),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize(),
                                    alpha = 0.35f
                                )
                            }
                            AuroraTimer(
                                totalSeconds = targetSeconds,
                                remainingSeconds = remainingSeconds,
                                isRunning = isRunning,
                                isBreak = isBreakMode,
                                subjectTitle = currentTag,
                                mascotPose = timerMascotPose,
                                theme = selectedTheme,
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
                                onTagClick = {
                                    if (!isRunning) showStudyTypeDialog = true
                                },
                                modifier = Modifier.testTag("aurora_timer_component")
                            )
                        }
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
                            onMascotClick = {},
                            mascotPose = timerMascotPose
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
                                            if (isSelected) appAccent else cardBorder,
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
                                                color = if (isSelected) RegainLimeDeepText else tPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        )
                                        Text(
                                            text = modeName,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                color = tSecondary,
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
                        horizontalArrangement = if (isRunning) Arrangement.Center else Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // v1.1.3: AI Study Guard quick entry on the LEFT (idle only),
                        // with the guard-pose mascot as its icon.
                        if (!isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(4.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF1E2A1A) else Color.White)
                                    .border(1.5.dp, RegainLimePrimary, CircleShape)
                                    .clickable { onOpenAiGuardHub() }
                                    .testTag("ai_guard_left_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.mascot_guard),
                                    contentDescription = "Open AI Study Guard",
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        // Main Study Action Button (Sizable, prominent pill)
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .then(if (isRunning) Modifier.fillMaxWidth(0.72f) else Modifier.weight(1f))
                                .shadow(
                                    elevation = 6.dp,
                                    shape = CircleShape,
                                    ambientColor = appAccent.copy(alpha = 0.3f),
                                    spotColor = appAccent.copy(alpha = 0.4f)
                                )
                                .clip(CircleShape)
                                .background(appAccent)
                                .clickable {
                                    if (isRunning) {
                                        onPauseTimer()
                                    } else if (remainingSeconds < targetSeconds) {
                                        onResumeTimer()
                                    } else {
                                        onStartTimer()
                                    }
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

                // 3b. AI Study Guard entry — one button to the consolidated guard hub
                // (shown when idle; during a run the screen stays distraction-free)
                if (!isRunning) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isDark) Color(0x1A8CE000) else RegainLimeContainer.copy(alpha = 0.45f)
                                )
                                .border(1.dp, RegainLimePrimary.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                                .clickable { onOpenAiGuardHub() }
                                .padding(horizontal = 16.dp, vertical = 13.dp)
                                .testTag("ai_guard_entry_card")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(RegainLimePrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = NearBlack,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "AI Study Guard",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            // v1.4.3: in dark mode this card is only
                                            // 10%-alpha lime over the backdrop —
                                            // title/subtitle use the theme's
                                            // onBackdropContent for contrast.
                                            color = if (isDark) selectedTheme.onBackdropContent else tPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                    Text(
                                        text = "YouTube + AI chat monitor · warnings · 3h lock",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = if (isDark) selectedTheme.onBackdropContent.copy(alpha = 0.85f) else tSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Open AI Study Guard",
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(20.dp)
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
                        // v1.3.1: Deep focus settings entry (Regain parity — all free).
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(cardBg)
                                .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                                .clickable { showDeepFocusSettings = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("deep_focus_settings_entry")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = RegainLimeDeepText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Deep focus settings",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = tPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = "Strict mode · notifications · uninstall & home-screen protection",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = tSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Open deep focus settings",
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
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
                                            color = tPrimary,
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
                                            color = tPrimary,
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
                                        .border(1.dp, if (isChosen) appAccent else cardBorder, RoundedCornerShape(12.dp))
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
                                            color = if (isChosen) RegainLimeDeepText else tPrimary,
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
                        colors = ButtonDefaults.buttonColors(containerColor = appAccent, contentColor = NearBlack)
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

        if (showPreStudyBlockSheet) {
            PreStudyAppBlockSheet(
                currentBlockedList = userPreferences?.blockedAppsList ?: "",
                onConfirmAndStart = { selectedPackages, dontShowAgain ->
                    onConfirmPreStudyBlock(selectedPackages, dontShowAgain)
                },
                onDismiss = { onDismissPreStudyBlock() }
            )
        }

        // v1.3.1: Deep Focus Settings sheet (Regain parity).
        if (showDeepFocusSettings) {
            DeepFocusSettingsSheet(
                onDismiss = { showDeepFocusSettings = false }
            )
        }
    }
}
