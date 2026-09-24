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
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "onReceive action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "[BootCompleted] Device boot completed. Re-registering system alarms and study blocks...")
                val pendingResult = goAsync()
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
                    } catch (e: Exception) {
                        Log.e(TAG, "[BootCompleted] Error restoring alarms/schedules on boot: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
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
            else -> {
                ScheduledBlockScheduler.evaluateAndReschedule(context)
            }
        }
    }
}
