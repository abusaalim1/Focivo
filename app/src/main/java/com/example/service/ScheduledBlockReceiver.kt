package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.SupabaseService
import com.example.data.model.AlarmItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledBlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduledBlockReceiver"
        const val ACTION_SCHEDULE_TRANSITION = "com.example.action.SCHEDULE_TRANSITION"
        const val ACTION_SCHEDULE_START = "com.example.action.SCHEDULE_BLOCK_START"
        const val ACTION_SCHEDULE_END = "com.example.action.SCHEDULE_BLOCK_END"
        /**
         * v1.4.3: fires 10 minutes before a scheduled study block starts.
         * Extra [EXTRA_REMINDER_SUBJECT] carries the block's subject (may be null).
         */
        const val ACTION_SCHEDULE_REMINDER = "com.example.action.SCHEDULE_REMINDER"
        const val EXTRA_REMINDER_SUBJECT = "extra_reminder_subject"
        /**
         * v1.2.3 (Task 13.1): fires 10 minutes before a STRICT schedule
         * starts. Extra [EXTRA_STRICT_SCHEDULE_ID] carries the schedule id,
         * [EXTRA_STRICT_START_MS] the window start epoch ms,
         * [EXTRA_STRICT_SUBJECT] the subject (may be null).
         */
        const val ACTION_STRICT_LOCK_WARNING = "com.example.action.STRICT_LOCK_WARNING"
        const val EXTRA_STRICT_SCHEDULE_ID = "extra_strict_schedule_id"
        const val EXTRA_STRICT_START_MS = "extra_strict_start_ms"
        const val EXTRA_STRICT_SUBJECT = "extra_strict_subject"
        // v1.1: notification survival — every scheduled type is AlarmManager-backed.
        const val ACTION_SUNDAY_RECAP_CHECK = "com.example.action.SUNDAY_RECAP_CHECK"
        const val ACTION_UPDATE_CHECK = "com.example.action.UPDATE_CHECK"
        const val ACTION_COMEBACK_CHECK = "com.example.action.COMEBACK_CHECK"

        /**
         * Restores alarms and study-block schedules after boot or app update.
         * Called by the exported [BootReceiver]; kept here so the logic is shared.
         */
        fun handleBootCompleted(context: Context, pendingResult: BroadcastReceiver.PendingResult) {
            Log.d(TAG, "[BootCompleted] Device boot completed. Re-registering system alarms and study blocks...")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dummyLabels = setOf("Morning Deep Focus", "Midday Reset", "Day Review")
                    val localAlarms = AlarmScheduler.getLocalAlarms(context).filterNot { it.label in dummyLabels }
                    if (localAlarms.isNotEmpty()) {
                        AlarmScheduler.rescheduleAllEnabled(context, localAlarms)
                        Log.d(TAG, "[BootCompleted] Re-registered ${localAlarms.size} local cached alarms")
                    }

                    val uid = SupabaseService.getInstance().getCurrentUserId()
                    if (!uid.isNullOrBlank()) {
                        val alarmsDto = SupabaseService.getInstance().fetchAlarms(uid).filterNot { it.label in dummyLabels }
                        if (alarmsDto.isNotEmpty()) {
                            val alarmItems = alarmsDto.map { dto ->
                                AlarmItem(
                                    id = dto.id,
                                    label = dto.label,
                                    hour = dto.hour,
                                    minute = dto.minute,
                                    isEnabled = dto.is_enabled,
                                    ringtone = dto.ringtone,
                                    vibrate = dto.vibrate,
                                    daysActive = dto.days_active
                                )
                            }
                            AlarmScheduler.saveLocalAlarms(context, alarmItems)
                            AlarmScheduler.rescheduleAllEnabled(context, alarmItems)
                        }
                    }

                    ScheduledBlockScheduler.evaluateAndReschedule(context)
                    // v1.1: re-arm ALL survival alarms (study
                    // reminder, Sunday recap, update check, comeback) —
                    // idempotent, no duplicates.
                    FocivoNotifications.scheduleAllSurvivalAlarms(context)
                    // v1.3.1: re-arm/validate the strict-session record on
                    // boot — expired records are purged (never resurrect),
                    // live ones stay armed so the tamper guard cannot be
                    // dodged by rebooting mid-session.
                    com.example.util.StrictSessionStore.validateOnBoot(context)
                } catch (e: Exception) {
                    Log.e(TAG, "[BootCompleted] Error restoring alarms/schedules on boot: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    /**
     * Exported receiver for system broadcasts only (boot / package replaced).
     * Declared in the manifest with android:exported="true".
     */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action
            Log.d(TAG, "BootReceiver onReceive action: $action")
            if (action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON"
            ) {
                handleBootCompleted(context, goAsync())
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "onReceive action: $action")

        when (action) {
            AlarmScheduler.ACTION_ALARM_TRIGGER -> {
                val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: ""
                val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Focus Alarm"
                val ringtone = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_RINGTONE) ?: "Zen Bell"
                val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
                Log.i(TAG, "Alarm triggered for id=$alarmId, label=$label, ringtone=$ringtone")

                // Show high-priority heads-up notification and start ringtone + vibration
                AlarmNotificationHelper.showAlarmNotification(
                    context = context,
                    alarmId = alarmId,
                    label = label,
                    ringtone = ringtone,
                    vibrate = vibrate
                )

                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("TRIGGER_ALARM_ID", alarmId)
                }
                if (launchIntent != null) {
                    try {
                        context.startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not start activity from background: ${e.message}")
                    }
                }
            }
            "com.example.action.DISMISS_ALARM" -> {
                Log.i(TAG, "Dismissing alarm sound and notification")
                AlarmNotificationHelper.stopAlarm(context)
            }
            "com.example.action.SNOOZE_ALARM" -> {
                val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: ""
                Log.i(TAG, "Snoozing alarm id=$alarmId for 5 minutes")
                AlarmNotificationHelper.stopAlarm(context)

                val cal = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.MINUTE, 5)
                }
                val snoozedAlarm = AlarmItem(
                    id = if (alarmId.isBlank()) java.util.UUID.randomUUID().toString() else alarmId,
                    label = "Snoozed Focus Alarm",
                    hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
                    minute = cal.get(java.util.Calendar.MINUTE),
                    isEnabled = true,
                    ringtone = "Zen Bell"
                )
                AlarmScheduler.scheduleAlarm(context, snoozedAlarm)
            }
            ACTION_SCHEDULE_TRANSITION, ACTION_SCHEDULE_START, ACTION_SCHEDULE_END -> {
                Log.d(TAG, "Evaluating and rescheduling all custom study blocks on: $action")
                ScheduledBlockScheduler.evaluateAndReschedule(context)
            }
            ACTION_SCHEDULE_REMINDER -> {
                val subject = intent.getStringExtra(EXTRA_REMINDER_SUBJECT)
                Log.i(TAG, "Schedule reminder fired for subject=$subject")
                FocivoNotifications.postScheduleReminder(context, subject)
            }
            ACTION_STRICT_LOCK_WARNING -> {
                val scheduleId = intent.getStringExtra(EXTRA_STRICT_SCHEDULE_ID) ?: ""
                val startMs = intent.getLongExtra(EXTRA_STRICT_START_MS, 0L)
                val subject = intent.getStringExtra(EXTRA_STRICT_SUBJECT)
                Log.i(TAG, "Strict lock-in warning fired for scheduleId=$scheduleId")
                StrictLockInManager.handleWarningFired(context, scheduleId, startMs, subject)
            }
            // v1.1: survival-hardened scheduled types.
            ACTION_SUNDAY_RECAP_CHECK -> {
                Log.i(TAG, "Sunday recap check fired")
                FocivoNotifications.handleSundayRecapCheck(context)
            }
            ACTION_UPDATE_CHECK -> {
                Log.i(TAG, "Update check fired")
                FocivoNotifications.handleUpdateCheck(context)
            }
            ACTION_COMEBACK_CHECK -> {
                Log.i(TAG, "Comeback check fired")
                FocivoNotifications.handleComebackCheck(context)
            }
            else -> {
                ScheduledBlockScheduler.evaluateAndReschedule(context)
            }
        }
    }
}
