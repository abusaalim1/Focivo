package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

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
    private var graceObservationJob: Job? = null

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
        updateGuardStatusNotification(context)
    }

    fun isAiGuardEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AI_GUARD_ENABLED, true)
    }

    fun setAiGuardEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AI_GUARD_ENABLED, enabled).apply()
        updateGuardStatusNotification(context)
    }

    /**
     * Evaluates in real-time whether current wall-clock time is an active study period:
     * 1. Active Quick Focus / manual focus timer running
     * 2. Inside an active Auto Study Schedule window (checking start_time, end_time, active day, break window)
     * 3. Focus Shield otherwise actively armed
     */
    fun isStudyPeriodActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Manual timer running
        val isTimerRunning = prefs.getBoolean("is_manual_timer_running", false)
        val timerEndTime = prefs.getLong("manual_timer_end_time_ms", 0L)
        val isTimerActive = isTimerRunning && (timerEndTime > System.currentTimeMillis())

        // 2. Active Auto Study Schedule window
        val isScheduleActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(context)

        // 3. Focus Shield running (standalone or punishment)
        val isShieldRunning = FocusShieldService.isShieldRunning(context)

        return isTimerActive || isScheduleActive || isShieldRunning
    }

    fun updateGuardStatusNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (!isAiGuardEnabled(context)) {
            manager.cancel(NOTIFICATION_ID_GUARD_STATUS)
            return
        }

        val isArmed = isStudyPeriodActive(context)
        val title = if (isArmed) "🛡️ AI Study Guard Armed" else "🟢 AI Study Guard Idle (Free Time)"
        val text = if (isArmed) {
            "Monitoring Claude & ChatGPT for study focus during active study time"
        } else {
            "Claude & ChatGPT unrestricted — Guard only monitors during active study time"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_GUARD_STATUS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(isArmed)
            .setPriority(if (isArmed) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setSound(null)
            .setContentIntent(pi)

        manager.notify(NOTIFICATION_ID_GUARD_STATUS, builder.build())
    }

    fun isAppUnderPunishment(context: Context, packageName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endTime = prefs.getLong("$KEY_PUNISHMENT_PREFIX$packageName", 0L)
        return endTime > System.currentTimeMillis()
    }

    fun getPunishmentRemainingSeconds(context: Context, packageName: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endTime = prefs.getLong("$KEY_PUNISHMENT_PREFIX$packageName", 0L)
        val diff = (endTime - System.currentTimeMillis()) / 1000L
        return if (diff > 0) diff.toInt() else 0
    }

    private var nonStudyFirstDetectedMs = 0L
    private var currentMonitoredPackage: String? = null

    fun resetSessionCounters() {
        nonStudyFirstDetectedMs = 0L
        currentMonitoredPackage = null
    }

    fun onStudyActivityDetected(context: Context, packageName: String) {
        if (currentMonitoredPackage == packageName) {
            Log.d(TAG, "[AiGuard] Educational study activity confirmed in $packageName — resetting non-study timer")
            nonStudyFirstDetectedMs = 0L
            currentMonitoredPackage = null
        }
        val cur = _activeWarning.value
        if (cur != null && cur.packageName == packageName) {
            Log.d(TAG, "Study activity detected for ${cur.appName}; resolving warning early")
            triggerStage3Resolved(context, cur, "Study content detected on screen")
        }
    }

    /**
     * Entry point for detected non-study activity in ChatGPT, Claude, or Chrome.
     * Evaluates continuous 35-second observation window (30-40 sec range):
     * - Requires non-study activity to persist for 35 seconds before triggering Stage 1 Warning
     * - Prevents 1-second notification spam completely
     * - Stage 1: Shows ONE warning popup + ONE high-priority notification
     * - Subsequent non-study activity during grace window: Triggers STAGE 2 (3-Hour BLOCK)
     */
    fun onNonStudyDetected(context: Context, packageName: String, reason: String, textHash: Int = 0) {
        if (!isAiGuardEnabled(context)) return
        if (!isStudyPeriodActive(context)) {
            Log.d(TAG, "Non-study detected in $packageName during free/unscheduled time — ignoring")
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
            return
        }

        val now = System.currentTimeMillis()

        if (currentMonitoredPackage != packageName || nonStudyFirstDetectedMs == 0L) {
            currentMonitoredPackage = packageName
            nonStudyFirstDetectedMs = now
        }

        val currentWarning = _activeWarning.value

        if (currentWarning != null && currentWarning.packageName == packageName) {
            // User continued off-topic messaging during warning window! Trigger STAGE 2 BLOCK!
            val warningTimeMs = currentWarning.warnedAtMillis
            if (now - warningTimeMs > 8000L) {
                Log.w(TAG, "Stage 2 triggered: Non-study activity repeated after warning for $appName")
                nonStudyFirstDetectedMs = 0L
                currentMonitoredPackage = null
                triggerStage2Block(context, currentWarning, reason)
            }
        } else {
            val elapsedSec = (now - nonStudyFirstDetectedMs) / 1000L
            Log.w(TAG, "[AiGuard] Non-study usage duration: ${elapsedSec}s for $appName (Threshold: 35s)")

            if (elapsedSec >= 35L) {
                Log.w(TAG, "Stage 1 triggered: Sustained 35s non-study usage for $appName -> Showing Warning")
                nonStudyFirstDetectedMs = 0L
                currentMonitoredPackage = null
                triggerStage1Warning(context, packageName, appName, reason)
            }
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
        val nowIso = Instant.now().toString()
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

        _activeWarning.value = warning

        // Show High-Priority Heads-up Notification
        showWarningNotification(context, warning)

        // Log to Supabase and Local Storage
        val logDto = SupabasePunishmentLogDto(
            id = logId,
            user_id = SupabaseService.getInstance().getCurrentUserId() ?: "anonymous",
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
        }
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
        graceObservationJob?.cancel()
        _activeWarning.value = null

        val lockDurationMinutes = 180
        val lockDurationSeconds = lockDurationMinutes * 60
        val nowMillis = System.currentTimeMillis()
        val nowIso = Instant.now().toString()
        val lockEndTime = nowMillis + (lockDurationSeconds * 1000L)

        // Enforce lock in preferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("$KEY_PUNISHMENT_PREFIX${warning.packageName}", lockEndTime).apply()

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
        val logDto = SupabasePunishmentLogDto(
            id = warning.logId,
            user_id = SupabaseService.getInstance().getCurrentUserId() ?: "anonymous",
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
        graceObservationJob?.cancel()
        _activeWarning.value = null
        nonStudyFirstDetectedMs = 0L
        currentMonitoredPackage = null

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
        val logDto = SupabasePunishmentLogDto(
            id = warning.logId,
            user_id = SupabaseService.getInstance().getCurrentUserId() ?: "anonymous",
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ Study Discipline Warning: ${warning.appName}")
            .setContentText("Casual/non-study activity detected. Return to studying within 3 min or this app locks for 3 hours!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Non-study usage detected in ${warning.appName}.\n\nReason: ${warning.reason}\n\n⚠️ Continuing off-topic activity will trigger an immediate 3-HOUR LOCKOUT on ${warning.appName}. Return to your study goals now!")
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⛔ ${block.appName} Blocked for 3 Hours")
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

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("✅ Good — No Block Applied")
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
        return try {
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
                    componentName.endsWith("AiStudyAccessibilityService", ignoreCase = true)
                ) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
