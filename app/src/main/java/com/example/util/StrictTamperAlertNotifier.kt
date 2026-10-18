package com.example.util

import android.content.Context
import android.util.Log
import com.example.service.FocivoNotifications
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.3.3 (owner order 2026-10-18, item 6b) — uninstall-attempt ALERTS.
 *
 * When StrictTamperGuard intercepts an uninstall or device-admin
 * deactivation attempt during a PROTECTED strict session, the
 * accessibility service already (1) back-presses out of the tamper screen,
 * (2) shows the premium StrictUninstallBlockedOverlay, and (3) logs the
 * intercept to Guard Hub. This posts (4): a high-priority notification
 * carrying the REAL session end time, so the blocked attempt stays visible
 * even when the user is mid-flow in another app.
 *
 * Rules (enforced here; session-state logic is REUSED from
 * StrictSessionStore, never duplicated):
 *  - STRICTLY session-scoped: posts only while
 *    StrictSessionStore.isStrictSessionActive() is true — never outside a
 *    protected session.
 *  - Throttled to one notification per THROTTLE_WINDOW_MS (mirrors the
 *    re-fire throttle concept in AiStudyAccessibilityService), so repeated
 *    intercept attempts don't spam the shade.
 *  - Every post is logged to Guard Hub.
 *  - Auto-cancelled at session end: StrictSessionStore.disarm() (and the
 *    expiry-purge path) call FocivoNotifications.cancelStrictUninstallAlert().
 *
 * The pure policy bits (title/body formatting, throttle math, gate check)
 * are unit-tested without a Context. English-only text, no emojis —
 * standing rule.
 */
object StrictTamperAlertNotifier {

    private const val TAG = "StrictTamperAlert"
    private const val PREFS_NAME = "focivo_strict_tamper_alert"
    private const val KEY_LAST_ALERT_MS = "last_alert_ms"

    /** One alert per window — mirrors the intercept re-fire throttle. */
    const val THROTTLE_WINDOW_MS = 1500L

    fun titleFor(kind: StrictTamperGuard.TamperKind): String = when (kind) {
        StrictTamperGuard.TamperKind.DEVICE_ADMIN_TAMPER -> "Admin change blocked"
        else -> "Uninstall attempt blocked"
    }

    /** e.g. "7:45 PM". Locale.US keeps the AM/PM marker English. */
    fun formatEndTime(endTimeMs: Long): String =
        SimpleDateFormat("h:mm a", Locale.US).format(Date(endTimeMs))

    fun bodyFor(endTimeMs: Long): String =
        "Strict Mode is protecting your focus session. It ends at ${formatEndTime(endTimeMs)}."

    /** Pure gate check — only a PROTECTED gate may post. */
    fun shouldPostForGate(state: UninstallProtectionGate.State): Boolean =
        state == UninstallProtectionGate.State.PROTECTED

    /** Pure throttle math: at most one post per window. */
    fun shouldNotify(nowMs: Long, lastNotifiedMs: Long): Boolean =
        nowMs - lastNotifiedMs >= THROTTLE_WINDOW_MS

    /**
     * Intercept entry point, called from the throttled path in
     * AiStudyAccessibilityService.guardStrictTamperScreen. Returns true only
     * when a notification was actually posted. Silently no-ops (false)
     * outside a protected session, inside the throttle window, when the
     * session end time is unavailable/expired, or on any error.
     */
    fun notifyIntercept(context: Context, kind: StrictTamperGuard.TamperKind): Boolean {
        return try {
            // STRICTLY session-scoped — the store's own verdict, never a
            // second copy of the logic.
            if (!StrictSessionStore.isStrictSessionActive(context)) return false
            val now = System.currentTimeMillis()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val last = try { prefs.getLong(KEY_LAST_ALERT_MS, 0L) } catch (_: Exception) { now }
            if (!shouldNotify(now, last)) return false
            val endMs = StrictSessionStore.getEndTimeMs(context)
            if (endMs <= now) return false
            try { prefs.edit().putLong(KEY_LAST_ALERT_MS, now).apply() } catch (_: Exception) { }
            val title = titleFor(kind)
            val body = bodyFor(endMs)
            FocivoNotifications.postStrictUninstallAlert(context, title, body)
            try {
                AiStudyGuardManager.logGuardActivity(
                    context,
                    "Focivo",
                    kind.hubStage,
                    "NOTIFIED",
                    "tamper alert posted",
                    "ends ${formatEndTime(endMs)}"
                )
            } catch (_: Exception) { }
            Log.i(TAG, "Tamper alert posted for ${kind.name}; session ends ${formatEndTime(endMs)}.")
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Clears the alert. Called by StrictSessionStore on session end/expiry. */
    fun cancelAlert(context: Context) {
        try { FocivoNotifications.cancelStrictUninstallAlert(context) } catch (_: Exception) { }
    }
}
