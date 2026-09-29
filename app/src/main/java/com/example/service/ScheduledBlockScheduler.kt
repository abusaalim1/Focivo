package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.SupabaseManager
import com.example.data.SupabaseScheduledBlockDto
import com.example.data.SupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar

object ScheduledBlockScheduler {
    private const val TAG = "ScheduledBlockScheduler"
    private const val PREFS_NAME = "focusly_local_data"
    private const val KEY_SCHEDULES_JSON = "custom_scheduled_blocks_json"
    private const val ALARM_REQUEST_CODE = 2001

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun getLocalSchedules(context: Context): List<SupabaseScheduledBlockDto> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SCHEDULES_JSON, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SupabaseScheduledBlockDto>>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse local schedules: ${e.message}")
            emptyList()
        }
    }

    fun saveLocalSchedules(context: Context, schedules: List<SupabaseScheduledBlockDto>) {
        try {
            val encoded = json.encodeToString(schedules)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SCHEDULES_JSON, encoded).commit()
            Log.d(TAG, "Saved ${schedules.size} schedules locally")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize schedules: ${e.message}")
        }
        evaluateAndReschedule(context)
    }

    fun upsertScheduleLocalAndRemote(context: Context, schedule: SupabaseScheduledBlockDto, triggerSource: String = "USER_SAVE") {
        val uid = SupabaseService.getInstance().getCurrentUserId() ?: ""
        val fixedSchedule = if ((schedule.user_id.isBlank() || schedule.user_id == "anonymous" || schedule.user_id == "local_user") && uid.isNotBlank()) {
            schedule.copy(user_id = uid)
        } else {
            schedule
        }

        Log.i(TAG, "[ScheduledBlockWrite] [$triggerSource] Upserting schedule id=${fixedSchedule.id}, label='${fixedSchedule.label}', is_enabled=${fixedSchedule.is_enabled}, user_id=${fixedSchedule.user_id}")

        val current = getLocalSchedules(context).toMutableList()
        val index = current.indexOfFirst { it.id == fixedSchedule.id }
        if (index >= 0) {
            current[index] = fixedSchedule
        } else {
            current.add(0, fixedSchedule)
        }
        saveLocalSchedules(context, current)

        // Sync with Supabase
        if (fixedSchedule.user_id.isNotBlank() && fixedSchedule.user_id != "anonymous" && fixedSchedule.user_id != "local_user") {
            ioScope.launch {
                try {
                    val res = SupabaseService.getInstance().upsertScheduledBlock(fixedSchedule)
                    if (res.isSuccess) {
                        Log.i(TAG, "[ScheduledBlockWrite] Successfully synced upsert to Supabase for id=${fixedSchedule.id}, is_enabled=${fixedSchedule.is_enabled}")
                    } else {
                        Log.e(TAG, "[ScheduledBlockWrite] Failed to sync upsert to Supabase for id=${fixedSchedule.id}: ${res.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[ScheduledBlockWrite] Supabase upsert error: ${e.message}")
                }
            }
        } else {
            Log.w(TAG, "[ScheduledBlockWrite] Skipping remote Supabase sync: user_id is '${fixedSchedule.user_id}'")
        }
    }

    fun deleteScheduleLocalAndRemote(context: Context, scheduleId: String, triggerSource: String = "USER_DELETE") {
        Log.i(TAG, "[ScheduledBlockWrite] [$triggerSource] Deleting schedule id=$scheduleId")
        val current = getLocalSchedules(context).filterNot { it.id == scheduleId }
        saveLocalSchedules(context, current)

        ioScope.launch {
            try {
                val res = SupabaseService.getInstance().deleteScheduledBlock(scheduleId)
                if (res.isSuccess) {
                    Log.i(TAG, "[ScheduledBlockWrite] Successfully synced deletion to Supabase for id=$scheduleId")
                } else {
                    Log.e(TAG, "[ScheduledBlockWrite] Failed to sync deletion to Supabase for id=$scheduleId: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[ScheduledBlockWrite] Supabase delete error: ${e.message}")
            }
        }
    }

    fun toggleSchedule(context: Context, scheduleId: String, isEnabled: Boolean, triggerSource: String = "USER_TOGGLE") {
        val uid = SupabaseService.getInstance().getCurrentUserId() ?: ""
        val current = getLocalSchedules(context).toMutableList()
        val index = current.indexOfFirst { it.id == scheduleId }
        if (index >= 0) {
            val oldItem = current[index]
            val finalUserId = if ((oldItem.user_id.isBlank() || oldItem.user_id == "anonymous" || oldItem.user_id == "local_user") && uid.isNotBlank()) uid else oldItem.user_id

            val updated = oldItem.copy(is_enabled = isEnabled, user_id = finalUserId)
            current[index] = updated
            saveLocalSchedules(context, current)

            Log.i(TAG, "[ScheduledBlockWrite] [$triggerSource] Toggled schedule id=$scheduleId from is_enabled=${oldItem.is_enabled} -> $isEnabled, user_id=$finalUserId")

            if (finalUserId.isNotBlank() && finalUserId != "anonymous" && finalUserId != "local_user") {
                ioScope.launch {
                    try {
                        val res = SupabaseService.getInstance().upsertScheduledBlock(updated)
                        if (res.isSuccess) {
                            Log.i(TAG, "[ScheduledBlockWrite] Successfully synced toggle to Supabase for id=$scheduleId, is_enabled=$isEnabled")
                        } else {
                            Log.e(TAG, "[ScheduledBlockWrite] Failed to sync toggle to Supabase for id=$scheduleId: ${res.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[ScheduledBlockWrite] Supabase toggle error: ${e.message}")
                    }
                }
            } else {
                Log.w(TAG, "[ScheduledBlockWrite] Skipping remote Supabase toggle sync: user_id is '$finalUserId'")
            }
        } else {
            Log.w(TAG, "[ScheduledBlockWrite] toggleSchedule failed: scheduleId $scheduleId not found in local cache")
        }
    }

    fun syncFromRemote(context: Context, remoteList: List<SupabaseScheduledBlockDto>): List<SupabaseScheduledBlockDto> {
        val local = getLocalSchedules(context)
        Log.d(TAG, "[ScheduledBlockSync] Syncing from remote: remoteCount=${remoteList.size}, localCount=${local.size}")

        if (remoteList.isEmpty()) {
            val uid = SupabaseService.getInstance().getCurrentUserId()
            if (!uid.isNullOrBlank() && local.isNotEmpty()) {
                Log.i(TAG, "[ScheduledBlockSync] Remote is empty, pushing ${local.size} local schedules to Supabase for uid=$uid")
                local.forEach { loc ->
                    val toPush = if (loc.user_id.isBlank() || loc.user_id == "anonymous") loc.copy(user_id = uid) else loc
                    upsertScheduleLocalAndRemote(context, toPush, triggerSource = "REMOTE_EMPTY_LOCAL_PUSH")
                }
            }
            return local
        }

        val mergedMap = remoteList.associateBy { it.id }.toMutableMap()

        local.forEach { loc ->
            val remoteItem = mergedMap[loc.id]
            if (remoteItem != null) {
                if (loc.is_enabled != remoteItem.is_enabled) {
                    Log.i(TAG, "[ScheduledBlockSync] Schedule id=${loc.id} local is_enabled=${loc.is_enabled} differs from remote is_enabled=${remoteItem.is_enabled}. Preserving local toggled state and pushing to remote.")
                    val updated = remoteItem.copy(is_enabled = loc.is_enabled)
                    mergedMap[loc.id] = updated
                    ioScope.launch {
                        SupabaseService.getInstance().upsertScheduledBlock(updated)
                    }
                }
            } else {
                mergedMap[loc.id] = loc
                val uid = SupabaseService.getInstance().getCurrentUserId()
                if (!uid.isNullOrBlank()) {
                    val toPush = if (loc.user_id.isBlank() || loc.user_id == "anonymous") loc.copy(user_id = uid) else loc
                    ioScope.launch {
                        SupabaseService.getInstance().upsertScheduledBlock(toPush)
                    }
                }
            }
        }

        val finalList = mergedMap.values.toList()
        saveLocalSchedules(context, finalList)
        return finalList
    }

    fun clearLocalSchedules(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SCHEDULES_JSON).apply()
        cancelAlarms(context)
        val shieldIntent = Intent(context, FocusShieldService::class.java).apply {
            action = FocusShieldService.ACTION_STOP_SHIELD
        }
        try { context.stopService(shieldIntent) } catch (_: Exception) {}
        evaluateAndReschedule(context)
    }

    /**
     * Checks whether a specific schedule is actively blocking at the given time.
     * Honors active days, start/end time window, and break window if configured.
     */
    fun isScheduleActiveAt(schedule: SupabaseScheduledBlockDto, calendar: Calendar): Boolean {
        if (!schedule.is_enabled) return false

        val dayStr = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            Calendar.SUNDAY -> "Sun"
            else -> ""
        }

        val activeDays = schedule.days_active.split(",").map { it.trim() }
        if (!activeDays.contains(dayStr)) {
            return false
        }

        val currentMin = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startMin = parseTimeToMinutes(schedule.start_time)
        val endMin = parseTimeToMinutes(schedule.end_time)

        if (startMin == endMin) return false

        val inMainBlock = if (endMin > startMin) {
            currentMin in startMin until endMin
        } else {
            // Overnight window
            currentMin >= startMin || currentMin < endMin
        }

        if (!inMainBlock) return false

        // Check if currently inside break window
        if (!schedule.break_start_time.isNullOrBlank() && !schedule.break_end_time.isNullOrBlank()) {
            val bStart = parseTimeToMinutes(schedule.break_start_time)
            val bEnd = parseTimeToMinutes(schedule.break_end_time)
            if (bStart != bEnd) {
                val inBreak = if (bEnd > bStart) {
                    currentMin in bStart until bEnd
                } else {
                    currentMin >= bStart || currentMin < bEnd
                }
                if (inBreak) {
                    // Block is temporarily lifted for break
                    return false
                }
            }
        }

        return true
    }

    fun isAnyScheduleCurrentlyActive(context: Context): Boolean {
        val schedules = getLocalSchedules(context)
        val now = Calendar.getInstance()
        return schedules.any { isScheduleActiveAt(it, now) }
    }

    fun isScheduleCurrentlyActive(context: Context): Boolean {
        return isAnyScheduleCurrentlyActive(context)
    }

    /**
     * Returns the currently active schedule if its label/mode represents a study window, null otherwise.
     */
    fun getActiveStudySchedule(context: Context): SupabaseScheduledBlockDto? {
        val schedules = getLocalSchedules(context).filter { it.is_enabled }
        val now = Calendar.getInstance()
        return schedules.firstOrNull { isScheduleActiveAt(it, now) }
    }

    /**
     * Calculates remaining seconds in the currently active block window
     * (counting down to break start time if set and before break, else to end time).
     */
    fun getSecondsRemainingInActiveBlock(context: Context, schedule: SupabaseScheduledBlockDto): Int {
        val now = Calendar.getInstance()
        if (!isScheduleActiveAt(schedule, now)) return 0

        val currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val currentSec = now.get(Calendar.SECOND)

        var targetMin = parseTimeToMinutes(schedule.end_time)
        if (!schedule.break_start_time.isNullOrBlank() && !schedule.break_end_time.isNullOrBlank()) {
            val bStart = parseTimeToMinutes(schedule.break_start_time)
            val bEnd = parseTimeToMinutes(schedule.break_end_time)
            if (bStart != bEnd && currentMin < bStart) {
                targetMin = bStart
            }
        }

        var diffMin = targetMin - currentMin
        if (diffMin < 0) {
            diffMin += 1440 // Overnight window wrap
        }
        val totalSec = diffMin * 60 - currentSec
        // Maximum study timer from a block is capped safely between 10s and 4 hours (never 24 hours)
        return totalSec.coerceIn(10, 4 * 3600)
    }

    fun getScheduleStatusSummary(context: Context): String {
        val schedules = getLocalSchedules(context).filter { it.is_enabled }
        if (schedules.isEmpty()) return "No schedule configured"

        val now = Calendar.getInstance()
        val activeSchedule = schedules.firstOrNull { isScheduleActiveAt(it, now) }

        if (activeSchedule != null) {
            val currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val subjectPrefix = if (!activeSchedule.subject.isNullOrBlank()) {
                "Studying ${activeSchedule.subject.trim()}"
            } else {
                "Study window active"
            }
            if (!activeSchedule.break_start_time.isNullOrBlank() && !activeSchedule.break_end_time.isNullOrBlank()) {
                val bStart = parseTimeToMinutes(activeSchedule.break_start_time)
                if (bStart > currentMin) {
                    return "$subjectPrefix until ${formatDisplayTime(activeSchedule.break_start_time)} (Break at ${formatDisplayTime(activeSchedule.break_start_time)})"
                }
            }
            return "$subjectPrefix until ${formatDisplayTime(activeSchedule.end_time)}"
        }

        val breakSchedule = schedules.firstOrNull { schedule ->
            if (!schedule.break_start_time.isNullOrBlank() && !schedule.break_end_time.isNullOrBlank()) {
                val currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val bStart = parseTimeToMinutes(schedule.break_start_time)
                val bEnd = parseTimeToMinutes(schedule.break_end_time)
                if (bEnd > bStart) {
                    currentMin in bStart until bEnd
                } else {
                    currentMin >= bStart || currentMin < bEnd
                }
            } else false
        }

        if (breakSchedule != null && !breakSchedule.break_end_time.isNullOrBlank()) {
            val breakSubject = if (!breakSchedule.subject.isNullOrBlank()) " (${breakSchedule.subject.trim()})" else ""
            return "In Break until ${formatDisplayTime(breakSchedule.break_end_time)}$breakSubject"
        }

        val nextTransitionMillis = getNextTransitionMillis(context)
        if (nextTransitionMillis != null) {
            val nextCal = Calendar.getInstance().apply { timeInMillis = nextTransitionMillis }
            val nextHourMin = String.format("%02d:%02d", nextCal.get(Calendar.HOUR_OF_DAY), nextCal.get(Calendar.MINUTE))
            return "Free until ${formatDisplayTime(nextHourMin)}"
        }

        return "No active schedule"
    }

    fun getCurrentlyActiveBlockedPackages(context: Context): Set<String> {
        val schedules = getLocalSchedules(context)
        val now = Calendar.getInstance()
        val activeSchedules = schedules.filter { isScheduleActiveAt(it, now) }
        return activeSchedules
            .flatMap { it.blocked_apps_list.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun parseTimeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.trim().split(":")
            val hour = parts[0].toInt()
            val min = if (parts.size > 1) parts[1].toInt() else 0
            hour * 60 + min
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Calculates the remaining seconds until the current blocking window changes state
     * (e.g., until a break starts, or until the schedule ends).
     */
    fun getSecondsUntilNextStateChange(context: Context): Int {
        val now = Calendar.getInstance()
        val nowMillis = now.timeInMillis
        val nextTransition = getNextTransitionMillis(context) ?: (nowMillis + 3600_000L)
        val diff = (nextTransition - nowMillis) / 1000L
        return if (diff > 0) diff.toInt() else 3600
    }

    /**
     * Finds the next upcoming transition time across all enabled schedules:
     * - Start time
     * - Break start time (if break configured)
     * - Break end time (if break configured)
     * - End time
     */
    fun getNextTransitionMillis(context: Context): Long? {
        val schedules = getLocalSchedules(context).filter { it.is_enabled }
        if (schedules.isEmpty()) return null

        val now = Calendar.getInstance()
        val nowMillis = now.timeInMillis
        var closestFutureMillis: Long? = null

        val dayMap = mapOf(
            "Mon" to Calendar.MONDAY,
            "Tue" to Calendar.TUESDAY,
            "Wed" to Calendar.WEDNESDAY,
            "Thu" to Calendar.THURSDAY,
            "Fri" to Calendar.FRIDAY,
            "Sat" to Calendar.SATURDAY,
            "Sun" to Calendar.SUNDAY
        )

        // Check across the next 8 days to catch the next transition cleanly
        for (dayOffset in 0..7) {
            val dayCal = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val calDayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK)

            for (schedule in schedules) {
                val activeDays = schedule.days_active.split(",").map { it.trim() }
                val isTodayActive = activeDays.any { dayMap[it] == calDayOfWeek }
                if (!isTodayActive) continue

                val timeEvents = mutableListOf<String>()
                timeEvents.add(schedule.start_time)
                timeEvents.add(schedule.end_time)
                if (!schedule.break_start_time.isNullOrBlank() && !schedule.break_end_time.isNullOrBlank()) {
                    timeEvents.add(schedule.break_start_time)
                    timeEvents.add(schedule.break_end_time)
                }

                for (t in timeEvents) {
                    val parts = t.split(":")
                    if (parts.size >= 2) {
                        val h = parts[0].toIntOrNull() ?: continue
                        val m = parts[1].toIntOrNull() ?: continue
                        val eventCal = (dayCal.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, m)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val eventMillis = eventCal.timeInMillis
                        if (eventMillis > nowMillis) {
                            if (closestFutureMillis == null || eventMillis < closestFutureMillis) {
                                closestFutureMillis = eventMillis
                            }
                        }
                    }
                }
            }
        }

        return closestFutureMillis
    }

    private const val SCHEDULE_NOTIF_CHANNEL_ID = "schedule_transitions_channel"

    private fun createScheduleNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                SCHEDULE_NOTIF_CHANNEL_ID,
                "Schedule & Break Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Audible alerts for study schedule start, break start, and break end"
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun notifyScheduleEvent(context: Context, title: String, message: String) {
        createScheduleNotificationChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val openIntent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_large)
        val builder = NotificationCompat.Builder(context, SCHEDULE_NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setContentIntent(pi)
            .setAutoCancel(true)

        manager.notify(8881, builder.build())
    }

    fun isBreakCurrentlyActive(context: Context): Boolean {
        val schedules = getLocalSchedules(context).filter { it.is_enabled }
        if (schedules.isEmpty()) return false
        val now = Calendar.getInstance()
        val currentMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return schedules.any { schedule ->
            if (!schedule.break_start_time.isNullOrBlank() && !schedule.break_end_time.isNullOrBlank()) {
                val bStart = parseTimeToMinutes(schedule.break_start_time)
                val bEnd = parseTimeToMinutes(schedule.break_end_time)
                if (bEnd > bStart) {
                    currentMin in bStart until bEnd
                } else {
                    currentMin >= bStart || currentMin < bEnd
                }
            } else false
        }
    }

    /**
     * Evaluates current state across all schedules and updates AlarmManager and FocusShieldService.
     */
    fun evaluateAndReschedule(context: Context) {
        val isAnyActive = isAnyScheduleCurrentlyActive(context)
        val isBreakActive = isBreakCurrentlyActive(context)
        val activePackages = getCurrentlyActiveBlockedPackages(context)

        val statePrefs = context.getSharedPreferences("scheduled_block_state", Context.MODE_PRIVATE)
        val prevState = statePrefs.getString("last_state", "FREE") ?: "FREE"
        val currentState = if (isAnyActive) "STUDY" else if (isBreakActive) "BREAK" else "FREE"

        if (prevState != currentState) {
            statePrefs.edit().putString("last_state", currentState).apply()
            when (currentState) {
                "STUDY" -> {
                    val activeBlock = getActiveStudySchedule(context)
                    val subjectText = activeBlock?.subject?.trim()
                    if (prevState == "BREAK") {
                        val breakResumeMsg = if (!subjectText.isNullOrBlank()) {
                            "Break time is over. Focus Shield is active for '$subjectText' and apps are locked!"
                        } else {
                            "Break time is over. Focus Shield is active and apps are locked!"
                        }
                        notifyScheduleEvent(context, "🔔 Break Ended - Study Resumed!", breakResumeMsg)
                    } else {
                        val studyStartMsg = if (!subjectText.isNullOrBlank()) {
                            "Your study block for '$subjectText' has started. Focus Shield is active and apps are locked."
                        } else {
                            "Your study block has started. Focus Shield is active and apps are locked."
                        }
                        notifyScheduleEvent(context, "📚 Study Schedule Started!", studyStartMsg)
                    }
                }
                "BREAK" -> {
                    notifyScheduleEvent(context, "☕ Break Time Started!", "Take a break now! Your study break window is active.")
                }
                "FREE" -> {
                    if (prevState == "STUDY" || prevState == "BREAK") {
                        notifyScheduleEvent(context, "🎉 Study Schedule Completed", "Your study window has ended. Great work!")
                    }
                }
            }
        }

        Log.d(TAG, "[ScheduleTransition] Transition triggered/evaluated at ${java.util.Date()}: isAnyActive=$isAnyActive, activePackagesCount=${activePackages.size}, summary='${getScheduleStatusSummary(context)}'")

        if (isAnyActive) {
            val remainingSeconds = getSecondsUntilNextStateChange(context)
            val intent = Intent(context, FocusShieldService::class.java).apply {
                action = FocusShieldService.ACTION_START_SHIELD
                putExtra(FocusShieldService.EXTRA_DURATION_SECONDS, remainingSeconds)
                putExtra(FocusShieldService.EXTRA_IS_PUNISHMENT, false)
                if (activePackages.isNotEmpty()) {
                    putExtra(FocusShieldService.EXTRA_BLOCKED_LIST, activePackages.joinToString(","))
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "Started/Updated FocusShieldService for $remainingSeconds seconds")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting FocusShieldService: ${e.message}")
            }
        } else {
            // If shield is running, check if it was started by a schedule and not a manual/punishment timer
            val isShieldRunning = FocusShieldService.isShieldRunning(context)
            val isPunishment = FocusShieldService.isPunishmentLock.value
            if (isShieldRunning && !isPunishment) {
                // Check if user is running a manual focus timer
                val prefs = context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
                val isManualTimerRunning = prefs.getBoolean("is_timer_running", false)
                if (!isManualTimerRunning) {
                    val stopIntent = Intent(context, FocusShieldService::class.java).apply {
                        action = FocusShieldService.ACTION_STOP_SHIELD
                    }
                    try {
                        context.startService(stopIntent)
                        Log.d(TAG, "Stopped FocusShieldService (schedule window ended/break)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping FocusShieldService: ${e.message}")
                    }
                }
            }
        }

        // Schedule the next transition alarm
        scheduleNextAlarm(context)
    }

    private fun scheduleNextAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nextTransition = getNextTransitionMillis(context)

        val intent = Intent(context, ScheduledBlockReceiver::class.java).apply {
            action = ScheduledBlockReceiver.ACTION_SCHEDULE_TRANSITION
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (nextTransition == null) {
            alarmManager.cancel(pi)
            Log.d(TAG, "[ScheduleAlarm] No future schedule transitions; cancelled alarm")
            return
        }

        try {
            val showIntent = PendingIntent.getActivity(
                context,
                ALARM_REQUEST_CODE,
                Intent(context, com.example.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canScheduleExact) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTransition, showIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pi)
                Log.d(TAG, "[ScheduleAlarm] Exact OS alarm registered via setAlarmClock for: ${java.util.Date(nextTransition)}")
            } else {
                Log.w(TAG, "[ScheduleAlarm] Exact alarm permission not granted; scheduling with setAndAllowWhileIdle")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTransition, pi)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, nextTransition, pi)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[ScheduleAlarm] Failed to schedule setAlarmClock: ${e.message}; falling back to setAndAllowWhileIdle")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTransition, pi)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, nextTransition, pi)
                }
                Log.d(TAG, "[ScheduleAlarm] Fallback alarm registered for: ${java.util.Date(nextTransition)}")
            } catch (e2: Exception) {
                Log.e(TAG, "[ScheduleAlarm] Fallback schedule alarm error: ${e2.message}")
            }
        }
    }

    fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduledBlockReceiver::class.java).apply {
            action = ScheduledBlockReceiver.ACTION_SCHEDULE_TRANSITION
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
        Log.d(TAG, "Cancelled scheduled block alarm")
    }

    fun formatDisplayTime(timeStr: String): String {
        return try {
            val parts = timeStr.split(":")
            val h = parts[0].toInt()
            val m = if (parts.size > 1) parts[1].toInt() else 0
            val amPm = if (h >= 12) "PM" else "AM"
            val displayHour = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            String.format("%d:%02d %s", displayHour, m, amPm)
        } catch (_: Exception) {
            timeStr
        }
    }
}
