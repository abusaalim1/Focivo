package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.service.FocusShieldService
import com.example.util.DeepFocusManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FocivoWidgetHelper {

    private const val TAG = "FocivoWidgetHelper"

    const val ACTION_START_25M = "com.example.widget.ACTION_START_25M"
    const val ACTION_START_50M = "com.example.widget.ACTION_START_50M"
    const val ACTION_OPEN_TIMER = "com.example.widget.ACTION_OPEN_TIMER"
    const val ACTION_OPEN_SCHEDULE = "com.example.widget.ACTION_OPEN_SCHEDULE"
    const val ACTION_OPEN_SHIELD_HUB = "com.example.widget.ACTION_OPEN_SHIELD_HUB"
    const val ACTION_TOGGLE_SHIELD = "com.example.widget.ACTION_TOGGLE_SHIELD"
    const val ACTION_OPEN_ZEN_BREAK = "com.example.widget.ACTION_OPEN_ZEN_BREAK"
    const val ACTION_OPEN_STREAK = "com.example.widget.ACTION_OPEN_STREAK"

    private val MOTIVATIONAL_QUOTES = listOf(
        "\"Every page studied today is a step towards your dream ✨\"",
        "\"Stay consistent. Small daily focus turns into giant victories 🚀\"",
        "\"Your future self will thank you for studying right now 🌟\"",
        "\"Deep focus beats shallow hustle every single day 🎯\"",
        "\"Discipline is choosing between what you want now and what you want most 🔥\"",
        "\"One chapter at a time. You are closer than you think 📚\"",
        "\"Focus on the process, and the rank will follow automatically 💡\""
    )

    private val ZEN_STUDY_TIPS = listOf(
        "\"Take a deep breath. 25 minutes of deep focus is better than 4 hours of distracted study 🧘\"",
        "\"Clear your desk, take a sip of water, and conquer your next 25-minute study target 💧\"",
        "\"When your mind feels scattered, 5 minutes of box breathing restores laser clarity 🌿\"",
        "\"Break your hardest subject into 20-minute chunks. You got this! 🎯\"",
        "\"Your mind is a muscle: the more you resist distractions, the stronger it grows 🧠\""
    )

    fun updateAllWidgets(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // 1. Timer Widget
            val timerIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, StudyTimerWidgetProvider::class.java)
            )
            for (id in timerIds) {
                updateTimerWidget(context, appWidgetManager, id)
            }

            // 2. Streak & Motivation Widget
            val streakIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, StreakMotivationWidgetProvider::class.java)
            )
            for (id in streakIds) {
                updateStreakWidget(context, appWidgetManager, id)
            }

            // 3. Today's Schedule Widget
            val scheduleIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TodayScheduleWidgetProvider::class.java)
            )
            for (id in scheduleIds) {
                updateScheduleWidget(context, appWidgetManager, id)
            }

            // 4. Focus Shield Widget
            val shieldIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, FocusShieldWidgetProvider::class.java)
            )
            for (id in shieldIds) {
                updateShieldWidget(context, appWidgetManager, id)
            }

            // 5. Zen Study Coach Widget
            val zenIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ZenStudyCoachWidgetProvider::class.java)
            )
            for (id in zenIds) {
                updateZenWidget(context, appWidgetManager, id)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error updating widgets: ${e.message}", e)
        }
    }

    // --- 1. Study Timer Widget ---
    fun updateTimerWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_study_timer)

        val timerPrefs = context.getSharedPreferences("focusly_timer_state", Context.MODE_PRIVATE)
        val isRunning = timerPrefs.getBoolean("is_timer_running", false)
        val remainingSecs = timerPrefs.getInt("remaining_seconds", 25 * 60)
        val currentTask = timerPrefs.getString("current_task_title", "Ready for Study Session") ?: "Ready for Study Session"

        val minutes = remainingSecs / 60
        val seconds = remainingSecs % 60
        val timeDisplay = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        views.setTextViewText(R.id.widget_timer_clock, if (isRunning) timeDisplay else "25:00")
        views.setTextViewText(
            R.id.widget_timer_task_title,
            if (isRunning) "Studying: $currentTask" else "Ready for Study Session"
        )
        views.setTextViewText(
            R.id.widget_timer_status_badge,
            if (isRunning) "STUDYING" else "READY"
        )
        views.setTextViewText(
            R.id.widget_timer_subtitle,
            if (isRunning) "Deep focus session in progress · Stay on track 🎯" else "Tap 25m or 50m to start instant study ⚡"
        )

        val start25Intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_25M
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_QUICK_START_MINS", 25)
            putExtra("EXTRA_NAV_TAB", "FOCUS")
        }
        val p25 = PendingIntent.getActivity(
            context, 101, start25Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_quick_25, p25)

        val start50Intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_50M
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_QUICK_START_MINS", 50)
            putExtra("EXTRA_NAV_TAB", "FOCUS")
        }
        val p50 = PendingIntent.getActivity(
            context, 102, start50Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_quick_50, p50)

        val openTimerIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TIMER
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_TAB", "FOCUS")
            if (!isRunning) {
                putExtra("EXTRA_START_TIMER_IMMEDIATELY", true)
            }
        }
        val pOpen = PendingIntent.getActivity(
            context, 103, openTimerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_open_timer, pOpen)
        views.setOnClickPendingIntent(R.id.widget_timer_root, pOpen)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // --- 2. Daily Streak & Motivation Widget ---
    fun updateStreakWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_streak_motivation)

        val localPrefs = context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
        val userPrefsJson = localPrefs.getString("user_preferences", null)
        var streak = 1
        var level = 1
        var focusPoints = 0
        var dailyGoal = 90

        if (userPrefsJson != null) {
            try {
                val obj = JSONObject(userPrefsJson)
                streak = obj.optInt("currentStreak", 1).coerceAtLeast(1)
                level = obj.optInt("userLevel", obj.optInt("currentLevel", 1)).coerceAtLeast(1)
                focusPoints = obj.optInt("focusPoints", 0)
                dailyGoal = obj.optInt("dailyGoalMinutes", 90).coerceAtLeast(30)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing prefs for streak widget: ${e.message}")
            }
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        var todayMinutes = 0
        try {
            val sessionsJson = localPrefs.getString("focus_sessions", "[]") ?: "[]"
            val array = JSONArray(sessionsJson)
            for (i in 0 until array.length()) {
                val session = array.getJSONObject(i)
                val date = session.optString("date", "")
                if (date == todayStr || date.startsWith(todayStr)) {
                    todayMinutes += session.optInt("durationMinutes", 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating today's minutes: ${e.message}")
        }

        val progress = ((todayMinutes.toFloat() / dailyGoal.toFloat()) * 100).toInt().coerceIn(0, 100)
        val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        val quote = MOTIVATIONAL_QUOTES[dayOfYear % MOTIVATIONAL_QUOTES.size]

        views.setTextViewText(
            R.id.widget_streak_title,
            "🔥 $streak DAY${if (streak > 1) "S" else ""} STREAK"
        )
        views.setTextViewText(
            R.id.widget_streak_level,
            "Level $level Student · $focusPoints XP"
        )
        views.setTextViewText(R.id.widget_streak_quote, quote)
        views.setTextViewText(
            R.id.widget_streak_goal_text,
            "${todayMinutes}m / ${dailyGoal}m ($progress%)"
        )
        views.setProgressBar(R.id.widget_streak_progress_bar, 100, progress, false)

        val streakIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_STREAK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_TAB", "PROFILE")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 201, streakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_streak_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // --- 3. Today's Schedule & Targets Widget ---
    fun updateScheduleWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_today_schedule)

        val todayDateStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
        views.setTextViewText(R.id.widget_schedule_date, todayDateStr)

        val localPrefs = context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
        var topPriorityTitle = ""
        var topPriorityDuration = 25
        var topPriorityCategory = "Deep Work"
        var pendingCount = 0

        try {
            val tasksJson = localPrefs.getString("tasks", "[]") ?: "[]"
            val array = JSONArray(tasksJson)
            for (i in 0 until array.length()) {
                val task = array.getJSONObject(i)
                val isCompleted = task.optBoolean("isCompleted", false)
                if (!isCompleted) {
                    pendingCount++
                    val isTop = task.optBoolean("isTopPriority", false)
                    if (topPriorityTitle.isEmpty() || isTop) {
                        topPriorityTitle = task.optString("title", "Study Block")
                        topPriorityDuration = task.optInt("durationMinutes", 25)
                        topPriorityCategory = task.optString("category", "Deep Work")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tasks for schedule widget: ${e.message}")
        }

        if (topPriorityTitle.isNotEmpty()) {
            views.setTextViewText(
                R.id.widget_schedule_next_time,
                "🎯 TARGET (${topPriorityDuration}m · $topPriorityCategory)"
            )
            views.setTextViewText(
                R.id.widget_schedule_next_title,
                topPriorityTitle
            )
            views.setTextViewText(
                R.id.widget_schedule_pending_count,
                "📖 $pendingCount task${if (pendingCount > 1) "s" else ""} remaining for today"
            )
        } else {
            views.setTextViewText(R.id.widget_schedule_next_time, "🎯 ALL TARGETS COMPLETED")
            views.setTextViewText(R.id.widget_schedule_next_title, "Great work today! 🌟")
            views.setTextViewText(R.id.widget_schedule_pending_count, "Tap to plan new study blocks")
        }

        val openScheduleIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_SCHEDULE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_TAB", "HOME")
        }
        val pSchedule = PendingIntent.getActivity(
            context, 301, openScheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_open_schedule, pSchedule)

        val startTargetIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_25M
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_TAB", "FOCUS")
            if (topPriorityTitle.isNotEmpty()) {
                putExtra("EXTRA_TASK_TITLE", topPriorityTitle)
                putExtra("EXTRA_QUICK_START_MINS", topPriorityDuration)
            } else {
                putExtra("EXTRA_QUICK_START_MINS", 25)
            }
        }
        val pStart = PendingIntent.getActivity(
            context, 302, startTargetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_start_scheduled_block, pStart)
        views.setOnClickPendingIntent(R.id.widget_schedule_root, pSchedule)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // --- 4. Focus Shield & Deep Lock Widget ---
    fun updateShieldWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_focus_shield)

        val isShieldActive = FocusShieldService.isShieldActive.value
        val isDeepFocusOn = DeepFocusManager.isDeepFocusEnabled(context)

        val localPrefs = context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
        val userPrefsJson = localPrefs.getString("user_preferences", null)
        var blockedCount = 0
        if (userPrefsJson != null) {
            try {
                val obj = JSONObject(userPrefsJson)
                val blockedStr = obj.optString("blockedAppsList", "")
                if (blockedStr.isNotBlank()) {
                    blockedCount = blockedStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.size
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error counting blocked apps: ${e.message}")
            }
        }

        views.setTextViewText(
            R.id.widget_shield_status_badge,
            if (isShieldActive) "ARMED" else "READY"
        )
        views.setTextViewText(
            R.id.widget_shield_blocked_count,
            "🚫 $blockedCount Distracting App${if (blockedCount != 1) "s" else ""} Blocked"
        )
        views.setTextViewText(
            R.id.widget_shield_subtext,
            if (isDeepFocusOn) "🔒 Deep Focus Active · Shorts & Reels Locked" else "⚡ Shorts & Reels auto-intercept active"
        )
        views.setTextViewText(
            R.id.widget_btn_toggle_shield,
            if (isShieldActive) "🛡️ Armed" else "🛡️ Arm Shield"
        )

        val hubIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_SHIELD_HUB
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_SHIELD_HUB", true)
        }
        val pHub = PendingIntent.getActivity(
            context, 401, hubIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_open_shield_hub, pHub)
        views.setOnClickPendingIntent(R.id.widget_btn_toggle_shield, pHub)
        views.setOnClickPendingIntent(R.id.widget_shield_root, pHub)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // --- 5. AI Study Coach & Zen Break Widget ---
    fun updateZenWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_zen_coach)

        val localPrefs = context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
        val userPrefsJson = localPrefs.getString("user_preferences", null)
        var level = 1
        var focusPoints = 0

        if (userPrefsJson != null) {
            try {
                val obj = JSONObject(userPrefsJson)
                level = obj.optInt("userLevel", obj.optInt("currentLevel", 1)).coerceAtLeast(1)
                focusPoints = obj.optInt("focusPoints", 0)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing prefs for zen widget: ${e.message}")
            }
        }

        val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        val tip = ZEN_STUDY_TIPS[dayOfYear % ZEN_STUDY_TIPS.size]

        views.setTextViewText(
            R.id.widget_zen_user_points,
            "⭐ Level $level Student · 🪙 $focusPoints Focus XP"
        )
        views.setTextViewText(R.id.widget_zen_tip_text, tip)

        val zenIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ZEN_BREAK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_ZEN_BREAK", true)
        }
        val pZen = PendingIntent.getActivity(
            context, 501, zenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_zen_break, pZen)

        val coachIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_STREAK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_TAB", "PROFILE")
        }
        val pCoach = PendingIntent.getActivity(
            context, 502, coachIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_open_companion, pCoach)
        views.setOnClickPendingIntent(R.id.widget_zen_root, pCoach)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
