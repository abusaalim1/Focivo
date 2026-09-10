package com.example.data

import android.content.Context
import android.util.Log
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
import org.json.JSONArray
import org.json.JSONObject

class FocuslyRepository(private val context: Context) {

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

    private val localDataPrefs by lazy {
        context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
    }

    private val userPrefs by lazy {
        context.getSharedPreferences("focusly_users_cache", Context.MODE_PRIVATE)
    }

    init {
        loadAllLocalData()
    }

    private fun loadAllLocalData() {
        _allTasks.value = loadTasksFromLocal()
        _allSessions.value = loadSessionsFromLocal()
        _allReflections.value = loadReflectionsFromLocal()
        _userPreferences.value = loadPreferencesFromLocal()

        // Real-time real user data only - do not populate dummy placeholder tasks
    }

    private fun loadTasksFromLocal(): List<TaskEntity> {
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
        return list
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
        } catch (e: Exception) {
            Log.e(TAG, "Error saving tasks to local JSON: ${e.message}")
        }
    }

    private fun loadSessionsFromLocal(): List<FocusSessionEntity> {
        val jsonStr = localDataPrefs.getString("focus_sessions", "[]") ?: "[]"
        val list = mutableListOf<FocusSessionEntity>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    FocusSessionEntity(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        taskTitle = obj.optString("taskTitle", ""),
                        durationSeconds = obj.optInt("durationSeconds", 0),
                        targetDurationSeconds = obj.optInt("targetDurationSeconds", 0),
                        mode = obj.optString("mode", "Deep Work"),
                        distractionsCount = obj.optInt("distractionsCount", 0),
                        distractionTypes = obj.optString("distractionTypes", ""),
                        focusPointsEarned = obj.optInt("focusPointsEarned", 12),
                        completedAt = obj.optLong("completedAt", System.currentTimeMillis()),
                        dayOfWeek = obj.optInt("dayOfWeek", 1),
                        hourOfDay = obj.optInt("hourOfDay", 10),
                        notes = obj.optString("notes", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sessions from local JSON: ${e.message}")
        }
        return list
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
        } catch (e: Exception) {
            Log.e(TAG, "Error saving sessions to local JSON: ${e.message}")
        }
    }

    private fun loadReflectionsFromLocal(): List<ReflectionEntity> {
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
        return list
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
        } catch (e: Exception) {
            Log.e(TAG, "Error saving reflections to local JSON: ${e.message}")
        }
    }

    private fun loadPreferencesFromLocal(): UserPreferencesEntity {
        val jsonStr = localDataPrefs.getString("user_preferences", null)
        if (jsonStr == null) {
            return UserPreferencesEntity()
        }
        try {
            val obj = JSONObject(jsonStr)
            return UserPreferencesEntity(
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
                studentStream = obj.optString("studentStream", "Science (PCM)"),
                studySchedule = obj.optString("studySchedule", "6:00 PM – 10:00 PM"),
                mobileBreakTime = obj.optString("mobileBreakTime", "8:00 PM – 8:30 PM"),
                allowedEducationApps = obj.optString("allowedEducationApps", "com.google.android.youtube,com.openai.chatgpt,com.anthropic.claude"),
                isAutoStudyBlockerEnabled = obj.optBoolean("isAutoStudyBlockerEnabled", true),
                penaltyBlockEndTime = obj.optLong("penaltyBlockEndTime", 0L)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading preferences from local JSON: ${e.message}")
            return UserPreferencesEntity()
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
                put("studentStream", prefs.studentStream)
                put("studySchedule", prefs.studySchedule)
                put("mobileBreakTime", prefs.mobileBreakTime)
                put("allowedEducationApps", prefs.allowedEducationApps)
                put("isAutoStudyBlockerEnabled", prefs.isAutoStudyBlockerEnabled)
                put("penaltyBlockEndTime", prefs.penaltyBlockEndTime)
            }
            localDataPrefs.edit().putString("user_preferences", obj.toString()).apply()
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
                    val dto = SupabaseUserDto(
                        id = uid,
                        email = user.email,
                        full_name = user.fullName,
                        primary_goal = user.primaryGoal,
                        focus_style = user.focusStyle,
                        daily_target_hours = user.dailyTargetHours,
                        peak_productivity_time = user.peakProductivityTime,
                        primary_distraction = user.primaryDistraction,
                        sound_preference = user.soundPreference,
                        student_age = user.studentAge,
                        student_class = user.studentClass,
                        student_stream = user.studentStream,
                        study_schedule = user.studySchedule,
                        mobile_break_time = user.mobileBreakTime
                    )
                    SupabaseService.getInstance().upsertUserProfile(dto)
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
                    SupabaseService.getInstance().upsertTask(dto)
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
                    SupabaseService.getInstance().upsertTask(dto)
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
                    SupabaseService.getInstance().upsertTask(dto)
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
                    SupabaseService.getInstance().upsertFocusSession(dto)
                } catch (e: Exception) {
                    Log.w(TAG, "Error recording session on Supabase: ${e.message}")
                }
            }
        }
        return id
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
                    SupabaseService.getInstance().upsertReflection(dto)
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

        val uid = SupabaseService.getInstance().getCurrentUserId()
        if (!uid.isNullOrBlank()) {
            scope.launch {
                try {
                    val dto = SupabaseUserPreferencesDto(
                        user_id = uid,
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
                        has_completed_onboarding = preferences.hasCompletedOnboarding,
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
                        penalty_block_end_time = preferences.penaltyBlockEndTime
                    )
                    SupabaseService.getInstance().upsertUserPreferences(dto)
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

    suspend fun syncWithSupabase(userUuid: String) {
        if (userUuid.isBlank()) return
        scope.launch {
            try {
                // 1. Tasks
                val remoteTasks = SupabaseService.getInstance().fetchUserTasks(userUuid)
                if (remoteTasks.isNotEmpty()) {
                    val mappedTasks = remoteTasks.map { dto ->
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
                    _allTasks.value = mappedTasks
                    saveTasksToLocal(mappedTasks)
                } else {
                    _allTasks.value.forEach { task ->
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
                        SupabaseService.getInstance().upsertTask(dto)
                    }
                }

                // 2. Focus Sessions
                val remoteSessions = SupabaseService.getInstance().fetchFocusSessions(userUuid)
                if (remoteSessions.isNotEmpty()) {
                    val mappedSessions = remoteSessions.map { dto ->
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
                    _allSessions.value = mappedSessions
                    saveSessionsToLocal(mappedSessions)
                } else {
                    _allSessions.value.forEach { session ->
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
                        SupabaseService.getInstance().upsertFocusSession(dto)
                    }
                }

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
                    _allReflections.value = mappedReflections
                    saveReflectionsToLocal(mappedReflections)
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
                        SupabaseService.getInstance().upsertReflection(dto)
                    }
                }

                // 4. Preferences
                val remotePrefs = SupabaseService.getInstance().fetchUserPreferences(userUuid)
                val remoteProfile = SupabaseService.getInstance().fetchUserProfile(userUuid)
                val profileName = remoteProfile?.full_name?.takeIf { it.isNotBlank() }
                val profileEmail = remoteProfile?.email?.takeIf { it.isNotBlank() }
                val profilePhoto = remoteProfile?.avatar_url?.takeIf { it.isNotBlank() }

                val (calcCurrentStreak, calcBestStreak) = calculateConsecutiveStreak(_allSessions.value)

                if (remotePrefs != null) {
                    val localPrefs = _userPreferences.value ?: UserPreferencesEntity()
                    val resolvedCurrentStreak = maxOf(calcCurrentStreak, remotePrefs.current_streak, localPrefs.currentStreak)
                    val resolvedBestStreak = maxOf(calcBestStreak, remotePrefs.best_streak, localPrefs.bestStreak, resolvedCurrentStreak)

                    val merged = localPrefs.copy(
                        themeMode = remotePrefs.theme_mode,
                        dailyGoalMinutes = remotePrefs.daily_goal_minutes,
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
                        hasCompletedOnboarding = remotePrefs.has_completed_onboarding,
                        userLevel = remotePrefs.user_level,
                        focusPoints = remotePrefs.focus_points,
                        focusIdentity = remotePrefs.focus_identity,
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
                        currentUserName = profileName ?: localPrefs.currentUserName,
                        currentUserEmail = profileEmail ?: localPrefs.currentUserEmail,
                        currentUserPhotoUrl = profilePhoto ?: localPrefs.currentUserPhotoUrl
                    )
                    _userPreferences.value = merged
                    savePreferencesToLocal(merged)

                    if (remotePrefs.current_streak == 0 && resolvedCurrentStreak > 0) {
                        savePreferences(merged)
                    }
                } else if (profileName != null || profileEmail != null || profilePhoto != null) {
                    val localPrefs = _userPreferences.value ?: UserPreferencesEntity()
                    val merged = localPrefs.copy(
                        currentUserName = profileName ?: localPrefs.currentUserName,
                        currentUserEmail = profileEmail ?: localPrefs.currentUserEmail,
                        currentUserPhotoUrl = profilePhoto ?: localPrefs.currentUserPhotoUrl,
                        currentStreak = maxOf(calcCurrentStreak, localPrefs.currentStreak),
                        bestStreak = maxOf(calcBestStreak, localPrefs.bestStreak)
                    )
                    _userPreferences.value = merged
                    savePreferencesToLocal(merged)
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
                            penalty_block_end_time = prefs.penaltyBlockEndTime
                        )
                        SupabaseService.getInstance().upsertUserPreferences(dto)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error during syncWithSupabase: ${e.message}")
            }
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

        savePreferences(
            UserPreferencesEntity(
                id = 1,
                hasCompletedOnboarding = false,
                focusPoints = 0,
                currentStreak = 0,
                bestStreak = 0,
                userLevel = 1,
                focusIdentity = "Novice Deep Worker"
            )
        )
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
}
