package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.BlockedAppsListSheet
import com.example.ui.components.BuddyMascot
import com.example.ui.components.CustomizeTimerSheet
import com.example.ui.components.DeepFocusActiveBanner
import com.example.ui.components.DeepFocusSettingsSheet
import com.example.ui.components.DistractionDialog
import com.example.ui.components.MascotPose
import com.example.ui.components.PreStudyAppBlockSheet
import com.example.util.MascotPose as BuddyMascotPose
import com.example.ui.components.StudyHubSheet
import com.example.ui.components.TimerFaceHost
import com.example.ui.components.TimerFaceParams
import com.example.ui.components.resolveThemeBackdropRes
import com.example.ui.theme.BottomNavClearance
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.backdropTitleShadow
import com.example.ui.theme.isAppInDarkTheme
import com.example.ui.theme.themedCardBackground
import com.example.util.PomodoroConfig
import com.example.util.PomodoroPhase
import com.example.util.PomodoroState
import com.example.util.PomodoroStyleManager
import com.example.util.TimerFaceManager
import com.example.util.TimerThemeManager
import com.example.util.nextPomodoroPhase
import com.example.util.phaseDisplayLabel
import com.example.util.phaseMinutes
import com.example.ui.timer.FocivoPomodoro
import com.example.ui.timer.PomodoroUiState
import com.example.ui.timer.PomoPhase as UiPomoPhase

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
    // v1.1: Mascot Buddy — opens the dedicated Mascot screen.
    onOpenMascot: () -> Unit = {},
    // v1.1: Pomodoro — live phase state (null when no pomodoro is active).
    pomodoroState: PomodoroState? = null,
    // v1.1: starts a pomodoro run through the normal session-start gate.
    onStartPomodoro: (PomodoroConfig) -> Unit = {},
    // v1.2.3: pomodoro-run controls for the FocivoPomodoro slot —
    // stop clears the run (discards like finishing early); skip on a focus
    // phase discards the phase without recording, then advances.
    onStopPomodoro: () -> Unit = {},
    onSkipPomodoroFocus: () -> Unit = {},
    // v1.1: blocked-apps list (dedicated destination from the hub).
    onRemoveBlockedApp: (String) -> Unit = {},
    onSaveBlockedApps: (Set<String>) -> Unit = {},
    // v1.5.0: test hook — screenshot tests open the hub directly.
    initialShowHub: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val selectedTheme by TimerThemeManager.selectedTheme.collectAsState()
    // v1.5.0: the hub redesign uses one accent lime (owner clean-redesign
    // #B6F13A, aligned with Focus Shield in v1.2.3 design-unification).
    val hubAccent = Color(0xFFB6F13A)
    val cardBg = themedCardBackground()
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight

    var showHub by remember { mutableStateOf(initialShowHub) }
    var hubInitialSection by remember { mutableStateOf<String?>(null) }
    var showBlockedAppsList by remember { mutableStateOf(false) }
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
            // Top Bar: session title + the
            // ONE menu button that opens the study hub.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // v1.2.3: no top-bar readout in idle state; the leading spacer
                // keeps the title centered (hub holds the controls).
                Spacer(modifier = Modifier.width(44.dp))

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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomNavClearance),
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
                // v1.1: TimerFaceHost renders the selected timer face; the
                // dressed buddy (Mascot system) shows on the default theme,
                // themed mascots keep their pose on other themes.
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    val isBreakMode = currentMode.contains("break", ignoreCase = true)
                    val timerMascotPose = when {
                        isBreakMode -> MascotPose.BREAK
                        isRunning -> MascotPose.STUDYING
                        else -> selectedTheme.mascotPose
                    }
                    val selectedFace by TimerFaceManager.selectedFace.collectAsState()
                    val density = LocalDensity.current
                    val swipeThresholdPx = remember(density) {
                        with(density) { 100.dp.toPx() }
                    }

                    // v1.1: pomodoro phase pill (focus / short / long break).
                    if (pomodoroState != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isBreakMode) Color(0xFF69F0AE).copy(alpha = 0.16f)
                                    else hubAccent.copy(alpha = 0.14f)
                                )
                                .border(
                                    1.dp,
                                    if (isBreakMode) Color(0xFF69F0AE).copy(alpha = 0.5f)
                                    else hubAccent.copy(alpha = 0.45f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("pomodoro_phase_pill")
                        ) {
                            Text(
                                text = pomodoroState.phaseDisplayLabel(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    fontSize = 11.sp,
                                    color = if (isBreakMode) Color(0xFF69F0AE)
                                    else selectedTheme.onBackdropContent
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // v1.1: swipe up on the timer region opens the
                            // hub's Themes section. Tap vs drag are distinct:
                            // detectVerticalDragGestures only fires after
                            // touch slop, so taps (mascot, digits, buttons)
                            // and LazyColumn scrolling are unaffected.
                            .pointerInput(Unit) {
                                var totalDrag = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onVerticalDrag = { _, dragAmount ->
                                        totalDrag += dragAmount
                                    },
                                    onDragEnd = {
                                        if (totalDrag < -swipeThresholdPx) {
                                            hubInitialSection = "themes"
                                            showHub = true
                                        }
                                        totalDrag = 0f
                                    },
                                    onDragCancel = { totalDrag = 0f }
                                )
                            },
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
                        // v1.2.3: an active pomodoro run renders the FocivoPomodoro
                        // UI, constrained to the timer slot — the surrounding
                        // app chrome (swipe-up gesture, phase pill, controls
                        // below) is untouched. Classic flow
                        // (pomodoroState == null) is completely unchanged.
                        val pomoStyle by PomodoroStyleManager.selectedStyle.collectAsState()
                        val pomoUiState = remember(pomodoroState, remainingSeconds, targetSeconds, isRunning) {
                            pomodoroState?.let {
                                buildPomodoroUiState(it, remainingSeconds, targetSeconds, isRunning)
                            }
                        }
                        if (pomoUiState != null && pomodoroState != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(660.dp)
                                    .clip(RoundedCornerShape(28.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                FocivoPomodoro(
                                    state = pomoUiState,
                                    style = pomoStyle,
                                    onToggle = {
                                        if (isRunning) onPauseTimer() else onResumeTimer()
                                    },
                                    onSkip = {
                                        if (pomodoroState.phase == PomodoroPhase.FOCUS) {
                                            onSkipPomodoroFocus()
                                        } else {
                                            onSkipBreak()
                                        }
                                    },
                                    onReset = onStopPomodoro,
                                    modifier = Modifier.testTag("pomodoro_timer_component")
                                )
                            }
                        } else {
                        TimerFaceHost(
                            faceId = selectedFace.id,
                            params = TimerFaceParams(
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
                                // v1.1: tap-time-to-modify — digits open the
                                // duration editor when idle/paused; running
                                // sessions keep the existing no-edit rule.
                                onTimeClick = if (!isRunning) {
                                    { showCustomizeTimer = true }
                                } else null,
                                // v1.2: the equipped outfit set IS the mascot everywhere —
                                // the dressed buddy renders in the timer ring on every
                                // theme (no standalone illustrations, no book in hands);
                                // tap opens the Mascot screen.
                                mascotContent = {
                                    BuddyMascot(
                                        modifier = Modifier.width(108.dp),
                                        pose = BuddyMascotPose.STUDY
                                    )
                                },
                                onMascotClick = onOpenMascot
                            ),
                            modifier = Modifier.testTag("aurora_timer_component")
                        )
                        } // else: classic timer face
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
                onDismiss = {
                    showHub = false
                    hubInitialSection = null
                },
                // v1.1: swipe-up on the timer opens the hub straight into Themes.
                initialExpandedSection = hubInitialSection,
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
                // v1.1: the hub's Blocked Apps row opens the DEDICATED
                // blocked-apps list (not the Shield page).
                onOpenBlockedApps = {
                    showHub = false
                    showBlockedAppsList = true
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
        // v1.1: + Timer Style picker, + Pomodoro showcase (onStartPomodoro).
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
                },
                onStartPomodoro = onStartPomodoro
            )
        }

        // v1.1: dedicated blocked-apps list (hub row destination).
        if (showBlockedAppsList) {
            BlockedAppsListSheet(
                onDismiss = { showBlockedAppsList = false },
                blockedPackages = userPreferences?.blockedAppsList
                    ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
                onRemoveApp = onRemoveBlockedApp,
                onSaveBlockedApps = onSaveBlockedApps
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

/**
 * v1.2.3 — builds the Pomodoro UI state from the live engine state.
 * remaining/total are the active phase timer's real values (the same ones
 * the timer slot already passes to TimerFaceHost). sessionIndex follows the
 * "current session" convention: during a break it points at the session the
 * break belongs to (so the cycle track shows it complete).
 */
private fun buildPomodoroUiState(
    pomo: PomodoroState,
    remainingSeconds: Int,
    targetSeconds: Int,
    isRunning: Boolean
): PomodoroUiState {
    val phase = when (pomo.phase) {
        PomodoroPhase.FOCUS -> UiPomoPhase.FOCUS
        PomodoroPhase.SHORT_BREAK -> UiPomoPhase.SHORT
        PomodoroPhase.LONG_BREAK -> UiPomoPhase.LONG
    }
    val cycles = pomo.config.cycles.coerceAtLeast(1)
    val sessionIndex = when (pomo.phase) {
        PomodoroPhase.FOCUS -> pomo.focusCompleted
        else -> pomo.focusCompleted - 1
    }.coerceIn(0, cycles - 1)
    // Pure lookahead for the "up next" row; null-safe fallback.
    val next = nextPomodoroPhase(pomo)
    val nextPhase = when (next?.phase) {
        PomodoroPhase.FOCUS -> UiPomoPhase.FOCUS
        PomodoroPhase.SHORT_BREAK -> UiPomoPhase.SHORT
        PomodoroPhase.LONG_BREAK -> UiPomoPhase.LONG
        null -> UiPomoPhase.SHORT
    }
    return PomodoroUiState(
        phase = phase,
        remainingSec = remainingSeconds,
        totalSec = targetSeconds,
        sessionIndex = sessionIndex,
        sessions = cycles,
        running = isRunning,
        nextPhase = nextPhase,
        nextMinutes = next?.phaseMinutes() ?: pomo.config.shortBreakMinutes
    )
}
