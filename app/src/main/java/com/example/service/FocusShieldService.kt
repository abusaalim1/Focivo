package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusShieldService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var monitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var violationCount = 0

    companion object {
        const val CHANNEL_ID = "focus_shield_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START_SHIELD = "com.example.action.START_SHIELD"
        const val ACTION_STOP_SHIELD = "com.example.action.STOP_SHIELD"
        const val ACTION_INTERCEPT_BLOCKED_APP = "com.example.action.INTERCEPT_BLOCKED_APP"
        const val ACTION_START_SCHEDULED_BLOCK = "com.example.action.START_SCHEDULED_BLOCK"

        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCKED_NAME = "extra_blocked_name"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_BLOCKED_LIST = "extra_blocked_list"
        const val EXTRA_IS_PUNISHMENT = "extra_is_punishment"
        const val EXTRA_PUNISHMENT_REASON = "extra_punishment_reason"

        const val PREFS_NAME = "regain_shield_prefs"
        const val PREF_KEY_SHIELD_ACTIVE = "is_shield_active"
        const val PREF_KEY_SHIELD_END_TIME = "shield_end_time"
        const val PREF_KEY_BLOCKED_LIST = "blocked_packages"
        const val PREF_KEY_IS_PUNISHMENT = "is_punishment_active"
        const val PREF_KEY_PUNISHED_PACKAGE = "punished_package_target"

        private val _isShieldActive = MutableStateFlow(false)
        val isShieldActive: StateFlow<Boolean> = _isShieldActive.asStateFlow()

        private val _lastInterceptedPackage = MutableStateFlow<String?>(null)
        val lastInterceptedPackage: StateFlow<String?> = _lastInterceptedPackage.asStateFlow()

        private val _shieldRemainingSeconds = MutableStateFlow(0)
        val shieldRemainingSeconds: StateFlow<Int> = _shieldRemainingSeconds.asStateFlow()

        private val _isPunishmentLock = MutableStateFlow(false)
        val isPunishmentLock: StateFlow<Boolean> = _isPunishmentLock.asStateFlow()

        private val _punishedPackageTarget = MutableStateFlow<String?>(null)
        val punishedPackageTarget: StateFlow<String?> = _punishedPackageTarget.asStateFlow()

        fun isShieldRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val endTime = prefs.getLong(PREF_KEY_SHIELD_END_TIME, 0L)
            val isScheduled = ScheduledBlockScheduler.isScheduleCurrentlyActive(context)
            val isPunishment = _isPunishmentLock.value || prefs.getBoolean(PREF_KEY_IS_PUNISHMENT, false)
            return (_isShieldActive.value || (endTime > System.currentTimeMillis()) || isScheduled) && !isPunishment
        }

        fun getActiveBlockedPackages(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val blockedRaw = prefs.getString(PREF_KEY_BLOCKED_LIST, "") ?: ""
            val baseList = blockedRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            val scheduled = if (ScheduledBlockScheduler.isScheduleCurrentlyActive(context)) {
                ScheduledBlockScheduler.getCurrentlyActiveBlockedPackages(context)
            } else emptySet()
            return com.example.util.EssentialAppsGuard.sanitizeBlockedPackages(context, baseList + scheduled)
        }

        fun getRemainingSeconds(context: Context): Int {
            if (_shieldRemainingSeconds.value > 0) return _shieldRemainingSeconds.value
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val endTime = prefs.getLong(PREF_KEY_SHIELD_END_TIME, 0L)
            val diff = (endTime - System.currentTimeMillis()) / 1000L
            return if (diff > 0) diff.toInt() else 0
        }

        fun getForegroundPackage(usageStatsManager: android.app.usage.UsageStatsManager): String? {
            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(now - 4000L, now)
            val event = android.app.usage.UsageEvents.Event()
            var latestPackage: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    latestPackage = event.packageName
                }
            }
            return latestPackage
        }

        fun friendlyAppName(context: Context, packageName: String): String {
            return try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                when {
                    packageName.contains("instagram") -> "Instagram & Reels"
                    packageName.contains("youtube") -> "YouTube & Shorts"
                    packageName.contains("musically") || packageName.contains("tiktok") -> "TikTok"
                    packageName.contains("twitter") || packageName.contains("com.twitter") -> "X (Twitter)"
                    packageName.contains("facebook") -> "Facebook"
                    packageName.contains("snapchat") -> "Snapchat"
                    packageName.contains("reddit") -> "Reddit"
                    packageName.contains("pubg") || packageName.contains("imobile") -> "BGMI / PUBG"
                    packageName.contains("freefire") -> "Free Fire"
                    else -> "Distracting App"
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Focusly:ShieldLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SHIELD) {
            stopShield()
            stopSelf()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Handle OS restart after process kill
        if (intent == null) {
            val isScheduledActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(this)
            val isShieldActiveInPrefs = prefs.getBoolean(PREF_KEY_SHIELD_ACTIVE, false)
            val savedEndTime = prefs.getLong(PREF_KEY_SHIELD_END_TIME, 0L)
            val remainingTimer = ((savedEndTime - System.currentTimeMillis()) / 1000L).toInt()

            if (isScheduledActive || isShieldActiveInPrefs || remainingTimer > 0) {
                val isPunishment = prefs.getBoolean(PREF_KEY_IS_PUNISHMENT, false)
                val punishedPkg = prefs.getString(PREF_KEY_PUNISHED_PACKAGE, null)
                val blockedListRaw = prefs.getString(PREF_KEY_BLOCKED_LIST, "") ?: ""
                val scheduledPackages = if (isScheduledActive) ScheduledBlockScheduler.getCurrentlyActiveBlockedPackages(this) else emptySet()
                val configuredPackages = blockedListRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                val allBlockedPackages = com.example.util.EssentialAppsGuard.sanitizeBlockedPackages(
                    this,
                    scheduledPackages + configuredPackages
                )

                val effectiveSec = if (remainingTimer > 0) remainingTimer else 24 * 60 * 60
                _isPunishmentLock.value = isPunishment
                _punishedPackageTarget.value = punishedPkg
                Log.d("FocusShieldService", "[ProcessRestart] Restoring shield for $effectiveSec seconds on packages: $allBlockedPackages")
                safeStartForeground(NOTIFICATION_ID, buildNotification(effectiveSec, isPunishment, punishedPkg))
                startMonitoring(effectiveSec, allBlockedPackages, isPunishment, punishedPkg)
                return START_STICKY
            } else {
                Log.d("FocusShieldService", "[ProcessRestart] No active schedule or shield on restart; stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val isScheduledActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(this)
        val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 25 * 60)
        val isPunishment = intent.getBooleanExtra(EXTRA_IS_PUNISHMENT, false)
        val blockedListRaw = intent.getStringExtra(EXTRA_BLOCKED_LIST) ?: ""

        val punishedPackage: String?
        val blockedPackages: Set<String>

        if (isPunishment) {
            punishedPackage = blockedListRaw.split(",").firstOrNull { it.isNotBlank() }
            blockedPackages = emptySet()
            _punishedPackageTarget.value = punishedPackage
            prefs.edit().putString(PREF_KEY_PUNISHED_PACKAGE, punishedPackage ?: "").apply()
        } else {
            punishedPackage = null
            _punishedPackageTarget.value = null
            val schedulePackages = if (isScheduledActive) ScheduledBlockScheduler.getCurrentlyActiveBlockedPackages(this) else emptySet()
            val intentPackages = blockedListRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            blockedPackages = (intentPackages + schedulePackages).ifEmpty {
                setOf("com.instagram.android", "com.zhiliaoapp.musically", "com.twitter.android", "com.facebook.katana", "com.snapchat.android", "com.reddit.frontpage")
            }
        }

        _isPunishmentLock.value = isPunishment
        safeStartForeground(NOTIFICATION_ID, buildNotification(durationSeconds, isPunishment, punishedPackage))
        startMonitoring(durationSeconds, blockedPackages, isPunishment, punishedPackage)

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endTime = prefs.getLong(PREF_KEY_SHIELD_END_TIME, 0L)
        val isScheduleActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(this)
        if (endTime > System.currentTimeMillis() || isScheduleActive) {
            val remaining = if (isScheduleActive) {
                ScheduledBlockScheduler.getSecondsUntilNextStateChange(this)
            } else {
                ((endTime - System.currentTimeMillis()) / 1000L).toInt()
            }
            val isPunishment = prefs.getBoolean(PREF_KEY_IS_PUNISHMENT, false)
            val punishedPkg = prefs.getString(PREF_KEY_PUNISHED_PACKAGE, null)
            val restartIntent = Intent(applicationContext, FocusShieldService::class.java).apply {
                action = ACTION_START_SHIELD
                putExtra(EXTRA_DURATION_SECONDS, remaining)
                putExtra(EXTRA_IS_PUNISHMENT, isPunishment)
                putExtra(EXTRA_BLOCKED_LIST, if (isPunishment) punishedPkg else prefs.getString(PREF_KEY_BLOCKED_LIST, ""))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        }
    }

    private fun safeStartForeground(notificationId: Int, notification: Notification) {
        try {
            createNotificationChannel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    notificationId,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Throwable) {
            Log.e("FocusShieldService", "safeStartForeground with type failed: ${e.message}; attempting fallback", e)
            try {
                startForeground(notificationId, notification)
            } catch (fallbackEx: Throwable) {
                Log.e("FocusShieldService", "Foreground service fallback failed: ${fallbackEx.message}", fallbackEx)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Shield Blocker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Distraction blocker active during focus sessions"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(remainingSec: Int, isPunishment: Boolean = false, punishedPkg: String? = null): Notification {
        val hours = remainingSec / 3600
        val mins = (remainingSec % 3600) / 60
        val secs = remainingSec % 60
        val timeFormatted = if (hours > 0) {
            String.format("%dh %02dm", hours, mins)
        } else {
            String.format("%02d:%02d", mins, secs)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isPunishment) {
            val appTitle = punishedPkg?.let { friendlyAppName(this, it) } ?: "AI App"
            "⚠️ 3-Hour Discipline Lock: $appTitle · $timeFormatted Left"
        } else {
            "🛡️ Focus Shield Active · $timeFormatted Left"
        }

        val text = if (isPunishment) {
            val appTitle = punishedPkg?.let { friendlyAppName(this, it) } ?: "App"
            "$appTitle is locked due to non-study usage. All other apps remain unlocked."
        } else {
            "Blocking distracting apps during study focus. Academic study tools allowed."
        }

        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_notification_large)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun isPhoneCallInProgress(): Boolean {
        return try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            mode == AudioManager.MODE_RINGTONE ||
            mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {
            false
        }
    }

    private fun isSettingsOrPackageInstallerApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.android.settings" ||
               lower == "com.google.android.packageinstaller" ||
               lower == "com.android.packageinstaller" ||
               lower.contains("packageinstaller") ||
               lower.contains("securitycenter") ||
               lower.contains("uninstaller") ||
               lower.contains("appmanager")
    }

    private fun isTelecomOrSystemApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("dialer") ||
               lower.contains("phone") ||
               lower.contains("incall") ||
               lower.contains("telecom") ||
               lower.contains("contacts") ||
               lower.contains("call") ||
               lower.startsWith("com.android.server.telecom") ||
               lower.startsWith("com.google.android.dialer") ||
               lower.startsWith("com.samsung.android.incallui")
    }

    private fun isPermittedEducationalApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.openai.chatgpt" ||
               lower == "com.anthropic.claude" ||
               lower.contains("duolingo") ||
               lower.contains("khanacademy") ||
               lower.contains("coursera") ||
               lower.contains("unacademy") ||
               lower.contains("physicswallah")
    }

    private fun startMonitoring(
        totalSeconds: Int,
        blockedPackages: Set<String>,
        isPunishment: Boolean = false,
        punishedPackage: String? = null
    ) {
        monitorJob?.cancel()
        _isShieldActive.value = true
        _shieldRemainingSeconds.value = totalSeconds
        _isPunishmentLock.value = isPunishment
        _punishedPackageTarget.value = punishedPackage

        val endTime = System.currentTimeMillis() + (totalSeconds * 1000L)
        com.example.util.StudyNotificationBlockerManager.activateStudyNotificationBlock(this)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val edit = prefs.edit()
            .putBoolean(PREF_KEY_SHIELD_ACTIVE, true)
            .putLong(PREF_KEY_SHIELD_END_TIME, endTime)
            .putBoolean(PREF_KEY_IS_PUNISHMENT, isPunishment)

        if (isPunishment) {
            edit.putString(PREF_KEY_PUNISHED_PACKAGE, punishedPackage ?: "")
        } else {
            val sanitized = com.example.util.EssentialAppsGuard.sanitizeBlockedPackages(this, blockedPackages)
            edit.putString(PREF_KEY_BLOCKED_LIST, sanitized.joinToString(","))
        }
        edit.apply()

        try {
            wakeLock?.acquire(totalSeconds * 1000L + 5000L)
        } catch (_: Exception) {}

        monitorJob = serviceScope.launch {
            var secondsLeft = totalSeconds
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            var lastInterceptTime = 0L

            while (isActive) {
                delay(1000L)

                val isScheduled = ScheduledBlockScheduler.isScheduleCurrentlyActive(this@FocusShieldService)
                if (isScheduled) {
                    val remainingScheduleSec = ScheduledBlockScheduler.getSecondsUntilNextStateChange(this@FocusShieldService)
                    secondsLeft = remainingScheduleSec
                } else {
                    secondsLeft -= 1
                }

                _shieldRemainingSeconds.value = secondsLeft.coerceAtLeast(0)

                if (secondsLeft <= 0 && !isScheduled) {
                    break
                }

                // Update notification periodically
                if (secondsLeft % 15 == 0) {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, buildNotification(secondsLeft, isPunishment, punishedPackage))
                }

                // Check foreground app (reduced delay to 800ms for immediate reaction)
                if (usageStatsManager != null && System.currentTimeMillis() - lastInterceptTime > 800L) {
                    // NEVER block if phone call is active or ringing!
                    if (isPhoneCallInProgress()) {
                        continue
                    }

                    val foregroundPackage = getForegroundPackage(usageStatsManager)
                    if (foregroundPackage != null && foregroundPackage != packageName) {
                        // PERMANENTLY PROTECTED: Phone, Contacts, Camera, Messages/SMS, Settings, Telecom
                        if (com.example.util.EssentialAppsGuard.isEssentialApp(this@FocusShieldService, foregroundPackage)) {
                            continue
                        }

                        // Whitelist incoming calls, telecom, dialer, system phone
                        if (isTelecomOrSystemApp(foregroundPackage)) {
                            continue
                        }

                        // Prevent uninstallation and opening Settings during active schedule or shield (Strict mode only, never on punishment)
                        val isStrictEnabled = com.example.util.StrictModeManager.isStrictModeEnabled(this@FocusShieldService)
                        if (!isPunishment && (_isShieldActive.value || isScheduled) && isStrictEnabled && isSettingsOrPackageInstallerApp(foregroundPackage)) {
                            val now = System.currentTimeMillis()
                            if (now - lastInterceptTime > 3000L) {
                                lastInterceptTime = now
                                _lastInterceptedPackage.value = foregroundPackage
                                violationCount++
                                interceptDistraction(
                                    blockedPackage = foregroundPackage,
                                    remainingSec = secondsLeft,
                                    isPunishment = false,
                                    reason = "Settings & App Management are protected during your active study session."
                                )
                            }
                            continue
                        }

                        // Check if specific app is under AI Study Punishment Lock
                        val isForegroundPunished = (!punishedPackage.isNullOrBlank() && foregroundPackage == punishedPackage) ||
                                com.example.util.AiStudyGuardManager.isAppUnderPunishment(this@FocusShieldService, foregroundPackage)

                        if (isForegroundPunished) {
                            val now = System.currentTimeMillis()
                            if (now - lastInterceptTime > 2500L) {
                                lastInterceptTime = now
                                _lastInterceptedPackage.value = foregroundPackage
                                val remainingLock = com.example.util.AiStudyGuardManager.getPunishmentRemainingSeconds(this@FocusShieldService, foregroundPackage)
                                val effectiveRemaining = if (remainingLock > 0) remainingLock else secondsLeft
                                val appTitle = friendlyAppName(this@FocusShieldService, foregroundPackage)
                                interceptDistraction(
                                    blockedPackage = foregroundPackage,
                                    remainingSec = effectiveRemaining,
                                    isPunishment = true,
                                    reason = "3-Hour Penalty: $appTitle is locked for non-study conversation. Other apps remain unlocked."
                                )
                            }
                            continue
                        }

                        // If this service was triggered purely as an AI Punishment lock, DO NOT block any other app!
                        if (isPunishment) {
                            continue
                        }

                        // Whitelist educational apps (ChatGPT, Claude) during general focus shield
                        if (isPermittedEducationalApp(foregroundPackage)) {
                            continue
                        }

                        // General distraction blocking check (includes both configured list and active schedule list)
                        val activeScheduleBlocked = ScheduledBlockScheduler.getCurrentlyActiveBlockedPackages(this@FocusShieldService)
                        val effectiveBlockedPackages = blockedPackages + activeScheduleBlocked

                        val isBlocked = effectiveBlockedPackages.any { blocked ->
                            foregroundPackage.equals(blocked, ignoreCase = true) ||
                                foregroundPackage.startsWith(blocked, ignoreCase = true)
                        }

                        if (isBlocked) {
                            val now = System.currentTimeMillis()
                            if (now - lastInterceptTime > 2500L) {
                                lastInterceptTime = now
                                _lastInterceptedPackage.value = foregroundPackage
                                violationCount++
                                interceptDistraction(foregroundPackage, secondsLeft)
                            }
                        }
                    }
                }
            }

            // Time finished! Unlock all apps
            onShieldTimerComplete()
        }
    }

    private fun getForegroundPackage(usageStatsManager: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - 4000L, now)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestPackage = event.packageName
            }
        }
        return latestPackage
    }

    private var lastInterceptPkg: String? = null
    private var lastInterceptTimestamp: Long = 0L

    private fun interceptDistraction(
        blockedPackage: String,
        remainingSec: Int,
        isPunishment: Boolean = false,
        reason: String? = null
    ) {
        val now = System.currentTimeMillis()
        if (com.example.ui.screens.BlockedAppLockActivity.isCurrentlyShowing && blockedPackage == lastInterceptPkg && (now - lastInterceptTimestamp) < 1500L) {
            return
        }
        lastInterceptPkg = blockedPackage
        lastInterceptTimestamp = now

        val friendlyName = friendlyAppName(this, blockedPackage)
        val interceptIntent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
            context = this,
            packageName = blockedPackage,
            appName = friendlyName,
            durationSec = remainingSec,
            reason = reason,
            isPunishment = isPunishment
        )
        try {
            startActivity(interceptIntent)
        } catch (e: Exception) {
            Log.e("FocusShieldService", "Failed to launch BlockedAppLockActivity: ${e.message}")
        }
    }

    private fun onShieldTimerComplete() {
        _isShieldActive.value = false
        _shieldRemainingSeconds.value = 0
        _isPunishmentLock.value = false
        _punishedPackageTarget.value = null
        violationCount = 0

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(PREF_KEY_SHIELD_ACTIVE, false)
            .putLong(PREF_KEY_SHIELD_END_TIME, 0L)
            .putBoolean(PREF_KEY_IS_PUNISHMENT, false)
            .putString(PREF_KEY_PUNISHED_PACKAGE, "")
            .apply()

        val finishIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            finishIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_notification_large)
        val completeNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(largeIcon)
            .setContentTitle("🎉 Unbroken Focus Achieved!")
            .setContentText("Focus Shield timer finished. Selected apps are now unlocked.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID + 1, completeNotification)

        stopShield()
        stopSelf()
    }

    private fun stopShield() {
        com.example.util.StudyNotificationBlockerManager.deactivateStudyNotificationBlock(this)
        monitorJob?.cancel()
        monitorJob = null
        _isShieldActive.value = false
        _shieldRemainingSeconds.value = 0
        _isPunishmentLock.value = false
        _punishedPackageTarget.value = null

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(PREF_KEY_SHIELD_ACTIVE, false)
            .putLong(PREF_KEY_SHIELD_END_TIME, 0L)
            .putBoolean(PREF_KEY_IS_PUNISHMENT, false)
            .putString(PREF_KEY_PUNISHED_PACKAGE, "")
            .apply()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endTime = prefs.getLong(PREF_KEY_SHIELD_END_TIME, 0L)
        if (endTime <= System.currentTimeMillis()) {
            stopShield()
        }
    }
}
