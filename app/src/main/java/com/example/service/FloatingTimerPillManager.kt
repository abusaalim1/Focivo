package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import com.example.ui.components.FloatingTimerPill
import com.example.ui.theme.FocuslyTheme

/**
 * v1.5.0 — owns the floating session countdown pill that floats over other
 * apps while a focus session runs and the user has left the app.
 *
 * Uses the same display-over-other-apps path as the repo's existing
 * TYPE_APPLICATION_OVERLAY usage (SessionCompleteScreen share-card capture):
 * WindowManager + TYPE_APPLICATION_OVERLAY, gated on
 * Settings.canDrawOverlays(). If the permission is not granted, every call
 * here is a silent no-op — nothing is requested or shown.
 *
 * Tapping the pill returns to the app (launches MainActivity); the X button
 * only hides the pill — it ends nothing (the session keeps running).
 *
 * Wiring (intentionally left to the integrator — see report):
 *  - MainActivity.onUserLeaveHint / onPause: FloatingTimerPillManager.showIfSessionRunning(this)
 *  - MainActivity.onResume: FloatingTimerPillManager.hide(this)
 *  - FocusTimerService onDestroy / ACTION_STOP_TIMER: FloatingTimerPillManager.hide(context)
 */
object FloatingTimerPillManager {

    private const val TAG = "FloatingTimerPill"

    // Mirrors FocusTimerService private pref keys (kept local to avoid scope creep).
    private const val PREFS_LOCAL = "focusly_local_data"
    private const val KEY_MANUAL_RUNNING = "is_manual_timer_running"
    private const val KEY_MANUAL_END = "manual_timer_end_time_ms"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pillView: ComposeView? = null
    private var windowManager: WindowManager? = null

    fun isShowing(): Boolean = pillView != null

    /**
     * Remaining seconds of the running session, or 0 when none runs.
     * Checks the manual focus timer first, then the shield lock.
     */
    fun getSessionRemainingSeconds(context: Context): Int {
        return try {
            val prefs = context.getSharedPreferences(PREFS_LOCAL, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_MANUAL_RUNNING, false)) {
                val end = prefs.getLong(KEY_MANUAL_END, 0L)
                val rem = ((end - System.currentTimeMillis()) / 1000L).toInt()
                if (rem > 0) return rem
            }
            FocusShieldService.getRemainingSeconds(context)
        } catch (_: Exception) {
            0
        }
    }

    /** Show the pill if a session is running. Silent no-op without overlay permission. */
    fun showIfSessionRunning(context: Context) {
        if (!Settings.canDrawOverlays(context)) return // permission skip: silent
        val remaining = getSessionRemainingSeconds(context)
        if (remaining <= 0) return
        show(context.applicationContext, remaining)
    }

    /** Hide the pill if visible. Safe to call any time. */
    fun hide(context: Context) {
        runOnMain {
            val view = pillView ?: return@runOnMain
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) { }
            pillView = null
            windowManager = null
        }
    }

    private fun show(appContext: Context, remainingSeconds: Int) {
        runOnMain {
            if (pillView != null) return@runOnMain // already showing
            try {
                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val view = ComposeView(appContext).apply {
                    setContent { PillHost(remainingSeconds = remainingSeconds) }
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    // NOT_FOCUSABLE: touches outside the pill pass through; the
                    // wrap_content view itself only covers the pill area.
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = (appContext.resources.displayMetrics.density * 64).toInt()
                }
                wm.addView(view, params)
                pillView = view
                windowManager = wm
            } catch (_: Exception) {
                // Overlay add failed (permission revoked, etc.) — silent.
                pillView = null
                windowManager = null
            }
        }
    }

    @Composable
    private fun PillHost(remainingSeconds: Int) {
        FocuslyTheme(darkTheme = true) {
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(4.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                FloatingTimerPill(
                    initialRemainingSeconds = remainingSeconds,
                    onReturnToApp = {
                        val ctx = pillView?.context
                        pillView?.context?.let { c ->
                            runCatching {
                                c.startActivity(
                                    Intent(c, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                )
                            }
                        }
                        ctx?.let { hide(it) }
                    },
                    onDismiss = {
                        // X hides the pill only — ends nothing.
                        pillView?.context?.let { hide(it) }
                    }
                )
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { block() }
        } else {
            mainHandler.post { runCatching { block() } }
        }
    }
}
