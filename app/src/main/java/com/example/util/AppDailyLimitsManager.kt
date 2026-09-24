package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.FocuslyRepository
import com.example.data.model.AppDailyLimitEntity
import com.example.service.FocusShieldService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

object AppDailyLimitsManager {
    private const val TAG = "AppDailyLimitsManager"
    private const val CHANNEL_ID = "app_daily_limits_channel"

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        createNotificationChannel(context)
        startBackgroundMonitor(context)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Daily Time Limits",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time warnings and enforcement for per-app daily time budgets"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun startBackgroundMonitor(context: Context) {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            while (isActive) {
                delay(2500L) // Polling interval
                try {
                    // Update all active configured limits with real screen time
                    updateAllLimitsUsage(context)

                    if (usageStatsManager != null) {
                        val foregroundPkg = FocusShieldService.getForegroundPackage(usageStatsManager)
                        if (!foregroundPkg.isNullOrBlank() && foregroundPkg != context.packageName) {
                            onForegroundAppPolled(context, foregroundPkg)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in daily limits monitoring loop", e)
                }
            }
        }
    }

    /**
     * Periodically updates real minutes_used_today for all configured app limits
     * using live UsageStatsManager data.
     */
    private suspend fun updateAllLimitsUsage(context: Context) {
        val repo = FocuslyRepository.getInstance(context)
        val limits = repo.loadAppDailyLimitsFromLocal()
        if (limits.isEmpty()) return

        for (limit in limits) {
            if (!limit.isEnabled || EssentialAppsGuard.isEssentialApp(context, limit.appPackage)) continue

            val realMinutes = queryRealAppUsageMinutesToday(context, limit.appPackage)
            val resetLimit = limit.checkAndResetDailyUsage()

            if (realMinutes != resetLimit.minutesUsedToday) {
                val updated = resetLimit.copy(minutesUsedToday = realMinutes)
                repo.saveAppDailyLimit(updated)
            }
        }
    }

    /**
     * Query real, actual foreground screen time for appPackage today using UsageStatsManager.
     * Uses start of day (00:00:00.000) to now.
     */
    fun queryRealAppUsageMinutesToday(context: Context, appPackage: String): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        if (endTime <= startTime) return 0

        var totalUsageMs = 0L

        // Primary: queryUsageStats for daily interval
        try {
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            if (!statsList.isNullOrEmpty()) {
                val matching = statsList.filter { it.packageName.equals(appPackage, ignoreCase = true) }
                totalUsageMs = matching.sumOf { it.totalTimeInForeground }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryUsageStats failed for $appPackage", e)
        }

        // Secondary fallback / precision calculation via queryEvents if totalUsageMs is 0
        if (totalUsageMs <= 0L) {
            try {
                val events = usageStatsManager.queryEvents(startTime, endTime)
                val event = UsageEvents.Event()
                var lastResumeTime = 0L
                var eventUsageSum = 0L

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.packageName.equals(appPackage, ignoreCase = true)) {
                        when (event.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED -> {
                                lastResumeTime = event.timeStamp
                            }
                            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                                if (lastResumeTime > 0L && event.timeStamp >= lastResumeTime) {
                                    eventUsageSum += (event.timeStamp - lastResumeTime)
                                    lastResumeTime = 0L
                                }
                            }
                        }
                    }
                }
                // If currently running in foreground
                if (lastResumeTime > 0L && endTime >= lastResumeTime) {
                    eventUsageSum += (endTime - lastResumeTime)
                }

                if (eventUsageSum > 0L) {
                    totalUsageMs = eventUsageSum
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryEvents calculation failed for $appPackage", e)
            }
        }

        return (totalUsageMs / (1000 * 60)).toInt()
    }

    /**
     * Query all apps today's foreground usage minutes in a single aggregated batch.
     * Returns a map of packageName -> minutes used today.
     */
    fun queryAllAppsUsageMinutesToday(context: Context): Map<String, Int> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        if (endTime <= startTime) return emptyMap()

        val usageMap = mutableMapOf<String, Long>()

        try {
            val aggregated = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            if (!aggregated.isNullOrEmpty()) {
                for ((pkg, stats) in aggregated) {
                    if (stats.totalTimeInForeground > 0L) {
                        usageMap[pkg] = stats.totalTimeInForeground
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryAndAggregateUsageStats failed in queryAllAppsUsageMinutesToday", e)
        }

        try {
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            if (!statsList.isNullOrEmpty()) {
                for (stats in statsList) {
                    if (stats.totalTimeInForeground > 0L) {
                        val current = usageMap[stats.packageName] ?: 0L
                        usageMap[stats.packageName] = maxOf(current, stats.totalTimeInForeground)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryUsageStats fallback failed in queryAllAppsUsageMinutesToday", e)
        }

        return usageMap.mapValues { (_, ms) -> (ms / (1000 * 60)).toInt() }
    }

    /**
     * Checks whether an app's daily screen time limit is currently exceeded and active.
     */
    fun isDailyLimitExceeded(context: Context, packageName: String): Boolean {
        if (EssentialAppsGuard.isEssentialApp(context, packageName)) return false
        val repo = FocuslyRepository.getInstance(context)
        val limits = repo.loadAppDailyLimitsFromLocal()
        val limit = limits.find { it.appPackage.equals(packageName, ignoreCase = true) && it.isEnabled } ?: return false
        val checked = limit.checkAndResetDailyUsage()
        val realMinutes = queryRealAppUsageMinutesToday(context, packageName)
        val effectiveMins = if (realMinutes > 0) realMinutes else checked.minutesUsedToday
        val isEmergencyActive = checked.emergencyBypassUntilMs > System.currentTimeMillis()
        return effectiveMins >= checked.dailyLimitMinutes && !isEmergencyActive
    }

    /**
     * Called when a foreground package is polled by the background monitor.
     */
    fun onForegroundAppPolled(context: Context, foregroundPackage: String) {
        if (EssentialAppsGuard.isEssentialApp(context, foregroundPackage)) return

        val repo = FocuslyRepository.getInstance(context)
        val limits = repo.loadAppDailyLimitsFromLocal()
        if (limits.isEmpty()) return

        val matchingLimit = limits.find { it.appPackage.equals(foregroundPackage, ignoreCase = true) && it.isEnabled }
            ?: return

        checkAndEnforceLimit(context, matchingLimit)
    }

    fun checkAndEnforceLimit(context: Context, limit: AppDailyLimitEntity): Boolean {
        if (!limit.isEnabled || EssentialAppsGuard.isEssentialApp(context, limit.appPackage)) return false

        val checkedLimit = limit.checkAndResetDailyUsage()

        // Query REAL screen time from UsageStatsManager
        val realMinutes = queryRealAppUsageMinutesToday(context, checkedLimit.appPackage)
        val effectiveMinutes = if (realMinutes > 0) realMinutes else checkedLimit.minutesUsedToday

        var updatedLimit = checkedLimit.copy(minutesUsedToday = effectiveMinutes)

        val remainingMins = (updatedLimit.dailyLimitMinutes - effectiveMinutes).coerceAtLeast(0)
        val isEmergencyActive = updatedLimit.emergencyBypassUntilMs > System.currentTimeMillis()
        val isReached = effectiveMinutes >= updatedLimit.dailyLimitMinutes

        // 1. Pre-Limit Warning Notification & On-Screen Alert (20 Minutes Left: trigger when remaining is between 1 and 20 mins)
        if (updatedLimit.showReminders && !updatedLimit.reminder15MinSentToday && updatedLimit.dailyLimitMinutes > 20 && remainingMins in 1..20) {
            sendNotification(
                context,
                limit.id.hashCode() + 20,
                "⚠️ 20 Mins Left: ${updatedLimit.appName}",
                "Daily screen time limit approaching! Only $remainingMins minutes remaining today."
            )
            updatedLimit = updatedLimit.copy(reminder15MinSentToday = true)
        }

        // 2. 80% Threshold Reminder Check
        if (updatedLimit.showReminders && !updatedLimit.reminder80SentToday && !isReached) {
            val threshold = (updatedLimit.dailyLimitMinutes * 0.8).toInt()
            if (effectiveMinutes >= threshold && threshold > 0) {
                sendNotification(
                    context,
                    limit.id.hashCode() + 80,
                    "Limit Warning: 80% Used",
                    "You've used $effectiveMinutes of ${updatedLimit.dailyLimitMinutes} mins on ${updatedLimit.appName} today."
                )
                updatedLimit = updatedLimit.copy(reminder80SentToday = true)
            }
        }

        // Save updated usage and notification states
        scope.launch {
            repoSave(context, updatedLimit)
        }

        // 3. Limit Reached Enforcement (Strict block for the day)
        if (isReached) {
            if (isEmergencyActive) {
                // Emergency 15-minute bypass window is currently active
                return false
            }

            // Always enforce limit block
            triggerAppBlock(context, updatedLimit)
            return true
        }

        return false
    }

    private fun triggerAppBlock(context: Context, limit: AppDailyLimitEntity) {
        val appName = limit.appName.ifBlank { limit.appPackage }
        val reason = "Daily time limit of ${limit.dailyLimitMinutes} mins reached for $appName."
        try {
            val interceptIntent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                context = context,
                packageName = limit.appPackage,
                appName = appName,
                durationSec = 0,
                reason = reason,
                isPunishment = false
            )
            context.startActivity(interceptIntent)
        } catch (_: Exception) {}
    }

    private suspend fun repoSave(context: Context, limit: AppDailyLimitEntity) {
        FocuslyRepository.getInstance(context).saveAppDailyLimit(limit)
    }

    private fun sendNotification(context: Context, notificationId: Int, title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(notificationId, notification)
    }
}
