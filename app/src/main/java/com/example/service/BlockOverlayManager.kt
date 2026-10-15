package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.example.MainActivity
import com.example.R
import com.example.ui.components.BlockedAppOverlay
import com.example.ui.components.ShortsBlockedOverlay
import com.example.ui.overlay.OverlayLifecycleHost
import com.example.ui.theme.FocuslyTheme

/**
 * v1.2.1 — the freeform/floating-window block overlay (fix #5).
 *
 * ROOT CAUSE: the block screen was a plain fullscreen Activity
 * (BlockedAppLockActivity). On vivo (and stock Android freeform), a blocked
 * app opened in a FLOATING window z-orders ABOVE the fullscreen activity
 * stack, so the app stayed fully visible and interactive on top of the
 * "Stay in Focus" card (owner screenshot, 2026-10-15).
 *
 * FIX: intercept with a full-screen TYPE_APPLICATION_OVERLAY window instead.
 * System overlay windows z-order above every app window — fullscreen,
 * split-screen AND freeform/floating — on Android 14–16 including vivo's
 * small-window implementation. The overlay hosts the SAME BlockedAppOverlay
 * UI as the Activity (consistent copy, mascot, Open Focivo / Stay in Focus
 * actions) and consumes all touches, so the floating window underneath is
 * neither visible nor interactive.
 *
 * HONEST LIMITS (see report):
 * - Requires the overlay ("Display over other apps") permission, which is
 *   part of the 7-permission Start-Study gate — without it we fall back to
 *   the legacy Activity (fullscreen/split still covered, freeform not).
 * - Detection of the floating window itself still rides the accessibility
 *   service's interactive-window scan (flagRetrieveInteractiveWindows);
 *   without the accessibility permission the usage-stats poll is the
 *   fallback and a freeform resume can be missed.
 * - OEMs can always change freeform z-ordering; this is the strongest
 *   mechanism the public Android APIs offer. Device-test required.
 */
object BlockOverlayManager {

    private const val TAG = "BlockOverlay"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: ComposeView? = null
    private var overlayHost: OverlayLifecycleHost? = null
    private var windowManager: WindowManager? = null

    private val blockedPackageState = mutableStateOf("")
    private val appNameState = mutableStateOf("")
    private val durationState = mutableStateOf(0)
    private val reasonState = mutableStateOf<String?>(null)
    private val punishmentState = mutableStateOf(false)

    // v1.2.2 (C): dedicated Shorts & Reels surface overlay — separate from the
    // main block overlay so a surface lock never disturbs an active block UI.
    private var shortsOverlayView: ComposeView? = null
    private var shortsOverlayHost: OverlayLifecycleHost? = null
    private var shortsOverlayWm: WindowManager? = null
    private val shortsLabelState = mutableStateOf("Shorts")

    fun isShowing(): Boolean = overlayView != null

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else mainHandler.post(action)
    }

    /**
     * Shows the block overlay if the overlay permission is granted.
     * Returns true when the overlay is (now) showing; false when the caller
     * must use the legacy Activity fallback instead.
     */
    fun showIfPermitted(
        context: Context,
        blockedPackage: String,
        appName: String,
        durationSec: Int = 0,
        reason: String? = null,
        isPunishment: Boolean = false
    ): Boolean {
        if (isShowing()) {
            // Refresh the payload on the already-visible overlay.
            blockedPackageState.value = blockedPackage
            appNameState.value = appName
            durationState.value = durationSec
            reasonState.value = reason
            punishmentState.value = isPunishment
            return true
        }
        if (!Settings.canDrawOverlays(context)) return false
        show(context.applicationContext, blockedPackage, appName, durationSec, reason, isPunishment)
        return isShowing()
    }

    private fun show(
        appContext: Context,
        blockedPackage: String,
        appName: String,
        durationSec: Int,
        reason: String?,
        isPunishment: Boolean
    ) {
        runOnMain {
            if (overlayView != null) return@runOnMain
            val host = OverlayLifecycleHost()
            try {
                blockedPackageState.value = blockedPackage
                appNameState.value = appName
                durationState.value = durationSec
                reasonState.value = reason
                punishmentState.value = isPunishment

                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val view = ComposeView(appContext).apply {
                    host.installOn(this)
                    setContent {
                        FocuslyTheme {
                            BlockedAppOverlay(
                                appName = appNameState.value,
                                blockedPackage = blockedPackageState.value,
                                initialDurationSec = durationState.value,
                                reason = reasonState.value,
                                isPunishment = punishmentState.value,
                                mascotRes = R.drawable.mascot_blocked,
                                onExitApp = { exitToHome(appContext) },
                                onOpenFocusly = { openFocivo(appContext) }
                            )
                        }
                    }
                    // Back button while the overlay has focus = "Stay in Focus".
                    isFocusableInTouchMode = true
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                            exitToHome(appContext)
                            true
                        } else false
                    }
                }
                host.show()
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    // No NOT_TOUCHABLE / NOT_FOCUSABLE: the overlay consumes
                    // every touch so the floating window underneath is dead.
                    // LAYOUT_IN_SCREEN so it covers status/nav areas too.
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                wm.addView(view, params)
                overlayView = view
                overlayHost = host
                windowManager = wm
                Log.i(TAG, "Block overlay shown for $blockedPackage (covers freeform windows)")
            } catch (t: Throwable) {
                Log.e(TAG, "Could not show block overlay: ${t.message}")
                runCatching { host.dismiss() }
                overlayView = null
                overlayHost = null
                windowManager = null
            }
        }
    }

    /** Hide the overlay if visible. Safe to call any time, any thread. */
    fun hide(context: Context) {
        runOnMain {
            val view = overlayView ?: return@runOnMain
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) { }
            runCatching { overlayHost?.dismiss() }
            overlayView = null
            overlayHost = null
            windowManager = null
        }
    }

    private fun exitToHome(appContext: Context) {
        hide(appContext)
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            appContext.startActivity(homeIntent)
        } catch (_: Exception) { }
    }

    private fun openFocivo(appContext: Context) {
        hide(appContext)
        try {
            val mainIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            appContext.startActivity(mainIntent)
        } catch (_: Exception) { }
    }

    /**
     * v1.2.1 — single choke point for every block-screen intercept.
     * Prefers the overlay (covers fullscreen, split-screen AND freeform);
     * falls back to the legacy fullscreen Activity when the overlay
     * permission is missing.
     */
    fun intercept(
        context: Context,
        blockedPackage: String,
        appName: String,
        durationSec: Int = 0,
        reason: String? = null,
        isPunishment: Boolean = false
    ) {
        if (showIfPermitted(context, blockedPackage, appName, durationSec, reason, isPunishment)) return
        try {
            val intent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                context = context,
                packageName = blockedPackage,
                appName = appName,
                durationSec = durationSec,
                reason = reason,
                isPunishment = isPunishment
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback block Activity failed: ${e.message}")
        }
    }

    /** True when any block UI (overlay or legacy Activity) is on screen. */
    fun isBlockUiShowing(): Boolean =
        isShowing() || com.example.ui.screens.BlockedAppLockActivity.isCurrentlyShowing

    // ------------------------------------------------------------------
    // v1.2.2 (C): Shorts & Reels surface overlay.
    //
    // Standalone surface lock: shown when the Shorts & Reels toggle is on and
    // ShortsSurfaceDetector finds a real Shorts/Reels surface. The
    // accessibility service performs a back-press first, so "Return" simply
    // dismisses the overlay back into the app (long-form stays watchable).
    // Never touches the AI discipline flow.
    // ------------------------------------------------------------------

    /** True when the Shorts & Reels overlay is on screen. */
    fun isShortsBlockShowing(): Boolean = shortsOverlayView != null

    /**
     * Shows the Shorts & Reels overlay. Returns true when it is (now)
     * showing; false when the overlay permission is missing (the caller
     * still performed the back-press, so the surface is gone regardless).
     */
    fun showShortsBlock(context: Context, surfaceLabel: String): Boolean {
        if (isShortsBlockShowing()) {
            shortsLabelState.value = surfaceLabel
            return true
        }
        if (!Settings.canDrawOverlays(context)) return false
        val appContext = context.applicationContext
        runOnMain {
            if (shortsOverlayView != null) return@runOnMain
            val host = OverlayLifecycleHost()
            try {
                shortsLabelState.value = surfaceLabel
                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val view = ComposeView(appContext).apply {
                    host.installOn(this)
                    setContent {
                        FocuslyTheme {
                            ShortsBlockedOverlay(
                                surfaceLabel = shortsLabelState.value,
                                onReturn = { hideShortsBlock() },
                                onOpenFocivo = {
                                    hideShortsBlock()
                                    openFocivo(appContext)
                                }
                            )
                        }
                    }
                    isFocusableInTouchMode = true
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                            hideShortsBlock()
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
                shortsOverlayView = view
                shortsOverlayHost = host
                shortsOverlayWm = wm
                Log.i(TAG, "Shorts & Reels overlay shown ($surfaceLabel)")
            } catch (t: Throwable) {
                Log.e(TAG, "Could not show Shorts overlay: ${t.message}")
                runCatching { host.dismiss() }
                shortsOverlayView = null
                shortsOverlayHost = null
                shortsOverlayWm = null
            }
        }
        return isShortsBlockShowing()
    }

    /** Hide the Shorts & Reels overlay if visible. Safe to call any time, any thread. */
    fun hideShortsBlock() {
        runOnMain {
            val view = shortsOverlayView ?: return@runOnMain
            try {
                shortsOverlayWm?.removeView(view)
            } catch (_: Exception) { }
            runCatching { shortsOverlayHost?.dismiss() }
            shortsOverlayView = null
            shortsOverlayHost = null
            shortsOverlayWm = null
        }
    }
}
