package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.util.PermissionUtils
import kotlinx.coroutines.flow.first

/**
 * v1.4.3: one central helper for all Focivo notifications, in the cute/minimal
 * house style the owner approved:
 * - app mascot as the large icon, lime accent, short one-line English text
 * - a proper notification channel per category
 * - per-type dedupe persisted in SharedPreferences
 * - every entry point silently skips on missing permission or any error
 *
 * Notification types and triggers (see report):
 *  1. Guard status      — while a study session is running (AiStudyGuardManager)
 *  2. Schedule reminder — 10 min before a scheduled study block (ScheduledBlockScheduler)
 *  3. Session complete  — onTimerCompleted, real minutes + points
 *  4. Achievement       — onTimerCompleted, diffed against notified set
 *  5. Theme unlocked    — onTimerCompleted, points crossing a theme price
 *  6. Update available  — checkForAppUpdates, once per version
 *  7. Break over        — onTimerCompleted when mode is a break
 *  8. Held digest       — onTimerCompleted, only if guard held >= 1 notification
 *  9. Sunday recap      — WeeklyRecapNotificationHelper (restyled here too)
 */
object FocivoNotifications {

    private const val TAG = "FocivoNotifications"
    private const val PREFS_DEDUPE = "focivo_notif_dedupe"
    private const val PREFS_STATE = "focivo_notif_state"

    private const val CHANNEL_SESSION = "focivo_session_channel"
    private const val CHANNEL_MOTIVATION = "focivo_motivation_channel"
    private const val CHANNEL_ACHIEVEMENTS = "focivo_achievements_channel"
    private const val CHANNEL_THEMES = "focivo_themes_channel"
    private const val CHANNEL_UPDATES = "focivo_updates_channel"
    private const val CHANNEL_SCHEDULE = "focivo_schedule_channel"
    private const val CHANNEL_RECAP = "focivo_recap_channel"
    private const val CHANNEL_STRICT_GUARD = "focivo_strict_guard_channel"

    private const val ID_SESSION_COMPLETE = 9101
    private const val ID_BREAK_OVER = 9102
    private const val ID_ACHIEVEMENT_BASE = 9110
    private const val ID_THEME_BASE = 9130
    private const val ID_UPDATE_AVAILABLE = 9150
    private const val ID_SCHEDULE_REMINDER = 9152
    private const val ID_COMEBACK = 9153
    private const val ID_SUNDAY_RECAP = 9154
    private const val ID_STRICT_UNINSTALL_ALERT = 9160

    private const val LIME_ACCENT = 0xFF8CE000

    // ------------------------------------------------------------------ internals

    private fun canNotify(context: Context): Boolean {
        return try {
            PermissionUtils.hasNotificationPermission(context)
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureChannel(
        context: Context,
        id: String,
        name: String,
        importance: Int,
        description: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(id, name, importance).apply {
                this.description = description
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        } catch (_: Exception) { }
    }

    private fun mascotIcon(context: Context): android.graphics.Bitmap? {
        return try {
            BitmapFactory.decodeResource(context.resources, R.drawable.mascot_study_happy)
        } catch (_: Exception) {
            null
        }
    }

    private fun contentIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    private fun post(
        context: Context,
        channelId: String,
        channelName: String,
        channelDesc: String,
        importance: Int,
        notificationId: Int,
        title: String,
        text: String,
        autoCancel: Boolean = true,
        priority: Int = NotificationCompat.PRIORITY_HIGH
    ) {
        try {
            if (!canNotify(context)) return
            ensureChannel(context, channelId, channelName, importance, channelDesc)
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setColor(LIME_ACCENT.toInt())
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent(context, notificationId))
                .setAutoCancel(autoCancel)
                .setPriority(priority)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            mascotIcon(context)?.let { builder.setLargeIcon(it) }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            manager.notify(notificationId, builder.build())
            Log.i(TAG, "Posted notification: $title")
        } catch (e: Exception) {
            Log.e(TAG, "post failed: ${e.message}")
        }
    }

    private fun isDeduped(context: Context, key: String): Boolean {
        return try {
            context.getSharedPreferences(PREFS_DEDUPE, Context.MODE_PRIVATE).getBoolean(key, false)
        } catch (_: Exception) {
            true // fail closed: never spam on error
        }
    }

    private fun markDeduped(context: Context, key: String) {
        try {
            context.getSharedPreferences(PREFS_DEDUPE, Context.MODE_PRIVATE)
                .edit().putBoolean(key, true).apply()
        } catch (_: Exception) { }
    }

    // ------------------------------------------------------------------ types

    /**
     * v1.3.3 (item 6b) — high-priority tamper alert. Posted when
     * StrictTamperGuard intercepts an uninstall or device-admin
     * deactivation attempt during a PROTECTED strict session (in addition
     * to the back-press + overlay). Dedicated channel so these stay
     * separable from session/motivation noise; tapping returns to the app
     * (MainActivity) via the shared content intent. Auto-cancelled when
     * the session ends (StrictSessionStore.disarm).
     */
    fun postStrictUninstallAlert(context: Context, title: String, text: String) {
        post(
            context,
            CHANNEL_STRICT_GUARD, "Strict Guard", "Strict Mode uninstall and admin tamper alerts",
            NotificationManager.IMPORTANCE_HIGH,
            ID_STRICT_UNINSTALL_ALERT,
            title,
            text,
            autoCancel = true,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    /** v1.3.3 (item 6b) — clears the tamper alert (session end / expiry). */
    fun cancelStrictUninstallAlert(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            manager.cancel(ID_STRICT_UNINSTALL_ALERT)
        } catch (_: Exception) { }
    }

    /** 3. Session complete — real minutes and real Focus Points. */
    fun postSessionComplete(context: Context, minutes: Int, points: Int) {
        post(
            context,
            CHANNEL_SESSION, "Focus Sessions", "Session complete and break notifications",
            NotificationManager.IMPORTANCE_HIGH,
            ID_SESSION_COMPLETE,
            "Session complete",
            "You focused $minutes minutes \u00b7 +$points Focus Points"
        )
    }

    /** 8. Pomodoro break finished. */
    fun postBreakOver(context: Context) {
        post(
            context,
            CHANNEL_SESSION, "Focus Sessions", "Session complete and break notifications",
            NotificationManager.IMPORTANCE_HIGH,
            ID_BREAK_OVER,
            "Break's over",
            "Ready for the next round?"
        )
    }

    /** 5. Achievement unlocked — once per achievement id. */
    fun postAchievementUnlocked(context: Context, achievementId: String, achievementName: String) {
        val key = "achievement_$achievementId"
        if (isDeduped(context, key)) return
        markDeduped(context, key)
        post(
            context,
            CHANNEL_ACHIEVEMENTS, "Achievements", "Achievement unlocks",
            NotificationManager.IMPORTANCE_HIGH,
            ID_ACHIEVEMENT_BASE + (achievementId.hashCode() and 0x0F),
            "Achievement unlocked",
            achievementName
        )
    }

    /**
     * Silently marks achievements as already-notified (first run after the
     * feature ships, so long-time users don't get spammed for old unlocks).
     */
    fun seedAchievementNotified(context: Context, achievementId: String) {
        markDeduped(context, "achievement_$achievementId")
    }

    fun isAchievementSeeded(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREFS_DEDUPE, Context.MODE_PRIVATE)
                .getBoolean("ach_seed_done", false)
        } catch (_: Exception) { true }
    }

    fun markAchievementSeeded(context: Context) {
        try {
            context.getSharedPreferences(PREFS_DEDUPE, Context.MODE_PRIVATE)
                .edit().putBoolean("ach_seed_done", true).apply()
        } catch (_: Exception) { }
    }

    /** 6. Theme unlocked — once per theme. */
    fun postThemeUnlocked(context: Context, themeId: String, themeName: String) {
        val key = "theme_$themeId"
        if (isDeduped(context, key)) return
        markDeduped(context, key)
        post(
            context,
            CHANNEL_THEMES, "Themes", "Theme unlocks",
            NotificationManager.IMPORTANCE_DEFAULT,
            ID_THEME_BASE + (themeId.hashCode() and 0x0F),
            "New theme unlocked",
            "$themeName is yours now \u2014 try it from Timer Themes."
        )
    }

    /** Silently marks themes as already-notified (same first-run seeding). */
    fun seedThemeNotified(context: Context, themeId: String) {
        markDeduped(context, "theme_$themeId")
    }

    fun isThemeSeeded(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREFS_DEDUPE, Context.MODE_PRIVATE)
                .getBoolean("theme_seed_done", false)
        } catch (_: Exception) { true }
    }

    fun markThemeSeeded(context: Context) {
        try {
            context.getSharedPreferences(PREFS_DEDUPE, Context.MODE_PRIVATE)
                .edit().putBoolean("theme_seed_done", true).apply()
        } catch (_: Exception) { }
    }

    /**
     * 7. Update available — AT MOST ONE notification per version, ever.
     * v1.3.2: the gate lives in AppUpdateManager as a persisted
     * last-notified versionCode, shared by every check path (app-open check +
     * scheduled daily check). Respects the in-app overlay dismissal.
     * v1.3.4: FORCE updates additionally re-notify at most once per 24h
     * until the user updates or a newer version replaces this one.
     * Stable notification id [ID_UPDATE_AVAILABLE] keeps the shade clean.
     */
    fun postUpdateAvailable(
        context: Context,
        versionName: String,
        versionCode: Int,
        isForceUpdate: Boolean = false
    ) {
        // Dedupe with the stored overlay dismissal for the same version.
        try {
            if (com.example.util.AppUpdateManager.isUpdateOverlayDismissed(context, versionCode)) return
        } catch (_: Exception) { }
        // Central gate: one notification per versionCode, across all paths.
        if (com.example.util.AppUpdateManager.claimNotifyForVersion(context, versionCode)) {
            if (isForceUpdate) {
                // Arm the 24h re-notify gate for this force version.
                com.example.util.AppUpdateManager.recordForceUpdateNotified(context, versionCode)
            }
            post(
                context,
                CHANNEL_UPDATES, "App Updates", "New version alerts",
                NotificationManager.IMPORTANCE_HIGH,
                ID_UPDATE_AVAILABLE,
                "Focivo $versionName is here",
                "New themes and fixes. Tap to update."
            )
            return
        }
        // Already notified once: only FORCE updates may nag again (24h gate).
        if (isForceUpdate &&
            com.example.util.AppUpdateManager.claimForceRenotify(context, versionCode)
        ) {
            post(
                context,
                CHANNEL_UPDATES, "App Updates", "New version alerts",
                NotificationManager.IMPORTANCE_HIGH,
                ID_UPDATE_AVAILABLE,
                "Focivo $versionName is waiting",
                "Please update to continue using Focivo."
            )
        }
    }

    /** 2. Schedule reminder — 10 minutes before a scheduled study block starts. */
    fun postScheduleReminder(context: Context, subject: String?) {
        val title = "Your focus session starts soon"
        val text = if (!subject.isNullOrBlank()) {
            "'$subject' begins in 10 minutes. Get ready to focus."
        } else {
            "Scheduled session begins in 10 minutes. Get ready to focus."
        }
        post(
            context,
            CHANNEL_SCHEDULE, "Study Schedule", "Scheduled session reminders",
            NotificationManager.IMPORTANCE_HIGH,
            ID_SCHEDULE_REMINDER,
            title,
            text
        )
    }

    // ------------------------------------------------------------------ state

    // ------------------------------------------------------------------ scheduling

    /** 10-minute heads-up before the next scheduled study block starts. */
    fun scheduleStudyReminder(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val next = com.example.service.ScheduledBlockScheduler.getNextStudyStartMillis(context)
            val intent = Intent(context, ScheduledBlockReceiver::class.java).apply {
                action = ScheduledBlockReceiver.ACTION_SCHEDULE_REMINDER
            }
            val pi = PendingIntent.getBroadcast(
                context, 9302, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (next == null) {
                am.cancel(pi)
                return
            }
            val (startMillis, subject) = next
            intent.putExtra(ScheduledBlockReceiver.EXTRA_REMINDER_SUBJECT, subject)
            // Rebuild the PendingIntent with the extra.
            val pi2 = PendingIntent.getBroadcast(
                context, 9302, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val fireAt = startMillis - 10 * 60 * 1000L
            if (fireAt <= System.currentTimeMillis()) {
                am.cancel(pi2)
                return
            }
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi2)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, fireAt, pi2)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi2)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, fireAt, pi2)
                }
            }
            Log.i(TAG, "Study reminder scheduled 10 min before ${java.util.Date(startMillis)}")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleStudyReminder failed: ${e.message}")
        }
    }

    // ---------------------------------------------------------------
    // v1.1 — notification survival hardening: every scheduled type is
    // backed by AlarmManager (never in-process timers), re-armed on boot.
    // ---------------------------------------------------------------

    /** 11. Comeback — "Your mascot misses you" after 3 days away. */
    fun postComeback(context: Context) {
        post(
            context,
            CHANNEL_MOTIVATION, "Motivation", "Gentle re-engagement nudges",
            NotificationManager.IMPORTANCE_HIGH,
            ID_COMEBACK,
            "Your mascot misses you",
            "It has been 3 days — one small focus session today keeps your habit alive."
        )
    }

    /**
     * Stamp every app open. Resets the comeback dedupe flag so the next
     * 3-day absence can fire exactly once.
     */
    fun stampAppOpen(context: Context) {
        try {
            context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE).edit()
                .putLong("last_app_open_ms", System.currentTimeMillis())
                .putBoolean("comeback_fired_for_absence", false)
                .apply()
        } catch (_: Exception) { }
    }

    /** Receiver-side: fires the comeback notification at most once per absence. */
    fun handleComebackCheck(context: Context) {
        try {
            if (!canNotify(context)) return
            val p = context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
            val lastOpen = p.getLong("last_app_open_ms", 0L)
            if (lastOpen == 0L) return
            val gapDays = (System.currentTimeMillis() - lastOpen) / (24 * 60 * 60 * 1000L)
            if (gapDays < 3) return
            if (p.getBoolean("comeback_fired_for_absence", false)) return // already fired for this absence
            p.edit().putBoolean("comeback_fired_for_absence", true).apply()
            postComeback(context)
        } catch (_: Exception) { }
    }

    /** Receiver-side: Sunday recap — posts the weekly recap notification if due. */
    fun handleSundayRecapCheck(context: Context) {
        try {
            if (!canNotify(context)) return
            val cal = java.util.Calendar.getInstance()
            if (cal.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.SUNDAY) return
            val weekKey = "${cal.get(java.util.Calendar.YEAR)}-W${cal.get(java.util.Calendar.WEEK_OF_YEAR)}"
            val p = context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
            if (p.getString("recap_notified_week", null) == weekKey) return // once per week
            // Build the recap from local sessions (same summary the dialog uses).
            val sessions = try {
                com.example.data.FocuslyRepository.getInstance(context).let { repo ->
                    kotlinx.coroutines.runBlocking {
                        repo.allSessions.first()
                    }
                }
            } catch (_: Exception) { emptyList<com.example.data.model.FocusSessionEntity>() }
            if (sessions.isEmpty()) return
            val totalMin = sessions.sumOf { it.durationSeconds } / 60
            val count = sessions.count { it.durationSeconds > 0 }
            p.edit().putString("recap_notified_week", weekKey).apply()
            post(
                context,
                CHANNEL_RECAP, "Weekly Recap", "Sunday study recaps",
                NotificationManager.IMPORTANCE_HIGH,
                ID_SUNDAY_RECAP,
                "Your week in focus",
                "$count sessions · $totalMin minutes. Tap to see your Sunday recap."
            )
        } catch (_: Exception) { }
    }

    /** Receiver-side: daily update check — notifies once per new version. */
    fun handleUpdateCheck(context: Context) {
        try {
            val info = kotlinx.coroutines.runBlocking {
                com.example.util.AppUpdateManager.checkForUpdates(context, isManualCheck = false)
            }
            if (info != null && info.isUpdateAvailable && info.checkSucceeded) {
                // postUpdateAvailable dedupes per version internally.
                postUpdateAvailable(context, info.latestVersionName, info.latestVersionCode, info.isForceUpdate)
            }
        } catch (_: Exception) { }
    }

    /** Weekly Sunday ~20:00 recap check (inexact). */
    fun scheduleSundayRecapWeekly(context: Context) {
        scheduleInexactDailyAt(context, 9303,
            com.example.service.ScheduledBlockReceiver.ACTION_SUNDAY_RECAP_CHECK,
            20, 0, java.util.Calendar.SUNDAY)
    }

    /** Daily update-available check (inexact, midday). */
    fun scheduleUpdateCheckDaily(context: Context) {
        scheduleInexactDailyAt(context, 9304,
            com.example.service.ScheduledBlockReceiver.ACTION_UPDATE_CHECK,
            12, 0, null)
    }

    /** Daily comeback-absence check (inexact, morning). */
    fun scheduleComebackCheckDaily(context: Context) {
        scheduleInexactDailyAt(context, 9305,
            com.example.service.ScheduledBlockReceiver.ACTION_COMEBACK_CHECK,
            9, 0, null)
    }

    /**
     * Inexact repeating alarm at a fixed local time; optionally restricted
     * to one weekday (e.g. Sunday). Idempotent — same request code replaces.
     */
    private fun scheduleInexactDailyAt(
        context: Context,
        requestCode: Int,
        action: String,
        hour: Int,
        minute: Int,
        onlyWeekday: Int?
    ) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, com.example.service.ScheduledBlockReceiver::class.java).apply {
                this.action = action
            }
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val next = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (onlyWeekday != null) {
                    // Advance to the next occurrence of that weekday.
                    while (get(java.util.Calendar.DAY_OF_WEEK) != onlyWeekday ||
                        timeInMillis <= System.currentTimeMillis()
                    ) {
                        add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                } else if (timeInMillis <= System.currentTimeMillis()) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }.timeInMillis
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next, AlarmManager.INTERVAL_DAY, pi)
            Log.i(TAG, "Scheduled $action (req=$requestCode)")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleInexactDailyAt($action) failed: ${e.message}")
        }
    }

    /** Re-arm every Focivo alarm (idempotent). Called on app start + boot. */
    fun scheduleAllSurvivalAlarms(context: Context) {
        scheduleStudyReminder(context)
        scheduleSundayRecapWeekly(context)
        scheduleUpdateCheckDaily(context)
        scheduleComebackCheckDaily(context)
    }
}
