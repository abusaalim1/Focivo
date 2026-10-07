package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.model.FocusSessionEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreSessionRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "FirestoreSessionRepo"
        private const val COLLECTION_SESSIONS = "focus_sessions"
        private const val USERS_COLLECTION = "users"

        @Volatile
        private var INSTANCE: FirestoreSessionRepository? = null

        fun getInstance(): FirestoreSessionRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirestoreSessionRepository().also { INSTANCE = it }
            }
        }
    }

    /**
     * Observes past focus sessions for the given user from Firestore in real-time.
     */
    fun observeUserSessions(userId: String): Flow<List<FocusSessionEntity>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Listen to user's focus sessions ordered by completion time descending
        val userSessionsRef = db.collection(USERS_COLLECTION)
            .document(userId)
            .collection(COLLECTION_SESSIONS)
            .orderBy("completedAt", Query.Direction.DESCENDING)

        val registration = userSessionsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Listen failed for user sessions: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val sessions = snapshot.documents.mapNotNull { doc ->
                    try {
                        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                        val taskTitle = doc.getString("taskTitle") ?: "Focus Session"
                        val durationSeconds = (doc.getLong("durationSeconds") ?: 0L).toInt()
                        val targetDurationSeconds = (doc.getLong("targetDurationSeconds") ?: 0L).toInt()
                        val mode = doc.getString("mode") ?: "Deep Work"
                        val startTime = doc.getLong("startTime") ?: (doc.getLong("completedAt") ?: System.currentTimeMillis()) - (durationSeconds * 1000L)
                        val endTime = doc.getLong("endTime") ?: (doc.getLong("completedAt") ?: System.currentTimeMillis())
                        val distractionsCount = (doc.getLong("distractionsCount") ?: 0L).toInt()
                        val distractionTypes = doc.getString("distractionTypes") ?: ""
                        val focusPointsEarned = (doc.getLong("focusPointsEarned") ?: 12L).toInt()
                        val completedAt = doc.getLong("completedAt") ?: System.currentTimeMillis()
                        val dayOfWeek = (doc.getLong("dayOfWeek") ?: 1L).toInt()
                        val hourOfDay = (doc.getLong("hourOfDay") ?: 10L).toInt()
                        val notes = doc.getString("notes") ?: ""

                        FocusSessionEntity(
                            id = id,
                            userId = userId,
                            taskTitle = taskTitle,
                            durationSeconds = durationSeconds,
                            targetDurationSeconds = targetDurationSeconds,
                            mode = mode,
                            startTime = startTime,
                            endTime = endTime,
                            distractionsCount = distractionsCount,
                            distractionTypes = distractionTypes,
                            focusPointsEarned = focusPointsEarned,
                            completedAt = completedAt,
                            dayOfWeek = dayOfWeek,
                            hourOfDay = hourOfDay,
                            notes = notes
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error mapping doc ${doc.id}: ${e.message}")
                        null
                    }
                }
                trySend(sessions)
            }
        }

        awaitClose {
            registration.remove()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Saves a completed focus session to Firestore.
     */
    suspend fun saveSession(session: FocusSessionEntity, userId: String): Boolean = withContext(Dispatchers.IO) {
        val effectiveUid = if (userId.isNotBlank()) userId else FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        if (effectiveUid.isBlank() || effectiveUid == "guest") {
            return@withContext false
        }

        val docId = session.id.toString()
        val data = hashMapOf<String, Any>(
            "id" to session.id,
            "userId" to effectiveUid,
            "taskTitle" to session.taskTitle,
            "durationSeconds" to session.durationSeconds,
            "targetDurationSeconds" to session.targetDurationSeconds,
            "mode" to session.mode,
            "startTime" to session.startTime,
            "endTime" to session.endTime,
            "distractionsCount" to session.distractionsCount,
            "distractionTypes" to session.distractionTypes,
            "focusPointsEarned" to session.focusPointsEarned,
            "completedAt" to session.completedAt,
            "dayOfWeek" to session.dayOfWeek,
            "hourOfDay" to session.hourOfDay,
            "notes" to session.notes,
            "savedAt" to System.currentTimeMillis()
        )

        try {
            // Write to both user subcollection and root collection for multi-query resilience
            db.collection(USERS_COLLECTION)
                .document(effectiveUid)
                .collection(COLLECTION_SESSIONS)
                .document(docId)
                .set(data)
                .await()

            Log.d(TAG, "Successfully recorded focus session $docId to Firestore")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save session to Firestore: ${e.message}")
            false
        }
    }

    /**
     * Deletes a focus session from Firestore.
     */
    suspend fun deleteSession(sessionId: Long, userId: String): Boolean = withContext(Dispatchers.IO) {
        val effectiveUid = if (userId.isNotBlank()) userId else FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext false
        val docId = sessionId.toString()
        try {
            db.collection(USERS_COLLECTION)
                .document(effectiveUid)
                .collection(COLLECTION_SESSIONS)
                .document(docId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete session from Firestore: ${e.message}")
            false
        }
    }
}
