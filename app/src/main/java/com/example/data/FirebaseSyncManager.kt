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
    val studySeconds: Long = 0L,
    val streak: Int = 1,
    val subjectTag: String = "Study",
    val isCurrentUser: Boolean = false
)

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

    init {
        fetchSupabaseLeaderboard()
    }

    private fun fetchSupabaseLeaderboard() {
        scope.launch {
            try {
                val remoteList = SupabaseService.getInstance().fetchLeaderboard()
                if (remoteList.isNotEmpty()) {
                    val currentUserId = SupabaseService.getInstance().getCurrentUserId()
                    val mappedList = remoteList.map { dto ->
                        LeaderboardUser(
                            id = dto.user_id,
                            displayName = dto.display_name,
                            studySeconds = dto.study_seconds,
                            streak = dto.streak,
                            subjectTag = dto.subject_tag,
                            isCurrentUser = dto.user_id == currentUserId
                        )
                    }.sortedByDescending { it.studySeconds }
                    _leaderboardUsers.value = mappedList
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching Supabase leaderboard: ${e.message}")
            }
        }
    }

    fun recordUserStudyProgress(
        userId: String,
        displayName: String,
        studySeconds: Long,
        streak: Int,
        subjectTag: String
    ) {
        scope.launch {
            val currentList = _leaderboardUsers.value.filterNot { it.id == userId || it.isCurrentUser }
            val me = LeaderboardUser(
                id = userId,
                displayName = displayName,
                studySeconds = studySeconds,
                streak = streak,
                subjectTag = subjectTag,
                isCurrentUser = true
            )
            _leaderboardUsers.value = (listOf(me) + currentList).sortedByDescending { it.studySeconds }

            // Sync to Supabase public.study_leaderboard
            if (userId.isNotBlank()) {
                try {
                    val dto = SupabaseStudyLeaderboardDto(
                        user_id = userId,
                        display_name = displayName,
                        study_seconds = studySeconds,
                        streak = streak,
                        subject_tag = subjectTag
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
        recordUserStudyProgress(userId, displayName, studySeconds.toLong(), streak, "Deep Work")
    }

    fun syncSession(session: FocusSessionEntity, totalPoints: Int, streak: Int) {
        // Local-only tracking
    }
}
