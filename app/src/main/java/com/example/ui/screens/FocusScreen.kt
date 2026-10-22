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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.example.ui.components.BlockedAppsListSheet
import com.example.ui.components.BuddyMascot
import com.example.ui.components.CustomizeTimerSheet
import com.example.ui.components.DeepFocusActiveBanner
import com.example.ui.components.DeepFocusSettingsSheet
import com.example.ui.components.BlockHubEntry
import com.example.ui.components.BlockHubSheet
import com.example.ui.components.FocusShieldPermissionSheet
import com.example.ui.components.FeatureIcon
import com.example.ui.components.StrictAdminExplainerSheet
import com.example.ui.components.DistractionDialog
import com.example.ui.components.MascotPose
import com.example.ui.components.PreStudyAppBlockSheet
import com.example.util.MascotPose as BuddyMascotPose
import com.example.ui.components.StudyHubSheet
import com.example.ui.components.StudyTopStatsHeader
import com.example.ui.components.StrictGuardPromptSheet
import com.example.ui.components.TimerFaceHost
import com.example.ui.components.TimerFaceParams
import com.example.ui.components.resolveThemeBackdropRes
import com.example.ui.theme.BottomNavClearance
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SecondaryTextLight
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
    // v1.2.3 (Task 13.2) — pre-session Strict Mode + AI Guard prompt sheet.
    showStrictGuardPromptSheet: Boolean = false,
    promptStrictOn: Boolean = false,
    promptGuardOn: Boolean = false,
    onEnableStrictFromPrompt: () -> Unit = {},
    onEnableGuardFromPrompt: () -> Unit = {},
    onStartFromStrictGuardPrompt: () -> Unit = {},
    onDismissStrictGuardPrompt: () -> Unit = {},
    onStartTimer: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    // v1.1.4d (ROUND D, item D2): the running row's Reset — discard this
    // attempt and restart the same timer from full (strict-gated by the
    // caller, exactly like Stop).
    onRestartSession: () -> Unit = {},
    onFinishEarly: () -> Unit,
    onAddFiveMinutes: () -> Unit,
    onSkipBreak: () -> Unit,
    onSelectMode: (mode: String, durationMinutes: Int) -> Unit,
    onSelectAmbientSound: (sound: String) -> Unit,
    onLogDistraction: (type: String) -> Unit,
    onUpdateTaskTitle: (String) -> Unit,
    onOpenShieldHub: () -> Unit = {},
    onOpenAiGuardHub: () -> Unit = {},
    // v1.3.3 (ITEM 5): guard control cluster -> screen-time budget settings.
    onOpenScreenTimeBudgets: () -> Unit = {},
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
    // v1.3.3 (item 1): today's completed focus minutes for the Study-top
    // header (computed from the session log, same as the splash/Home).
    todayFocusMinutes: Int = 0,
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
    // v1.3.0 follow-up (item 2): when strict is enabled from the pre-session
    // prompt sheet and the device admin isn't active yet, offer the premium
    // admin explainer right after the prompt closes.
    var showAdminExplainer by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        com.example.util.InstalledAppsManager.preloadApps(context)
    }

    // v1.3.3 (item 1): Study-top header data — today's total device usage
    // from the shared DailyUsagePipeline; refreshes on first entry and
    // whenever the screen resumes (e.g. returning from system settings).
    var usageMillisToday by remember { mutableStateOf<Long?>(null) }
    var hasUsagePermission by remember {
        mutableStateOf(com.example.util.DailyUsagePipeline.hasUsagePermission(context))
    }
    var showUsagePermissionSheet by remember { mutableStateOf(false) }
    // v1.1.4b (item B, clause 9): dismiss flag for the in-context usage-access
    // ask. Signed-in users who never granted (or revoked) must be actively
    // asked too — not just guests. Dismissible, but reset on every resume so
    // the ask is re-presented on this surface until the grant exists.
    var usageAskDismissed by remember { mutableStateOf(false) }

    var lastSeenDayKey by remember {
        mutableStateOf(com.example.util.DailyUsagePipeline.dayKeyFor())
    }

    suspend fun refreshStudyHeaderUsage(force: Boolean) {
        val dayRolledOver = com.example.util.DailyUsagePipeline.refreshIfDayChanged()
        lastSeenDayKey = com.example.util.DailyUsagePipeline.dayKeyFor()
        val granted = withContext(Dispatchers.IO) {
            com.example.util.DailyUsagePipeline.hasUsagePermission(context)
        }
        hasUsagePermission = granted
        usageMillisToday = if (granted) {
            withContext(Dispatchers.IO) {
                com.example.util.DailyUsagePipeline.getSnapshot(
                    context,
                    forceRefresh = force || dayRolledOver
                )?.totalMillis
            }
        } else {
            null
        }
    }

    LaunchedEffect(Unit) {
        refreshStudyHeaderUsage(force = false)
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            val nowKey = com.example.util.DailyUsagePipeline.dayKeyFor()
            if (nowKey != lastSeenDayKey) {
                refreshStudyHeaderUsage(force = true)
            }
        }
    }

    val focusLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(focusLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    // Re-present the in-context ask until the grant exists.
                    usageAskDismissed = false
                    refreshStudyHeaderUsage(force = true)
                }
            }
        }
        focusLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { focusLifecycleOwner.lifecycle.removeObserver(observer) }
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
                        color = selectedTheme.backdropContent(isDark),
                        shadow = backdropTitleShadow(selectedTheme.backdropContent(isDark)),
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
                    .weight(1f)
                    .testTag("study_scroll_column"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BottomNavClearance),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // v1.1.4b (item B, clause 9): in-context usage-access ask for
                // signed-in users too. Shows whenever PACKAGE_USAGE_STATS is
                // missing (never granted, or revoked) until the grant exists.
                // Dismissible per resume; the tap target opens the premium
                // permission sheet (same flow as the usage-card tap).
                if (!hasUsagePermission && !usageAskDismissed) {
                    item {
                        UsageAccessAskCard(
                            onGrant = { showUsagePermissionSheet = true },
                            onDismiss = { usageAskDismissed = true },
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            accent = hubAccent,
                            textPrimary = if (isDark) Color(0xFFF4F4F5) else Color(0xFF141416),
                            textSecondary = if (isDark) Color(0xFF8E8E93) else SecondaryTextLight
                        )
                    }
                }

                // v1.3.3 (item 1): Study-top header — Usage Time (device total
                // from DailyUsagePipeline) + Focus Time (today's completed
                // focus minutes from the session log).
                item {
                    StudyTopStatsHeader(
                        usageMillis = usageMillisToday,
                        hasUsagePermission = hasUsagePermission,
                        focusMinutesToday = todayFocusMinutes,
                        onUsageCardClick = {
                            // v1.1.4d (ROUND D, item D3): the Usage Time
                            // box is a real door now. WITHOUT the grant it
                            // only ever opens the permission ask (the card
                            // itself shows "--", never a number). WITH the
                            // grant it opens the per-app usage view (the
                            // App Limits screen: every app sorted by time
                            // spent, top app highlighted) — same
                            // DailyUsagePipeline snapshot as this header.
                            if (hasUsagePermission) {
                                onOpenScreenTimeBudgets()
                            } else {
                                showUsagePermissionSheet = true
                            }
                        }
                    )
                }

                // v1.1.4d (ROUND D, item D2): the Block + START STUDY
                // cluster no longer sits here. Round A had moved it up
                // under this header; the owner ordered it back DOWN below
                // the timer ("uper wala niche lake de de") — it now
                // renders as the LAST LazyColumn item, state-aware:
                // idle -> Block + START STUDY, running -> Reset + Stop
                // only, paused -> Resume + Stop. The timer faces' own
                // play/reset buttons (the duplication) are gone too.

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

                // 3. Timer presentation — the selected face renders here.
                // v1.3.0: the Aurora Ring face was removed by owner order;
                // Minimal Digits is the default face.
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
                                    else selectedTheme.backdropContent(isDark)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // v1.1.4c (C3, owner order 2026-10-21): the v1.1
                        // swipe-up-on-timer gesture (detectVerticalDragGestures
                        // -> hub Themes) is REMOVED. On the owner's device,
                        // ordinary up/down scrolling of the Study page that
                        // started on the timer region kept opening Themes.
                        // Themes now open ONLY by explicit tap: the top-bar
                        // hub button (testTag study_hub_button) opens the
                        // Study Hub, and its Themes row (hub_themes_row)
                        // expands the Themes section. No drag/scroll path
                        // opens themes anywhere on this screen.
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
                        // app chrome (phase pill, controls
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

                // 4. Deep Focus active indicator (running only)
                if (isRunning && (isDeepFocusEnabled || isDeepFocusSessionActive)) {
                    item {
                        DeepFocusActiveBanner(
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                }

                // 5. v1.1.4d (ROUND D, item D2) — THE action cluster, back
                // BELOW the timer (owner order: "uper study guard and
                // start rakha hai use niche lake de de"). One cluster, one
                // home for every session action — the timer faces no
                // longer carry their own play/reset buttons, so nothing
                // is duplicated:
                //   idle    -> Block entry + START STUDY (as before)
                //   running -> Reset + Stop, ONLY ("bas 2 option")
                //   paused  -> Resume + Stop (a paused session must stay
                //              resumable; Stop still discards it)
                // During a pomodoro run the cluster hides entirely —
                // FocivoPomodoro carries its own in-component controls.
                if (pomodoroState == null) {
                    item {
                        var showBlockHub by remember { mutableStateOf(false) }
                        val inSession = isRunning || remainingSeconds < targetSeconds
                        val backdropInk = selectedTheme.backdropContent(isDark)
                        when {
                            isRunning -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .clip(CircleShape)
                                            .background(cardBg)
                                            .border(1.dp, cardBorder, CircleShape)
                                            .clickable { onRestartSession() }
                                            .testTag("session_reset_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Reset session",
                                                tint = backdropInk,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "RESET",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    color = backdropInk,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    letterSpacing = 1.sp
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .shadow(
                                                elevation = 6.dp,
                                                shape = CircleShape,
                                                ambientColor = hubAccent.copy(alpha = 0.3f),
                                                spotColor = hubAccent.copy(alpha = 0.4f)
                                            )
                                            .clip(CircleShape)
                                            .background(hubAccent)
                                            .clickable { onFinishEarly() }
                                            .testTag("session_stop_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Stop session",
                                                tint = NearBlack,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "STOP",
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
                            inSession -> {
                                // Paused mid-session: Resume (primary) + Stop.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .shadow(
                                                elevation = 6.dp,
                                                shape = CircleShape,
                                                ambientColor = hubAccent.copy(alpha = 0.3f),
                                                spotColor = hubAccent.copy(alpha = 0.4f)
                                            )
                                            .clip(CircleShape)
                                            .background(hubAccent)
                                            .clickable { onResumeTimer() }
                                            .testTag("play_pause_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Resume Study",
                                                tint = NearBlack,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "RESUME",
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
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .height(56.dp)
                                            .clip(CircleShape)
                                            .background(cardBg)
                                            .border(1.dp, cardBorder, CircleShape)
                                            .clickable { onFinishEarly() }
                                            .padding(horizontal = 22.dp)
                                            .testTag("session_stop_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Stop session",
                                                tint = backdropInk,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "STOP",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    color = backdropInk,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    letterSpacing = 1.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BlockHubEntry(
                                        // The label sits on the ambient
                                        // timer-theme backdrop — backdrop
                                        // content color, never the raw token.
                                        contentColor = backdropInk,
                                        modifier = Modifier.weight(0.44f),
                                        onOpen = { showBlockHub = true }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(0.56f)
                                            .height(58.dp)
                                            .shadow(
                                                elevation = 6.dp,
                                                shape = CircleShape,
                                                ambientColor = hubAccent.copy(alpha = 0.3f),
                                                spotColor = hubAccent.copy(alpha = 0.4f)
                                            )
                                            .clip(CircleShape)
                                            .background(hubAccent)
                                            .clickable { onStartTimer() }
                                            .testTag("play_pause_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Start Study",
                                                tint = NearBlack,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "START STUDY",
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
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Timer, music, themes and protection live in the hub",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = selectedTheme.backdropCaptionContent(isDark),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                        // v1.1.1: the one premium Block sheet (Strict, AI
                        // Guard, AI YouTube Filter, Screen-time limits).
                        if (showBlockHub) {
                            BlockHubSheet(
                                onOpenScreenTimeBudgets = onOpenScreenTimeBudgets,
                                onDismiss = { showBlockHub = false }
                            )
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

        // v1.5.0: the one-button hub. Everything scattered before now opens
        // from here; the hub dismisses itself before opening the next sheet.
        if (showHub) {
            StudyHubSheet(
                onDismiss = {
                    showHub = false
                    hubInitialSection = null
                },
                // v1.1.4c (C3): always null now — the swipe-up shortcut
                // that set "themes" here was removed; the hub's Themes row
                // is the tap-only path. Parameter kept for the sheet API.
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

        // v1.3.3 (item 1): premium usage-permission sheet opened from the
        // Usage Time card when PACKAGE_USAGE_STATS is missing.
        if (showUsagePermissionSheet) {
            FocusShieldPermissionSheet(
                onDismiss = {
                    showUsagePermissionSheet = false
                    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                        refreshStudyHeaderUsage(force = true)
                    }
                },
                onAllGranted = {
                    showUsagePermissionSheet = false
                    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                        refreshStudyHeaderUsage(force = true)
                    }
                }
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

        // v1.2.3 (Task 13.2) — pre-session Strict Mode + AI Guard prompt.
        // v1.3.0 follow-up (item 2): enabling strict here must also offer the
        // device-admin uninstall shield — previously this path skipped it,
        // which is exactly how the owner's device ended up unprotected.
        if (showStrictGuardPromptSheet) {
            StrictGuardPromptSheet(
                strictOn = promptStrictOn,
                guardOn = promptGuardOn,
                onEnableStrict = {
                    val wasOff = !promptStrictOn
                    onEnableStrictFromPrompt()
                    if (wasOff && !com.example.util.DeviceAdminGuard.isAdminActive(context)) {
                        showAdminExplainer = true
                    }
                },
                onEnableGuard = onEnableGuardFromPrompt,
                onStartSession = onStartFromStrictGuardPrompt,
                onDismiss = onDismissStrictGuardPrompt
            )
        }

        // The premium explainer renders only after the prompt sheet is gone.
        if (showAdminExplainer && !showStrictGuardPromptSheet) {
            StrictAdminExplainerSheet(
                onEnable = {
                    showAdminExplainer = false
                    com.example.util.StrictModeManager.setUninstallProtectionEnabled(context, true)
                    com.example.util.DeviceAdminGuard.launchActivation(context)
                },
                onOptOut = {
                    showAdminExplainer = false
                    com.example.util.StrictModeManager.setUninstallProtectionEnabled(context, false)
                },
                onDismiss = { showAdminExplainer = false }
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

/**
 * v1.1.4b (item B, clause 9) — slim in-context usage-access ask for the Study
 * screen. Rendered whenever PACKAGE_USAGE_STATS is missing (never granted,
 * or revoked after granting) so signed-in users are actively asked too —
 * not only guests on the onboarding path. Dismissible; the parent resets
 * the dismissal on every resume so the ask returns until the grant exists.
 * Tapping Grant opens the premium permission sheet (same flow as tapping
 * the Usage Time card). English only, no emoji, design-system tokens.
 */
@Composable
private fun UsageAccessAskCard(
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    cardBg: Color,
    cardBorder: Color,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeatureIcon(
                drawableRes = R.drawable.ic3d_chart,
                contentDescription = null,
                size = 46.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "See your screen time",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Grant Usage Access to show today's screen time here.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.5.sp,
                        color = textSecondary
                    )
                )
                Spacer(modifier = Modifier.height(9.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent)
                        .clickable { onGrant() }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Grant Access",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0D110E)
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
