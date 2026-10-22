package com.example.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Silences device notifications during active study sessions.
 *
 * v1.1.4e: DND-ONLY. The notification-listener service was removed from the
 * app entirely (Play Protect hard-blocks sideloaded apps that declare a
 * NotificationListenerService), so blocking works purely through Do Not
 * Disturb: while a session runs, the interruption filter is set to
 * INTERRUPTION_FILTER_ALARMS (alarms still ring, casual pings stay silent)
 * and the user's previous filter is restored when the session ends.
 * Requires Do Not Disturb policy access (ACCESS_NOTIFICATION_POLICY),
 * checked via [NotificationManager.isNotificationPolicyAccessGranted].
 */
object StudyNotificationBlockerManager {
    private const val TAG = "StudyNotifBlocker"
    private const val PREFS_NAME = "study_notification_blocker_prefs"
    private const val KEY_BLOCK_DURING_STUDY = "key_block_notifications_during_study"
    private const val KEY_SAVED_INTERRUPTION_FILTER = "key_saved_interruption_filter"
    private const val KEY_FILTER_APPLIED = "key_filter_applied"

    fun isBlockNotificationsDuringStudyEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BLOCK_DURING_STUDY, true)
    }

    fun setBlockNotificationsDuringStudyEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BLOCK_DURING_STUDY, enabled).apply()
        if (!enabled) {
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
     * Activates DND silencing when a study session or focus shield begins.
     * No-op unless the feature is enabled AND Do Not Disturb policy access
     * is granted. Idempotent: a second overlapping activation never
     * overwrites the saved pre-session interruption filter.
     */
    fun activateStudyNotificationBlock(context: Context) {
        if (!isBlockNotificationsDuringStudyEnabled(context)) return
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.isNotificationPolicyAccessGranted != true) {
                Log.i(TAG, "DND policy access not granted; study notification block skipped")
                AiStudyGuardManager.logGuardActivity(
                    context = context,
                    app = "Notification Shield",
                    stage = "NOTIF_SHIELD",
                    verdict = "SKIPPED",
                    reason = "Do Not Disturb access not granted; notifications left untouched"
                )
                return
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_FILTER_APPLIED, false)) return

            val previousFilter = notificationManager.currentInterruptionFilter
            prefs.edit()
                .putInt(KEY_SAVED_INTERRUPTION_FILTER, previousFilter)
                .putBoolean(KEY_FILTER_APPLIED, true)
                .apply()
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            Log.i(TAG, "DND interruption filter set to ALARMS for study session (was $previousFilter)")
            AiStudyGuardManager.logGuardActivity(
                context = context,
                app = "Notification Shield",
                stage = "NOTIF_SHIELD",
                verdict = "ARMED",
                reason = "Do Not Disturb silencing armed for focus session (alarms still allowed)"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error activating study notification block: ${t.message}", t)
        }
    }

    /**
     * Restores the user's previous interruption filter when the session ends.
     */
    fun deactivateStudyNotificationBlock(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_FILTER_APPLIED, false)) return
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.isNotificationPolicyAccessGranted == true) {
                val savedFilter = prefs.getInt(
                    KEY_SAVED_INTERRUPTION_FILTER,
                    NotificationManager.INTERRUPTION_FILTER_ALL
                )
                try {
                    notificationManager.setInterruptionFilter(savedFilter)
                    Log.i(TAG, "DND interruption filter restored to $savedFilter")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore DND filter: ${e.message}")
                }
            }
            prefs.edit()
                .putBoolean(KEY_FILTER_APPLIED, false)
                .remove(KEY_SAVED_INTERRUPTION_FILTER)
                .apply()
            AiStudyGuardManager.logGuardActivity(
                context = context,
                app = "Notification Shield",
                stage = "NOTIF_SHIELD",
                verdict = "DISARMED",
                reason = "Do Not Disturb silencing released after focus session"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error deactivating study notification block: ${t.message}", t)
        }
    }
}
