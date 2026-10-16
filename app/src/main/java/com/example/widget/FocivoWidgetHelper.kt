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

    private val ZEN_STUDY_TIPS = listOf(
        "\"Take a deep breath. 25 minutes of deep focus is better than 4 hours of distracted study.\"",
        "\"Clear your desk, take a sip of water, and conquer your next 25-minute study target.\"",
        "\"When your mind feels scattered, 5 minutes of box breathing restores laser clarity.\"",
        "\"Break your hardest subject into 20-minute chunks. You got this!\"",
        "\"Your mind is a muscle: the more you resist distractions, the stronger it grows.\""
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

            // 2. Today's Schedule Widget
            val scheduleIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TodayScheduleWidgetProvider::class.java)
            )
            for (id in scheduleIds) {
                updateScheduleWidget(context, appWidgetManager, id)
            }

            // 3. Focus Shield Widget
            val shieldIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, FocusShieldWidgetProvider::class.java)
            )
            for (id in shieldIds) {
                updateShieldWidget(context, appWidgetManager, id)
            }

            // 4. Zen Study Coach Widget
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

    // --- 1. Study Timer Widget (v1.1 pack: widget_focus_timer) ---
    fun updateTimerWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_focus_timer)

        val timerPrefs = context.getSharedPreferences("focusly_timer_state", Context.MODE_PRIVATE)
        val isRunning = timerPrefs.getBoolean("is_timer_running", false)
        val remainingSecs = timerPrefs.getInt("remaining_seconds", 25 * 60)
        val currentTask = timerPrefs.getString("current_task_title", "Deep Work Session") ?: "Deep Work Session"

        val minutes = remainingSecs / 60
        val seconds = remainingSecs % 60
        val timeDisplay = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        views.setTextViewText(R.id.tv_title, if (isRunning) timeDisplay else "25:00")
        views.setTextViewText(
            R.id.tv_sub,
            if (isRunning) "Focus: $currentTask" else "Deep work session"
        )
        views.setTextViewText(
            R.id.tv_badge,
            if (isRunning) "Focusing" else "Ready"
        )
        views.setTextViewText(
            R.id.btn_primary,
            if (isRunning) "Focus" else "Start"
        )

        val start25Intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_25M
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_QUICK_START_MINS", 25)
            putExtra("EXTRA_START_TIMER_IMMEDIATELY", true)
            putExtra("EXTRA_NAV_TAB", "FOCUS")
        }
        val p25 = PendingIntent.getActivity(
            context, 101, start25Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_25, p25)

        val start50Intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_50M
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_QUICK_START_MINS", 50)
            putExtra("EXTRA_START_TIMER_IMMEDIATELY", true)
            putExtra("EXTRA_NAV_TAB", "FOCUS")
        }
        val p50 = PendingIntent.getActivity(
            context, 102, start50Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_50, p50)

        val openTimerIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TIMER
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_TAB", "FOCUS")
            if (!isRunning) {
                putExtra("EXTRA_START_TIMER_IMMEDIATELY", true)
                putExtra("EXTRA_QUICK_START_MINS", 25)
            }
        }
        val pOpen = PendingIntent.getActivity(
            context, 103, openTimerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_primary, pOpen)
        views.setOnClickPendingIntent(R.id.fw_root, pOpen)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // --- 2. Today's Schedule & Targets Widget (v1.1 pack: widget_timetable) ---
    fun updateScheduleWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_timetable)

        val todayDateStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
        views.setTextViewText(R.id.tv_eyebrow, "Today · $todayDateStr")

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
                R.id.tv_title,
                topPriorityTitle
            )
            views.setTextViewText(
                R.id.tv_sub,
                "Next target · $topPriorityDuration min · $pendingCount task${if (pendingCount > 1) "s" else ""} remaining"
            )
        } else {
            views.setTextViewText(R.id.tv_title, "Great work today.")
            views.setTextViewText(R.id.tv_sub, "All targets completed. Plan new study blocks?")
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
        views.setOnClickPendingIntent(R.id.btn_secondary, pSchedule)

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
        views.setOnClickPendingIntent(R.id.btn_primary, pStart)
        views.setOnClickPendingIntent(R.id.fw_root, pSchedule)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // --- 3. Focus Shield & Deep Lock Widget (v1.1 pack: widget_focus_shield) ---
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
            R.id.tv_badge,
            if (isShieldActive) "Armed" else "Ready"
        )
        views.setTextViewText(
            R.id.tv_title,
            "$blockedCount app${if (blockedCount != 1) "s" else ""} blocked"
        )
        views.setTextViewText(
            R.id.tv_sub,
            if (isDeepFocusOn) "Deep Focus active · Shorts and Reels locked" else "Shorts & Reels auto-intercept is on"
        )
        views.setTextViewText(
            R.id.btn_primary,
            if (isShieldActive) "Armed" else "Arm Shield"
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
        views.setOnClickPendingIntent(R.id.btn_secondary, pHub)
        views.setOnClickPendingIntent(R.id.btn_primary, pHub)
        views.setOnClickPendingIntent(R.id.fw_root, pHub)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // --- 4. AI Study Coach & Zen Break Widget (v1.1 pack: widget_zen_coach) ---
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
            R.id.tv_sub,
            "Level $level Student · $focusPoints Focus XP"
        )
        // Pack pattern: serif tip in quotes.
        views.setTextViewText(R.id.tv_title, "\u201C$tip\u201D")

        val zenIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ZEN_BREAK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_ZEN_BREAK", true)
        }
        val pZen = PendingIntent.getActivity(
            context, 501, zenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_primary, pZen)

        // v1.2.3 (streak removal): the "AI Coach" secondary button and the
        // widget root previously pointed at the deleted streak widget. They
        // now open the app home — a widget button must never be dead.
        val homeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pHome = PendingIntent.getActivity(
            context, 502, homeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_secondary, pHome)
        views.setOnClickPendingIntent(R.id.fw_root, pHome)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
