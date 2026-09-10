package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.SupabaseAlarmDto
import com.example.data.model.AlarmItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val PREFS_NAME = "focusly_local_data"
    private const val KEY_ALARMS_JSON = "custom_alarms_json"

    const val ACTION_ALARM_TRIGGER = "com.example.action.FOCUS_ALARM_RING"
    const val EXTRA_ALARM_ID = "extra_alarm_id"

    const val EXTRA_ALARM_LABEL = "extra_alarm_label"
    const val EXTRA_ALARM_RINGTONE = "extra_alarm_ringtone"
    const val EXTRA_ALARM_VIBRATE = "extra_alarm_vibrate"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getLocalAlarms(context: Context): List<AlarmItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ALARMS_JSON, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<AlarmItem>>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "[AlarmLoad] Failed to parse local alarms JSON: ${e.message}")
            emptyList()
        }
    }

    fun clearLocalAlarms(context: Context) {
        val current = getLocalAlarms(context)
        current.forEach { cancelAlarm(context, it.id) }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ALARMS_JSON).apply()
        AlarmNotificationHelper.stopAlarm(context)
    }

    fun saveLocalAlarms(context: Context, alarms: List<AlarmItem>) {
        try {
            val encoded = json.encodeToString(alarms)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_ALARMS_JSON, encoded).apply()
            Log.d(TAG, "[AlarmSave] Saved ${alarms.size} alarms locally to SharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "[AlarmSave] Failed to serialize local alarms: ${e.message}")
        }
    }

    fun scheduleAlarm(context: Context, alarm: AlarmItem) {
        if (!alarm.isEnabled) {
            cancelAlarm(context, alarm.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, ScheduledBlockReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_ALARM_RINGTONE, alarm.ringtone)
            putExtra(EXTRA_ALARM_VIBRATE, alarm.vibrate)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            flags
        )

        try {
            val showIntent = PendingIntent.getActivity(
                context,
                alarm.id.hashCode(),
                Intent(context, com.example.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                flags
            )

            val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

            if (canScheduleExact) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, showIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(TAG, "[AlarmManager] Alarm registered via setAlarmClock for ${calendar.time} (id=${alarm.id}, label='${alarm.label}')")
            } else {
                Log.w(TAG, "[AlarmManager] Exact alarm permission not granted, falling back to setAndAllowWhileIdle")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[AlarmManager] Failed setAlarmClock for '${alarm.label}', falling back: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
                Log.d(TAG, "[AlarmManager] Alarm registered via fallback setAndAllowWhileIdle for ${calendar.time} (id=${alarm.id})")
            } catch (e2: Exception) {
                Log.e(TAG, "[AlarmManager] Fallback alarm scheduling error: ${e2.message}")
            }
        }
    }

    fun cancelAlarm(context: Context, alarmId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduledBlockReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarmId)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            flags
        )

        try {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "[AlarmManager] Cancelled system alarm id=$alarmId")
        } catch (e: Exception) {
            Log.w(TAG, "[AlarmManager] Error cancelling alarm $alarmId: ${e.message}")
        }
    }

    fun rescheduleAllEnabled(context: Context, alarms: List<AlarmItem>) {
        Log.d(TAG, "[AlarmManager] Rescheduling ${alarms.size} total alarms with AlarmManager")
        alarms.forEach { alarm ->
            if (alarm.isEnabled) {
                scheduleAlarm(context, alarm)
            } else {
                cancelAlarm(context, alarm.id)
            }
        }
    }
}
