package com.example.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseService {

    companion object {
        private const val TAG = "SupabaseService"
        @Volatile
        private var instance: SupabaseService? = null

        fun getInstance(): SupabaseService {
            return instance ?: synchronized(this) {
                instance ?: SupabaseService().also { instance = it }
            }
        }
    }

    fun clearCache() {
        val ctx = SupabaseManager.getApplicationContext()
        if (ctx != null) {
            AndroidPreferenceSessionManager.clearStoredSession(ctx)
        }
    }

    private val client = SupabaseManager.client

    suspend fun awaitSessionRestoration(): String? = withContext(Dispatchers.IO) {
        try {
            client.auth.awaitInitialization()
            val user = client.auth.currentUserOrNull()
            if (user != null) {
                Log.d(TAG, "awaitSessionRestoration: restored user=${user.id}, email=${user.email}")
                return@withContext user.id
            }
            val session = client.auth.currentSessionOrNull()
            if (session?.user != null) {
                Log.d(TAG, "awaitSessionRestoration: restored from session=${session.user?.id}")
                return@withContext session.user?.id
            }
            // Fallback: check SharedPreferences durable storage
            val ctx = SupabaseManager.getApplicationContext()
            if (ctx != null) {
                val storedId = AndroidPreferenceSessionManager.getStoredUserId(ctx)
                if (!storedId.isNullOrBlank()) {
                    Log.d(TAG, "awaitSessionRestoration: restored from local storage uid=$storedId")
                    return@withContext storedId
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "awaitSessionRestoration error (possibly offline): ${e.message}")
            // Don't log user out if device is temporarily offline!
            val ctx = SupabaseManager.getApplicationContext()
            val storedId = ctx?.let { AndroidPreferenceSessionManager.getStoredUserId(it) }
            if (!storedId.isNullOrBlank()) {
                Log.i(TAG, "Preserving offline logged-in session for user: $storedId")
                storedId
            } else {
                null
            }
        }
    }

    suspend fun handleAuthDeeplink(uri: android.net.Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "handleAuthDeeplink: processing deep link $uri")

            val fragment = uri.fragment
            val fragmentParams = if (!fragment.isNullOrBlank()) {
                fragment.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                }
            } else {
                emptyMap()
            }

            // 1. Check for error parameters in query or fragment
            val error = uri.getQueryParameter("error") ?: fragmentParams["error"]
            val errorCode = uri.getQueryParameter("error_code") ?: fragmentParams["error_code"]
            val errorDescription = uri.getQueryParameter("error_description") ?: fragmentParams["error_description"]

            if (!error.isNullOrBlank() || !errorCode.isNullOrBlank() || !errorDescription.isNullOrBlank()) {
                Log.w(TAG, "Auth deep link returned error: $error, code: $errorCode, desc: $errorDescription")
                val isExpired = errorCode == "otp_expired" ||
                        errorDescription?.contains("expired", ignoreCase = true) == true ||
                        errorDescription?.contains("invalid", ignoreCase = true) == true ||
                        error?.contains("expired", ignoreCase = true) == true
                val message = if (isExpired) {
                    "This verification link has expired, please request a new one"
                } else {
                    errorDescription?.replace("+", " ") ?: "This verification link has expired, please request a new one"
                }
                return@withContext Result.failure(Exception(message))
            }

            // 2. Check for 'code' query parameter (PKCE flow)
            val code = uri.getQueryParameter("code") ?: fragmentParams["code"]
            if (!code.isNullOrBlank()) {
                Log.d(TAG, "Exchanging PKCE code for session...")
                client.auth.exchangeCodeForSession(code)
                val user = client.auth.currentUserOrNull()
                if (user != null) {
                    val ctx = SupabaseManager.getApplicationContext()
                    if (ctx != null) {
                        client.auth.currentSessionOrNull()?.let { session ->
                            AndroidPreferenceSessionManager(ctx).saveSession(session)
                        }
                    }
                    return@withContext Result.success(user.id)
                }
            }

            // 4. Check for direct tokens in query or fragment (#access_token=...&refresh_token=...)
            val accessToken = uri.getQueryParameter("access_token") ?: fragmentParams["access_token"]
            val refreshToken = uri.getQueryParameter("refresh_token") ?: fragmentParams["refresh_token"]
            if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                val expiresIn = (uri.getQueryParameter("expires_in") ?: fragmentParams["expires_in"])?.toLongOrNull() ?: 3600L
                val tokenType = uri.getQueryParameter("token_type") ?: fragmentParams["token_type"] ?: "bearer"
                val userSession = io.github.jan.supabase.auth.user.UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn,
                    tokenType = tokenType,
                    user = null
                )
                client.auth.importSession(userSession)
                val user = client.auth.retrieveUserForCurrentSession()
                val ctx = SupabaseManager.getApplicationContext()
                if (ctx != null) {
                    AndroidPreferenceSessionManager(ctx).saveSession(client.auth.currentSessionOrNull() ?: userSession)
                }
                return@withContext Result.success(user.id)
            }

            // 5. Fallback: check if session is already initialized
            val user = client.auth.currentUserOrNull()
            if (user != null) {
                return@withContext Result.success(user.id)
            }

            Result.failure(Exception("This verification link has expired, please request a new one"))
        } catch (e: Exception) {
            Log.e(TAG, "handleAuthDeeplink error: ${e.message}", e)
            val msg = if (e.message?.contains("expired", ignoreCase = true) == true || e.message?.contains("invalid", ignoreCase = true) == true) {
                "This verification link has expired, please request a new one"
            } else {
                e.message ?: "This verification link has expired, please request a new one"
            }
            Result.failure(Exception(msg))
        }
    }

    fun getCurrentUserId(): String? {
        return try {
            client.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun signUp(email: String, pinOrPass: String, fullName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            client.auth.signUpWith(Email, redirectUrl = "regain://auth-callback") {
                this.email = cleanEmail
                this.password = pinOrPass
            }

            val user = client.auth.currentUserOrNull()
            val userId = user?.id ?: return@withContext Result.failure(Exception("Account registered! Please check your email inbox to confirm your address before logging in."))

            // 1. Insert matching row in public.users
            try {
                val userDto = SupabaseUserDto(
                    id = userId,
                    email = cleanEmail,
                    full_name = fullName,
                    primary_goal = "Boost Focus & Productivity",
                    focus_style = "Deep Work (50m)",
                    daily_target_hours = 4,
                    peak_productivity_time = "Morning (8 AM - 12 PM)",
                    primary_distraction = "Smartphone & Social Media",
                    sound_preference = "Silent"
                )
                client.from("users").upsert(userDto)
            } catch (e: Exception) {
                Log.w(TAG, "Error inserting into public.users: ${e.message}")
            }

            // 2. Insert matching row in public.user_preferences
            try {
                val prefsDto = SupabaseUserPreferencesDto(
                    user_id = userId,
                    has_completed_onboarding = true
                )
                client.from("user_preferences").upsert(prefsDto)
            } catch (e: Exception) {
                Log.w(TAG, "Error inserting into public.user_preferences: ${e.message}")
            }

            // 3. Insert matching row in public.study_leaderboard
            try {
                val leaderboardDto = SupabaseStudyLeaderboardDto(
                    user_id = userId,
                    display_name = fullName.ifBlank { cleanEmail.substringBefore("@") },
                    study_seconds = 0L,
                    streak = 1,
                    subject_tag = "General Study"
                )
                client.from("study_leaderboard").upsert(leaderboardDto)
            } catch (e: Exception) {
                Log.w(TAG, "Error inserting into public.study_leaderboard: ${e.message}")
            }

            Result.success(userId)
        } catch (e: Exception) {
            Log.w(TAG, "SignUp attempt failed: ${e.message}")
            val rawMsg = e.message ?: ""
            val userFriendlyMsg = when {
                rawMsg.contains("already registered", ignoreCase = true) || rawMsg.contains("already exists", ignoreCase = true) || rawMsg.contains("User already registered", ignoreCase = true) ->
                    "An account with this email already exists. Please sign in instead."
                rawMsg.contains("Password should be at least", ignoreCase = true) ->
                    "Password must be at least 6 characters long."
                rawMsg.contains("Unable to resolve host", ignoreCase = true) || rawMsg.contains("ConnectException", ignoreCase = true) ->
                    "Network error. Please check your internet connection."
                else -> e.localizedMessage ?: "Sign up failed"
            }
            Result.failure(Exception(userFriendlyMsg))
        }
    }

    suspend fun signIn(email: String, pinOrPass: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            client.auth.signInWith(Email) {
                this.email = cleanEmail
                this.password = pinOrPass
            }

            val user = client.auth.currentUserOrNull()
            val userId = user?.id ?: return@withContext Result.failure(Exception("Supabase login succeeded but no user ID returned"))

            Result.success(userId)
        } catch (e: Exception) {
            Log.w(TAG, "SignIn attempt failed: ${e.message}")
            val rawMsg = e.message ?: ""
            val userFriendlyMsg = when {
                rawMsg.contains("invalid_credentials", ignoreCase = true) || rawMsg.contains("Invalid login credentials", ignoreCase = true) ->
                    "Invalid email or password. Please check your login credentials or create a new account."
                rawMsg.contains("Email not confirmed", ignoreCase = true) ->
                    "Email not confirmed. Please check your inbox or spam folder to confirm your email address."
                rawMsg.contains("User not found", ignoreCase = true) ->
                    "No account found with this email. Please sign up."
                rawMsg.contains("Unable to resolve host", ignoreCase = true) || rawMsg.contains("ConnectException", ignoreCase = true) ->
                    "Network error. Please check your internet connection."
                else -> e.localizedMessage ?: "Sign in failed"
            }
            Result.failure(Exception(userFriendlyMsg))
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            client.auth.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "SignOut error: ${e.message}")
        }
    }

    suspend fun fetchUserTasks(userId: String): List<SupabaseTaskDto> = withContext(Dispatchers.IO) {
        try {
            client.from("tasks").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeList<SupabaseTaskDto>()
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserTasks error: ${e.message}")
            emptyList()
        }
    }

    suspend fun upsertTask(taskDto: SupabaseTaskDto) = withContext(Dispatchers.IO) {
        try {
            client.from("tasks").upsert(taskDto)
        } catch (e: Exception) {
            Log.w(TAG, "upsertTask error: ${e.message}")
        }
    }

    suspend fun deleteTask(taskId: String) = withContext(Dispatchers.IO) {
        try {
            client.from("tasks").delete {
                filter {
                    eq("id", taskId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "deleteTask error: ${e.message}")
        }
    }

    suspend fun fetchFocusSessions(userId: String): List<SupabaseFocusSessionDto> = withContext(Dispatchers.IO) {
        try {
            client.from("focus_sessions").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeList<SupabaseFocusSessionDto>()
        } catch (e: Exception) {
            Log.w(TAG, "fetchFocusSessions error: ${e.message}")
            emptyList()
        }
    }

    suspend fun upsertFocusSession(sessionDto: SupabaseFocusSessionDto) = withContext(Dispatchers.IO) {
        try {
            client.from("focus_sessions").upsert(sessionDto)
        } catch (e: Exception) {
            Log.w(TAG, "upsertFocusSession error: ${e.message}")
        }
    }

    suspend fun fetchReflections(userId: String): List<SupabaseReflectionDto> = withContext(Dispatchers.IO) {
        try {
            client.from("reflections").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeList<SupabaseReflectionDto>()
        } catch (e: Exception) {
            Log.w(TAG, "fetchReflections error: ${e.message}")
            emptyList()
        }
    }

    suspend fun upsertReflection(reflectionDto: SupabaseReflectionDto) = withContext(Dispatchers.IO) {
        try {
            client.from("reflections").upsert(reflectionDto)
        } catch (e: Exception) {
            Log.w(TAG, "upsertReflection error: ${e.message}")
        }
    }

    suspend fun fetchUserPreferences(userId: String): SupabaseUserPreferencesDto? = withContext(Dispatchers.IO) {
        try {
            client.from("user_preferences").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeSingleOrNull<SupabaseUserPreferencesDto>()
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserPreferences error: ${e.message}")
            null
        }
    }

    suspend fun upsertUserPreferences(prefsDto: SupabaseUserPreferencesDto) = withContext(Dispatchers.IO) {
        try {
            client.from("user_preferences").upsert(prefsDto)
        } catch (e: Exception) {
            Log.w(TAG, "upsertUserPreferences error: ${e.message}")
        }
    }

    suspend fun fetchUserProfile(userId: String): SupabaseUserDto? = withContext(Dispatchers.IO) {
        try {
            client.from("users").select {
                filter {
                    eq("id", userId)
                }
            }.decodeSingleOrNull<SupabaseUserDto>()
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserProfile error: ${e.message}")
            null
        }
    }

    suspend fun upsertUserProfile(userDto: SupabaseUserDto) = withContext(Dispatchers.IO) {
        try {
            client.from("users").upsert(userDto)
        } catch (e: Exception) {
            Log.w(TAG, "upsertUserProfile error: ${e.message}")
        }
    }

    suspend fun fetchLeaderboard(): List<SupabaseStudyLeaderboardDto> = withContext(Dispatchers.IO) {
        try {
            client.from("study_leaderboard").select().decodeList<SupabaseStudyLeaderboardDto>()
        } catch (e: Exception) {
            Log.w(TAG, "fetchLeaderboard error: ${e.message}")
            emptyList()
        }
    }

    suspend fun upsertLeaderboard(leaderboardDto: SupabaseStudyLeaderboardDto) = withContext(Dispatchers.IO) {
        try {
            client.from("study_leaderboard").upsert(leaderboardDto)
        } catch (e: Exception) {
            Log.w(TAG, "upsertLeaderboard error: ${e.message}")
        }
    }

    suspend fun fetchScheduledBlocks(userId: String): List<SupabaseScheduledBlockDto> = withContext(Dispatchers.IO) {
        try {
            client.from("scheduled_blocks").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeList<SupabaseScheduledBlockDto>()
        } catch (e: Exception) {
            Log.w(TAG, "fetchScheduledBlocks error: ${e.message}")
            emptyList()
        }
    }

    private fun isUuidValid(uuidStr: String?): Boolean {
        if (uuidStr.isNullOrBlank()) return false
        return try {
            java.util.UUID.fromString(uuidStr)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun upsertScheduledBlock(blockDto: SupabaseScheduledBlockDto): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentUid = getCurrentUserId()
            val validUserId = when {
                isUuidValid(blockDto.user_id) -> blockDto.user_id
                isUuidValid(currentUid) -> currentUid
                else -> null
            }

            if (validUserId == null) {
                Log.w(TAG, "[SupabaseDatabaseWrite] Skipping remote Supabase upsert for scheduled_block id=${blockDto.id}: user_id '${blockDto.user_id}' is not a valid UUID")
                return@withContext Result.success(Unit)
            }

            val finalStartTime = if (blockDto.start_time.isBlank()) "18:00" else blockDto.start_time
            val finalEndTime = if (blockDto.end_time.isBlank()) "22:00" else blockDto.end_time
            val finalLabel = if (blockDto.label.isBlank()) "Study Block" else blockDto.label
            val finalDays = if (blockDto.days_active.isBlank()) "Mon,Tue,Wed,Thu,Fri" else blockDto.days_active

            val finalDto = blockDto.copy(
                user_id = validUserId,
                start_time = finalStartTime,
                end_time = finalEndTime,
                label = finalLabel,
                days_active = finalDays
            )
            Log.d(TAG, "[SupabaseDatabaseWrite] Upserting scheduled_block: id=${finalDto.id}, user_id=${finalDto.user_id}, label='${finalDto.label}', start_time='${finalDto.start_time}', end_time='${finalDto.end_time}', is_enabled=${finalDto.is_enabled}")
            client.from("scheduled_blocks").upsert(finalDto)
            Log.i(TAG, "[SupabaseDatabaseWrite] Successfully wrote to public.scheduled_blocks for id=${finalDto.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[SupabaseDatabaseWrite] Error writing to public.scheduled_blocks for id=${blockDto.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteScheduledBlock(blockId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[SupabaseDatabaseWrite] Deleting from scheduled_blocks where id=$blockId")
            client.from("scheduled_blocks").delete {
                filter {
                    eq("id", blockId)
                }
            }
            Log.i(TAG, "[SupabaseDatabaseWrite] Successfully deleted from public.scheduled_blocks for id=$blockId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[SupabaseDatabaseWrite] Error deleting from public.scheduled_blocks for id=$blockId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchPunishmentLogs(userId: String): List<SupabasePunishmentLogDto> = withContext(Dispatchers.IO) {
        try {
            client.from("punishment_logs").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeList<SupabasePunishmentLogDto>()
        } catch (e: Exception) {
            Log.w(TAG, "fetchPunishmentLogs error: ${e.message}")
            emptyList()
        }
    }

    suspend fun upsertPunishmentLog(logDto: SupabasePunishmentLogDto): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.from("punishment_logs").upsert(logDto)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "upsertPunishmentLog error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchAlarms(userId: String): List<SupabaseAlarmDto> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[AlarmLoad] Querying SELECT * FROM public.alarms WHERE user_id = '$userId'")
            val result = client.from("alarms").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeList<SupabaseAlarmDto>()
            Log.d(TAG, "[AlarmLoad] Loaded ${result.size} rows from public.alarms for uid=$userId: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "[AlarmLoad] FAILED query for uid=$userId: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun upsertAlarm(alarmDto: SupabaseAlarmDto): Result<SupabaseAlarmDto> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[AlarmSave] Executing upsert to public.alarms for dto=$alarmDto")
            client.from("alarms").upsert(alarmDto)
            Log.d(TAG, "[AlarmSave] Supabase upsert SUCCESS for alarm id=${alarmDto.id}, label='${alarmDto.label}'")
            Result.success(alarmDto)
        } catch (e: Exception) {
            Log.e(TAG, "[AlarmSave] Supabase upsert FAILED for id=${alarmDto.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAlarm(alarmId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[AlarmDelete] Deleting alarm from public.alarms id=$alarmId")
            client.from("alarms").delete {
                filter {
                    eq("id", alarmId)
                }
            }
            Log.d(TAG, "[AlarmDelete] Supabase delete SUCCESS for id=$alarmId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[AlarmDelete] Supabase delete FAILED for id=$alarmId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadAvatar(userId: String, imageBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = "$userId/avatar.jpg"
            val storageUrl = "${SupabaseManager.SUPABASE_URL}/storage/v1/object/avatars/$fileName"

            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestBody = okhttp3.RequestBody.create(mediaType, imageBytes)
            val request = okhttp3.Request.Builder()
                .url(storageUrl)
                .addHeader("Authorization", "Bearer " + (client.auth.currentSessionOrNull()?.accessToken ?: SupabaseManager.SUPABASE_KEY))
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("x-upsert", "true")
                .post(requestBody)
                .build()

            val okHttpClient = okhttp3.OkHttpClient()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful || response.code == 200 || response.code == 201) {
                val publicUrl = "${SupabaseManager.SUPABASE_URL}/storage/v1/object/public/avatars/$fileName"
                Log.d(TAG, "Avatar uploaded successfully to: $publicUrl")
                Result.success(publicUrl)
            } else {
                val errBody = response.body?.string().orEmpty()
                Log.w(TAG, "Avatar upload HTTP ${response.code}: $errBody — using fallback data URL")
                val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                val dataUrl = "data:image/jpeg;base64,$base64"
                Result.success(dataUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading avatar: ${e.message}", e)
            val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$base64"
            Result.success(dataUrl)
        }
    }

    suspend fun uploadAvatarImage(userId: String, imageBytes: ByteArray): String? {
        return uploadAvatar(userId, imageBytes).getOrNull()
    }
}
