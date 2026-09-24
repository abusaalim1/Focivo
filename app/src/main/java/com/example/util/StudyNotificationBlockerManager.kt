package com.example.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Manages blocking and muting device notifications during active study sessions.
 * Supports Do Not Disturb (DND) priority/alarm filtering when granted, and audio stream muting as instant fallback.
 */
object StudyNotificationBlockerManager {
    private const val TAG = "StudyNotifBlocker"
    private const val PREFS_NAME = "study_notification_blocker_prefs"
    private const val KEY_BLOCK_DURING_STUDY = "key_block_notifications_during_study"
    private const val KEY_SAVED_NOTIFICATION_VOLUME = "key_saved_notification_volume"
    private const val KEY_SAVED_RINGER_MODE = "key_saved_ringer_mode"

    private var isCurrentlyMuted = false

    fun isBlockNotificationsDuringStudyEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BLOCK_DURING_STUDY, true)
    }

    fun setBlockNotificationsDuringStudyEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BLOCK_DURING_STUDY, enabled).apply()
        if (!enabled && isCurrentlyMuted) {
            deactivateStudyNotificationBlock(context)
        }
    }

    fun hasDndPermission(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return notificationManager?.isNotificationPolicyAccessGranted == true
    }

    fun openDndSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open DND settings: ${e.message}")
        }
    }

    /**
     * Activates notification blocking when study session or focus shield begins.
     */
    fun activateStudyNotificationBlock(context: Context) {
        if (!isBlockNotificationsDuringStudyEnabled(context)) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Save previous notification volume
            audioManager?.let { am ->
                val currentVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                val currentRinger = am.ringerMode
                prefs.edit()
                    .putInt(KEY_SAVED_NOTIFICATION_VOLUME, currentVol)
                    .putInt(KEY_SAVED_RINGER_MODE, currentRinger)
                    .apply()

                try {
                    am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set notification stream volume: ${e.message}")
                }
            }

            // Apply DND interruption filter if granted (allows alarms while suppressing casual pings)
            if (notificationManager?.isNotificationPolicyAccessGranted == true) {
                try {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                    Log.i(TAG, "DND Interruption Filter set to INTERRUPTION_FILTER_ALARMS for study session")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set DND filter: ${e.message}")
                }
            }

            isCurrentlyMuted = true
            Log.i(TAG, "Study notification blocker ACTIVATED")
        } catch (t: Throwable) {
            Log.e(TAG, "Error activating study notification block: ${t.message}", t)
        }
    }

    /**
     * Deactivates notification blocking when study session ends.
     */
    fun deactivateStudyNotificationBlock(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Restore DND filter
            if (notificationManager?.isNotificationPolicyAccessGranted == true) {
                try {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    Log.i(TAG, "DND Interruption Filter restored to INTERRUPTION_FILTER_ALL")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore DND filter: ${e.message}")
                }
            }

            // Restore previous notification volume
            audioManager?.let { am ->
                val savedVol = prefs.getInt(KEY_SAVED_NOTIFICATION_VOLUME, -1)
                if (savedVol >= 0) {
                    try {
                        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, savedVol, 0)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not restore notification stream volume: ${e.message}")
                    }
                }
            }

            isCurrentlyMuted = false
            Log.i(TAG, "Study notification blocker DEACTIVATED")
        } catch (t: Throwable) {
            Log.e(TAG, "Error deactivating study notification block: ${t.message}", t)
        }
    }
}
