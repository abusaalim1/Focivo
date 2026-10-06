package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AlarmAudioEngine
import com.example.audio.AmbientAudioEngine
import com.example.data.AndroidPreferenceSessionManager
import com.example.data.FirebaseAuthManager
import com.example.data.FocuslyRepository
import com.example.data.FirebaseSyncManager
import com.example.data.SupabaseService
import com.example.data.LeaderboardUser
import com.example.data.model.AlarmItem
import com.example.data.model.AppDailyLimitEntity
import com.example.data.model.FocusSessionEntity
import com.example.data.model.ReflectionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserAccountEntity
import com.example.data.model.UserPreferencesEntity
import com.example.data.model.WeeklyRecapSummary
import com.example.data.model.WeeklySubjectStat
import com.example.data.model.WeeklyDailyStat
import com.example.data.model.WeeklySessionDetail
import com.example.service.FocusShieldService
import com.example.service.ScheduledBlockScheduler
import com.example.service.WeeklyRecapNotificationHelper
import com.example.util.DeviceAppInfo
import com.example.util.InstalledAppsManager
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

class FocuslyViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "FocuslyViewModel"
    }

    val authManager = FirebaseAuthManager.getInstance(application)
    private val repository = FocuslyRepository.getInstance(application)

    val audioEngine = AmbientAudioEngine()
    val alarmAudioEngine = AlarmAudioEngine(application.applicationContext)

    // Local Authentication states
    private val _currentUser = MutableStateFlow<UserAccountEntity?>(null)
    val currentUser: StateFlow<UserAccountEntity?> = _currentUser.asStateFlow()

    private val _isAuthChecking = MutableStateFlow(true)
    val isAuthChecking: StateFlow<Boolean> = _isAuthChecking.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()

    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError.asStateFlow()

    private val _showQuestionnaire = MutableStateFlow(false)
    val showQuestionnaire: StateFlow<Boolean> = _showQuestionnaire.asStateFlow()

    // Alarm management states
    private val _alarms = MutableStateFlow<List<AlarmItem>>(emptyList())
    val alarms: StateFlow<List<AlarmItem>> = _alarms.asStateFlow()

    private val _isAlarmRinging = MutableStateFlow(false)
    val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()

    private val _ringingAlarm = MutableStateFlow<AlarmItem?>(null)
    val ringingAlarm: StateFlow<AlarmItem?> = _ringingAlarm.asStateFlow()

    private val _currentlyPreviewingRingtone = MutableStateFlow<String?>(null)
    val currentlyPreviewingRingtone: StateFlow<String?> = _currentlyPreviewingRingtone.asStateFlow()

    private var alarmCheckerJob: Job? = null
    private var lastTriggeredMinute = -1

    // Database flows
    val allTasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topPriorities: StateFlow<List<TaskEntity>> = repository.topPriorities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<FocusSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReflections: StateFlow<List<ReflectionEntity>> = repository.allReflections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<UserPreferencesEntity?> = repository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val appDailyLimits: StateFlow<List<AppDailyLimitEntity>> = repository.appDailyLimits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Focus Session State
    private var sessionStartTimeMs: Long = 0L
    private var accumulatedSessionElapsedSecs: Int = 0

    private val _remainingSeconds = MutableStateFlow(25 * 60)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _targetSeconds = MutableStateFlow(25 * 60)
    val targetSeconds: StateFlow<Int> = _targetSeconds.asStateFlow()

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning.asStateFlow()

    private val _currentMode = MutableStateFlow("Deep Work")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _currentTaskTitle = MutableStateFlow("Deep Work Session")
    val currentTaskTitle: StateFlow<String> = _currentTaskTitle.asStateFlow()

    private var didSessionAutoEnableAppBlocker = false
    private var didSessionAutoEnableAiGuard = false

    private val _sessionStartConfirmation = MutableStateFlow<String?>(null)
    val sessionStartConfirmation: StateFlow<String?> = _sessionStartConfirmation.asStateFlow()

    private val _sessionProtectionNote = MutableStateFlow<String?>(null)
    val sessionProtectionNote: StateFlow<String?> = _sessionProtectionNote.asStateFlow()

    fun isGuestUser(user: UserAccountEntity? = null): Boolean {
        val target = user ?: _currentUser.value
        val isGuestByPref = AndroidPreferenceSessionManager.appContext?.let {
            AndroidPreferenceSessionManager.isGuestSession(it)
        } ?: false
        if (isGuestByPref) return true
        if (target == null) return false
        val email = target.email.lowercase(Locale.ROOT)
        val uid = target.firebaseUid.lowercase(Locale.ROOT)
        val name = target.fullName.lowercase(Locale.ROOT)
        return email.contains("guest") || uid.startsWith("guest_") || name.contains("guest")
    }

    fun dismissSessionConfirmation() {
        _sessionStartConfirmation.value = null
    }

    fun dismissProtectionNote() {
        _sessionProtectionNote.value = null
    }

    private val _distractionsCount = MutableStateFlow(0)
    val distractionsCount: StateFlow<Int> = _distractionsCount.asStateFlow()

    private val _loggedDistractions = MutableStateFlow<List<String>>(emptyList())
    val loggedDistractions: StateFlow<List<String>> = _loggedDistractions.asStateFlow()

    private val _sessionNotes = MutableStateFlow("")
    val sessionNotes: StateFlow<String> = _sessionNotes.asStateFlow()

    private val _isBreakMode = MutableStateFlow(false)
    val isBreakMode: StateFlow<Boolean> = _isBreakMode.asStateFlow()

    // Ambient sound state (defaults to Silent)
    private val _ambientSound = MutableStateFlow("Silent")
    val ambientSound: StateFlow<String> = _ambientSound.asStateFlow()

    // Full screen completion moment trigger
    private val _showCompletionScreen = MutableStateFlow(false)
    val showCompletionScreen: StateFlow<Boolean> = _showCompletionScreen.asStateFlow()

    private val _completedSessionSummary = MutableStateFlow<FocusSessionEntity?>(null)
    val completedSessionSummary: StateFlow<FocusSessionEntity?> = _completedSessionSummary.asStateFlow()

    // Support LockZen / Voluntary Donation State
    private val _isSupportLockZenSheetOpen = MutableStateFlow(false)
    val isSupportLockZenSheetOpen: StateFlow<Boolean> = _isSupportLockZenSheetOpen.asStateFlow()

    private val _showMilestoneDonationPrompt = MutableStateFlow(false)
    val showMilestoneDonationPrompt: StateFlow<Boolean> = _showMilestoneDonationPrompt.asStateFlow()

    // Post-session donation prompt state (triggers after EVERY completed study session)
    private val _showSessionDonationPrompt = MutableStateFlow(false)
    val showSessionDonationPrompt: StateFlow<Boolean> = _showSessionDonationPrompt.asStateFlow()

    private val _lastCompletedSessionForPrompt = MutableStateFlow<FocusSessionEntity?>(null)
    val lastCompletedSessionForPrompt: StateFlow<FocusSessionEntity?> = _lastCompletedSessionForPrompt.asStateFlow()

    private var sessionDonationRotationIndex = 0

    // App Blocker / Focus Shield State
    private val _isShieldHubOpen = MutableStateFlow(false)
    val isShieldHubOpen: StateFlow<Boolean> = _isShieldHubOpen.asStateFlow()

    private val _isShieldOverlayVisible = MutableStateFlow(false)
    val isShieldOverlayVisible: StateFlow<Boolean> = _isShieldOverlayVisible.asStateFlow()

    private val _shieldInterceptedAppName = MutableStateFlow("Instagram & Reels")
    val shieldInterceptedAppName: StateFlow<String> = _shieldInterceptedAppName.asStateFlow()

    private val _shieldInterceptReason = MutableStateFlow<String?>(null)
    val shieldInterceptReason: StateFlow<String?> = _shieldInterceptReason.asStateFlow()

    private val _isGeminiBlocked = MutableStateFlow(false)
    val isGeminiBlocked: StateFlow<Boolean> = _isGeminiBlocked.asStateFlow()

    private val _isStandaloneShieldActive = MutableStateFlow(false)
    val isStandaloneShieldActive: StateFlow<Boolean> = _isStandaloneShieldActive.asStateFlow()

    val isDeepFocusEnabled: StateFlow<Boolean> = com.example.util.DeepFocusManager.isDeepFocusEnabledState
    val isDeepFocusSessionActive: StateFlow<Boolean> = com.example.util.DeepFocusManager.isDeepFocusActiveState

    fun toggleDeepFocus(enabled: Boolean) {
        com.example.util.DeepFocusManager.setDeepFocusEnabled(getApplication(), enabled)
        val current = userPreferences.value ?: UserPreferencesEntity()
        viewModelScope.launch {
            repository.savePreferences(current.copy(isDeepFocusEnabled = enabled))
        }
    }

    private val _standaloneShieldRemainingSeconds = MutableStateFlow(0)
    val standaloneShieldRemainingSeconds: StateFlow<Int> = _standaloneShieldRemainingSeconds.asStateFlow()

    private var standaloneShieldJob: Job? = null
    private var timerJob: Job? = null

    val firebaseSyncManager = FirebaseSyncManager.getInstance(application)
    private val _installedApps = MutableStateFlow<List<DeviceAppInfo>>(emptyList())
    val installedApps: StateFlow<List<DeviceAppInfo>> = _installedApps.asStateFlow()
    val leaderboardUsers: StateFlow<List<LeaderboardUser>> = firebaseSyncManager.leaderboardUsers
    val hallOfFame: StateFlow<List<com.example.data.HallOfFameItem>> = firebaseSyncManager.hallOfFame

    fun refreshLeaderboard() {
        firebaseSyncManager.refreshLeaderboard()
    }

    // Scheduled Study Blocks (User-editable custom schedules)
    private val _scheduleStatusSummary = MutableStateFlow("No active schedule")
    val scheduleStatusSummary: StateFlow<String> = _scheduleStatusSummary.asStateFlow()

    private val _scheduledBlocks = MutableStateFlow<List<com.example.data.SupabaseScheduledBlockDto>>(
        com.example.service.ScheduledBlockScheduler.getLocalSchedules(application)
    )
    val scheduledBlocks: StateFlow<List<com.example.data.SupabaseScheduledBlockDto>> = _scheduledBlocks.asStateFlow()

    private val _shouldNavigateToStudyTab = MutableStateFlow(false)
    val shouldNavigateToStudyTab: StateFlow<Boolean> = _shouldNavigateToStudyTab.asStateFlow()

    private val _weeklyReviewSummary = MutableStateFlow<WeeklyRecapSummary?>(null)
    val weeklyReviewSummary: StateFlow<WeeklyRecapSummary?> = _weeklyReviewSummary.asStateFlow()

    fun consumeStudyTabNavigation() {
        _shouldNavigateToStudyTab.value = false
    }

    fun handleWidgetLaunch(
        tab: String?,
        quickMins: Int?,
        taskTitle: String?,
        startTimer: Boolean,
        openShieldHub: Boolean,
        openZenBreak: Boolean
    ) {
        if (!taskTitle.isNullOrBlank()) {
            setCurrentTaskTitle(taskTitle)
        }
        if (quickMins != null && quickMins > 0) {
            val modeName = if (quickMins >= 50) "Deep Work" else if (quickMins <= 15) "Sprint" else "Classic"
            setMode(modeName, quickMins)
            _shouldNavigateToStudyTab.value = true
            if (!_isTimerRunning.value) {
                startTimer()
            }
        } else if (startTimer) {
            _shouldNavigateToStudyTab.value = true
            if (!_isTimerRunning.value) {
                startTimer()
            }
        } else if (tab == "FOCUS") {
            _shouldNavigateToStudyTab.value = true
        }

        if (openShieldHub) {
            openShieldHub()
        }

        if (openZenBreak) {
            openSupportLockZenSheet()
        }

        com.example.widget.FocivoWidgetHelper.updateAllWidgets(getApplication())
    }

    fun calculateWeeklyRecapSummary(sessions: List<FocusSessionEntity>, streak: Int = 0): WeeklyRecapSummary? {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val currentWeekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
        val currentYear = cal.get(Calendar.YEAR)
        val weekKey = "$currentYear-W$currentWeekOfYear"

        // Calculate 7-day start time (6 days ago at 00:00:00)
        val sevenDaysCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val weekStart = sevenDaysCal.timeInMillis

        // Filter all sessions completed in the past 7 days (or fallback to latest sessions)
        val thisWeekSessions = sessions.filter {
            it.completedAt >= weekStart && it.durationSeconds > 0
        }.ifEmpty {
            sessions.filter { (now - it.completedAt) <= (8 * 24 * 3600 * 1000L) && it.durationSeconds > 0 }
        }.ifEmpty {
            sessions.filter { it.durationSeconds > 0 }
        }

        if (thisWeekSessions.isEmpty()) {
            return null
        }

        val totalSecs = thisWeekSessions.sumOf { it.durationSeconds }
        val totalMinutes = maxOf(1, totalSecs / 60)
        val hoursFormatted = String.format(Locale.US, "%.1f", totalSecs / 3600.0)

        // Date range label: e.g. "Sep 14 – Sep 20, 2026"
        val startDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val endDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val earliestSessionTime = thisWeekSessions.minOfOrNull { it.completedAt } ?: weekStart
        val weekIdentifier = "${startDateFormat.format(Date(earliestSessionTime))} – ${endDateFormat.format(Date(now))}"

        // Daily breakdown (Mon - Sun)
        val dailyMap = mutableMapOf<Int, Int>() // 1=Sun, 2=Mon, ..., 7=Sat
        for (i in 1..7) dailyMap[i] = 0

        for (s in thisWeekSessions) {
            val c = Calendar.getInstance()
            c.timeInMillis = s.completedAt
            val dayOfWeek = c.get(Calendar.DAY_OF_WEEK)
            dailyMap[dayOfWeek] = (dailyMap[dayOfWeek] ?: 0) + (s.durationSeconds / 60)
        }

        // Ordered Mon -> Sun
        val orderedDayIndices = listOf(2, 3, 4, 5, 6, 7, 1)
        val bestDayEntry = dailyMap.maxByOrNull { it.value }
        val bestDayName = if (bestDayEntry != null && (bestDayEntry.value ?: 0) > 0) {
            when (bestDayEntry.key) {
                1 -> "Sunday"
                2 -> "Monday"
                3 -> "Tuesday"
                4 -> "Wednesday"
                5 -> "Thursday"
                6 -> "Friday"
                7 -> "Saturday"
                else -> "Sunday"
            }
        } else "Sunday"
        val bestDayMins = bestDayEntry?.value ?: 0

        val dailyStats = orderedDayIndices.map { dayIdx ->
            val dName = when (dayIdx) {
                1 -> "Sun"
                2 -> "Mon"
                3 -> "Tue"
                4 -> "Wed"
                5 -> "Thu"
                6 -> "Fri"
                7 -> "Sat"
                else -> "Day"
            }
            val mins = dailyMap[dayIdx] ?: 0
            WeeklyDailyStat(
                dayName = dName,
                totalMinutes = mins,
                isBestDay = (dayIdx == bestDayEntry?.key && mins > 0)
            )
        }

        // Subject breakdown ("kis session kya pdha")
        val subjectMap = mutableMapOf<String, Pair<Int, Int>>() // subject -> (totalMinutes, sessionCount)
        for (s in thisWeekSessions) {
            val sub = if (s.taskTitle.isNotBlank() && s.taskTitle.contains("-")) {
                s.taskTitle.substringBefore("-").trim()
            } else if (s.mode.isNotBlank()) {
                s.mode
            } else {
                "General Study"
            }
            val curr = subjectMap[sub] ?: Pair(0, 0)
            subjectMap[sub] = Pair(curr.first + (s.durationSeconds / 60), curr.second + 1)
        }

        val subjectStats = subjectMap.map { (sub, pair) ->
            val mins = pair.first
            val count = pair.second
            val pct = if (totalMinutes > 0) mins.toFloat() / totalMinutes.toFloat() else 0f
            WeeklySubjectStat(
                subject = sub,
                totalMinutes = mins,
                sessionCount = count,
                percentage = pct
            )
        }.sortedByDescending { it.totalMinutes }

        val topSubject = subjectStats.firstOrNull()?.subject ?: "Deep Work"

        // Session details list
        val timeFormat = SimpleDateFormat("EEE, h:mm a", Locale.getDefault())
        val sessionDetails = thisWeekSessions.sortedByDescending { it.completedAt }.map { s ->
            val sub = if (s.taskTitle.isNotBlank() && s.taskTitle.contains("-")) {
                s.taskTitle.substringBefore("-").trim()
            } else {
                s.mode.ifBlank { "Deep Work" }
            }
            WeeklySessionDetail(
                sessionId = s.id,
                title = s.taskTitle.ifBlank { "Focus Session" },
                subject = sub,
                durationMinutes = maxOf(1, s.durationSeconds / 60),
                completedAt = s.completedAt,
                formattedTime = timeFormat.format(Date(s.completedAt)),
                notes = s.notes,
                mode = s.mode
            )
        }

        val motivationalQuotes = listOf(
            "“Success is the sum of small efforts, repeated day in and day out.” — Exceptional work this week!",
            "“Deep focus is a superpower in the modern world.” You proved your dedication this week! 🚀",
            "“Continuous improvement is better than delayed perfection.” Keep this momentum going strong!",
            "“Focus on the step in front of you, not the whole staircase.” Stellar focus logged this week 🌱"
        )
        val quote = motivationalQuotes[kotlin.math.abs(weekKey.hashCode()) % motivationalQuotes.size]

        return WeeklyRecapSummary(
            weekIdentifier = weekIdentifier,
            weekKey = weekKey,
            weekStartTimestamp = weekStart,
            weekEndTimestamp = now,
            totalMinutesFocused = totalMinutes,
            totalHoursFormatted = hoursFormatted,
            sessionCount = thisWeekSessions.size,
            currentStreak = streak,
            pointsEarned = 0,
            bestDayName = bestDayName,
            bestDayMinutes = bestDayMins,
            topSubject = topSubject,
            subjectBreakdowns = subjectStats,
            dailyBreakdowns = dailyStats,
            sessionDetails = sessionDetails,
            motivationalQuote = quote
        )
    }

    fun checkWeeklyReview(context: Context? = null) {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            if (!isSunday) {
                // Sunday recap ONLY available and shown on Sundays
                return@launch
            }

            val prefs = userPreferences.value ?: repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
            val currentWeekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
            val currentYear = cal.get(Calendar.YEAR)
            val currentWeekKey = "$currentYear-W$currentWeekOfYear"

            // Check if user already viewed this week's Sunday recap -> show only once!
            if (prefs.lastViewedSundayRecapWeek == currentWeekKey) {
                return@launch
            }

            val pastSessions = repository.allSessions.firstOrNull() ?: emptyList()
            val recap = calculateWeeklyRecapSummary(pastSessions, prefs.currentStreak) ?: return@launch

            _weeklyReviewSummary.value = recap

            // Trigger Sunday notification
            val ctx = context ?: AndroidPreferenceSessionManager.appContext
            if (ctx != null) {
                WeeklyRecapNotificationHelper.showSundayRecapNotification(ctx, recap)
            }

            // Also persist recap to Supabase reflections in real-time
            val reflectionEntity = ReflectionEntity(
                id = System.currentTimeMillis(),
                weekLabel = recap.weekIdentifier,
                totalMinutesFocused = recap.totalMinutesFocused,
                sessionsCompleted = recap.sessionCount,
                bestDay = recap.bestDayName,
                completionRate = 100,
                reflectionText = recap.toShareableText(),
                createdAt = System.currentTimeMillis()
            )
            repository.insertReflection(reflectionEntity)
        }
    }

    fun dismissWeeklyReview() {
        viewModelScope.launch {
            val currentRecap = _weeklyReviewSummary.value
            _weeklyReviewSummary.value = null
            val cal = Calendar.getInstance()
            val currentWeekKey = currentRecap?.weekKey ?: "${cal.get(Calendar.YEAR)}-W${cal.get(Calendar.WEEK_OF_YEAR)}"
            val prefs = userPreferences.value ?: repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
            val updatedPrefs = prefs.copy(
                lastWeeklyReviewShownAt = System.currentTimeMillis(),
                lastViewedSundayRecapWeek = currentWeekKey
            )
            repository.savePreferences(updatedPrefs)
        }
    }

    fun openLatestSundayRecap() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            if (!isSunday) {
                // Sunday recap ONLY available on Sundays
                return@launch
            }
            val prefs = userPreferences.value ?: repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
            val pastSessions = repository.allSessions.firstOrNull() ?: emptyList()
            val recap = calculateWeeklyRecapSummary(pastSessions, prefs.currentStreak)
            if (recap != null) {
                _weeklyReviewSummary.value = recap
            }
        }
    }

    // AI Study Guard & Punishment Logs State
    val activeAiWarning = com.example.util.AiStudyGuardManager.activeWarning
    val activeAiBlock = com.example.util.AiStudyGuardManager.activeBlock
    val activeAiResolved = com.example.util.AiStudyGuardManager.activeResolved
    val punishmentLogs: StateFlow<List<com.example.data.SupabasePunishmentLogDto>> =
        com.example.util.AiStudyGuardManager.punishmentLogs

    init {
        viewModelScope.launch {
            val app = getApplication<Application>()
            com.example.util.AiStudyGuardManager.init(app)
            com.example.util.DeepFocusManager.init(app)
            com.example.util.InstalledAppsManager.preloadApps(app)

            // Load local alarms immediately on cold launch
            val rawLocal = com.example.service.AlarmScheduler.getLocalAlarms(app)
            val dummyLabels = setOf("Morning Deep Focus", "Midday Reset", "Day Review")
            rawLocal.filter { it.label in dummyLabels }.forEach {
                com.example.service.AlarmScheduler.cancelAlarm(app, it.id)
            }
            val localAlarms = rawLocal.filterNot { it.label in dummyLabels }
            if (rawLocal.size != localAlarms.size) {
                com.example.service.AlarmScheduler.saveLocalAlarms(app, localAlarms)
            }
            if (localAlarms.isNotEmpty()) {
                _alarms.value = localAlarms
                com.example.service.AlarmScheduler.rescheduleAllEnabled(app, localAlarms)
                Log.d(TAG, "[AlarmLoad] Restored ${localAlarms.size} local cached alarms on cold startup")
            } else {
                _alarms.value = emptyList()
            }

            refreshScheduledBlocks()
            refreshPunishmentLogs()
            val isShieldRunning = FocusShieldService.isShieldRunning(app)
            val remainingSecs = FocusShieldService.getRemainingSeconds(app)
            val isScheduleActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(app)

            if (isShieldRunning && remainingSecs > 0) {
                _isStandaloneShieldActive.value = true
                _standaloneShieldRemainingSeconds.value = remainingSecs
                startShieldTicker(remainingSecs)
                val currentPrefs = repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
                repository.savePreferences(currentPrefs.copy(isAppBlockerEnabled = true))
            } else if (!isScheduleActive) {
                _isStandaloneShieldActive.value = false
                _standaloneShieldRemainingSeconds.value = 0
            }

            // 2. Restore persistent Supabase or local Guest session on cold start before UI rendering
            _isAuthChecking.value = true
            val restoredUid: String? = SupabaseService.getInstance().awaitSessionRestoration()
            if (!restoredUid.isNullOrBlank()) {
                restoreUserAccount(restoredUid)
            } else {
                // Check if user is a logged-in Guest or has existing local account stored
                val isGuest = AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.isGuestSession(it) } ?: false
                val storedUid = AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.getStoredUserId(it) }
                val guestStorage = com.example.data.GuestDataStorageManager.getInstance(app)
                val storedGuestProfile = guestStorage.loadGuestProfile()
                val localPrefs = repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
                val localUser = if (!localPrefs.currentUserEmail.isNullOrBlank()) {
                    repository.getUserByEmail(localPrefs.currentUserEmail)
                } else if (localPrefs.currentUserId != null) {
                    repository.getUserById(localPrefs.currentUserId)
                } else null

                if (localUser != null) {
                    _currentUser.value = localUser
                    val isSurveyCompleted = localPrefs.hasCompletedIntakeSurvey || localPrefs.hasCompletedOnboarding || localUser.hasCompletedIntakeSurvey
                    _showQuestionnaire.value = !isSurveyCompleted
                    Log.d("StartupAuth", "[SessionRestore] Restored local account from database: ${localUser.email}, surveyCompleted=$isSurveyCompleted")
                } else if (isGuest || storedGuestProfile != null || !storedUid.isNullOrBlank()) {
                    val finalUid = storedGuestProfile?.guestId
                        ?: (if (!storedUid.isNullOrBlank()) storedUid else "guest_${System.currentTimeMillis()}")
                    val guestName = storedGuestProfile?.guestName
                        ?: AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.getStoredGuestName(it) }
                        ?: localPrefs.currentUserName
                        ?: "Guest Scholar"
                    val guestEmail = storedGuestProfile?.guestEmail
                        ?: AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.getStoredEmail(it) }
                        ?: localPrefs.currentUserEmail
                        ?: "guest_${finalUid.take(8)}@focusly.app"

                    val guestUser = UserAccountEntity(
                        id = Math.abs(finalUid.hashCode().toLong()).coerceAtLeast(1L),
                        email = guestEmail,
                        fullName = guestName,
                        firebaseUid = finalUid,
                        photoUrl = localPrefs.currentUserPhotoUrl,
                        hasCompletedIntakeSurvey = localPrefs.hasCompletedIntakeSurvey || localPrefs.hasCompletedOnboarding,
                        isGoogleUser = false
                    )
                    repository.registerUser(guestUser)
                    _currentUser.value = guestUser
                    val isSurveyCompleted = localPrefs.hasCompletedIntakeSurvey || localPrefs.hasCompletedOnboarding
                    _showQuestionnaire.value = !isSurveyCompleted
                    Log.d("StartupAuth", "[SessionRestore] Restored local Guest account from guest_data: $finalUid, name=$guestName, surveyCompleted=$isSurveyCompleted")
                } else {
                    _currentUser.value = null
                }
            }
            _isAuthChecking.value = false

            // Restore active study timer if running, otherwise set standard initial mode
            val timerPrefs = app.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
            val isManualRunning = timerPrefs.getBoolean("is_manual_timer_running", false)
            val timerEndTime = timerPrefs.getLong("manual_timer_end_time_ms", 0L)
            val now = System.currentTimeMillis()
            if (isManualRunning && timerEndTime > now) {
                val rem = ((timerEndTime - now) / 1000L).toInt().coerceIn(1, 4 * 3600)
                _isTimerRunning.value = true
                _remainingSeconds.value = rem
                _targetSeconds.value = rem
                timerJob?.cancel()
                timerJob = viewModelScope.launch {
                    while (isActive && _remainingSeconds.value > 0) {
                        delay(1000L)
                        _remainingSeconds.value -= 1
                    }
                    if (_remainingSeconds.value <= 0) {
                        onTimerCompleted()
                    }
                }
            } else {
                timerPrefs.edit().putBoolean("is_manual_timer_running", false).putLong("manual_timer_end_time_ms", 0L).apply()
                setMode("Study", 25)
            }
            startAlarmChecker()
            refreshInstalledApps()
        }
    }

    suspend fun restoreUserAccount(restoredUid: String) {
        _isProfileLoading.value = true
        _profileError.value = null
        try {
            // First sync with Supabase to pull remote points, streak, profile & preferences
            repository.syncWithSupabase(restoredUid)
            refreshAlarms()
            refreshScheduledBlocks()

            val profile = SupabaseService.getInstance().fetchUserProfile(restoredUid)
            val currentPrefs = repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()

            val userEmail: String = profile?.email
                ?: currentPrefs.currentUserEmail
                ?: ("user_" + restoredUid.take(6) + "@focusly.app")
            val userName: String = if (!profile?.full_name.isNullOrBlank()) {
                profile!!.full_name
            } else {
                userEmail.substringBefore("@")
            }

            val photoUrl: String? = profile?.avatar_url?.takeIf { it.isNotBlank() } ?: currentPrefs.currentUserPhotoUrl
            val totalSessionsPoints = allSessions.value.sumOf { it.focusPointsEarned }
            val resolvedPoints = if (totalSessionsPoints > 0) totalSessionsPoints else currentPrefs.focusPoints
            val totalMins = allSessions.value.sumOf { it.durationSeconds } / 60
            val resolvedLevel = when {
                totalMins >= 600 -> 3
                totalMins >= 120 -> 2
                else -> 1
            }.coerceAtMost(3)
            val isSurveyCompletedInDb = profile?.has_completed_intake_survey == true
            val finalSurveyCompleted = if (profile != null) {
                isSurveyCompletedInDb || currentPrefs.hasCompletedIntakeSurvey
            } else {
                currentPrefs.hasCompletedIntakeSurvey
            }

            Log.d("StartupAuth", "[BUG1_VERIFICATION] Cold launch profile fetch for $restoredUid: profileNotNull=${profile != null}, points=$resolvedPoints, raw_has_completed_intake_survey=${profile?.has_completed_intake_survey}, isSurveyCompletedInDb=$isSurveyCompletedInDb, localPrefsSurveyCompleted=${currentPrefs.hasCompletedIntakeSurvey}, finalSurveyCompleted=$finalSurveyCompleted")

            val user = UserAccountEntity(
                id = Math.abs(restoredUid.hashCode().toLong()).coerceAtLeast(1L),
                email = userEmail,
                fullName = userName,
                firebaseUid = restoredUid,
                photoUrl = photoUrl,
                hasCompletedIntakeSurvey = finalSurveyCompleted
            )
            repository.updateUser(user)
            _currentUser.value = user

            repository.savePreferences(
                currentPrefs.copy(
                    currentUserId = user.id,
                    currentUserEmail = userEmail,
                    currentUserName = userName,
                    currentUserPhotoUrl = photoUrl,
                    hasCompletedIntakeSurvey = finalSurveyCompleted,
                    focusPoints = resolvedPoints,
                    userLevel = resolvedLevel
                )
            )

            _showQuestionnaire.value = !finalSurveyCompleted
            Log.i("FocuslyViewModel", "restoreUserAccount finished: uid=$restoredUid, isSurveyCompletedInDb=$isSurveyCompletedInDb, showQuestionnaire=${!finalSurveyCompleted}, avatar_url=$photoUrl")
            checkWeeklyReview()
        } catch (e: Exception) {
            _profileError.value = e.message ?: "Failed to fetch user profile"
            Log.e("FocuslyViewModel", "restoreUserAccount error: ${e.message}", e)
        } finally {
            _isProfileLoading.value = false
        }
    }

    fun fetchUserProfile() {
        viewModelScope.launch {
            val restoredUid = SupabaseService.getInstance().getCurrentUserId() ?: _currentUser.value?.firebaseUid
            if (!restoredUid.isNullOrBlank()) {
                restoreUserAccount(restoredUid)
            } else {
                _isProfileLoading.value = false
            }
        }
    }

    fun handleAuthDeeplink(uri: android.net.Uri) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _verificationMessage.value = "Verifying email confirmation link..."
            val result = SupabaseService.getInstance().handleAuthDeeplink(uri)
            result.onSuccess { uid ->
                _verificationMessage.value = "Email verified successfully! Welcome to Focivo."
                restoreUserAccount(uid)
                _showQuestionnaire.value = false
                _isAuthLoading.value = false
            }.onFailure { err ->
                val errorText = err.message ?: "This verification link has expired, please request a new one"
                _authError.value = if (errorText.contains("expired", ignoreCase = true) || errorText.contains("invalid", ignoreCase = true)) {
                    "This verification link has expired, please request a new one"
                } else {
                    errorText
                }
                _verificationMessage.value = null
                _isAuthLoading.value = false
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = repository.userPreferences.firstOrNull()
            val blockedSet = (prefs?.blockedAppsList ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            val apps = InstalledAppsManager.getInstalledApps(getApplication(), blockedSet)
            _installedApps.value = apps
        }
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun login(email: String, pin: String) {
        if (email.isBlank()) {
            _authError.value = "Please enter your email address."
            return
        }
        if (pin.length < 4) {
            _authError.value = "Password must be at least 4 characters."
            return
        }

        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null

            val cleanEmail = email.trim().lowercase()

            // 1. Authenticate via Supabase Auth
            val authResult = authManager.signInWithEmail(cleanEmail, pin)
            if (authResult.isFailure) {
                val err = authResult.exceptionOrNull()?.localizedMessage ?: "Invalid email or password on Supabase"
                _authError.value = err
                _isAuthLoading.value = false
                return@launch
            }
            val supabaseUid = authResult.getOrNull()?.uid ?: ""

            // 1. Sync user data FIRST with Supabase Postgrest tables to load remote points, avatar, sessions, and preferences
            if (supabaseUid.isNotBlank()) {
                repository.syncWithSupabase(supabaseUid)
                refreshAlarms()
                refreshScheduledBlocks()
            }

            // Query fresh profile from Supabase
            val profile = if (supabaseUid.isNotBlank()) {
                SupabaseService.getInstance().fetchUserProfile(supabaseUid)
            } else null

            val mergedPrefs = userPreferences.value ?: UserPreferencesEntity()
            val totalSessionsPoints = allSessions.value.sumOf { it.focusPointsEarned }
            val resolvedPoints = if (totalSessionsPoints > 0) totalSessionsPoints else mergedPrefs.focusPoints
            val totalMins = allSessions.value.sumOf { it.durationSeconds } / 60
            val resolvedLevel = when {
                totalMins >= 600 -> 3
                totalMins >= 120 -> 2
                else -> 1
            }.coerceAtMost(3)
            val isSurveyCompletedInDb = profile?.has_completed_intake_survey == true || mergedPrefs.hasCompletedIntakeSurvey
            val photoUrl = profile?.avatar_url?.takeIf { it.isNotBlank() } ?: mergedPrefs.currentUserPhotoUrl
            val displayName = profile?.full_name?.takeIf { it.isNotBlank() } ?: mergedPrefs.currentUserName ?: cleanEmail.substringBefore("@")

            var existingUser = repository.getUserByEmail(cleanEmail)
            val finalUser = if (existingUser == null) {
                val safeId = if (supabaseUid.isNotBlank()) Math.abs(supabaseUid.hashCode().toLong()).coerceAtLeast(1L) else System.currentTimeMillis()
                val created = UserAccountEntity(
                    id = safeId,
                    email = cleanEmail,
                    passwordHash = hashPassword(pin),
                    fullName = displayName,
                    firebaseUid = supabaseUid,
                    photoUrl = photoUrl,
                    hasCompletedIntakeSurvey = isSurveyCompletedInDb,
                    createdAt = System.currentTimeMillis()
                )
                repository.registerUser(created)
                created
            } else {
                val updated = existingUser.copy(
                    firebaseUid = supabaseUid,
                    fullName = displayName,
                    photoUrl = photoUrl ?: existingUser.photoUrl,
                    hasCompletedIntakeSurvey = isSurveyCompletedInDb || existingUser.hasCompletedIntakeSurvey
                )
                repository.updateUser(updated)
                updated
            }

            _currentUser.value = finalUser
            repository.savePreferences(
                mergedPrefs.copy(
                    currentUserId = finalUser.id,
                    currentUserEmail = finalUser.email,
                    currentUserName = finalUser.fullName,
                    currentUserPhotoUrl = finalUser.photoUrl,
                    hasCompletedOnboarding = true,
                    hasCompletedIntakeSurvey = isSurveyCompletedInDb || mergedPrefs.hasCompletedIntakeSurvey,
                    focusPoints = resolvedPoints,
                    userLevel = resolvedLevel
                )
            )

            val updatedPrefs = userPreferences.value ?: mergedPrefs
            val finalSurveyCompleted = isSurveyCompletedInDb || updatedPrefs.hasCompletedIntakeSurvey || updatedPrefs.hasCompletedOnboarding || finalUser.hasCompletedIntakeSurvey
            _showQuestionnaire.value = !finalSurveyCompleted
            Log.i("FocuslyViewModel", "login completed: uid=$supabaseUid, points=${updatedPrefs.focusPoints}, isSurveyCompletedInDb=$isSurveyCompletedInDb, finalSurveyCompleted=$finalSurveyCompleted, showQuestionnaire=${!finalSurveyCompleted}, avatar_url=$photoUrl")
            _authError.value = null
            _isAuthLoading.value = false
        }
    }

    fun register(email: String, pin: String, fullName: String = "") {
        if (email.isBlank()) {
            _authError.value = "Please enter your email address."
            return
        }
        if (!email.contains("@")) {
            _authError.value = "Please enter a valid email address (e.g. name@gmail.com)."
            return
        }
        if (pin.length < 4) {
            _authError.value = "Password must be at least 4 characters."
            return
        }

        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null

            val cleanEmail = email.trim().lowercase()
            val effectiveName = fullName.trim().ifBlank { cleanEmail.substringBefore("@") }

            // 1. Sign up via Supabase Auth
            val authResult = authManager.signUpWithEmail(cleanEmail, pin, effectiveName)
            if (authResult.isFailure) {
                val err = authResult.exceptionOrNull()?.localizedMessage ?: "Sign up failed on Supabase"
                _authError.value = err
                _isAuthLoading.value = false
                return@launch
            }
            val supabaseUid = authResult.getOrNull()?.uid ?: ""

            val safeId = System.currentTimeMillis()
            val existingUser = repository.getUserByEmail(cleanEmail)
            val newUser = if (existingUser != null) {
                existingUser.copy(
                    fullName = effectiveName,
                    firebaseUid = supabaseUid
                )
            } else {
                UserAccountEntity(
                    id = safeId,
                    email = cleanEmail,
                    passwordHash = hashPassword(pin),
                    fullName = effectiveName,
                    firebaseUid = supabaseUid,
                    createdAt = System.currentTimeMillis()
                )
            }

            if (existingUser != null) {
                repository.updateUser(newUser)
            } else {
                repository.registerUser(newUser)
            }

            _currentUser.value = newUser

            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(
                prefs.copy(
                    currentUserId = newUser.id,
                    currentUserEmail = cleanEmail,
                    currentUserName = effectiveName,
                    hasCompletedOnboarding = true,
                    hasCompletedIntakeSurvey = false
                )
            )

            // 2. Sync user data with Supabase Postgrest tables
            if (supabaseUid.isNotBlank()) {
                repository.syncWithSupabase(supabaseUid)
            }

            _showQuestionnaire.value = true
            _authError.value = null
            _isAuthLoading.value = false
        }
    }

    fun signInWithGoogle(context: Context, serverClientId: String? = null, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = authManager.signInWithGoogleCredential(context, serverClientId)
            result.onSuccess { authData ->
                val cleanEmail = authData.email

                if (authData.uid.isNotBlank()) {
                    repository.syncWithSupabase(authData.uid)
                    refreshAlarms()
                    refreshScheduledBlocks()
                }

                val profile = if (authData.uid.isNotBlank()) {
                    SupabaseService.getInstance().fetchUserProfile(authData.uid)
                } else null

                val prefs = userPreferences.value ?: UserPreferencesEntity()
                val totalSessionsPoints = allSessions.value.sumOf { it.focusPointsEarned }
                val resolvedPoints = if (totalSessionsPoints > 0) totalSessionsPoints else prefs.focusPoints
                val totalMins = allSessions.value.sumOf { it.durationSeconds } / 60
                val resolvedLevel = when {
                    totalMins >= 600 -> 3
                    totalMins >= 120 -> 2
                    else -> 1
                }.coerceAtMost(3)
                val isSurveyCompletedInDb = profile?.has_completed_intake_survey == true || prefs.hasCompletedIntakeSurvey
                val photoUrl = profile?.avatar_url?.takeIf { it.isNotBlank() } ?: authData.photoUrl
                val displayName = profile?.full_name?.takeIf { it.isNotBlank() } ?: authData.displayName?.ifBlank { cleanEmail.substringBefore("@") } ?: cleanEmail.substringBefore("@")

                val existing = repository.getUserByEmail(cleanEmail)
                val user = if (existing != null) {
                    val updated = existing.copy(
                        firebaseUid = authData.uid,
                        photoUrl = photoUrl,
                        isGoogleUser = true,
                        fullName = displayName,
                        hasCompletedIntakeSurvey = isSurveyCompletedInDb || existing.hasCompletedIntakeSurvey
                    )
                    repository.updateUser(updated)
                    updated
                } else {
                    val safeId = Math.abs(authData.uid.hashCode().toLong()).let { if (it == 0L) System.currentTimeMillis() else it }
                    val newUser = UserAccountEntity(
                        id = safeId,
                        email = cleanEmail,
                        fullName = displayName,
                        firebaseUid = authData.uid,
                        photoUrl = photoUrl,
                        isGoogleUser = true,
                        hasCompletedIntakeSurvey = isSurveyCompletedInDb
                    )
                    val newId = repository.registerUser(newUser)
                    newUser.copy(id = newId)
                }

                _currentUser.value = user
                repository.savePreferences(
                    prefs.copy(
                        currentUserId = user.id,
                        currentUserEmail = user.email,
                        currentUserName = user.fullName,
                        currentUserPhotoUrl = user.photoUrl,
                        isGoogleAuth = true,
                        hasCompletedOnboarding = true,
                        hasCompletedIntakeSurvey = isSurveyCompletedInDb || prefs.hasCompletedIntakeSurvey,
                        focusPoints = resolvedPoints,
                        userLevel = resolvedLevel
                    )
                )

                val updatedPrefs = userPreferences.value ?: prefs
                val finalSurveyCompleted = isSurveyCompletedInDb || updatedPrefs.hasCompletedIntakeSurvey || updatedPrefs.hasCompletedOnboarding || user.hasCompletedIntakeSurvey
                _showQuestionnaire.value = !finalSurveyCompleted
                Log.i("FocuslyViewModel", "signInWithGoogle finished: uid=${authData.uid}, points=$resolvedPoints, isSurveyCompletedInDb=$isSurveyCompletedInDb, finalSurveyCompleted=$finalSurveyCompleted, showQuestionnaire=${!finalSurveyCompleted}, avatar_url=$photoUrl")
                _isAuthLoading.value = false
                onResult?.invoke(true)
            }.onFailure { ex ->
                _isAuthLoading.value = false
                _authError.value = ex.localizedMessage ?: "Google Sign-In failed"
                onResult?.invoke(false)
            }
        }
    }

    fun signInWithGoogleDirect(
        displayName: String,
        email: String,
        photoUrl: String? = null
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = authManager.signInWithGoogleDirect(displayName, email, photoUrl)
            result.onSuccess { authData ->
                val cleanEmail = email.trim().lowercase()

                if (authData.uid.isNotBlank()) {
                    repository.syncWithSupabase(authData.uid)
                    refreshAlarms()
                    refreshScheduledBlocks()
                }

                val profile = if (authData.uid.isNotBlank()) {
                    SupabaseService.getInstance().fetchUserProfile(authData.uid)
                } else null

                val prefs = userPreferences.value ?: UserPreferencesEntity()
                val totalSessionsPoints = allSessions.value.sumOf { it.focusPointsEarned }
                val resolvedPoints = if (totalSessionsPoints > 0) totalSessionsPoints else prefs.focusPoints
                val totalMins = allSessions.value.sumOf { it.durationSeconds } / 60
                val resolvedLevel = when {
                    totalMins >= 600 -> 3
                    totalMins >= 120 -> 2
                    else -> 1
                }.coerceAtMost(3)
                val isSurveyCompletedInDb = profile?.has_completed_intake_survey == true || prefs.hasCompletedIntakeSurvey
                val finalPhotoUrl = profile?.avatar_url?.takeIf { it.isNotBlank() } ?: photoUrl
                val finalDisplayName = profile?.full_name?.takeIf { it.isNotBlank() } ?: displayName.ifBlank { cleanEmail.substringBefore("@") }

                val existing = repository.getUserByEmail(cleanEmail)
                val user = if (existing != null) {
                    val updated = existing.copy(
                        firebaseUid = authData.uid,
                        fullName = finalDisplayName,
                        photoUrl = finalPhotoUrl,
                        isGoogleUser = true,
                        hasCompletedIntakeSurvey = isSurveyCompletedInDb || existing.hasCompletedIntakeSurvey
                    )
                    repository.updateUser(updated)
                    updated
                } else {
                    val safeId = Math.abs(authData.uid.hashCode().toLong()).let { if (it == 0L) System.currentTimeMillis() else it }
                    val newUser = UserAccountEntity(
                        id = safeId,
                        email = cleanEmail,
                        fullName = finalDisplayName,
                        firebaseUid = authData.uid,
                        photoUrl = finalPhotoUrl,
                        isGoogleUser = true,
                        hasCompletedIntakeSurvey = isSurveyCompletedInDb
                    )
                    val newId = repository.registerUser(newUser)
                    newUser.copy(id = newId)
                }

                _currentUser.value = user
                repository.savePreferences(
                    prefs.copy(
                        currentUserId = user.id,
                        currentUserEmail = cleanEmail,
                        currentUserName = finalDisplayName,
                        currentUserPhotoUrl = finalPhotoUrl,
                        isGoogleAuth = true,
                        hasCompletedOnboarding = true,
                        hasCompletedIntakeSurvey = isSurveyCompletedInDb || prefs.hasCompletedIntakeSurvey,
                        focusPoints = resolvedPoints,
                        userLevel = resolvedLevel
                    )
                )

                val updatedPrefs = userPreferences.value ?: prefs
                val finalSurveyCompleted = isSurveyCompletedInDb || updatedPrefs.hasCompletedIntakeSurvey || updatedPrefs.hasCompletedOnboarding || user.hasCompletedIntakeSurvey
                _showQuestionnaire.value = !finalSurveyCompleted
                Log.i("FocuslyViewModel", "signInWithGoogleDirect finished: uid=${authData.uid}, points=$resolvedPoints, isSurveyCompletedInDb=$isSurveyCompletedInDb, finalSurveyCompleted=$finalSurveyCompleted, showQuestionnaire=${!finalSurveyCompleted}, avatar_url=$finalPhotoUrl")
                _isAuthLoading.value = false
            }.onFailure { ex ->
                _isAuthLoading.value = false
                _authError.value = ex.localizedMessage ?: "Google authentication failed"
            }
        }
    }

    fun signInAsGuest() {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = authManager.signInAnonymously()
            result.onSuccess { authData ->
                val guestUser = UserAccountEntity(
                    id = Math.abs(authData.uid.hashCode().toLong()).let { if (it == 0L) System.currentTimeMillis() else it },
                    email = authData.email,
                    fullName = "Guest Scholar",
                    firebaseUid = authData.uid,
                    hasCompletedIntakeSurvey = true,
                    isGoogleUser = false
                )
                val newId = repository.registerUser(guestUser)
                val createdUser = guestUser.copy(id = newId)
                _currentUser.value = createdUser
                val prefs = userPreferences.value ?: UserPreferencesEntity()
                repository.savePreferences(
                    prefs.copy(
                        currentUserId = newId,
                        currentUserEmail = createdUser.email,
                        currentUserName = createdUser.fullName,
                        hasCompletedOnboarding = true,
                        hasCompletedIntakeSurvey = true,
                        isGoogleAuth = false
                    )
                )

                // Persist guest user locally, in guest_data JSON directory, and in Supabase
                AndroidPreferenceSessionManager.appContext?.let { ctx ->
                    AndroidPreferenceSessionManager.setStoredGuestSession(
                        context = ctx,
                        guestId = authData.uid,
                        guestName = createdUser.fullName,
                        guestEmail = createdUser.email
                    )
                }
                com.example.data.GuestDataStorageManager.getInstance(getApplication()).saveGuestProfile(
                    com.example.data.GuestProfileData(
                        guestId = authData.uid,
                        guestName = createdUser.fullName,
                        guestEmail = createdUser.email
                    )
                )

                val guestUserDto = com.example.data.SupabaseUserDto(
                    id = authData.uid,
                    email = authData.email,
                    full_name = "Guest Scholar",
                    has_completed_intake_survey = true
                )
                com.example.data.SupabaseService.getInstance().upsertUserProfile(guestUserDto)

                val guestPrefsDto = com.example.data.SupabaseUserPreferencesDto(
                    user_id = authData.uid,
                    has_completed_onboarding = true
                )
                com.example.data.SupabaseService.getInstance().upsertUserPreferences(guestPrefsDto)

                val guestLeaderboardDto = com.example.data.SupabaseStudyLeaderboardDto(
                    user_id = authData.uid,
                    display_name = "Guest Scholar",
                    study_seconds = 0L,
                    streak = 1,
                    subject_tag = "General Study"
                )
                com.example.data.SupabaseService.getInstance().upsertLeaderboard(guestLeaderboardDto)

                // Guest users bypass onboarding directly to homepage
                _showQuestionnaire.value = false
                _isAuthLoading.value = false
            }.onFailure { ex ->
                // Even if Supabase auth is offline or failed, persist guest session locally in guest_data
                val fallbackUid = "guest_${System.currentTimeMillis()}"
                val guestUser = UserAccountEntity(
                    id = Math.abs(fallbackUid.hashCode().toLong()).coerceAtLeast(1L),
                    email = "guest@focivo.local",
                    fullName = "Guest Scholar",
                    firebaseUid = fallbackUid,
                    hasCompletedIntakeSurvey = true,
                    isGoogleUser = false
                )
                val newId = repository.registerUser(guestUser)
                val createdUser = guestUser.copy(id = newId)
                _currentUser.value = createdUser
                val prefs = userPreferences.value ?: UserPreferencesEntity()
                repository.savePreferences(
                    prefs.copy(
                        currentUserId = newId,
                        currentUserEmail = createdUser.email,
                        currentUserName = createdUser.fullName,
                        hasCompletedOnboarding = true,
                        hasCompletedIntakeSurvey = true,
                        isGoogleAuth = false
                    )
                )
                AndroidPreferenceSessionManager.appContext?.let { ctx ->
                    AndroidPreferenceSessionManager.setStoredGuestSession(
                        context = ctx,
                        guestId = fallbackUid,
                        guestName = createdUser.fullName,
                        guestEmail = createdUser.email
                    )
                }
                com.example.data.GuestDataStorageManager.getInstance(getApplication()).saveGuestProfile(
                    com.example.data.GuestProfileData(
                        guestId = fallbackUid,
                        guestName = createdUser.fullName,
                        guestEmail = createdUser.email
                    )
                )
                // Guest users bypass onboarding directly to homepage
                _showQuestionnaire.value = false
                _isAuthLoading.value = false
            }
        }
    }

    fun submitQuestionnaire(
        name: String,
        age: Int = 16,
        studentClass: String = "Class 11",
        stream: String = "Science (PCM)",
        studySchedule: String = "6:00 PM – 10:00 PM",
        mobileBreakTime: String = "8:00 PM – 8:30 PM",
        goal: String = "Acing College / Academic Studies",
        focusStyle: String = "Deep Flow (50m Focus · 10m Rest)",
        targetHours: Int = 4,
        peakTime: String = "Evening (6 PM - 10 PM)",
        distraction: String = "Social Media & Notifications"
    ) {
        viewModelScope.launch {
            val user = _currentUser.value ?: return@launch
            val updatedUser = user.copy(
                fullName = name,
                studentAge = age,
                studentClass = studentClass,
                studentStream = stream,
                studySchedule = studySchedule,
                mobileBreakTime = mobileBreakTime,
                primaryGoal = goal,
                focusStyle = focusStyle,
                dailyTargetHours = targetHours,
                peakProductivityTime = peakTime,
                primaryDistraction = distraction,
                hasCompletedIntakeSurvey = true
            )
            repository.updateUser(updatedUser)
            _currentUser.value = updatedUser

            val prefs = userPreferences.value ?: UserPreferencesEntity()
            val focusIdentityLabel = "The Scholar of Deep Study"

            repository.savePreferences(
                prefs.copy(
                    currentUserName = name,
                    studentAge = age,
                    studentClass = studentClass,
                    studentStream = stream,
                    studySchedule = studySchedule,
                    mobileBreakTime = mobileBreakTime,
                    dailyGoalMinutes = targetHours * 60,
                    focusIdentity = focusIdentityLabel,
                    isAutoStudyBlockerEnabled = true,
                    preferredFocusMode = if (focusStyle.contains("50m")) "Deep Work" else if (focusStyle.contains("25m")) "Classic" else "Short Sprint",
                    hasCompletedIntakeSurvey = true
                )
            )

            val userIdStr = if (!updatedUser.firebaseUid.isNullOrBlank()) updatedUser.firebaseUid else (SupabaseService.getInstance().getCurrentUserId() ?: updatedUser.id.toString())
            if (userIdStr.isNotBlank()) {
                SupabaseService.getInstance().updateUserSurveyCompleted(
                    userId = userIdStr,
                    isCompleted = true,
                    name = name,
                    studentClassLevel = studentClass,
                    primaryStudyGoal = goal,
                    biggestDistractionApp = distraction,
                    preferredStudyTimeWindow = studySchedule,
                    dailyScreenTimeGoalMinutes = targetHours * 60
                )
            }

            _showQuestionnaire.value = false
        }
    }

    fun submitIntakeSurvey(
        name: String,
        studentClassLevel: String,
        isBoardExamYear: Boolean,
        primaryStudyGoal: String,
        biggestDistractionApp: String,
        preferredStudyTimeWindow: String,
        dailyScreenTimeGoalMinutes: Int,
        motivationStyle: String
    ) {
        viewModelScope.launch {
            val user = _currentUser.value
            val userIdStr = if (!user?.firebaseUid.isNullOrBlank()) user?.firebaseUid ?: "" else (SupabaseService.getInstance().getCurrentUserId() ?: user?.id?.toString() ?: "")
            val updatedUser = user?.copy(
                fullName = name,
                studentClassLevel = studentClassLevel,
                isBoardExamYear = isBoardExamYear,
                primaryStudyGoal = primaryStudyGoal,
                biggestDistractionApp = biggestDistractionApp,
                preferredStudyTimeWindow = preferredStudyTimeWindow,
                dailyScreenTimeGoalMinutes = dailyScreenTimeGoalMinutes,
                motivationStyle = motivationStyle,
                hasCompletedIntakeSurvey = true
            )
            if (updatedUser != null) {
                repository.updateUser(updatedUser)
                _currentUser.value = updatedUser
            }

            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(
                prefs.copy(
                    currentUserName = name,
                    studentClass = studentClassLevel,
                    hasCompletedIntakeSurvey = true,
                    studentClassLevel = studentClassLevel,
                    isBoardExamYear = isBoardExamYear,
                    primaryStudyGoal = primaryStudyGoal,
                    biggestDistractionApp = biggestDistractionApp,
                    preferredStudyTimeWindow = preferredStudyTimeWindow,
                    dailyScreenTimeGoalMinutes = dailyScreenTimeGoalMinutes,
                    motivationStyle = motivationStyle
                )
            )

            if (userIdStr.isNotBlank()) {
                Log.d("FocuslyViewModel", "submitIntakeSurvey: Writing has_completed_intake_survey = true to Supabase for userId=$userIdStr")
                val result = SupabaseService.getInstance().updateUserSurveyCompleted(
                    userId = userIdStr,
                    isCompleted = true,
                    name = name,
                    studentClassLevel = studentClassLevel,
                    isBoardExamYear = isBoardExamYear,
                    primaryStudyGoal = primaryStudyGoal,
                    biggestDistractionApp = biggestDistractionApp,
                    preferredStudyTimeWindow = preferredStudyTimeWindow,
                    dailyScreenTimeGoalMinutes = dailyScreenTimeGoalMinutes,
                    motivationStyle = motivationStyle
                )
                if (result.isSuccess) {
                    Log.i("FocuslyViewModel", "submitIntakeSurvey: Supabase write SUCCESS for has_completed_intake_survey = true")
                } else {
                    Log.e("FocuslyViewModel", "submitIntakeSurvey: Supabase write FAILED: ${result.exceptionOrNull()?.message}")
                }
            }

            _showQuestionnaire.value = false
        }
    }

    fun updateProfileNameAndAvatar(name: String, avatarBytes: ByteArray?) {
        viewModelScope.launch {
            val user = _currentUser.value ?: return@launch
            val supabaseUid = SupabaseService.getInstance().getCurrentUserId()
            val userIdStr = if (!supabaseUid.isNullOrBlank()) supabaseUid else if (!user.firebaseUid.isNullOrBlank()) user.firebaseUid else ""
            var photoUrl = user.photoUrl

            Log.i("FocuslyViewModel", "[AvatarUpdate] BEFORE UPDATE: Updating profile name='$name' and avatar bytes (${avatarBytes?.size ?: 0} bytes) for user_id='$userIdStr'")

            if (avatarBytes != null && userIdStr.isNotBlank()) {
                val uploadedUrl = com.example.data.SupabaseService.getInstance().uploadAvatarImage(userIdStr, avatarBytes)
                if (!uploadedUrl.isNullOrBlank()) {
                    photoUrl = uploadedUrl
                    Log.i("FocuslyViewModel", "[AvatarUpdate] Uploaded avatar successfully: ${photoUrl.take(60)}...")
                } else {
                    val base64 = android.util.Base64.encodeToString(avatarBytes, android.util.Base64.NO_WRAP)
                    photoUrl = "data:image/jpeg;base64,$base64"
                    Log.w("FocuslyViewModel", "[AvatarUpdate] Storage upload returned fallback data URL")
                }
            }

            val updatedUser = user.copy(fullName = name, photoUrl = photoUrl)
            repository.updateUser(updatedUser)
            _currentUser.value = updatedUser

            val prefs = userPreferences.value ?: UserPreferencesEntity()
            val updatedPrefs = prefs.copy(
                currentUserName = name,
                currentUserPhotoUrl = photoUrl
            )
            repository.savePreferences(updatedPrefs)

            if (userIdStr.isNotBlank()) {
                AndroidPreferenceSessionManager.appContext?.let { ctx ->
                    AndroidPreferenceSessionManager.setStoredUserId(ctx, userIdStr)
                }

                if (!photoUrl.isNullOrBlank()) {
                    val avatarResult = com.example.data.SupabaseService.getInstance().updateUserAvatarUrl(userIdStr, photoUrl)
                    Log.i("FocuslyViewModel", "[AvatarUpdate] AFTER UPDATE: Supabase avatar_url update success=${avatarResult.isSuccess}")
                }
                val nameResult = com.example.data.SupabaseService.getInstance().updateUserSurveyCompleted(
                    userId = userIdStr,
                    isCompleted = user.hasCompletedIntakeSurvey,
                    name = name
                )
                Log.i("FocuslyViewModel", "[AvatarUpdate] AFTER UPDATE: Supabase name update success=${nameResult.isSuccess}")

                // Also update leaderboard row with new display name and avatar
                val lbDto = com.example.data.SupabaseStudyLeaderboardDto(
                    user_id = userIdStr,
                    display_name = name,
                    avatar_url = photoUrl,
                    study_seconds = ((allSessions.value.sumOf { it.durationSeconds })).toLong(),
                    streak = updatedPrefs.currentStreak
                )
                com.example.data.SupabaseService.getInstance().upsertLeaderboard(lbDto)
            } else {
                Log.e("FocuslyViewModel", "[AvatarUpdate] ERROR: Could not resolve valid user UUID for Supabase profile update!")
            }
        }
    }

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    fun syncAndLogout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isSigningOut.value = true
            try {
                Log.i("FocuslyViewModel", "[SignOut] Performing pre-logout final Supabase sync...")
                withTimeoutOrNull(6500L) {
                    val uid = SupabaseService.getInstance().getCurrentUserId() ?: _currentUser.value?.firebaseUid
                    if (!uid.isNullOrBlank()) {
                        for (block in _scheduledBlocks.value) {
                            com.example.service.ScheduledBlockScheduler.upsertScheduleLocalAndRemote(
                                getApplication(),
                                block,
                                triggerSource = "SIGN_OUT_SYNC"
                            )
                        }
                    }
                    repository.syncAllLocalDataToSupabase()
                }
                Log.i("FocuslyViewModel", "[SignOut] Supabase sync completed. Proceeding with clean signOut.")
            } catch (e: Exception) {
                Log.w("FocuslyViewModel", "[SignOut] Exception during pre-logout sync: ${e.message}")
            } finally {
                authManager.signOut()
                repository.clearAllData()
                _currentUser.value = null
                _scheduledBlocks.value = emptyList()
                _alarms.value = emptyList()
                _showQuestionnaire.value = false
                _verificationMessage.value = null
                _isSigningOut.value = false
                onComplete()
            }
        }
    }

    fun logout() {
        syncAndLogout()
    }

    fun isLiveFirebaseConfigured(): Boolean = authManager.isLiveFirebaseConfigured()
    fun getActiveFirebaseProjectId(): String = authManager.getActiveProjectId()
    fun getActiveFirebaseApiKey(): String = authManager.getActiveApiKey()

    fun updateFirebaseConfig(apiKey: String, projectId: String, appId: String? = null): Boolean {
        return authManager.saveFirebaseConfig(apiKey, projectId, appId)
    }

    fun clearFirebaseConfig() {
        authManager.clearFirebaseConfig()
    }

    private val _verificationMessage = MutableStateFlow<String?>(null)
    val verificationMessage: StateFlow<String?> = _verificationMessage.asStateFlow()

    fun resendVerificationEmail() {
        viewModelScope.launch {
            val result = authManager.resendVerificationEmail()
            result.onSuccess {
                _verificationMessage.value = "Verification email sent! Please check your inbox or spam."
            }.onFailure { ex ->
                _verificationMessage.value = ex.localizedMessage ?: "Failed to send verification email."
            }
        }
    }

    fun checkEmailVerification() {
        viewModelScope.launch {
            val isVerified = authManager.reloadAndCheckEmailVerification()
            if (isVerified) {
                val user = _currentUser.value
                if (user != null) {
                    val updated = user.copy(isEmailVerified = true)
                    repository.updateUser(updated)
                    _currentUser.value = updated
                }
                _verificationMessage.value = "Email verified successfully! Welcome to Focivo."
            } else {
                _verificationMessage.value = "Email is not verified yet. Please check your inbox or tap Resend."
            }
        }
    }

    fun clearVerificationMessage() {
        _verificationMessage.value = null
    }

    private fun startAlarmChecker() {
        alarmCheckerJob?.cancel()
        alarmCheckerJob = viewModelScope.launch {
            while (isActive) {
                val cal = Calendar.getInstance()
                val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                val currentMinute = cal.get(Calendar.MINUTE)
                val currentSecond = cal.get(Calendar.SECOND)

                // Check on the minute boundary
                if (currentMinute != lastTriggeredMinute && currentSecond < 5) {
                    val matchingAlarm = _alarms.value.firstOrNull { it.isEnabled && it.hour == currentHour && it.minute == currentMinute }
                    if (matchingAlarm != null && !_isAlarmRinging.value) {
                        lastTriggeredMinute = currentMinute
                        triggerAlarm(matchingAlarm)
                    }
                }

                delay(1000L)
            }
        }
    }

    fun triggerAlarm(alarm: AlarmItem) {
        _ringingAlarm.value = alarm
        _isAlarmRinging.value = true
        alarmAudioEngine.startAlarm(alarm.ringtone)
    }

    fun triggerTestAlarm(alarm: AlarmItem? = null) {
        val target = alarm ?: _alarms.value.firstOrNull() ?: AlarmItem(
            label = "Test Alarm",
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            minute = Calendar.getInstance().get(Calendar.MINUTE),
            ringtone = userPreferences.value?.alarmRingtone ?: "Zen Bell"
        )
        triggerAlarm(target)
    }

    fun silenceAlarmRingtone() {
        alarmAudioEngine.stopAlarm()
        com.example.service.AlarmNotificationHelper.silenceAudio(getApplication())
    }

    fun dismissAlarm() {
        _isAlarmRinging.value = false
        _ringingAlarm.value = null
        alarmAudioEngine.stopAlarm()
        com.example.service.AlarmNotificationHelper.stopAlarm(getApplication())
    }

    fun snoozeAlarm(minutes: Int = 5) {
        val current = _ringingAlarm.value
        dismissAlarm()
        if (current != null) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MINUTE, minutes)
            }
            val snoozed = current.copy(
                id = UUID.randomUUID().toString(),
                label = "Snoozed: ${current.label}",
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE),
                isEnabled = true,
                isSnoozed = true
            )
            com.example.service.AlarmScheduler.scheduleAlarm(getApplication(), snoozed)
            _alarms.value = com.example.service.AlarmScheduler.getLocalAlarms(getApplication())
        }
    }

    fun refreshAlarms() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val uid = _currentUser.value?.firebaseUid
                ?: SupabaseService.getInstance().getCurrentUserId()

            val dummyLabels = setOf("Morning Deep Focus", "Midday Reset", "Day Review")

            // 1. First populate with local cache, purging any legacy dummy alarms
            val rawLocalAlarms = com.example.service.AlarmScheduler.getLocalAlarms(app)
            val localAlarms = rawLocalAlarms.filterNot { it.label in dummyLabels }
            if (rawLocalAlarms.size != localAlarms.size) {
                rawLocalAlarms.filter { it.label in dummyLabels }.forEach {
                    com.example.service.AlarmScheduler.cancelAlarm(app, it.id)
                }
                com.example.service.AlarmScheduler.saveLocalAlarms(app, localAlarms)
            }
            _alarms.value = localAlarms

            if (!uid.isNullOrBlank()) {
                Log.d(TAG, "[AlarmLoad] Querying SELECT * FROM public.alarms WHERE user_id = '$uid'")
                val remoteDtos = SupabaseService.getInstance().fetchAlarms(uid)
                Log.d(TAG, "[AlarmLoad] Loaded ${remoteDtos.size} alarms from Supabase for uid=$uid: $remoteDtos")

                // Remove legacy dummy alarms from remote database if present
                val (dummyRemote, validRemote) = remoteDtos.partition { it.label in dummyLabels }
                if (dummyRemote.isNotEmpty()) {
                    dummyRemote.forEach { dummy ->
                        try {
                            SupabaseService.getInstance().deleteAlarm(dummy.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error removing dummy alarm from remote: ${e.message}")
                        }
                    }
                }

                if (validRemote.isNotEmpty()) {
                    val remoteAlarms = validRemote.map { dto ->
                        AlarmItem(
                            id = dto.id,
                            label = dto.label,
                            hour = dto.hour,
                            minute = dto.minute,
                            isEnabled = dto.is_enabled,
                            ringtone = dto.ringtone,
                            vibrate = dto.vibrate,
                            daysActive = dto.days_active
                        )
                    }
                    _alarms.value = remoteAlarms
                    com.example.service.AlarmScheduler.saveLocalAlarms(app, remoteAlarms)
                    com.example.service.AlarmScheduler.rescheduleAllEnabled(app, remoteAlarms)
                } else if (localAlarms.isNotEmpty()) {
                    Log.d(TAG, "[AlarmSave] Remote public.alarms is empty for uid=$uid. Syncing ${localAlarms.size} local cached alarms to Supabase")
                    localAlarms.forEach { local ->
                        val dto = com.example.data.SupabaseAlarmDto(
                            id = local.id,
                            user_id = uid,
                            label = local.label,
                            hour = local.hour,
                            minute = local.minute,
                            days_active = local.daysActive,
                            ringtone = local.ringtone,
                            is_enabled = local.isEnabled,
                            vibrate = local.vibrate
                        )
                        SupabaseService.getInstance().upsertAlarm(dto)
                    }
                } else {
                    Log.d(TAG, "[AlarmLoad] No alarms found for user $uid")
                    _alarms.value = emptyList()
                    com.example.service.AlarmScheduler.saveLocalAlarms(app, emptyList())
                }
            } else {
                Log.d(TAG, "[AlarmLoad] Unauthenticated; using local alarms cache (${_alarms.value.size} items)")
                com.example.service.AlarmScheduler.rescheduleAllEnabled(app, _alarms.value)
            }
        }
    }

    fun addAlarm(hour: Int, minute: Int, label: String, ringtone: String, vibrate: Boolean) {
        val app = getApplication<Application>()
        val uid = _currentUser.value?.firebaseUid ?: SupabaseService.getInstance().getCurrentUserId() ?: "local_user"
        Log.d(TAG, "[AlarmSave] User tapped save: hour=$hour, minute=$minute, label='$label', ringtone='$ringtone', vibrate=$vibrate, uid=$uid")

        val newAlarm = AlarmItem(
            hour = hour,
            minute = minute,
            label = if (label.isBlank()) "Focus Alarm" else label,
            ringtone = ringtone,
            vibrate = vibrate,
            isEnabled = true
        )

        val updated = _alarms.value + newAlarm
        _alarms.value = updated

        // 1. Save local cache & schedule with System AlarmManager
        com.example.service.AlarmScheduler.saveLocalAlarms(app, updated)
        com.example.service.AlarmScheduler.scheduleAlarm(app, newAlarm)

        // 2. Async Supabase upsert
        viewModelScope.launch {
            if (uid != "local_user") {
                val dto = com.example.data.SupabaseAlarmDto(
                    id = newAlarm.id,
                    user_id = uid,
                    label = newAlarm.label,
                    hour = newAlarm.hour,
                    minute = newAlarm.minute,
                    days_active = newAlarm.daysActive,
                    ringtone = newAlarm.ringtone,
                    is_enabled = newAlarm.isEnabled,
                    vibrate = newAlarm.vibrate
                )
                Log.d(TAG, "[AlarmSave] Sending upsert to public.alarms for dto=$dto")
                val res = SupabaseService.getInstance().upsertAlarm(dto)
                if (res.isSuccess) {
                    Log.d(TAG, "[AlarmSave] Supabase upsert result: SUCCESS for id=${newAlarm.id}")
                } else {
                    Log.e(TAG, "[AlarmSave] Supabase upsert result: FAILED for id=${newAlarm.id}: ${res.exceptionOrNull()?.message}")
                }
            } else {
                Log.w(TAG, "[AlarmSave] User not logged in to Supabase; alarm saved to local cache only")
            }
        }
    }

    fun toggleAlarm(alarmId: String) {
        val app = getApplication<Application>()
        val uid = _currentUser.value?.firebaseUid ?: SupabaseService.getInstance().getCurrentUserId() ?: "local_user"
        Log.d(TAG, "[AlarmToggle] User toggled alarm id=$alarmId")

        val updated = _alarms.value.map {
            if (it.id == alarmId) it.copy(isEnabled = !it.isEnabled) else it
        }
        _alarms.value = updated

        com.example.service.AlarmScheduler.saveLocalAlarms(app, updated)
        val toggledItem = updated.firstOrNull { it.id == alarmId }
        if (toggledItem != null) {
            if (toggledItem.isEnabled) {
                com.example.service.AlarmScheduler.scheduleAlarm(app, toggledItem)
            } else {
                com.example.service.AlarmScheduler.cancelAlarm(app, alarmId)
            }

            viewModelScope.launch {
                if (uid != "local_user") {
                    val dto = com.example.data.SupabaseAlarmDto(
                        id = toggledItem.id,
                        user_id = uid,
                        label = toggledItem.label,
                        hour = toggledItem.hour,
                        minute = toggledItem.minute,
                        days_active = toggledItem.daysActive,
                        ringtone = toggledItem.ringtone,
                        is_enabled = toggledItem.isEnabled,
                        vibrate = toggledItem.vibrate
                    )
                    SupabaseService.getInstance().upsertAlarm(dto)
                }
            }
        }
    }

    fun deleteAlarm(alarmId: String) {
        val app = getApplication<Application>()
        val uid = _currentUser.value?.firebaseUid ?: SupabaseService.getInstance().getCurrentUserId() ?: "local_user"
        Log.d(TAG, "[AlarmDelete] User deleted alarm id=$alarmId")

        val updated = _alarms.value.filter { it.id != alarmId }
        _alarms.value = updated

        com.example.service.AlarmScheduler.saveLocalAlarms(app, updated)
        com.example.service.AlarmScheduler.cancelAlarm(app, alarmId)

        viewModelScope.launch {
            if (uid != "local_user") {
                SupabaseService.getInstance().deleteAlarm(alarmId)
            }
        }
    }

    fun previewRingtone(ringtone: String) {
        alarmAudioEngine.togglePreview(ringtone) { activeRingtone ->
            _currentlyPreviewingRingtone.value = activeRingtone
        }
    }

    fun stopPreview() {
        alarmAudioEngine.stopPreview()
        _currentlyPreviewingRingtone.value = null
    }

    fun updateSessionAlarmRingtone(ringtone: String) {
        viewModelScope.launch {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(prefs.copy(alarmRingtone = ringtone))
        }
    }

    fun setMode(mode: String, durationMinutes: Int) {
        _currentMode.value = mode
        val seconds = durationMinutes * 60
        _targetSeconds.value = seconds
        _remainingSeconds.value = seconds
        _isBreakMode.value = false
    }

    fun setCurrentTaskTitle(title: String) {
        _currentTaskTitle.value = title
    }

    fun setAmbientSound(sound: String) {
        _ambientSound.value = sound
        if (_isTimerRunning.value) {
            audioEngine.play(sound)
        }
    }

    private val _showPreStudyBlockSheet = MutableStateFlow(false)
    val showPreStudyBlockSheet: StateFlow<Boolean> = _showPreStudyBlockSheet.asStateFlow()

    fun requestStudySessionStart() {
        if (_isTimerRunning.value) {
            pauseTimer()
            return
        }
        val prefs = userPreferences.value ?: com.example.data.model.UserPreferencesEntity()
        // If user clicked "Save & Block", auto-block saved apps without asking. Otherwise show selection sheet.
        if (prefs.autoBlockStudyAppsWithoutAsking && prefs.blockedAppsList.isNotBlank()) {
            startTimer()
        } else {
            _showPreStudyBlockSheet.value = true
        }
    }

    fun dismissPreStudyBlockSheet() {
        _showPreStudyBlockSheet.value = false
    }

    fun confirmPreStudyBlock(selectedPackages: Set<String>, saveForFuture: Boolean) {
        val app = getApplication<Application>()
        val sanitized = com.example.util.EssentialAppsGuard.sanitizeBlockedPackages(app, selectedPackages)
        val joined = sanitized.joinToString(",")
        val currentPrefs = userPreferences.value ?: com.example.data.model.UserPreferencesEntity()
        val updated = currentPrefs.copy(
            blockedAppsList = if (saveForFuture) joined else currentPrefs.blockedAppsList,
            isAppBlockerEnabled = true,
            autoBlockStudyAppsWithoutAsking = saveForFuture
        )
        viewModelScope.launch {
            repository.savePreferences(updated)
        }
        _showPreStudyBlockSheet.value = false
        startTimer(overrideBlockedList = joined)
    }

    fun startTimer(overrideBlockedList: String? = null) {
        if (_isTimerRunning.value) return
        _isTimerRunning.value = true
        if (sessionStartTimeMs == 0L) {
            sessionStartTimeMs = System.currentTimeMillis()
        }
        audioEngine.play(_ambientSound.value)

        val app = getApplication<Application>()

        // 1. Auto-populate task title with subject from active schedule if not custom-named
        val activeSchedule = ScheduledBlockScheduler.getActiveStudySchedule(app)
        if (!activeSchedule?.subject.isNullOrBlank() && (
                _currentTaskTitle.value.isBlank() ||
                _currentTaskTitle.value == "Deep Work Session" ||
                _currentTaskTitle.value == "Deep Study" ||
                _currentTaskTitle.value == "Focus Session" ||
                _currentTaskTitle.value.startsWith("Scheduled Study:")
            )) {
            _currentTaskTitle.value = activeSchedule!!.subject!!
        }

        // Save manual timer state to local prefs for real-time AI Guard checking
        try {
            val prefs = app.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
            val endTime = System.currentTimeMillis() + (_remainingSeconds.value * 1000L)
            prefs.edit()
                .putBoolean("is_manual_timer_running", true)
                .putLong("manual_timer_end_time_ms", endTime)
                .apply()
            com.example.util.StrictModeManager.setSessionActive(app, true)

            val tPrefs = app.getSharedPreferences("focusly_timer_state", Context.MODE_PRIVATE)
            tPrefs.edit()
                .putBoolean("is_timer_running", true)
                .putInt("remaining_seconds", _remainingSeconds.value)
                .putString("current_task_title", _currentTaskTitle.value)
                .apply()
            com.example.widget.FocivoWidgetHelper.updateAllWidgets(app)
        } catch (_: Exception) {}

        // 2. Evaluate permissions and auto-activate Focus Shield & AI Study Guard
        val hasUsage = com.example.ui.components.FocusShieldPermissions.hasUsageStatsPermission(app)
        val hasAccessibility = com.example.util.AiStudyGuardManager.isAccessibilityPermissionGranted(app)
        val hasBlockerPermission = hasUsage || hasAccessibility

        val currentPrefs = userPreferences.value ?: com.example.data.model.UserPreferencesEntity()
        val effectiveBlockedRaw = overrideBlockedList ?: currentPrefs.blockedAppsList
        val blockedApps = com.example.util.EssentialAppsGuard.sanitizeBlockedPackages(
            app,
            effectiveBlockedRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        )
        val hasBlockedList = blockedApps.isNotEmpty()

        var blockerActive = false
        var aiGuardActive = false
        var blockedCount = 0

        didSessionAutoEnableAppBlocker = false
        didSessionAutoEnableAiGuard = false

        // Auto-activate App Blocker Shield
        if (hasBlockerPermission && hasBlockedList) {
            blockedCount = blockedApps.size
            blockerActive = true
            if (!currentPrefs.isAppBlockerEnabled) {
                didSessionAutoEnableAppBlocker = true
                viewModelScope.launch {
                    repository.savePreferences(currentPrefs.copy(isAppBlockerEnabled = true, blockedAppsList = blockedApps.joinToString(",")))
                }
            }
            val serviceIntent = Intent(app, FocusShieldService::class.java).apply {
                action = FocusShieldService.ACTION_START_SHIELD
                putExtra(FocusShieldService.EXTRA_DURATION_SECONDS, _targetSeconds.value)
                putExtra(FocusShieldService.EXTRA_BLOCKED_LIST, blockedApps.joinToString(","))
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(serviceIntent)
                } else {
                    app.startService(serviceIntent)
                }
            } catch (_: Exception) {}
        } else if (currentPrefs.isAppBlockerEnabled && hasBlockedList) {
            blockedCount = blockedApps.size
            blockerActive = true
            val serviceIntent = Intent(app, FocusShieldService::class.java).apply {
                action = FocusShieldService.ACTION_START_SHIELD
                putExtra(FocusShieldService.EXTRA_DURATION_SECONDS, _targetSeconds.value)
                putExtra(FocusShieldService.EXTRA_BLOCKED_LIST, blockedApps.joinToString(","))
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(serviceIntent)
                } else {
                    app.startService(serviceIntent)
                }
            } catch (_: Exception) {}
        }

        // Auto-activate AI Study Guard
        if (hasAccessibility) {
            if (!com.example.util.AiStudyGuardManager.isAiGuardEnabled(app)) {
                didSessionAutoEnableAiGuard = true
                com.example.util.AiStudyGuardManager.setAiGuardEnabled(app, true)
            }
            aiGuardActive = true
            com.example.util.AiStudyGuardManager.updateGuardStatusNotification(app)
        }

        // Auto-activate Deep Focus Task Locking if enabled
        val isDeepFocusOn = com.example.util.DeepFocusManager.isDeepFocusEnabled(app)
        if (isDeepFocusOn) {
            com.example.util.DeepFocusManager.setDeepFocusSessionActive(app, true)
        }

        // 3. Non-blocking inline note if permissions missing
        if (!hasBlockerPermission || !hasBlockedList) {
            _sessionProtectionNote.value = "App blocking is off — enable Focus Shield in Settings for full protection"
        } else if (!hasAccessibility) {
            _sessionProtectionNote.value = "AI Guard is off — enable Accessibility in Settings for full protection"
        } else {
            _sessionProtectionNote.value = null
        }

        // 4. Combined confirmation banner: e.g. "Session started · 12 apps blocked · AI Guard active · Deep Focus locked"
        val parts = mutableListOf<String>()
        parts.add("Session started")
        if (blockerActive && blockedCount > 0) {
            parts.add("$blockedCount apps blocked")
        }
        if (aiGuardActive) {
            parts.add("AI Guard active")
        }
        if (isDeepFocusOn) {
            parts.add("Deep Focus locked")
        }
        val bannerMsg = parts.joinToString(" · ")
        _sessionStartConfirmation.value = bannerMsg
        viewModelScope.launch {
            delay(4000L)
            if (_sessionStartConfirmation.value == bannerMsg) {
                _sessionStartConfirmation.value = null
            }
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _remainingSeconds.value > 0) {
                delay(1000L)
                _remainingSeconds.value -= 1
            }
            if (_remainingSeconds.value <= 0) {
                onTimerCompleted()
            }
        }
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        if (sessionStartTimeMs > 0L) {
            val elapsed = ((System.currentTimeMillis() - sessionStartTimeMs) / 1000L).toInt()
            accumulatedSessionElapsedSecs += elapsed.coerceAtLeast(0)
            sessionStartTimeMs = 0L
        }
        timerJob?.cancel()
        timerJob = null
        audioEngine.stop()

        val app = getApplication<Application>()
        try {
            val prefs = app.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_manual_timer_running", false)
                .putLong("manual_timer_end_time_ms", 0L)
                .apply()
            com.example.util.StrictModeManager.setSessionActive(app, false)
            com.example.util.DeepFocusManager.setDeepFocusSessionActive(app, false)

            val tPrefs = app.getSharedPreferences("focusly_timer_state", Context.MODE_PRIVATE)
            tPrefs.edit()
                .putBoolean("is_timer_running", false)
                .putInt("remaining_seconds", _remainingSeconds.value)
                .apply()
            com.example.widget.FocivoWidgetHelper.updateAllWidgets(app)
        } catch (_: Exception) {}

        // Deactivate protections started for this session UNLESS an Auto Study Schedule is actively running
        val isScheduleActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(app)

        if (!isScheduleActive) {
            if (didSessionAutoEnableAppBlocker) {
                didSessionAutoEnableAppBlocker = false
                val currentPrefs = userPreferences.value ?: com.example.data.model.UserPreferencesEntity()
                viewModelScope.launch {
                    repository.savePreferences(currentPrefs.copy(isAppBlockerEnabled = false))
                }
            }

            val serviceIntent = Intent(app, FocusShieldService::class.java).apply {
                action = FocusShieldService.ACTION_STOP_SHIELD
            }
            try {
                app.startService(serviceIntent)
            } catch (_: Exception) {}

            if (didSessionAutoEnableAiGuard) {
                didSessionAutoEnableAiGuard = false
                com.example.util.AiStudyGuardManager.setAiGuardEnabled(app, false)
            }
            com.example.util.AiStudyGuardManager.resetSessionCounters()
            com.example.util.AiStudyGuardManager.updateGuardStatusNotification(app)
        } else {
            Log.d(TAG, "Auto Study Schedule is active — maintaining shield and AI Guard")
        }
        _sessionProtectionNote.value = null
    }

    fun resumeTimer() {
        startTimer()
    }

    fun addFiveMinutes() {
        _remainingSeconds.value += 300
        _targetSeconds.value += 300
    }

    fun finishSessionEarly() {
        onTimerCompleted()
    }

    fun skipBreak() {
        pauseTimer()
        _isBreakMode.value = false
        setMode(_currentMode.value, _targetSeconds.value / 60)
    }

    private fun calculateConsecutiveStreak(sessions: List<FocusSessionEntity>): Pair<Int, Int> {
        if (sessions.isEmpty()) return Pair(0, 0)

        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sessionDays = sessions.map { dayFormat.format(Date(it.completedAt)) }.toSet()

        val cal = Calendar.getInstance()
        val todayStr = dayFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = dayFormat.format(cal.time)

        var currentStreak = 0
        if (sessionDays.contains(todayStr) || sessionDays.contains(yesterdayStr)) {
            val checkCal = Calendar.getInstance()
            if (!sessionDays.contains(todayStr)) {
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            }
            while (sessionDays.contains(dayFormat.format(checkCal.time))) {
                currentStreak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            }
        }

        val sortedDates = sessionDays.mapNotNull {
            try { dayFormat.parse(it) } catch (_: Exception) { null }
        }.sorted()

        var maxStreak = currentStreak
        var streakCount = 0
        var lastDate: Date? = null

        for (d in sortedDates) {
            if (lastDate == null) {
                streakCount = 1
            } else {
                val diffMs = d.time - lastDate.time
                val diffDays = (diffMs / (24 * 60 * 60 * 1000L)).toInt()
                if (diffDays == 1) {
                    streakCount++
                } else if (diffDays > 1) {
                    streakCount = 1
                }
            }
            lastDate = d
            if (streakCount > maxStreak) {
                maxStreak = streakCount
            }
        }

        val finalCurrent = if (sessionDays.contains(todayStr)) currentStreak.coerceAtLeast(1) else currentStreak
        return Pair(finalCurrent, maxStreak.coerceAtLeast(finalCurrent))
    }

    private fun onTimerCompleted() {
        pauseTimer()
        audioEngine.playCompletionChime()

        if (userPreferences.value?.isAppBlockerEnabled == true) {
            val app = getApplication<Application>()
            val serviceIntent = Intent(app, FocusShieldService::class.java).apply {
                action = FocusShieldService.ACTION_STOP_SHIELD
            }
            try {
                app.startService(serviceIntent)
            } catch (_: Exception) {}
            audioEngine.playShieldUnlockChime()
        }

        val durationSpent = _targetSeconds.value - _remainingSeconds.value
        val actualSeconds = when {
            accumulatedSessionElapsedSecs > 0 -> accumulatedSessionElapsedSecs.coerceIn(1, (_targetSeconds.value * 3).coerceAtLeast(300))
            durationSpent > 0 -> durationSpent
            else -> _targetSeconds.value.coerceAtLeast(60)
        }
        accumulatedSessionElapsedSecs = 0
        sessionStartTimeMs = 0L

        val pointsEarned = maxOf(1, actualSeconds / 60)

        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
        val mappedDayOfWeek = if (dayOfWeek == 1) 7 else dayOfWeek - 1 // 1=Mon, 7=Sun
        val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)

        val session = FocusSessionEntity(
            taskTitle = _currentTaskTitle.value,
            durationSeconds = actualSeconds,
            targetDurationSeconds = _targetSeconds.value,
            mode = _currentMode.value,
            distractionsCount = _distractionsCount.value,
            distractionTypes = _loggedDistractions.value.joinToString(", "),
            focusPointsEarned = pointsEarned,
            completedAt = System.currentTimeMillis(),
            dayOfWeek = mappedDayOfWeek,
            hourOfDay = hourOfDay,
            notes = _sessionNotes.value
        )

        viewModelScope.launch {
            repository.recordSession(session)
            // Update points and streak in preferences
            val prefs = userPreferences.value ?: UserPreferencesEntity()

            val allPast = repository.allSessions.firstOrNull() ?: emptyList()
            val totalSessions = allPast + session
            val (calcCurrent, calcBest) = calculateConsecutiveStreak(totalSessions)
            val currentStreak = calcCurrent
            val bestStreak = maxOf(prefs.bestStreak, calcBest, currentStreak)

            val updatedTotalFocusMinutes = totalSessions.sumOf { it.durationSeconds } / 60
            val updatedPoints = totalSessions.sumOf { it.focusPointsEarned }
            val newGrowthStage = when {
                updatedTotalFocusMinutes >= 600 -> 3
                updatedTotalFocusMinutes >= 120 -> 2
                else -> 1
            }.coerceAtMost(3)

            val updatedLevel = newGrowthStage

            val updatedPrefs = prefs.copy(
                focusPoints = updatedPoints,
                userLevel = updatedLevel,
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                buddyTotalFocusMinutes = updatedTotalFocusMinutes,
                buddyGrowthStage = newGrowthStage
            )
            repository.savePreferences(updatedPrefs)

            // Real-time Supabase sync for points, streak, and study duration
            val uid = SupabaseService.getInstance().getCurrentUserId()
                ?: _currentUser.value?.firebaseUid
                ?: AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.getStoredUserId(it) }

            if (!uid.isNullOrBlank()) {
                val totalSeconds = totalSessions.sumOf { it.durationSeconds }
                val avatarUrl = updatedPrefs.currentUserPhotoUrl
                    ?: AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.getStoredAvatarUrl(it) }

                val leaderboardDto = com.example.data.SupabaseStudyLeaderboardDto(
                    user_id = uid,
                    display_name = updatedPrefs.currentUserName ?: "Student",
                    study_seconds = totalSeconds.toLong().coerceAtLeast(1L),
                    streak = currentStreak.coerceAtLeast(1),
                    subject_tag = _currentTaskTitle.value.ifBlank { "General Study" },
                    avatar_url = avatarUrl
                )

                Log.i("FocuslyViewModel", "[FocusPointsSync] BEFORE WRITE: Session finished! Earned $pointsEarned pts for user_id=$uid. Updating user_preferences focus_points=$updatedPoints, buddy_minutes=$updatedTotalFocusMinutes, current_streak=$currentStreak, level=$updatedLevel")

                val lbResult = SupabaseService.getInstance().upsertLeaderboard(leaderboardDto)
                Log.i("FocuslyViewModel", "[FocusPointsSync] AFTER WRITE (Leaderboard): status=${lbResult.isSuccess}")

                if (!avatarUrl.isNullOrBlank()) {
                    SupabaseService.getInstance().updateUserAvatarUrl(uid, avatarUrl)
                }
            } else {
                Log.e("FocuslyViewModel", "[FocusPointsSync] ERROR: Cannot sync focus_points to Supabase — user_id is blank!")
            }

            // Real-time Firebase sync for completed study session
            val user = _currentUser.value
            if (user != null) {
                firebaseSyncManager.syncSession(
                    session = session,
                    totalPoints = updatedPoints,
                    streak = currentStreak
                )
            }
        }

        _completedSessionSummary.value = session
        _showCompletionScreen.value = true

        // Reset timer
        _remainingSeconds.value = _targetSeconds.value
        _distractionsCount.value = 0
        _loggedDistractions.value = emptyList()
        _sessionNotes.value = ""

        try {
            val tPrefs = getApplication<Application>().getSharedPreferences("focusly_timer_state", Context.MODE_PRIVATE)
            tPrefs.edit()
                .putBoolean("is_timer_running", false)
                .putInt("remaining_seconds", _targetSeconds.value)
                .apply()
            com.example.widget.FocivoWidgetHelper.updateAllWidgets(getApplication())
        } catch (_: Exception) {}
    }

    // App Blocker / Focus Shield Functions
    fun openShieldHub() {
        refreshInstalledApps()
        _isShieldHubOpen.value = true
    }

    fun closeShieldHub() {
        _isShieldHubOpen.value = false
    }

    fun dismissShieldOverlay() {
        _isShieldOverlayVisible.value = false
        _shieldInterceptReason.value = null
        _isGeminiBlocked.value = false
    }

    fun triggerShieldIntercept(appName: String, reason: String? = null, isGeminiBlocked: Boolean = false) {
        _shieldInterceptedAppName.value = appName
        _shieldInterceptReason.value = reason
        _isGeminiBlocked.value = isGeminiBlocked
        _isShieldOverlayVisible.value = true
        if (_isTimerRunning.value) {
            _distractionsCount.value += 1
        }
        viewModelScope.launch {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(
                prefs.copy(shieldBlockedAttempts = prefs.shieldBlockedAttempts + 1)
            )
        }
    }

    private var isAutoScheduledSession = false

    fun syncShieldStateWithService() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val isShieldRunning = FocusShieldService.isShieldRunning(app)
            val isScheduleActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(app)
            val activeStudySchedule = ScheduledBlockScheduler.getActiveStudySchedule(app)
            val remainingSecs = FocusShieldService.getRemainingSeconds(app)
            val prefs = userPreferences.value ?: repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()

            // Auto-start a study timer session when a schedule with mode="Study" becomes active
            if (activeStudySchedule != null) {
                val studyRemaining = ScheduledBlockScheduler.getSecondsRemainingInActiveBlock(app, activeStudySchedule)
                if (studyRemaining > 10 && (!_isTimerRunning.value || isAutoScheduledSession)) {
                    if (!isAutoScheduledSession) {
                        _shouldNavigateToStudyTab.value = true
                    }
                    _isTimerRunning.value = true
                    _remainingSeconds.value = studyRemaining
                    _targetSeconds.value = studyRemaining
                    _currentTaskTitle.value = if (!activeStudySchedule.subject.isNullOrBlank()) {
                        activeStudySchedule.subject!!
                    } else {
                        "Scheduled Study: ${activeStudySchedule.label}"
                    }
                    isAutoScheduledSession = true

                    try {
                        val localPrefs = app.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
                        val endTime = System.currentTimeMillis() + (studyRemaining * 1000L)
                        localPrefs.edit()
                            .putBoolean("is_manual_timer_running", true)
                            .putLong("manual_timer_end_time_ms", endTime)
                            .apply()
                    } catch (_: Exception) {}

                    if (timerJob == null || !timerJob!!.isActive) {
                        timerJob = viewModelScope.launch {
                            while (isActive && _remainingSeconds.value > 0) {
                                delay(1000L)
                                _remainingSeconds.value -= 1
                            }
                            if (_remainingSeconds.value <= 0) {
                                onTimerCompleted()
                                isAutoScheduledSession = false
                            }
                        }
                    }
                }
            } else if (isAutoScheduledSession) {
                // Scheduled study window finished or break started
                onTimerCompleted()
                isAutoScheduledSession = false
            }

            if ((isShieldRunning || isScheduleActive) && remainingSecs > 0) {
                _isStandaloneShieldActive.value = true
                _standaloneShieldRemainingSeconds.value = remainingSecs
                if (standaloneShieldJob == null || !standaloneShieldJob!!.isActive) {
                    startShieldTicker(remainingSecs)
                }
                if (!prefs.isAppBlockerEnabled) {
                    repository.savePreferences(prefs.copy(isAppBlockerEnabled = true))
                }
            } else if (!isShieldRunning && !isScheduleActive) {
                if (_isStandaloneShieldActive.value && _standaloneShieldRemainingSeconds.value <= 0) {
                    _isStandaloneShieldActive.value = false
                    standaloneShieldJob?.cancel()
                    standaloneShieldJob = null
                }
            }
        }
    }

    fun toggleMasterShield(enabled: Boolean) {
        val app = getApplication<Application>()
        val isPunishment = FocusShieldService.isPunishmentLock.value ||
                com.example.util.AiStudyGuardManager.isAppUnderPunishment(app, "com.openai.chatgpt") ||
                com.example.util.AiStudyGuardManager.isAppUnderPunishment(app, "com.anthropic.claude")

        if (!enabled && isPunishment) {
            // Strict lock active: cannot turn off master shield while punishment is ongoing
            return
        }

        viewModelScope.launch {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(prefs.copy(isAppBlockerEnabled = enabled))

            if (!enabled) {
                val serviceIntent = Intent(app, FocusShieldService::class.java).apply {
                    action = FocusShieldService.ACTION_STOP_SHIELD
                }
                try {
                    app.startService(serviceIntent)
                } catch (_: Exception) {}
                _isStandaloneShieldActive.value = false
                _standaloneShieldRemainingSeconds.value = 0
                standaloneShieldJob?.cancel()
                standaloneShieldJob = null
            } else {
                // Distraction blocker is enabled and armed for study sessions.
                // Background service & notifications are NOT started when user is not studying.
                _isStandaloneShieldActive.value = false
                _standaloneShieldRemainingSeconds.value = 0
                standaloneShieldJob?.cancel()
                standaloneShieldJob = null
            }

            firebaseSyncManager.syncBlockerState(
                isArmed = enabled,
                endTime = prefs.appBlockerEndTime,
                blockedCount = prefs.blockedAppsList.split(",").filter { it.isNotBlank() }.size
            )
        }
    }

    private fun isEssentialOrEmergencyPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("dialer") ||
               lower.contains("phone") ||
               lower.contains("contacts") ||
               lower.contains("telephony") ||
               lower.contains("emergency") ||
               lower == "com.android.dialer" ||
               lower == "com.google.android.dialer" ||
               lower == "com.samsung.android.dialer" ||
               lower == "com.android.contacts" ||
               lower == "com.openai.chatgpt" ||
               lower == "com.anthropic.claude" ||
               lower == "com.google.android.youtube" ||
               lower == "com.android.chrome"
    }

    fun toggleBlockedPackage(packageName: String, isBlocked: Boolean) {
        if (isBlocked && isEssentialOrEmergencyPackage(packageName) && (packageName.contains("dialer") || packageName.contains("phone") || packageName.contains("contacts"))) {
            Log.w("FocuslyViewModel", "Emergency/phone app $packageName cannot be blocked.")
            return
        }
        viewModelScope.launch {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            val currentList = prefs.blockedAppsList.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toMutableSet()
            if (isBlocked) {
                currentList.add(packageName)
            } else {
                currentList.remove(packageName)
            }
            val updatedListStr = currentList.joinToString(",")
            repository.savePreferences(prefs.copy(blockedAppsList = updatedListStr))

            // Update installed apps state flow immediately
            _installedApps.value = _installedApps.value.map {
                if (it.packageName == packageName) it.copy(isBlocked = isBlocked) else it
            }

            // Sync with Firebase Cloud
            firebaseSyncManager.syncBlockerState(
                isArmed = _isStandaloneShieldActive.value || prefs.isAppBlockerEnabled,
                endTime = prefs.appBlockerEndTime,
                blockedCount = currentList.size
            )
        }
    }

    fun blockAllSocialApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            val currentList = prefs.blockedAppsList.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toMutableSet()

            _installedApps.value.filter { app ->
                !isEssentialOrEmergencyPackage(app.packageName) && (
                    app.category.contains("Social", ignoreCase = true) ||
                    app.category.contains("Game", ignoreCase = true) ||
                    app.category.contains("Media", ignoreCase = true)
                )
            }.forEach {
                currentList.add(it.packageName)
            }
            val updatedListStr = currentList.joinToString(",")
            repository.savePreferences(prefs.copy(blockedAppsList = updatedListStr))
            val blockedSet = currentList.toSet()
            _installedApps.value = _installedApps.value.map {
                it.copy(isBlocked = blockedSet.contains(it.packageName))
            }
            firebaseSyncManager.syncBlockerState(
                isArmed = _isStandaloneShieldActive.value || prefs.isAppBlockerEnabled,
                endTime = prefs.appBlockerEndTime,
                blockedCount = currentList.size
            )
        }
    }

    fun unblockAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(prefs.copy(blockedAppsList = ""))
            _installedApps.value = _installedApps.value.map { it.copy(isBlocked = false) }
            firebaseSyncManager.syncBlockerState(
                isArmed = false,
                endTime = 0L,
                blockedCount = 0
            )
        }
    }

    fun startStandaloneShield(durationMinutes: Int) {
        standaloneShieldJob?.cancel()
        _isStandaloneShieldActive.value = true
        val seconds = durationMinutes * 60
        _standaloneShieldRemainingSeconds.value = seconds
        val endTime = System.currentTimeMillis() + (seconds * 1000L)

        viewModelScope.launch {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(
                prefs.copy(
                    isAppBlockerEnabled = true,
                    appBlockerEndTime = endTime,
                    appBlockerDurationMinutes = durationMinutes
                )
            )
            // Push real-time state to Firebase
            val blockedCount = prefs.blockedAppsList.split(",").filter { it.isNotBlank() }.size
            firebaseSyncManager.syncBlockerState(
                isArmed = true,
                endTime = endTime,
                blockedCount = blockedCount
            )
        }

        val app = getApplication<Application>()
        val serviceIntent = Intent(app, FocusShieldService::class.java).apply {
            action = FocusShieldService.ACTION_START_SHIELD
            putExtra(FocusShieldService.EXTRA_DURATION_SECONDS, seconds)
            putExtra(FocusShieldService.EXTRA_BLOCKED_LIST, userPreferences.value?.blockedAppsList)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(serviceIntent)
            } else {
                app.startService(serviceIntent)
            }
        } catch (_: Exception) {}

        startShieldTicker(seconds)
    }

    private fun startShieldTicker(totalSeconds: Int) {
        standaloneShieldJob?.cancel()
        standaloneShieldJob = viewModelScope.launch {
            var left = totalSeconds
            while (isActive && left > 0) {
                delay(1000L)
                left -= 1
                _standaloneShieldRemainingSeconds.value = left
            }
            // Strict lock: Automatically unlock ONLY when reaching 0
            internalFinishShield()
            audioEngine.playShieldUnlockChime()
        }
    }

    private fun internalFinishShield() {
        standaloneShieldJob?.cancel()
        standaloneShieldJob = null
        _isStandaloneShieldActive.value = false
        _standaloneShieldRemainingSeconds.value = 0

        viewModelScope.launch {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(
                prefs.copy(
                    isAppBlockerEnabled = false,
                    appBlockerEndTime = 0L
                )
            )
            val blockedCount = prefs.blockedAppsList.split(",").filter { it.isNotBlank() }.size
            firebaseSyncManager.syncBlockerState(
                isArmed = false,
                endTime = 0L,
                blockedCount = blockedCount
            )
        }

        val app = getApplication<Application>()
        val serviceIntent = Intent(app, FocusShieldService::class.java).apply {
            action = FocusShieldService.ACTION_STOP_SHIELD
        }
        try {
            app.startService(serviceIntent)
        } catch (_: Exception) {}
    }

    // Per User Request: Users CANNOT manually turn off the blocker while timer is running.
    fun stopStandaloneShield() {
        if (_standaloneShieldRemainingSeconds.value > 0) {
            // Strict lock active: manual turn off is forbidden
            return
        }
        internalFinishShield()
    }

    fun dismissCompletionScreen() {
        val lastSession = _completedSessionSummary.value
        _showCompletionScreen.value = false
        _completedSessionSummary.value = null

        // Trigger post-session donation prompt with frequency and probability rules
        if (lastSession != null) {
            checkAndTriggerSessionDonationPrompt(lastSession)
        }
    }

    private fun checkAndTriggerSessionDonationPrompt(lastSession: FocusSessionEntity) {
        viewModelScope.launch {
            val prefs = userPreferences.value ?: repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
            if (prefs.neverShowDonationPrompt) {
                // User opted out via "Don't show again" -> never prompt again
                return@launch
            }
            val now = System.currentTimeMillis()

            // 1. Cooldown check: 7 days default; 14 days if user dismissed prompt >= 2 times
            val requiredCooldownMs = if (prefs.donationPromptDismissedCount >= 2) {
                14 * 24 * 60 * 60 * 1000L // 14 days cooldown for dismissed >= 2
            } else {
                7 * 24 * 60 * 60 * 1000L // 7 days cooldown
            }

            if (prefs.lastDonationPromptShownAt > 0L) {
                val timeSinceLastShown = now - prefs.lastDonationPromptShownAt
                if (timeSinceLastShown < requiredCooldownMs) {
                    // Cooldown active -> skip prompt
                    return@launch
                }
            }

            // 2. Probability check (~10% chance / 1 in 10 eligible times)
            val passesProbabilityCheck = kotlin.random.Random.nextFloat() < 0.10f
            if (!passesProbabilityCheck) {
                // Failed probability check -> skip prompt
                return@launch
            }

            // 3. Show prompt & update last_donation_prompt_shown_at immediately
            _lastCompletedSessionForPrompt.value = lastSession
            _showSessionDonationPrompt.value = true
            sessionDonationRotationIndex = (sessionDonationRotationIndex + 1) % 6

            val updatedPrefs = prefs.copy(lastDonationPromptShownAt = now)
            repository.savePreferences(updatedPrefs)
        }
    }

    fun dismissSessionDonationPrompt() {
        _showSessionDonationPrompt.value = false
        _lastCompletedSessionForPrompt.value = null

        // Explicit dismissal ("Maybe later") -> increment donationPromptDismissedCount
        viewModelScope.launch {
            val prefs = userPreferences.value ?: repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
            val updatedPrefs = prefs.copy(donationPromptDismissedCount = prefs.donationPromptDismissedCount + 1)
            repository.savePreferences(updatedPrefs)
        }
    }

    fun setNeverShowDonationPrompt(neverShow: Boolean = true) {
        _showSessionDonationPrompt.value = false
        _showMilestoneDonationPrompt.value = false
        _lastCompletedSessionForPrompt.value = null

        viewModelScope.launch {
            val prefs = userPreferences.value ?: repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
            val updatedPrefs = prefs.copy(neverShowDonationPrompt = neverShow)
            repository.savePreferences(updatedPrefs)
        }
    }

    fun openSupportFromSessionPrompt() {
        _showSessionDonationPrompt.value = false
        _lastCompletedSessionForPrompt.value = null
        _isSupportLockZenSheetOpen.value = true
        // User chose to donate -> do not increment dismissed count
    }

    fun getSessionDonationRotationIndex(): Int = sessionDonationRotationIndex

    // Support LockZen / Donation Functions
    fun openSupportLockZenSheet() {
        _isSupportLockZenSheetOpen.value = true
    }

    fun closeSupportLockZenSheet() {
        _isSupportLockZenSheetOpen.value = false
    }

    fun triggerMilestoneDonationCheck(completedSessionCount: Int) {
        viewModelScope.launch {
            val prefs = userPreferences.value ?: repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
            if (prefs.neverShowDonationPrompt) return@launch
            if (completedSessionCount < 5) return@launch // Don't prompt new users (<5 sessions)
            val now = System.currentTimeMillis()
            val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
            if (now - prefs.lastDonationPromptTimestamp >= sevenDaysMs) {
                _showMilestoneDonationPrompt.value = true
            }
        }
    }

    fun dismissMilestoneDonationPrompt() {
        _showMilestoneDonationPrompt.value = false
        viewModelScope.launch {
            val prefs = userPreferences.value ?: repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
            repository.savePreferences(prefs.copy(lastDonationPromptTimestamp = System.currentTimeMillis()))
        }
    }

    fun logDistraction(type: String) {
        _distractionsCount.value += 1
        _loggedDistractions.value = _loggedDistractions.value + type
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            repository.setTaskCompletion(task.id, !task.isCompleted)
        }
    }

    fun createTask(
        title: String,
        category: String,
        durationMinutes: Int,
        priority: Int,
        scheduledTime: String,
        notes: String,
        isTopPriority: Boolean
    ) {
        viewModelScope.launch {
            val task = TaskEntity(
                title = title,
                category = category,
                durationMinutes = durationMinutes,
                priority = priority,
                scheduledTime = scheduledTime,
                notes = notes,
                isTopPriority = isTopPriority
            )
            repository.insertTask(task)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun saveWeeklyReflection(text: String) {
        viewModelScope.launch {
            val reflection = ReflectionEntity(
                weekLabel = "Current Week",
                totalMinutesFocused = 1122,
                sessionsCompleted = 14,
                bestDay = "Wednesday",
                completionRate = 86,
                reflectionText = text
            )
            repository.insertReflection(reflection)
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(prefs.copy(themeMode = mode))
        }
    }

    fun updateDailyGoal(minutes: Int) {
        viewModelScope.launch {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(prefs.copy(dailyGoalMinutes = minutes))
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(prefs.copy(hasCompletedOnboarding = true))
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(
                prefs.copy(
                    hasCompletedOnboarding = false,
                    hasCompletedIntakeSurvey = false
                )
            )
            _showQuestionnaire.value = true
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.populateDemoData()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    // ==========================================
    // SCHEDULED STUDY BLOCKS (Custom user-editable)
    // ==========================================

    fun updateScheduleStatusSummary() {
        _scheduleStatusSummary.value = com.example.service.ScheduledBlockScheduler.getScheduleStatusSummary(getApplication())
    }

    fun refreshScheduledBlocks() {
        viewModelScope.launch {
            val local = com.example.service.ScheduledBlockScheduler.getLocalSchedules(getApplication())
            _scheduledBlocks.value = local
            updateScheduleStatusSummary()
            val uid = SupabaseService.getInstance().getCurrentUserId()
            if (!uid.isNullOrBlank()) {
                val remote = SupabaseService.getInstance().fetchScheduledBlocks(uid)
                if (remote.isNotEmpty()) {
                    val merged = com.example.service.ScheduledBlockScheduler.syncFromRemote(getApplication(), remote)
                    _scheduledBlocks.value = merged
                    updateScheduleStatusSummary()
                }
            }
        }
    }

    fun saveScheduledBlock(block: com.example.data.SupabaseScheduledBlockDto) {
        viewModelScope.launch {
            val uid = SupabaseService.getInstance().getCurrentUserId() ?: "local_user"
            val toSave = if (block.user_id.isBlank() || block.user_id == "anonymous") block.copy(user_id = uid) else block
            Log.d(TAG, "[UserAction] User clicked save scheduled block id=${toSave.id}, label='${toSave.label}', is_enabled=${toSave.is_enabled}")
            com.example.service.ScheduledBlockScheduler.upsertScheduleLocalAndRemote(getApplication(), toSave, triggerSource = "USER_TAP_SAVE")
            _scheduledBlocks.value = com.example.service.ScheduledBlockScheduler.getLocalSchedules(getApplication())
            updateScheduleStatusSummary()
        }
    }

    fun toggleScheduledBlock(scheduleId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "[UserAction] User clicked toggle scheduled block id=$scheduleId to isEnabled=$isEnabled")
            com.example.service.ScheduledBlockScheduler.toggleSchedule(getApplication(), scheduleId, isEnabled, triggerSource = "USER_TAP_TOGGLE")
            _scheduledBlocks.value = com.example.service.ScheduledBlockScheduler.getLocalSchedules(getApplication())
            updateScheduleStatusSummary()
        }
    }

    fun deleteScheduledBlock(scheduleId: String) {
        viewModelScope.launch {
            Log.d(TAG, "[UserAction] User clicked delete scheduled block id=$scheduleId")
            com.example.service.ScheduledBlockScheduler.deleteScheduleLocalAndRemote(getApplication(), scheduleId, triggerSource = "USER_TAP_DELETE")
            _scheduledBlocks.value = com.example.service.ScheduledBlockScheduler.getLocalSchedules(getApplication())
            updateScheduleStatusSummary()
        }
    }

    // ==========================================
    // AI STUDY GUARD & PUNISHMENT LOGS
    // ==========================================

    fun refreshPunishmentLogs() {
        viewModelScope.launch {
            val local = com.example.util.AiStudyGuardManager.loadLocalLogs(getApplication())
            val uid = SupabaseService.getInstance().getCurrentUserId()
            if (!uid.isNullOrBlank()) {
                val remote = SupabaseService.getInstance().fetchPunishmentLogs(uid)
                if (remote.isNotEmpty()) {
                    com.example.util.AiStudyGuardManager.syncFromRemoteLogs(getApplication(), remote)
                }
            }
        }
    }

    fun resolveAiWarning() {
        com.example.util.AiStudyGuardManager.resolveActiveWarningManually(getApplication())
    }

    fun dismissAiBlock() {
        com.example.util.AiStudyGuardManager.dismissActiveBlock()
    }

    fun dismissAiResolved() {
        com.example.util.AiStudyGuardManager.dismissActiveResolved()
    }

    // --- APP DAILY LIMITS VIEWMODEL ACTIONS ---

    fun saveAppDailyLimit(limit: AppDailyLimitEntity) {
        viewModelScope.launch {
            val uid = _currentUser.value?.firebaseUid ?: SupabaseService.getInstance().getCurrentUserId() ?: ""
            repository.saveAppDailyLimit(limit.copy(userId = uid))
        }
    }

    fun deleteAppDailyLimit(id: String) {
        viewModelScope.launch {
            repository.deleteAppDailyLimit(id)
        }
    }

    fun useEmergencyAccess(appPackage: String) {
        viewModelScope.launch {
            repository.useEmergencyAccess(appPackage)
        }
    }

    fun toggleAppDailyLimitEnabled(id: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val current = appDailyLimits.value.find { it.id == id }
            if (current != null) {
                repository.saveAppDailyLimit(current.copy(isEnabled = isEnabled))
            }
        }
    }

    fun syncAppDailyLimitsFromSupabase() {
        viewModelScope.launch {
            val uid = _currentUser.value?.firebaseUid ?: SupabaseService.getInstance().getCurrentUserId() ?: ""
            if (uid.isNotBlank()) {
                repository.syncAppDailyLimitsFromSupabase(uid)
            }
        }
    }

    fun syncUserData(context: Context? = null) {
        viewModelScope.launch {
            val uid = _currentUser.value?.firebaseUid
                ?: SupabaseService.getInstance().getCurrentUserId()
                ?: AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.getStoredUserId(it) }
                ?: ""
            if (uid.isNotBlank()) {
                repository.syncWithSupabase(uid)
                refreshAlarms()
                refreshScheduledBlocks()
                syncAppDailyLimitsFromSupabase()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stop()
        alarmAudioEngine.stopAlarm()
        timerJob?.cancel()
        alarmCheckerJob?.cancel()
    }
}
