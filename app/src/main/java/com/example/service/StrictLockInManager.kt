package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.example.ui.components.StrictWarningOverlay
import com.example.ui.overlay.OverlayLifecycleHost
import com.example.ui.theme.FocuslyTheme
import org.json.JSONArray

/**
 * v1.2.3 (Task 13.1) — strict schedule lock-in (owner order 2026-10-16).
 *
 * For an Auto Study schedule with Strict Mode ON, 10 minutes before its
 * start time:
 *  (a) a premium SYSTEM OVERLAY warns the user the strict study starts in
 *      10 minutes (works even when Focivo is not open — fired from the
 *      AlarmManager/receiver path, reusing the app's overlay machinery).
 *  (b) that schedule's toggle becomes LOCKED: tapping it does nothing
 *      except show the plain-English reason. The pre-locked state (schedule
 *      id + window start) is persisted in SharedPreferences so it survives
 *      app death and reboot.
 *  (c) If the user turned the schedule OFF before the warning fired, the
 *      cancellation stands: the warning alarm is cancelled on toggle-off
 *      and the receiver re-validates before firing.
 *  (d) At schedule time the EXISTING engine (ScheduledBlockScheduler ->
 *      ACTION_SCHEDULE_TRANSITION -> evaluateAndReschedule -> FocusShieldService)
 *      auto-starts the shield. The warning alarm is scheduled ALONGSIDE the
 *      existing reminder/transition alarms — no parallel scheduling engine.
 *  (e) Schedules without strict are untouched: their toggle works anytime
 *      and the plain 10-minute reminder notification behaves as before.
 *
 * No stale locks: the lock is cleared when the schedule's window starts (or
 * passes) — see clearExpiredLocks, called from evaluateAndReschedule and
 * every lock read — and when the schedule is edited/deleted/disabled.
 */
object StrictLockInManager {

    private const val TAG = "StrictLockIn"
    private const val PREFS = "focusly_strict_lockin"
    private const val KEY_LOCKED_ID = "locked_schedule_id"
    private const val KEY_LOCKED_START_MS = "locked_window_start_ms"
    private const val KEY_LOCKED_SUBJECT = "locked_subject"
    private const val KEY_ARMED_IDS_JSON = "armed_warning_ids_json"

    /** 10 minutes, in milliseconds — the lock-in window. */
    const val LOCK_WINDOW_MS = 10L * 60 * 1000

    private const val ALARM_REQ_BASE = 9400

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun alarmRequestCode(scheduleId: String): Int =
        ALARM_REQ_BASE + (scheduleId.hashCode() and 0x7FFFFFFF) % 4000

    // ------------------------------------------------------------- lock state

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Clears locks whose window start has been reached/passed. Never stale. */
    fun clearExpiredLocks(context: Context) {
        try {
            val p = prefs(context)
            val startMs = p.getLong(KEY_LOCKED_START_MS, 0L)
            if (startMs != 0L && System.currentTimeMillis() >= startMs) {
                Log.i(TAG, "Lock window reached — clearing pre-lock ${p.getString(KEY_LOCKED_ID, null)}")
                clearLock(context)
            }
        } catch (_: Exception) { }
    }

    /** Sets the pre-lock for a schedule. Survives app death (SharedPreferences). */
    fun setLock(context: Context, scheduleId: String, windowStartMs: Long, subject: String?) {
        try {
            prefs(context).edit()
                .putString(KEY_LOCKED_ID, scheduleId)
                .putLong(KEY_LOCKED_START_MS, windowStartMs)
                .putString(KEY_LOCKED_SUBJECT, subject)
                .apply()
            Log.i(TAG, "Pre-lock SET for schedule=$scheduleId windowStart=${java.util.Date(windowStartMs)}")
        } catch (_: Exception) { }
    }

    fun clearLock(context: Context) {
        try {
            prefs(context).edit()
                .remove(KEY_LOCKED_ID)
                .remove(KEY_LOCKED_START_MS)
                .remove(KEY_LOCKED_SUBJECT)
                .apply()
            Log.i(TAG, "Pre-lock cleared")
        } catch (_: Exception) { }
    }

    /**
     * The currently pre-locked schedule id, or null. Defensively clears
     * expired locks first so callers never see a stale lock.
     */
    fun getLockedScheduleId(context: Context): String? {
        clearExpiredLocks(context)
        return try {
            prefs(context).getString(KEY_LOCKED_ID, null)?.takeIf { it.isNotBlank() }?.let { id ->
                // Schedule gone, disabled or un-stricted since? Clear — no stale lock.
                val sched = ScheduledBlockScheduler.getLocalSchedules(context)
                    .firstOrNull { it.id == id }
                if (sched == null || !sched.is_enabled || !sched.is_strict_mode) {
                    clearLock(context)
                    null
                } else id
            }
        } catch (_: Exception) { null }
    }

    /** True when this schedule's toggle must be locked right now. */
    fun isScheduleLocked(context: Context, scheduleId: String): Boolean =
        getLockedScheduleId(context) == scheduleId

    /** Whole minutes (ceil, min 1) until the locked window starts. */
    fun minutesLeft(context: Context): Int {
        return try {
            val startMs = prefs(context).getLong(KEY_LOCKED_START_MS, 0L)
            if (startMs == 0L) return 0
            val diff = startMs - System.currentTimeMillis()
            if (diff <= 0L) 0 else ((diff + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        } catch (_: Exception) { 0 }
    }

    fun lockedWindowStartMs(context: Context): Long {
        return try { prefs(context).getLong(KEY_LOCKED_START_MS, 0L) } catch (_: Exception) { 0L }
    }

    fun lockedSubject(context: Context): String? {
        return try { prefs(context).getString(KEY_LOCKED_SUBJECT, null) } catch (_: Exception) { null }
    }

    // ------------------------------------------------------------ alarm arming

    /**
     * Re-arms the strict warning alarm for every enabled + strict schedule
     * (next occurrence only) and cancels warnings for schedules that are no
     * longer enabled/strict/deleted. Called from
     * ScheduledBlockScheduler.refreshReminderAlarms (every schedule mutation,
     * evaluate, boot) so it rides the existing alarm flow.
     */
    fun refreshStrictWarnings(context: Context) {
        try {
            clearExpiredLocks(context)
            val now = System.currentTimeMillis()
            val schedules = ScheduledBlockScheduler.getLocalSchedules(context)
            val armedIds = readArmedIds(context).toMutableSet()
            val stillArmed = mutableSetOf<String>()

            for (schedule in schedules) {
                val id = schedule.id
                if (!schedule.is_enabled || !schedule.is_strict_mode) {
                    if (armedIds.contains(id)) cancelWarning(context, id)
                    continue
                }
                val nextStart = ScheduledBlockScheduler.getNextStartMillisForSchedule(context, schedule)
                if (nextStart == null || nextStart <= now) {
                    if (armedIds.contains(id)) cancelWarning(context, id)
                    continue
                }
                val fireAt = nextStart - LOCK_WINDOW_MS
                if (fireAt <= now) {
                    // Inside the 10-minute window but no warning yet (e.g.
                    // schedule enabled/created late) — fire immediately.
                    if (armedIds.contains(id)) cancelWarning(context, id)
                    if (!isScheduleLocked(context, id)) {
                        val subject = schedule.subject?.takeIf { it.isNotBlank() }
                            ?: schedule.label.takeIf { it.isNotBlank() }
                        Log.i(TAG, "Inside lock window for $id — firing warning immediately")
                        handleWarningFired(context, id, nextStart, subject)
                    }
                    stillArmed.add(id)
                    continue
                }
                scheduleWarning(context, id, nextStart, fireAt,
                    schedule.subject?.takeIf { it.isNotBlank() }
                        ?: schedule.label.takeIf { it.isNotBlank() })
                stillArmed.add(id)
            }
            // Cancel warnings for ids no longer present/enabled/strict.
            for (staleId in armedIds - stillArmed) {
                cancelWarning(context, staleId)
            }
            writeArmedIds(context, stillArmed)
        } catch (e: Exception) {
            Log.e(TAG, "refreshStrictWarnings failed: ${e.message}")
        }
    }

    private fun readArmedIds(context: Context): Set<String> {
        return try {
            val raw = prefs(context).getString(KEY_ARMED_IDS_JSON, "[]") ?: "[]"
            val arr = JSONArray(raw)
            (0 until arr.length()).mapTo(mutableSetOf()) { arr.optString(it) }
        } catch (_: Exception) { emptySet() }
    }

    private fun writeArmedIds(context: Context, ids: Set<String>) {
        try {
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            prefs(context).edit().putString(KEY_ARMED_IDS_JSON, arr.toString()).apply()
        } catch (_: Exception) { }
    }

    private fun scheduleWarning(
        context: Context,
        scheduleId: String,
        startMs: Long,
        fireAt: Long,
        subject: String?
    ) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ScheduledBlockReceiver::class.java).apply {
                action = ScheduledBlockReceiver.ACTION_STRICT_LOCK_WARNING
                putExtra(ScheduledBlockReceiver.EXTRA_STRICT_SCHEDULE_ID, scheduleId)
                putExtra(ScheduledBlockReceiver.EXTRA_STRICT_START_MS, startMs)
                putExtra(ScheduledBlockReceiver.EXTRA_STRICT_SUBJECT, subject)
            }
            val pi = PendingIntent.getBroadcast(
                context, alarmRequestCode(scheduleId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, fireAt, pi)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, fireAt, pi)
                }
            }
            Log.i(TAG, "Strict warning armed for $scheduleId at ${java.util.Date(fireAt)} (start ${java.util.Date(startMs)})")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleWarning failed: ${e.message}")
        }
    }

    /** Cancels the warning alarm for one schedule (toggle-off / delete / un-strict). */
    fun cancelWarning(context: Context, scheduleId: String) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ScheduledBlockReceiver::class.java).apply {
                action = ScheduledBlockReceiver.ACTION_STRICT_LOCK_WARNING
            }
            val pi = PendingIntent.getBroadcast(
                context, alarmRequestCode(scheduleId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            Log.i(TAG, "Strict warning cancelled for $scheduleId")
        } catch (_: Exception) { }
    }

    // ----------------------------------------------------------- warning fired

    /**
     * Receiver-side: the 10-minute warning alarm fired. Re-validates the
     * schedule (a user toggling OFF before the warning stands — requirement
     * (c); races are guarded here too), sets the pre-lock, and shows the
     * premium system overlay.
     */
    fun handleWarningFired(context: Context, scheduleId: String, startMs: Long, subject: String?) {
        try {
            clearExpiredLocks(context)
            val now = System.currentTimeMillis()
            if (startMs <= now) {
                Log.w(TAG, "Warning fired late for $scheduleId (window already started) — no lock")
                return
            }
            val schedule = ScheduledBlockScheduler.getLocalSchedules(context)
                .firstOrNull { it.id == scheduleId }
            if (schedule == null) {
                Log.i(TAG, "Warning fired but schedule $scheduleId deleted — nothing to lock")
                return
            }
            if (!schedule.is_enabled || !schedule.is_strict_mode) {
                Log.i(TAG, "Warning fired but schedule $scheduleId no longer enabled+strict — cancellation stands, nothing fires")
                return
            }
            setLock(context, scheduleId, startMs, subject)
            showWarningOverlay(context, subject, startMs)
        } catch (e: Exception) {
            Log.e(TAG, "handleWarningFired failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------ the overlay

    /**
     * Shows the 10-minute strict lock-in warning as a SYSTEM OVERLAY from
     * the receiver/alarm path — works even when Focivo is not open.
     * Path priority mirrors BlockOverlayManager: accessibility layer (no
     * overlay permission needed, window-proof on every OEM) -> app overlay
     * (needs "Display over other apps") -> fallback heads-up notification.
     */
    fun showWarningOverlay(context: Context, subject: String?, startMs: Long) {
        val appContext = context.applicationContext
        val label = if (!subject.isNullOrBlank()) "'$subject'" else "your scheduled session"
        // 1) Accessibility layer — strongest, works without overlay permission.
        if (showViaA11y(label, startMs)) {
            Log.i(TAG, "Strict warning overlay shown (a11y path)")
            return
        }
        // 2) App overlay layer.
        if (Settings.canDrawOverlays(appContext) && showViaAppOverlayOnMain(appContext, label, startMs)) {
            Log.i(TAG, "Strict warning overlay shown (app overlay path)")
            return
        }
        // 3) Fallback: the plain 10-minute heads-up notification.
        Log.w(TAG, "No overlay layer available — falling back to heads-up notification")
        FocivoNotifications.postScheduleReminder(appContext, subject)
    }

    /**
     * App-overlay path routed onto the main thread (view creation must run
     * there; callers may be on a background thread).
     */
    private fun showViaAppOverlayOnMain(appContext: Context, label: String, startMs: Long): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return showViaAppOverlay(appContext, label, startMs)
        }
        val result = java.util.concurrent.atomic.AtomicBoolean(false)
        val latch = java.util.concurrent.CountDownLatch(1)
        mainHandler.post {
            try {
                result.set(showViaAppOverlay(appContext, label, startMs))
            } finally {
                latch.countDown()
            }
        }
        latch.await(4, java.util.concurrent.TimeUnit.SECONDS)
        return result.get()
    }

    private fun showViaA11y(label: String, startMs: Long): Boolean {
        val svc = AiStudyAccessibilityService.connectedInstance ?: return false
        if (svc.isA11yStrictWarningShowing()) return true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return svc.showA11yStrictWarningOverlay(label, startMs)
        }
        val result = java.util.concurrent.atomic.AtomicBoolean(false)
        val latch = java.util.concurrent.CountDownLatch(1)
        mainHandler.post {
            try {
                result.set(svc.showA11yStrictWarningOverlay(label, startMs))
            } finally {
                latch.countDown()
            }
        }
        latch.await(4, java.util.concurrent.TimeUnit.SECONDS)
        return result.get()
    }

    private var appOverlayView: ComposeView? = null
    private var appOverlayHost: OverlayLifecycleHost? = null
    private var appOverlayWm: WindowManager? = null

    /**
     * App-process overlay path (TYPE_APPLICATION_OVERLAY). Runs on the main
     * thread. Returns true when the overlay is showing.
     */
    private fun showViaAppOverlay(appContext: Context, label: String, startMs: Long): Boolean {
        if (appOverlayView != null) return true
        try {
            val host = OverlayLifecycleHost()
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = ComposeView(appContext).apply {
                host.installOn(this)
                setContent {
                    FocuslyTheme {
                        StrictWarningOverlay(
                            subjectLabel = label,
                            startMs = startMs,
                            onDismiss = { hideAppOverlay() }
                        )
                    }
                }
                // Back = acknowledge (same as the primary button).
                isFocusableInTouchMode = true
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        hideAppOverlay()
                        true
                    } else false
                }
            }
            host.show()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            wm.addView(view, params)
            appOverlayView = view
            appOverlayHost = host
            appOverlayWm = wm
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "App-overlay strict warning failed: ${t.message}")
            return false
        }
    }

    /** Hide the app-process strict warning overlay, if showing. Any thread. */
    fun hideAppOverlay() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeAppOverlay()
        } else {
            mainHandler.post { removeAppOverlay() }
        }
    }

    private fun removeAppOverlay() {
        val view = appOverlayView ?: return
        try { appOverlayWm?.removeView(view) } catch (_: Exception) { }
        runCatching { appOverlayHost?.dismiss() }
        appOverlayView = null
        appOverlayHost = null
        appOverlayWm = null
    }
}
