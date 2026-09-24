package com.example.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
            // Fallback: check SharedPreferences durable storage & import session
            val ctx = SupabaseManager.getApplicationContext()
            if (ctx != null) {
                val storedSession = AndroidPreferenceSessionManager(ctx).loadSession()
                if (storedSession != null) {
                    try {
                        client.auth.importSession(storedSession)
                        val importedUser = client.auth.currentUserOrNull()
                        if (importedUser != null) {
                            Log.d(TAG, "awaitSessionRestoration: imported session for user=${importedUser.id}")
                            return@withContext importedUser.id
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed importing stored session: ${e.message}")
                    }
                }
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

    fun getAuthToken(): String? {
        return try {
            client.auth.currentSessionOrNull()?.accessToken
                ?: SupabaseManager.getApplicationContext()?.let {
                    it.getSharedPreferences("supabase_auth_session", android.content.Context.MODE_PRIVATE)
                        .getString("supabase_persisted_access_token", null)
                }
        } catch (_: Exception) {
            null
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
            val uid = client.auth.currentUserOrNull()?.id
            if (!uid.isNullOrBlank()) return uid
            val ctx = SupabaseManager.getApplicationContext()
            ctx?.let { AndroidPreferenceSessionManager.getStoredUserId(it) }
        } catch (e: Exception) {
            val ctx = SupabaseManager.getApplicationContext()
            ctx?.let { AndroidPreferenceSessionManager.getStoredUserId(it) }
        }
    }

    suspend fun signUp(email: String, pinOrPass: String, fullName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            client.auth.signUpWith(Email, redirectUrl = "focivo://auth-callback") {
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
        if (userId.isBlank()) return@withContext emptyList()
        try {
            val list = client.from("tasks").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeList<SupabaseTaskDto>()
            if (list.isNotEmpty()) return@withContext list
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserTasks SDK error: ${e.message}")
        }

        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val getUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/tasks?user_id=eq.$userId&select=*"
            val request = okhttp3.Request.Builder()
                .url(getUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = okhttp3.OkHttpClient().newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful && body.isNotBlank()) {
                val jsonArray = org.json.JSONArray(body)
                val result = mutableListOf<SupabaseTaskDto>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    result.add(
                        SupabaseTaskDto(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            user_id = obj.optString("user_id", userId),
                            title = obj.optString("title", ""),
                            category = obj.optString("category", "Deep Work"),
                            priority = obj.optInt("priority", 1),
                            duration_minutes = obj.optInt("duration_minutes", 25),
                            scheduled_time = obj.optString("scheduled_time", "09:00"),
                            is_completed = obj.optBoolean("is_completed", false),
                            is_top_priority = obj.optBoolean("is_top_priority", false),
                            notes = obj.optString("notes", ""),
                            date = obj.optString("date", "")
                        )
                    )
                }
                return@withContext result
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchUserTasks REST fallback error: ${e.message}")
        }
        emptyList()
    }

    suspend fun upsertTask(taskDto: SupabaseTaskDto): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = taskDto.user_id
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("user_id is blank"))
        }
        try {
            client.from("tasks").upsert(taskDto)
            Log.i(TAG, "[TaskSync] SDK upsert succeeded for task_id=${taskDto.id}")
            return@withContext Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "[TaskSync] SDK upsert failed: ${e.message}. Trying REST fallback...")
        }

        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val postUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/tasks"
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            val json = org.json.JSONObject().apply {
                put("id", taskDto.id)
                put("user_id", taskDto.user_id)
                put("title", taskDto.title)
                put("category", taskDto.category)
                put("priority", taskDto.priority)
                put("duration_minutes", taskDto.duration_minutes)
                put("scheduled_time", taskDto.scheduled_time)
                put("is_completed", taskDto.is_completed)
                put("is_top_priority", taskDto.is_top_priority)
                put("notes", taskDto.notes)
                put("date", taskDto.date)
            }

            val requestBody = json.toString().toRequestBody(mediaType)
            val request = okhttp3.Request.Builder()
                .url(postUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(requestBody)
                .build()

            val okHttpClient = okhttp3.OkHttpClient()
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            if (code in 200..299) {
                Log.i(TAG, "[TaskSync] REST POST succeeded for task_id=${taskDto.id}")
                Result.success(true)
            } else {
                Log.w(TAG, "[TaskSync] REST POST failed code $code: ${response.body?.string()}")
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[TaskSync] Error saving task: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            client.from("tasks").delete {
                filter {
                    eq("id", taskId)
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "deleteTask error: ${e.message}")
            try {
                val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
                val deleteUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/tasks?id=eq.$taskId"
                val request = okhttp3.Request.Builder()
                    .url(deleteUrl)
                    .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()
                val response = okhttp3.OkHttpClient().newCall(request).execute()
                if (response.isSuccessful) Result.success(true) else Result.failure(Exception("HTTP ${response.code}"))
            } catch (ex: Exception) {
                Result.failure(ex)
            }
        }
    }

    suspend fun fetchFocusSessions(userId: String): List<SupabaseFocusSessionDto> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext emptyList()
        try {
            val list = client.from("focus_sessions").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeList<SupabaseFocusSessionDto>()
            if (list.isNotEmpty()) return@withContext list
        } catch (e: Exception) {
            Log.w(TAG, "fetchFocusSessions SDK error: ${e.message}")
        }

        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val getUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/focus_sessions?user_id=eq.$userId&select=*"
            val request = okhttp3.Request.Builder()
                .url(getUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = okhttp3.OkHttpClient().newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful && body.isNotBlank()) {
                val jsonArray = org.json.JSONArray(body)
                val result = mutableListOf<SupabaseFocusSessionDto>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val completedAtVal = when {
                        obj.has("completed_at") && !obj.isNull("completed_at") -> {
                            val v = obj.get("completed_at")
                            if (v is Number) v.toLong()
                            else if (v is String) {
                                try {
                                    java.time.Instant.parse(v).toEpochMilli()
                                } catch (ex: Exception) {
                                    try {
                                        v.toLong()
                                    } catch (_: Exception) {
                                        System.currentTimeMillis()
                                    }
                                }
                            } else System.currentTimeMillis()
                        }
                        else -> System.currentTimeMillis()
                    }
                    result.add(
                        SupabaseFocusSessionDto(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            user_id = obj.optString("user_id", userId),
                            task_title = obj.optString("task_title", ""),
                            duration_seconds = obj.optInt("duration_seconds", 0),
                            target_duration_seconds = obj.optInt("target_duration_seconds", 0),
                            mode = obj.optString("mode", "Deep Work"),
                            distractions_count = obj.optInt("distractions_count", 0),
                            distraction_types = obj.optString("distraction_types", ""),
                            focus_points_earned = obj.optInt("focus_points_earned", 12),
                            completed_at = completedAtVal,
                            day_of_week = obj.optInt("day_of_week", 1),
                            hour_of_day = obj.optInt("hour_of_day", 10),
                            notes = obj.optString("notes", "")
                        )
                    )
                }
                return@withContext result
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchFocusSessions REST fallback error: ${e.message}")
        }
        emptyList()
    }

    suspend fun upsertFocusSession(sessionDto: SupabaseFocusSessionDto): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = sessionDto.user_id
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("user_id is blank"))
        }
        try {
            client.from("focus_sessions").upsert(sessionDto)
            Log.i(TAG, "[FocusSessionSync] SDK upsert succeeded for session_id=${sessionDto.id}")
            return@withContext Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "[FocusSessionSync] SDK upsert failed: ${e.message}. Trying REST fallback...")
        }

        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val postUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/focus_sessions"
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            val json = org.json.JSONObject().apply {
                put("id", sessionDto.id)
                put("user_id", sessionDto.user_id)
                put("task_title", sessionDto.task_title)
                put("duration_seconds", sessionDto.duration_seconds)
                put("target_duration_seconds", sessionDto.target_duration_seconds)
                put("mode", sessionDto.mode)
                put("distractions_count", sessionDto.distractions_count)
                put("distraction_types", sessionDto.distraction_types)
                put("focus_points_earned", sessionDto.focus_points_earned)
                sessionDto.completed_at?.let { put("completed_at", it) }
                put("day_of_week", sessionDto.day_of_week)
                put("hour_of_day", sessionDto.hour_of_day)
                put("notes", sessionDto.notes)
            }

            val requestBody = json.toString().toRequestBody(mediaType)
            val request = okhttp3.Request.Builder()
                .url(postUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(requestBody)
                .build()

            val okHttpClient = okhttp3.OkHttpClient()
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            if (code in 200..299) {
                Log.i(TAG, "[FocusSessionSync] REST POST succeeded for session_id=${sessionDto.id}")
                Result.success(true)
            } else {
                Log.w(TAG, "[FocusSessionSync] REST POST failed code $code: ${response.body?.string()}")
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[FocusSessionSync] Error saving focus session: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchReflections(userId: String): List<SupabaseReflectionDto> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext emptyList()
        try {
            val list = client.from("reflections").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeList<SupabaseReflectionDto>()
            if (list.isNotEmpty()) return@withContext list
        } catch (e: Exception) {
            Log.w(TAG, "fetchReflections SDK error: ${e.message}")
        }

        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val getUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/reflections?user_id=eq.$userId&select=*"
            val request = okhttp3.Request.Builder()
                .url(getUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = okhttp3.OkHttpClient().newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful && body.isNotBlank()) {
                val jsonArray = org.json.JSONArray(body)
                val result = mutableListOf<SupabaseReflectionDto>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    result.add(
                        SupabaseReflectionDto(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            user_id = obj.optString("user_id", userId),
                            week_label = obj.optString("week_label", ""),
                            total_minutes_focused = obj.optInt("total_minutes_focused", 0),
                            sessions_completed = obj.optInt("sessions_completed", 0),
                            best_day = obj.optString("best_day", "Wednesday"),
                            completion_rate = obj.optInt("completion_rate", 80),
                            reflection_text = obj.optString("reflection_text", "")
                        )
                    )
                }
                return@withContext result
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchReflections REST fallback error: ${e.message}")
        }
        emptyList()
    }

    suspend fun upsertReflection(reflectionDto: SupabaseReflectionDto): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = reflectionDto.user_id
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("user_id is blank"))
        }
        try {
            client.from("reflections").upsert(reflectionDto)
            Log.i(TAG, "[ReflectionSync] SDK upsert succeeded for reflection_id=${reflectionDto.id}")
            return@withContext Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "[ReflectionSync] SDK upsert failed: ${e.message}. Trying REST fallback...")
        }

        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val postUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/reflections"
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            val json = org.json.JSONObject().apply {
                put("id", reflectionDto.id)
                put("user_id", reflectionDto.user_id)
                put("week_label", reflectionDto.week_label)
                put("total_minutes_focused", reflectionDto.total_minutes_focused)
                put("sessions_completed", reflectionDto.sessions_completed)
                put("best_day", reflectionDto.best_day)
                put("completion_rate", reflectionDto.completion_rate)
                put("reflection_text", reflectionDto.reflection_text)
            }

            val requestBody = json.toString().toRequestBody(mediaType)
            val request = okhttp3.Request.Builder()
                .url(postUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(requestBody)
                .build()

            val okHttpClient = okhttp3.OkHttpClient()
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            if (code in 200..299) {
                Log.i(TAG, "[ReflectionSync] REST POST succeeded for reflection_id=${reflectionDto.id}")
                Result.success(true)
            } else {
                Log.w(TAG, "[ReflectionSync] REST POST failed code $code: ${response.body?.string()}")
                Result.failure(Exception("HTTP $code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[ReflectionSync] Error saving reflection: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchUserPreferences(userId: String): SupabaseUserPreferencesDto? = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext null
        try {
            val dto = client.from("user_preferences").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeSingleOrNull<SupabaseUserPreferencesDto>()
            if (dto != null) return@withContext dto
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserPreferences SDK error: ${e.message}")
        }

        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val getUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/user_preferences?user_id=eq.$userId&select=*"
            val request = okhttp3.Request.Builder()
                .url(getUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = okhttp3.OkHttpClient().newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful && body.isNotBlank()) {
                val jsonArray = org.json.JSONArray(body)
                if (jsonArray.length() > 0) {
                    val obj = jsonArray.getJSONObject(0)
                    return@withContext SupabaseUserPreferencesDto(
                        user_id = obj.optString("user_id", userId),
                        theme_mode = obj.optString("theme_mode", "system"),
                        daily_goal_minutes = obj.optInt("daily_goal_minutes", 360),
                        preferred_focus_mode = obj.optString("preferred_focus_mode", "Deep Work"),
                        deep_work_duration = obj.optInt("deep_work_duration", 50),
                        deep_work_break = obj.optInt("deep_work_break", 10),
                        classic_duration = obj.optInt("classic_duration", 25),
                        classic_break = obj.optInt("classic_break", 5),
                        short_sprint_duration = obj.optInt("short_sprint_duration", 15),
                        sound_enabled = obj.optBoolean("sound_enabled", true),
                        ambient_sound = obj.optString("ambient_sound", "Silent"),
                        haptics_enabled = obj.optBoolean("haptics_enabled", true),
                        notifications_enabled = obj.optBoolean("notifications_enabled", true),
                        auto_start_break = obj.optBoolean("auto_start_break", false),
                        auto_start_focus = obj.optBoolean("auto_start_focus", false),
                        week_starts_on = obj.optString("week_starts_on", "Monday"),
                        has_completed_onboarding = obj.optBoolean("has_completed_onboarding", false),
                        user_level = obj.optInt("user_level", 1),
                        focus_points = obj.optInt("focus_points", 0),
                        focus_identity = obj.optString("focus_identity", "Novice Deep Worker"),
                        current_streak = obj.optInt("current_streak", 0),
                        best_streak = obj.optInt("best_streak", 0),
                        alarm_ringtone = obj.optString("alarm_ringtone", "Zen Bell"),
                        is_app_blocker_enabled = obj.optBoolean("is_app_blocker_enabled", false),
                        app_blocker_duration_minutes = obj.optInt("app_blocker_duration_minutes", 25),
                        app_blocker_end_time = obj.optLong("app_blocker_end_time", 0L),
                        blocked_apps_list = obj.optString("blocked_apps_list", "com.instagram.android,com.zhiliaoapp.musically,com.twitter.android,com.facebook.katana,com.snapchat.android,com.reddit.frontpage"),
                        shield_blocked_attempts = obj.optInt("shield_blocked_attempts", 0),
                        allowed_education_apps = obj.optString("allowed_education_apps", "com.google.android.youtube,com.openai.chatgpt,com.anthropic.claude"),
                        is_auto_study_blocker_enabled = obj.optBoolean("is_auto_study_blocker_enabled", true),
                        penalty_block_end_time = obj.optLong("penalty_block_end_time", 0L),
                        buddy_growth_stage = obj.optInt("buddy_growth_stage", 1),
                        buddy_total_focus_minutes = obj.optInt("buddy_total_focus_minutes", 0),
                        last_donation_prompt_shown_at = if (obj.has("last_donation_prompt_shown_at") && !obj.isNull("last_donation_prompt_shown_at")) obj.getString("last_donation_prompt_shown_at") else null,
                        donation_prompt_dismissed_count = obj.optInt("donation_prompt_dismissed_count", 0),
                        never_show_donation_prompt = obj.optBoolean("never_show_donation_prompt", false),
                        last_viewed_sunday_recap_week = if (obj.has("last_viewed_sunday_recap_week") && !obj.isNull("last_viewed_sunday_recap_week")) obj.getString("last_viewed_sunday_recap_week") else null
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchUserPreferences REST fallback error: ${e.message}")
        }
        null
    }

    suspend fun upsertUserPreferences(prefsDto: SupabaseUserPreferencesDto): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = prefsDto.user_id
        if (userId.isBlank()) {
            Log.e(TAG, "[UserPreferencesSync] BEFORE WRITE ERROR: user_id is blank!")
            return@withContext Result.failure(IllegalArgumentException("user_id is blank"))
        }

        Log.i(
            TAG,
            "[UserPreferencesSync] BEFORE WRITE: Executing UPDATE/UPSERT public.user_preferences SET focus_points=${prefsDto.focus_points}, user_level=${prefsDto.user_level}, current_streak=${prefsDto.current_streak}, best_streak=${prefsDto.best_streak} WHERE user_id='$userId'"
        )

        try {
            client.from("user_preferences").upsert(prefsDto)
            Log.i(TAG, "[UserPreferencesSync] SDK upsert succeeded for user_id=$userId (focus_points=${prefsDto.focus_points})")
        } catch (e: Exception) {
            Log.w(TAG, "[UserPreferencesSync] SDK upsert failed: ${e.message}. Attempting direct REST POST/PATCH fallback...")
        }

        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val postUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/user_preferences"
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            val json = org.json.JSONObject().apply {
                put("user_id", prefsDto.user_id)
                put("theme_mode", prefsDto.theme_mode)
                put("daily_goal_minutes", prefsDto.daily_goal_minutes)
                put("preferred_focus_mode", prefsDto.preferred_focus_mode)
                put("deep_work_duration", prefsDto.deep_work_duration)
                put("deep_work_break", prefsDto.deep_work_break)
                put("classic_duration", prefsDto.classic_duration)
                put("classic_break", prefsDto.classic_break)
                put("short_sprint_duration", prefsDto.short_sprint_duration)
                put("sound_enabled", prefsDto.sound_enabled)
                put("ambient_sound", prefsDto.ambient_sound)
                put("haptics_enabled", prefsDto.haptics_enabled)
                put("notifications_enabled", prefsDto.notifications_enabled)
                put("auto_start_break", prefsDto.auto_start_break)
                put("auto_start_focus", prefsDto.auto_start_focus)
                put("week_starts_on", prefsDto.week_starts_on)
                put("has_completed_onboarding", prefsDto.has_completed_onboarding)
                put("user_level", prefsDto.user_level)
                put("focus_points", prefsDto.focus_points)
                put("focus_identity", prefsDto.focus_identity)
                put("current_streak", prefsDto.current_streak)
                put("best_streak", prefsDto.best_streak)
                put("alarm_ringtone", prefsDto.alarm_ringtone)
                put("is_app_blocker_enabled", prefsDto.is_app_blocker_enabled)
                put("app_blocker_duration_minutes", prefsDto.app_blocker_duration_minutes)
                put("app_blocker_end_time", prefsDto.app_blocker_end_time)
                put("blocked_apps_list", prefsDto.blocked_apps_list)
                put("shield_blocked_attempts", prefsDto.shield_blocked_attempts)
                put("allowed_education_apps", prefsDto.allowed_education_apps)
                put("is_auto_study_blocker_enabled", prefsDto.is_auto_study_blocker_enabled)
                put("penalty_block_end_time", prefsDto.penalty_block_end_time)
                put("buddy_growth_stage", prefsDto.buddy_growth_stage)
                put("buddy_total_focus_minutes", prefsDto.buddy_total_focus_minutes)
                if (!prefsDto.last_donation_prompt_shown_at.isNullOrBlank()) {
                    put("last_donation_prompt_shown_at", prefsDto.last_donation_prompt_shown_at)
                }
                put("donation_prompt_dismissed_count", prefsDto.donation_prompt_dismissed_count)
                if (!prefsDto.last_viewed_sunday_recap_week.isNullOrBlank()) {
                    put("last_viewed_sunday_recap_week", prefsDto.last_viewed_sunday_recap_week)
                }
            }

            val requestBody = json.toString().toRequestBody(mediaType)
            val request = okhttp3.Request.Builder()
                .url(postUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(requestBody)
                .build()

            val okHttpClient = okhttp3.OkHttpClient()
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            val code = response.code

            Log.i(TAG, "[UserPreferencesSync] AFTER WRITE: REST POST code=$code, body=$responseBody")
            if (code in 200..299) {
                Log.i(TAG, "[UserPreferencesSync] SUCCESS: user_preferences updated for user_id=$userId with focus_points=${prefsDto.focus_points}")
                Result.success(true)
            } else {
                Log.w(TAG, "[UserPreferencesSync] REST POST returned code $code. Trying REST PATCH fallback...")
                val patchUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/user_preferences?user_id=eq.$userId"
                val patchRequest = okhttp3.Request.Builder()
                    .url(patchUrl)
                    .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(requestBody)
                    .build()

                val patchResponse = okHttpClient.newCall(patchRequest).execute()
                val patchBody = patchResponse.body?.string().orEmpty()
                val patchCode = patchResponse.code
                Log.i(TAG, "[UserPreferencesSync] AFTER WRITE (PATCH): REST PATCH code=$patchCode, body=$patchBody")
                if (patchCode in 200..299) {
                    Log.i(TAG, "[UserPreferencesSync] SUCCESS via PATCH: user_preferences updated for user_id=$userId with focus_points=${prefsDto.focus_points}")
                    Result.success(true)
                } else {
                    Log.e(TAG, "[UserPreferencesSync] FAILED: user_preferences write failed with code $patchCode: $patchBody")
                    Result.failure(Exception("HTTP $patchCode: $patchBody"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[UserPreferencesSync] EXCEPTION: Error saving user_preferences: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(userId: String): SupabaseUserDto? = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext null
        try {
            val dto = client.from("users").select {
                filter {
                    eq("id", userId)
                }
            }.decodeSingleOrNull<SupabaseUserDto>()
            if (dto != null) {
                Log.d(TAG, "[ProfileFetch] SDK select success: surveyCompleted=${dto.has_completed_intake_survey}, avatar_url=${dto.avatar_url}")
                return@withContext dto
            }
        } catch (e: Exception) {
            Log.w(TAG, "[ProfileFetch] SDK select failed: ${e.message}, attempting REST GET fallback...")
        }

        // Direct REST GET fallback with Bearer token
        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val getUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/users?id=eq.$userId&select=*"
            val request = okhttp3.Request.Builder()
                .url(getUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val okHttpClient = okhttp3.OkHttpClient()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful && body.isNotBlank()) {
                val jsonArray = org.json.JSONArray(body)
                if (jsonArray.length() > 0) {
                    val obj = jsonArray.getJSONObject(0)
                    val parsed = SupabaseUserDto(
                        id = obj.optString("id", userId),
                        email = if (obj.has("email") && !obj.isNull("email")) obj.getString("email") else null,
                        full_name = if (obj.has("full_name") && !obj.isNull("full_name")) obj.getString("full_name") else null,
                        avatar_url = if (obj.has("avatar_url") && !obj.isNull("avatar_url")) obj.getString("avatar_url") else null,
                        primary_goal = if (obj.has("primary_goal") && !obj.isNull("primary_goal")) obj.getString("primary_goal") else null,
                        focus_style = if (obj.has("focus_style") && !obj.isNull("focus_style")) obj.getString("focus_style") else null,
                        daily_target_hours = obj.optInt("daily_target_hours", 4),
                        peak_productivity_time = if (obj.has("peak_productivity_time") && !obj.isNull("peak_productivity_time")) obj.getString("peak_productivity_time") else null,
                        primary_distraction = if (obj.has("primary_distraction") && !obj.isNull("primary_distraction")) obj.getString("primary_distraction") else null,
                        sound_preference = if (obj.has("sound_preference") && !obj.isNull("sound_preference")) obj.getString("sound_preference") else null,
                        student_age = obj.optInt("student_age", 16),
                        student_class = if (obj.has("student_class") && !obj.isNull("student_class")) obj.getString("student_class") else "Class 11",
                        student_stream = if (obj.has("student_stream") && !obj.isNull("student_stream")) obj.getString("student_stream") else "Science (PCM)",
                        study_schedule = if (obj.has("study_schedule") && !obj.isNull("study_schedule")) obj.getString("study_schedule") else "6:00 PM – 10:00 PM",
                        mobile_break_time = if (obj.has("mobile_break_time") && !obj.isNull("mobile_break_time")) obj.getString("mobile_break_time") else "8:00 PM – 8:30 PM",
                        student_class_level = if (obj.has("student_class_level") && !obj.isNull("student_class_level")) obj.getString("student_class_level") else null,
                        is_board_exam_year = if (obj.has("is_board_exam_year") && !obj.isNull("is_board_exam_year")) obj.getBoolean("is_board_exam_year") else null,
                        daily_screen_time_goal_minutes = obj.optInt("daily_screen_time_goal_minutes", 480),
                        primary_study_goal = if (obj.has("primary_study_goal") && !obj.isNull("primary_study_goal")) obj.getString("primary_study_goal") else null,
                        biggest_distraction_app = if (obj.has("biggest_distraction_app") && !obj.isNull("biggest_distraction_app")) obj.getString("biggest_distraction_app") else null,
                        preferred_study_time_window = if (obj.has("preferred_study_time_window") && !obj.isNull("preferred_study_time_window")) obj.getString("preferred_study_time_window") else null,
                        motivation_style = if (obj.has("motivation_style") && !obj.isNull("motivation_style")) obj.getString("motivation_style") else null,
                        has_completed_intake_survey = if (obj.has("has_completed_intake_survey") && !obj.isNull("has_completed_intake_survey")) obj.getBoolean("has_completed_intake_survey") else false,
                        created_at = if (obj.has("created_at") && !obj.isNull("created_at")) obj.getString("created_at") else null
                    )
                    Log.d(TAG, "[ProfileFetch] Loaded user profile via REST: surveyCompleted=${parsed.has_completed_intake_survey}, avatar_url=${parsed.avatar_url}")
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[ProfileFetch] REST fallback error: ${e.message}", e)
        }
        null
    }

    suspend fun updateUserSurveyCompleted(
        userId: String,
        isCompleted: Boolean,
        name: String? = null,
        studentClassLevel: String? = null,
        isBoardExamYear: Boolean? = null,
        primaryStudyGoal: String? = null,
        biggestDistractionApp: String? = null,
        preferredStudyTimeWindow: String? = null,
        dailyScreenTimeGoalMinutes: Int? = null,
        motivationStyle: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[SurveyPersistence] Executing UPDATE public.users SET has_completed_intake_survey = $isCompleted WHERE id = '$userId'")
            val updateJson = org.json.JSONObject().apply {
                put("has_completed_intake_survey", isCompleted)
                name?.takeIf { it.isNotBlank() }?.let { put("full_name", it) }
                studentClassLevel?.takeIf { it.isNotBlank() }?.let {
                    put("student_class_level", it)
                    put("student_class", it)
                }
                isBoardExamYear?.let { put("is_board_exam_year", it) }
                primaryStudyGoal?.takeIf { it.isNotBlank() }?.let {
                    put("primary_study_goal", it)
                    put("primary_goal", it)
                }
                biggestDistractionApp?.takeIf { it.isNotBlank() }?.let {
                    put("biggest_distraction_app", it)
                    put("primary_distraction", it)
                }
                preferredStudyTimeWindow?.takeIf { it.isNotBlank() }?.let { put("preferred_study_time_window", it) }
                dailyScreenTimeGoalMinutes?.let { put("daily_screen_time_goal_minutes", it) }
                motivationStyle?.takeIf { it.isNotBlank() }?.let { put("motivation_style", it) }
            }

            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val patchUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/users?id=eq.$userId"
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = updateJson.toString().toRequestBody(mediaType)

            val request = okhttp3.Request.Builder()
                .url(patchUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .patch(requestBody)
                .build()

            val okHttpClient = okhttp3.OkHttpClient()
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            val code = response.code
            Log.d(TAG, "[SurveyPersistence] REST PATCH response code=$code body=$responseBody")

            if (code in 200..299) {
                Log.i(TAG, "[SurveyPersistence] Successfully updated has_completed_intake_survey=$isCompleted for user $userId")
                Result.success(true)
            } else {
                Log.w(TAG, "[SurveyPersistence] PATCH failed with code $code: $responseBody. Attempting upsert fallback...")
                val userDto = SupabaseUserDto(
                    id = userId,
                    full_name = name,
                    has_completed_intake_survey = isCompleted,
                    student_class_level = studentClassLevel,
                    is_board_exam_year = isBoardExamYear,
                    primary_study_goal = primaryStudyGoal,
                    biggest_distraction_app = biggestDistractionApp,
                    preferred_study_time_window = preferredStudyTimeWindow,
                    daily_screen_time_goal_minutes = dailyScreenTimeGoalMinutes,
                    motivation_style = motivationStyle
                )
                upsertUserProfile(userDto)
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[SurveyPersistence] Error updating survey completed flag: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserAvatarUrl(userId: String, avatarUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            Log.e(TAG, "[AvatarPersistence] BEFORE UPDATE ERROR: userId is blank!")
            return@withContext Result.failure(IllegalArgumentException("userId is blank"))
        }

        Log.i(TAG, "[AvatarPersistence] BEFORE UPDATE: Executing UPSERT public.users & public.study_leaderboard SET avatar_url = '${avatarUrl.take(60)}...' WHERE id = '$userId'")
        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val okHttpClient = okhttp3.OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            // 1. Try SDK update first
            try {
                client.from("users").update(mapOf("avatar_url" to avatarUrl)) {
                    filter { eq("id", userId) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "[AvatarPersistence] SDK update users failed: ${e.message}")
            }

            // 2. Perform REST UPSERT into public.users to ensure row exists and avatar_url is persisted
            val userUpsertJson = org.json.JSONObject().apply {
                put("id", userId)
                put("avatar_url", avatarUrl)
            }
            val usersPostUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/users"
            val usersRequest = okhttp3.Request.Builder()
                .url(usersPostUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(userUpsertJson.toString().toRequestBody(mediaType))
                .build()

            val usersResponse = okHttpClient.newCall(usersRequest).execute()
            val usersBody = usersResponse.body?.string().orEmpty()
            val usersCode = usersResponse.code
            Log.i(TAG, "[AvatarPersistence] AFTER UPSERT (users): REST POST code=$usersCode, body=$usersBody")

            // 3. Perform REST UPSERT into public.study_leaderboard
            val lbUpsertJson = org.json.JSONObject().apply {
                put("user_id", userId)
                put("avatar_url", avatarUrl)
            }
            val lbPostUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/study_leaderboard"
            val lbRequest = okhttp3.Request.Builder()
                .url(lbPostUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(lbUpsertJson.toString().toRequestBody(mediaType))
                .build()

            val lbResponse = okHttpClient.newCall(lbRequest).execute()
            val lbBody = lbResponse.body?.string().orEmpty()
            Log.i(TAG, "[AvatarPersistence] AFTER UPSERT (study_leaderboard): REST POST code=${lbResponse.code}, body=$lbBody")

            Log.i(TAG, "[AvatarPersistence] SUCCESS: Successfully persisted avatar_url for user $userId to Supabase")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "[AvatarPersistence] EXCEPTION: Error updating avatar_url: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun upsertUserProfile(userDto: SupabaseUserDto): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = userDto.id
        if (userId.isBlank()) return@withContext Result.failure(IllegalArgumentException("user id is blank"))
        try {
            client.from("users").upsert(userDto)
            return@withContext Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "upsertUserProfile SDK failed: ${e.message}. Trying REST POST fallback...")
        }

        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val postUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/users"
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            val json = org.json.JSONObject().apply {
                put("id", userDto.id)
                userDto.email?.let { put("email", it) }
                userDto.full_name?.let { put("full_name", it) }
                userDto.avatar_url?.let { put("avatar_url", it) }
                userDto.primary_goal?.let { put("primary_goal", it) }
                userDto.focus_style?.let { put("focus_style", it) }
                put("daily_target_hours", userDto.daily_target_hours)
                userDto.peak_productivity_time?.let { put("peak_productivity_time", it) }
                userDto.primary_distraction?.let { put("primary_distraction", it) }
                userDto.sound_preference?.let { put("sound_preference", it) }
                put("student_age", userDto.student_age)
                put("student_class", userDto.student_class)
                put("student_stream", userDto.student_stream)
                put("study_schedule", userDto.study_schedule)
                put("mobile_break_time", userDto.mobile_break_time)
                userDto.student_class_level?.let { put("student_class_level", it) }
                userDto.is_board_exam_year?.let { put("is_board_exam_year", it) }
                userDto.daily_screen_time_goal_minutes?.let { put("daily_screen_time_goal_minutes", it) }
                userDto.primary_study_goal?.let { put("primary_study_goal", it) }
                userDto.biggest_distraction_app?.let { put("biggest_distraction_app", it) }
                userDto.preferred_study_time_window?.let { put("preferred_study_time_window", it) }
                userDto.motivation_style?.let { put("motivation_style", it) }
                put("has_completed_intake_survey", userDto.has_completed_intake_survey)
            }

            val request = okhttp3.Request.Builder()
                .url(postUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(json.toString().toRequestBody(mediaType))
                .build()

            val response = okhttp3.OkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                Log.i(TAG, "upsertUserProfile REST succeeded for $userId")
                Result.success(true)
            } else {
                Log.w(TAG, "upsertUserProfile REST failed code ${response.code}: ${response.body?.string()}")
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "upsertUserProfile error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchLeaderboard(): List<SupabaseStudyLeaderboardDto> = withContext(Dispatchers.IO) {
        try {
            val leaderboard = client.from("study_leaderboard").select().decodeList<SupabaseStudyLeaderboardDto>()
            if (leaderboard.isNotEmpty()) {
                val userIds = leaderboard.map { it.user_id }.filter { it.isNotBlank() }.distinct()
                if (userIds.isNotEmpty()) {
                    val userProfiles = try {
                        client.from("users").select {
                            filter {
                                isIn("id", userIds)
                            }
                        }.decodeList<SupabaseUserDto>()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val avatarMap = userProfiles.associate { it.id to it.avatar_url }
                    leaderboard.forEach { item ->
                        item.avatar_url = avatarMap[item.user_id]
                    }
                }
            }
            leaderboard
        } catch (e: Exception) {
            Log.w(TAG, "fetchLeaderboard error: ${e.message}")
            emptyList()
        }
    }

    suspend fun upsertLeaderboard(leaderboardDto: SupabaseStudyLeaderboardDto): Result<Boolean> = withContext(Dispatchers.IO) {
        val userId = leaderboardDto.user_id
        if (userId.isBlank()) {
            Log.e(TAG, "[LeaderboardSync] BEFORE WRITE ERROR: user_id is blank!")
            return@withContext Result.failure(IllegalArgumentException("user_id is blank"))
        }

        Log.i(TAG, "[LeaderboardSync] BEFORE WRITE: Executing UPDATE/UPSERT public.study_leaderboard SET display_name='${leaderboardDto.display_name}', study_seconds=${leaderboardDto.study_seconds}, streak=${leaderboardDto.streak} WHERE user_id='$userId'")
        try {
            client.from("study_leaderboard").upsert(leaderboardDto)
            Log.i(TAG, "[LeaderboardSync] SDK upsert succeeded for user_id=$userId")
        } catch (e: Exception) {
            Log.w(TAG, "[LeaderboardSync] SDK upsert failed: ${e.message}. Attempting REST POST fallback...")
        }

        try {
            val token = getAuthToken() ?: SupabaseManager.SUPABASE_KEY
            val postUrl = "${SupabaseManager.SUPABASE_URL}/rest/v1/study_leaderboard"
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            val json = org.json.JSONObject().apply {
                put("user_id", leaderboardDto.user_id)
                put("display_name", leaderboardDto.display_name)
                put("study_seconds", leaderboardDto.study_seconds)
                put("streak", leaderboardDto.streak)
                put("subject_tag", leaderboardDto.subject_tag)
                leaderboardDto.avatar_url?.takeIf { it.isNotBlank() }?.let { put("avatar_url", it) }
            }

            val requestBody = json.toString().toRequestBody(mediaType)
            val request = okhttp3.Request.Builder()
                .url(postUrl)
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(requestBody)
                .build()

            val okHttpClient = okhttp3.OkHttpClient()
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            val code = response.code

            Log.i(TAG, "[LeaderboardSync] AFTER WRITE: REST POST code=$code, body=$responseBody")
            if (code in 200..299) {
                Log.i(TAG, "[LeaderboardSync] SUCCESS: study_leaderboard updated for user_id=$userId (study_seconds=${leaderboardDto.study_seconds})")
                Result.success(true)
            } else {
                Log.w(TAG, "[LeaderboardSync] REST POST failed with code $code: $responseBody")
                Result.failure(Exception("HTTP $code: $responseBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[LeaderboardSync] EXCEPTION: Error saving study_leaderboard: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchHallOfFame(): List<SupabaseHallOfFameDto> = withContext(Dispatchers.IO) {
        try {
            val list = client.from("leaderboard_hall_of_fame").select().decodeList<SupabaseHallOfFameDto>()
            list.sortedByDescending { it.week_start }
        } catch (e: Exception) {
            Log.w(TAG, "fetchHallOfFame error: ${e.message}")
            emptyList()
        }
    }

    suspend fun archiveWeeklyWinner(weekStart: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Calling archive_weekly_winner for p_week_start=$weekStart")
            client.postgrest.rpc(
                function = "archive_weekly_winner",
                parameters = buildJsonObject {
                    put("p_week_start", weekStart)
                }
            )
            Log.i(TAG, "archive_weekly_winner RPC succeeded for $weekStart")
            true
        } catch (e: Exception) {
            Log.w(TAG, "archiveWeeklyWinner error: ${e.message}")
            false
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

    // --- APP DAILY LIMITS (public.app_daily_limits) ---

    suspend fun fetchAppDailyLimits(userId: String): List<SupabaseAppDailyLimitDto> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[AppLimitsLoad] Querying SELECT * FROM public.app_daily_limits WHERE user_id = '$userId'")
            val result = client.from("app_daily_limits").select {
                filter {
                    eq("user_id", userId)
                }
            }.decodeList<SupabaseAppDailyLimitDto>()
            Log.d(TAG, "[AppLimitsLoad] Loaded ${result.size} app daily limit rows for uid=$userId")
            result
        } catch (e: Exception) {
            Log.e(TAG, "[AppLimitsLoad] FAILED query for uid=$userId: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun upsertAppDailyLimit(limitDto: SupabaseAppDailyLimitDto): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[AppLimitSave] Executing upsert to public.app_daily_limits for dto=$limitDto")
            client.from("app_daily_limits").upsert(limitDto)
            Log.d(TAG, "[AppLimitSave] Supabase upsert SUCCESS for limit id=${limitDto.id}, app=${limitDto.app_package}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[AppLimitSave] Supabase upsert FAILED for id=${limitDto.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAppDailyLimit(limitId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[AppLimitDelete] Deleting limit from public.app_daily_limits id=$limitId")
            client.from("app_daily_limits").delete {
                filter {
                    eq("id", limitId)
                }
            }
            Log.d(TAG, "[AppLimitDelete] Supabase delete SUCCESS for id=$limitId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[AppLimitDelete] Supabase delete FAILED for id=$limitId: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun compressAvatarToThumbnailBase64(imageBytes: ByteArray): String {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return "data:image/jpeg;base64," + android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            val maxDim = 120
            val width = bitmap.width
            val height = bitmap.height
            val scale = Math.min(maxDim.toFloat() / width, maxDim.toFloat() / height).coerceAtMost(1.0f)
            val scaledWidth = Math.max(1, (width * scale).toInt())
            val scaledHeight = Math.max(1, (height * scale).toInt())
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            val baos = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, baos)
            val compressedBytes = baos.toByteArray()
            val base64 = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        }
    }

    suspend fun uploadAvatar(userId: String, imageBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            Log.e(TAG, "[AvatarUpload] BEFORE UPLOAD ERROR: userId is blank!")
            return@withContext Result.failure(IllegalArgumentException("userId is blank"))
        }
        Log.i(TAG, "[AvatarUpload] BEFORE UPLOAD: Uploading avatar image (${imageBytes.size} bytes) for user_id=$userId to storage bucket 'avatars'")
        try {
            val fileName = "$userId/avatar.jpg"
            val storageUrl = "${SupabaseManager.SUPABASE_URL}/storage/v1/object/avatars/$fileName"

            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val requestBody = imageBytes.toRequestBody(mediaType)
            val request = okhttp3.Request.Builder()
                .url(storageUrl)
                .addHeader("Authorization", "Bearer " + (client.auth.currentSessionOrNull()?.accessToken ?: SupabaseManager.SUPABASE_KEY))
                .addHeader("apikey", SupabaseManager.SUPABASE_KEY)
                .addHeader("x-upsert", "true")
                .post(requestBody)
                .build()

            val okHttpClient = okhttp3.OkHttpClient()
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            Log.i(TAG, "[AvatarUpload] AFTER UPLOAD: Storage response code=${response.code}, body=$responseBody")
            if (response.isSuccessful || response.code == 200 || response.code == 201) {
                val publicUrl = "${SupabaseManager.SUPABASE_URL}/storage/v1/object/public/avatars/$fileName"
                Log.i(TAG, "[AvatarUpload] SUCCESS: Avatar uploaded to storage public URL: $publicUrl")
                Result.success(publicUrl)
            } else {
                Log.w(TAG, "[AvatarUpload] Storage upload returned HTTP ${response.code}: $responseBody. Note: Storage bucket 'avatars' may be missing or restricted. Using compressed thumbnail Base64 fallback.")
                val dataUrl = compressAvatarToThumbnailBase64(imageBytes)
                Result.success(dataUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[AvatarUpload] EXCEPTION: Error uploading avatar to storage: ${e.message}", e)
            val dataUrl = compressAvatarToThumbnailBase64(imageBytes)
            Result.success(dataUrl)
        }
    }

    suspend fun uploadAvatarImage(userId: String, imageBytes: ByteArray): String? {
        return uploadAvatar(userId, imageBytes).getOrNull()
    }
}

@kotlinx.serialization.Serializable
data class SupabaseAppDailyLimitDto(
    val id: String = java.util.UUID.randomUUID().toString(),
    val user_id: String? = null,
    val app_package: String,
    val app_name: String,
    val daily_limit_minutes: Int = 60,
    val emergency_uses_allowed: Int = 2,
    val emergency_uses_remaining_today: Int = 2,
    val show_reminders: Boolean = true,
    val strict_mode_enabled: Boolean = false,
    val minutes_used_today: Int = 0,
    val last_reset_date: String? = null,
    val is_enabled: Boolean = true
)

