package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.WeeklyRecapSummary

object WeeklyRecapNotificationHelper {

    private const val CHANNEL_ID = "focivo_weekly_sunday_recap"
    private const val CHANNEL_NAME = "Sunday Weekly Recap"
    private const val NOTIFICATION_ID = 9021

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Weekly Sunday Study Recap & Achievements"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showSundayRecapNotification(context: Context, recap: WeeklyRecapSummary) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_SUNDAY_RECAP", true)
            putExtra("WEEK_KEY", recap.weekKey)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            pendingIntentFlags
        )

        // v1.4.3: cute/minimal house style — mascot icon, no emoji, one-line text.
        val summaryTitle = "Your Sunday Recap is ready"
        val summaryBody = "See how your study week went."
        val bigText = buildString {
            append("Focus time: ${recap.totalHoursFormatted} hrs (${recap.sessionCount} sessions)\n")
            if (recap.topSubject.isNotBlank() && recap.topSubject != "General Study") {
                append("Top subject: ${recap.topSubject}\n")
            }
            append("Best day: ${recap.bestDayName}\n")
            append("Tap to see your full weekly study breakdown.")
        }

        val largeIcon = try {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.mascot_study_happy)
        } catch (_: Exception) { null }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setColor(0xFF8CE000.toInt())
            .setContentTitle(summaryTitle)
            .setContentText(summaryBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        if (largeIcon != null) builder.setLargeIcon(largeIcon)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, builder.build())
    }
}
