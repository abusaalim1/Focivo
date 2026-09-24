package com.example.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AndroidPreferenceSessionManager(private val context: Context) : SessionManager {

    companion object {
        private const val TAG = "SessionManager"
        private const val PREFS_NAME = "supabase_auth_session"
        private const val KEY_SESSION = "supabase_user_session"
        private const val KEY_USER_ID = "supabase_persisted_user_id"
        private const val KEY_EMAIL = "supabase_persisted_email"
        private const val KEY_AVATAR_URL = "supabase_persisted_avatar_url"
        private const val KEY_ACCESS_TOKEN = "supabase_persisted_access_token"
        private const val KEY_REFRESH_TOKEN = "supabase_persisted_refresh_token"

        @Volatile
        var appContext: Context? = null

        fun setStoredUserId(context: Context, userId: String) {
            if (userId.isBlank()) return
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_USER_ID, userId)
                .commit()
            Log.d(TAG, "[SessionPersistence] Synchronously stored active userId: $userId")
        }

        fun getStoredUserId(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_USER_ID, null)
        }

        fun setStoredAvatarUrl(context: Context, avatarUrl: String) {
            if (avatarUrl.isBlank()) return
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_AVATAR_URL, avatarUrl)
                .commit()
            Log.d(TAG, "[SessionPersistence] Stored avatar url: ${avatarUrl.take(60)}...")
        }

        fun getStoredAvatarUrl(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_AVATAR_URL, null)
        }

        fun getStoredEmail(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_EMAIL, null)
        }

        fun getStoredRefreshToken(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_REFRESH_TOKEN, null)
        }

        fun clearStoredSession(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    override suspend fun saveSession(session: UserSession) {
        withContext(Dispatchers.IO) {
            try {
                val serialized = json.encodeToString(UserSession.serializer(), session)
                prefs.edit()
                    .putString(KEY_SESSION, serialized)
                    .putString(KEY_USER_ID, session.user?.id)
                    .putString(KEY_EMAIL, session.user?.email)
                    .putString(KEY_ACCESS_TOKEN, session.accessToken)
                    .putString(KEY_REFRESH_TOKEN, session.refreshToken)
                    .commit()
                Log.d(TAG, "Saved persistent session for user: ${session.user?.id}, email: ${session.user?.email}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save persistent session: ${e.message}", e)
            }
        }
    }

    override suspend fun loadSession(): UserSession? {
        return withContext(Dispatchers.IO) {
            try {
                val serialized = prefs.getString(KEY_SESSION, null)
                if (serialized.isNullOrBlank()) {
                    Log.d(TAG, "No persistent session found in SharedPreferences")
                    return@withContext null
                }
                val session = json.decodeFromString(UserSession.serializer(), serialized)
                Log.d(TAG, "Restored persistent session for user: ${session.user?.id}")
                session
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode persistent session: ${e.message}", e)
                null
            }
        }
    }

    override suspend fun deleteSession() {
        withContext(Dispatchers.IO) {
            try {
                prefs.edit().clear().commit()
                Log.d(TAG, "Cleared persistent session from SharedPreferences")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear persistent session: ${e.message}", e)
            }
        }
    }

    fun getPersistedUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    fun getPersistedEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }
}
