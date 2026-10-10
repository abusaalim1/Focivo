package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.model.AppDailyLimitEntity
import com.example.data.model.FocusSessionEntity
import com.example.data.model.ReflectionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserAccountEntity
import com.example.data.model.UserPreferencesEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FocuslyRepository(private val context: Context) {

    /** Logs failed Supabase writes at ERROR level with the entity id.
     * Previously these Results were discarded, so transient failures were silently lost. */
    private fun logSupabaseResult(res: Result<*>, op: String, entityId: String) {
        if (res.isFailure) {
            Log.e(TAG, "Supabase $op failed for id=$entityId: ${res.exceptionOrNull()?.message}")
        }
    }

    companion object {
        private const val TAG = "FocuslyRepository"
        @Volatile
        private var instance: FocuslyRepository? = null

        fun getInstance(context: Context): FocuslyRepository {
            return instance ?: synchronized(this) {
                instance ?: FocuslyRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    // Guards read-modify-write on _appDailyLimits (lost minutes / double-spent bypass)
    private val limitsMutex = kotlinx.coroutines.sync.Mutex()

    // In-memory state flows backed by local SharedPreferences
    private val _allTasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val allTasks: Flow<List<TaskEntity>> = _allTasks.asStateFlow()

    val topPriorities: Flow<List<TaskEntity>> = _allTasks.map { tasks ->
        tasks.filter { it.isTopPriority && !it.isCompleted }
    }

    private val _allSessions = MutableStateFlow<List<FocusSessionEntity>>(emptyList())
    val allSessions: Flow<List<FocusSessionEntity>> = _allSessions.asStateFlow()

    private val _allReflections = MutableStateFlow<List<ReflectionEntity>>(emptyList())
    val allReflections: Flow<List<ReflectionEntity>> = _allReflections.asStateFlow()

    private val _userPreferences = MutableStateFlow<UserPreferencesEntity?>(UserPreferencesEntity())
    val userPreferences: Flow<UserPreferencesEntity?> = _userPreferences.asStateFlow()

    private val _appDailyLimits = MutableStateFlow<List<AppDailyLimitEntity>>(emptyList())
    val appDailyLimits: Flow<List<AppDailyLimitEntity>> = _appDailyLimits.asStateFlow()

    private val localDataPrefs by lazy {
        context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
    }

    private val userPrefs by lazy {
        context.getSharedPreferences("focusly_users_cache", Context.MODE_PRIVATE)
    }

    init {
        // Load off the calling thread: loadAllLocalData() does disk IO and getInstance()
        // is typically called on the main thread (ANR risk as data grows).
        // StateFlow.value sets are thread-safe; collectors receive data when ready.
        scope.launch {
            loadAllLocalData()
        }
    }

    private fun loadAllLocalData() {
        _allTasks.value = loadTasksFromLocal()
        _allSessions.value = loadSessionsFromLocal()
        _allReflections.value = loadReflectionsFromLocal()
        _userPreferences.value = loadPreferencesFromLocal()
        _appDailyLimits.value = loadAppDailyLimitsFromLocal()

        // Real-time real user data only - do not populate dummy placeholder tasks
    }

    private fun loadTasksFromLocal(): List<TaskEntity> {
        val guestTasks = GuestDataStorageManager.getInstance(context).loadGuestTasks()
        val jsonStr = localDataPrefs.getString("tasks", "[]") ?: "[]"
        val list = mutableListOf<TaskEntity>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TaskEntity(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        title = obj.optString("title", ""),
                        category = obj.optString("category", "Deep Work"),
                        priority = obj.optInt("priority", 1),
                        durationMinutes = obj.optInt("durationMinutes", 25),
                        scheduledTime = obj.optString("scheduledTime", "09:00"),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        isTopPriority = obj.optBoolean("isTopPriority", false),
                        notes = obj.optString("notes", ""),
                        date = obj.optString("date", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading tasks from local JSON: ${e.message}")
        }
        val mergedMap = LinkedHashMap<Long, TaskEntity>()
        for (t in list) mergedMap[t.id] = t
        for (gt in guestTasks) mergedMap[gt.id] = gt
        val result = mergedMap.values.toList()
        if (guestTasks.isEmpty() && result.isNotEmpty()) {
            GuestDataStorageManager.getInstance(context).saveGuestTasks(result)
        }
        return result
    }

    private fun saveTasksToLocal(list: List<TaskEntity>) {
        try {
            val array = JSONArray()
            for (task in list) {
                val obj = JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("category", task.category)
                    put("priority", task.priority)
                    put("durationMinutes", task.durationMinutes)
                    put("scheduledTime", task.scheduledTime)
                    put("isCompleted", task.isCompleted)
                    put("isTopPriority", task.isTopPriority)
                    put("notes", task.notes)
                    put("date", task.date)
                    put("createdAt", task.createdAt)
                }
                array.put(obj)
            }
            localDataPrefs.edit().putString("tasks", array.toString()).apply()
            GuestDataStorageManager.getInstance(context).saveGuestTasks(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving tasks to local JSON: ${e.message}")
        }
    }

    private fun loadSessionsFromLocal(): List<FocusSessionEntity> {
        val guestSessions = GuestDataStorageManager.getInstance(context).loadGuestSessions()
        val jsonStr = localDataPrefs.getString("focus_sessions", "[]") ?: "[]"
        val list = mutableListOf<FocusSessionEntity>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val completedAt = obj.optLong("completedAt", 0L)
                list.add(
                    FocusSessionEntity(
                        id = obj.optLong("id", if (completedAt > 0) completedAt else System.currentTimeMillis()),
                        taskTitle = obj.optString("taskTitle", ""),
                        durationSeconds = obj.optInt("durationSeconds", 0),
                        targetDurationSeconds = obj.optInt("targetDurationSeconds", 0),
                        mode = obj.optString("mode", "Deep Work"),
                        distractionsCount = obj.optInt("distractionsCount", 0),
                        distractionTypes = obj.optString("distractionTypes", ""),
                        focusPointsEarned = obj.optInt("focusPointsEarned", 12),
                        completedAt = completedAt,
                        dayOfWeek = obj.optInt("dayOfWeek", 1),
                        hourOfDay = obj.optInt("hourOfDay", 10),
                        notes = obj.optString("notes", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sessions from local JSON: ${e.message}")
        }
        val mergedMap = LinkedHashMap<Long, FocusSessionEntity>()
        for (s in list) mergedMap[s.id] = s
        for (g in guestSessions) mergedMap[g.id] = g
        val result = mergedMap.values.sortedByDescending { it.completedAt }
        if (guestSessions.isEmpty() && result.isNotEmpty()) {
            GuestDataStorageManager.getInstance(context).saveGuestSessions(result)
        }
        return result
    }

    private fun saveSessionsToLocal(list: List<FocusSessionEntity>) {
        try {
            val array = JSONArray()
            for (sess in list) {
                val obj = JSONObject().apply {
                    put("id", sess.id)
                    put("taskTitle", sess.taskTitle)
                    put("durationSeconds", sess.durationSeconds)
                    put("targetDurationSeconds", sess.targetDurationSeconds)
                    put("mode", sess.mode)
                    put("distractionsCount", sess.distractionsCount)
                    put("distractionTypes", sess.distractionTypes)
                    put("focusPointsEarned", sess.focusPointsEarned)
                    put("completedAt", sess.completedAt)
                    put("dayOfWeek", sess.dayOfWeek)
                    put("hourOfDay", sess.hourOfDay)
                    put("notes", sess.notes)
                }
                array.put(obj)
            }
            localDataPrefs.edit().putString("focus_sessions", array.toString()).apply()

            // Persist guest sessions to dedicated guest_data directory using FileOutputStream
            GuestDataStorageManager.getInstance(context).saveGuestSessions(list)

            // Update guest profile aggregate statistics
            val totalMins = list.sumOf { it.durationSeconds } / 60
            val existingProfile = GuestDataStorageManager.getInstance(context).loadGuestProfile() ?: GuestProfileData()
            GuestDataStorageManager.getInstance(context).saveGuestProfile(
                existingProfile.copy(
                    totalFocusMinutes = totalMins,
                    totalSessionsCompleted = list.size,
                    lastSessionTimestamp = list.firstOrNull()?.completedAt ?: System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving sessions to local JSON: ${e.message}")
        }
    }

    private fun loadReflectionsFromLocal(): List<ReflectionEntity> {
        val guestRefs = GuestDataStorageManager.getInstance(context).loadGuestReflections()
        val jsonStr = localDataPrefs.getString("reflections", "[]") ?: "[]"
        val list = mutableListOf<ReflectionEntity>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ReflectionEntity(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        weekLabel = obj.optString("weekLabel", ""),
                        totalMinutesFocused = obj.optInt("totalMinutesFocused", 0),
                        sessionsCompleted = obj.optInt("sessionsCompleted", 0),
                        bestDay = obj.optString("bestDay", "Wednesday"),
                        completionRate = obj.optInt("completionRate", 80),
                        reflectionText = obj.optString("reflectionText", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading reflections from local JSON: ${e.message}")
        }
        val mergedMap = LinkedHashMap<Long, ReflectionEntity>()
        for (r in list) mergedMap[r.id] = r
        for (gr in guestRefs) mergedMap[gr.id] = gr
        val result = mergedMap.values.toList()
        if (guestRefs.isEmpty() && result.isNotEmpty()) {
            GuestDataStorageManager.getInstance(context).saveGuestReflections(result)
        }
        return result
    }

    private fun saveReflectionsToLocal(list: List<ReflectionEntity>) {
        try {
            val array = JSONArray()
            for (ref in list) {
                val obj = JSONObject().apply {
                    put("id", ref.id)
                    put("weekLabel", ref.weekLabel)
                    put("totalMinutesFocused", ref.totalMinutesFocused)
                    put("sessionsCompleted", ref.sessionsCompleted)
                    put("bestDay", ref.bestDay)
                    put("completionRate", ref.completionRate)
                    put("reflectionText", ref.reflectionText)
                    put("createdAt", ref.createdAt)
                }
                array.put(obj)
            }
            localDataPrefs.edit().putString("reflections", array.toString()).apply()
            GuestDataStorageManager.getInstance(context).saveGuestReflections(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving reflections to local JSON: ${e.message}")
        }
    }

    private fun loadPreferencesFromLocal(): UserPreferencesEntity {
        val guestPrefs = GuestDataStorageManager.getInstance(context).loadGuestPreferences()
        val jsonStr = localDataPrefs.getString("user_preferences", null)
        if (jsonStr == null) {
            if (guestPrefs != null) {
                return guestPrefs
            }
            return UserPreferencesEntity()
        }
        try {
            val obj = JSONObject(jsonStr)
            val prefs = UserPreferencesEntity(
                id = obj.optInt("id", 1),
                themeMode = obj.optString("themeMode", "system"),
                dailyGoalMinutes = obj.optInt("dailyGoalMinutes", 360),
                preferredFocusMode = obj.optString("preferredFocusMode", "Deep Work"),
                deepWorkDuration = obj.optInt("deepWorkDuration", 50),
                deepWorkBreak = obj.optInt("deepWorkBreak", 10),
                classicDuration = obj.optInt("classicDuration", 25),
                classicBreak = obj.optInt("classicBreak", 5),
                shortSprintDuration = obj.optInt("shortSprintDuration", 15),
                soundEnabled = obj.optBoolean("soundEnabled", true),
                ambientSound = obj.optString("ambientSound", "Silent"),
                hapticsEnabled = obj.optBoolean("hapticsEnabled", true),
                notificationsEnabled = obj.optBoolean("notificationsEnabled", true),
                autoStartBreak = obj.optBoolean("autoStartBreak", false),
                autoStartFocus = obj.optBoolean("autoStartFocus", false),
                weekStartsOn = obj.optString("weekStartsOn", "Monday"),
                hasCompletedOnboarding = obj.optBoolean("hasCompletedOnboarding", false),
                hasCompletedIntakeSurvey = obj.optBoolean("hasCompletedIntakeSurvey", false),
                userLevel = obj.optInt("userLevel", 1),
                focusPoints = obj.optInt("focusPoints", 0),
                focusIdentity = obj.optString("focusIdentity", "Novice Deep Worker"),
                currentStreak = obj.optInt("currentStreak", 0),
                bestStreak = obj.optInt("bestStreak", 0),
                alarmRingtone = obj.optString("alarmRingtone", "Zen Bell"),
                currentUserId = if (obj.has("currentUserId") && !obj.isNull("currentUserId")) obj.getLong("currentUserId") else null,
                currentUserEmail = if (obj.has("currentUserEmail") && !obj.isNull("currentUserEmail")) obj.getString("currentUserEmail") else null,
                currentUserName = if (obj.has("currentUserName") && !obj.isNull("currentUserName")) obj.getString("currentUserName") else null,
                currentUserPhotoUrl = if (obj.has("currentUserPhotoUrl") && !obj.isNull("currentUserPhotoUrl")) obj.getString("currentUserPhotoUrl") else null,
                isGoogleAuth = obj.optBoolean("isGoogleAuth", false),
                isAppBlockerEnabled = obj.optBoolean("isAppBlockerEnabled", false),
                appBlockerDurationMinutes = obj.optInt("appBlockerDurationMinutes", 25),
                appBlockerEndTime = obj.optLong("appBlockerEndTime", 0L),
                blockedAppsList = obj.optString("blockedAppsList", "com.instagram.android,com.zhiliaoapp.musically,com.twitter.android,com.facebook.katana,com.snapchat.android,com.reddit.frontpage"),
                shieldBlockedAttempts = obj.optInt("shieldBlockedAttempts", 0),
                studentAge = obj.optInt("studentAge", 16),
                studentClass = obj.optString("studentClass", "Class 11"),
                studentClassLevel = obj.optString("studentClassLevel", "Class 11"),
                isBoardExamYear = obj.optBoolean("isBoardExamYear", false),
                dailyScreenTimeGoalMinutes = obj.optInt("dailyScreenTimeGoalMinutes", 120),
                primaryStudyGoal = obj.optString("primaryStudyGoal", "Exam Preparation"),
                biggestDistractionApp = obj.optString("biggestDistractionApp", "Instagram"),
                preferredStudyTimeWindow = obj.optString("preferredStudyTimeWindow", "Evening (6 PM - 10 PM)"),
                motivationStyle = obj.optString("motivationStyle", "Strict"),
                studentStream = obj.optString("studentStream", "Science (PCM)"),
                studySchedule = obj.optString("studySchedule", "6:00 PM – 10:00 PM"),
                mobileBreakTime = obj.optString("mobileBreakTime", "8:00 PM – 8:30 PM"),
                allowedEducationApps = obj.optString("allowedEducationApps", "com.google.android.youtube,com.openai.chatgpt,com.anthropic.claude"),
                isAutoStudyBlockerEnabled = obj.optBoolean("isAutoStudyBlockerEnabled", true),
                penaltyBlockEndTime = obj.optLong("penaltyBlockEndTime", 0L),
                buddyGrowthStage = obj.optInt("buddyGrowthStage", 1),
                buddyTotalFocusMinutes = obj.optInt("buddyTotalFocusMinutes", 0),
                lastDonationPromptShownAt = obj.optLong("lastDonationPromptShownAt", 0L),
                lastDonationPromptTimestamp = obj.optLong("lastDonationPromptTimestamp", 0L),
                lastWeeklyReviewShownAt = obj.optLong("lastWeeklyReviewShownAt", 0L),
                donationPromptDismissedCount = obj.optInt("donationPromptDismissedCount", 0),
                neverShowDonationPrompt = obj.optBoolean("neverShowDonationPrompt", false),
                lastViewedSundayRecapWeek = obj.optString("lastViewedSundayRecapWeek", ""),
                autoBlockStudyAppsWithoutAsking = obj.optBoolean("autoBlockStudyAppsWithoutAsking", false)
            )
            if (guestPrefs == null) {
                GuestDataStorageManager.getInstance(context).saveGuestPreferences(prefs)
            }
            return prefs
        } catch (e: Exception) {
            Log.e(TAG, "Error loading preferences from local JSON: ${e.message}")
            return guestPrefs ?: UserPreferencesEntity()
        }
    }

    private fun savePreferencesToLocal(prefs: UserPreferencesEntity) {
        try {
            val obj = JSONObject().apply {
                put("id", prefs.id)
                put("themeMode", prefs.themeMode)
                put("dailyGoalMinutes", prefs.dailyGoalMinutes)
                put("preferredFocusMode", prefs.preferredFocusMode)
                put("deepWorkDuration", prefs.deepWorkDuration)
                put("deepWorkBreak", prefs.deepWorkBreak)
                put("classicDuration", prefs.classicDuration)
                put("classicBreak", prefs.classicBreak)
                put("shortSprintDuration", prefs.shortSprintDuration)
                put("soundEnabled", prefs.soundEnabled)
                put("ambientSound", prefs.ambientSound)
                put("hapticsEnabled", prefs.hapticsEnabled)
                put("notificationsEnabled", prefs.notificationsEnabled)
                put("autoStartBreak", prefs.autoStartBreak)
                put("autoStartFocus", prefs.autoStartFocus)
                put("weekStartsOn", prefs.weekStartsOn)
                put("hasCompletedOnboarding", prefs.hasCompletedOnboarding)
                put("hasCompletedIntakeSurvey", prefs.hasCompletedIntakeSurvey)
                put("userLevel", prefs.userLevel)
                put("focusPoints", prefs.focusPoints)
                put("focusIdentity", prefs.focusIdentity)
                put("currentStreak", prefs.currentStreak)
                put("bestStreak", prefs.bestStreak)
                put("alarmRingtone", prefs.alarmRingtone)
                put("currentUserId", prefs.currentUserId)
                put("currentUserEmail", prefs.currentUserEmail)
                put("currentUserName", prefs.currentUserName)
                put("currentUserPhotoUrl", prefs.currentUserPhotoUrl)
                put("isGoogleAuth", prefs.isGoogleAuth)
                put("isAppBlockerEnabled", prefs.isAppBlockerEnabled)
                put("appBlockerDurationMinutes", prefs.appBlockerDurationMinutes)
                put("appBlockerEndTime", prefs.appBlockerEndTime)
                put("blockedAppsList", prefs.blockedAppsList)
                put("shieldBlockedAttempts", prefs.shieldBlockedAttempts)
                put("studentAge", prefs.studentAge)
                put("studentClass", prefs.studentClass)
                put("studentClassLevel", prefs.studentClassLevel)
                put("isBoardExamYear", prefs.isBoardExamYear)
                put("dailyScreenTimeGoalMinutes", prefs.dailyScreenTimeGoalMinutes)
                put("primaryStudyGoal", prefs.primaryStudyGoal)
                put("biggestDistractionApp", prefs.biggestDistractionApp)
                put("preferredStudyTimeWindow", prefs.preferredStudyTimeWindow)
                put("motivationStyle", prefs.motivationStyle)
                put("studentStream", prefs.studentStream)
                put("studySchedule", prefs.studySchedule)
                put("mobileBreakTime", prefs.mobileBreakTime)
                put("allowedEducationApps", prefs.allowedEducationApps)
                put("isAutoStudyBlockerEnabled", prefs.isAutoStudyBlockerEnabled)
                put("penaltyBlockEndTime", prefs.penaltyBlockEndTime)
                put("buddyGrowthStage", prefs.buddyGrowthStage)
                put("buddyTotalFocusMinutes", prefs.buddyTotalFocusMinutes)
                put("lastDonationPromptShownAt", prefs.lastDonationPromptShownAt)
                put("lastDonationPromptTimestamp", prefs.lastDonationPromptTimestamp)
                put("lastWeeklyReviewShownAt", prefs.lastWeeklyReviewShownAt)
                put("donationPromptDismissedCount", prefs.donationPromptDismissedCount)
                put("neverShowDonationPrompt", prefs.neverShowDonationPrompt)
                put("lastViewedSundayRecapWeek", prefs.lastViewedSundayRecapWeek)
                put("autoBlockStudyAppsWithoutAsking", prefs.autoBlockStudyAppsWithoutAsking)
            }
            localDataPrefs.edit().putString("user_preferences", obj.toString()).commit()
            GuestDataStorageManager.getInstance(context).saveGuestPreferences(prefs)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving preferences to local JSON: ${e.message}")
        }
    }

    private fun saveUserToLocalCache(user: UserAccountEntity) {
        val cleanEmail = user.email.trim().lowercase()
        userPrefs.edit().apply {
            putString("usr_email_${cleanEmail}_id", user.id.toString())
            putString("usr_email_${cleanEmail}_email", cleanEmail)
            putString("usr_email_${cleanEmail}_pwd", user.passwordHash)
            putString("usr_email_${cleanEmail}_name", user.fullName)
            putString("usr_email_${cleanEmail}_fbid", user.firebaseUid)
            putString("usr_email_${cleanEmail}_photo", user.photoUrl ?: "")
            putBoolean("usr_email_${cleanEmail}_google", user.isGoogleUser)
            putBoolean("usr_email_${cleanEmail}_verified", user.isEmailVerified)
            putLong("usr_email_${cleanEmail}_created", user.createdAt)

            // Index by user ID
            putString("usr_id_${user.id}_email", cleanEmail)

            // Keep set of registered emails
            val existingEmails = userPrefs.getStringSet("all_registered_emails", emptySet()) ?: emptySet()
            val updatedEmails = HashSet(existingEmails)
            updatedEmails.add(cleanEmail)
            putStringSet("all_registered_emails", updatedEmails)
            apply()
        }
    }

    private fun getUserFromLocalCache(email: String): UserAccountEntity? {
        val cleanEmail = email.trim().lowercase()
        val idStr = userPrefs.getString("usr_email_${cleanEmail}_id", null) ?: return null
        val id = idStr.toLongOrNull() ?: Math.abs(cleanEmail.hashCode().toLong())
        val pwd = userPrefs.getString("usr_email_${cleanEmail}_pwd", "") ?: ""
        val name = userPrefs.getString("usr_email_${cleanEmail}_name", "") ?: ""
        val fbid = userPrefs.getString("usr_email_${cleanEmail}_fbid", "") ?: ""
        val photo = userPrefs.getString("usr_email_${cleanEmail}_photo", null)?.takeIf { it.isNotBlank() }
        val isGoogle = userPrefs.getBoolean("usr_email_${cleanEmail}_google", false)
        val isVerified = userPrefs.getBoolean("usr_email_${cleanEmail}_verified", false)
        val created = userPrefs.getLong("usr_email_${cleanEmail}_created", System.currentTimeMillis())

        return UserAccountEntity(
            id = id,
            email = cleanEmail,
            passwordHash = pwd,
            fullName = name,
            firebaseUid = fbid,
            photoUrl = photo,
            isGoogleUser = isGoogle,
            isEmailVerified = isVerified,
            createdAt = created
        )
    }

    suspend fun getUserByEmail(email: String): UserAccountEntity? {
        return getUserFromLocalCache(email.trim().lowercase())
    }

    suspend fun getUserById(id: Long): UserAccountEntity? {
        val email = userPrefs.getString("usr_id_${id}_email", null)
        if (email != null) {
            return getUserFromLocalCache(email)
        }
        return null
    }

    suspend fun registerUser(user: UserAccountEntity): Long {
        val id = if (user.id != 0L) user.id else System.currentTimeMillis()
        val toSave = user.copy(id = id, email = user.email.trim().lowercase())
        saveUserToLocalCache(toSave)
        return id
    }

    suspend fun updateUser(user: UserAccountEntity) {
        saveUserToLocalCache(user)
        val uid = SupabaseService.getInstance().getCurrentUserId()
        if (!uid.isNullOrBlank()) {
            scope.launch {
                try {
                    // Targeted update: only these columns are written. (Previously a
                    // partial-DTO upsert wiped avatar_url / survey columns with nulls.)
                    val columns = mapOf(
                        "email" to user.email,
                        "full_name" to user.fullName,
                        "primary_goal" to user.primaryGoal,
                        "focus_style" to user.focusStyle,
                        "daily_target_hours" to user.dailyTargetHours,
                        "peak_productivity_time" to user.peakProductivityTime,
                        "primary_distraction" to user.primaryDistraction,
                        "sound_preference" to user.soundPreference,
                        "student_age" to user.studentAge,
                        "student_class" to user.studentClass,
                        "student_stream" to user.studentStream,
                        "study_schedule" to user.studySchedule,
                        "mobile_break_time" to user.mobileBreakTime
                    )
                    SupabaseService.getInstance().updateUserProfileColumns(uid, columns)
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating Supabase user profile: ${e.message}")
                }
            }
        }
    }

    suspend fun insertTask(task: TaskEntity): Long {
        val id = if (task.id != 0L) task.id else System.currentTimeMillis()
        val newTask = task.copy(id = id)
        val updatedList = listOf(newTask) + _allTasks.value.filterNot { it.id == id }
        _allTasks.value = updatedList
        saveTasksToLocal(updatedList)

        val uid = SupabaseService.getInstance().getCurrentUserId()
        if (!uid.isNullOrBlank()) {
            scope.launch {
                try {
                    val dto = SupabaseTaskDto(
                        id = java.util.UUID.nameUUIDFromBytes("task_$id".toByteArray()).toString(),
                        user_id = uid,
                        title = newTask.title,
                        category = newTask.category,
                        priority = newTask.priority,
                        duration_minutes = newTask.durationMinutes,
                        scheduled_time = newTask.scheduledTime,
                        is_completed = newTask.isCompleted,
                        is_top_priority = newTask.isTopPriority,
                        notes = newTask.notes,
                        date = newTask.date
                    )
                    logSupabaseResult(SupabaseService.getInstance().upsertTask(dto), "upsertTask", dto.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error inserting task to Supabase: ${e.message}")
                }
            }
        }
        return id
    }

    suspend fun updateTask(task: TaskEntity) {
        val updatedList = _allTasks.value.map { if (it.id == task.id) task else it }
        _allTasks.value = updatedList
        saveTasksToLocal(updatedList)

        val uid = SupabaseService.getInstance().getCurrentUserId()
        if (!uid.isNullOrBlank()) {
            scope.launch {
                try {
                    val dto = SupabaseTaskDto(
                        id = java.util.UUID.nameUUIDFromBytes("task_${task.id}".toByteArray()).toString(),
                        user_id = uid,
                        title = task.title,
                        category = task.category,
                        priority = task.priority,
                        duration_minutes = task.durationMinutes,
                        scheduled_time = task.scheduledTime,
                        is_completed = task.isCompleted,
                        is_top_priority = task.isTopPriority,
                        notes = task.notes,
                        date = task.date
                    )
                    logSupabaseResult(SupabaseService.getInstance().upsertTask(dto), "upsertTask", dto.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating task to Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteTask(task: TaskEntity) {
        val updatedList = _allTasks.value.filterNot { it.id == task.id }
        _allTasks.value = updatedList
        saveTasksToLocal(updatedList)

        val uid = SupabaseService.getInstance().getCurrentUserId()
        if (!uid.isNullOrBlank()) {
            scope.launch {
                try {
                    val taskId = java.util.UUID.nameUUIDFromBytes("task_${task.id}".toByteArray()).toString()
                    SupabaseService.getInstance().deleteTask(taskId)
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting task from Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun setTaskCompletion(id: Long, completed: Boolean) {
        val updatedList = _allTasks.value.map { if (it.id == id) it.copy(isCompleted = completed) else it }
        _allTasks.value = updatedList
        saveTasksToLocal(updatedList)

        val task = updatedList.find { it.id == id }
        val uid = SupabaseService.getInstance().getCurrentUserId()
        if (task != null && !uid.isNullOrBlank()) {
            scope.launch {
                try {
                    val dto = SupabaseTaskDto(
                        id = java.util.UUID.nameUUIDFromBytes("task_$id".toByteArray()).toString(),
                        user_id = uid,
                        title = task.title,
                        category = task.category,
                        priority = task.priority,
                        duration_minutes = task.durationMinutes,
                        scheduled_time = task.scheduledTime,
                        is_completed = task.isCompleted,
                        is_top_priority = task.isTopPriority,
                        notes = task.notes,
                        date = task.date
                    )
                    logSupabaseResult(SupabaseService.getInstance().upsertTask(dto), "upsertTask", dto.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting task completion on Supabase: ${e.message}")
                }
            }
        }
    }

    suspend fun recordSession(session: FocusSessionEntity): Long {
        val id = if (session.id != 0L) session.id else System.currentTimeMillis()
        val newSession = session.copy(id = id)
        val updatedList = listOf(newSession) + _allSessions.value.filterNot { it.id == id }
        _allSessions.value = updatedList
        saveSessionsToLocal(updatedList)

        val uid = SupabaseService.getInstance().getCurrentUserId()
        if (!uid.isNullOrBlank()) {
            scope.launch {
                try {
                    val dto = SupabaseFocusSessionDto(
                        id = java.util.UUID.nameUUIDFromBytes("session_$id".toByteArray()).toString(),
                        user_id = uid,
                        task_title = newSession.taskTitle,
                        duration_seconds = newSession.durationSeconds,
                        target_duration_seconds = newSession.targetDurationSeconds,
                        mode = newSession.mode,
                        distractions_count = newSession.distractionsCount,
                        distraction_types = newSession.distractionTypes,
                        focus_points_earned = newSession.focusPointsEarned,
                        completed_at = newSession.completedAt,
                        day_of_week = newSession.dayOfWeek,
                        hour_of_day = newSession.hourOfDay,
                        notes = newSession.notes
                    )
                    logSupabaseResult(SupabaseService.getInstance().upsertFocusSession(dto), "upsertFocusSession", dto.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error recording session on Supabase: ${e.message}")
                }
            }
        }

        // Firestore Cloud Database Storage
        scope.launch {
            try {
                val firebaseUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid ?: ""
                if (firebaseUid.isNotBlank()) {
                    FirestoreSessionRepository.getInstance().saveSession(newSession, firebaseUid)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error saving session to Firestore: ${e.message}")
            }
        }
        return id
    }

    suspend fun deleteSession(sessionId: Long) {
        val updatedList = _allSessions.value.filterNot { it.id == sessionId }
        _allSessions.value = updatedList
        saveSessionsToLocal(updatedList)

        scope.launch {
            try {
                val firebaseUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                if (firebaseUid.isNotBlank()) {
                    FirestoreSessionRepository.getInstance().deleteSession(sessionId, firebaseUid)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting session in Firestore: ${e.message}")
            }
            // Also delete from Supabase, otherwise the row resurrects on next sync.
            try {
                val sessionUuid = java.util.UUID.nameUUIDFromBytes("session_$sessionId".toByteArray()).toString()
                val res = SupabaseService.getInstance().deleteFocusSession(sessionUuid)
                if (res.isFailure) Log.w(TAG, "Supabase deleteSession failed: ${res.exceptionOrNull()?.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting session in Supabase: ${e.message}")
            }
        }
    }

    suspend fun insertReflection(reflection: ReflectionEntity): Long {
        val id = if (reflection.id != 0L) reflection.id else System.currentTimeMillis()
        val newReflection = reflection.copy(id = id)
        val updatedList = listOf(newReflection) + _allReflections.value.filterNot { it.id == id }
        _allReflections.value = updatedList
        saveReflectionsToLocal(updatedList)

        val uid = SupabaseService.getInstance().getCurrentUserId()
        if (!uid.isNullOrBlank()) {
            scope.launch {
                try {
                    val dto = SupabaseReflectionDto(
                        id = java.util.UUID.nameUUIDFromBytes("reflection_$id".toByteArray()).toString(),
                        user_id = uid,
                        week_label = newReflection.weekLabel,
                        total_minutes_focused = newReflection.totalMinutesFocused,
                        sessions_completed = newReflection.sessionsCompleted,
                        best_day = newReflection.bestDay,
                        completion_rate = newReflection.completionRate,
                        reflection_text = newReflection.reflectionText
                    )
                    logSupabaseResult(SupabaseService.getInstance().upsertReflection(dto), "upsertReflection", dto.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error inserting reflection on Supabase: ${e.message}")
                }
            }
        }
        return id
    }

    fun savePreferences(preferences: UserPreferencesEntity) {
        _userPreferences.value = preferences
        savePreferencesToLocal(preferences)

        val resolvedUid = SupabaseService.getInstance().getCurrentUserId()
            ?: AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.getStoredUserId(it) }
            ?: (preferences.currentUserEmail?.let { getUserFromLocalCache(it)?.firebaseUid })?.takeIf { it.isNotBlank() }
            ?: (preferences.currentUserId?.let { userPrefs.getString("usr_id_${it}_email", null) }?.let { getUserFromLocalCache(it)?.firebaseUid })?.takeIf { it.isNotBlank() }
            ?: preferences.currentUserEmail?.let { java.util.UUID.nameUUIDFromBytes("google_$it".toByteArray()).toString() }

        if (!resolvedUid.isNullOrBlank()) {
            scope.launch {
                try {
                    val dto = SupabaseUserPreferencesDto(
                        user_id = resolvedUid,
                        theme_mode = preferences.themeMode,
                        daily_goal_minutes = preferences.dailyGoalMinutes,
                        preferred_focus_mode = preferences.preferredFocusMode,
                        deep_work_duration = preferences.deepWorkDuration,
                        deep_work_break = preferences.deepWorkBreak,
                        classic_duration = preferences.classicDuration,
                        classic_break = preferences.classicBreak,
                        short_sprint_duration = preferences.shortSprintDuration,
                        sound_enabled = preferences.soundEnabled,
                        ambient_sound = preferences.ambientSound,
                        haptics_enabled = preferences.hapticsEnabled,
                        notifications_enabled = preferences.notificationsEnabled,
                        auto_start_break = preferences.autoStartBreak,
                        auto_start_focus = preferences.autoStartFocus,
                        week_starts_on = preferences.weekStartsOn,
                        has_completed_onboarding = preferences.hasCompletedOnboarding || preferences.hasCompletedIntakeSurvey,
                        user_level = preferences.userLevel,
                        focus_points = preferences.focusPoints,
                        focus_identity = preferences.focusIdentity,
                        current_streak = preferences.currentStreak,
                        best_streak = preferences.bestStreak,
                        alarm_ringtone = preferences.alarmRingtone,
                        is_app_blocker_enabled = preferences.isAppBlockerEnabled,
                        app_blocker_duration_minutes = preferences.appBlockerDurationMinutes,
                        app_blocker_end_time = preferences.appBlockerEndTime,
                        blocked_apps_list = preferences.blockedAppsList,
                        shield_blocked_attempts = preferences.shieldBlockedAttempts,
                        allowed_education_apps = preferences.allowedEducationApps,
                        is_auto_study_blocker_enabled = preferences.isAutoStudyBlockerEnabled,
                        penalty_block_end_time = preferences.penaltyBlockEndTime,
                        buddy_growth_stage = preferences.buddyGrowthStage,
                        buddy_total_focus_minutes = preferences.buddyTotalFocusMinutes,
                        last_donation_prompt_shown_at = formatMillisToIso(preferences.lastDonationPromptShownAt),
                        donation_prompt_dismissed_count = preferences.donationPromptDismissedCount,
                        never_show_donation_prompt = preferences.neverShowDonationPrompt,
                        last_viewed_sunday_recap_week = preferences.lastViewedSundayRecapWeek
                    )
                    logSupabaseResult(SupabaseService.getInstance().upsertUserPreferences(dto), "upsertUserPreferences", dto.user_id)

                    // Also sync to public.study_leaderboard with real accumulated session study duration
                    val emailPart = preferences.currentUserEmail?.substringBefore("@")
                    val displayName = preferences.currentUserName?.ifBlank { emailPart } ?: emailPart ?: "Student"
                    val avatar = preferences.currentUserPhotoUrl
                        ?: AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.getStoredAvatarUrl(it) }

                    if (!avatar.isNullOrBlank()) {
                        AndroidPreferenceSessionManager.appContext?.let {
                            AndroidPreferenceSessionManager.setStoredAvatarUrl(it, avatar)
                        }
                    }

                    val totalActualStudySeconds = _allSessions.value.sumOf { it.durationSeconds }.toLong()
                    val effectiveStudySeconds = totalActualStudySeconds
                    // Weekly total: only sessions completed since Monday 00:00 local time.
                    // (Previously the LIFETIME total was written into weekly_study_seconds,
                    // corrupting weekly rankings and Hall of Fame.)
                    val weekStartMs = run {
                        val cal = java.util.Calendar.getInstance()
                        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                        val daysToSubtract = (dayOfWeek - java.util.Calendar.MONDAY + 7) % 7
                        cal.add(java.util.Calendar.DAY_OF_MONTH, -daysToSubtract)
                        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        cal.set(java.util.Calendar.MINUTE, 0)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        cal.timeInMillis
                    }
                    val weeklyStudySeconds = _allSessions.value
                        .filter { it.completedAt >= weekStartMs }
                        .sumOf { it.durationSeconds }.toLong()

                    SupabaseService.getInstance().upsertLeaderboard(
                        SupabaseStudyLeaderboardDto(
                            user_id = resolvedUid,
                            display_name = displayName,
                            study_seconds = effectiveStudySeconds,
                            weekly_study_seconds = weeklyStudySeconds,
                            current_week_start = LeaderboardDateUtils.getCurrentWeekMonday(),
                            streak = preferences.currentStreak.coerceAtLeast(1),
                            subject_tag = preferences.primaryStudyGoal.ifBlank { "Study" },
                            avatar_url = avatar
                        )
                    )

                    if (!avatar.isNullOrBlank()) {
                        SupabaseService.getInstance().updateUserAvatarUrl(resolvedUid, avatar)
                    }

                    FirebaseSyncManager.getInstance(context).recordUserStudyProgress(
                        userId = resolvedUid,
                        displayName = displayName,
                        studySeconds = effectiveStudySeconds,
                        weeklyStudySeconds = weeklyStudySeconds,
                        streak = preferences.currentStreak.coerceAtLeast(1),
                        subjectTag = preferences.primaryStudyGoal.ifBlank { "Study" },
                        avatarUrl = avatar
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error saving preferences to Supabase: ${e.message}")
                }
            }
        }
    }

    private fun calculateConsecutiveStreak(sessions: List<FocusSessionEntity>): Pair<Int, Int> {
        if (sessions.isEmpty()) return Pair(0, 0)

        val dayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val sessionDays = sessions.map { dayFormat.format(java.util.Date(it.completedAt)) }.toSet()

        val cal = java.util.Calendar.getInstance()
        val todayStr = dayFormat.format(cal.time)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = dayFormat.format(cal.time)

        var currentStreak = 0
        if (sessionDays.contains(todayStr) || sessionDays.contains(yesterdayStr)) {
            val checkCal = java.util.Calendar.getInstance()
            if (!sessionDays.contains(todayStr)) {
                checkCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            while (sessionDays.contains(dayFormat.format(checkCal.time))) {
                currentStreak++
                checkCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
        }

        val sortedDates = sessionDays.mapNotNull {
            try { dayFormat.parse(it) } catch (_: Exception) { null }
        }.sorted()

        var maxStreak = currentStreak
        var streakCount = 0
        var lastDate: java.util.Date? = null

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

    suspend fun syncWithSupabase(userUuid: String) = withContext(Dispatchers.IO) {
        if (userUuid.isBlank()) return@withContext
        try {
                // 1. Tasks: Bidirectional merge between remote and local
                val remoteTasks = SupabaseService.getInstance().fetchUserTasks(userUuid)
                val mappedRemoteTasks = remoteTasks.map { dto ->
                    TaskEntity(
                        id = Math.abs(dto.id.hashCode().toLong()).let { if (it == 0L) System.currentTimeMillis() else it },
                        title = dto.title,
                        category = dto.category,
                        priority = dto.priority,
                        durationMinutes = dto.duration_minutes,
                        scheduledTime = dto.scheduled_time,
                        isCompleted = dto.is_completed,
                        isTopPriority = dto.is_top_priority,
                        notes = dto.notes,
                        date = dto.date
                    )
                }
                val localTasks = _allTasks.value
                val remoteTitles = mappedRemoteTasks.map { it.title.trim().lowercase() }.toSet()
                val localOnlyTasks = localTasks.filterNot { it.title.trim().lowercase() in remoteTitles }

                // Upload any tasks created offline so they exist on Supabase
                localOnlyTasks.forEach { task ->
                    val dto = SupabaseTaskDto(
                        id = java.util.UUID.nameUUIDFromBytes("task_${task.id}".toByteArray()).toString(),
                        user_id = userUuid,
                        title = task.title,
                        category = task.category,
                        priority = task.priority,
                        duration_minutes = task.durationMinutes,
                        scheduled_time = task.scheduledTime,
                        is_completed = task.isCompleted,
                        is_top_priority = task.isTopPriority,
                        notes = task.notes,
                        date = task.date
                    )
                    logSupabaseResult(SupabaseService.getInstance().upsertTask(dto), "upsertTask", dto.id)
                }
                val mergedTasks = mappedRemoteTasks + localOnlyTasks
                _allTasks.value = mergedTasks
                saveTasksToLocal(mergedTasks)

                // 2. Focus Sessions: Bidirectional merge
                val remoteSessions = SupabaseService.getInstance().fetchFocusSessions(userUuid)
                val mappedRemoteSessions = remoteSessions.map { dto ->
                    FocusSessionEntity(
                        id = Math.abs(dto.id.hashCode().toLong()).let { if (it == 0L) System.currentTimeMillis() else it },
                        taskTitle = dto.task_title,
                        durationSeconds = dto.duration_seconds,
                        targetDurationSeconds = dto.target_duration_seconds,
                        mode = dto.mode,
                        distractionsCount = dto.distractions_count,
                        distractionTypes = dto.distraction_types,
                        focusPointsEarned = dto.focus_points_earned,
                        completedAt = dto.completed_at ?: System.currentTimeMillis(),
                        dayOfWeek = dto.day_of_week,
                        hourOfDay = dto.hour_of_day,
                        notes = dto.notes
                    )
                }
                val localSessions = _allSessions.value
                val remoteTimestamps = mappedRemoteSessions.map { it.completedAt }.toSet()
                val localOnlySessions = localSessions.filterNot { loc ->
                    remoteTimestamps.any { Math.abs(it - loc.completedAt) < 2000 }
                }

                // Upload any offline sessions to Supabase
                localOnlySessions.forEach { session ->
                    val dto = SupabaseFocusSessionDto(
                        id = java.util.UUID.nameUUIDFromBytes("session_${session.id}".toByteArray()).toString(),
                        user_id = userUuid,
                        task_title = session.taskTitle,
                        duration_seconds = session.durationSeconds,
                        target_duration_seconds = session.targetDurationSeconds,
                        mode = session.mode,
                        distractions_count = session.distractionsCount,
                        distraction_types = session.distractionTypes,
                        focus_points_earned = session.focusPointsEarned,
                        completed_at = session.completedAt,
                        day_of_week = session.dayOfWeek,
                        hour_of_day = session.hourOfDay,
                        notes = session.notes
                    )
                    logSupabaseResult(SupabaseService.getInstance().upsertFocusSession(dto), "upsertFocusSession", dto.id)
                }
                val mergedSessions = (mappedRemoteSessions + localOnlySessions).sortedByDescending { it.completedAt }
                _allSessions.value = mergedSessions
                saveSessionsToLocal(mergedSessions)

                // 3. Reflections
                val remoteReflections = SupabaseService.getInstance().fetchReflections(userUuid)
                if (remoteReflections.isNotEmpty()) {
                    val mappedReflections = remoteReflections.map { dto ->
                        ReflectionEntity(
                            id = Math.abs(dto.id.hashCode().toLong()).let { if (it == 0L) System.currentTimeMillis() else it },
                            weekLabel = dto.week_label,
                            totalMinutesFocused = dto.total_minutes_focused,
                            sessionsCompleted = dto.sessions_completed,
                            bestDay = dto.best_day,
                            completionRate = dto.completion_rate,
                            reflectionText = dto.reflection_text
                        )
                    }
                    val localReflections = _allReflections.value
                    val remoteWeeks = mappedReflections.map { it.weekLabel }.toSet()
                    val localOnly = localReflections.filterNot { it.weekLabel in remoteWeeks }
                    localOnly.forEach { ref ->
                        val dto = SupabaseReflectionDto(
                            id = java.util.UUID.nameUUIDFromBytes("reflection_${ref.id}".toByteArray()).toString(),
                            user_id = userUuid,
                            week_label = ref.weekLabel,
                            total_minutes_focused = ref.totalMinutesFocused,
                            sessions_completed = ref.sessionsCompleted,
                            best_day = ref.bestDay,
                            completion_rate = ref.completionRate,
                            reflection_text = ref.reflectionText
                        )
                        logSupabaseResult(SupabaseService.getInstance().upsertReflection(dto), "upsertReflection", dto.id)
                    }
                    val mergedReflections = mappedReflections + localOnly
                    _allReflections.value = mergedReflections
                    saveReflectionsToLocal(mergedReflections)
                } else {
                    _allReflections.value.forEach { reflection ->
                        val dto = SupabaseReflectionDto(
                            id = java.util.UUID.nameUUIDFromBytes("reflection_${reflection.id}".toByteArray()).toString(),
                            user_id = userUuid,
                            week_label = reflection.weekLabel,
                            total_minutes_focused = reflection.totalMinutesFocused,
                            sessions_completed = reflection.sessionsCompleted,
                            best_day = reflection.bestDay,
                            completion_rate = reflection.completionRate,
                            reflection_text = reflection.reflectionText
                        )
                        logSupabaseResult(SupabaseService.getInstance().upsertReflection(dto), "upsertReflection", dto.id)
                    }
                }

                // 4. Preferences & Leaderboard Recovery
                val remotePrefs = SupabaseService.getInstance().fetchUserPreferences(userUuid)
                val remoteProfile = SupabaseService.getInstance().fetchUserProfile(userUuid)
                val leaderboardList = SupabaseService.getInstance().fetchLeaderboard()
                val myLeaderboardRow = leaderboardList.find { it.user_id == userUuid }

                val profileName = remoteProfile?.full_name?.takeIf { it.isNotBlank() } ?: myLeaderboardRow?.display_name?.takeIf { it.isNotBlank() && it != "Student" }
                val profileEmail = remoteProfile?.email?.takeIf { it.isNotBlank() }
                val profilePhoto = remoteProfile?.avatar_url?.takeIf { it.isNotBlank() }
                    ?: myLeaderboardRow?.avatar_url?.takeIf { it.isNotBlank() }

                val (calcCurrentStreak, calcBestStreak) = calculateConsecutiveStreak(_allSessions.value)
                val totalSessionsPoints = _allSessions.value.sumOf { it.focusPointsEarned }
                val localPrefs = _userPreferences.value ?: UserPreferencesEntity()

                val isRemoteSurveyDone = (remoteProfile?.has_completed_intake_survey == true) ||
                    (remotePrefs?.has_completed_onboarding == true) ||
                    ((remotePrefs?.focus_points ?: 0) > 0) ||
                    _allSessions.value.isNotEmpty() ||
                    _allTasks.value.isNotEmpty() ||
                    !remoteProfile?.primary_study_goal.isNullOrBlank() ||
                    !remoteProfile?.student_class_level.isNullOrBlank()

                val resolvedSurveyCompleted = isRemoteSurveyDone || localPrefs.hasCompletedIntakeSurvey || localPrefs.hasCompletedOnboarding

                val resolvedPoints = maxOf(remotePrefs?.focus_points ?: 0, totalSessionsPoints, localPrefs.focusPoints)
                val resolvedLevel = maxOf(1, remotePrefs?.user_level ?: 1, localPrefs.userLevel, (resolvedPoints / 100) + 1)
                val resolvedCurrentStreak = maxOf(calcCurrentStreak, remotePrefs?.current_streak ?: 0, localPrefs.currentStreak)
                val resolvedBestStreak = maxOf(calcBestStreak, remotePrefs?.best_streak ?: 0, localPrefs.bestStreak, resolvedCurrentStreak)
                val remoteShownAtMs = parseIsoToMillis(remotePrefs?.last_donation_prompt_shown_at)
                val resolvedShownAt = maxOf(localPrefs.lastDonationPromptShownAt, remoteShownAtMs)
                val resolvedDismissedCount = maxOf(localPrefs.donationPromptDismissedCount, remotePrefs?.donation_prompt_dismissed_count ?: 0)
                val resolvedNeverShowDonation = localPrefs.neverShowDonationPrompt

                if (remotePrefs != null) {
                    val merged = localPrefs.copy(
                        themeMode = remotePrefs.theme_mode,
                        dailyGoalMinutes = if (remotePrefs.daily_goal_minutes > 0) remotePrefs.daily_goal_minutes else (remoteProfile?.daily_screen_time_goal_minutes ?: localPrefs.dailyGoalMinutes),
                        preferredFocusMode = remotePrefs.preferred_focus_mode,
                        deepWorkDuration = remotePrefs.deep_work_duration,
                        deepWorkBreak = remotePrefs.deep_work_break,
                        classicDuration = remotePrefs.classic_duration,
                        classicBreak = remotePrefs.classic_break,
                        shortSprintDuration = remotePrefs.short_sprint_duration,
                        soundEnabled = remotePrefs.sound_enabled,
                        ambientSound = remotePrefs.ambient_sound,
                        hapticsEnabled = remotePrefs.haptics_enabled,
                        notificationsEnabled = remotePrefs.notifications_enabled,
                        autoStartBreak = remotePrefs.auto_start_break,
                        autoStartFocus = remotePrefs.auto_start_focus,
                        weekStartsOn = remotePrefs.week_starts_on,
                        hasCompletedOnboarding = resolvedSurveyCompleted,
                        hasCompletedIntakeSurvey = resolvedSurveyCompleted,
                        userLevel = resolvedLevel,
                        focusPoints = resolvedPoints,
                        focusIdentity = remotePrefs.focus_identity.takeIf { it.isNotBlank() } ?: localPrefs.focusIdentity,
                        currentStreak = resolvedCurrentStreak,
                        bestStreak = resolvedBestStreak,
                        alarmRingtone = remotePrefs.alarm_ringtone,
                        isAppBlockerEnabled = localPrefs.isAppBlockerEnabled || remotePrefs.is_app_blocker_enabled,
                        appBlockerDurationMinutes = if (remotePrefs.app_blocker_duration_minutes > 0) remotePrefs.app_blocker_duration_minutes else localPrefs.appBlockerDurationMinutes,
                        appBlockerEndTime = maxOf(localPrefs.appBlockerEndTime, remotePrefs.app_blocker_end_time),
                        blockedAppsList = (localPrefs.blockedAppsList.split(",") + remotePrefs.blocked_apps_list.split(","))
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(","),
                        shieldBlockedAttempts = maxOf(localPrefs.shieldBlockedAttempts, remotePrefs.shield_blocked_attempts),
                        allowedEducationApps = if (remotePrefs.allowed_education_apps.isNotBlank()) remotePrefs.allowed_education_apps else localPrefs.allowedEducationApps,
                        isAutoStudyBlockerEnabled = localPrefs.isAutoStudyBlockerEnabled || remotePrefs.is_auto_study_blocker_enabled,
                        penaltyBlockEndTime = maxOf(localPrefs.penaltyBlockEndTime, remotePrefs.penalty_block_end_time),
                        buddyGrowthStage = maxOf(localPrefs.buddyGrowthStage, remotePrefs.buddy_growth_stage),
                        buddyTotalFocusMinutes = maxOf(localPrefs.buddyTotalFocusMinutes, remotePrefs.buddy_total_focus_minutes),
                        currentUserName = profileName ?: localPrefs.currentUserName,
                        currentUserEmail = profileEmail ?: localPrefs.currentUserEmail,
                        currentUserPhotoUrl = profilePhoto ?: localPrefs.currentUserPhotoUrl,
                        studentClass = remoteProfile?.student_class_level ?: remoteProfile?.student_class ?: localPrefs.studentClass,
                        studentClassLevel = remoteProfile?.student_class_level ?: localPrefs.studentClassLevel,
                        isBoardExamYear = remoteProfile?.is_board_exam_year ?: localPrefs.isBoardExamYear,
                        dailyScreenTimeGoalMinutes = remoteProfile?.daily_screen_time_goal_minutes ?: localPrefs.dailyScreenTimeGoalMinutes,
                        primaryStudyGoal = remoteProfile?.primary_study_goal ?: remoteProfile?.primary_goal ?: localPrefs.primaryStudyGoal,
                        biggestDistractionApp = remoteProfile?.biggest_distraction_app ?: remoteProfile?.primary_distraction ?: localPrefs.biggestDistractionApp,
                        preferredStudyTimeWindow = remoteProfile?.preferred_study_time_window ?: remoteProfile?.study_schedule ?: localPrefs.preferredStudyTimeWindow,
                        motivationStyle = remoteProfile?.motivation_style ?: localPrefs.motivationStyle,
                        lastDonationPromptShownAt = resolvedShownAt,
                        donationPromptDismissedCount = resolvedDismissedCount,
                        neverShowDonationPrompt = resolvedNeverShowDonation,
                        lastViewedSundayRecapWeek = remotePrefs.last_viewed_sunday_recap_week ?: localPrefs.lastViewedSundayRecapWeek
                    )
                    _userPreferences.value = merged
                    savePreferencesToLocal(merged)

                    if (resolvedPoints > remotePrefs.focus_points || (remotePrefs.current_streak == 0 && resolvedCurrentStreak > 0) || (!remotePrefs.has_completed_onboarding && resolvedSurveyCompleted)) {
                        savePreferences(merged)
                    }
                } else if (profileName != null || profileEmail != null || profilePhoto != null) {
                    val merged = localPrefs.copy(
                        currentUserName = profileName ?: localPrefs.currentUserName,
                        currentUserEmail = profileEmail ?: localPrefs.currentUserEmail,
                        currentUserPhotoUrl = profilePhoto ?: localPrefs.currentUserPhotoUrl,
                        currentStreak = resolvedCurrentStreak,
                        bestStreak = resolvedBestStreak,
                        focusPoints = resolvedPoints,
                        userLevel = resolvedLevel,
                        hasCompletedOnboarding = resolvedSurveyCompleted,
                        hasCompletedIntakeSurvey = resolvedSurveyCompleted
                    )
                    _userPreferences.value = merged
                    savePreferencesToLocal(merged)
                    savePreferences(merged)
                } else {
                    _userPreferences.value?.let { prefs ->
                        val dto = SupabaseUserPreferencesDto(
                            user_id = userUuid,
                            theme_mode = prefs.themeMode,
                            daily_goal_minutes = prefs.dailyGoalMinutes,
                            preferred_focus_mode = prefs.preferredFocusMode,
                            deep_work_duration = prefs.deepWorkDuration,
                            deep_work_break = prefs.deepWorkBreak,
                            classic_duration = prefs.classicDuration,
                            classic_break = prefs.classicBreak,
                            short_sprint_duration = prefs.shortSprintDuration,
                            sound_enabled = prefs.soundEnabled,
                            ambient_sound = prefs.ambientSound,
                            haptics_enabled = prefs.hapticsEnabled,
                            notifications_enabled = prefs.notificationsEnabled,
                            auto_start_break = prefs.autoStartBreak,
                            auto_start_focus = prefs.autoStartFocus,
                            week_starts_on = prefs.weekStartsOn,
                            has_completed_onboarding = prefs.hasCompletedOnboarding,
                            user_level = prefs.userLevel,
                            focus_points = prefs.focusPoints,
                            focus_identity = prefs.focusIdentity,
                            current_streak = prefs.currentStreak,
                            best_streak = prefs.bestStreak,
                            alarm_ringtone = prefs.alarmRingtone,
                            is_app_blocker_enabled = prefs.isAppBlockerEnabled,
                            app_blocker_duration_minutes = prefs.appBlockerDurationMinutes,
                            app_blocker_end_time = prefs.appBlockerEndTime,
                            blocked_apps_list = prefs.blockedAppsList,
                            shield_blocked_attempts = prefs.shieldBlockedAttempts,
                            allowed_education_apps = prefs.allowedEducationApps,
                            is_auto_study_blocker_enabled = prefs.isAutoStudyBlockerEnabled,
                            penalty_block_end_time = prefs.penaltyBlockEndTime,
                            buddy_growth_stage = prefs.buddyGrowthStage,
                            buddy_total_focus_minutes = prefs.buddyTotalFocusMinutes,
                            last_donation_prompt_shown_at = formatMillisToIso(prefs.lastDonationPromptShownAt),
                            donation_prompt_dismissed_count = prefs.donationPromptDismissedCount,
                            never_show_donation_prompt = prefs.neverShowDonationPrompt,
                            last_viewed_sunday_recap_week = prefs.lastViewedSundayRecapWeek
                        )
                        logSupabaseResult(SupabaseService.getInstance().upsertUserPreferences(dto), "upsertUserPreferences", dto.user_id)
                    }
                }

                if (resolvedSurveyCompleted && remoteProfile?.has_completed_intake_survey != true) {
                    try {
                        SupabaseService.getInstance().updateUserSurveyCompleted(
                            userId = userUuid,
                            isCompleted = true,
                            name = profileName,
                            studentClassLevel = remoteProfile?.student_class_level,
                            primaryStudyGoal = remoteProfile?.primary_study_goal,
                            biggestDistractionApp = remoteProfile?.biggest_distraction_app,
                            preferredStudyTimeWindow = remoteProfile?.preferred_study_time_window,
                            dailyScreenTimeGoalMinutes = remoteProfile?.daily_screen_time_goal_minutes,
                            motivationStyle = remoteProfile?.motivation_style
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating user survey flag to Supabase: ${e.message}")
                    }
                }

                // 5. App Daily Limits
                try {
                    syncAppDailyLimitsFromSupabase(userUuid)
                } catch (e: Exception) {
                    Log.w(TAG, "Error syncing app daily limits from Supabase: ${e.message}")
                }

                // 6. Scheduled Blocks
                try {
                    val remoteSchedules = SupabaseService.getInstance().fetchScheduledBlocks(userUuid)
                    com.example.service.ScheduledBlockScheduler.syncFromRemote(context, remoteSchedules)
                } catch (e: Exception) {
                    Log.w(TAG, "Error syncing scheduled blocks from Supabase: ${e.message}")
                }

                // 7. Alarms
                try {
                    val dummyLabels = setOf("Study Session Alert", "Deep Work Reminder", "Break Over - Time to Study")
                    val remoteAlarms = SupabaseService.getInstance().fetchAlarms(userUuid).filterNot { it.label in dummyLabels }
                    val localAlarms = com.example.service.AlarmScheduler.getLocalAlarms(context)
                    if (remoteAlarms.isNotEmpty()) {
                        val mappedAlarms = remoteAlarms.map { dto ->
                            com.example.data.model.AlarmItem(
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
                        com.example.service.AlarmScheduler.saveLocalAlarms(context, mappedAlarms)
                        com.example.service.AlarmScheduler.rescheduleAllEnabled(context, mappedAlarms)
                    } else if (localAlarms.isNotEmpty()) {
                        localAlarms.forEach { local ->
                            val dto = SupabaseAlarmDto(
                                id = local.id,
                                user_id = userUuid,
                                label = local.label,
                                hour = local.hour,
                                minute = local.minute,
                                days_active = local.daysActive,
                                ringtone = local.ringtone,
                                is_enabled = local.isEnabled,
                                vibrate = local.vibrate
                            )
                            logSupabaseResult(SupabaseService.getInstance().upsertAlarm(dto), "upsertAlarm", dto.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error syncing alarms from Supabase: ${e.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error during syncWithSupabase: ${e.message}")
            }
        }

    fun clearAllData() {
        _allTasks.value = emptyList()
        _allSessions.value = emptyList()
        _allReflections.value = emptyList()
        localDataPrefs.edit().clear().apply()
        userPrefs.edit().clear().apply()

        try {
            com.example.service.ScheduledBlockScheduler.clearLocalSchedules(context)
            com.example.service.AlarmScheduler.clearLocalAlarms(context)
            com.example.data.SupabaseService.getInstance().clearCache()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing local cache components: ${e.message}")
        }

        val defaultLocalPrefs = UserPreferencesEntity(
            id = 1,
            hasCompletedOnboarding = false,
            hasCompletedIntakeSurvey = false,
            focusPoints = 0,
            currentStreak = 0,
            bestStreak = 0,
            userLevel = 1,
            focusIdentity = "Novice Deep Worker"
        )
        _userPreferences.value = defaultLocalPrefs
        savePreferencesToLocal(defaultLocalPrefs)
        // CRITICAL: NEVER call savePreferences() during clearAllData()! It would wipe remote user data in Supabase on logout!
    }

    fun populateDemoData() {
        _allTasks.value = emptyList()
        saveTasksToLocal(emptyList())

        _allSessions.value = emptyList()
        saveSessionsToLocal(emptyList())

        _allReflections.value = emptyList()
        saveReflectionsToLocal(emptyList())

        val cleanPrefs = UserPreferencesEntity(
            id = 1,
            themeMode = "system",
            dailyGoalMinutes = 240,
            preferredFocusMode = "Deep Work",
            hasCompletedOnboarding = false,
            userLevel = 1,
            focusPoints = 0,
            focusIdentity = "Novice Deep Worker",
            currentStreak = 0,
            bestStreak = 0,
            soundEnabled = true,
            ambientSound = "Silent",
            hapticsEnabled = true
        )
        savePreferences(cleanPrefs)
    }

    private fun formatMillisToIso(millis: Long): String? {
        if (millis <= 0L) return null
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(millis))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseIsoToMillis(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return 0L
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(isoString.take(19))?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // --- APP DAILY LIMITS METHODS ---

    fun loadAppDailyLimitsFromLocal(): List<AppDailyLimitEntity> {
        val jsonStr = localDataPrefs.getString("app_daily_limits", "[]") ?: "[]"
        val list = mutableListOf<AppDailyLimitEntity>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val item = AppDailyLimitEntity(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    userId = obj.optString("userId", ""),
                    appPackage = obj.optString("appPackage", ""),
                    appName = obj.optString("appName", ""),
                    dailyLimitMinutes = obj.optInt("dailyLimitMinutes", 60),
                    emergencyUsesAllowed = obj.optInt("emergencyUsesAllowed", 2),
                    emergencyUsesRemainingToday = obj.optInt("emergencyUsesRemainingToday", 2),
                    showReminders = obj.optBoolean("showReminders", true),
                    strictModeEnabled = obj.optBoolean("strictModeEnabled", false),
                    minutesUsedToday = obj.optInt("minutesUsedToday", 0),
                    lastResetDate = obj.optString("lastResetDate", ""),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    emergencyBypassUntilMs = obj.optLong("emergencyBypassUntilMs", 0L),
                    reminder80SentToday = obj.optBoolean("reminder80SentToday", false),
                    limit100SentToday = obj.optBoolean("limit100SentToday", false)
                )
                // Perform daily reset check on load
                list.add(item.checkAndResetDailyUsage())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading app daily limits from local JSON: ${e.message}")
        }
        return list
    }

    fun saveAppDailyLimitsToLocal(limits: List<AppDailyLimitEntity>) {
        try {
            val array = JSONArray()
            for (item in limits) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("userId", item.userId)
                    put("appPackage", item.appPackage)
                    put("appName", item.appName)
                    put("dailyLimitMinutes", item.dailyLimitMinutes)
                    put("emergencyUsesAllowed", item.emergencyUsesAllowed)
                    put("emergencyUsesRemainingToday", item.emergencyUsesRemainingToday)
                    put("showReminders", item.showReminders)
                    put("strictModeEnabled", item.strictModeEnabled)
                    put("minutesUsedToday", item.minutesUsedToday)
                    put("lastResetDate", item.lastResetDate)
                    put("isEnabled", item.isEnabled)
                    put("emergencyBypassUntilMs", item.emergencyBypassUntilMs)
                    put("reminder80SentToday", item.reminder80SentToday)
                    put("limit100SentToday", item.limit100SentToday)
                }
                array.put(obj)
            }
            localDataPrefs.edit().putString("app_daily_limits", array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving app daily limits to local JSON: ${e.message}")
        }
    }

    suspend fun saveAppDailyLimit(limit: AppDailyLimitEntity) {
        limitsMutex.withLock {
        val checked = limit.checkAndResetDailyUsage()
        val currentList = _appDailyLimits.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.appPackage == checked.appPackage || it.id == checked.id }
        if (existingIndex >= 0) {
            currentList[existingIndex] = checked
        } else {
            currentList.add(checked)
        }
        _appDailyLimits.value = currentList
        saveAppDailyLimitsToLocal(currentList)

        // Remote Supabase sync
        val userId = checked.userId.ifBlank { SupabaseService.getInstance().getCurrentUserId() ?: "" }
        if (userId.isNotBlank()) {
            val dto = com.example.data.SupabaseAppDailyLimitDto(
                id = checked.id,
                user_id = userId,
                app_package = checked.appPackage,
                app_name = checked.appName,
                daily_limit_minutes = checked.dailyLimitMinutes,
                emergency_uses_allowed = checked.emergencyUsesAllowed,
                emergency_uses_remaining_today = checked.emergencyUsesRemainingToday,
                show_reminders = checked.showReminders,
                strict_mode_enabled = checked.strictModeEnabled,
                minutes_used_today = checked.minutesUsedToday,
                last_reset_date = checked.lastResetDate.ifBlank { checked.getTodayDateString() },
                is_enabled = checked.isEnabled
            )
            logSupabaseResult(SupabaseService.getInstance().upsertAppDailyLimit(dto), "upsertAppDailyLimit", dto.id)
        }
    
    }}

    suspend fun deleteAppDailyLimit(id: String) {
        val currentList = _appDailyLimits.value.filter { it.id != id }
        _appDailyLimits.value = currentList
        saveAppDailyLimitsToLocal(currentList)
        SupabaseService.getInstance().deleteAppDailyLimit(id)
    }

    suspend fun useEmergencyAccess(appPackage: String, durationMinutes: Int = 15) {
        limitsMutex.withLock {
        val currentList = _appDailyLimits.value.toMutableList()
        val index = currentList.indexOfFirst { it.appPackage == appPackage }
        if (index >= 0) {
            val limit = currentList[index].checkAndResetDailyUsage()
            if (limit.emergencyUsesRemainingToday > 0) {
                val updated = limit.copy(
                    emergencyUsesRemainingToday = (limit.emergencyUsesRemainingToday - 1).coerceAtLeast(0),
                    emergencyBypassUntilMs = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
                )
                currentList[index] = updated
                _appDailyLimits.value = currentList
                saveAppDailyLimitsToLocal(currentList)

                val userId = updated.userId.ifBlank { SupabaseService.getInstance().getCurrentUserId() ?: "" }
                if (userId.isNotBlank()) {
                    val dto = com.example.data.SupabaseAppDailyLimitDto(
                        id = updated.id,
                        user_id = userId,
                        app_package = updated.appPackage,
                        app_name = updated.appName,
                        daily_limit_minutes = updated.dailyLimitMinutes,
                        emergency_uses_allowed = updated.emergencyUsesAllowed,
                        emergency_uses_remaining_today = updated.emergencyUsesRemainingToday,
                        show_reminders = updated.showReminders,
                        strict_mode_enabled = updated.strictModeEnabled,
                        minutes_used_today = updated.minutesUsedToday,
                        last_reset_date = updated.lastResetDate,
                        is_enabled = updated.isEnabled
                    )
                    logSupabaseResult(SupabaseService.getInstance().upsertAppDailyLimit(dto), "upsertAppDailyLimit", dto.id)
                }
            }
        }
    
    }}

    suspend fun updateAppUsageMinutes(appPackage: String, additionalMinutes: Int = 1): AppDailyLimitEntity? {limitsMutex.withLock {
        val currentList = _appDailyLimits.value.toMutableList()
        val index = currentList.indexOfFirst { it.appPackage == appPackage }
        if (index >= 0) {
            val limit = currentList[index].checkAndResetDailyUsage()
            val updated = limit.copy(
                minutesUsedToday = limit.minutesUsedToday + additionalMinutes
            )
            currentList[index] = updated
            _appDailyLimits.value = currentList
            saveAppDailyLimitsToLocal(currentList)

            val userId = updated.userId.ifBlank { SupabaseService.getInstance().getCurrentUserId() ?: "" }
            if (userId.isNotBlank()) {
                val dto = com.example.data.SupabaseAppDailyLimitDto(
                    id = updated.id,
                    user_id = userId,
                    app_package = updated.appPackage,
                    app_name = updated.appName,
                    daily_limit_minutes = updated.dailyLimitMinutes,
                    emergency_uses_allowed = updated.emergencyUsesAllowed,
                    emergency_uses_remaining_today = updated.emergencyUsesRemainingToday,
                    show_reminders = updated.showReminders,
                    strict_mode_enabled = updated.strictModeEnabled,
                    minutes_used_today = updated.minutesUsedToday,
                    last_reset_date = updated.lastResetDate,
                    is_enabled = updated.isEnabled
                )
                logSupabaseResult(SupabaseService.getInstance().upsertAppDailyLimit(dto), "upsertAppDailyLimit", dto.id)
            }
            return updated
        }
        return null
    
    }}

    suspend fun syncAppDailyLimitsFromSupabase(userId: String) {
        if (userId.isBlank()) return
        val dtos = SupabaseService.getInstance().fetchAppDailyLimits(userId)
        if (dtos.isNotEmpty()) {
            val remoteLimits = dtos.map { dto ->
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                AppDailyLimitEntity(
                    id = dto.id,
                    userId = dto.user_id ?: userId,
                    appPackage = dto.app_package,
                    appName = dto.app_name,
                    dailyLimitMinutes = dto.daily_limit_minutes,
                    emergencyUsesAllowed = dto.emergency_uses_allowed,
                    emergencyUsesRemainingToday = dto.emergency_uses_remaining_today,
                    showReminders = dto.show_reminders,
                    strictModeEnabled = dto.strict_mode_enabled,
                    minutesUsedToday = dto.minutes_used_today,
                    lastResetDate = dto.last_reset_date ?: todayStr,
                    isEnabled = dto.is_enabled
                ).checkAndResetDailyUsage()
            }
            _appDailyLimits.value = remoteLimits
            saveAppDailyLimitsToLocal(remoteLimits)
        }
    }

    /**
     * Performs a comprehensive, blocking sync of all local progress data to Supabase
     * before sign-out. This guarantees that all unsynced local progress, user preferences,
     * study sessions, tasks, reflections, app limits, and leaderboard entries are given a final
     * chance to be written to Supabase, preventing accidental data loss on logout.
     */
    suspend fun syncAllLocalDataToSupabase(): Boolean = withContext(Dispatchers.IO) {
        val resolvedUid = SupabaseService.getInstance().getCurrentUserId()
            ?: AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.getStoredUserId(it) }
            ?: (_userPreferences.value?.currentUserEmail?.let { getUserFromLocalCache(it)?.firebaseUid })?.takeIf { it.isNotBlank() }
            ?: (_userPreferences.value?.currentUserId?.let { userPrefs.getString("usr_id_${it}_email", null) }?.let { getUserFromLocalCache(it)?.firebaseUid })?.takeIf { it.isNotBlank() }
            ?: _userPreferences.value?.currentUserEmail?.let { java.util.UUID.nameUUIDFromBytes("google_$it".toByteArray()).toString() }
            ?: _userPreferences.value?.currentUserId?.toString()

        if (resolvedUid.isNullOrBlank()) {
            Log.w(TAG, "[PreLogoutSync] Cannot perform pre-logout Supabase write: user_id is blank/unresolved.")
            return@withContext false
        }

        Log.i(TAG, "[PreLogoutSync] Starting final pre-logout Supabase synchronization for user_id=$resolvedUid")

        var allSucceeded = true

        // 1. Sync User Preferences & Leaderboard
        try {
            val prefs = _userPreferences.value ?: UserPreferencesEntity()
            val prefsDto = SupabaseUserPreferencesDto(
                user_id = resolvedUid,
                theme_mode = prefs.themeMode,
                daily_goal_minutes = prefs.dailyGoalMinutes,
                preferred_focus_mode = prefs.preferredFocusMode,
                deep_work_duration = prefs.deepWorkDuration,
                deep_work_break = prefs.deepWorkBreak,
                classic_duration = prefs.classicDuration,
                classic_break = prefs.classicBreak,
                short_sprint_duration = prefs.shortSprintDuration,
                sound_enabled = prefs.soundEnabled,
                ambient_sound = prefs.ambientSound,
                haptics_enabled = prefs.hapticsEnabled,
                notifications_enabled = prefs.notificationsEnabled,
                auto_start_break = prefs.autoStartBreak,
                auto_start_focus = prefs.autoStartFocus,
                week_starts_on = prefs.weekStartsOn,
                has_completed_onboarding = prefs.hasCompletedOnboarding || prefs.hasCompletedIntakeSurvey,
                user_level = prefs.userLevel,
                focus_points = prefs.focusPoints,
                focus_identity = prefs.focusIdentity,
                current_streak = prefs.currentStreak,
                best_streak = prefs.bestStreak,
                alarm_ringtone = prefs.alarmRingtone,
                is_app_blocker_enabled = prefs.isAppBlockerEnabled,
                app_blocker_duration_minutes = prefs.appBlockerDurationMinutes,
                app_blocker_end_time = prefs.appBlockerEndTime,
                blocked_apps_list = prefs.blockedAppsList,
                shield_blocked_attempts = prefs.shieldBlockedAttempts,
                allowed_education_apps = prefs.allowedEducationApps,
                is_auto_study_blocker_enabled = prefs.isAutoStudyBlockerEnabled,
                penalty_block_end_time = prefs.penaltyBlockEndTime,
                buddy_growth_stage = prefs.buddyGrowthStage,
                buddy_total_focus_minutes = prefs.buddyTotalFocusMinutes,
                last_donation_prompt_shown_at = formatMillisToIso(prefs.lastDonationPromptShownAt),
                donation_prompt_dismissed_count = prefs.donationPromptDismissedCount,
                never_show_donation_prompt = prefs.neverShowDonationPrompt
            )
            val prefRes = SupabaseService.getInstance().upsertUserPreferences(prefsDto)
            if (prefRes.isFailure) allSucceeded = false

            val emailPart = prefs.currentUserEmail?.substringBefore("@")
            val displayName = prefs.currentUserName?.ifBlank { emailPart } ?: emailPart ?: "Student"
            val avatar = prefs.currentUserPhotoUrl
                ?: AndroidPreferenceSessionManager.appContext?.let { AndroidPreferenceSessionManager.getStoredAvatarUrl(it) }

            if (!avatar.isNullOrBlank()) {
                AndroidPreferenceSessionManager.appContext?.let {
                    AndroidPreferenceSessionManager.setStoredAvatarUrl(it, avatar)
                }
                SupabaseService.getInstance().updateUserAvatarUrl(resolvedUid, avatar)
            }

            // Sync user profile table
            val userDto = SupabaseUserDto(
                id = resolvedUid,
                email = prefs.currentUserEmail,
                full_name = displayName,
                avatar_url = avatar,
                primary_goal = prefs.primaryStudyGoal,
                focus_style = prefs.preferredFocusMode,
                daily_target_hours = (prefs.dailyGoalMinutes / 60).coerceAtLeast(1),
                primary_distraction = prefs.biggestDistractionApp,
                student_age = prefs.studentAge,
                student_class = prefs.studentClass,
                student_stream = prefs.studentStream,
                study_schedule = prefs.studySchedule,
                mobile_break_time = prefs.mobileBreakTime,
                student_class_level = prefs.studentClassLevel,
                is_board_exam_year = prefs.isBoardExamYear,
                daily_screen_time_goal_minutes = prefs.dailyScreenTimeGoalMinutes,
                primary_study_goal = prefs.primaryStudyGoal,
                biggest_distraction_app = prefs.biggestDistractionApp,
                preferred_study_time_window = prefs.preferredStudyTimeWindow,
                motivation_style = prefs.motivationStyle,
                has_completed_intake_survey = prefs.hasCompletedIntakeSurvey || prefs.hasCompletedOnboarding
            )
            logSupabaseResult(SupabaseService.getInstance().upsertUserProfile(userDto), "upsertUserProfile", userDto.id)

            val weekStartMsSync = run {
                val cal = java.util.Calendar.getInstance()
                val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                val daysToSubtract = (dayOfWeek - java.util.Calendar.MONDAY + 7) % 7
                cal.add(java.util.Calendar.DAY_OF_MONTH, -daysToSubtract)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            val weeklyStudySecondsSync = _allSessions.value
                .filter { it.completedAt >= weekStartMsSync }
                .sumOf { it.durationSeconds }.toLong()
            val lbRes = SupabaseService.getInstance().upsertLeaderboard(
                SupabaseStudyLeaderboardDto(
                    user_id = resolvedUid,
                    display_name = displayName,
                    study_seconds = ((_allSessions.value.sumOf { it.durationSeconds })).toLong().coerceAtLeast(1L),
                    weekly_study_seconds = weeklyStudySecondsSync,
                    current_week_start = LeaderboardDateUtils.getCurrentWeekMonday(),
                    streak = prefs.currentStreak.coerceAtLeast(1),
                    subject_tag = prefs.primaryStudyGoal.ifBlank { "Study" },
                    avatar_url = avatar
                )
            )
            if (lbRes.isFailure) allSucceeded = false
        } catch (e: Exception) {
            Log.w(TAG, "[PreLogoutSync] Error syncing preferences/leaderboard: ${e.message}")
            allSucceeded = false
        }

        // 2. Sync all Scheduled Blocks
        try {
            val localSchedules = com.example.service.ScheduledBlockScheduler.getLocalSchedules(context)
            for (sched in localSchedules) {
                val toPush = if (sched.user_id.isBlank() || sched.user_id == "anonymous" || sched.user_id == "local_user") {
                    sched.copy(user_id = resolvedUid)
                } else {
                    sched
                }
                logSupabaseResult(SupabaseService.getInstance().upsertScheduledBlock(toPush), "upsertScheduledBlock", toPush.id)
            }
            Log.i(TAG, "[PreLogoutSync] Synced ${localSchedules.size} scheduled blocks to Supabase.")
        } catch (e: Exception) {
            Log.w(TAG, "[PreLogoutSync] Error syncing scheduled blocks: ${e.message}")
        }

        // 3. Sync all Alarms
        try {
            val dummyLabels = setOf("Study Session Alert", "Deep Work Reminder", "Break Over - Time to Study")
            val localAlarms = com.example.service.AlarmScheduler.getLocalAlarms(context).filterNot { it.label in dummyLabels }
            for (alarm in localAlarms) {
                val dto = SupabaseAlarmDto(
                    id = alarm.id,
                    user_id = resolvedUid,
                    label = alarm.label,
                    hour = alarm.hour,
                    minute = alarm.minute,
                    days_active = alarm.daysActive,
                    ringtone = alarm.ringtone,
                    is_enabled = alarm.isEnabled,
                    vibrate = alarm.vibrate
                )
                logSupabaseResult(SupabaseService.getInstance().upsertAlarm(dto), "upsertAlarm", dto.id)
            }
            Log.i(TAG, "[PreLogoutSync] Synced ${localAlarms.size} alarms to Supabase.")
        } catch (e: Exception) {
            Log.w(TAG, "[PreLogoutSync] Error syncing alarms: ${e.message}")
        }

        // 4. Sync all Focus Sessions
        try {
            val sessions = _allSessions.value
            for (sess in sessions) {
                val dto = SupabaseFocusSessionDto(
                    id = java.util.UUID.nameUUIDFromBytes("session_${sess.id}".toByteArray()).toString(),
                    user_id = resolvedUid,
                    task_title = sess.taskTitle,
                    duration_seconds = sess.durationSeconds,
                    target_duration_seconds = sess.targetDurationSeconds,
                    mode = sess.mode,
                    distractions_count = sess.distractionsCount,
                    distraction_types = sess.distractionTypes,
                    focus_points_earned = sess.focusPointsEarned,
                    completed_at = sess.completedAt,
                    day_of_week = sess.dayOfWeek,
                    hour_of_day = sess.hourOfDay,
                    notes = sess.notes
                )
                logSupabaseResult(SupabaseService.getInstance().upsertFocusSession(dto), "upsertFocusSession", dto.id)
            }
            Log.i(TAG, "[PreLogoutSync] Synced ${sessions.size} sessions to Supabase.")
        } catch (e: Exception) {
            Log.w(TAG, "[PreLogoutSync] Error syncing sessions: ${e.message}")
            allSucceeded = false
        }

        // 4. Sync all Tasks
        try {
            val tasks = _allTasks.value
            for (task in tasks) {
                val dto = SupabaseTaskDto(
                    id = java.util.UUID.nameUUIDFromBytes("task_${task.id}".toByteArray()).toString(),
                    user_id = resolvedUid,
                    title = task.title,
                    category = task.category,
                    priority = task.priority,
                    duration_minutes = task.durationMinutes,
                    scheduled_time = task.scheduledTime,
                    is_completed = task.isCompleted,
                    is_top_priority = task.isTopPriority,
                    notes = task.notes,
                    date = task.date
                )
                logSupabaseResult(SupabaseService.getInstance().upsertTask(dto), "upsertTask", dto.id)
            }
            Log.i(TAG, "[PreLogoutSync] Synced ${tasks.size} tasks to Supabase.")
        } catch (e: Exception) {
            Log.w(TAG, "[PreLogoutSync] Error syncing tasks: ${e.message}")
            allSucceeded = false
        }

        // 5. Sync all Reflections
        try {
            val reflections = _allReflections.value
            for (ref in reflections) {
                val dto = SupabaseReflectionDto(
                    id = java.util.UUID.nameUUIDFromBytes("reflection_${ref.id}".toByteArray()).toString(),
                    user_id = resolvedUid,
                    week_label = ref.weekLabel,
                    total_minutes_focused = ref.totalMinutesFocused,
                    sessions_completed = ref.sessionsCompleted,
                    best_day = ref.bestDay,
                    completion_rate = ref.completionRate,
                    reflection_text = ref.reflectionText
                )
                logSupabaseResult(SupabaseService.getInstance().upsertReflection(dto), "upsertReflection", dto.id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "[PreLogoutSync] Error syncing reflections: ${e.message}")
        }

        // 6. Sync App Daily Limits
        try {
            val limits = _appDailyLimits.value
            for (limit in limits) {
                val dto = SupabaseAppDailyLimitDto(
                    id = limit.id,
                    user_id = resolvedUid,
                    app_package = limit.appPackage,
                    app_name = limit.appName,
                    daily_limit_minutes = limit.dailyLimitMinutes,
                    emergency_uses_allowed = limit.emergencyUsesAllowed,
                    emergency_uses_remaining_today = limit.emergencyUsesRemainingToday,
                    show_reminders = limit.showReminders,
                    strict_mode_enabled = limit.strictModeEnabled,
                    minutes_used_today = limit.minutesUsedToday,
                    last_reset_date = limit.lastResetDate,
                    is_enabled = limit.isEnabled
                )
                logSupabaseResult(SupabaseService.getInstance().upsertAppDailyLimit(dto), "upsertAppDailyLimit", dto.id)
            }
        } catch (e: Exception) {
            Log.w(TAG, "[PreLogoutSync] Error syncing app daily limits: ${e.message}")
        }

        Log.i(TAG, "[PreLogoutSync] Final Supabase sync completed (success=$allSucceeded)")
        allSucceeded
    }
}
