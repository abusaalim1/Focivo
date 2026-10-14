package com.example.util

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AndroidPreferenceSessionManager
import com.example.data.SupabaseManager
import com.example.data.SupabasePunishmentLogDto
import com.example.data.SupabaseService
import com.example.service.FocusShieldService
import com.example.service.ScheduledBlockScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ActiveAiWarning(
    val logId: String,
    val packageName: String,
    val appName: String,
    val reason: String,
    val warnedAtMillis: Long,
    val warnedAtIso: String,
    val graceExpiresAtMillis: Long
)

data class ActiveAiBlock(
    val logId: String,
    val packageName: String,
    val appName: String,
    val reason: String,
    val blockedAtMillis: Long,
    val durationMinutes: Int = 180
)

data class ActiveAiResolved(
    val logId: String,
    val packageName: String,
    val appName: String,
    val message: String = "Good — no block applied!"
)

object AiStudyGuardManager {
    private const val TAG = "AiStudyGuardManager"
    private const val PREFS_NAME = "focusly_local_data"
    private const val KEY_AI_GUARD_ENABLED = "is_ai_study_guard_enabled"
    private const val KEY_PUNISHMENT_LOGS_JSON = "custom_punishment_logs_json"
    private const val KEY_PUNISHMENT_PREFIX = "punishment_lock_"

    const val CHANNEL_ID_ALERTS = "ai_study_guard_channel"
    private const val NOTIFICATION_ID_GUARD_STATUS = 8000
    private const val NOTIFICATION_ID_WARNING = 8001
    private const val NOTIFICATION_ID_BLOCK = 8002
    private const val NOTIFICATION_ID_RESOLVED = 8003

    const val GRACE_PERIOD_SECONDS = 180 // 3 minutes observation window

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile
    private var graceObservationJob: Job? = null

    /**
     * Guards the warning/block/resolve state transitions so concurrent triggers
     * (accessibility thread, UI thread, IO grace coroutine) cannot double-fire.
     */
    private val resolveLock = Any()

    private val _activeWarning = MutableStateFlow<ActiveAiWarning?>(null)
    val activeWarning: StateFlow<ActiveAiWarning?> = _activeWarning.asStateFlow()

    private val _activeBlock = MutableStateFlow<ActiveAiBlock?>(null)
    val activeBlock: StateFlow<ActiveAiBlock?> = _activeBlock.asStateFlow()

    private val _activeResolved = MutableStateFlow<ActiveAiResolved?>(null)
    val activeResolved: StateFlow<ActiveAiResolved?> = _activeResolved.asStateFlow()

    private val _punishmentLogs = MutableStateFlow<List<SupabasePunishmentLogDto>>(emptyList())
    val punishmentLogs: StateFlow<List<SupabasePunishmentLogDto>> = _punishmentLogs.asStateFlow()

    fun init(context: Context) {
        createNotificationChannel(context)
        loadLocalLogs(context)
        loadGuardActivity(context)
        updateGuardStatusNotification(context)
    }

    fun isAiGuardEnabled(context: Context): Boolean {
        val hasPerm = isAccessibilityPermissionGranted(context)
        if (!hasPerm) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AI_GUARD_ENABLED, true)
    }

    fun setAiGuardEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AI_GUARD_ENABLED, enabled).apply()
        // MERGED: YouTube AI content detection is part of the master AI Study
        // Guard — one option instead of two. Drive the YouTube filter state
        // from the master switch so they can never disagree.
        YouTubeStudyGuardManager.setFilterEnabled(context, enabled)
        updateGuardStatusNotification(context)
    }

    /**
     * Evaluates in real-time whether current wall-clock time is an active study period:
     * 1. Active Quick Focus / manual focus timer running (is_manual_timer_running && manual_timer_end_time_ms > now)
     * 2. Inside an active Auto Study Schedule window (checking start_time, end_time, active day, break window)
     *
     * Protection ONLY applies during genuine active study sessions or triggered schedules.
     *
     * v1.1.9 robustness: the two timer prefs files are written with separate apply()
     * calls, so a kill between them (or any writer inconsistency) can leave
     * is_timer_running=true while manual_timer_end_time_ms is 0/stale — which used
     * to silently kill the guard (zero detections). In that suspicious state we check
     * whether FocusTimerService is actually alive (it only runs during a genuine
     * timer session) instead of trusting the stale end time.
     */
    fun isStudyPeriodActive(context: Context): Boolean {
        val now = System.currentTimeMillis()
        // 1. Manual timer running
        val localPrefs = context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
        val isManualRunning = localPrefs.getBoolean("is_manual_timer_running", false)
        val timerEndTime = localPrefs.getLong("manual_timer_end_time_ms", 0L)
        val timerStatePrefs = context.getSharedPreferences("focusly_timer_state", Context.MODE_PRIVATE)
        val isTimerStateRunning = timerStatePrefs.getBoolean("is_timer_running", false)

        val flagsSayRunning = isManualRunning || isTimerStateRunning
        // Normal path: flags say running AND the end time is fresh.
        val freshTimerActive = flagsSayRunning && timerEndTime > now
        // Hole path (v1.1.9): timer_state says running but the manual end-time is
        // missing/stale. Trust it only if the timer service is genuinely alive.
        val staleButLive = isTimerStateRunning && timerEndTime <= now && isFocusTimerServiceAlive(context)

        // 2. Active Auto Study Schedule window
        val isScheduleActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(context)

        return freshTimerActive || staleButLive || isScheduleActive
    }

    /**
     * v1.1.9: liveness check for the dead-guard hole. FocusTimerService is a
     * foreground service that only exists while a timer session is genuinely
     * active, so a live service means the timer is real even if the prefs
     * end-time got out of sync.
     */
    private fun isFocusTimerServiceAlive(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            @Suppress("DEPRECATION")
            val running = am.getRunningServices(Int.MAX_VALUE) ?: return false
            running.any { it.service.className == "com.example.service.FocusTimerService" }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * v1.4.3: the persistent guard notification shows ONLY while a study
     * session is genuinely running (manual timer or active schedule). On
     * plain app open / cold start it is cancelled — the owner reported it
     * popping up on every app open. Also restyled: mascot icon, no emoji.
     */
    fun updateGuardStatusNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (!isAiGuardEnabled(context) || !isStudyPeriodActive(context)) {
            manager.cancel(NOTIFICATION_ID_GUARD_STATUS)
            return
        }

        val title = "AI Study Guard is watching"
        val text = "Blocking distractions while you study"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_GUARD_STATUS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.mascot_study_happy)
        } catch (_: Exception) { null }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setColor(0xFF8CE000.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSound(null)
            .setContentIntent(pi)
        if (largeIcon != null) builder.setLargeIcon(largeIcon)

        try {
            manager.notify(NOTIFICATION_ID_GUARD_STATUS, builder.build())
        } catch (_: Exception) { }
    }

    fun cancelGuardStatusNotification(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID_GUARD_STATUS)
        } catch (_: Exception) { }
    }

    fun isAppUnderPunishment(context: Context, packageName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endTime = prefs.getLong("$KEY_PUNISHMENT_PREFIX$packageName", 0L)
        return endTime > System.currentTimeMillis()
    }

    fun isAnyPunishmentActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        return prefs.all.any { (key, value) ->
            key.startsWith(KEY_PUNISHMENT_PREFIX) && (value as? Long ?: 0L) > now
        }
    }

    fun getPunishmentRemainingSeconds(context: Context, packageName: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endTime = prefs.getLong("$KEY_PUNISHMENT_PREFIX$packageName", 0L)
        val diff = (endTime - System.currentTimeMillis()) / 1000L
        return if (diff > 0) diff.toInt() else 0
    }

    private val nonStudyMessageCounts = ConcurrentHashMap<String, Int>()
    private var nonStudyFirstDetectedMs = 0L
    private var currentMonitoredPackage: String? = null

    fun getNonStudyCount(packageName: String): Int = nonStudyMessageCounts[packageName] ?: 0

    fun resetSessionCounters() {
        nonStudyMessageCounts.clear()
        nonStudyFirstDetectedMs = 0L
        currentMonitoredPackage = null
    }

    fun onStudyActivityDetected(context: Context, packageName: String, messageText: String = "") {
        val count = nonStudyMessageCounts[packageName] ?: 0
        Log.d(TAG, "[AiGuard Classification] App: $packageName | Result: STUDY | Message: '$messageText' | Running Counter: $count")
        // v1.1.9 diagnostic
        logGuardActivity(context, packageName, "outcome", "STUDY", "counter=$count", messageText)

        val cur = _activeWarning.value
        if (cur != null && cur.packageName == packageName) {
            Log.i(TAG, "[AiGuard] User sent study message after warning for ${cur.appName}. Resolving warning and resetting counter to 0.")
            triggerStage3Resolved(context, cur, "Study content detected: '$messageText'")
            nonStudyMessageCounts[packageName] = 0
        }
    }

    /**
     * Entry point for detected non-study activity in ChatGPT, Claude, or Chrome.
     * Evaluates message-based 2-then-warn counter:
     * - Message 1 & 2: Silently count (no visible warning)
     * - Message 3: Fires exactly ONE warning (popup + heads-up notification)
     * - Message 4+: Continued non-study usage after warning triggers 3-Hour BLOCK for this app only
     */
    fun onNonStudyDetected(
        context: Context,
        packageName: String,
        messageText: String = "",
        reason: String = "",
        textHash: Int = 0
    ) {
        if (!isAiGuardEnabled(context)) {
            // v1.1.9 diagnostic (throttled)
            logGuardSkip(context, packageName, "outcome", "guard-disabled")
            return
        }

        val appName = when {
            packageName.contains("claude") -> "Claude"
            packageName.contains("chatgpt") || packageName.contains("openai") -> "ChatGPT"
            packageName.contains("chrome") -> "Chrome"
            else -> FocusShieldService.friendlyAppName(context, packageName)
        }

        // If already under 3h punishment lock, ignore
        if (isAppUnderPunishment(context, packageName)) {
            Log.d(TAG, "$appName is already under punishment lock")
            // v1.1.9 diagnostic (throttled — can fire per event)
            logGuardSkip(context, packageName, "outcome", "already-punished")
            return
        }

        // Increment running counter
        val count = (nonStudyMessageCounts[packageName] ?: 0) + 1
        nonStudyMessageCounts[packageName] = count

        Log.d(TAG, "[AiGuard Classification] App: $packageName ($appName) | Result: NON-STUDY | Message: '$messageText' | Running Counter: $count")

        val currentWarning = _activeWarning.value

        if (count == 1) {
            // 1st non-study message: Immediately trigger Stage 1 Warning
            Log.w(TAG, "[AiGuard] Non-study message #1 detected for $appName! Triggering Stage 1 Warning.")
            // v1.1.9 diagnostic
            logGuardActivity(context, packageName, "outcome", "NON_STUDY", "count=1→warning", messageText)
            triggerStage1Warning(context, packageName, appName, reason.ifBlank { "Non-study conversation: '$messageText'" })
        } else if (count >= 2) {
            // Continued non-study messaging after warning: Trigger Stage 2 Block / Punishment
            Log.w(TAG, "[AiGuard] Continued non-study usage (count=$count) after warning for $appName! Triggering Stage 2 Block.")
            // v1.1.9 diagnostic
            logGuardActivity(context, packageName, "outcome", "NON_STUDY", "count=$count→block-3h", messageText)
            val warningToUse = currentWarning ?: ActiveAiWarning(
                logId = java.util.UUID.randomUUID().toString(),
                packageName = packageName,
                appName = appName,
                reason = reason.ifBlank { "Non-study conversation: '$messageText'" },
                warnedAtMillis = System.currentTimeMillis(),
                graceExpiresAtMillis = System.currentTimeMillis() + (GRACE_PERIOD_SECONDS * 1000L),
                warnedAtIso = getIsoNow()
            )
            triggerStage2Block(context, warningToUse, reason.ifBlank { "Continued non-study messaging: '$messageText'" })
        }
    }

    /**
     * STAGE 1: WARNING
     * - Shows in-app popup immediately
     * - Fires high-priority heads-up system notification
     * - Logs "warned" to public.punishment_logs
     * - Starts grace/observation window (3 minutes)
     */
    private fun triggerStage1Warning(
        context: Context,
        packageName: String,
        appName: String,
        reason: String
    ) {
        val nowMillis = System.currentTimeMillis()
        val nowIso = getIsoNow()
        val graceExpiresAtMillis = nowMillis + (GRACE_PERIOD_SECONDS * 1000L)
        val logId = UUID.randomUUID().toString()

        val warning = ActiveAiWarning(
            logId = logId,
            packageName = packageName,
            appName = appName,
            reason = reason,
            warnedAtMillis = nowMillis,
            warnedAtIso = nowIso,
            graceExpiresAtMillis = graceExpiresAtMillis
        )

        synchronized(resolveLock) {
            _activeWarning.value = warning
        }

        // Show High-Priority Heads-up Notification
        showWarningNotification(context, warning)

        // Launch Mascot Warning Dialog overlay on-screen
        try {
            val popupIntent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                context = context,
                packageName = packageName,
                appName = appName,
                durationSec = GRACE_PERIOD_SECONDS,
                reason = "Study Warning: Non-study content detected in $appName.\n\nPlease avoid non-study content and stay focused on your studies!\n\nKeep your questions study & exam-related, or $appName will be locked for 3 hours.",
                isPunishment = false,
                guardVariant = com.example.ui.screens.BlockedAppLockActivity.GUARD_VARIANT_WARNING
            )
            context.startActivity(popupIntent)
        } catch (_: Exception) {}

        // Log to Supabase and Local Storage
        val validUid = resolveValidUserId(context)
        val logDto = SupabasePunishmentLogDto(
            id = logId,
            user_id = validUid,
            app_package = packageName,
            status = "warned",
            reason = reason,
            warned_at = nowIso,
            blocked_at = null,
            block_duration_minutes = null,
            created_at = nowIso
        )
        recordLog(context, logDto)

        // Start grace window observation
        synchronized(resolveLock) {
            graceObservationJob?.cancel()
            graceObservationJob = scope.launch {
                val checkInterval = 2000L
                while (true) {
                    delay(checkInterval)
                    val cur = _activeWarning.value ?: break
                    if (System.currentTimeMillis() >= cur.graceExpiresAtMillis) {
                        // Grace window expired without further violations!
                        // User corrected course -> STAGE 3: RESOLVED
                        Log.i(TAG, "Grace window elapsed without repeat violation for ${cur.appName}. Resolving warning.")
                        triggerStage3Resolved(context, cur, "User adhered to study focus throughout grace period.")
                        break
                    }
                }
        } // end scope.launch
        } // end synchronized(resolveLock) — grace job replace
    }

    /**
     * STAGE 2: BLOCK
     * - Triggers 3-hour lock via AppBlocker mechanism
     * - Shows second popup and notification confirming the block and its duration
     * - Updates log row to status = "blocked", blocked_at = now, block_duration_minutes = 180
     */
    private fun triggerStage2Block(
        context: Context,
        warning: ActiveAiWarning,
        repeatReason: String
    ) {
        synchronized(resolveLock) {
            graceObservationJob?.cancel()
            graceObservationJob = null
            _activeWarning.value = null
        }

        val lockDurationMinutes = 180
        val lockDurationSeconds = lockDurationMinutes * 60
        val nowMillis = System.currentTimeMillis()
        val nowIso = getIsoNow()
        val lockEndTime = nowMillis + (lockDurationSeconds * 1000L)

        // Enforce lock in preferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("$KEY_PUNISHMENT_PREFIX${warning.packageName}", lockEndTime).apply()

        // Kick user out of the blocked app immediately
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        } catch (_: Exception) {}

        // Launch FocusShieldService with 3-hour block
        try {
            val intent = Intent(context, FocusShieldService::class.java).apply {
                action = FocusShieldService.ACTION_START_SHIELD
                putExtra(FocusShieldService.EXTRA_DURATION_SECONDS, lockDurationSeconds)
                putExtra(FocusShieldService.EXTRA_IS_PUNISHMENT, true)
                putExtra(FocusShieldService.EXTRA_BLOCKED_LIST, warning.packageName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting punishment shield service: ${e.message}")
        }

        // Show Mascot Block Overlay inside blocked app
        try {
            val interceptIntent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                context = context,
                packageName = warning.packageName,
                appName = warning.appName,
                durationSec = lockDurationSeconds,
                reason = "3-Hour Penalty: ${warning.appName} is locked for non-study conversation. Other apps remain unlocked.",
                isPunishment = true
            )
            context.startActivity(interceptIntent)
        } catch (_: Exception) {}

        val blockState = ActiveAiBlock(
            logId = warning.logId,
            packageName = warning.packageName,
            appName = warning.appName,
            reason = "Warning ignored: $repeatReason",
            blockedAtMillis = nowMillis,
            durationMinutes = lockDurationMinutes
        )
        _activeBlock.value = blockState

        // High priority Block Notification
        showBlockedNotification(context, blockState)

        // Update punishment log
        val validUid = resolveValidUserId(context)
        val logDto = SupabasePunishmentLogDto(
            id = warning.logId,
            user_id = validUid,
            app_package = warning.packageName,
            status = "blocked",
            reason = "Ignored warning: $repeatReason",
            warned_at = warning.warnedAtIso,
            blocked_at = nowIso,
            block_duration_minutes = lockDurationMinutes,
            created_at = warning.warnedAtIso
        )
        recordLog(context, logDto)
    }

    /**
     * STAGE 3: RESOLVED
     * - User stopped casual chat or switched back to study
     * - Cancels pending block
     * - Logs status = "warned_resolved"
     * - Shows positive confirmation popup and notification ("Good — no block applied")
     */
    fun triggerStage3Resolved(
        context: Context,
        warning: ActiveAiWarning,
        resolutionNote: String = "Returned to study in time"
    ) {
        // Atomic claim: only the first caller resolves a given warning, so the
        // grace-expiry coroutine and a manual/UI resolve cannot double-fire
        // (duplicate notifications and duplicate Supabase log upserts).
        val claimed = synchronized(resolveLock) {
            val cur = _activeWarning.value
            if (cur == null || cur.logId != warning.logId) {
                false
            } else {
                graceObservationJob?.cancel()
                graceObservationJob = null
                _activeWarning.value = null
                nonStudyFirstDetectedMs = 0L
                currentMonitoredPackage = null
                true
            }
        }
        if (!claimed) {
            Log.d(TAG, "[AiGuard] Resolve skipped — warning ${warning.logId} already handled.")
            return
        }

        val resolvedState = ActiveAiResolved(
            logId = warning.logId,
            packageName = warning.packageName,
            appName = warning.appName,
            message = "Good — no block applied! You stayed disciplined and got back on track."
        )
        _activeResolved.value = resolvedState

        // High priority positive confirmation notification
        showResolvedNotification(context, resolvedState)

        // Update punishment log in Supabase & local
        val validUid = resolveValidUserId(context)
        val logDto = SupabasePunishmentLogDto(
            id = warning.logId,
            user_id = validUid,
            app_package = warning.packageName,
            status = "warned_resolved",
            reason = "${warning.reason} ($resolutionNote)",
            warned_at = warning.warnedAtIso,
            blocked_at = null,
            block_duration_minutes = null,
            created_at = warning.warnedAtIso
        )
        recordLog(context, logDto)
    }

    /**
     * Called when the user actively taps "I'll Return to Studying" on the Warning Dialog.
     */
    fun resolveActiveWarningManually(context: Context) {
        val cur = _activeWarning.value ?: return
        triggerStage3Resolved(context, cur, "User manually confirmed return to studying")
    }

    fun dismissActiveBlock() {
        _activeBlock.value = null
    }

    fun dismissActiveResolved() {
        _activeResolved.value = null
    }

    fun dismissActiveWarning() {
        _activeWarning.value = null
    }

    // ==========================================
    // NOTIFICATIONS
    // ==========================================

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "AI Study Discipline Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent warnings and block notifications for non-study AI distractions"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showWarningNotification(context: Context, warning: ActiveAiWarning) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_SHOW_AI_WARNING", true)
            putExtra("EXTRA_WARNING_PACKAGE", warning.packageName)
        }
        val pi = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_WARNING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_large)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(largeIcon)
            .setContentTitle("Study Discipline Warning: ${warning.appName}")
            .setContentText("Casual/non-study activity detected. Return to studying within 3 min or this app locks for 3 hours!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Non-study usage detected in ${warning.appName}.\n\nReason: ${warning.reason}\n\nContinuing off-topic activity will trigger an immediate 3-HOUR LOCKOUT on ${warning.appName}. Return to your study goals now!")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setContentIntent(pi)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID_WARNING, builder.build())
    }

    private fun showBlockedNotification(context: Context, block: ActiveAiBlock) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_BLOCK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_large)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(largeIcon)
            .setContentTitle("${block.appName} Blocked for 3 Hours")
            .setContentText("Study warning was ignored. ${block.appName} is now locked for 3 hours.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Study warning was ignored. ${block.appName} has been locked for 3 hours (${block.durationMinutes} minutes) to enforce your focus and study habits.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pi)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID_BLOCK, builder.build())
        // Clear warning notification
        manager?.cancel(NOTIFICATION_ID_WARNING)
    }

    private fun showResolvedNotification(context: Context, resolved: ActiveAiResolved) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_RESOLVED,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_large)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(largeIcon)
            .setContentTitle("Good — No Block Applied")
            .setContentText("You corrected course in ${resolved.appName} in time. Keep up the great focus!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID_RESOLVED, builder.build())
        // Clear warning notification
        manager?.cancel(NOTIFICATION_ID_WARNING)
    }

    // ==========================================
    // LOG PERSISTENCE (Supabase + Local Cache)
    // ==========================================

    private fun recordLog(context: Context, logDto: SupabasePunishmentLogDto) {
        val current = _punishmentLogs.value.toMutableList()
        val index = current.indexOfFirst { it.id == logDto.id }
        if (index >= 0) {
            current[index] = logDto
        } else {
            current.add(0, logDto)
        }
        _punishmentLogs.value = current
        saveLocalLogs(context, current)

        // Sync with Supabase
        val uid = SupabaseService.getInstance().getCurrentUserId()
        if (!uid.isNullOrBlank()) {
            val toUpload = if (logDto.user_id == "anonymous") logDto.copy(user_id = uid) else logDto
            scope.launch {
                try {
                    SupabaseService.getInstance().upsertPunishmentLog(toUpload)
                    Log.d(TAG, "Synced punishment log to Supabase: ${toUpload.id} -> ${toUpload.status}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync punishment log to Supabase: ${e.message}")
                }
            }
        }
    }

    fun syncFromRemoteLogs(context: Context, remoteLogs: List<SupabasePunishmentLogDto>) {
        _punishmentLogs.value = remoteLogs
        saveLocalLogs(context, remoteLogs)
    }

    private fun saveLocalLogs(context: Context, logs: List<SupabasePunishmentLogDto>) {
        try {
            val encoded = json.encodeToString(logs)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_PUNISHMENT_LOGS_JSON, encoded).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing punishment logs: ${e.message}")
        }
    }

    fun loadLocalLogs(context: Context): List<SupabasePunishmentLogDto> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PUNISHMENT_LOGS_JSON, null) ?: return emptyList()
        return try {
            val list = json.decodeFromString<List<SupabasePunishmentLogDto>>(raw)
            _punishmentLogs.value = list
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding local punishment logs: ${e.message}")
            emptyList()
        }
    }

    fun isAccessibilityPermissionGranted(context: Context): Boolean {
        if (com.example.service.AiStudyAccessibilityService.isServiceConnected) {
            return true
        }
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            val enabledList = am?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledList != null) {
                for (service in enabledList) {
                    val id = service.id ?: continue
                    if (id.contains("AiStudyAccessibilityService", ignoreCase = true)) {
                        return true
                    }
                }
            }
            val expectedServiceName = "${context.packageName}/com.example.service.AiStudyAccessibilityService"
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true) ||
                    componentName.endsWith("AiStudyAccessibilityService", ignoreCase = true) ||
                    componentName.contains("AiStudyAccessibilityService", ignoreCase = true)
                ) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * v1.1.6: Liveness signal. The accessibility service writes a heartbeat timestamp
     * on every processed event; the hub uses this to distinguish a truly listening
     * service from a stale "Granted" settings flag.
     */
    fun getLastGuardEventAtMs(context: Context): Long {
        return try {
            context.getSharedPreferences("focusly_guard_prefs", Context.MODE_PRIVATE)
                .getLong("last_guard_event_at_ms", 0L)
        } catch (_: Exception) {
            0L
        }
    }

    fun isGuardListeningRecently(context: Context): Boolean {
        val last = getLastGuardEventAtMs(context)
        return last > 0L && System.currentTimeMillis() - last < 5 * 60 * 1000L
    }

    // ------------------------------------------------------------------
    // v1.1.9: Guard activity log — lightweight diagnostic ring buffer.
    // The owner's guard detects nothing and we are blind without device logs,
    // so every evaluation (including skips) is recorded here. The hub shows
    // the recent entries; the owner screenshots them to show exactly where
    // detection dies.
    // Privacy: only the first 60 chars of evaluated text are kept, never full
    // messages. Prefs writes are throttled to at most once per 5s (the
    // in-memory buffer is always current).
    // ------------------------------------------------------------------
    @kotlinx.serialization.Serializable
    data class GuardActivityEntry(
        val ts: Long = 0L,
        val app: String = "",
        val stage: String = "",
        val verdict: String = "",
        val reason: String = "",
        val snippet: String = ""
    )

    private const val KEY_GUARD_ACTIVITY_JSON = "guard_activity_log_json"
    private const val GUARD_ACTIVITY_CAP = 20
    private const val GUARD_ACTIVITY_PERSIST_MS = 5000L
    private const val GUARD_ACTIVITY_SKIP_THROTTLE_MS = 30000L

    private val guardActivityLock = Any()
    private val guardActivityBuffer = ArrayDeque<GuardActivityEntry>()
    @Volatile
    private var lastActivityPersistMs = 0L
    private val lastSkipLogMs = mutableMapOf<String, Long>()

    private val _guardActivity = MutableStateFlow<List<GuardActivityEntry>>(emptyList())
    val guardActivity: StateFlow<List<GuardActivityEntry>> = _guardActivity.asStateFlow()

    /**
     * Records one guard evaluation. Thread-safe; callable from the
     * accessibility service's background paths.
     */
    fun logGuardActivity(
        context: Context,
        app: String,
        stage: String,
        verdict: String,
        reason: String,
        snippet: String = ""
    ) {
        try {
            val entry = GuardActivityEntry(
                ts = System.currentTimeMillis(),
                app = app.take(32),
                stage = stage.take(32),
                verdict = verdict.take(16),
                reason = reason.take(64),
                snippet = snippet.take(60)
            )
            val snapshot: List<GuardActivityEntry>
            synchronized(guardActivityLock) {
                guardActivityBuffer.addLast(entry)
                while (guardActivityBuffer.size > GUARD_ACTIVITY_CAP) guardActivityBuffer.removeFirst()
                snapshot = guardActivityBuffer.toList()
            }
            _guardActivity.value = snapshot.asReversed()
            val now = System.currentTimeMillis()
            if (now - lastActivityPersistMs >= GUARD_ACTIVITY_PERSIST_MS) {
                lastActivityPersistMs = now
                try {
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_GUARD_ACTIVITY_JSON, json.encodeToString(snapshot))
                        .apply()
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    /**
     * Throttled skip logging: SKIPPED entries (not-study-period, title-null, …)
     * can fire on every accessibility event, so at most one per app+stage+reason
     * every 30s. Real evaluations are never throttled here.
     */
    fun logGuardSkip(
        context: Context,
        app: String,
        stage: String,
        reason: String
    ) {
        try {
            val key = "$app|$stage|$reason"
            val now = System.currentTimeMillis()
            synchronized(guardActivityLock) {
                val last = lastSkipLogMs[key] ?: 0L
                if (now - last < GUARD_ACTIVITY_SKIP_THROTTLE_MS) return
                lastSkipLogMs[key] = now
                if (lastSkipLogMs.size > 60) lastSkipLogMs.clear()
            }
            logGuardActivity(context, app, stage, "SKIPPED", reason)
        } catch (_: Throwable) {}
    }

    fun loadGuardActivity(context: Context) {
        try {
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_GUARD_ACTIVITY_JSON, null) ?: return
            val list = json.decodeFromString<List<GuardActivityEntry>>(raw).take(GUARD_ACTIVITY_CAP)
            synchronized(guardActivityLock) {
                guardActivityBuffer.clear()
                guardActivityBuffer.addAll(list)
            }
            _guardActivity.value = list.asReversed()
        } catch (_: Throwable) {}
    }

    fun clearGuardActivity(context: Context) {
        try {
            synchronized(guardActivityLock) { guardActivityBuffer.clear() }
            _guardActivity.value = emptyList()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_GUARD_ACTIVITY_JSON).apply()
        } catch (_: Throwable) {}
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun resolveValidUserId(context: Context): String {
        val uid = SupabaseService.getInstance().getCurrentUserId()
            ?: AndroidPreferenceSessionManager.getStoredUserId(context)
        if (!uid.isNullOrBlank()) {
            try {
                java.util.UUID.fromString(uid)
                return uid
            } catch (_: Exception) {
                return java.util.UUID.nameUUIDFromBytes(uid.toByteArray()).toString()
            }
        }
        return java.util.UUID.nameUUIDFromBytes("local_student_device".toByteArray()).toString()
    }

    private fun getIsoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
