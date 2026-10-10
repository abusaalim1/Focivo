package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.model.FocusSessionEntity
import com.example.data.model.ReflectionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Data model representing the persistent guest user profile and overall stats.
 */
data class GuestProfileData(
    val guestId: String = "guest_scholar",
    val guestName: String = "Guest Scholar",
    val guestEmail: String = "guest@focivo.local",
    val userStreak: Int = 1,
    val totalFocusMinutes: Int = 0,
    val totalSessionsCompleted: Int = 0,
    val lastSessionTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Local JSON-based storage engine using FileOutputStream to save guest session data
 * in a dedicated 'guest_data' directory.
 *
 * Guarantees that guest study records, sessions, tasks, and streaks reliably persist
 * across app restarts, cold boots, and offline states even if Supabase authentication is not active.
 */
class GuestDataStorageManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GuestDataStorageManager"
        private const val GUEST_DIR_NAME = "guest_data"
        private const val SESSIONS_FILE = "guest_sessions.json"
        private const val TASKS_FILE = "guest_tasks.json"
        private const val PREFERENCES_FILE = "guest_preferences.json"
        private const val REFLECTIONS_FILE = "guest_reflections.json"
        private const val PROFILE_FILE = "guest_profile.json"

        @Volatile
        private var instance: GuestDataStorageManager? = null

        fun getInstance(context: Context): GuestDataStorageManager {
            return instance ?: synchronized(this) {
                instance ?: GuestDataStorageManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Serializes all file access: concurrent writers previously shared one
     * "$fileName.tmp" path and interleaved load-modify-save sequences,
     * corrupting guest data. (synchronized is reentrant, so nested
     * load/save calls from appendGuestSession are safe.)
     */
    private val fileLock = Any()

    private val guestDir: File by lazy {
        val dir = File(context.filesDir, GUEST_DIR_NAME)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            Log.d(TAG, "Initialized dedicated guest_data directory at: ${dir.absolutePath} (created=$created)")
        }
        dir
    }

    /**
     * Atomically writes JSON string to disk using FileOutputStream with file descriptor synchronization.
     */
    private fun writeJsonStringToFile(fileName: String, jsonString: String): Boolean {
        synchronized(fileLock) {
            return try {
            if (!guestDir.exists()) {
                guestDir.mkdirs()
            }
            val targetFile = File(guestDir, fileName)
            val tempFile = File(guestDir, "$fileName.tmp")

            FileOutputStream(tempFile).use { fos ->
                val bytes = jsonString.toByteArray(Charsets.UTF_8)
                fos.write(bytes)
                fos.flush()
                try {
                    fos.fd.sync()
                } catch (_: Exception) {}
            }

            if (tempFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    // Fallback copy if rename fails
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                Log.d(TAG, "Successfully persisted $fileName (${jsonString.length} chars) using FileOutputStream in guest_data/")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $fileName to guest_data directory using FileOutputStream", e)
            false
        }
        }
    }

    /**
     * Reads raw JSON string from disk using FileInputStream.
     */
    private fun readJsonStringFromFile(fileName: String): String? {
        synchronized(fileLock) {
            return try {
            val file = File(guestDir, fileName)
            if (!file.exists() || !file.canRead()) {
                return null
            }
            FileInputStream(file).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $fileName from guest_data directory using FileInputStream", e)
            null
        }
        }
    }

    // =========================================================================
    // FOCUS SESSIONS
    // =========================================================================

    /**
     * Saves the entire list of guest Focus Sessions to guest_data/guest_sessions.json using FileOutputStream.
     */
    fun saveGuestSessions(sessions: List<FocusSessionEntity>): Boolean {
        try {
            val jsonArray = JSONArray()
            for (session in sessions) {
                val obj = JSONObject().apply {
                    put("id", session.id)
                    put("taskTitle", session.taskTitle)
                    put("durationSeconds", session.durationSeconds)
                    put("targetDurationSeconds", session.targetDurationSeconds)
                    put("mode", session.mode)
                    put("distractionsCount", session.distractionsCount)
                    put("distractionTypes", session.distractionTypes)
                    put("focusPointsEarned", session.focusPointsEarned)
                    put("completedAt", session.completedAt)
                    put("dayOfWeek", session.dayOfWeek)
                    put("hourOfDay", session.hourOfDay)
                    put("notes", session.notes)
                }
                jsonArray.put(obj)
            }
            return writeJsonStringToFile(SESSIONS_FILE, jsonArray.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing guest sessions to JSON", e)
            return false
        }
    }

    /**
     * Reads all guest Focus Sessions from guest_data/guest_sessions.json.
     */
    fun loadGuestSessions(): List<FocusSessionEntity> {
        val jsonStr = readJsonStringFromFile(SESSIONS_FILE) ?: return emptyList()
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
            Log.e(TAG, "Error deserializing guest sessions from JSON in guest_data/", e)
        }
        return list.sortedByDescending { it.completedAt }
    }

    /**
     * Appends a new completed focus session directly to guest_data storage.
     */
    fun appendGuestSession(session: FocusSessionEntity): Boolean {
        // Atomic load-modify-save: prevents lost updates when two threads append at once.
        synchronized(fileLock) {
            val existing = loadGuestSessions().toMutableList()
            // Deduplicate by ID or completedAt
            existing.removeAll { it.id == session.id || (it.completedAt > 0 && it.completedAt == session.completedAt) }
            existing.add(0, session)
            return saveGuestSessions(existing)
        }
    }

    // =========================================================================
    // TASKS
    // =========================================================================

    /**
     * Saves all guest tasks to guest_data/guest_tasks.json using FileOutputStream.
     */
    fun saveGuestTasks(tasks: List<TaskEntity>): Boolean {
        try {
            val jsonArray = JSONArray()
            for (task in tasks) {
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
                jsonArray.put(obj)
            }
            return writeJsonStringToFile(TASKS_FILE, jsonArray.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing guest tasks", e)
            return false
        }
    }

    /**
     * Loads all guest tasks from guest_data/guest_tasks.json.
     */
    fun loadGuestTasks(): List<TaskEntity> {
        val jsonStr = readJsonStringFromFile(TASKS_FILE) ?: return emptyList()
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
            Log.e(TAG, "Error deserializing guest tasks from JSON in guest_data/", e)
        }
        return list
    }

    // =========================================================================
    // USER PREFERENCES & STREAKS
    // =========================================================================

    /**
     * Saves user preferences to guest_data/guest_preferences.json using FileOutputStream.
     */
    fun saveGuestPreferences(prefs: UserPreferencesEntity): Boolean {
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
            return writeJsonStringToFile(PREFERENCES_FILE, obj.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing guest preferences", e)
            return false
        }
    }

    /**
     * Loads guest preferences from guest_data/guest_preferences.json.
     */
    fun loadGuestPreferences(): UserPreferencesEntity? {
        val jsonStr = readJsonStringFromFile(PREFERENCES_FILE) ?: return null
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
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing guest preferences from guest_data/", e)
            return null
        }
    }

    // =========================================================================
    // REFLECTIONS
    // =========================================================================

    /**
     * Saves reflections to guest_data/guest_reflections.json using FileOutputStream.
     */
    fun saveGuestReflections(reflections: List<ReflectionEntity>): Boolean {
        try {
            val array = JSONArray()
            for (ref in reflections) {
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
            return writeJsonStringToFile(REFLECTIONS_FILE, array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing guest reflections", e)
            return false
        }
    }

    /**
     * Loads reflections from guest_data/guest_reflections.json.
     */
    fun loadGuestReflections(): List<ReflectionEntity> {
        val jsonStr = readJsonStringFromFile(REFLECTIONS_FILE) ?: return emptyList()
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
            Log.e(TAG, "Error deserializing guest reflections from guest_data/", e)
        }
        return list
    }

    // =========================================================================
    // GUEST PROFILE & STATS
    // =========================================================================

    /**
     * Saves guest profile metadata to guest_data/guest_profile.json using FileOutputStream.
     */
    fun saveGuestProfile(profile: GuestProfileData): Boolean {
        try {
            val obj = JSONObject().apply {
                put("guestId", profile.guestId)
                put("guestName", profile.guestName)
                put("guestEmail", profile.guestEmail)
                put("userStreak", profile.userStreak)
                put("totalFocusMinutes", profile.totalFocusMinutes)
                put("totalSessionsCompleted", profile.totalSessionsCompleted)
                put("lastSessionTimestamp", profile.lastSessionTimestamp)
                put("createdAt", profile.createdAt)
            }
            return writeJsonStringToFile(PROFILE_FILE, obj.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing guest profile", e)
            return false
        }
    }

    /**
     * Loads guest profile metadata from guest_data/guest_profile.json.
     */
    fun loadGuestProfile(): GuestProfileData? {
        val jsonStr = readJsonStringFromFile(PROFILE_FILE) ?: return null
        return try {
            val obj = JSONObject(jsonStr)
            GuestProfileData(
                guestId = obj.optString("guestId", "guest_scholar"),
                guestName = obj.optString("guestName", "Guest Scholar"),
                guestEmail = obj.optString("guestEmail", "guest@focivo.local"),
                userStreak = obj.optInt("userStreak", 1),
                totalFocusMinutes = obj.optInt("totalFocusMinutes", 0),
                totalSessionsCompleted = obj.optInt("totalSessionsCompleted", 0),
                lastSessionTimestamp = obj.optLong("lastSessionTimestamp", 0L),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing guest profile from guest_data/", e)
            null
        }
    }

    /**
     * Checks if any guest data files exist in the guest_data directory.
     */
    fun hasGuestData(): Boolean {
        if (!guestDir.exists()) return false
        val files = guestDir.listFiles() ?: return false
        return files.any { it.isFile && it.length() > 0 }
    }

    /**
     * Returns the absolute path of the dedicated guest_data directory.
     */
    fun getGuestDataDirectoryPath(): String {
        return guestDir.absolutePath
    }

    /**
     * Clears all guest data in the guest_data directory.
     */
    fun clearGuestData(): Boolean {
        return try {
            if (guestDir.exists()) {
                guestDir.listFiles()?.forEach { it.delete() }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing guest data in guest_data/", e)
            false
        }
    }
}
