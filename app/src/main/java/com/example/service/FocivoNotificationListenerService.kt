package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.util.AiStudyGuardManager
import com.example.util.EssentialAppsGuard
import com.example.util.SessionNotificationDigestManager
import com.example.util.StudyNotificationBlockerManager

class FocivoNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return // Never suppress own notifications

        // Never suppress essential communication / calls / system alerts
        if (EssentialAppsGuard.isEssentialApp(applicationContext, pkg)) return

        // Only active during genuine study sessions or triggered schedules
        val isStudyPeriod = AiStudyGuardManager.isStudyPeriodActive(applicationContext)
        if (!isStudyPeriod) return

        val isNotifBlockerOn = StudyNotificationBlockerManager.isBlockNotificationsDuringStudyEnabled(applicationContext)
        val blockedPackages = FocusShieldService.getActiveBlockedPackages(applicationContext)
        val isAppBlocked = blockedPackages.any { pkg.equals(it, ignoreCase = true) || pkg.startsWith(it, ignoreCase = true) }

        if (isNotifBlockerOn || isAppBlocked) {
            val extras = sbn.notification?.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val friendlyName = FocusShieldService.friendlyAppName(applicationContext, pkg)

            try {
                cancelNotification(sbn.key)
                SessionNotificationDigestManager.recordSuppressedNotification(
                    context = applicationContext,
                    packageName = pkg,
                    appName = friendlyName,
                    title = title.ifBlank { "Notification from $friendlyName" },
                    text = text
                )
                AiStudyGuardManager.logGuardActivity(
                    context = applicationContext,
                    app = friendlyName,
                    stage = "NOTIF_SHIELD",
                    verdict = "SUPPRESSED",
                    reason = "Notification suppressed during focus: \"$title\"",
                    snippet = text.take(60)
                )
                Log.i(TAG, "Suppressed notification from $friendlyName ($pkg): $title")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel notification for $pkg: ${e.message}")
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Focivo Notification Listener connected successfully.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Focivo Notification Listener disconnected.")
    }

    companion object {
        private const val TAG = "FocivoNotifListener"
    }
}
