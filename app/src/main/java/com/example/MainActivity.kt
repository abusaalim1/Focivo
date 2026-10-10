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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.FocusShieldService
import com.example.ui.components.AlarmHubSheet
import com.example.ui.components.AlarmRingingOverlay
import com.example.ui.components.BlockShieldOverlay
import com.example.ui.components.FloatingNavigation
import com.example.ui.components.FocusShieldHubSheet
import com.example.ui.components.AiStudyWarningDialog
import com.example.ui.components.AiStudyBlockedDialog
import com.example.ui.components.AiStudyResolvedDialog
import com.example.ui.components.AppUpdateDialog
import com.example.ui.screens.AutoStudyScheduleScreen
import com.example.ui.screens.FocusSessionLogScreen
import com.example.ui.components.NavTab
import com.example.ui.components.NotificationAlarmPermissionSheet
import com.example.ui.components.SignOutConfirmationDialog
import com.example.ui.components.SupportLockZenSheet
import com.example.ui.components.SupportLockZenMilestoneDialog
import com.example.ui.components.SessionDonationPromptSheet
import com.example.ui.components.SundayRecapGlassDialog
import com.example.ui.components.TaskBottomSheet
import com.example.util.AiStudyGuardManager
import com.example.util.PermissionUtils
import kotlinx.coroutines.delay
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.OnboardingSurveyScreen
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
        // ViewModel is created eagerly here (not inside composition) so that
        // intent handling runs exactly once per intent. Calling the handlers
        // inside setContent{} re-fired them on every recomposition
        // (e.g. every timer tick), re-launching network calls and widget IPC.
        val viewModel: FocuslyViewModel =
            androidx.lifecycle.ViewModelProvider(this)[FocuslyViewModel::class.java]
        activeViewModel = viewModel
        handleActivityIntent(intent, viewModel)
        setContent {
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
        activeViewModel?.let { handleActivityIntent(intent, it) }
    }

    private fun handleActivityIntent(intent: Intent?, viewModel: FocuslyViewModel) {
        handleShieldIntent(intent, viewModel)
        handleAuthIntent(intent, viewModel)
        handleSundayRecapIntent(intent, viewModel)
        handleWidgetIntent(intent, viewModel)
    }

    private fun handleWidgetIntent(intent: Intent?, viewModel: FocuslyViewModel) {
        if (intent == null) return
        val navTab = intent.getStringExtra("EXTRA_NAV_TAB")
        val quickMins = intent.getIntExtra("EXTRA_QUICK_START_MINS", 0)
        val taskTitle = intent.getStringExtra("EXTRA_TASK_TITLE")
        val startTimer = intent.getBooleanExtra("EXTRA_START_TIMER_IMMEDIATELY", false)
        val openShieldHub = intent.getBooleanExtra("EXTRA_OPEN_SHIELD_HUB", false)
        val openZenBreak = intent.getBooleanExtra("EXTRA_OPEN_ZEN_BREAK", false)

        if (navTab != null || quickMins > 0 || startTimer || openShieldHub || openZenBreak || taskTitle != null) {
            viewModel.handleWidgetLaunch(
                tab = navTab,
                quickMins = if (quickMins > 0) quickMins else null,
                taskTitle = taskTitle,
                startTimer = startTimer,
                openShieldHub = openShieldHub,
                openZenBreak = openZenBreak
            )
        }
    }

    private fun handleSundayRecapIntent(intent: Intent?, viewModel: FocuslyViewModel) {
        if (intent?.getBooleanExtra("OPEN_SUNDAY_RECAP", false) == true) {
            viewModel.openLatestSundayRecap()
        }
    }

    private fun handleShieldIntent(intent: Intent?, viewModel: FocuslyViewModel) {
        if (intent?.action == FocusShieldService.ACTION_INTERCEPT_BLOCKED_APP) {
            val appName = intent.getStringExtra(FocusShieldService.EXTRA_BLOCKED_NAME)
                ?: intent.getStringExtra(FocusShieldService.EXTRA_BLOCKED_PACKAGE)
                ?: "Distracting App"
            val reason = intent.getStringExtra(FocusShieldService.EXTRA_PUNISHMENT_REASON)
            val isGemini = intent.getBooleanExtra("EXTRA_IS_GEMINI_INTERCEPT", false) || (reason?.contains("Gemini", ignoreCase = true) == true)
            viewModel.triggerShieldIntercept(appName, reason, isGemini)
        }
    }

    private fun handleAuthIntent(intent: Intent?, viewModel: FocuslyViewModel) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase()
        val host = data.host?.lowercase()
        if ((scheme == "focivo" || scheme == "regain" || scheme == "studytracker") && host == "auth-callback") {
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
    val sessionStartConfirmation by viewModel.sessionStartConfirmation.collectAsState()
    val sessionProtectionNote by viewModel.sessionProtectionNote.collectAsState()

    // Alarm management states
    val alarms by viewModel.alarms.collectAsState()
    val isAlarmRinging by viewModel.isAlarmRinging.collectAsState()
    val ringingAlarm by viewModel.ringingAlarm.collectAsState()
    val currentlyPreviewingRingtone by viewModel.currentlyPreviewingRingtone.collectAsState()

    // Focus Shield / App Blocker states
    val isShieldHubOpen by viewModel.isShieldHubOpen.collectAsState()
    val isShieldOverlayVisible by viewModel.isShieldOverlayVisible.collectAsState()
    val isDeepFocusEnabled by viewModel.isDeepFocusEnabled.collectAsState()
    val isDeepFocusSessionActive by viewModel.isDeepFocusSessionActive.collectAsState()
    val shieldInterceptedAppName by viewModel.shieldInterceptedAppName.collectAsState()
    val shieldInterceptReason by viewModel.shieldInterceptReason.collectAsState()
    val isGeminiBlocked by viewModel.isGeminiBlocked.collectAsState()
    val isStandaloneShieldActive by viewModel.isStandaloneShieldActive.collectAsState()
    val standaloneShieldRemainingSeconds by viewModel.standaloneShieldRemainingSeconds.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val leaderboardUsers by viewModel.leaderboardUsers.collectAsState()
    val hallOfFame by viewModel.hallOfFame.collectAsState()

    // Scheduled Blocks & AI Study Guard states
    val scheduledBlocks by viewModel.scheduledBlocks.collectAsState()
    val appDailyLimits by viewModel.appDailyLimits.collectAsState()
    var isAppLimitsOpen by remember { mutableStateOf(false) }
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
    val showPreStudyBlockSheet by viewModel.showPreStudyBlockSheet.collectAsState()
    val isSupportLockZenSheetOpen by viewModel.isSupportLockZenSheetOpen.collectAsState()
    val showMilestoneDonationPrompt by viewModel.showMilestoneDonationPrompt.collectAsState()
    val showSessionDonationPrompt by viewModel.showSessionDonationPrompt.collectAsState()
    val lastCompletedSessionForPrompt by viewModel.lastCompletedSessionForPrompt.collectAsState()

    // Local Auth & Questionnaire states
    val currentUser by viewModel.currentUser.collectAsState()
    val availableAppUpdate by viewModel.availableAppUpdate.collectAsState()
    val manualUpdateCheckResult by viewModel.manualUpdateCheckResult.collectAsState()
    val isAuthChecking by viewModel.isAuthChecking.collectAsState()
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val isProfileLoading by viewModel.isProfileLoading.collectAsState()
    val profileError by viewModel.profileError.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val showQuestionnaire by viewModel.showQuestionnaire.collectAsState()
    val verificationMessage by viewModel.verificationMessage.collectAsState()
    val isSigningOut by viewModel.isSigningOut.collectAsState()

    val context = LocalContext.current
    var isNetworkConnected by remember { mutableStateOf(com.example.util.NetworkUtils.isInternetAvailable(context)) }
    var allowOfflineSession by rememberSaveable { mutableStateOf(false) }
    var hasCheckedNotificationPermissionOnStartup by rememberSaveable(currentUser?.id) { mutableStateOf(false) }
    var showNotificationPermissionPrompt by rememberSaveable(currentUser?.id) { mutableStateOf(false) }
    var showSignOutConfirmationDialog by rememberSaveable { mutableStateOf(false) }

    // Real-time network connectivity observation
    LaunchedEffect(Unit) {
        com.example.util.NetworkUtils.observeNetworkConnectivity(context).collect { connected ->
            isNetworkConnected = connected
            if (connected) {
                allowOfflineSession = false
                viewModel.refreshScheduledBlocks()
                viewModel.refreshAlarms()
                viewModel.syncUserData(context)
            }
        }
    }

    // Deep Focus: Android Screen Pinning / Lock Task Mode lifecycle
    val currentActivity = context as? android.app.Activity
    LaunchedEffect(isTimerRunning, isDeepFocusSessionActive) {
        if (currentActivity != null) {
            if (isTimerRunning && isDeepFocusSessionActive) {
                com.example.util.DeepFocusManager.startScreenPinning(currentActivity)
            } else if (!isTimerRunning && !isDeepFocusSessionActive) {
                com.example.util.DeepFocusManager.stopScreenPinning(currentActivity)
            }
        }
    }

    // Prevent back navigation away when Deep Focus task lock is active
    androidx.activity.compose.BackHandler(enabled = isTimerRunning && isDeepFocusSessionActive) {
        // Deep Focus Active: Back gesture intercepted to keep student inside session
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("MainActivity", "Notification permission request result: $isGranted")
    }

    // Startup Notification Permission Check, Sunday Recap Check, & In-App Update Check
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            viewModel.checkWeeklyReview(context)
            viewModel.checkForAppUpdates(context, isManual = false)
            if (!hasCheckedNotificationPermissionOnStartup) {
                hasCheckedNotificationPermissionOnStartup = true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val isOsGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!isOsGranted) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

    LaunchedEffect(manualUpdateCheckResult) {
        if (!manualUpdateCheckResult.isNullOrBlank()) {
            android.widget.Toast.makeText(context, manualUpdateCheckResult, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearManualUpdateMessage()
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
    var isSessionLogOpen by remember { mutableStateOf(false) }
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

    val shouldShowOfflineBlocker = !isNetworkConnected && !allowOfflineSession && currentUser == null

    FocuslyTheme(darkTheme = isDarkTheme) {
        if (shouldShowOfflineBlocker) {
            com.example.ui.screens.OfflineScreen(
                onRetry = {
                    isNetworkConnected = com.example.util.NetworkUtils.isInternetAvailable(context)
                },
                onContinueOffline = {
                    allowOfflineSession = true
                    viewModel.signInAsGuest()
                }
            )
        } else if (!isSplashFinished || isAuthChecking) {
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
        } else if (!viewModel.isGuestUser(currentUser) && (showQuestionnaire || userPreferences?.hasCompletedIntakeSurvey == false || userPreferences?.hasCompletedOnboarding == false)) {
            OnboardingSurveyScreen(
                initialUserName = currentUser?.fullName ?: userPreferences?.currentUserName ?: "Scholar",
                onFinishSurvey = { name, studentClassLevel, isBoardExamYear, primaryStudyGoal, biggestDistractionApp, preferredStudyTimeWindow, dailyScreenTimeGoalMinutes, motivationStyle ->
                    viewModel.submitIntakeSurvey(
                        name = name,
                        studentClassLevel = studentClassLevel,
                        isBoardExamYear = isBoardExamYear,
                        primaryStudyGoal = primaryStudyGoal,
                        biggestDistractionApp = biggestDistractionApp,
                        preferredStudyTimeWindow = preferredStudyTimeWindow,
                        dailyScreenTimeGoalMinutes = dailyScreenTimeGoalMinutes,
                        motivationStyle = motivationStyle
                    )
                    viewModel.completeOnboarding()
                    if (!PermissionUtils.hasNotificationPermission(context)) {
                        showNotificationPermissionPrompt = true
                    }
                }
            )
        } else {
            // Main OS Shell (Direct Open)
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!isSettingsOpen && !isTimerRunning) {
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
                                            showSignOutConfirmationDialog = true
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
                                    val hasCoreShieldPerms = PermissionUtils.hasUsageStatsPermission(context) &&
                                        PermissionUtils.hasOverlayPermission(context) &&
                                        AiStudyGuardManager.isAccessibilityPermissionGranted(context)

                                    FocusScreen(
                                        remainingSeconds = remainingSeconds,
                                        targetSeconds = targetSeconds,
                                        isRunning = isTimerRunning,
                                        currentMode = currentMode,
                                        currentTaskTitle = currentTaskTitle,
                                        distractionsCount = distractionsCount,
                                        ambientSound = ambientSound,
                                        userStreak = userPreferences?.currentStreak ?: 0,
                                        userPreferences = userPreferences,
                                        isShieldActive = hasCoreShieldPerms && (userPreferences?.isAppBlockerEnabled == true || isTimerRunning || isStandaloneShieldActive),
                                        isDeepFocusEnabled = isDeepFocusEnabled,
                                        isDeepFocusSessionActive = isDeepFocusSessionActive,
                                        onToggleDeepFocus = { viewModel.toggleDeepFocus(it) },
                                        sessionStartConfirmation = sessionStartConfirmation,
                                        sessionProtectionNote = sessionProtectionNote,
                                        showPreStudyBlockSheet = showPreStudyBlockSheet,
                                        onConfirmPreStudyBlock = { pkgs, dontShowAgain ->
                                            viewModel.confirmPreStudyBlock(pkgs, dontShowAgain)
                                        },
                                        onDismissPreStudyBlock = { viewModel.dismissPreStudyBlockSheet() },
                                        onStartTimer = { viewModel.requestStudySessionStart() },
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
                                        hallOfFame = hallOfFame,
                                        currentUserName = userPreferences?.currentUserName ?: "You",
                                        currentUserPhotoUrl = userPreferences?.currentUserPhotoUrl,
                                        currentUserPoints = userPreferences?.focusPoints ?: 0,
                                        currentUserStreak = userPreferences?.currentStreak ?: 1,
                                        sessions = sessions,
                                        onRefresh = { viewModel.refreshLeaderboard() }
                                    )
                                }
                                NavTab.INSIGHTS.name -> {
                                    InsightsScreen(
                                        sessions = sessions,
                                        reflections = reflections,
                                        userPreferences = userPreferences,
                                        onSaveReflection = { viewModel.saveWeeklyReflection(it) },
                                        onOpenSessionHistory = { isSessionLogOpen = true }
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
                                        onOpenSundayRecap = { viewModel.openLatestSundayRecap() },
                                        onOpenSupportLockZen = { viewModel.openSupportLockZenSheet() },
                                        onOpenSessionHistory = { isSessionLogOpen = true },
                                        onCheckForUpdates = { viewModel.checkForAppUpdates(context, isManual = true) },
                                        onLogout = { showSignOutConfirmationDialog = true },
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

                // Weekly Review (Sunday Recap) Glassmorphism Dialog
                val currentWeeklyReview = viewModel.weeklyReviewSummary.collectAsStateWithLifecycle().value
                currentWeeklyReview?.let { review ->
                    SundayRecapGlassDialog(
                        recap = review,
                        onDismiss = { viewModel.dismissWeeklyReview() }
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
                        onOpenAppLimits = { isAppLimitsOpen = true },
                        onDismiss = { viewModel.closeShieldHub() }
                    )
                }

                // Per-App Daily Time Limits Screen
                if (isAppLimitsOpen) {
                    com.example.ui.screens.AppLimitsScreen(
                        limits = appDailyLimits,
                        installedApps = installedApps,
                        onBack = { isAppLimitsOpen = false },
                        onSaveLimit = { viewModel.saveAppDailyLimit(it) },
                        onDeleteLimit = { viewModel.deleteAppDailyLimit(it) },
                        onToggleEnabled = { id, en -> viewModel.toggleAppDailyLimitEnabled(id, en) }
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
                        reason = shieldInterceptReason,
                        isGeminiDetected = isGeminiBlocked,
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

                // Full-screen Past Focus Sessions Log Screen (Firestore Cloud Storage)
                if (isSessionLogOpen) {
                    androidx.activity.compose.BackHandler {
                        isSessionLogOpen = false
                    }
                    FocusSessionLogScreen(
                        sessions = sessions,
                        onBack = { isSessionLogOpen = false },
                        onStartNewSession = {
                            isSessionLogOpen = false
                            selectedTab = NavTab.FOCUS
                        },
                        onDeleteSession = { sessionId ->
                            viewModel.deleteFocusSession(sessionId)
                        }
                    )
                }

                // Post-Login / Post-Onboarding Alarm & Notification Permission Sheet
                if (showNotificationPermissionPrompt && !PermissionUtils.hasNotificationPermission(context)) {
                    NotificationAlarmPermissionSheet(
                        onDismiss = { showNotificationPermissionPrompt = false },
                        onPermissionGranted = { showNotificationPermissionPrompt = false }
                    )
                }

                // Sign Out & Final Supabase Sync Confirmation Dialog
                if (showSignOutConfirmationDialog) {
                    SignOutConfirmationDialog(
                        userPreferences = userPreferences,
                        sessionsCount = sessions.size,
                        isSyncing = isSigningOut,
                        onDismiss = {
                            if (!isSigningOut) {
                                showSignOutConfirmationDialog = false
                            }
                        },
                        onConfirmSignOut = {
                            viewModel.syncAndLogout {
                                showSignOutConfirmationDialog = false
                                isSettingsOpen = false
                                android.widget.Toast.makeText(context, "Study progress synced & signed out", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // Voluntary Support / Donation Sheet
                if (isSupportLockZenSheetOpen) {
                    SupportLockZenSheet(
                        sheetState = supportLockZenSheetState,
                        onDismiss = { viewModel.closeSupportLockZenSheet() }
                    )
                }

                // In-App Update Overlay Dialog
                availableAppUpdate?.let { updateInfo ->
                    if (updateInfo.isUpdateAvailable) {
                        AppUpdateDialog(
                            updateInfo = updateInfo,
                            onDownloadClick = { downloadUrl ->
                                viewModel.downloadAndInstallUpdate(context, downloadUrl)
                            },
                            onDismiss = {
                                viewModel.dismissAppUpdate(context)
                            }
                        )
                    }
                }
            }
        }
    }
}
