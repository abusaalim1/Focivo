package com.example.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SupabaseUserDto(
    val id: String,
    val email: String? = null,
    val full_name: String? = null,
    val avatar_url: String? = null,
    val primary_goal: String? = null,
    val focus_style: String? = null,
    val daily_target_hours: Int? = 4,
    val peak_productivity_time: String? = null,
    val primary_distraction: String? = null,
    val sound_preference: String? = null,
    val student_age: Int? = 16,
    val student_class: String? = "Class 11",
    val student_stream: String? = "Science (PCM)",
    val study_schedule: String? = "6:00 PM – 10:00 PM",
    val mobile_break_time: String? = "8:00 PM – 8:30 PM",
    val student_class_level: String? = null,
    val is_board_exam_year: Boolean? = null,
    val daily_screen_time_goal_minutes: Int? = 480,
    val primary_study_goal: String? = null,
    val biggest_distraction_app: String? = null,
    val preferred_study_time_window: String? = null,
    val motivation_style: String? = null,
    val has_completed_intake_survey: Boolean? = false,
    val created_at: String? = null
)

@Serializable
data class SupabaseAlarmDto(
    val id: String = java.util.UUID.randomUUID().toString(),
    val user_id: String,
    val label: String = "Focus Alarm",
    val hour: Int = 8,
    val minute: Int = 0,
    val days_active: String = "Mon,Tue,Wed,Thu,Fri",
    val ringtone: String = "Zen Bell",
    val is_enabled: Boolean = true,
    val vibrate: Boolean = true,
    val created_at: String? = null
)

@Serializable
data class SupabaseTaskDto(
    val id: String = java.util.UUID.randomUUID().toString(),
    val user_id: String,
    val title: String = "",
    val category: String = "Deep Work",
    val priority: Int = 1,
    val duration_minutes: Int = 25,
    val scheduled_time: String = "09:00",
    val is_completed: Boolean = false,
    val is_top_priority: Boolean = false,
    val notes: String = "",
    val date: String = "",
    val created_at: String? = null
)

@Serializable
data class SupabaseFocusSessionDto(
    val id: String = java.util.UUID.randomUUID().toString(),
    val user_id: String,
    val task_title: String = "",
    val duration_seconds: Int = 0,
    val target_duration_seconds: Int = 0,
    val mode: String = "Deep Work",
    val distractions_count: Int = 0,
    val distraction_types: String = "",
    val focus_points_earned: Int = 12,
    val completed_at: Long? = System.currentTimeMillis(),
    val day_of_week: Int = 1,
    val hour_of_day: Int = 10,
    val notes: String = ""
)

@Serializable
data class SupabaseReflectionDto(
    val id: String = java.util.UUID.randomUUID().toString(),
    val user_id: String,
    val week_label: String = "",
    val total_minutes_focused: Int = 0,
    val sessions_completed: Int = 0,
    val best_day: String = "Wednesday",
    val completion_rate: Int = 80,
    val reflection_text: String = "",
    val created_at: String? = null
)

@Serializable
data class SupabaseUserPreferencesDto(
    val user_id: String,
    val theme_mode: String = "system",
    val daily_goal_minutes: Int = 360,
    val preferred_focus_mode: String = "Deep Work",
    val deep_work_duration: Int = 50,
    val deep_work_break: Int = 10,
    val classic_duration: Int = 25,
    val classic_break: Int = 5,
    val short_sprint_duration: Int = 15,
    val sound_enabled: Boolean = true,
    val ambient_sound: String = "Silent",
    val haptics_enabled: Boolean = true,
    val notifications_enabled: Boolean = true,
    val auto_start_break: Boolean = false,
    val auto_start_focus: Boolean = false,
    val week_starts_on: String = "Monday",
    val has_completed_onboarding: Boolean = false,
    val user_level: Int = 1,
    val focus_points: Int = 0,
    val focus_identity: String = "Novice Deep Worker",
    val current_streak: Int = 0,
    val best_streak: Int = 0,
    val alarm_ringtone: String = "Zen Bell",
    val is_app_blocker_enabled: Boolean = false,
    val app_blocker_duration_minutes: Int = 25,
    val app_blocker_end_time: Long = 0L,
    val blocked_apps_list: String = "com.instagram.android,com.zhiliaoapp.musically,com.twitter.android,com.facebook.katana,com.snapchat.android,com.reddit.frontpage",
    val shield_blocked_attempts: Int = 0,
    val allowed_education_apps: String = "com.google.android.youtube,com.openai.chatgpt,com.anthropic.claude",
    val is_auto_study_blocker_enabled: Boolean = true,
    val penalty_block_end_time: Long = 0L
)

@Serializable
data class SupabaseStudyLeaderboardDto(
    val user_id: String,
    val display_name: String = "Student",
    val study_seconds: Long = 0L,
    val streak: Int = 1,
    val subject_tag: String = "General Study",
    val last_updated: String? = null
)

@Serializable
data class SupabaseScheduledBlockDto(
    val id: String = java.util.UUID.randomUUID().toString(),
    val user_id: String,
    val label: String = "Study Block",
    val start_time: String = "18:00",
    val end_time: String = "22:00",
    val break_start_time: String? = null,
    val break_end_time: String? = null,
    val days_active: String = "Mon,Tue,Wed,Thu,Fri",
    val is_enabled: Boolean = true,
    val blocked_apps_list: String = "",
    val is_strict_mode: Boolean = true,
    val created_at: String? = null
)

@Serializable
data class SupabasePunishmentLogDto(
    val id: String = java.util.UUID.randomUUID().toString(),
    val user_id: String,
    val app_package: String,
    val status: String,
    val reason: String,
    val warned_at: String? = null,
    val blocked_at: String? = null,
    val block_duration_minutes: Int? = null,
    val created_at: String? = null
)

object SupabaseManager {
    private const val TAG = "SupabaseManager"
    const val SUPABASE_URL = "https://dgjseiatlqimjsdhmhxn.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_BK36wT7zhjbbmo1Ok87eQQ_Rf5uBxY3"

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun getContext(): Context? {
        return appContext ?: runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()
    }

    fun getApplicationContext(): Context? = getContext()

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Auth) {
                scheme = "studytracker"
                host = "auth-callback"
                getContext()?.let { ctx ->
                    sessionManager = AndroidPreferenceSessionManager(ctx)
                }
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest) {
                serializer = KotlinXSerializer(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            install(Realtime)
        }
    }
}
