package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.AuroraTimer
import com.example.ui.components.CustomizeTimerSheet
import com.example.ui.components.DeepFocusActiveBanner
import com.example.ui.components.DeepFocusSettingsSheet
import com.example.ui.components.DistractionDialog
import com.example.ui.components.MascotPose
import com.example.ui.components.PreStudyAppBlockSheet
import com.example.ui.components.StudyHubSheet
import com.example.ui.components.resolveThemeBackdropRes
import com.example.ui.theme.BottomNavClearance
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.backdropTitleShadow
import com.example.ui.theme.isAppInDarkTheme
import com.example.ui.theme.themedCardBackground
import com.example.util.TimerThemeManager

/**
 * v1.5.0 — ONE-BUTTON HUB redesign.
 *
 * The screen keeps only three things: the timer presentation, Start/Pause,
 * and ONE menu button (top-right) that opens [StudyHubSheet]. Every control
 * that used to live in scattered rows/cards — Focus Music, Themes, Blocked
 * Apps, Ultra Focus Settings, AI Study Guard, Customize Timer, Deep Focus
 * mode, the distraction logger — now lives in that sheet.
 *
 * Presentation only: the timer engine, guard, notifications and the
 * session-complete flow are untouched.
 */
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
    // v1.5.0: test hook — screenshot tests open the hub directly.
    initialShowHub: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val selectedTheme by TimerThemeManager.selectedTheme.collectAsState()
    // v1.5.0: the hub redesign uses one accent lime.
    val hubAccent = Color(0xFF8FD400)
    val cardBg = themedCardBackground()
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight

    var showHub by remember { mutableStateOf(initialShowHub) }
    var showDistractionModal by remember { mutableStateOf(false) }
    var showDeepFocusSettings by remember { mutableStateOf(false) }
    var showCustomizeTimer by remember { mutableStateOf(false) }
    var currentTag by remember { mutableStateOf(currentTaskTitle.ifBlank { "Deep Study" }) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar: streak readout (idle) / session title (running) + the
            // ONE menu button that opens the study hub.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRunning) {
                    // Current streak pill (display only — hub holds the controls)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x28FFFFFF) else Color(0x10000000))
                            .border(1.dp, if (isDark) Color(0x358FD400) else Color(0x208FD400), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("focus_streak_pill")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // v1.5.0: iOS-style 3D flame asset for the streak.
                            Image(
                                painter = painterResource(id = R.drawable.emoji3d_flame),
                                contentDescription = "Streak",
                                modifier = Modifier.size(15.dp)
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
                    Spacer(modifier = Modifier.width(44.dp))
                }

                // Active topic title (only while a session runs)
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

                // THE one menu button -> study hub.
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(cardBg)
                        .border(1.dp, cardBorder, CircleShape)
                        .clickable { showHub = true }
                        .testTag("study_hub_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic3d_gear),
                        contentDescription = "Open study hub",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Timer presentation + Start/Pause. Generous whitespace, no
            // scattered controls — everything else lives in the hub sheet.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (isRunning) 24.dp else BottomNavClearance),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
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

                // 1. Timer presentation — Aurora is the single flagship timer
                // (the old Aurora/Classic toggle was a scattered control).
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    val isBreakMode = currentMode.contains("break", ignoreCase = true)
                    val timerMascotPose = when {
                        isBreakMode -> MascotPose.BREAK
                        isRunning -> MascotPose.STUDYING
                        else -> selectedTheme.mascotPose
                    }
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
                            // Tapping the tag opens Customize Timer directly.
                            onTagClick = {
                                if (!isRunning) showCustomizeTimer = true
                            },
                            modifier = Modifier.testTag("aurora_timer_component")
                        )
                    }
                }

                // 2. Deep Focus active indicator (running only)
                if (isRunning && (isDeepFocusEnabled || isDeepFocusSessionActive)) {
                    item {
                        DeepFocusActiveBanner(
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                }

                // 3. The primary action — Start / Pause.
                item {
                    Box(
                        modifier = Modifier
                            .height(58.dp)
                            .fillMaxWidth(0.78f)
                            .shadow(
                                elevation = 6.dp,
                                shape = CircleShape,
                                ambientColor = hubAccent.copy(alpha = 0.3f),
                                spotColor = hubAccent.copy(alpha = 0.4f)
                            )
                            .clip(CircleShape)
                            .background(hubAccent)
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
                    // Breathing room under the action; the hub hint sits quiet.
                    if (!isRunning) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Timer, music, themes and protection live in the hub",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = selectedTheme.onBackdropContent.copy(alpha = 0.55f),
                                fontSize = 11.sp
                            )
                        )
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

        // v1.5.0: the one-button hub. Everything scattered before now opens
        // from here; the hub dismisses itself before opening the next sheet.
        if (showHub) {
            StudyHubSheet(
                onDismiss = { showHub = false },
                isRunning = isRunning,
                ambientSound = ambientSound,
                onSelectAmbientSound = onSelectAmbientSound,
                isDeepFocusEnabled = isDeepFocusEnabled,
                onToggleDeepFocus = onToggleDeepFocus,
                distractionsCount = distractionsCount,
                onLogDistraction = onLogDistraction,
                blockedAppsCount = userPreferences?.blockedAppsList
                    ?.split(",")?.count { it.isNotBlank() } ?: 0,
                isShieldActive = isShieldActive,
                onOpenBlockedApps = {
                    showHub = false
                    onOpenShieldHub()
                },
                onOpenUltraFocus = {
                    showHub = false
                    showDeepFocusSettings = true
                },
                onOpenAiGuard = {
                    showHub = false
                    onOpenAiGuardHub()
                },
                onOpenCustomizeTimer = {
                    showHub = false
                    showCustomizeTimer = true
                }
            )
        }

        if (showDeepFocusSettings) {
            DeepFocusSettingsSheet(
                onDismiss = { showDeepFocusSettings = false }
            )
        }

        // v1.5.0: replaces the old "Study Focus & Custom Timer" dialog.
        if (showCustomizeTimer) {
            CustomizeTimerSheet(
                onDismiss = { showCustomizeTimer = false },
                currentFocusMinutes = (targetSeconds / 60).coerceAtLeast(1),
                currentSubject = currentTag,
                ambientSound = ambientSound,
                onSelectAmbientSound = onSelectAmbientSound,
                onSelectMode = onSelectMode,
                onUpdateSubject = { subject ->
                    currentTag = subject
                    onUpdateTaskTitle(subject)
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
    }
}
