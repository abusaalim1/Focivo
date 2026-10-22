package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppDailyLimitsManager {
    private const val TAG = "AppDailyLimitsManager"
    private const val CHANNEL_ID = "app_daily_limits_channel"

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Serializes all daily-limit repo writes shared between the monitor
     * coroutine and checkAndEnforceLimit, so concurrent full-entity saves
     * cannot clobber reminder flags or minutes-used updates.
     */
    private val repoSaveMutex = Mutex()

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
                description = "Warnings and enforcement for per-app daily time budgets"
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
        DailyUsagePipeline.refreshIfDayChanged()
        if (!DailyUsagePipeline.hasUsagePermission(context)) return
        val repo = FocuslyRepository.getInstance(context)
        val limits = repo.loadAppDailyLimitsFromLocal()
        if (limits.isEmpty()) return

        for (limit in limits) {
            if (!limit.isEnabled || EssentialAppsGuard.isEssentialApp(context, limit.appPackage)) continue

            val realMinutes = queryRealAppUsageMinutesToday(context, limit.appPackage)
            val resetLimit = limit.checkAndResetDailyUsage()

            if (realMinutes != resetLimit.minutesUsedToday) {
                val updated = resetLimit.copy(minutesUsedToday = realMinutes)
                repoSaveMutex.withLock { repo.saveAppDailyLimit(updated) }
            }
        }
    }

    /**
     * v1.3.3 — now delegates to [DailyUsagePipeline], the single source of
     * truth for usage stats. Kept for API compatibility (callers + tests).
     * Returns today's real foreground minutes for appPackage.
     */
    fun queryRealAppUsageMinutesToday(context: Context, appPackage: String): Int {
        return (DailyUsagePipeline.usageMillisForPackage(context, appPackage) / (1000 * 60)).toInt()
    }

    /**
     * v1.3.3 — now delegates to [DailyUsagePipeline]. Returns a map of
     * packageName -> minutes used today, from the cached pipeline snapshot.
     */
    fun queryAllAppsUsageMinutesToday(context: Context): Map<String, Int> {
        return DailyUsagePipeline.getSnapshot(context)?.perAppMillis
            ?.mapValues { (_, ms) -> (ms / (1000 * 60)).toInt() }
            ?: emptyMap()
    }

    /**
     * Checks whether an app's daily screen time limit is currently exceeded and active.
     */
    fun isDailyLimitExceeded(context: Context, packageName: String): Boolean {
        if (EssentialAppsGuard.isEssentialApp(context, packageName)) return false
        DailyUsagePipeline.refreshIfDayChanged()
        if (!DailyUsagePipeline.hasUsagePermission(context)) return false
        val repo = FocuslyRepository.getInstance(context)
        val limits = repo.loadAppDailyLimitsFromLocal()
        val limit = limits.find { it.appPackage.equals(packageName, ignoreCase = true) && it.isEnabled } ?: return false
        val checked = limit.checkAndResetDailyUsage()
        val realMinutes = queryRealAppUsageMinutesToday(context, packageName)
        return PerAppBudgetEvaluator.isExceeded(
            usageMinutesToday = realMinutes,
            dailyLimitMinutes = checked.dailyLimitMinutes,
            emergencyBypassUntilMs = checked.emergencyBypassUntilMs
        )
    }

    /**
     * Called when a foreground package is polled by the background monitor.
     */
    fun onForegroundAppPolled(context: Context, foregroundPackage: String) {
        if (EssentialAppsGuard.isEssentialApp(context, foregroundPackage)) return
        if (!DailyUsagePipeline.hasUsagePermission(context)) return

        val repo = FocuslyRepository.getInstance(context)
        val limits = repo.loadAppDailyLimitsFromLocal()
        if (limits.isEmpty()) return

        val matchingLimit = limits.find { it.appPackage.equals(foregroundPackage, ignoreCase = true) && it.isEnabled }
            ?: return

        checkAndEnforceLimit(context, matchingLimit)
    }

    fun checkAndEnforceLimit(context: Context, limit: AppDailyLimitEntity): Boolean {
        if (!limit.isEnabled || EssentialAppsGuard.isEssentialApp(context, limit.appPackage)) return false
        DailyUsagePipeline.refreshIfDayChanged()
        if (!DailyUsagePipeline.hasUsagePermission(context)) return false

        val checkedLimit = limit.checkAndResetDailyUsage()

        // Query screen time from DailyUsagePipeline snapshot
        val effectiveMinutes = queryRealAppUsageMinutesToday(context, checkedLimit.appPackage)

        var updatedLimit = checkedLimit.copy(minutesUsedToday = effectiveMinutes)

        val remainingMins = (updatedLimit.dailyLimitMinutes - effectiveMinutes).coerceAtLeast(0)
        val isEmergencyActive = updatedLimit.emergencyBypassUntilMs > System.currentTimeMillis()
        val isReached = effectiveMinutes >= updatedLimit.dailyLimitMinutes

        // 1. Pre-Limit Warning Notification & On-Screen Alert (20 Minutes Left: trigger when remaining is between 1 and 20 mins)
        if (updatedLimit.showReminders && !updatedLimit.reminder15MinSentToday && updatedLimit.dailyLimitMinutes > 20 && remainingMins in 1..20) {
            sendNotification(
                context,
                limit.id.hashCode() + 20,
                " 20 Mins Left: ${updatedLimit.appName}",
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
        // v1.2.3: log the concrete reason to Guard Hub — this lock fires BY
        // DESIGN with no session/schedule, so the hub entry is what tells
        // the owner it is a daily limit and not a phantom lock.
        com.example.util.AiStudyGuardManager.logGuardActivity(
            context = context,
            app = appName,
            stage = "DAILY_LIMIT",
            verdict = "LIMIT_BLOCK",
            reason = reason
        )
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
        repoSaveMutex.withLock {
            FocuslyRepository.getInstance(context).saveAppDailyLimit(limit)
        }
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
