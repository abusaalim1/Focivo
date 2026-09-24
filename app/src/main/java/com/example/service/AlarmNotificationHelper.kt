package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AlarmAudioEngine

object AlarmNotificationHelper {
    private const val CHANNEL_ID = "focus_alarm_channel"
    private const val NOTIFICATION_ID = 88192
    private var audioEngine: AlarmAudioEngine? = null

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Focus Studio Alarms"
            val descriptionText = "High-priority notifications for scheduled study alarms"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 600)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showAlarmNotification(
        context: Context,
        alarmId: String,
        label: String,
        ringtone: String,
        vibrate: Boolean
    ) {
        createNotificationChannel(context)

        // Start audio engine for ringtone and vibration
        if (audioEngine == null) {
            audioEngine = AlarmAudioEngine(context.applicationContext)
        }
        audioEngine?.startAlarm(ringtone)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Full screen / Content Intent to launch MainActivity
        val contentIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("TRIGGER_ALARM_ID", alarmId)
            },
            flags
        )

        // Dismiss Action
        val dismissIntent = Intent(context, ScheduledBlockReceiver::class.java).apply {
            action = "com.example.action.DISMISS_ALARM"
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId + "_dismiss").hashCode(),
            dismissIntent,
            flags
        )

        // Snooze Action
        val snoozeIntent = Intent(context, ScheduledBlockReceiver::class.java).apply {
            action = "com.example.action.SNOOZE_ALARM"
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId + "_snooze").hashCode(),
            snoozeIntent,
            flags
        )

        val displayTitle = if (label.isBlank()) "Focus Studio Alarm" else label
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_large)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setLargeIcon(largeIcon)
            .setContentTitle(displayTitle)
            .setContentText("Time for your deep study session! Ringtone: $ringtone")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze (5m)", snoozePendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun silenceAudio(context: Context) {
        audioEngine?.stopAlarm()
    }

    fun isRinging(): Boolean {
        return audioEngine != null
    }

    fun stopAlarm(context: Context) {
        audioEngine?.stopAlarm()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
