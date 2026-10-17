package com.example.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import com.example.ui.components.FloatingPillDragController
import com.example.ui.components.FloatingTimerMiniPill
import com.example.ui.components.FloatingTimerPill
import com.example.ui.overlay.OverlayLifecycleHost
import com.example.ui.theme.FocuslyTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
 * v1.3.1 — the pill is now draggable horizontally (drag updates
 * LayoutParams.x, y stays fixed; small movements stay a tap). 3 seconds
 * after it appears, and 3 seconds after the last drag ends, it docks to
 * the nearest screen edge as a compact circular mini-pill (same design
 * tokens, lime-ring accent, "MM:SS"). Tapping the docked mini-pill expands
 * back to the full pill at its last x position. Dock/expand are smooth
 * ValueAnimator-driven x animations; hide() tears down everything.
 *
 * Wiring (unchanged — call sites untouched):
 *  - MainActivity.onUserLeaveHint / onPause: FloatingTimerPillManager.showIfSessionRunning(this)
 *  - MainActivity.onResume: FloatingTimerPillManager.hide(this)
 *  - FocusTimerService onDestroy / ACTION_STOP_TIMER: FloatingTimerPillManager.hide(context)
 */
object FloatingTimerPillManager {

    private const val TAG = "FloatingTimerPill"
    private const val DOCK_ANIM_DURATION_MS = 320L

    // Mirrors FocusTimerService private pref keys (kept local to avoid scope creep).
    private const val PREFS_LOCAL = "focusly_local_data"
    private const val KEY_MANUAL_RUNNING = "is_manual_timer_running"
    private const val KEY_MANUAL_END = "manual_timer_end_time_ms"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pillView: ComposeView? = null
    private var pillHost: OverlayLifecycleHost? = null
    private var windowManager: WindowManager? = null
    private var pillParams: WindowManager.LayoutParams? = null

    // v1.3.1 state
    private val dockedState = mutableStateOf(false)
    private var isDragging = false
    private var screenWidthPx = 0
    private var density = 1f
    private var lastExpandedXPx = 0
    private var dockAnimator: ValueAnimator? = null

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

    /** Hide the pill if visible. Safe to call any time. Tears down timers/animators. */
    fun hide(context: Context) {
        runOnMain {
            // v1.3.1: cancel the auto-dock timer and any in-flight animation first.
            cancelAutoDock()
            dockAnimator?.cancel()
            dockAnimator = null
            dockedState.value = false
            isDragging = false
            lastExpandedXPx = 0
            val view = pillView ?: return@runOnMain
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) { }
            // v1.5.1: tear down the overlay lifecycle host too.
            runCatching { pillHost?.dismiss() }
            pillView = null
            pillHost = null
            windowManager = null
            pillParams = null
        }
    }

    // ── v1.3.1 drag + dock internals ──────────────────────────────────────

    private val dockRunnable = Runnable { runOnMain { dockToNearestEdge() } }

    private fun scheduleAutoDock() {
        mainHandler.removeCallbacks(dockRunnable)
        mainHandler.postDelayed(dockRunnable, FloatingPillDocking.AUTO_DOCK_DELAY_MS)
    }

    private fun cancelAutoDock() {
        mainHandler.removeCallbacks(dockRunnable)
    }

    private val dragController = object : FloatingPillDragController {
        override fun onDragStart() {
            runOnMain {
                if (dockedState.value) return@runOnMain
                isDragging = true
                cancelAutoDock()
            }
        }

        override fun onDragDelta(deltaPx: Float) {
            runOnMain { nudgeExpandedPill(deltaPx) }
        }

        override fun onDragEnd() {
            runOnMain {
                if (dockedState.value) return@runOnMain
                isDragging = false
                lastExpandedXPx = pillParams?.x ?: lastExpandedXPx
                scheduleAutoDock() // re-dock 3s after the last interaction
            }
        }
    }

    /** Horizontal drag: shift LayoutParams.x, y fixed, clamped to screen. */
    private fun nudgeExpandedPill(deltaPx: Float) {
        if (isDragging.not() || dockedState.value) return
        val view = pillView ?: return
        val params = pillParams ?: return
        val width = view.width.takeIf { it > 0 } ?: return
        params.x = FloatingPillDocking.clampExpandedXPx(
            (params.x + deltaPx).roundToInt(), width, screenWidthPx
        )
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    /** 3s idle (after show or after last drag end): dock to the nearest edge. */
    private fun dockToNearestEdge() {
        val view = pillView ?: return
        val params = pillParams ?: return
        if (dockedState.value || isDragging) return
        val width = view.width.takeIf { it > 0 } ?: return
        val edge = FloatingPillDocking.nearestEdge(params.x, width, screenWidthPx)
        val dockedWidthPx = (FloatingPillDocking.DOCKED_SIZE_DP * density).roundToInt()
        val marginPx = (FloatingPillDocking.DOCK_MARGIN_DP * density).roundToInt()
        val targetX = FloatingPillDocking.dockedXPx(edge, screenWidthPx, dockedWidthPx, marginPx)
        dockedState.value = true // content swaps to the mini-pill; x then animates
        animatePillX(params.x, targetX)
    }

    /** Tap on the docked mini-pill: expand back to the full pill at its last x. */
    private fun expandPill() {
        val params = pillParams ?: return
        if (!dockedState.value || isDragging) return
        dockedState.value = false
        animatePillX(params.x, lastExpandedXPx)
    }

    /** Smooth x animation posting WindowManager.updateViewLayout steps. */
    private fun animatePillX(fromX: Int, toX: Int) {
        val view = pillView ?: return
        dockAnimator?.cancel()
        dockAnimator = null
        if (fromX == toX) return
        val animator = ValueAnimator.ofInt(fromX, toX).apply {
            duration = DOCK_ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val v = pillView ?: return@addUpdateListener
                val p = pillParams ?: return@addUpdateListener
                p.x = a.animatedValue as Int
                runCatching { windowManager?.updateViewLayout(v, p) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (dockAnimator === animation) dockAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (dockAnimator === animation) dockAnimator = null
                }
            })
        }
        dockAnimator = animator
        animator.start()
    }

    private fun show(appContext: Context, remainingSeconds: Int) {
        runOnMain {
            if (pillView != null) return@runOnMain // already showing
            val host = OverlayLifecycleHost()
            try {
                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = appContext.resources.displayMetrics
                density = metrics.density
                screenWidthPx = metrics.widthPixels
                // v1.5.1: overlay ComposeViews have no Activity/Fragment, so install a
                // dedicated lifecycle host — otherwise onAttachedToWindow throws
                // IllegalStateException("ViewTreeLifecycleOwner not found") (Android 16).
                val view = ComposeView(appContext).apply {
                    host.installOn(this)
                    setContent { PillHost(remainingSeconds = remainingSeconds) }
                }
                host.show()
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    // NOT_FOCUSABLE: touches outside the pill pass through; the
                    // wrap_content view itself only covers the pill area.
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    // v1.3.1: absolute x positioning (TOP|LEFT) so dragging can
                    // update x directly; y stays fixed for the whole session.
                    gravity = Gravity.TOP or Gravity.LEFT
                    x = 0 // re-centered after the first layout pass below
                    y = (density * 64).toInt()
                }
                wm.addView(view, params)
                pillView = view
                pillHost = host
                windowManager = wm
                pillParams = params
                dockedState.value = false
                isDragging = false
                // Center horizontally once the wrap_content view has measured.
                view.post {
                    val p = pillParams ?: return@post
                    val w = view.width
                    if (w > 0) {
                        p.x = FloatingPillDocking.clampExpandedXPx(
                            (screenWidthPx - w) / 2, w, screenWidthPx
                        )
                        lastExpandedXPx = p.x
                        runCatching { wm.updateViewLayout(view, p) }
                    }
                }
                scheduleAutoDock() // 3s after the pill appears
            } catch (_: Exception) {
                // Overlay add failed (permission revoked, etc.) — silent.
                runCatching { host.dismiss() }
                pillView = null
                pillHost = null
                windowManager = null
                pillParams = null
            }
        }
    }

    @Composable
    private fun PillHost(remainingSeconds: Int) {
        FocuslyTheme(darkTheme = true) {
            val isDocked by dockedState
            // v1.3.1: the countdown lives here so docking/expanding never resets it.
            var remaining by remember(remainingSeconds) {
                mutableIntStateOf(remainingSeconds.coerceAtLeast(0))
            }
            val dismiss = {
                pillView?.context?.let { hide(it) }
            }
            LaunchedEffect(Unit) {
                while (remaining > 0) {
                    delay(1000L)
                    remaining -= 1
                }
                dismiss()
            }
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(4.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isDocked) {
                    FloatingTimerMiniPill(
                        remainingSeconds = remaining,
                        onExpand = { expandPill() }
                    )
                } else {
                    FloatingTimerPill(
                        remainingSeconds = remaining,
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
                            dismiss()
                        },
                        dragController = dragController
                    )
                }
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
