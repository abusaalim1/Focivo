package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.FocusShieldService
import com.example.ui.components.AlarmHubSheet
import com.example.ui.components.AlarmRingingOverlay
import com.example.ui.components.BlockShieldOverlay
import com.example.ui.components.FloatingNavigation
import com.example.ui.components.FocusShieldHubSheet
import com.example.ui.components.AiStudyWarningDialog
import com.example.ui.components.AiStudyBlockedDialog
import com.example.ui.components.AiStudyResolvedDialog
import com.example.ui.screens.AutoStudyScheduleScreen
import com.example.ui.components.NavTab
import com.example.ui.components.NotificationAlarmPermissionSheet
import com.example.ui.components.SupportLockZenSheet
import com.example.ui.components.SupportLockZenMilestoneDialog
import com.example.ui.components.TaskBottomSheet
import com.example.util.PermissionUtils
import kotlinx.coroutines.delay
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PlannerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.QuestionnaireScreen
import com.example.ui.screens.SessionCompleteScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.viewmodel.FocuslyViewModel

class MainActivity : ComponentActivity() {
    private var activeViewModel: FocuslyViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: FocuslyViewModel = viewModel()
            activeViewModel = viewModel
            handleShieldIntent(intent, viewModel)
            handleAuthIntent(intent, viewModel)
            FocuslyApp(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        activeViewModel?.syncShieldStateWithService()
        com.example.service.ScheduledBlockScheduler.evaluateAndReschedule(this)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            val isRinging = activeViewModel?.isAlarmRinging?.value == true ||
                    com.example.service.AlarmNotificationHelper.isRinging()
            if (isRinging) {
                activeViewModel?.silenceAlarmRingtone()
                com.example.service.AlarmNotificationHelper.silenceAudio(this)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeViewModel?.let {
            handleShieldIntent(intent, it)
            handleAuthIntent(intent, it)
        }
    }

    private fun handleShieldIntent(intent: Intent?, viewModel: FocuslyViewModel) {
        if (intent?.action == FocusShieldService.ACTION_INTERCEPT_BLOCKED_APP) {
            val appName = intent.getStringExtra(FocusShieldService.EXTRA_BLOCKED_NAME)
                ?: intent.getStringExtra(FocusShieldService.EXTRA_BLOCKED_PACKAGE)
                ?: "Distracting App"
            viewModel.triggerShieldIntercept(appName)
        }
    }

    private fun handleAuthIntent(intent: Intent?, viewModel: FocuslyViewModel) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase()
        val host = data.host?.lowercase()
        if ((scheme == "regain" || scheme == "studytracker") && host == "auth-callback") {
            viewModel.handleAuthDeeplink(data)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocuslyApp(viewModel: FocuslyViewModel) {
    val userPreferences by viewModel.userPreferences.collectAsState()
    val tasks by viewModel.allTasks.collectAsState()
    val topPriorities by viewModel.topPriorities.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val reflections by viewModel.allReflections.collectAsState()

    // Timer states
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val targetSeconds by viewModel.targetSeconds.collectAsState()
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val currentTaskTitle by viewModel.currentTaskTitle.collectAsState()
    val distractionsCount by viewModel.distractionsCount.collectAsState()
    val ambientSound by viewModel.ambientSound.collectAsState()
    val showCompletionScreen by viewModel.showCompletionScreen.collectAsState()
    val completedSessionSummary by viewModel.completedSessionSummary.collectAsState()

    // Alarm management states
    val alarms by viewModel.alarms.collectAsState()
    val isAlarmRinging by viewModel.isAlarmRinging.collectAsState()
    val ringingAlarm by viewModel.ringingAlarm.collectAsState()
    val currentlyPreviewingRingtone by viewModel.currentlyPreviewingRingtone.collectAsState()

    // Focus Shield / App Blocker states
    val isShieldHubOpen by viewModel.isShieldHubOpen.collectAsState()
    val isShieldOverlayVisible by viewModel.isShieldOverlayVisible.collectAsState()
    val shieldInterceptedAppName by viewModel.shieldInterceptedAppName.collectAsState()
    val isStandaloneShieldActive by viewModel.isStandaloneShieldActive.collectAsState()
    val standaloneShieldRemainingSeconds by viewModel.standaloneShieldRemainingSeconds.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val leaderboardUsers by viewModel.leaderboardUsers.collectAsState()

    // Scheduled Blocks & AI Study Guard states
    val scheduledBlocks by viewModel.scheduledBlocks.collectAsState()
    val shouldNavigateToStudyTab by viewModel.shouldNavigateToStudyTab.collectAsState()
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }

    LaunchedEffect(shouldNavigateToStudyTab) {
        if (shouldNavigateToStudyTab) {
            selectedTab = NavTab.FOCUS
            viewModel.consumeStudyTabNavigation()
        }
    }
    val scheduleStatusSummary by viewModel.scheduleStatusSummary.collectAsState()
    val punishmentLogs by viewModel.punishmentLogs.collectAsState()
    val activeAiWarning by viewModel.activeAiWarning.collectAsState()
    val activeAiBlock by viewModel.activeAiBlock.collectAsState()
    val activeAiResolved by viewModel.activeAiResolved.collectAsState()
    val isSupportLockZenSheetOpen by viewModel.isSupportLockZenSheetOpen.collectAsState()
    val showMilestoneDonationPrompt by viewModel.showMilestoneDonationPrompt.collectAsState()

    // Local Auth & Questionnaire states
    val currentUser by viewModel.currentUser.collectAsState()
    val isAuthChecking by viewModel.isAuthChecking.collectAsState()
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val isProfileLoading by viewModel.isProfileLoading.collectAsState()
    val profileError by viewModel.profileError.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val showQuestionnaire by viewModel.showQuestionnaire.collectAsState()
    val verificationMessage by viewModel.verificationMessage.collectAsState()

    val context = LocalContext.current
    var hasCheckedNotificationPermission by rememberSaveable(currentUser?.id) { mutableStateOf(false) }
    var showNotificationPermissionPrompt by rememberSaveable(currentUser?.id) { mutableStateOf(false) }

    LaunchedEffect(currentUser, showQuestionnaire) {
        if (currentUser != null && !showQuestionnaire && !hasCheckedNotificationPermission) {
            hasCheckedNotificationPermission = true
            if (!PermissionUtils.hasNotificationPermission(context)) {
                delay(600)
                showNotificationPermissionPrompt = true
            }
        }
    }

    // Screen navigation states
    var isSplashFinished by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser, authError) {
        if (currentUser != null || !authError.isNullOrBlank()) {
            isSplashFinished = true
        }
    }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isAlarmHubOpen by remember { mutableStateOf(false) }
    var isAutoScheduleOpen by remember { mutableStateOf(false) }
    var showTaskBottomSheet by remember { mutableStateOf(false) }

    val taskSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val alarmHubSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shieldHubSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val supportLockZenSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Dark theme resolution based on user preferences
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (userPreferences?.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemDark
    }

    FocuslyTheme(darkTheme = isDarkTheme) {
        if (!isSplashFinished) {
            SplashScreen(
                onSplashFinished = { isSplashFinished = true }
            )
        } else if (currentUser == null) {
            // Firebase Authentication Screen with Google Sign-In (Private Background Backend)
            AuthScreen(
                isLoading = isAuthLoading,
                errorMessage = authError,
                verificationMessage = verificationMessage,
                onLogin = { email, pin -> viewModel.login(email, pin) },
                onRegister = { email, pin, name -> viewModel.register(email, pin, name) },
                onGoogleSignIn = { ctx -> viewModel.signInWithGoogle(ctx) },
                onGoogleDirectSignIn = { name, email -> viewModel.signInWithGoogleDirect(name, email) },
                onGuestSignIn = { viewModel.signInAsGuest() },
                onClearError = { viewModel.clearAuthError() }
            )
        } else if (showQuestionnaire) {
            // Student Onboarding Questionnaire (Age, Class, Stream, Daily Routine, Mobile Break, Distractions)
            QuestionnaireScreen(
                initialUserName = currentUser?.fullName?.ifBlank { currentUser?.email ?: "" } ?: "",
                onFinishQuestions = { name, age, studentClass, stream, studySchedule, mobileBreakTime, goal, focusStyle, targetHours, peakTime, distraction ->
                    viewModel.submitQuestionnaire(
                        name = name,
                        age = age,
                        studentClass = studentClass,
                        stream = stream,
                        studySchedule = studySchedule,
                        mobileBreakTime = mobileBreakTime,
                        goal = goal,
                        focusStyle = focusStyle,
                        targetHours = targetHours,
                        peakTime = peakTime,
                        distraction = distraction
                    )
                },
                onFinishIntakeSurvey = { name, classLevel, isBoard, goal, distraction, timeWindow, screenTimeMins, motivation ->
                    viewModel.submitIntakeSurvey(
                        name = name,
                        studentClassLevel = classLevel,
                        isBoardExamYear = isBoard,
                        primaryStudyGoal = goal,
                        biggestDistractionApp = distraction,
                        preferredStudyTimeWindow = timeWindow,
                        dailyScreenTimeGoalMinutes = screenTimeMins,
                        motivationStyle = motivation
                    )
                }
            )
        } else {
            // Main OS Shell
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!isSettingsOpen) {
                            FloatingNavigation(
                                selectedTab = selectedTab,
                                onTabSelected = { tab ->
                                    selectedTab = tab
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        AnimatedContent(
                            targetState = if (isSettingsOpen) "SETTINGS" else selectedTab.name,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(240)) togetherWith fadeOut(animationSpec = tween(180))
                            },
                            label = "tab_navigation"
                        ) { target ->
                            when (target) {
                                "SETTINGS" -> {
                                    SettingsScreen(
                                        userPreferences = userPreferences,
                                        onBack = { isSettingsOpen = false },
                                        onUpdateTheme = { viewModel.updateThemeMode(it) },
                                        onUpdateDailyGoal = { viewModel.updateDailyGoal(it) },
                                        onResetDemoData = { viewModel.resetDemoData() },
                                        onClearAllData = { viewModel.clearAllData() },
                                        onReplayOnboarding = {
                                            isSettingsOpen = false
                                            viewModel.resetOnboarding()
                                        },
                                        onOpenAlarmStudio = { isAlarmHubOpen = true },
                                        onOpenShieldHub = { viewModel.openShieldHub() },
                                        onLogout = {
                                            isSettingsOpen = false
                                            viewModel.logout()
                                        }
                                    )
                                }
                                NavTab.HOME.name -> {
                                    HomeScreen(
                                        topPriorities = topPriorities,
                                        userPreferences = userPreferences,
                                        sessions = sessions,
                                        onToggleTask = { viewModel.toggleTaskCompletion(it) },
                                        onStartFocus = { taskTitle, durationMins ->
                                            if (!taskTitle.isNullOrBlank()) {
                                                viewModel.setCurrentTaskTitle(taskTitle)
                                            }
                                            viewModel.setMode(
                                                if (durationMins == 50) "Deep Work" else if (durationMins == 15) "Sprint" else "Classic",
                                                durationMins
                                            )
                                            selectedTab = NavTab.FOCUS
                                            viewModel.startTimer()
                                        },
                                        onCustomizeFocus = {
                                            selectedTab = NavTab.FOCUS
                                        },
                                        onOpenPlanner = {
                                            selectedTab = NavTab.FOCUS
                                        },
                                        onOpenTaskCreate = {
                                            showTaskBottomSheet = true
                                        },
                                        scheduleStatusSummary = scheduleStatusSummary,
                                        scheduledBlocksCount = scheduledBlocks.size,
                                        onOpenAlarmStudio = { isAlarmHubOpen = true },
                                        onOpenShieldHub = { viewModel.openShieldHub() },
                                        onOpenAutoSchedule = { isAutoScheduleOpen = true }
                                    )
                                }
                                NavTab.FOCUS.name -> {
                                    FocusScreen(
                                        remainingSeconds = remainingSeconds,
                                        targetSeconds = targetSeconds,
                                        isRunning = isTimerRunning,
                                        currentMode = currentMode,
                                        currentTaskTitle = currentTaskTitle,
                                        distractionsCount = distractionsCount,
                                        ambientSound = ambientSound,
                                        userStreak = userPreferences?.currentStreak ?: 0,
                                        isShieldActive = userPreferences?.isAppBlockerEnabled == true || isTimerRunning || isStandaloneShieldActive,
                                        onStartTimer = { viewModel.startTimer() },
                                        onPauseTimer = { viewModel.pauseTimer() },
                                        onResumeTimer = { viewModel.resumeTimer() },
                                        onFinishEarly = { viewModel.finishSessionEarly() },
                                        onAddFiveMinutes = { viewModel.addFiveMinutes() },
                                        onSkipBreak = { viewModel.skipBreak() },
                                        onSelectMode = { mode, mins -> viewModel.setMode(mode, mins) },
                                        onSelectAmbientSound = { sound -> viewModel.setAmbientSound(sound) },
                                        onLogDistraction = { type -> viewModel.logDistraction(type) },
                                        onUpdateTaskTitle = { viewModel.setCurrentTaskTitle(it) },
                                        onOpenShieldHub = { viewModel.openShieldHub() }
                                    )
                                }
                                NavTab.LEADERBOARD.name -> {
                                    LeaderboardScreen(
                                        users = leaderboardUsers,
                                        currentUserName = userPreferences?.currentUserName ?: "You",
                                        currentUserPoints = userPreferences?.focusPoints ?: 0,
                                        currentUserStreak = userPreferences?.currentStreak ?: 1
                                    )
                                }
                                NavTab.INSIGHTS.name -> {
                                    InsightsScreen(
                                        sessions = sessions,
                                        reflections = reflections,
                                        userPreferences = userPreferences,
                                        onSaveReflection = { viewModel.saveWeeklyReflection(it) }
                                    )
                                }
                                NavTab.PROFILE.name -> {
                                    ProfileScreen(
                                        userPreferences = userPreferences,
                                        sessions = sessions,
                                        isProfileLoading = isProfileLoading,
                                        profileError = profileError,
                                        onRetryFetchProfile = { viewModel.fetchUserProfile() },
                                        onOpenSettings = { isSettingsOpen = true },
                                        onOpenAlarmStudio = { isAlarmHubOpen = true },
                                        onOpenShieldHub = { viewModel.openShieldHub() },
                                        onOpenSupportLockZen = { viewModel.openSupportLockZenSheet() },
                                        onLogout = { viewModel.logout() },
                                        onUpdateProfile = { name, avatarBytes ->
                                            viewModel.updateProfileNameAndAvatar(name, avatarBytes)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Global Task Creation Sheet
                if (showTaskBottomSheet) {
                    TaskBottomSheet(
                        sheetState = taskSheetState,
                        onDismiss = { showTaskBottomSheet = false },
                        onSaveTask = { title, category, duration, priority, schedule, notes, isTop ->
                            viewModel.createTask(title, category, duration, priority, schedule, notes, isTop)
                        }
                    )
                }

                // Global Alarm Studio Hub Sheet
                if (isAlarmHubOpen) {
                    AlarmHubSheet(
                        sheetState = alarmHubSheetState,
                        alarms = alarms,
                        currentlyPreviewingRingtone = currentlyPreviewingRingtone,
                        defaultRingtone = userPreferences?.alarmRingtone ?: "Zen Bell",
                        onDismiss = {
                            viewModel.stopPreview()
                            isAlarmHubOpen = false
                        },
                        onToggleAlarm = { viewModel.toggleAlarm(it) },
                        onDeleteAlarm = { viewModel.deleteAlarm(it) },
                        onAddAlarm = { hour, min, lbl, ringtone, vib ->
                            viewModel.addAlarm(hour, min, lbl, ringtone, vib)
                        },
                        onPreviewRingtone = { viewModel.previewRingtone(it) },
                        onSetDefaultRingtone = { viewModel.updateSessionAlarmRingtone(it) },
                        onTestAlarm = { viewModel.triggerTestAlarm() }
                    )
                }

                // Full-screen Alarm Ringing Overlay
                if (isAlarmRinging && ringingAlarm != null) {
                    AlarmRingingOverlay(
                        alarm = ringingAlarm!!,
                        onDismiss = { viewModel.dismissAlarm() },
                        onSnooze = { viewModel.snoozeAlarm(5) }
                    )
                }

                // Full-screen Completion Moment Screen
                if (showCompletionScreen && completedSessionSummary != null) {
                    SessionCompleteScreen(
                        session = completedSessionSummary!!,
                        onDismiss = { viewModel.dismissCompletionScreen() }
                    )
                }

                // Global Focus Shield (App Blocker) Hub Sheet
                if (isShieldHubOpen) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val blockedSet = remember(userPreferences?.blockedAppsList) {
                        userPreferences?.blockedAppsList
                            ?.split(",")
                            ?.map { it.trim() }
                            ?.filter { it.isNotEmpty() }
                            ?.toSet() ?: emptySet()
                    }
                    val isServiceRunning = FocusShieldService.isShieldRunning(context)
                    val isShieldTimerRunning = isTimerRunning || isStandaloneShieldActive || isServiceRunning
                    val effectiveRemaining = if (isTimerRunning) remainingSeconds else if (isStandaloneShieldActive) standaloneShieldRemainingSeconds else FocusShieldService.getRemainingSeconds(context)
                    val isMasterShieldEnabled = (userPreferences?.isAppBlockerEnabled == true) || isStandaloneShieldActive || isServiceRunning

                    FocusShieldHubSheet(
                        isShieldEnabled = isMasterShieldEnabled,
                        isShieldTimerRunning = isShieldTimerRunning,
                        remainingSeconds = effectiveRemaining,
                        blockedPackages = blockedSet,
                        blockedAttemptsCount = userPreferences?.shieldBlockedAttempts ?: 0,
                        installedApps = installedApps,
                        onToggleMasterShield = { viewModel.toggleMasterShield(it) },
                        onToggleAppBlocked = { pkg, blocked -> viewModel.toggleBlockedPackage(pkg, blocked) },
                        onBlockAllSocial = { viewModel.blockAllSocialApps() },
                        onUnblockAll = { viewModel.unblockAllApps() },
                        onStartStandaloneShield = { mins -> viewModel.startStandaloneShield(mins) },
                        onStopStandaloneShield = { viewModel.stopStandaloneShield() },
                        onTriggerTestIntercept = { appName -> viewModel.triggerShieldIntercept(appName) },
                        scheduledBlocks = scheduledBlocks,
                        punishmentLogs = punishmentLogs,
                        onSaveSchedule = { viewModel.saveScheduledBlock(it) },
                        onToggleSchedule = { id, en -> viewModel.toggleScheduledBlock(id, en) },
                        onDeleteSchedule = { viewModel.deleteScheduledBlock(it) },
                        onDismiss = { viewModel.closeShieldHub() }
                    )
                }

                // AI Study Guard In-App Discipline Popups (Stage 1 Warning, Stage 2 Block, Stage 3 Resolved)
                activeAiWarning?.let { warning ->
                    AiStudyWarningDialog(
                        warning = warning,
                        onReturnToStudy = { viewModel.resolveAiWarning() },
                        onDismiss = { viewModel.resolveAiWarning() }
                    )
                }

                activeAiBlock?.let { block ->
                    AiStudyBlockedDialog(
                        block = block,
                        onDismiss = { viewModel.dismissAiBlock() }
                    )
                }

                activeAiResolved?.let { resolved ->
                    AiStudyResolvedDialog(
                        resolved = resolved,
                        onDismiss = { viewModel.dismissAiResolved() }
                    )
                }

                // Full-screen App Blocker Intercept Overlay
                if (isShieldOverlayVisible) {
                    val remainingTime = if (isTimerRunning) remainingSeconds else if (isStandaloneShieldActive) standaloneShieldRemainingSeconds else 0
                    BlockShieldOverlay(
                        blockedAppName = shieldInterceptedAppName,
                        remainingSeconds = remainingTime,
                        isPunishment = (remainingTime > 3600),
                        onReturnToFocus = {
                            viewModel.dismissShieldOverlay()
                            selectedTab = NavTab.FOCUS
                        },
                        onEmergencyBypass = {
                            viewModel.dismissShieldOverlay()
                        }
                    )
                }
                // Full-screen Auto Study Schedule Screen
                if (isAutoScheduleOpen) {
                    AutoStudyScheduleScreen(
                        scheduledBlocks = scheduledBlocks,
                        installedApps = installedApps,
                        statusSummary = scheduleStatusSummary,
                        onSaveSchedule = { viewModel.saveScheduledBlock(it) },
                        onToggleSchedule = { id, en -> viewModel.toggleScheduledBlock(id, en) },
                        onDeleteSchedule = { viewModel.deleteScheduledBlock(it) },
                        onBack = { isAutoScheduleOpen = false }
                    )
                }

                // Global Support LockZen Sheet
                if (isSupportLockZenSheetOpen) {
                    SupportLockZenSheet(
                        sheetState = supportLockZenSheetState,
                        onDismiss = { viewModel.closeSupportLockZenSheet() }
                    )
                }

                // Milestone Soft Prompt Dialog
                if (showMilestoneDonationPrompt) {
                    SupportLockZenMilestoneDialog(
                        onSupportClick = {
                            viewModel.dismissMilestoneDonationPrompt()
                            viewModel.openSupportLockZenSheet()
                        },
                        onDismiss = { viewModel.dismissMilestoneDonationPrompt() }
                    )
                }

                // Post-Login / Post-Onboarding Alarm & Notification Permission Sheet
                if (showNotificationPermissionPrompt) {
                    NotificationAlarmPermissionSheet(
                        onDismiss = { showNotificationPermissionPrompt = false },
                        onPermissionGranted = { showNotificationPermissionPrompt = false }
                    )
                }
            }
        }
    }
}
