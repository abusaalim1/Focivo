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
import com.example.data.FirebaseAuthManager
import com.example.data.FocuslyRepository
import com.example.data.FirebaseSyncManager
import com.example.data.SupabaseService
import com.example.data.LeaderboardUser
import com.example.data.model.AlarmItem
import com.example.data.model.FocusSessionEntity
import com.example.data.model.ReflectionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserAccountEntity
import com.example.data.model.UserPreferencesEntity
import com.example.service.FocusShieldService
import com.example.service.ScheduledBlockScheduler
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
    private val _alarms = MutableStateFlow<List<AlarmItem>>(
        listOf(
            AlarmItem(
                label = "Morning Deep Focus",
                hour = 8,
                minute = 30,
                isEnabled = true,
                ringtone = "Zen Bell"
            ),
            AlarmItem(
                label = "Midday Reset",
                hour = 13,
                minute = 45,
                isEnabled = true,
                ringtone = "Celestial Harp"
            ),
            AlarmItem(
                label = "Day Review",
                hour = 18,
                minute = 0,
                isEnabled = false,
                ringtone = "Digital Pulse"
            )
        )
    )
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

    // App Blocker / Focus Shield State
    private val _isShieldHubOpen = MutableStateFlow(false)
    val isShieldHubOpen: StateFlow<Boolean> = _isShieldHubOpen.asStateFlow()

    private val _isShieldOverlayVisible = MutableStateFlow(false)
    val isShieldOverlayVisible: StateFlow<Boolean> = _isShieldOverlayVisible.asStateFlow()

    private val _shieldInterceptedAppName = MutableStateFlow("Instagram & Reels")
    val shieldInterceptedAppName: StateFlow<String> = _shieldInterceptedAppName.asStateFlow()

    private val _isStandaloneShieldActive = MutableStateFlow(false)
    val isStandaloneShieldActive: StateFlow<Boolean> = _isStandaloneShieldActive.asStateFlow()

    private val _standaloneShieldRemainingSeconds = MutableStateFlow(0)
    val standaloneShieldRemainingSeconds: StateFlow<Int> = _standaloneShieldRemainingSeconds.asStateFlow()

    private var standaloneShieldJob: Job? = null
    private var timerJob: Job? = null

    val firebaseSyncManager = FirebaseSyncManager.getInstance(application)
    private val _installedApps = MutableStateFlow<List<DeviceAppInfo>>(emptyList())
    val installedApps: StateFlow<List<DeviceAppInfo>> = _installedApps.asStateFlow()
    val leaderboardUsers: StateFlow<List<LeaderboardUser>> = firebaseSyncManager.leaderboardUsers

    // Scheduled Study Blocks (User-editable custom schedules)
    private val _scheduleStatusSummary = MutableStateFlow("No active schedule")
    val scheduleStatusSummary: StateFlow<String> = _scheduleStatusSummary.asStateFlow()

    private val _scheduledBlocks = MutableStateFlow<List<com.example.data.SupabaseScheduledBlockDto>>(
        com.example.service.ScheduledBlockScheduler.getLocalSchedules(application)
    )
    val scheduledBlocks: StateFlow<List<com.example.data.SupabaseScheduledBlockDto>> = _scheduledBlocks.asStateFlow()

    private val _shouldNavigateToStudyTab = MutableStateFlow(false)
    val shouldNavigateToStudyTab: StateFlow<Boolean> = _shouldNavigateToStudyTab.asStateFlow()

    fun consumeStudyTabNavigation() {
        _shouldNavigateToStudyTab.value = false
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

            // Load local alarms immediately on cold launch
            val localAlarms = com.example.service.AlarmScheduler.getLocalAlarms(app)
            if (localAlarms.isNotEmpty()) {
                _alarms.value = localAlarms
                com.example.service.AlarmScheduler.rescheduleAllEnabled(app, localAlarms)
                Log.d(TAG, "[AlarmLoad] Restored ${localAlarms.size} local cached alarms on cold startup")
            }

            refreshScheduledBlocks()
            refreshPunishmentLogs()
            val isShieldRunning = FocusShieldService.isShieldRunning(app)
            val remainingSecs = FocusShieldService.getRemainingSeconds(app)
            val isScheduleActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(app)

            if (isShieldRunning || isScheduleActive) {
                _isStandaloneShieldActive.value = true
                val effectiveSecs = if (remainingSecs > 0) remainingSecs else 25 * 60
                _standaloneShieldRemainingSeconds.value = effectiveSecs
                startShieldTicker(effectiveSecs)
                val currentPrefs = repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
                repository.savePreferences(currentPrefs.copy(isAppBlockerEnabled = true))
            }

            // 2. Restore persistent Supabase session on cold start before UI rendering
            _isAuthChecking.value = true
            val restoredUid: String? = SupabaseService.getInstance().awaitSessionRestoration()
            if (!restoredUid.isNullOrBlank()) {
                restoreUserAccount(restoredUid)
            } else {
                // Not authenticated in Supabase - strictly clear any placeholder account
                _currentUser.value = null
            }
            _isAuthChecking.value = false

            // Set initial mode
            setMode("Study", 25)
            startAlarmChecker()
            refreshInstalledApps()
        }
    }

    suspend fun restoreUserAccount(restoredUid: String) {
        _isProfileLoading.value = true
        _profileError.value = null
        try {
            val profile = SupabaseService.getInstance().fetchUserProfile(restoredUid)
            val userEmail: String = profile?.email
                ?: repository.userPreferences.firstOrNull()?.currentUserEmail
                ?: ("user_" + restoredUid.take(6) + "@focusly.app")
            val userName: String = if (!profile?.full_name.isNullOrBlank()) {
                profile!!.full_name
            } else {
                userEmail.substringBefore("@")
            }

            val photoUrl: String? = profile?.avatar_url?.takeIf { it.isNotBlank() }
            val user = UserAccountEntity(
                id = Math.abs(restoredUid.hashCode().toLong()).coerceAtLeast(1L),
                email = userEmail,
                fullName = userName,
                firebaseUid = restoredUid,
                photoUrl = photoUrl
            )
            _currentUser.value = user

            val currentPrefs = repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
            repository.savePreferences(
                currentPrefs.copy(
                    currentUserId = user.id,
                    currentUserEmail = userEmail,
                    currentUserName = userName,
                    currentUserPhotoUrl = photoUrl ?: currentPrefs.currentUserPhotoUrl
                )
            )

            repository.syncWithSupabase(restoredUid)
            refreshAlarms()
            refreshScheduledBlocks()

            if (userName.isBlank()) {
                _showQuestionnaire.value = true
            }
        } catch (e: Exception) {
            _profileError.value = e.message ?: "Failed to fetch user profile"
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

            var existingUser = repository.getUserByEmail(cleanEmail)
            if (existingUser == null) {
                val safeId = System.currentTimeMillis()
                existingUser = UserAccountEntity(
                    id = safeId,
                    email = cleanEmail,
                    passwordHash = hashPassword(pin),
                    fullName = cleanEmail.substringBefore("@"),
                    firebaseUid = supabaseUid ?: "",
                    createdAt = System.currentTimeMillis()
                )
                repository.registerUser(existingUser)
            } else if (!supabaseUid.isNullOrEmpty()) {
                existingUser = existingUser.copy(firebaseUid = supabaseUid)
                repository.updateUser(existingUser)
            }

            _currentUser.value = existingUser
            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(
                prefs.copy(
                    currentUserId = existingUser.id,
                    currentUserEmail = existingUser.email,
                    currentUserName = existingUser.fullName.ifBlank { cleanEmail.substringBefore("@") },
                    hasCompletedOnboarding = true
                )
            )

            // 2. Sync user data with Supabase Postgrest tables
            if (!supabaseUid.isNullOrBlank()) {
                repository.syncWithSupabase(supabaseUid)
            }

            if (existingUser.fullName.isBlank()) {
                _showQuestionnaire.value = true
            }
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
                    hasCompletedOnboarding = true
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
                val displayName = authData.displayName ?: cleanEmail.substringBefore("@")
                val existing = repository.getUserByEmail(cleanEmail)
                val user = if (existing != null) {
                    val updated = existing.copy(
                        firebaseUid = authData.uid,
                        photoUrl = authData.photoUrl,
                        isGoogleUser = true,
                        fullName = if (existing.fullName.isBlank()) displayName else existing.fullName
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
                        photoUrl = authData.photoUrl,
                        isGoogleUser = true
                    )
                    val newId = repository.registerUser(newUser)
                    newUser.copy(id = newId)
                }

                _currentUser.value = user
                val prefs = userPreferences.value ?: UserPreferencesEntity()
                repository.savePreferences(
                    prefs.copy(
                        currentUserId = user.id,
                        currentUserEmail = user.email,
                        currentUserName = user.fullName,
                        currentUserPhotoUrl = user.photoUrl,
                        isGoogleAuth = true
                    )
                )

                if (user.fullName.isBlank()) {
                    _showQuestionnaire.value = true
                }
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
                val existing = repository.getUserByEmail(cleanEmail)
                val user = if (existing != null) {
                    val updated = existing.copy(
                        firebaseUid = authData.uid,
                        fullName = displayName,
                        photoUrl = photoUrl,
                        isGoogleUser = true
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
                        isGoogleUser = true
                    )
                    val newId = repository.registerUser(newUser)
                    newUser.copy(id = newId)
                }

                _currentUser.value = user
                val prefs = userPreferences.value ?: UserPreferencesEntity()
                repository.savePreferences(
                    prefs.copy(
                        currentUserId = user.id,
                        currentUserEmail = cleanEmail,
                        currentUserName = displayName,
                        currentUserPhotoUrl = photoUrl,
                        isGoogleAuth = true
                    )
                )

                if (user.fullName.isBlank()) {
                    _showQuestionnaire.value = true
                }
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
                    fullName = "Guest Deep Worker",
                    firebaseUid = authData.uid,
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
                        isGoogleAuth = false
                    )
                )
                _isAuthLoading.value = false
            }.onFailure { ex ->
                _isAuthLoading.value = false
                _authError.value = ex.localizedMessage ?: "Guest login failed"
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
                primaryDistraction = distraction
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
                    preferredFocusMode = if (focusStyle.contains("50m")) "Deep Work" else if (focusStyle.contains("25m")) "Classic" else "Short Sprint"
                )
            )

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
            val userIdStr = if (!user?.firebaseUid.isNullOrBlank()) user?.firebaseUid ?: "" else user?.id?.toString() ?: ""
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
                val userDto = com.example.data.SupabaseUserDto(
                    id = userIdStr,
                    email = user?.email ?: "",
                    full_name = name,
                    has_completed_intake_survey = true,
                    student_class_level = studentClassLevel,
                    is_board_exam_year = isBoardExamYear,
                    primary_study_goal = primaryStudyGoal,
                    biggest_distraction_app = biggestDistractionApp,
                    preferred_study_time_window = preferredStudyTimeWindow,
                    daily_screen_time_goal_minutes = dailyScreenTimeGoalMinutes,
                    motivation_style = motivationStyle
                )
                com.example.data.SupabaseService.getInstance().upsertUserProfile(userDto)
            }

            _showQuestionnaire.value = false
        }
    }

    fun updateProfileNameAndAvatar(name: String, avatarBytes: ByteArray?) {
        viewModelScope.launch {
            val user = _currentUser.value ?: return@launch
            val userIdStr = if (!user.firebaseUid.isNullOrBlank()) user.firebaseUid else user.id.toString()
            var photoUrl = user.photoUrl

            if (avatarBytes != null && userIdStr.isNotBlank()) {
                val uploadedUrl = com.example.data.SupabaseService.getInstance().uploadAvatarImage(userIdStr, avatarBytes)
                if (!uploadedUrl.isNullOrBlank()) {
                    photoUrl = uploadedUrl
                } else {
                    // Fallback to Base64 data URL for instant offline/local rendering
                    val base64 = android.util.Base64.encodeToString(avatarBytes, android.util.Base64.NO_WRAP)
                    photoUrl = "data:image/jpeg;base64,$base64"
                }
            }

            val updatedUser = user.copy(fullName = name, photoUrl = photoUrl)
            repository.updateUser(updatedUser)
            _currentUser.value = updatedUser

            val prefs = userPreferences.value ?: UserPreferencesEntity()
            repository.savePreferences(
                prefs.copy(
                    currentUserName = name,
                    currentUserPhotoUrl = photoUrl
                )
            )

            val userDto = com.example.data.SupabaseUserDto(
                id = userIdStr,
                email = user.email,
                full_name = name,
                avatar_url = photoUrl
            )
            com.example.data.SupabaseService.getInstance().upsertUserProfile(userDto)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.signOut()
            repository.clearAllData()
            _currentUser.value = null
            _scheduledBlocks.value = emptyList()
            _alarms.value = emptyList()
            _showQuestionnaire.value = false
            _verificationMessage.value = null
        }
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

            // 1. First populate with local cache
            val localAlarms = com.example.service.AlarmScheduler.getLocalAlarms(app)
            if (localAlarms.isNotEmpty()) {
                _alarms.value = localAlarms
            }

            if (!uid.isNullOrBlank()) {
                Log.d(TAG, "[AlarmLoad] Querying SELECT * FROM public.alarms WHERE user_id = '$uid'")
                val remoteDtos = SupabaseService.getInstance().fetchAlarms(uid)
                Log.d(TAG, "[AlarmLoad] Loaded ${remoteDtos.size} alarms from Supabase for uid=$uid: $remoteDtos")

                if (remoteDtos.isNotEmpty()) {
                    val remoteAlarms = remoteDtos.map { dto ->
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

    fun startTimer() {
        if (_isTimerRunning.value) return
        _isTimerRunning.value = true
        if (sessionStartTimeMs == 0L) {
            sessionStartTimeMs = System.currentTimeMillis()
        }
        audioEngine.play(_ambientSound.value)

        // Save manual timer state to local prefs for real-time AI Guard checking
        try {
            val app = getApplication<Application>()
            val prefs = app.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
            val endTime = System.currentTimeMillis() + (_remainingSeconds.value * 1000L)
            prefs.edit()
                .putBoolean("is_manual_timer_running", true)
                .putLong("manual_timer_end_time_ms", endTime)
                .apply()
        } catch (_: Exception) {}

        // If App Blocker Shield is enabled, activate background service
        if (userPreferences.value?.isAppBlockerEnabled == true) {
            val app = getApplication<Application>()
            val serviceIntent = Intent(app, FocusShieldService::class.java).apply {
                action = FocusShieldService.ACTION_START_SHIELD
                putExtra(FocusShieldService.EXTRA_DURATION_SECONDS, _targetSeconds.value)
                putExtra(FocusShieldService.EXTRA_BLOCKED_LIST, userPreferences.value?.blockedAppsList)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(serviceIntent)
                } else {
                    app.startService(serviceIntent)
                }
            } catch (_: Exception) {}
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

        try {
            val app = getApplication<Application>()
            val prefs = app.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_manual_timer_running", false)
                .putLong("manual_timer_end_time_ms", 0L)
                .apply()
        } catch (_: Exception) {}

        if (userPreferences.value?.isAppBlockerEnabled == true) {
            val app = getApplication<Application>()
            val serviceIntent = Intent(app, FocusShieldService::class.java).apply {
                action = FocusShieldService.ACTION_STOP_SHIELD
            }
            try {
                app.startService(serviceIntent)
            } catch (_: Exception) {}
        }
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

        val pointsEarned = maxOf(1, (actualSeconds * 10) / 60)

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
            val updatedPoints = prefs.focusPoints + pointsEarned
            val updatedLevel = (updatedPoints / 100) + 1

            val allPast = repository.allSessions.firstOrNull() ?: emptyList()
            val totalSessions = allPast + session
            val (calcCurrent, calcBest) = calculateConsecutiveStreak(totalSessions)
            val currentStreak = calcCurrent
            val bestStreak = maxOf(prefs.bestStreak, calcBest, currentStreak)

            val updatedPrefs = prefs.copy(
                focusPoints = updatedPoints,
                userLevel = updatedLevel,
                currentStreak = currentStreak,
                bestStreak = bestStreak
            )
            repository.savePreferences(updatedPrefs)

            // Real-time Supabase sync for points, streak, and study duration
            val uid = _currentUser.value?.firebaseUid ?: SupabaseService.getInstance().getCurrentUserId()
            if (!uid.isNullOrBlank()) {
                val totalSeconds = totalSessions.sumOf { it.durationSeconds }
                val leaderboardDto = com.example.data.SupabaseStudyLeaderboardDto(
                    user_id = uid,
                    display_name = updatedPrefs.currentUserName ?: "Student",
                    study_seconds = totalSeconds.toLong(),
                    streak = currentStreak,
                    subject_tag = _currentTaskTitle.value.ifBlank { "General Study" }
                )
                SupabaseService.getInstance().upsertLeaderboard(leaderboardDto)

                val prefsDto = com.example.data.SupabaseUserPreferencesDto(
                    user_id = uid,
                    focus_points = updatedPoints,
                    user_level = updatedLevel,
                    current_streak = currentStreak,
                    best_streak = bestStreak
                )
                SupabaseService.getInstance().upsertUserPreferences(prefsDto)
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
    }

    fun triggerShieldIntercept(appName: String) {
        _shieldInterceptedAppName.value = appName
        _isShieldOverlayVisible.value = true
        audioEngine.playShieldWarningTone()
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
                if (!_isTimerRunning.value || isAutoScheduledSession) {
                    if (!isAutoScheduledSession) {
                        _shouldNavigateToStudyTab.value = true
                    }
                    _isTimerRunning.value = true
                    _remainingSeconds.value = studyRemaining
                    _targetSeconds.value = studyRemaining
                    _currentTaskTitle.value = "Scheduled Study: ${activeStudySchedule.label}"
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
                // If user turns on master shield / distraction blocker, start background service immediately
                val intent = Intent(app, FocusShieldService::class.java).apply {
                    action = FocusShieldService.ACTION_START_SHIELD
                    putExtra(FocusShieldService.EXTRA_DURATION_SECONDS, 24 * 60 * 60)
                    putExtra(FocusShieldService.EXTRA_BLOCKED_LIST, prefs.blockedAppsList)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        app.startForegroundService(intent)
                    } else {
                        app.startService(intent)
                    }
                } catch (e: Exception) {
                    Log.e("FocuslyViewModel", "Failed to start FocusShieldService: ${e.message}")
                }
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
        _showCompletionScreen.value = false
        _completedSessionSummary.value = null
        viewModelScope.launch {
            val sessionsCount = repository.allSessions.firstOrNull()?.size ?: 0
            triggerMilestoneDonationCheck(sessionsCount)
        }
    }

    // Support LockZen / Donation Functions
    fun openSupportLockZenSheet() {
        _isSupportLockZenSheetOpen.value = true
    }

    fun closeSupportLockZenSheet() {
        _isSupportLockZenSheetOpen.value = false
    }

    fun triggerMilestoneDonationCheck(completedSessionCount: Int) {
        viewModelScope.launch {
            if (completedSessionCount < 5) return@launch // Don't prompt new users (<5 sessions)
            val prefs = userPreferences.value ?: repository.userPreferences.firstOrNull() ?: UserPreferencesEntity()
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
            repository.savePreferences(prefs.copy(hasCompletedOnboarding = false))
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

    override fun onCleared() {
        super.onCleared()
        audioEngine.stop()
        alarmAudioEngine.stopAlarm()
        timerJob?.cancel()
        alarmCheckerJob?.cancel()
    }
}
