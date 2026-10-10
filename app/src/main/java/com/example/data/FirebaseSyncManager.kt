package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.model.FocusSessionEntity
import com.example.data.model.UserAccountEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class LeaderboardEntry(
    val userId: String = "",
    val userName: String = "",
    val focusHoursFormatted: String = "0h 0m",
    val totalSeconds: Long = 0L,
    val avatarColor: Long = 0xFF69F0AE,
    val rank: Int = 0,
    val isCurrentUser: Boolean = false
)

data class LeaderboardUser(
    val id: String = "",
    val displayName: String = "",
    val studySeconds: Long = 0L,           // All-time lifetime cumulative total
    val weeklyStudySeconds: Long = 0L,     // Current week's study seconds (resets to 0 if current_week_start != current week Monday)
    val todayStudySeconds: Long = 0L,      // Today's focus sessions total
    val currentWeekStart: String? = null,
    val streak: Int = 1,
    val subjectTag: String = "Study",
    val avatarUrl: String? = null,
    val isCurrentUser: Boolean = false
)

data class HallOfFameItem(
    val userId: String = "",
    val displayName: String = "",
    val weekStart: String = "",
    val weekEnd: String = "",
    val winningStudySeconds: Long = 0L
)

object LeaderboardDateUtils {
    // SimpleDateFormat is not thread-safe; create a fresh instance per call
    // (this object is used from concurrent Dispatchers.IO coroutines).
    private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Returns Monday of the current week in ISO-8601 format: YYYY-MM-DD
     */
    fun getCurrentWeekMonday(): String {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Convert DAY_OF_WEEK (Sunday=1, Monday=2, ..., Saturday=7) to days since Monday
        val daysToSubtract = (dayOfWeek - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysToSubtract)
        return isoFormat().format(cal.time)
    }

    /**
     * Returns Monday of the previous week in ISO-8601 format: YYYY-MM-DD
     */
    fun getPreviousWeekMonday(): String {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysToSubtract = (dayOfWeek - Calendar.MONDAY + 7) % 7
        cal.add(Calendar.DAY_OF_MONTH, -daysToSubtract - 7)
        return isoFormat().format(cal.time)
    }

    /**
     * Formats a week range into a user-friendly label, e.g. "Week of Sep 1–7"
     */
    fun formatWeekRange(startIso: String, endIso: String): String {
        return try {
            val startDate = isoFormat().parse(startIso) ?: return "Week of $startIso"
            val cal = Calendar.getInstance().apply { time = startDate }
            val monthStr = SimpleDateFormat("MMM", Locale.US).format(cal.time)
            val startDay = cal.get(Calendar.DAY_OF_MONTH)

            val endDay = if (endIso.isNotBlank()) {
                val endDate = isoFormat().parse(endIso)
                if (endDate != null) {
                    val calEnd = Calendar.getInstance().apply { time = endDate }
                    calEnd.get(Calendar.DAY_OF_MONTH)
                } else {
                    startDay + 6
                }
            } else {
                startDay + 6
            }
            "Week of $monthStr $startDay–$endDay"
        } catch (e: Exception) {
            if (startIso.isNotBlank()) "Week of $startIso" else "Past Week"
        }
    }
}

class FirebaseSyncManager(private val context: Context) {

    companion object {
        @Volatile
        private var instance: FirebaseSyncManager? = null

        fun getInstance(context: Context): FirebaseSyncManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val TAG = "FirebaseSyncManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _syncStatus = MutableStateFlow("High-Performance Local Engine")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _cloudLeaderboard = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val cloudLeaderboard: StateFlow<List<LeaderboardEntry>> = _cloudLeaderboard.asStateFlow()

    private val _leaderboardUsers = MutableStateFlow<List<LeaderboardUser>>(emptyList())
    val leaderboardUsers: StateFlow<List<LeaderboardUser>> = _leaderboardUsers.asStateFlow()

    private val _hallOfFame = MutableStateFlow<List<HallOfFameItem>>(emptyList())
    val hallOfFame: StateFlow<List<HallOfFameItem>> = _hallOfFame.asStateFlow()

    init {
        refreshLeaderboard()
    }

    fun refreshLeaderboard() {
        scope.launch {
            fetchSupabaseLeaderboardInternal()
            fetchHallOfFameInternal()
        }
    }

    private suspend fun fetchHallOfFameInternal() {
        try {
            val dtoList = SupabaseService.getInstance().fetchHallOfFame()
            val mapped = dtoList.map { dto ->
                HallOfFameItem(
                    userId = dto.user_id,
                    displayName = dto.display_name.ifBlank { "Top Student" },
                    weekStart = dto.week_start,
                    weekEnd = dto.week_end,
                    winningStudySeconds = dto.winning_study_seconds
                )
            }
            _hallOfFame.value = mapped
            Log.d(TAG, "Loaded ${mapped.size} Hall of Fame winners")
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Hall of Fame: ${e.message}")
        }
    }

    private suspend fun fetchSupabaseLeaderboardInternal() {
        try {
            val remoteList = SupabaseService.getInstance().fetchLeaderboard()
            val currentMonday = LeaderboardDateUtils.getCurrentWeekMonday()

            // Check if a new week has started and archive previous week's winner
            checkAndArchiveWeeklyWinner(remoteList, currentMonday)

            if (remoteList.isNotEmpty()) {
                val currentUserId = SupabaseService.getInstance().getCurrentUserId()
                val mappedList = remoteList.map { dto ->
                    // "This Week" genuinely resets each week:
                    // If current_week_start != currentMonday, user has not studied in the current week yet,
                    // so their weekly study seconds is strictly 0.
                    val isCurrentWeek = dto.current_week_start == currentMonday
                    val effectiveWeekly = if (isCurrentWeek) dto.weekly_study_seconds else 0L

                    LeaderboardUser(
                        id = dto.user_id,
                        displayName = dto.display_name,
                        studySeconds = dto.study_seconds,
                        weeklyStudySeconds = effectiveWeekly,
                        todayStudySeconds = 0L,
                        currentWeekStart = dto.current_week_start,
                        streak = dto.streak,
                        subjectTag = dto.subject_tag,
                        avatarUrl = dto.avatar_url,
                        isCurrentUser = dto.user_id == currentUserId
                    )
                }.sortedByDescending { it.weeklyStudySeconds } // default sorting for This Week
                _leaderboardUsers.value = mappedList
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Supabase leaderboard: ${e.message}")
        }
    }

    /**
     * Weekly Reset Trigger:
     * Checks if any entry has a current_week_start older than the current week's Monday.
     * If so, a new week has started since that entry was recorded.
     * Before weekly counters get updated by new sessions, we trigger archive_weekly_winner()
     * with the previous week's start date so the past week's #1 is preserved permanently.
     */
    private suspend fun checkAndArchiveWeeklyWinner(
        remoteList: List<SupabaseStudyLeaderboardDto>,
        currentMonday: String
    ) {
        val prefs = context.getSharedPreferences("leaderboard_reset_prefs", Context.MODE_PRIVATE)
        val lastArchivedWeek = prefs.getString("last_archived_week_start", "") ?: ""

        val olderEntries = remoteList.filter { dto ->
            !dto.current_week_start.isNullOrBlank() && dto.current_week_start < currentMonday
        }

        if (olderEntries.isNotEmpty()) {
            val weekToArchive = olderEntries
                .mapNotNull { it.current_week_start }
                .maxOrNull() ?: LeaderboardDateUtils.getPreviousWeekMonday()

            if (weekToArchive != lastArchivedWeek) {
                Log.i(TAG, "New week detected! Older week_start=$weekToArchive found (currentMonday=$currentMonday). Calling archive_weekly_winner($weekToArchive)")
                val success = SupabaseService.getInstance().archiveWeeklyWinner(weekToArchive)
                if (success) {
                    prefs.edit().putString("last_archived_week_start", weekToArchive).apply()
                    Log.i(TAG, "Successfully archived week $weekToArchive to leaderboard_hall_of_fame")
                    fetchHallOfFameInternal()
                }
            }
        }
    }

    fun recordUserStudyProgress(
        userId: String,
        displayName: String,
        studySeconds: Long,
        weeklyStudySeconds: Long = 0L,
        streak: Int = 1,
        subjectTag: String = "Study",
        avatarUrl: String? = null
    ) {
        scope.launch {
            val currentList = _leaderboardUsers.value.filterNot { it.id == userId || it.isCurrentUser }
            val currentMonday = LeaderboardDateUtils.getCurrentWeekMonday()
            val me = LeaderboardUser(
                id = userId,
                displayName = displayName,
                studySeconds = studySeconds,
                weeklyStudySeconds = weeklyStudySeconds,
                currentWeekStart = currentMonday,
                streak = streak,
                subjectTag = subjectTag,
                avatarUrl = avatarUrl,
                isCurrentUser = true
            )
            _leaderboardUsers.value = (listOf(me) + currentList).sortedByDescending { it.weeklyStudySeconds }

            // Sync to Supabase public.study_leaderboard
            if (userId.isNotBlank()) {
                try {
                    val dto = SupabaseStudyLeaderboardDto(
                        user_id = userId,
                        display_name = displayName,
                        study_seconds = studySeconds,
                        weekly_study_seconds = weeklyStudySeconds,
                        current_week_start = currentMonday,
                        streak = streak,
                        subject_tag = subjectTag,
                        avatar_url = avatarUrl
                    )
                    SupabaseService.getInstance().upsertLeaderboard(dto)
                } catch (e: Exception) {
                    Log.w(TAG, "Error upserting Supabase leaderboard: ${e.message}")
                }
            }
        }
    }

    fun syncBlockerState(isArmed: Boolean, endTime: Long, blockedCount: Int) {
        _syncStatus.value = "Local Blocker Armed"
    }

    fun syncSessionToCloud(session: FocusSessionEntity, user: UserAccountEntity?) {
        // Local-only tracking
    }

    fun syncUserProfile(userId: String, displayName: String, studySeconds: Int, streak: Int) {
        // NOTE: intentionally does NOT touch the leaderboard here. Previously this
        // hardcoded weeklyStudySeconds = 0L, wiping the user's weekly counter.
        _syncStatus.value = "Profile Synced"
    }

    fun syncSession(session: FocusSessionEntity, totalPoints: Int, streak: Int) {
        // Local-only tracking
    }
}

