package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.MainActivity
import com.example.util.AiStudyGuardManager
import com.example.util.BlockedChannelsManager
import com.example.util.DeepFocusManager
import com.example.util.EssentialAppsGuard
import com.example.util.GeminiContentClassifier
import com.example.util.LocalTitleClassifier
import com.example.util.OnDeviceStudyClassifier
import com.example.util.ShortsLockManager
import com.example.util.ShortsSurfaceDetector
import com.example.util.StrictModeManager
import com.example.util.StudyAllowlistManager
import com.example.util.StudyGuardKnowledge
import com.example.util.YouTubeStudyGuardManager
import com.example.util.YouTubeVideoClassifier
import com.example.util.YouTubePlaybackGate
import com.example.util.YouTubeGuardOrchestrator
import com.example.util.YouTubeTitleJudge
import com.example.util.YtGateNode
import com.example.util.YtPlaybackSignals
import com.example.util.YtPlayerSurface
import com.example.util.brain.BrainBundleLoader
import com.example.util.brain.FocivoBrainCorrections
import com.example.util.brain.FocivoCombinedBrain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class AiStudyAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val TAG = "AiAccessibility"

        @Volatile
        var isServiceConnected: Boolean = false

        /**
         * v1.2.3 — live service instance for the window-proof block overlay.
         * Set on connect, cleared on unbind/destroy. BlockOverlayManager
         * routes overlay show/hide through it when non-null (the
         * TYPE_ACCESSIBILITY_OVERLAY path); null means the service is not
         * running and the legacy app-overlay / Activity fallbacks apply.
         */
        @Volatile
        var connectedInstance: AiStudyAccessibilityService? = null
            private set

        private var lastShortsInterceptTimeMs: Long = 0L
        private var lastYouTubeAiInterceptTimeMs: Long = 0L
        private var lastScreenAiInterceptTimeMs: Long = 0L
        private var lastDeepFocusInterceptTimeMs: Long = 0L
        private var lastGeneralAppInterceptTimeMs: Long = 0L
        private var lastGeneralInterceptedPkg: String? = null
        private var lastPunishmentInterceptTimeMs: Long = 0L
        private var lastPunishedPackageIntercepted: String? = null
        private val lastEvaluatedScreenSignatures = LinkedHashMap<String, Long>()
        private val lastAiChatScanMs = mutableMapOf<String, Long>()
        // v1.1.6: throttle for Gemini tie-breaker on ambiguous AI-chat messages
        private val lastAiChatGeminiMs = mutableMapOf<String, Long>()
        // v1.1.6: liveness heartbeat write throttle
        private var lastLivenessWriteMs: Long = 0L
        // v1.3.0: multi-window protection scan throttle
        private var lastMultiWindowScanMs: Long = 0L
        // v1.2.3: periodic sweep throttle (catches idle windows that emit no events)
        private var lastPeriodicMwScanMs: Long = 0L
        // v1.3.1: strict-session tamper guard throttle (Focivo-targeted
        // uninstall / app-info / device-admin screens mid-session)
        private var lastTamperInterceptMs: Long = 0L
        private var lastTamperKey: String? = null
        // v1.3.x (item 3): content-changed evaluation throttle per package
        // (typeWindowContentChanged fires per keystroke/scroll).
        private var lastTamperEvalMs: Long = 0L
        private var lastTamperEvalKey: String? = null
        // v1.3.x (item 3): "seen but not protected" diagnostic throttle per
        // (reason, pkg) — the next device test shows exactly why an attempt
        // did NOT fire, without spamming the hub.
        private val lastTamperSkipLogMs = mutableMapOf<String, Long>()
        // v1.3.1: deep-focus home-screen block throttle
        private var lastHomeScreenInterceptMs: Long = 0L
        private var lastHomeInterceptedPkg: String? = null
        // v1.3.3 (ITEM 2): YouTube observe-path hub log throttle (per title).
        private var lastYtObserveKey: String? = null
        private var lastYtObserveMs: Long = 0L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        connectedInstance = this
        // v1.2.3 (Tasks 6 & 9): warm both on-device classifier brains off the
        // main thread so the first title/screen never pays the bundle-parse
        // cost on the accessibility callback thread.
        // v1.3.0 (Worker E): also warm the ported Focivo repo brain
        // (model + lexicon + channels).
        serviceScope.launch {
            runCatching { LocalTitleClassifier.get(applicationContext) }
            runCatching { LocalTitleClassifier.getContent(applicationContext) }
            runCatching { BrainBundleLoader.get(applicationContext) }
        }
        // v1.2.3-hotfix: start the real 5s multi-window sweep loop.
        a11yMainHandler.removeCallbacks(mwSweepRunnable)
        a11yMainHandler.postDelayed(mwSweepRunnable, 5000L)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isServiceConnected = false
        connectedInstance = null
        // v1.2.3: never leave a system-level block overlay orphaned when the
        // service goes away.
        runCatching { hideA11yBlockOverlay() }
        runCatching { hideA11yShortsOverlay() }
        a11yMainHandler.removeCallbacks(mwSweepRunnable)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isServiceConnected = false
        connectedInstance = null
        runCatching { hideA11yBlockOverlay() }
        runCatching { hideA11yShortsOverlay() }
        a11yMainHandler.removeCallbacks(mwSweepRunnable)
        super.onDestroy()
        serviceScope.cancel()
    }

    // ------------------------------------------------------------------
    // v1.2.3 — window-proof block overlay via TYPE_ACCESSIBILITY_OVERLAY.
    //
    // ROOT CAUSE (owner's vivo V2403, 1.2.1/1.2.2): the block screen was a
    // TYPE_APPLICATION_OVERLAY window added from the app process. On
    // vivo/OriginOS the freeform "small window" container is composited by
    // the OEM window manager at a layer that can sit ABOVE third-party
    // application overlays, and getWindows() may not even report the
    // freeform window — so the block screen rendered underneath (or never
    // fired for) the floating/split app. "Small window lock nhi hota hai."
    //
    // FIX: render the SAME block UI through the accessibility service with
    // WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY. This layer is
    // reserved for accessibility services and is composited above every app
    // window on every OEM — fullscreen, split-screen AND freeform/small
    // windows. It needs no SYSTEM_ALERT_WINDOW permission: the overlay works
    // exactly when the detection that triggers it works (the service itself).
    //
    // The 1.1 crash class (ViewTreeLifecycleOwner on a bare ComposeView) is
    // avoided with the same OverlayLifecycleHost pattern that fixed it.
    // ------------------------------------------------------------------

    private val a11yMainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * v1.2.3-hotfix — REAL periodic multi-window sweep. The old "periodic"
     * sweep was gated on accessibility EVENTS arriving: an idle blocked app
     * sitting in a freeform/split window emits no events, so the sweep never
     * ran and the app was never intercepted. This loop fires every 5s on the
     * service's main thread for as long as the service is connected; the
     * scan itself is study-period gated and 3s-throttled, so idle cost is
     * one cheap windows-list read per 5s.
     */
    private val mwSweepRunnable = object : Runnable {
        override fun run() {
            try {
                if (AiStudyGuardManager.isStudyPeriodActive(applicationContext)) {
                    enforceMultiWindowProtections(
                        System.currentTimeMillis(), "timer-sweep"
                    )
                    // v1.3.0: also re-glue here — covers window moves the OS
                    // didn't report as TYPE_WINDOWS_CHANGED.
                    glueWindowedOverlayToWindow()
                }
            } catch (_: Exception) { }
            a11yMainHandler.postDelayed(this, 5000L)
        }
    }
    private var a11yBlockView: androidx.compose.ui.platform.ComposeView? = null
    private var a11yBlockHost: com.example.ui.overlay.OverlayLifecycleHost? = null
    private var a11yBlockWm: WindowManager? = null
    // v1.2.4: bounds the current block overlay is clipped to (null =
    // fullscreen). Split-pane / freeform interceptions cover ONLY the
    // offending window so the rest of the screen stays usable.
    private var a11yBlockBounds: android.graphics.Rect? = null
    // v1.3.0: windowed block variant ("floating" | "split" | null) — the
    // compact small-window message for split-pane / freeform intercepts.
    // Null = the existing fullscreen copy.
    private val a11yWindowedMode = androidx.compose.runtime.mutableStateOf<String?>(null)
    // v1.3.0: the blocked package whose small window is currently tracked
    // (glue loop + z-order watchdog). ONLY ever a blocked package — never
    // another app's window.
    private var mwTrackedPkg: String? = null
    // v1.3.0: glue loop throttle (re-measures the tracked window on every
    // window-set change + the 5s sweep).
    private var lastGlueMs = 0L
    private var lastGlueLogMs = 0L
    // v1.3.0: z-order watchdog state. Armed when a window-bounded overlay is
    // placed; evaluated once after 4s. A touch-consuming overlay means any
    // tap/scroll/navigation inside the blocked app's window proves the OEM
    // composited the app ABOVE our overlay (vivo freeform layers do this).
    private var zWatchPkg: String? = null
    private var zWatchStrongHits = 0
    private var zWatchScrollHits = 0
    private var zWatchStartMs = 0L
    private val zWatchRunnable = Runnable { evaluateZOrderWatchdog() }
    private val a11yBlockedPackage = androidx.compose.runtime.mutableStateOf("")
    private val a11yAppName = androidx.compose.runtime.mutableStateOf("")
    private val a11yDurationSec = androidx.compose.runtime.mutableStateOf(0)
    private val a11yReason = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val a11yPunishment = androidx.compose.runtime.mutableStateOf(false)
    // v1.3.4 (ITEM 4): warning-variant as state — the reuse branch below
    // refreshes an already-visible overlay (warn -> block upgrade); the
    // mascot must follow the CURRENT variant, not the one captured at
    // composition time.
    private val a11yWarningVariant = androidx.compose.runtime.mutableStateOf(false)
    // v1.3.3 (item 8b): screen-time break variant payload.
    private val a11yBreakInfo =
        androidx.compose.runtime.mutableStateOf<com.example.ui.components.BreakOverlayInfo?>(null)

    private var a11yShortsView: androidx.compose.ui.platform.ComposeView? = null
    private var a11yShortsHost: com.example.ui.overlay.OverlayLifecycleHost? = null
    private var a11yShortsWm: WindowManager? = null
    private val a11yShortsLabel = androidx.compose.runtime.mutableStateOf("Shorts")

    // v1.2.3 (Task 13.1) — strict lock-in 10-minute warning overlay
    // (TYPE_ACCESSIBILITY_OVERLAY layer, fired from the alarm path).
    private var a11yStrictWarningView: androidx.compose.ui.platform.ComposeView? = null
    private var a11yStrictWarningHost: com.example.ui.overlay.OverlayLifecycleHost? = null
    private var a11yStrictWarningWm: WindowManager? = null
    private val a11yStrictWarningLabel = androidx.compose.runtime.mutableStateOf("")
    private val a11yStrictWarningStartMs = androidx.compose.runtime.mutableStateOf(0L)

    /**
     * v1.2.3 — draw confirmation for the accessibility block overlay.
     * addView succeeding does NOT prove the frame ever composited (the vivo
     * freeform question); a one-shot OnDrawListener flips this on the first
     * real draw. BlockOverlayManager uses it for the no-draw braces fallback.
     */
    @Volatile
    private var a11yBlockDrawn = false

    /** True once the accessibility block overlay has actually drawn a frame. */
    fun isA11yBlockOverlayDrawn(): Boolean = a11yBlockDrawn

    // v1.2.3 — throttled "seen but skipped" diagnostics for the Guard Hub
    // (key = reason; proves the scan ran and says why it skipped).
    private val lastMwSkipLogMs = mutableMapOf<String, Long>()

    /** True when the accessibility-layer block overlay is on screen. */
    fun isA11yBlockOverlayShowing(): Boolean = a11yBlockView != null

    /**
     * v1.2.3 (owner 2026-10-16): package currently named by the
     * accessibility-layer block/warning overlay ("" when hidden). Lets
     * BlockOverlayManager dismiss exactly the warning it showed without
     * touching an unrelated block overlay.
     */
    fun a11yBlockOverlayPackage(): String = a11yBlockedPackage.value

    /** True when the accessibility-layer Shorts overlay is on screen. */
    fun isA11yShortsOverlayShowing(): Boolean = a11yShortsView != null

    /** True when the accessibility-layer strict warning overlay is on screen. */
    fun isA11yStrictWarningShowing(): Boolean = a11yStrictWarningView != null

    private fun runOnA11yMain(action: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) action()
        else a11yMainHandler.post(action)
    }

    /**
     * v1.2.4 — window-bounded block overlay (owner order 2026-10-16 19:57):
     * when a blocked app is caught in a split pane or a freeform/small
     * window, [bounds] (the offending AccessibilityWindowInfo bounds in
     * screen coordinates) clips the TYPE_ACCESSIBILITY_OVERLAY to exactly
     * that window — the block appears INSIDE the small window, never on
     * the launcher/home. Null bounds = the existing fullscreen path.
     */
    private fun a11yOverlayParams(bounds: android.graphics.Rect? = null): WindowManager.LayoutParams {
        val useBounds = bounds != null && bounds.width() > 0 && bounds.height() > 0
        return WindowManager.LayoutParams(
            if (useBounds) bounds!!.width() else WindowManager.LayoutParams.MATCH_PARENT,
            if (useBounds) bounds!!.height() else WindowManager.LayoutParams.MATCH_PARENT,
            // Renders above ALL app windows (incl. OEM freeform layers) —
            // the strongest layer a third-party app can use.
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            if (useBounds) {
                // bounds are screen coordinates (getBoundsInScreen) and
                // FLAG_LAYOUT_IN_SCREEN keeps the window in screen space.
                x = bounds!!.left
                y = bounds!!.top
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * Shows the block overlay on the accessibility layer. Must be called on
     * the main thread (BlockOverlayManager routes here via Handler).
     * Returns true when the overlay is (now) showing.
     *
     * v1.2.3 (owner 2026-10-16): [warningVariant] renders the Stage-1 guard
     * warning through this same window-proof layer (Android 10+ silently
     * drops the old background startActivity, so the warning never
     * appeared). Lime accent + warning mascot + warning reason copy — not a
     * punishment.
     */
    fun showA11yBlockOverlay(
        blockedPackage: String,
        appName: String,
        durationSec: Int = 0,
        reason: String? = null,
        isPunishment: Boolean = false,
        warningVariant: Boolean = false,
        // v1.2.4: offending window bounds (screen coords) — clips the
        // overlay to exactly that window. Null = fullscreen path.
        windowBounds: android.graphics.Rect? = null,
        // v1.3.0: "floating" | "split" — the compact small-window message
        // for split-pane / freeform interceptions. Null = fullscreen copy.
        windowedMode: String? = null,
        // v1.3.3 (item 8b): screen-time break variant payload.
        breakInfo: com.example.ui.components.BreakOverlayInfo? = null
    ): Boolean {
        if (a11yBlockView != null) {
            a11yBlockedPackage.value = blockedPackage
            a11yAppName.value = appName
            a11yDurationSec.value = durationSec
            a11yReason.value = reason
            a11yPunishment.value = isPunishment
            a11yWarningVariant.value = warningVariant
            a11yWindowedMode.value = windowedMode
            a11yBreakInfo.value = breakInfo
            // Reposition to the offending window when a (different) bound
            // is supplied — e.g. fullscreen overlay followed by a
            // small-window intercept of the same package.
            if (windowBounds != null && windowBounds != a11yBlockBounds) {
                a11yBlockBounds = android.graphics.Rect(windowBounds)
                try {
                    a11yBlockWm?.updateViewLayout(a11yBlockView, a11yOverlayParams(a11yBlockBounds))
                } catch (_: Exception) { }
            }
            return true
        }
        val host = com.example.ui.overlay.OverlayLifecycleHost()
        return try {
            a11yBlockedPackage.value = blockedPackage
            a11yAppName.value = appName
            a11yDurationSec.value = durationSec
            a11yReason.value = reason
            a11yPunishment.value = isPunishment
            a11yWarningVariant.value = warningVariant
            a11yWindowedMode.value = windowedMode
            a11yBreakInfo.value = breakInfo
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = androidx.compose.ui.platform.ComposeView(this).apply {
                host.installOn(this)
                setContent {
                    com.example.ui.theme.FocuslyTheme {
                        com.example.ui.components.BlockedAppOverlay(
                            appName = a11yAppName.value,
                            blockedPackage = a11yBlockedPackage.value,
                            initialDurationSec = a11yDurationSec.value,
                            reason = a11yReason.value,
                            isPunishment = a11yPunishment.value,
                            breakInfo = a11yBreakInfo.value,
                            mascotRes = if (a11yWarningVariant.value) com.example.R.drawable.mascot_warning_transparent
                            else com.example.R.drawable.mascot_blocked,
                            windowedMode = a11yWindowedMode.value,
                            onExitApp = { a11yExitToHome() },
                            onOpenFocusly = { a11yOpenFocivo() }
                        )
                    }
                }
                isFocusableInTouchMode = true
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        a11yExitToHome()
                        true
                    } else false
                }
            }
            host.show()
            // v1.2.4: clip to the offending window when bounds were given.
            a11yBlockBounds = windowBounds?.let { android.graphics.Rect(it) }
            wm.addView(view, a11yOverlayParams(a11yBlockBounds))
            a11yBlockView = view
            a11yBlockHost = host
            a11yBlockWm = wm
            // v1.2.3: one-shot draw confirmation (see isA11yBlockOverlayDrawn).
            a11yBlockDrawn = false
            runCatching {
                view.viewTreeObserver.addOnDrawListener(
                    object : android.view.ViewTreeObserver.OnDrawListener {
                        override fun onDraw() {
                            a11yBlockDrawn = true
                            runCatching { view.viewTreeObserver.removeOnDrawListener(this) }
                        }
                    }
                )
            }
            Log.i(TAG, "A11y block overlay shown for $blockedPackage (TYPE_ACCESSIBILITY_OVERLAY)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "A11y block overlay failed: ${t.message}")
            runCatching { host.dismiss() }
            a11yBlockView = null
            a11yBlockHost = null
            a11yBlockWm = null
            false
        }
    }

    /** Hide the accessibility-layer block overlay if visible. Main thread. */
    fun hideA11yBlockOverlay() {
        // v1.3.0: a hidden overlay owns no window — drop the glue tracking
        // and the z-order watchdog with it.
        disarmZOrderWatchdog()
        mwTrackedPkg = null
        runOnA11yMain {
            val view = a11yBlockView ?: return@runOnA11yMain
            try { a11yBlockWm?.removeView(view) } catch (_: Exception) { }
            runCatching { a11yBlockHost?.dismiss() }
            a11yBlockView = null
            a11yBlockHost = null
            a11yBlockWm = null
            a11yBlockBounds = null
            a11yWindowedMode.value = null
            a11yBlockDrawn = false
        }
    }

    // ------------------------------------------------------------------
    // v1.3.0 — window-bounded block: glue + z-order watchdog (owner order
    // 2026-10-17).
    //
    // ROOT CAUSE (owner's vivo V2403, rounds 1-3): the full-screen block
    // fires, the draw listener fires, but the freeform window floats
    // UNBLOCKED on top / the split pane still shows the locked app. The
    // overlay drew — it just lost the z-order fight to the OEM's small-window
    // container. Two mechanisms close the gap:
    //
    // (a) GLUE: the bounds-matched overlay is re-measured against the live
    //     window list on every TYPE_WINDOWS_CHANGED (and the 5s sweep) and
    //     repositioned with updateViewLayout when the window moves/resizes.
    // (b) Z-ORDER WATCHDOG: the bounds overlay is deliberately
    //     touch-consuming, so any tap/scroll/navigation inside the blocked
    //     app's window while it is up proves the overlay lost z-order. On
    //     proof, GLOBAL_ACTION_HOME collapses the small window and the
    //     existing overlay expands into the fullscreen block notice.
    //
    // Both mechanisms act ONLY on the tracked blocked package — never on
    // another app's window. On stock Android (overlay wins z-order) the
    // watchdog simply records "clean" and disarms.
    // ------------------------------------------------------------------

    /**
     * Starts tracking a freshly placed window-bounded block: the glue loop
     * repositions the overlay as the window moves, and the z-order watchdog
     * verifies the overlay actually won the layer fight. Call only with the
     * blocked package — never another app's window.
     */
    private fun trackWindowedBlock(pkg: String, mode: String, bounds: android.graphics.Rect) {
        mwTrackedPkg = pkg
        armZOrderWatchdog(pkg)
        Log.i(TAG, "[MultiWindow] tracking $mode window for $pkg " +
            "(${bounds.width()}x${bounds.height()}@${bounds.left},${bounds.top}) — glue + z-order watch armed")
    }

    /**
     * Keeps the window-bounded block overlay GLUED to the offending window:
     * re-measures the tracked blocked window and repositions the overlay
     * with updateViewLayout when it moved/resized. A window that grew to
     * cover the display becomes the fullscreen block. A window that is gone
     * is left alone — the existing hide / re-intercept paths own that.
     */
    private fun glueWindowedOverlayToWindow() {
        val now = System.currentTimeMillis()
        if (now - lastGlueMs < 700L) return
        lastGlueMs = now
        val trackedPkg = mwTrackedPkg ?: return
        if (a11yBlockView == null || a11yBlockBounds == null) return
        if (!trackedPkg.equals(a11yBlockedPackage.value, ignoreCase = true)) return
        runOnA11yMain {
            try {
                val wins = try { windows } catch (_: Exception) { null } ?: return@runOnA11yMain
                val target = wins.firstOrNull {
                    try {
                        it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                            it.root?.packageName?.toString().equals(trackedPkg, ignoreCase = true)
                    } catch (_: Exception) { false }
                } ?: return@runOnA11yMain // Window gone — hide/re-intercept paths own this.
                val r = Rect()
                try { target.getBoundsInScreen(r) } catch (_: Exception) { return@runOnA11yMain }
                if (r.width() <= 0 || r.height() <= 0) return@runOnA11yMain
                val view = a11yBlockView ?: return@runOnA11yMain
                val (dispW, dispH) = getDisplaySizePx()
                if (dispW > 0 && dispH > 0 &&
                    r.width() >= dispW * 0.95f && r.height() >= dispH * 0.95f
                ) {
                    // The window grew to cover the display — expand the
                    // overlay into the fullscreen block (message stays).
                    a11yBlockBounds = null
                    a11yBlockWm?.updateViewLayout(view, a11yOverlayParams(null))
                    logGlue("fullscreen-expand", "${r.width()}x${r.height()}")
                    return@runOnA11yMain
                }
                val old = a11yBlockBounds ?: return@runOnA11yMain
                val moved = kotlin.math.abs(r.left - old.left) + kotlin.math.abs(r.top - old.top) +
                    kotlin.math.abs(r.width() - old.width()) + kotlin.math.abs(r.height() - old.height())
                if (moved > 6) {
                    val nb = android.graphics.Rect(r)
                    a11yBlockBounds = nb
                    a11yBlockWm?.updateViewLayout(view, a11yOverlayParams(nb))
                    logGlue(
                        "repositioned",
                        "${old.width()}x${old.height()}@${old.left},${old.top} -> " +
                            "${nb.width()}x${nb.height()}@${nb.left},${nb.top}"
                    )
                }
            } catch (_: Exception) { }
        }
    }

    /** Throttled Guard Hub evidence for glue actions. */
    private fun logGlue(action: String, detail: String) {
        val now = System.currentTimeMillis()
        if (now - lastGlueLogMs < 30_000L) return
        lastGlueLogMs = now
        try {
            AiStudyGuardManager.logGuardActivity(
                applicationContext, a11yAppName.value.ifBlank { "guard" },
                "window-glue", action, detail
            )
        } catch (_: Exception) { }
    }

    /**
     * Arms the z-order watchdog for a freshly placed window-bounded overlay.
     * Evaluated once after 4s; disarmed on hide. Watches ONLY the blocked
     * package — never another app's window.
     */
    private fun armZOrderWatchdog(pkg: String) {
        disarmZOrderWatchdog()
        zWatchPkg = pkg
        zWatchStrongHits = 0
        zWatchScrollHits = 0
        zWatchStartMs = System.currentTimeMillis()
        a11yMainHandler.postDelayed(zWatchRunnable, 4000L)
        try {
            AiStudyGuardManager.logGuardActivity(
                applicationContext, a11yAppName.value.ifBlank { pkg },
                "zorder-watch", "armed",
                "pkg=$pkg oem=${Build.MANUFACTURER} window=4s"
            )
        } catch (_: Exception) { }
    }

    private fun disarmZOrderWatchdog() {
        zWatchPkg = null
        zWatchStrongHits = 0
        zWatchScrollHits = 0
        zWatchStartMs = 0L
        a11yMainHandler.removeCallbacks(zWatchRunnable)
    }

    /**
     * Z-order watchdog event counting. The window-bounded overlay consumes
     * every touch (no NOT_TOUCHABLE flag), so interactive events from the
     * blocked package while it is up prove the OEM composited the app window
     * ABOVE our overlay. Scrolls alone are weak evidence (autoplay feeds
     * scroll themselves): one strong hit (tap / long-press / in-app
     * navigation) — or three scrolls — arms the escalation.
     */
    private fun countZOrderWatchEvent(pkg: String, eventType: Int) {
        val watchPkg = zWatchPkg ?: return
        if (!pkg.equals(watchPkg, ignoreCase = true)) return
        if (a11yBlockView == null || a11yBlockBounds == null || !a11yBlockDrawn) return
        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                zWatchStrongHits++
                Log.w(TAG, "[ZOrderWatch] strong hit #$zWatchStrongHits for $pkg " +
                    "(${AccessibilityEvent.eventTypeToString(eventType)}) — overlay may have lost z-order")
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                zWatchScrollHits++
            }
        }
    }

    /** One-shot 4s evaluation of the z-order watchdog. */
    private fun evaluateZOrderWatchdog() {
        val pkg = zWatchPkg
        if (pkg == null || a11yBlockView == null || a11yBlockBounds == null) {
            disarmZOrderWatchdog()
            return
        }
        if (!a11yBlockDrawn) {
            // Never composited — the no-draw braces watchdog in
            // BlockOverlayManager owns this case, not the z-order path.
            disarmZOrderWatchdog()
            return
        }
        val lost = zWatchStrongHits >= 1 || zWatchScrollHits >= 3
        try {
            AiStudyGuardManager.logGuardActivity(
                applicationContext, a11yAppName.value.ifBlank { pkg },
                "zorder-watch", if (lost) "lost" else "clean",
                "strong=$zWatchStrongHits scroll=$zWatchScrollHits oem=${Build.MANUFACTURER}"
            )
        } catch (_: Exception) { }
        if (lost) escalateZOrderLoss(pkg) else disarmZOrderWatchdog()
    }

    /**
     * Z-order escalation: the OEM won the layer fight, so collapse the small
     * window with GLOBAL_ACTION_HOME and expand the existing overlay into
     * the fullscreen block notice (the small-window message stays — it is
     * still why the app was blocked). Only the blocked app's window is ever
     * targeted; HOME is a system action, never a touch on another app's
     * window.
     */
    private fun escalateZOrderLoss(pkg: String) {
        disarmZOrderWatchdog()
        // The tracked window is being collapsed — forget it.
        mwTrackedPkg = null
        Log.w(TAG, "[ZOrderWatch] escalating: GLOBAL_ACTION_HOME for $pkg " +
            "(overlay lost z-order on ${Build.MANUFACTURER})")
        try {
            AiStudyGuardManager.logGuardActivity(
                applicationContext, a11yAppName.value.ifBlank { pkg },
                "zorder-escalation", "home",
                "action=GLOBAL_ACTION_HOME (collapse small window); block notice -> fullscreen"
            )
        } catch (_: Exception) { }
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (t: Throwable) {
            Log.e(TAG, "[ZOrderWatch] GLOBAL_ACTION_HOME failed: ${t.message}")
        }
        // Show the block notice: expand the already-placed overlay to the
        // fullscreen block layout.
        runOnA11yMain {
            val view = a11yBlockView ?: return@runOnA11yMain
            a11yBlockBounds = null
            try {
                a11yBlockWm?.updateViewLayout(view, a11yOverlayParams(null))
            } catch (t: Throwable) {
                Log.e(TAG, "[ZOrderWatch] fullscreen expand failed: ${t.message}")
            }
        }
    }

    /**
     * v1.2.3 (Task 13.1) — shows the strict lock-in 10-minute warning on the
     * accessibility layer. Must be called on the main thread (StrictLockInManager
     * routes here via Handler). Returns true when the overlay is (now) showing.
     */
    fun showA11yStrictWarningOverlay(subjectLabel: String, startMs: Long): Boolean {
        if (a11yStrictWarningView != null) {
            a11yStrictWarningLabel.value = subjectLabel
            a11yStrictWarningStartMs.value = startMs
            return true
        }
        val host = com.example.ui.overlay.OverlayLifecycleHost()
        return try {
            a11yStrictWarningLabel.value = subjectLabel
            a11yStrictWarningStartMs.value = startMs
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = androidx.compose.ui.platform.ComposeView(this).apply {
                host.installOn(this)
                setContent {
                    com.example.ui.theme.FocuslyTheme {
                        com.example.ui.components.StrictWarningOverlay(
                            subjectLabel = a11yStrictWarningLabel.value,
                            startMs = a11yStrictWarningStartMs.value,
                            onDismiss = { hideA11yStrictWarningOverlay() }
                        )
                    }
                }
                isFocusableInTouchMode = true
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        hideA11yStrictWarningOverlay()
                        true
                    } else false
                }
            }
            host.show()
            wm.addView(view, a11yOverlayParams())
            a11yStrictWarningView = view
            a11yStrictWarningHost = host
            a11yStrictWarningWm = wm
            Log.i(TAG, "A11y strict warning overlay shown (TYPE_ACCESSIBILITY_OVERLAY)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "A11y strict warning overlay failed: ${t.message}")
            runCatching { host.dismiss() }
            a11yStrictWarningView = null
            a11yStrictWarningHost = null
            a11yStrictWarningWm = null
            false
        }
    }

    /** Hide the accessibility-layer strict warning overlay if visible. Main thread. */
    fun hideA11yStrictWarningOverlay() {
        runOnA11yMain {
            val view = a11yStrictWarningView ?: return@runOnA11yMain
            try { a11yStrictWarningWm?.removeView(view) } catch (_: Exception) { }
            runCatching { a11yStrictWarningHost?.dismiss() }
            a11yStrictWarningView = null
            a11yStrictWarningHost = null
            a11yStrictWarningWm = null
        }
    }

    /**
     * Shows the Shorts & Reels overlay on the accessibility layer.
     * Returns true when it is (now) showing.
     */
    fun showA11yShortsOverlay(surfaceLabel: String): Boolean {
        if (a11yShortsView != null) {
            a11yShortsLabel.value = surfaceLabel
            return true
        }
        val host = com.example.ui.overlay.OverlayLifecycleHost()
        return try {
            a11yShortsLabel.value = surfaceLabel
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = androidx.compose.ui.platform.ComposeView(this).apply {
                host.installOn(this)
                setContent {
                    com.example.ui.theme.FocuslyTheme {
                        com.example.ui.components.ShortsBlockedOverlay(
                            surfaceLabel = a11yShortsLabel.value,
                            onReturn = { hideA11yShortsOverlay() },
                            onOpenFocivo = {
                                hideA11yShortsOverlay()
                                a11yOpenFocivo()
                            }
                        )
                    }
                }
                isFocusableInTouchMode = true
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        hideA11yShortsOverlay()
                        true
                    } else false
                }
            }
            host.show()
            wm.addView(view, a11yOverlayParams())
            a11yShortsView = view
            a11yShortsHost = host
            a11yShortsWm = wm
            Log.i(TAG, "A11y Shorts overlay shown ($surfaceLabel)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "A11y Shorts overlay failed: ${t.message}")
            runCatching { host.dismiss() }
            a11yShortsView = null
            a11yShortsHost = null
            a11yShortsWm = null
            false
        }
    }

    /** Hide the accessibility-layer Shorts overlay if visible. Main thread. */
    fun hideA11yShortsOverlay() {
        runOnA11yMain {
            val view = a11yShortsView ?: return@runOnA11yMain
            try { a11yShortsWm?.removeView(view) } catch (_: Exception) { }
            runCatching { a11yShortsHost?.dismiss() }
            a11yShortsView = null
            a11yShortsHost = null
            a11yShortsWm = null
        }
    }

    private fun a11yExitToHome() {
        hideA11yBlockOverlay()
        // v1.2.3: a braces Activity may be up behind the overlay — dismiss it too.
        runCatching { BlockOverlayManager.dismissBracesActivity() }
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        } catch (_: Exception) { }
    }

    private fun a11yOpenFocivo() {
        hideA11yBlockOverlay()
        // v1.2.3: a braces Activity may be up behind the overlay — dismiss it too.
        runCatching { BlockOverlayManager.dismissBracesActivity() }
        try {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
        } catch (_: Exception) { }
    }

    private fun isSettingsOrPackageInstallerApp(pkg: String): Boolean {
        val lower = pkg.lowercase(Locale.ROOT)
        return lower == "com.google.android.packageinstaller" ||
                lower == "com.android.packageinstaller" ||
                lower.contains("packageinstaller") ||
                lower.contains("uninstaller")
    }

    /**
     * v1.3.0 — multi-window protections. v1.1 final: enforcement is now
     * UNCONDITIONAL during an active focus period — a blocked package running
     * in split-screen or a floating/small window is intercepted the same way
     * as in fullscreen, redirecting it to the standard BlockedAppLockActivity.
     * (The old StrictMode opt-in toggles no longer gate this path; they remain
     * for standalone-shield use outside focus periods.)
     *
     * Scans interactive application windows (best-effort; requires the
     * service's flagRetrieveInteractiveWindows) for blocked packages.
     *
     * Signals used:
     * - split-screen: 2+ TYPE_APPLICATION windows visible at once.
     * - floating: an app window occupying < 85% of the display in BOTH
     *   dimensions (excludes fullscreen and half-screen split panes).
     *
     * Honest limitation: some OEMs restrict getWindows() data and PiP-style
     * mini windows may also match the floating heuristic — both are treated
     * as floating (blocking them is the safer behavior for a focus app).
     */
    /**
     * v1.2.3 — multi-window / any-window protection sweep.
     *
     * A blocked package appearing in ANY visible application window (any
     * bounds — fullscreen, split pane, floating/freeform, partial) is an
     * intercept trigger during a study period, NOT just the active/foreground
     * window that [onAccessibilityEvent]'s single-package path sees.
     *
     * Why this exists (vivo V2403, build-39): the event-package path misses
     * (a) blocked apps moved into a split pane without a WINDOW_STATE_CHANGED
     * for their package, (b) freeform windows sitting idle (no content
     * changes, so no scan trigger), (c) apps already open when the study
     * period starts. Every skip writes a throttled Guard Hub entry so the
     * owner's next device test shows exactly which step saw what.
     */
    // v1.2.3-hotfix: throttled scan-summary for the Guard Hub — the owner's
    // device test needs to SEE "windows-seen count + packages on last
    // checks", not just intercepts. Every scan exit below writes one.
    private var lastMwSummaryLogMs = 0L
    private fun logMwSummary(detail: String) {
        val now = System.currentTimeMillis()
        if (now - lastMwSummaryLogMs < 30_000L) return
        lastMwSummaryLogMs = now
        try {
            AiStudyGuardManager.logGuardActivity(
                applicationContext, "guard", "multiwindow-scan", "ok", detail
            )
        } catch (_: Exception) { }
    }

    private fun enforceMultiWindowProtections(nowTime: Long, triggerSrc: String) {
        val ctx = applicationContext
        if (nowTime - lastMultiWindowScanMs < 3000L) return
        lastMultiWindowScanMs = nowTime
        try {
            val blockedList = FocusShieldService.getActiveBlockedPackages(ctx)
            if (blockedList.isEmpty()) {
                logMwSummary("trigger=$triggerSrc windows=? blocked=0 (no active blocked packages)")
                return
            }
            val wins = try { windows } catch (_: Exception) { null }
            if (wins == null) {
                logMwSummary("trigger=$triggerSrc windows=? (windows list unavailable — OEM restricted or service not ready)")
                return
            }
            val appWins = wins.filter {
                try { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } catch (_: Exception) { false }
            }
            if (appWins.isEmpty()) {
                logMwSummary("trigger=$triggerSrc windows=${wins.size} appWindows=0 (no application windows reported)")
                return
            }
            val (dispW, dispH) = getDisplaySizePx()
            if (dispW <= 0 || dispH <= 0) return
            val rect = Rect()
            val seenPkgs = mutableListOf<String>()
            for (w in appWins) {
                val pkg = try { w.root?.packageName?.toString() } catch (_: Exception) { null }
                if (pkg == null) {
                    logMwSkip("root-null")
                    continue
                }
                seenPkgs.add(pkg)
                if (pkg.equals(ctx.packageName, ignoreCase = true)) continue
                val isBlocked = blockedList.any { b ->
                    pkg.equals(b, ignoreCase = true) || pkg.startsWith(b, ignoreCase = true)
                }
                if (!isBlocked) continue
                try { w.getBoundsInScreen(rect) } catch (_: Exception) {
                    logMwSkip("bounds-fail pkg=$pkg")
                    continue
                }
                if (rect.width() <= 0 || rect.height() <= 0) {
                    logMwSkip("empty-bounds pkg=$pkg")
                    continue
                }
                val isFloating = rect.width() < dispW * 0.85f && rect.height() < dispH * 0.85f
                val mode = when {
                    isFloating -> "floating"
                    appWins.size >= 2 -> "split"
                    else -> "fullscreen"
                }
                // v1.2.3: ANY visible blocked window triggers — the old code
                // only covered floating/split and relied on the event-package
                // path for fullscreen, which misses already-open apps.
                // v1.2.4 (owner 2026-10-16 19:57): the block UI must cover ONLY
                // the offending window — a blocked app in a split pane or a
                // freeform/small window gets the block INSIDE that window,
                // never a fullscreen block over the launcher/home. The
                // detected window bounds ride into the overlay layout; the
                // fullscreen path keeps the existing fullscreen overlay.
                val overlayBounds = if (mode == "fullscreen") null else android.graphics.Rect(rect)
                if (nowTime - lastGeneralAppInterceptTimeMs > 2500L || lastGeneralInterceptedPkg != pkg) {
                    lastGeneralAppInterceptTimeMs = nowTime
                    lastGeneralInterceptedPkg = pkg
                    val friendlyName = try { FocusShieldService.friendlyAppName(ctx, pkg) } catch (_: Exception) { pkg }
                    Log.w(TAG, "[MultiWindow] Blocked app ($pkg) visible in $mode window during focus — intercepting (src=windows-list, trigger=$triggerSrc).")
                    try {
                        AiStudyGuardManager.logGuardActivity(
                            ctx, friendlyName, "multiwindow-detect", "intercept",
                            "src=windows-list mode=$mode bounds=${rect.width()}x${rect.height()} " +
                                "overlay=" + (overlayBounds?.let { "window ${it.width()}x${it.height()}@${it.left},${it.top}" } ?: "fullscreen") +
                                " trigger=$triggerSrc"
                        )
                    } catch (_: Exception) { }
                    BlockOverlayManager.intercept(
                        context = ctx,
                        blockedPackage = pkg,
                        appName = friendlyName,
                        durationSec = try { FocusShieldService.getRemainingSeconds(ctx) } catch (_: Exception) { 0 },
                        reason = "$friendlyName is locked during your active focus session.",
                        isPunishment = false,
                        multiWindowMode = mode,
                        windowBounds = overlayBounds
                    )
                    // v1.3.0: when the intercept placed a window-bounded
                    // overlay, track that window — the glue loop repositions
                    // the overlay as the window moves, and the z-order
                    // watchdog verifies the overlay won the layer fight.
                    // Fullscreen keeps the existing fullscreen path.
                    if (overlayBounds != null && mode != "fullscreen" &&
                        isA11yBlockOverlayShowing()
                    ) {
                        trackWindowedBlock(pkg, mode, android.graphics.Rect(rect))
                    }
                }
                logMwSummary(
                    "trigger=$triggerSrc windows=${appWins.size} " +
                        "pkgs=${seenPkgs.distinct().take(8).joinToString(",")} " +
                        "blockedSeen=$pkg mode=$mode"
                )
                return
            }
            // No blocked package in any visible window this scan.
            logMwSummary(
                "trigger=$triggerSrc windows=${appWins.size} " +
                    "pkgs=${seenPkgs.distinct().take(8).joinToString(",")} blockedSeen=none"
            )
        } catch (_: Exception) { }
    }

    /** Throttled Guard Hub "seen but skipped" entries — proves the scan ran. */
    private fun logMwSkip(reason: String) {
        val now = System.currentTimeMillis()
        if (now - (lastMwSkipLogMs[reason] ?: 0L) < 30_000L) return
        lastMwSkipLogMs[reason] = now
        try {
            AiStudyGuardManager.logGuardActivity(
                applicationContext, "guard", "multiwindow-detect", "skipped", reason
            )
        } catch (_: Exception) { }
    }

    // v1.2.3 (C) — Shorts/Reels near-miss evidence (throttled per app): when
    // the toggle is ON but no surface is detected, the hub shows exactly
    // which signals were seen, so a "not blocking" report is debuggable.
    private val lastShortsCheckLogMs = mutableMapOf<String, Long>()

    private fun logShortsCheck(app: String, rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - (lastShortsCheckLogMs[app] ?: 0L) < 15_000L) return
        lastShortsCheckLogMs[app] = now
        try {
            val signals = if (app == "instagram") {
                com.example.util.ShortsSurfaceDetector.debugInstagramSignals(rootNode)
            } else {
                com.example.util.ShortsSurfaceDetector.debugYouTubeSignals(rootNode)
            }
            AiStudyGuardManager.logGuardActivity(
                applicationContext, app, "shorts-check", "no-surface", signals
            )
        } catch (_: Exception) { }
    }

    // v1.2.3 (owner 2026-10-16): player evidence is a REAL Guard Hub entry —
    // unthrottled but meaningful: it is written only when the evidence state
    // CHANGES (playing <-> not-playing), so the feed shows exactly when the
    // chain considered a video "playing" without drowning in per-event spam.
    // v1.3.3 (ITEM 2): the boolean is now ENFORCEABILITY (real player
    // surface + actual playback in progress), not mere player-shaped
    // pixels. The home feed's muted inline autoplay preview no longer
    // flips this to true.
    private var lastYouTubePlayerEvidence: Boolean? = null

    private fun logPlayerEvidence(hasPlayer: Boolean, signals: String) {
        if (lastYouTubePlayerEvidence == hasPlayer) return
        lastYouTubePlayerEvidence = hasPlayer
        try {
            AiStudyGuardManager.logGuardActivity(
                applicationContext, "YouTube", "youtube-player-evidence",
                if (hasPlayer) "seen" else "not-detected", signals
            )
        } catch (_: Exception) { }
    }

    /**
     * v1.2.3 (owner 2026-10-16): feeds the Guard Hub "Last YouTube
     * evaluation" card — one glanceable row of what the YouTube chain last
     * decided (player evidence, title source, p_ent band, verdict, action).
     */
    private fun recordYouTubeEval(
        titleSource: String,
        title: String,
        playerEvidence: Boolean = true,
        pEntBand: String = "",
        verdict: String = "",
        action: String = ""
    ) {
        try {
            AiStudyGuardManager.recordYouTubeEvaluation(
                AiStudyGuardManager.LastYouTubeEvaluation(
                    ts = System.currentTimeMillis(),
                    playerEvidence = playerEvidence,
                    titleSource = titleSource,
                    title = title.take(60),
                    fullTitle = title,
                    pEntBand = pEntBand,
                    verdict = verdict,
                    action = action
                )
            )
        } catch (_: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun getDisplaySizePx(): Pair<Int, Int> {
        return try {
            val wm = applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return 0 to 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = wm.currentWindowMetrics.bounds
                b.width() to b.height()
            } else {
                val dm = DisplayMetrics()
                wm.defaultDisplay.getMetrics(dm)
                dm.widthPixels to dm.heightPixels
            }
        } catch (_: Exception) {
            0 to 0
        }
    }

    /**
     * v1.3.1 hotfix (owner order 2026-10-17, item A1) — Focivo-specific
     * tamper guard. v1.3.x (item 3): evaluated BEFORE the essential-apps
     * early return (launchers and Settings ARE the tamper screens), on
     * window-state changes, window-set changes, and throttled content
     * changes (some OEM uninstall dialogs never emit a window-state
     * changes (some OEM uninstall dialogs never emit a window-state
     * change), and no longer pre-gated on the study-period check — the
     * four-condition gate below (identical to UninstallProtectionGate
     * PROTECTED: strict + opted-in + live session + admin active) is the
     * single authority, so shield-only sessions (store-armed, no study
     * period) are covered too.
     *
     * When a protected strict session is running
     * (UninstallProtectionGate == PROTECTED), the accessibility service
     * watches for the three tamper screens that name THIS app — the
     * package-installer uninstall confirmation, the Settings app-info page,
     * and the device-admin deactivation screen — and:
     *   1. back-presses out of the screen (GLOBAL_ACTION_BACK),
     *   2. re-fires on persistence (each new window event is a new
     *      detection, throttled at 1.5s so the user can never complete the
     *      flow mid-session),
     *   3. shows the premium StrictUninstallBlockedOverlay (window-safe),
     *   4. logs EVERY intercept to Guard Hub,
     *   5. posts a high-priority alert notification with the real session
     *      end time (v1.3.3 item 6b) — throttled by the same intercept path,
     *      session-scoped, auto-cancelled at session end.
     * Session-scoped only: outside a protected session this is a no-op and
     * uninstall/deactivation are 100% normal.
     */
    private fun guardStrictTamperScreen(
        event: AccessibilityEvent,
        pkg: String,
        eventType: Int
    ) {
        val ctx = applicationContext
        // Cheap pre-filter first: only tamper-capable packages are worth the
        // gate reads below. Pure string ops — the overwhelmingly common
        // non-candidate event costs nothing.
        if (!com.example.util.StrictTamperGuard.isTamperCandidatePackage(pkg)) return

        // TYPE_WINDOW_CONTENT_CHANGED fires per keystroke/scroll — throttle
        // the evaluation itself so a Settings/launcher scroll storm stays
        // cheap. Window-state/windows-changed events are always evaluated.
        val nowTime = System.currentTimeMillis()
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val evalKey = pkg
            if (nowTime - lastTamperEvalMs < 800L && lastTamperEvalKey == evalKey) return
            lastTamperEvalMs = nowTime
            lastTamperEvalKey = evalKey
        }

        // Session-scoped gate, cheapest checks first. PROTECTED requires ALL
        // of: Strict ON, opted in, a LIVE session (study period OR the
        // StrictSessionStore's armed, expiry-validated record — shield-only
        // sessions arm the store without a study period), and the device
        // admin actually active. Outside a protected session this whole
        // function is a no-op and uninstall/deactivation are 100% normal.
        // The 1.1.3 session-scoping (pause/discard/completion disarm,
        // monotonic expiry, purge on read) lives in StrictSessionStore and
        // is untouched.
        if (!com.example.util.StrictModeManager.isStrictModeEnabled(ctx)) {
            // Strict off: uninstall is 100% normal. No diagnostic needed —
            // this is the overwhelmingly common state, keep the hub quiet.
            return
        }
        // v1.1.4d (item D1): every gate decision is logged WITH the full
        // session state (which source claims a session, admin on/off), so
        // any residual silent block is diagnosable from Guard Hub on the
        // device instead of guesswork. The two session sources are read
        // separately precisely so the log can name the live one.
        val studyPeriodActive = com.example.util.AiStudyGuardManager.isStudyPeriodActive(ctx)
        val storeSessionActive = com.example.util.StrictSessionStore.isStrictSessionActive(ctx)
        val adminActiveNow = com.example.util.DeviceAdminGuard.isAdminActive(ctx)
        val sessionSource = when {
            studyPeriodActive && storeSessionActive -> "study-period+strict-store"
            studyPeriodActive -> "study-period"
            storeSessionActive -> "strict-store"
            else -> "none"
        }
        val gateSummary = "strict=on session=$sessionSource admin=${if (adminActiveNow) "on" else "off"}"
        if (!com.example.util.StrictModeManager.isUninstallProtectionEnabled(ctx)) {
            logTamperSkipped(pkg, "opted-out", gateSummary)
            return
        }
        val sessionActive = studyPeriodActive || storeSessionActive
        if (!sessionActive) {
            logTamperSkipped(pkg, "no-session", gateSummary)
            return
        }
        if (!adminActiveNow) {
            logTamperSkipped(pkg, "admin-inactive", gateSummary)
            return
        }

        val className = try { event.className?.toString() } catch (_: Exception) { null } ?: ""
        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        val screenText = com.example.util.StrictTamperGuard.collectScreenText(root)
        try { root?.recycle() } catch (_: Exception) { }
        val ownPackage = ctx.packageName
        val appLabel = try {
            val ai = ctx.packageManager.getApplicationInfo(ownPackage, 0)
            ctx.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { "Focivo" }

        val tamper = com.example.util.StrictTamperGuard.detect(
            pkg = pkg,
            className = className,
            screenText = screenText,
            ownPackage = ownPackage,
            appLabel = appLabel
        ) ?: return

        val key = "${tamper.name}|$pkg"
        if (nowTime - lastTamperInterceptMs < 1500L && lastTamperKey == key) return
        lastTamperInterceptMs = nowTime
        lastTamperKey = key

        Log.w(TAG, "[StrictTamperGuard] ${tamper.name} naming $ownPackage on $pkg ($className) — back-pressing out. Gate: $gateSummary")
        // 1. Kill the tamper screen. Back first (safer than HOME — never
        // collapses the user's other windows); the re-fire throttle keeps it
        // dead while the user keeps trying mid-session.
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (t: Throwable) {
            Log.e(TAG, "[StrictTamperGuard] BACK failed: ${t.message}")
        }
        // 2. Guard Hub evidence (throttled like the intercept). v1.1.4d:
        // the gate snapshot rides along so the intercept's session source
        // is on record next to the skip entries.
        try {
            com.example.util.AiStudyGuardManager.logGuardActivity(
                ctx,
                "Focivo",
                tamper.hubStage,
                "BLOCKED",
                "tamper-screen mid-session; back-press + overlay [$gateSummary]",
                "$pkg ${className.takeLast(40)}"
            )
        } catch (_: Exception) { }
        // 3. Premium overlay explaining why (window-safe a11y overlay path —
        // TYPE_ACCESSIBILITY_OVERLAY renders above every app window on every
        // OEM, incl. installer dialogs; needs no overlay permission since it
        // is added from this service).
        com.example.util.StrictUninstallGuard.notifyStrictUninstallBlocked(ctx)
        BlockOverlayManager.intercept(
            context = ctx,
            blockedPackage = pkg,
            appName = "Focivo",
            durationSec = FocusShieldService.getRemainingSeconds(ctx),
            reason = tamper.userReason,
            isPunishment = false
        )
        // 4. v1.3.3 (item 6b): high-priority alert notification alongside the
        // overlay + BACK. This call sits INSIDE the throttled intercept path
        // above, so at most one notification fires per re-fire window; the
        // notifier re-checks the protected-session scope itself and logs
        // every post to Guard Hub.
        com.example.util.StrictTamperAlertNotifier.notifyIntercept(ctx, tamper)
    }

    /**
     * v1.3.x (item 3) — throttled "seen but not protected" Guard Hub
     * diagnostic. When a tamper-candidate package is on screen but the
     * PROTECTED gate fails, the next device test shows exactly which
     * condition failed instead of silent nothing. Throttled per
     * (reason, pkg) so normal use never spams the hub.
     */
    private fun logTamperSkipped(pkg: String, reason: String, gateSummary: String = "") {
        val key = "$reason|$pkg"
        val now = System.currentTimeMillis()
        if (now - (lastTamperSkipLogMs[key] ?: 0L) < 30_000L) return
        lastTamperSkipLogMs[key] = now
        try {
            com.example.util.AiStudyGuardManager.logGuardActivity(
                applicationContext, "Focivo", "tamper-guard", "skipped",
                "candidate screen on $pkg; gate not PROTECTED ($reason) [$gateSummary]"
            )
        } catch (_: Exception) { }
    }

    private fun pauseMediaPlayback() {
        try {
            val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))
        } catch (_: Throwable) {}
    }

    /**
     * v1.2.2 (C) — Shorts & Reels surface intercept.
     *
     * Standalone surface lock: back-press out of the Shorts/Reels player,
     * pause media, then the dedicated "blocked during focus" overlay with a
     * Return button. Deliberately does NOT call onNonStudyDetected — a shorts
     * surface is not a content verdict and must never feed the AI discipline
     * (warning / 3-hour punishment) flow. Works with ONLY the Shorts & Reels
     * toggle on: no AI Guard, no blocklist entry required.
     */
    private fun interceptShortsSurface(
        blockedPackage: String,
        surface: ShortsSurfaceDetector.ShortsSurface,
        surfaceLabel: String
    ) {
        // The main block UI always wins if it is already showing.
        if (BlockOverlayManager.isBlockUiShowing()) return

        pauseMediaPlayback()
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (_: Exception) { }

        AiStudyGuardManager.logGuardActivity(
            applicationContext, surfaceLabel, "shorts-block", "BLOCKED",
            "shorts-reels-toggle", surface.name
        )
        // v1.2.2 (share screen): shorts/reels surface blocks count as shield
        // activity for the session's "apps stayed locked" card.
        com.example.service.SessionBlockRecorder.record(
            applicationContext, blockedPackage, surfaceLabel
        )
        val shown = BlockOverlayManager.showShortsBlock(applicationContext, surfaceLabel)
        if (!shown) {
            Log.w(TAG, "[Shorts Lock] Overlay permission missing — back-press only for $surfaceLabel.")
        }
    }

    /**
     * Immediately pauses active media/audio, then triggers the BlockedAppLockActivity overlay
     * showing the mascot, lock icon, and time remaining without redirecting to Focivo main app.
     */
    private fun forceStopMediaAndDismissToFocivo(
        context: Context,
        blockedPackage: String,
        appName: String,
        reason: String,
        isGeminiIntercept: Boolean = true
    ) {
        // v1.2.1: any block UI (overlay or legacy Activity) suppresses re-intercept.
        if (BlockOverlayManager.isBlockUiShowing()) return

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to send media pause key: ${e.message}")
        }

        val remainingSec = FocusShieldService.getRemainingSeconds(context)
        // v1.2.1: overlay-first intercept (covers freeform windows).
        BlockOverlayManager.intercept(
            context = context,
            blockedPackage = blockedPackage,
            appName = appName,
            durationSec = remainingSec,
            reason = reason,
            isPunishment = false
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            val eventType = event.eventType

            // v1.2.3-hotfix: window-set changes (split-screen enter/exit,
            // freeform window add/remove, PiP) often carry NO package name,
            // so they must be handled BEFORE the package-based early return
            // below. The scan itself is study-period gated and 3s-throttled.
            if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                if (AiStudyGuardManager.isStudyPeriodActive(applicationContext)) {
                    enforceMultiWindowProtections(
                        System.currentTimeMillis(), "windows-changed"
                    )
                    // v1.3.0: keep the window-bounded block overlay glued to
                    // the offending window as it moves/resizes.
                    glueWindowedOverlayToWindow()
                }
            }

            val pkg = event.packageName?.toString() ?: return

            // v1.3.0 — z-order watchdog counting: the window-bounded overlay
            // consumes every touch, so interactive events from the tracked
            // blocked package while it is up prove the OEM composited the
            // app window ABOVE our overlay.
            countZOrderWatchEvent(pkg, eventType)

            // 1. Never block self (Focivo)
            if (pkg.equals(applicationContext.packageName, ignoreCase = true)) {
                return
            }

            // 1a. v1.3.x (item 3): strict-session tamper guard — evaluated
            // BEFORE the essential-apps early return below, on window-state
            // changes, window-set changes, and (throttled) content changes.
            //
            // ROOT CAUSE (owner's vivo, item 3): the guard used to run AFTER
            // the essential-apps return and only on TYPE_WINDOW_STATE_CHANGED
            // inside an active study period. Launchers and Settings are
            // permanently "essential" (never blocked) — and they are exactly
            // where the tamper screens live (launcher long-press uninstall
            // dialogs, Settings app-info, device-admin management). Every
            // launcher/Settings tamper screen was swallowed before the guard
            // ever saw it, so NO notification and NO popup ever fired. It is
            // safe to evaluate first: the guard only acts when the screen
            // names Focivo, shows uninstall/admin text, AND the PROTECTED
            // gate (strict + opted-in + live session + admin active) holds.
            // Outside a protected session this is a complete no-op.
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) {
                guardStrictTamperScreen(event, pkg, eventType)
            }

            // 1b. v1.3.1: Deep-focus "Block phone home screen" (Regain parity, opt-in).
            // Scoped to active deep-focus sessions only. Best-effort: the OS Home key
            // itself can't be intercepted, but landing on the launcher re-shows the
            // focus overlay. Placed BEFORE the essential-apps early return because
            // launchers are permanently protected everywhere else.
            // v1.2.3 phantom-lock fix (RC2 belt-and-braces): ALSO require a real
            // study period, like every other intercept path — the persisted
            // deep-focus flag must never be the sole authority.
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                DeepFocusManager.isDeepFocusSessionActive(applicationContext) &&
                StrictModeManager.isBlockHomeScreenEnabled(applicationContext) &&
                AiStudyGuardManager.isStudyPeriodActive(applicationContext) &&
                EssentialAppsGuard.isLauncherPackage(applicationContext, pkg)
            ) {
                val nowHome = System.currentTimeMillis()
                if (nowHome - lastHomeScreenInterceptMs > 3000L || lastHomeInterceptedPkg != pkg) {
                    lastHomeScreenInterceptMs = nowHome
                    lastHomeInterceptedPkg = pkg
                    Log.w(TAG, "[Deep Focus] Home screen blocked during deep-focus session.")
                    BlockOverlayManager.intercept(
                        context = applicationContext,
                        blockedPackage = pkg,
                        appName = "Home Screen",
                        durationSec = FocusShieldService.getRemainingSeconds(applicationContext),
                        reason = "Deep Focus is on — the home screen stays blocked until your session ends. Get back in there!",
                        isPunishment = false
                    )
                }
                return
            }

            // 2. Essential apps, system launchers, folders, emergency, dialer, camera, gallery are PERMANENTLY protected.
            // v1.3.1: YouTube is essential-PROTECTED from blocking but must stay MONITORED by the AI Study
            // Guard (Shorts lock + video content filter below). Never early-return for it here.
            val isMonitoredYouTube = pkg == "com.google.android.youtube" || pkg.contains("youtube", ignoreCase = true)
            if (!isMonitoredYouTube && EssentialAppsGuard.isEssentialApp(applicationContext, pkg)) {
                return
            }

            // v1.1.6: liveness heartbeat — lets the hub show "listening" vs stale "Granted".
            // Throttled to one prefs write per 10s; records every event the service sees,
            // whether or not a study session is active.
            val nowHeartbeat = System.currentTimeMillis()
            if (nowHeartbeat - lastLivenessWriteMs > 10_000L) {
                lastLivenessWriteMs = nowHeartbeat
                try {
                    applicationContext.getSharedPreferences("focusly_guard_prefs", Context.MODE_PRIVATE)
                        .edit().putLong("last_guard_event_at_ms", nowHeartbeat).apply()
                } catch (_: Exception) { }
            }

            // CRITICAL: App blocking and study guard ONLY run during an active study session or active triggered schedule!
            val isStudyPeriod = AiStudyGuardManager.isStudyPeriodActive(applicationContext)
            if (!isStudyPeriod) {
                // v1.1.9 diagnostic: record why monitored apps are skipped (throttled).
                guardMonitoredLabel(pkg)?.let { label ->
                    AiStudyGuardManager.logGuardSkip(applicationContext, label, "gate", "not-study-period")
                }
                // When user is not studying, apps are 100% free and never intercepted/locked.
                return
            }

            val isWindowStateChanged = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            val isStrictActive = StrictModeManager.shouldBlockUninstallation(applicationContext)

            val nowTime = System.currentTimeMillis()

            // 3. Strict Mode: Guard uninstaller apps only during active study schedule / timer / shield.
            // v1.4.0-round2: com.android.settings is DELIBERATELY not matched here —
            // the device-admin grant/revoke flow lives in Settings and must never
            // be blocked or overlaid, otherwise the user gets trapped mid-permission.
            if (isWindowStateChanged && isStrictActive && isSettingsOrPackageInstallerApp(pkg)) {
                if (nowTime - lastGeneralAppInterceptTimeMs > 2500L || lastGeneralInterceptedPkg != pkg) {
                    lastGeneralAppInterceptTimeMs = nowTime
                    lastGeneralInterceptedPkg = pkg
                    Log.w(TAG, "[Strict Mode Anti-Uninstall] Intercepted Package Installer ($pkg) during active study!")
                    // v1.2.2 cross-workstream hook: also surface the in-app
                    // strict-uninstall-blocked notice (MainActivity handles the intent).
                    com.example.util.StrictUninstallGuard.notifyStrictUninstallBlocked(applicationContext)
                    BlockOverlayManager.intercept(
                        context = applicationContext,
                        blockedPackage = pkg,
                        appName = "App Installer",
                        durationSec = FocusShieldService.getRemainingSeconds(applicationContext),
                        reason = "Strict Mode Active: App Uninstallation is protected during your active study session.",
                        isPunishment = false
                    )
                }
                return
            }

            // 4. Check if specific package is currently under AI Study Guard 3-Hour Punishment Lock
            if (isWindowStateChanged && AiStudyGuardManager.isAppUnderPunishment(applicationContext, pkg)) {
                if (nowTime - lastPunishmentInterceptTimeMs > 2500L || lastPunishedPackageIntercepted != pkg) {
                    lastPunishmentInterceptTimeMs = nowTime
                    lastPunishedPackageIntercepted = pkg
                    Log.w(TAG, "[AI Punishment Lock] Attempted to open punished app ($pkg) during active 3-hour lockout!")
                    val remainingSec = AiStudyGuardManager.getPunishmentRemainingSeconds(applicationContext, pkg)
                    val friendlyName = FocusShieldService.friendlyAppName(applicationContext, pkg)
                    BlockOverlayManager.intercept(
                        context = applicationContext,
                        blockedPackage = pkg,
                        appName = friendlyName,
                        durationSec = remainingSec,
                        // v1.2.3: names the real trigger reason + expiry (owner:
                        // "a lock screen shows where no lock should exist").
                        reason = AiStudyGuardManager.punishmentLockReason(applicationContext, pkg, friendlyName),
                        isPunishment = true
                    )
                }
                return
            }

            // 5. Strictly block active scheduled blocked apps or active Focus Shield blocked apps immediately on launch
            if (isWindowStateChanged && isStudyPeriod && !isMonitoredYouTube) {
                val blockedList = FocusShieldService.getActiveBlockedPackages(applicationContext)
                if (blockedList.any { blocked -> pkg.equals(blocked, ignoreCase = true) || pkg.startsWith(blocked, ignoreCase = true) }) {
                    // v1.2.1: any block UI (overlay or Activity) suppresses re-intercept.
                    if (!BlockOverlayManager.isBlockUiShowing() || (nowTime - lastGeneralAppInterceptTimeMs > 1000L) || lastGeneralInterceptedPkg != pkg) {
                        lastGeneralAppInterceptTimeMs = nowTime
                        lastGeneralInterceptedPkg = pkg
                        Log.w(TAG, "[Focus Shield Lock] Opening blocked app ($pkg) during active study!")
                        val friendlyName = FocusShieldService.friendlyAppName(applicationContext, pkg)
                        val remainingSec = FocusShieldService.getRemainingSeconds(applicationContext)
                        BlockOverlayManager.intercept(
                            context = applicationContext,
                            blockedPackage = pkg,
                            appName = friendlyName,
                            durationSec = remainingSec,
                            reason = "$friendlyName is locked during your active focus session.",
                            isPunishment = false
                        )
                    }
                    return
                }
            }

            // 5b. v1.2.3: multi-window protections — a blocked app visible in
            // ANY window (split-screen pane, floating/freeform window, or an
            // already-open fullscreen window the event-package path missed)
            // is intercepted via the windows-list scan, not just the
            // active/foreground window. Event-driven AND periodic: idle
            // windows emit no events, so a 5s sweep covers them too.
            if (nowTime - lastPeriodicMwScanMs > 5000L) {
                lastPeriodicMwScanMs = nowTime
                enforceMultiWindowProtections(nowTime, "periodic")
            }
            if (isStudyPeriod && (isWindowStateChanged || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
                enforceMultiWindowProtections(nowTime, "event")
            }

            // 6. Check per-app daily limit on app open
            if (isWindowStateChanged && com.example.util.AppDailyLimitsManager.isDailyLimitExceeded(applicationContext, pkg)) {
                Log.w(TAG, "[Daily Limit Lock] Daily limit reached for $pkg")
                val friendlyName = FocusShieldService.friendlyAppName(applicationContext, pkg)
                BlockOverlayManager.intercept(
                    context = applicationContext,
                    blockedPackage = pkg,
                    appName = friendlyName,
                    durationSec = 0,
                    reason = "Daily screen time limit reached today for $friendlyName.",
                    isPunishment = false
                )
                return
            }

            // 6b. v1.3.3 (item 8b): global screen-time budget — when total
            // device usage today exceeds the budget, ALL non-essential apps
            // are blocked for a 3-hour break. Essentials (phone, camera,
            // maps, YouTube, Chrome, ...) short-circuit inside the manager.
            if (isWindowStateChanged) {
                val breakDecision = com.example.util.ScreenTimeBudgetManager.checkOnAppOpen(applicationContext, pkg)
                if (breakDecision is com.example.util.ScreenTimeBudgetManager.BreakDecision.Block) {
                    Log.w(TAG, "[Screen-Time Break] Global budget exceeded; blocking $pkg")
                    val friendlyName = FocusShieldService.friendlyAppName(applicationContext, pkg)
                    val budgetMins = com.example.util.ScreenTimeBudgetManager.getGlobalBudgetMinutes(applicationContext)
                    BlockOverlayManager.interceptScreenTimeBreak(
                        context = applicationContext,
                        blockedPackage = pkg,
                        appName = friendlyName,
                        remainingSec = (breakDecision.remainingMs / 1000L).toInt(),
                        budgetLabel = com.example.util.ScreenTimeBudgetManager.budgetLabel(budgetMins),
                        reason = com.example.util.ScreenTimeBudgetManager.breakReason(applicationContext, budgetMins)
                    )
                    return
                }
            }

            // 7. YouTube Shorts & AI Study Filter (Active ONLY during scheduled study time or active timer)
            val isYouTube = pkg == "com.google.android.youtube" || pkg.contains("youtube", ignoreCase = true)
            if (isYouTube) {
                if (!isStudyPeriod) return

                // If Shorts Lock is on, check active screen
                val isShortsLockOn = ShortsLockManager.isShortsLockEnabled(applicationContext)
                val isYouTubeAiFilterOn = YouTubeStudyGuardManager.isFilterEnabled(applicationContext)

                if (isShortsLockOn || isYouTubeAiFilterOn) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        try {
                            // 7A. Shorts Lock (v1.2.2 C): dedicated surface block.
                            // Detects the REAL Shorts player/feed surface via
                            // ShortsSurfaceDetector, then back-press + overlay.
                            // Fully decoupled from the AI discipline flow — this
                            // is a surface lock, not a content verdict. Long-form
                            // videos stay watchable.
                            if (isShortsLockOn) {
                                val shortsSurface = ShortsSurfaceDetector.detectYouTubeShorts(rootNode)
                                if (shortsSurface != null) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastShortsInterceptTimeMs > 2000L) {
                                        lastShortsInterceptTimeMs = now
                                        Log.w(TAG, "[Shorts Lock] YouTube Shorts surface detected ($shortsSurface) — intercepting.")
                                        interceptShortsSurface(
                                            blockedPackage = "com.google.android.youtube",
                                            surface = shortsSurface,
                                            surfaceLabel = "YouTube Shorts"
                                        )
                                    }
                                    return
                                } else {
                                    // v1.2.3 near-miss evidence (throttled): proves the
                                    // detector ran and shows exactly which signals
                                    // were (not) seen on this YouTube screen.
                                    logShortsCheck("youtube", rootNode)
                                }
                            }

                            // 7B. Intelligent YouTube AI Content Filter.
                            // v1.3.3 (ITEM 2, owner 2026-10-18): restructured
                            // around the explicit playback gate
                            // (YouTubePlaybackGate). Browsing surfaces (home /
                            // search / subscriptions / channel pages) are
                            // OBSERVED quietly — titles may be judged and
                            // cached for responsiveness, but the block overlay
                            // is UNREACHABLE from those paths. Enforcement
                            // happens ONLY via enforceIfPlayingYouTube(),
                            // which requires a real player surface AND
                            // playback in progress. The old 1.2.3 gate
                            // mistook the home feed's muted inline autoplay
                            // preview (full-width player_view) for playback
                            // and verdict-scored feed-card titles — that
                            // false positive is now structurally impossible:
                            // muted previews are INLINE_PREVIEW, and
                            // mayEnforce() is false for them by construction.
                            if (isYouTubeAiFilterOn) {
                                val (dispW, _) = getDisplaySizePx()
                                val audioActive = try {
                                    (applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                                        ?.isMusicActive == true
                                } catch (_: Exception) { false }
                                // Single snapshot walk for the gate (children
                                // recycled as we go; the extractor below
                                // re-reads the tree itself).
                                val gateView = snapshotYtGateNode(rootNode)
                                val signals = if (gateView != null) {
                                    YouTubePlaybackGate.detect(gateView, audioActive, dispW)
                                } else {
                                    YtPlaybackSignals(YtPlayerSurface.NONE, false, "surface=NONE playing=false sig=snapshot-failed")
                                }
                                // v1.2.3 (owner 2026-10-16): player evidence
                                // is a real, unthrottled hub entry
                                // (state-change only). v1.3.3: it now
                                // reports ENFORCEABILITY (surface + actual
                                // playback), not mere player-shaped pixels.
                                logPlayerEvidence(signals.enforceable, signals.evidence)

                                val activeVideo = extractPlayingYouTubeVideo(
                                    rootNode,
                                    watchScreen = signals.surface == YtPlayerSurface.WATCH_SCREEN
                                )?.let { info ->
                                    // v1.2.3 (B3): channel-extraction evidence for the hub.
                                    try {
                                        AiStudyGuardManager.logGuardActivity(
                                            applicationContext, "YouTube", "youtube-channel",
                                            if (info.channel.isNullOrBlank()) "not-detected" else "seen",
                                            "src=" + (info.channelSrc ?: "no-pattern-matched"),
                                            info.channel ?: info.title
                                        )
                                    } catch (_: Exception) { }
                                    YouTubeGuardOrchestrator.ActiveVideo(
                                        title = info.title,
                                        channel = info.channel,
                                        metadata = info.metadata,
                                        source = if (signals.surface == YtPlayerSurface.WATCH_SCREEN) "watch-screen" else "miniplayer/shorts"
                                    )
                                } ?: run {
                                    // v1.1.6 fallback: title view-ids not
                                    // exposed (e.g. fullscreen/custom player).
                                    // NOTE (v1.3.3): on browse surfaces this
                                    // scans feed cards — the result feeds ONLY
                                    // the observe path (never enforcement).
                                    val fallbackTitle = extractFallbackVideoTitle(rootNode)
                                    if (!fallbackTitle.isNullOrBlank() && fallbackTitle.length >= 4) {
                                        Log.d(TAG, "[YouTube AI Guard] Primary title extraction missed; using fallback title: '$fallbackTitle'")
                                        YouTubeGuardOrchestrator.ActiveVideo(
                                            title = fallbackTitle, channel = null,
                                            metadata = "fallback_title_scan", source = "fallback-scan"
                                        )
                                    } else null
                                }

                                val action = YouTubeGuardOrchestrator.handle(
                                    YouTubeGuardOrchestrator.Inputs(
                                        isYouTubeForeground = true,
                                        isStudyPeriod = isStudyPeriod,
                                        filterEnabled = true,
                                        signals = signals,
                                        video = activeVideo,
                                        judge = youTubeJudgeDeps()
                                    )
                                )
                                var enforced = false
                                when (action) {
                                    is YouTubeGuardOrchestrator.Action.Inert -> {
                                        // Unreachable here (filter on), kept for exhaustiveness.
                                    }
                                    is YouTubeGuardOrchestrator.Action.NoVideo -> {
                                        // v1.1.9 diagnostic: nothing title-like on screen.
                                        AiStudyGuardManager.logGuardSkip(applicationContext, "YouTube", "youtube-title", "title-null")
                                        recordYouTubeEval(
                                            titleSource = "title-null", title = "",
                                            playerEvidence = signals.enforceable,
                                            verdict = "SKIPPED", action = "allowed (fail-open)"
                                        )
                                    }
                                    is YouTubeGuardOrchestrator.Action.Observe -> {
                                        logYouTubeObserved(action.video, action.result, signals)
                                    }
                                    is YouTubeGuardOrchestrator.Action.Allowed -> {
                                        // Playing video judged study — quiet hub entry, nothing else.
                                        logYouTubeObserved(action.video, action.result, signals)
                                    }
                                    is YouTubeGuardOrchestrator.Action.Enforce -> {
                                        // THE choke point — the only caller
                                        // besides the Gemini tail below.
                                        logYouTubeTrace(action.video, action.result)
                                        enforced = enforceIfPlayingYouTube(
                                            signals, action.video, action.reason, action.viaBlocklist
                                        )
                                    }
                                    is YouTubeGuardOrchestrator.Action.AwaitGemini -> {
                                        // Sync pipeline uncertain on a PLAYING
                                        // video: launch the Gemini tail. It
                                        // re-verifies the gate on a FRESH
                                        // tree read before enforcing.
                                        recordYouTubeEval(
                                            titleSource = action.video.source, title = action.video.title,
                                            playerEvidence = signals.enforceable,
                                            verdict = "UNCERTAIN", action = "gemini check pending"
                                        )
                                        val pendingVideo = action.video
                                        serviceScope.launch {
                                            val geminiVerdict = GeminiContentClassifier.evaluateWithGemini(
                                                title = pendingVideo.title,
                                                channel = pendingVideo.channel,
                                                metadata = pendingVideo.metadata
                                            )
                                            handleYouTubeGeminiTail(pendingVideo, geminiVerdict)
                                        }
                                    }
                                }
                                if (enforced) return
                            } else {
                                // v1.1.9 diagnostic
                                AiStudyGuardManager.logGuardSkip(applicationContext, "YouTube", "youtube", "filter-disabled")
                            }
                        } finally {
                            try { rootNode.recycle() } catch (_: Exception) {}
                        }
                    }
                }
                return
            }

            // 8. Instagram Reels Detection (Active ONLY during scheduled study time or active timer)
            val isInstagram = pkg == "com.instagram.android" || pkg.contains("instagram", ignoreCase = true)
            if (isInstagram) {
                if (!isStudyPeriod) return

                val blockedList = FocusShieldService.getActiveBlockedPackages(applicationContext)
                val isFullInstagramBlocked = blockedList.any { it.contains("instagram", ignoreCase = true) }

                if (isFullInstagramBlocked && isWindowStateChanged) {
                    Log.w(TAG, "[Shield] Instagram is fully blocked!")
                    forceStopMediaAndDismissToFocivo(
                        context = applicationContext,
                        blockedPackage = pkg,
                        appName = "Instagram & Reels",
                        reason = "Instagram is blocked during your active study session.",
                        isGeminiIntercept = true
                    )
                    return
                }

                // v1.2.2 (C): dedicated Reels surface block — back-press + overlay,
                // decoupled from the AI discipline flow. Feed/DMs stay usable.
                if (ShortsLockManager.isReelsLockEnabled(applicationContext)) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        try {
                            val reelsSurface = ShortsSurfaceDetector.detectInstagramReels(rootNode)
                            if (reelsSurface != null) {
                                val now = System.currentTimeMillis()
                                if (now - lastShortsInterceptTimeMs > 2000L) {
                                    lastShortsInterceptTimeMs = now
                                    Log.w(TAG, "[Reels Lock] Instagram Reels surface detected ($reelsSurface) — intercepting.")
                                    interceptShortsSurface(
                                        blockedPackage = pkg,
                                        surface = reelsSurface,
                                        surfaceLabel = "Instagram Reels"
                                    )
                                }
                                return
                            } else {
                                // v1.2.3 near-miss evidence (throttled).
                                logShortsCheck("instagram", rootNode)
                            }
                        } finally {
                            try { rootNode.recycle() } catch (_: Exception) {}
                        }
                    }
                }
                return
            }

            // 9. AI Study Guard on AI chat apps (ChatGPT, Claude, Copilot, Perplexity, Meta AI, Grok, DeepSeek)
            // Strictly active ONLY when a study timer is running or within scheduled study hours!
            // v1.3.1: added Meta AI, Grok (X), DeepSeek — previously unmonitored by design (S3).
            // v1.3.4 (ITEM 4): attribute by the ACTIVE WINDOW's package, not
            // just the event's. Keystrokes arrive under the IME's package
            // (Gboard etc.) and dialogs under the system package while the AI
            // chat app owns the window — gating on event.packageName alone
            // silently dropped the typed message, the single most reliable
            // signal. The event package wins when it is itself a monitored
            // AI app (freshest signal); otherwise the window's package is
            // used.
            val rootPkgForChat = try {
                val r = rootInActiveWindow
                try {
                    r?.packageName?.toString()
                } finally {
                    try { r?.recycle() } catch (_: Exception) { }
                }
            } catch (_: Exception) { null }
            val monitoredPkg: String? = when {
                isAiChatPackage(pkg) -> pkg
                isAiChatPackage(rootPkgForChat) -> rootPkgForChat
                else -> null
            }
            val isAiApp = monitoredPkg != null
            // The package every throttle key, hub label and discipline call
            // in this section is attributed to.
            val aiPkg = monitoredPkg ?: pkg

            // v1.1.9 diagnostic: guard master switch off (throttled).
            if (isAiApp && !AiStudyGuardManager.isAiGuardEnabled(applicationContext)) {
                AiStudyGuardManager.logGuardSkip(applicationContext, guardMonitoredLabel(aiPkg) ?: aiPkg.take(24), "chat", "guard-disabled")
            }

            if (isAiApp && isStudyPeriod && (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                        || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        || isWindowStateChanged) && AiStudyGuardManager.isAiGuardEnabled(applicationContext)) {
                // Throttle full tree-walks: at most one scan per package per 1.5s
                val lastScan = lastAiChatScanMs[aiPkg] ?: 0L
                if (nowTime - lastScan < 1500L) {
                    // v1.1.9 diagnostic (throttled)
                    AiStudyGuardManager.logGuardSkip(applicationContext, guardMonitoredLabel(aiPkg) ?: aiPkg.take(24), "chat", "throttled")
                    return
                }
                lastAiChatScanMs[aiPkg] = nowTime
                if (lastAiChatScanMs.size > 40) {
                    val cutoff = nowTime - 120_000L
                    lastAiChatScanMs.entries.removeIf { it.value < cutoff }
                }
                AiStudyGuardManager.updateGuardStatusNotification(applicationContext)
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    try {
                        var candidates = extractCandidateMessages(rootNode)
                        if (candidates.isEmpty() && eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                            // v1.3.4 (ITEM 4): the tree exposed nothing usable
                            // (accessibility-dark chat view), but the event
                            // itself carries the changed text — the user's
                            // typed message. Harvest it directly instead of
                            // failing open in silence.
                            val harvested = ArrayList<String>()
                            for (cs in event.text) {
                                val t = cs?.toString()?.trim()
                                if (!t.isNullOrBlank() && t.length >= 4) harvested.add(t)
                            }
                            if (harvested.isNotEmpty()) {
                                Log.i(TAG, "[AI Chat Guard] Tree empty for $aiPkg; harvested ${harvested.size} text(s) from event")
                                candidates = harvested.distinct()
                            }
                        }
                        if (candidates.isEmpty()) {
                            // v1.3.4 (ITEM 4): fail-open, but never silent —
                            // the owner must see "scan ran, saw nothing" in
                            // the Guard Hub instead of a blank log.
                            AiStudyGuardManager.logGuardSkip(
                                applicationContext,
                                guardMonitoredLabel(aiPkg) ?: aiPkg.take(24),
                                "chat", "chat-empty"
                            )
                        }
                        val now = System.currentTimeMillis()

                        val newCandidates = candidates.filter { msg ->
                            val key = "$aiPkg:$msg"
                            val lastTime = evaluatedMessages[key]
                            if (lastTime != null && (now - lastTime < 15_000L)) {
                                false
                            } else {
                                evaluatedMessages[key] = now
                                true
                            }
                        }

                        if (evaluatedMessages.size > 200) {
                            val cutoff = now - 60_000L
                            evaluatedMessages.entries.removeIf { it.value < cutoff }
                        }

                        if (newCandidates.isNotEmpty()) {
                            // v1.1.9 diagnostic label
                            val chatLabel = guardMonitoredLabel(aiPkg) ?: aiPkg.take(24)
                            for (candidate in newCandidates) {
                                // v1.3.4 (ITEM 4): the user's own corrections
                                // ("Was study" / "Not study" pills) and the
                                // study-doubt gate run FIRST for chat — they
                                // previously only ran for YouTube/browser, so
                                // a corrected chat verdict never stuck and a
                                // doubt could be misread. Both are fail-open:
                                // they can only ALLOW (or honor an explicit
                                // user override); every other case falls
                                // through to the existing chain unchanged.
                                val correction = runCatching {
                                    FocivoBrainCorrections.lookup(applicationContext, candidate)
                                }.getOrNull()
                                if (correction != null) {
                                    if (correction) {
                                        Log.i(TAG, "[AI Chat Guard] Correction override (study) for $aiPkg")
                                        AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-correction", "STUDY", "user-correction", candidate)
                                        StudyGuardKnowledge.noteExternalChatVerdict(aiPkg, StudyGuardKnowledge.Verdict.STUDY)
                                        AiStudyGuardManager.onStudyActivityDetected(
                                            context = applicationContext,
                                            packageName = aiPkg,
                                            messageText = candidate
                                        )
                                    } else {
                                        Log.w(TAG, "[AI Chat Guard] Correction override (non-study) for $aiPkg: '$candidate'")
                                        AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-correction", "NON_STUDY", "user-correction", candidate)
                                        StudyGuardKnowledge.noteExternalChatVerdict(aiPkg, StudyGuardKnowledge.Verdict.NON_STUDY)
                                        AiStudyGuardManager.onNonStudyDetected(
                                            context = applicationContext,
                                            packageName = aiPkg,
                                            messageText = candidate,
                                            reason = "Marked non-study by your earlier correction"
                                        )
                                    }
                                    continue
                                }
                                if (OnDeviceStudyClassifier.isStudyDoubt(candidate)) {
                                    Log.i(TAG, "[AI Chat Guard] Study-doubt in $aiPkg — allowing, never a block")
                                    AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-doubt", "STUDY", "study-doubt-allow", candidate)
                                    StudyGuardKnowledge.noteExternalChatVerdict(aiPkg, StudyGuardKnowledge.Verdict.STUDY)
                                    AiStudyGuardManager.onStudyActivityDetected(
                                        context = applicationContext,
                                        packageName = aiPkg,
                                        messageText = candidate
                                    )
                                    continue
                                }
                                val result = OnDeviceStudyClassifier.evaluateContent(candidate)
                                if (result.isCasual) {
                                    Log.w(TAG, "[Non-Study Message] App: $aiPkg, Message: '$candidate', Reason: ${result.reason}")
                                    // v1.1.9 diagnostic
                                    AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-ondevice", "NON_STUDY", result.reason.take(64), candidate)
                                    // v1.1.8: feed confident verdict into the sticky cache so
                                    // low-signal follow-ups ("ok", "lol", ...) inherit it.
                                    StudyGuardKnowledge.noteExternalChatVerdict(aiPkg, StudyGuardKnowledge.Verdict.NON_STUDY)
                                    AiStudyGuardManager.onNonStudyDetected(
                                        context = applicationContext,
                                        packageName = aiPkg,
                                        messageText = candidate,
                                        reason = result.reason
                                    )
                                } else if (result.reason.contains("benefit of doubt")) {
                                    // v1.1.8: curated offline knowledge BEFORE the Gemini
                                    // tie-breaker — instant, no network. Confident verdicts
                                    // act directly; UNCERTAIN keeps the existing flow.
                                    when (StudyGuardKnowledge.scoreChatMessage(candidate, aiPkg)) {
                                        StudyGuardKnowledge.Verdict.NON_STUDY -> {
                                            Log.w(TAG, "[StudyGuardKnowledge] Non-study message in $aiPkg: '$candidate'")
                                            // v1.1.9 diagnostic
                                            AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-knowledge", "NON_STUDY", "knowledge", candidate)
                                            AiStudyGuardManager.onNonStudyDetected(
                                                context = applicationContext,
                                                packageName = aiPkg,
                                                messageText = candidate,
                                                reason = "Non-study content detected by Study Guard"
                                            )
                                        }
                                        StudyGuardKnowledge.Verdict.STUDY -> {
                                            // v1.1.9 diagnostic
                                            AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-knowledge", "STUDY", "knowledge", candidate)
                                            AiStudyGuardManager.onStudyActivityDetected(
                                                context = applicationContext,
                                                packageName = aiPkg,
                                                messageText = candidate
                                            )
                                        }
                                        StudyGuardKnowledge.Verdict.UNCERTAIN -> {
                                            // v1.1.6: on-device could not decide (no keyword match) — ask Gemini AI
                                            // as tie-breaker so natural Hinglish/casual chat is still judged.
                                            // Throttled: at most one Gemini chat check per package per 8s.
                                            val nowGem = System.currentTimeMillis()
                                            val lastGem = lastAiChatGeminiMs[aiPkg] ?: 0L
                                            if (nowGem - lastGem >= 8000L && candidate.length >= 4) {
                                                lastAiChatGeminiMs[aiPkg] = nowGem
                                                // v1.1.9 diagnostic
                                                AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-knowledge", "UNCERTAIN", "→gemini", candidate)
                                                val msgCopy = candidate
                                                serviceScope.launch {
                                                    try {
                                                        val gv = GeminiContentClassifier.evaluateChatMessageWithGemini(
                                                            message = msgCopy,
                                                            appName = aiPkg
                                                        )
                                                        // v1.2.2 (B3): AI_UNAVAILABLE = Gemini never ran.
                                                        // Fail open with an honest log entry; do NOT feed
                                                        // the sticky cache a verdict that was never made.
                                                        if (gv.source == GeminiContentClassifier.VerdictSource.AI_UNAVAILABLE) {
                                                            AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-gemini", "STUDY", "AI check unavailable — allowed", msgCopy)
                                                        } else if (!gv.isStudyAllowed || gv.isAdult) {
                                                            Log.w(TAG, "[Gemini Chat Guard] Non-study message in $aiPkg: '$msgCopy' -> ${gv.reason}")
                                                            StudyGuardKnowledge.noteExternalChatVerdict(aiPkg, StudyGuardKnowledge.Verdict.NON_STUDY)
                                                            // v1.1.9 diagnostic
                                                            AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-gemini", "NON_STUDY", gv.reason.take(64), msgCopy)
                                                            AiStudyGuardManager.onNonStudyDetected(
                                                                context = applicationContext,
                                                                packageName = aiPkg,
                                                                messageText = msgCopy,
                                                                reason = gv.reason
                                                            )
                                                        } else {
                                                            StudyGuardKnowledge.noteExternalChatVerdict(aiPkg, StudyGuardKnowledge.Verdict.STUDY)
                                                            // v1.1.9 diagnostic
                                                            AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-gemini", "STUDY", "gemini-allow", msgCopy)
                                                        }
                                                    } catch (t: Throwable) {
                                                        Log.w(TAG, "Gemini chat evaluation failed: ${t.message}")
                                                        // v1.1.9 diagnostic — v1.2.2 (B3): an exception
                                                        // means no verdict exists; fail open, honestly.
                                                        AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-gemini", "STUDY", "AI check unavailable — allowed", msgCopy)
                                                    }
                                                }
                                            } else {
                                                // v1.1.9 diagnostic: throttled or too short for Gemini.
                                                AiStudyGuardManager.logGuardSkip(applicationContext, chatLabel, "chat-gemini", "throttled")
                                            }
                                        }
                                    }
                                } else {
                                    // v1.1.8: confident study verdict feeds the sticky cache.
                                    StudyGuardKnowledge.noteExternalChatVerdict(aiPkg, StudyGuardKnowledge.Verdict.STUDY)
                                    // v1.1.9 diagnostic
                                    AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-ondevice", "STUDY", result.reason.take(64), candidate)
                                    AiStudyGuardManager.onStudyActivityDetected(
                                        context = applicationContext,
                                        packageName = aiPkg,
                                        messageText = candidate
                                    )
                                }
                            }
                        }
                    } finally {
                        try { rootNode.recycle() } catch (_: Exception) {}
                    }
                }
                return
            }

            // 10. Web Browser & General Screen Content AI Guard (Chrome, Firefox, Edge, Brave, Opera, Samsung Internet)
            // Active ONLY when Study Guard is armed and an active study session or timer is running!
            val lowerPkg = pkg.lowercase(Locale.ROOT)
            val isBrowser = lowerPkg == "com.android.chrome" ||
                    lowerPkg == "org.mozilla.firefox" ||
                    lowerPkg == "com.microsoft.emmx" ||
                    lowerPkg == "com.brave.browser" ||
                    lowerPkg == "com.opera.browser" ||
                    lowerPkg == "com.sec.android.app.sbrowser" ||
                    lowerPkg.contains("browser")

            if (isBrowser && isStudyPeriod && AiStudyGuardManager.isAiGuardEnabled(applicationContext) &&
                (isWindowStateChanged || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            ) {
                val lastScan = lastAiChatScanMs[pkg] ?: 0L
                if (nowTime - lastScan >= 2000L) {
                    lastAiChatScanMs[pkg] = nowTime
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        try {
                            val extraction = extractGeneralScreenContent(rootNode)
                            val combinedKey = "${extraction.screenTitle ?: ""}|${extraction.urlOrContext ?: ""}|${extraction.textSnippets.take(3).joinToString(",")}"
                            val signature = "$pkg:$combinedKey"
                            val lastEval = lastEvaluatedScreenSignatures[signature] ?: 0L

                            if (nowTime - lastEval >= 15_000L && (extraction.screenTitle != null || extraction.textSnippets.isNotEmpty())) {
                                lastEvaluatedScreenSignatures[signature] = nowTime
                                if (lastEvaluatedScreenSignatures.size > 100) {
                                    val cutoff = nowTime - 60_000L
                                    lastEvaluatedScreenSignatures.entries.removeIf { it.value < cutoff }
                                }

                                val browserLabel = guardMonitoredLabel(pkg) ?: "Browser"
                                val urlOrTitle = extraction.urlOrContext ?: extraction.screenTitle ?: ""

                                // v1.1.1: deterministic website-block check FIRST.
                                // WebsiteBlockManager holds the user's blocked-domain
                                // list (defaults + custom, allowlisted study sites
                                // exempt). A known distraction domain needs no AI
                                // verdict — treat it as non-study immediately.
                                if (com.example.util.WebsiteBlockManager.isBlockedDomain(
                                        applicationContext, urlOrTitle
                                    )
                                ) {
                                    val blockedDomain = com.example.util.WebsiteBlockManager
                                        .extractDomain(urlOrTitle) ?: "a blocked site"
                                    Log.w(TAG, "[Browser AI Guard] Blocked domain $blockedDomain in $browserLabel")
                                    AiStudyGuardManager.logGuardActivity(
                                        applicationContext, browserLabel, "website-block", "NON_STUDY",
                                        "blocked domain $blockedDomain", urlOrTitle
                                    )
                                    AiStudyGuardManager.onNonStudyDetected(
                                        context = applicationContext,
                                        packageName = pkg,
                                        messageText = urlOrTitle.ifBlank { extraction.textSnippets.firstOrNull() ?: "" },
                                        reason = "Blocked website ($blockedDomain) during study"
                                    )
                                    try { rootNode.recycle() } catch (_: Exception) {}
                                    return
                                }

                                // Check for web-based AI chats (ChatGPT web, Claude web, Perplexity web) in browser
                                val lowerUrlOrTitle = urlOrTitle.lowercase(Locale.ROOT)
                                val isWebAiChat = lowerUrlOrTitle.contains("chatgpt.com") ||
                                        lowerUrlOrTitle.contains("chat.openai.com") ||
                                        lowerUrlOrTitle.contains("claude.ai") ||
                                        lowerUrlOrTitle.contains("perplexity.ai") ||
                                        lowerUrlOrTitle.contains("gemini.google.com")

                                // Evaluate with on-device heuristics first
                                val quickScreen = GeminiContentClassifier.quickEvaluateScreen(
                                    packageName = pkg,
                                    appName = browserLabel,
                                    screenTitle = extraction.screenTitle,
                                    visibleSnippets = extraction.textSnippets
                                )

                                if (quickScreen.isDistraction) {
                                    Log.w(TAG, "[Browser AI Guard] Distraction detected on-device in $browserLabel: ${quickScreen.reason}")
                                    AiStudyGuardManager.logGuardActivity(applicationContext, browserLabel, "browser-ondevice", "NON_STUDY", quickScreen.reason.take(64), urlOrTitle)
                                    AiStudyGuardManager.onNonStudyDetected(
                                        context = applicationContext,
                                        packageName = pkg,
                                        messageText = urlOrTitle.ifBlank { extraction.textSnippets.firstOrNull() ?: "" },
                                        reason = quickScreen.reason
                                    )
                                } else if (quickScreen.confidence < 0.90f) {
                                    // v1.2.3 (Task 9, bundle v3): second brain — the on-device
                                    // screen-content classifier judges the extracted
                                    // text BEFORE any network call. Bands per spec:
                                    //   study-doubt phrasing -> STUDY -> allow
                                    //       (doubt gate FIRST — a doubt must never
                                    //       become a block; regional doubt rows can
                                    //       score ~0.70-0.75 above the 0.69 line)
                                    //   p_ent >= 0.69 -> non-study candidate ->
                                    //       same discipline flow as a confident
                                    //       on-device block.
                                    //   p_ent < 0.20  -> study -> allow locally
                                    //       (no Gemini call needed).
                                    // v1.3.0 (Worker E): combined brain for screen text —
                                    // repo brain + v3 content brain. Order inside the
                                    // judge: corrections -> doubt gate -> combined
                                    // score -> our bands (p >= 0.80 allow,
                                    // p <= 0.31 block, middle -> Gemini arbiter
                                    // below; no key -> existing fail-open).
                                    // A doubt phrasing can never block once the
                                    // judge sees it.
                                    val screenText = buildString {
                                        if (!extraction.screenTitle.isNullOrBlank()) append(extraction.screenTitle).append(". ")
                                        extraction.textSnippets.forEach { append(it).append(" ") }
                                    }.trim()
                                    val judgment = runCatching {
                                        FocivoCombinedBrain.judgeScreen(applicationContext, screenText)
                                    }.getOrNull()
                                    val pStr = judgment?.pCombined?.let {
                                        "p=%.3f".format(Locale.ROOT, it)
                                    } ?: "no-score"
                                    when {
                                        judgment?.tag == "doubt" -> {
                                            Log.i(TAG, "[Browser AI Guard] Study doubt detected in $browserLabel ($pStr) — allowing")
                                            AiStudyGuardManager.logGuardActivity(applicationContext, browserLabel, "screen-local", "STUDY", "study-doubt-allow $pStr", urlOrTitle)
                                            AiStudyGuardManager.onStudyActivityDetected(
                                                context = applicationContext,
                                                packageName = pkg,
                                                messageText = urlOrTitle
                                            )
                                        }
                                        judgment?.decision == FocivoCombinedBrain.Decision.BLOCK -> {
                                            val reason = "Non-study screen content detected [combined-brain $pStr]"
                                            Log.w(TAG, "[Browser AI Guard] $reason in $browserLabel")
                                            AiStudyGuardManager.logGuardActivity(applicationContext, browserLabel, "screen-local", "NON_STUDY", "combined-brain $pStr", urlOrTitle)
                                            AiStudyGuardManager.onNonStudyDetected(
                                                context = applicationContext,
                                                packageName = pkg,
                                                messageText = urlOrTitle.ifBlank { extraction.textSnippets.firstOrNull() ?: "" },
                                                reason = reason
                                            )
                                        }
                                        judgment?.decision == FocivoCombinedBrain.Decision.ALLOW -> {
                                            AiStudyGuardManager.logGuardActivity(applicationContext, browserLabel, "screen-local", "STUDY", "combined-brain $pStr", urlOrTitle)
                                            AiStudyGuardManager.onStudyActivityDetected(
                                                context = applicationContext,
                                                packageName = pkg,
                                                messageText = urlOrTitle
                                            )
                                        }
                                        else -> {
                                            if (judgment?.pCombined != null) {
                                                AiStudyGuardManager.logGuardActivity(applicationContext, browserLabel, "screen-local", "UNCERTAIN", "combined-brain $pStr->gemini", urlOrTitle)
                                            }
                                            // Ambient screen check with Gemini
                                            serviceScope.launch {
                                        try {
                                            val gv = GeminiContentClassifier.evaluateScreenWithGemini(
                                                packageName = pkg,
                                                appName = browserLabel,
                                                screenTitle = extraction.screenTitle,
                                                visibleSnippets = extraction.textSnippets,
                                                metadata = extraction.urlOrContext
                                            )
                                            // v1.2.2 (B3): AI_UNAVAILABLE = Gemini never ran —
                                            // fail open with an honest log entry.
                                            if (gv.source == GeminiContentClassifier.VerdictSource.AI_UNAVAILABLE) {
                                                AiStudyGuardManager.logGuardActivity(applicationContext, browserLabel, "screen-gemini", "STUDY", "AI check unavailable — allowed", urlOrTitle)
                                            } else if (gv.isDistraction || !gv.isStudyAllowed) {
                                                Log.w(TAG, "[Gemini Screen Guard] Distraction confirmed in $browserLabel: ${gv.reason}")
                                                AiStudyGuardManager.logGuardActivity(applicationContext, browserLabel, "screen-gemini", "NON_STUDY", gv.reason.take(64), urlOrTitle)
                                                AiStudyGuardManager.onNonStudyDetected(
                                                    context = applicationContext,
                                                    packageName = pkg,
                                                    messageText = urlOrTitle.ifBlank { extraction.textSnippets.firstOrNull() ?: "" },
                                                    reason = gv.reason
                                                )
                                            } else {
                                                AiStudyGuardManager.logGuardActivity(applicationContext, browserLabel, "screen-gemini", "STUDY", "gemini-allow", urlOrTitle)
                                                AiStudyGuardManager.onStudyActivityDetected(
                                                    context = applicationContext,
                                                    packageName = pkg,
                                                    messageText = urlOrTitle
                                                )
                                            }
                                        } catch (t: Throwable) {
                                            Log.w(TAG, "Gemini screen analysis exception: ${t.message}")
                                            // v1.2.2 (B3): exception = no verdict; fail open, honestly.
                                            AiStudyGuardManager.logGuardActivity(applicationContext, browserLabel, "screen-gemini", "STUDY", "AI check unavailable — allowed", urlOrTitle)
                                        }
                                    }
                                    }
                                }
                                }
                            }
                        } finally {
                            try { rootNode.recycle() } catch (_: Exception) {}
                        }
                    }
                }
                return
            }

            // Unblocked apps are NEVER intercepted or blocked! They open and function 100% freely.
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onAccessibilityEvent: ${t.message}", t)
        }
    }

    private val evaluatedMessages = LinkedHashMap<String, Long>()

    private val IGNORED_UI_STRINGS = setOf(
        "message chatgpt", "message chatgpt...", "message claude", "message claude...",
        "ask anything", "ask a question", "start a new chat", "new chat", "clear chat",
        "share", "copy", "settings", "profile", "account", "send", "attach", "voice",
        "explore gpts", "upgrade to plus", "upgrade to pro", "close", "back", "more options",
        "search", "menu", "chatgpt", "claude", "today", "yesterday", "previous 7 days"
    )

    private fun extractCandidateMessages(rootNode: AccessibilityNodeInfo): List<String> {
        val messages = mutableListOf<String>()
        collectCandidateNodes(rootNode, messages)
        // v1.3.4 (ITEM 4): parent containers often repeat their children's
        // text — dedupe so one message is classified (and hub-logged) once.
        // Cap per-candidate length so a giant concatenated node can't blow
        // up the regex classifiers downstream.
        return messages.distinct().map { if (it.length > 4000) it.take(4000) else it }
    }

    private fun collectCandidateNodes(node: AccessibilityNodeInfo, list: MutableList<String>, depth: Int = 0) {
        // v1.3.4 (ITEM 4): 14 -> 24. Chat apps nest their message lists deep
        // (app -> window -> composer -> list -> cell -> bubble -> text); the
        // old cap silently pruned every real conversation node while the
        // shallow chrome ("New chat", "Ask anything") still passed through.
        if (depth > 24) return
        var text = node.text?.toString()?.trim()
        // v1.3.4 (ITEM 4): chat apps with accessibility-dark custom views
        // (the ChatGPT app's TalkBack breakage is the documented case)
        // expose message text only via contentDescription, or not via text
        // at all. Read the description when text is absent.
        if (text.isNullOrBlank()) {
            text = node.contentDescription?.toString()?.trim()
        }
        if (!text.isNullOrBlank() && text.length >= 4) {
            val lower = text.lowercase(Locale.ROOT)
            if (!IGNORED_UI_STRINGS.contains(lower) && !lower.startsWith("message ")) {
                list.add(text)
            }
        }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            collectCandidateNodes(child, list, depth + 1)
            try {
                child.recycle()
            } catch (_: Exception) {}
        }
    }

    data class ScreenExtractionResult(
        val screenTitle: String?,
        val textSnippets: List<String>,
        val urlOrContext: String? = null
    )

    /**
     * Traverses the active window node hierarchy to extract screen titles,
     * web URL / search queries, and visible text snippets for Gemini screen analysis.
     */
    private fun extractGeneralScreenContent(rootNode: AccessibilityNodeInfo): ScreenExtractionResult {
        var foundTitle: String? = null
        var foundUrl: String? = null
        val snippets = mutableListOf<String>()

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12) return

            val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            // 1. URL Bar extraction (Chrome, Firefox, Brave, Samsung Internet)
            if (viewId.contains("url_bar") || viewId.contains("location_bar") || viewId.contains("search_src_text") || viewId.contains("address_bar")) {
                if (!text.isNullOrBlank()) {
                    foundUrl = text
                    if (foundTitle == null) foundTitle = text
                }
            }

            // 2. Toolbar / App bar screen title
            if (viewId.contains("toolbar") || viewId.contains("action_bar_title") || viewId.contains("title_text") || viewId.contains("header_title")) {
                if (!text.isNullOrBlank() && text.length >= 3 && foundTitle == null) {
                    foundTitle = text
                }
            }

            // 3. Collect readable text snippets (posts, headlines, video titles, tweets, comments)
            if (!text.isNullOrBlank() && text.length >= 6) {
                val lower = text.lowercase(Locale.ROOT)
                if (!IGNORED_UI_STRINGS.contains(lower) && snippets.size < 15) {
                    snippets.add(text)
                }
            } else if (!desc.isNullOrBlank() && desc.length >= 10) {
                val lower = desc.lowercase(Locale.ROOT)
                if (!IGNORED_UI_STRINGS.contains(lower) && snippets.size < 15) {
                    snippets.add(desc)
                }
            }

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
                try { child.recycle() } catch (_: Exception) {}
            }
        }

        traverse(rootNode, 0)

        return ScreenExtractionResult(
            screenTitle = foundTitle,
            textSnippets = snippets,
            urlOrContext = foundUrl
        )
    }

    private data class YouTubeActiveVideoInfo(
        val title: String,
        val channel: String? = null,
        val metadata: String? = null,
        /** Which extraction pattern produced the channel (hub diagnostics). */
        val channelSrc: String? = null
    )

    /**
     * v1.1.6: Evaluates one YouTube video title (primary or fallback extraction).
     * Returns true when the video was quick-blocked and the caller should stop
     * processing this event; false when a background Gemini check was launched.
     */
    /**
     * v1.1.9 diagnostic: short label for guard-monitored apps, null for others.
     * Used only for the activity log so the owner can see which app an entry
     * belongs to.
     */
    /**
     * v1.3.4 (ITEM 4): the monitored AI-chat package set, extracted so both
     * the event package and the active-window package can be tested.
     * Covers: ChatGPT (com.openai.chatgpt), Claude (com.anthropic.claude),
     * Gemini (com.google.android.apps.bard), Copilot, Perplexity
     * (ai.perplexity.app), Meta AI (com.meta.ai), Grok (ai.x.grok — matched
     * by the "grok" contains fallback; the legacy "com.x.ai" exact check is
     * kept harmlessly), DeepSeek (com.deepseek.chat).
     */
    private fun isAiChatPackage(p: String?): Boolean {
        if (p.isNullOrBlank()) return false
        return p == "com.openai.chatgpt" ||
                p == "com.anthropic.claude" ||
                p == "com.google.android.apps.bard" ||
                p.contains("chatgpt", ignoreCase = true) ||
                p.contains("claude", ignoreCase = true) ||
                p.contains("openai", ignoreCase = true) ||
                p.contains("anthropic", ignoreCase = true) ||
                p.contains("perplexity", ignoreCase = true) ||
                p.contains("copilot", ignoreCase = true) ||
                p.contains("meta.ai", ignoreCase = true) ||
                p.contains("metaai", ignoreCase = true) ||
                p.contains("deepseek", ignoreCase = true) ||
                p == "com.x.ai" ||
                p.contains("grok", ignoreCase = true)
    }

    private fun guardMonitoredLabel(pkg: String): String? {
        val lower = pkg.lowercase(Locale.ROOT)
        return when {
            lower == "com.google.android.youtube" || lower.contains("youtube") -> "YouTube"
            lower == "com.instagram.android" || lower.contains("instagram") -> "Instagram"
            lower == "com.openai.chatgpt" || lower.contains("chatgpt") || lower.contains("openai") -> "ChatGPT"
            lower.contains("claude") || lower.contains("anthropic") -> "Claude"
            lower.contains("perplexity") -> "Perplexity"
            lower.contains("copilot") -> "Copilot"
            lower.contains("bard") -> "Gemini"
            lower.contains("chrome") -> "Chrome"
            lower.contains("firefox") -> "Firefox"
            lower.contains("browser") || lower.contains("brave") || lower.contains("opera") -> "Browser"
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // v1.3.3 (ITEM 2 — YouTube guard over-blocking fix, owner 2026-10-18).
    //
    // The old processYouTubeVideoTitle() mixed classification and
    // enforcement in one function and was reachable from browse surfaces
    // (the 1.2.3 "player evidence" gate fired on the home feed's muted
    // inline autoplay preview, then the fallback title scan grabbed a feed
    // card title — instant block on open).
    //
    // New structure:
    //   section 7B  -> YouTubeGuardOrchestrator.handle() (pure routing)
    //   enforce*    -> enforceIfPlayingYouTube() — THE single enforcement
    //                  choke point — the only call sites are the Enforce
    //                  action branch in 7B and handleYouTubeGeminiTail().
    //   observe*    -> logYouTubeObserved() — quiet OBSERVED hub entries.
    // Classification order is unchanged (blocklist -> allowlist ->
    // combined brain with the doubt gate inside -> knowledge bands ->
    // quick -> Gemini tail), and the OFF-by-default filter toggle
    // semantics are untouched (the judge is never consulted when the
    // toggle is off — the orchestrator returns Inert).
    // ------------------------------------------------------------------

    private data class YtGateNodeSnap(
        override val viewId: String,
        override val text: String,
        override val contentDescription: String,
        override val className: String,
        override val boundsWidthPx: Int,
        override val children: List<YtGateNode>
    ) : YtGateNode

    /**
     * Eager, lowercased snapshot of the node tree for the pure playback
     * gate. Children are recycled as the walk proceeds; the title
     * extractors re-read rootNode themselves afterwards (getChild returns
     * fresh pooled instances, so sequential traversals are safe — the
     * existing code already does this).
     */
    private fun snapshotYtGateNode(root: AccessibilityNodeInfo, depth: Int = 0): YtGateNode? {
        if (depth > 14) return null
        val viewId = try { root.viewIdResourceName?.lowercase(Locale.ROOT) ?: "" } catch (_: Exception) { "" }
        val text = try { root.text?.toString()?.lowercase(Locale.ROOT) ?: "" } catch (_: Exception) { "" }
        val desc = try { root.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: "" } catch (_: Exception) { "" }
        val cls = try { root.className?.toString()?.lowercase(Locale.ROOT) ?: "" } catch (_: Exception) { "" }
        val width = try {
            val r = Rect()
            root.getBoundsInScreen(r)
            r.width()
        } catch (_: Exception) { 0 }
        val kids = ArrayList<YtGateNode>()
        val n = try { root.childCount } catch (_: Exception) { 0 }
        for (i in 0 until n) {
            val c = try { root.getChild(i) } catch (_: Exception) { null } ?: continue
            snapshotYtGateNode(c, depth + 1)?.let { kids.add(it) }
            try { c.recycle() } catch (_: Exception) {}
        }
        return YtGateNodeSnap(viewId, text, desc, cls, width, kids)
    }

    /** Wires the pure title pipeline to the real platform-backed judges. */
    private fun youTubeJudgeDeps(): YouTubeTitleJudge.Deps = object : YouTubeTitleJudge.Deps {
        override fun isChannelBlocklisted(channel: String?): Boolean =
            BlockedChannelsManager.isBlockedChannel(applicationContext, channel)

        override fun isChannelAllowlisted(channel: String?): Boolean =
            StudyAllowlistManager.isStudyChannel(applicationContext, channel)

        override fun judgeBrain(title: String, channel: String?): YouTubeTitleJudge.BrainVerdict {
            val j = runCatching { FocivoCombinedBrain.judgeTitle(applicationContext, title, channel) }.getOrNull()
                ?: return YouTubeTitleJudge.BrainVerdict(YouTubeTitleJudge.BrainDecision.UNCERTAIN, "error", null)
            return YouTubeTitleJudge.BrainVerdict(
                when (j.decision) {
                    FocivoCombinedBrain.Decision.BLOCK -> YouTubeTitleJudge.BrainDecision.BLOCK
                    FocivoCombinedBrain.Decision.ALLOW -> YouTubeTitleJudge.BrainDecision.ALLOW
                    else -> YouTubeTitleJudge.BrainDecision.UNCERTAIN
                },
                j.tag, j.pCombined
            )
        }

        override fun scoreKnowledge(title: String, channel: String?): YouTubeTitleJudge.KnowledgeVerdict {
            val d = StudyGuardKnowledge.scoreVideoDetailed(title, channel)
            return YouTubeTitleJudge.KnowledgeVerdict(
                when (d.verdict) {
                    StudyGuardKnowledge.Verdict.STUDY -> YouTubeTitleJudge.KnowledgeDecision.STUDY
                    StudyGuardKnowledge.Verdict.NON_STUDY -> YouTubeTitleJudge.KnowledgeDecision.NON_STUDY
                    else -> YouTubeTitleJudge.KnowledgeDecision.UNCERTAIN
                },
                d.score, d.evidence
            )
        }

        override fun quickEvaluate(title: String, channel: String?, metadata: String?): YouTubeTitleJudge.QuickVerdict {
            val q = GeminiContentClassifier.quickEvaluate(title = title, channel = channel, metadata = metadata)
            return YouTubeTitleJudge.QuickVerdict(q.isStudyAllowed, q.isAdult, q.confidence, q.reason, q.category)
        }
    }

    /** Guard Hub p-band label for the "Last YouTube evaluation" card. */
    private fun youTubePBand(result: YouTubeTitleJudge.Result): String {
        return when (result.brainTag) {
            "doubt", "correction", "repo-channel" -> result.brainTag
            "" -> ""
            else -> FocivoCombinedBrain.pBandLabel(
                result.brainP, FocivoCombinedBrain.TITLE_BLOCK_P, FocivoCombinedBrain.TITLE_ALLOW_P
            )
        }
    }

    /** Per-stage Guard Hub trace for one judged title (same stage labels as before the refactor). */
    private fun logYouTubeTrace(video: YouTubeGuardOrchestrator.ActiveVideo, result: YouTubeTitleJudge.Result) {
        for (t in result.trace) {
            AiStudyGuardManager.logGuardActivity(applicationContext, "YouTube", t.stage, t.verdict, t.detail, video.title)
        }
    }

    /**
     * Quiet OBSERVED hub entry for the observe path (browse surfaces and
     * allowed videos). Never enforces — enforcement lives only in
     * enforceIfPlayingYouTube().
     */
    private fun logYouTubeObserved(
        video: YouTubeGuardOrchestrator.ActiveVideo,
        result: YouTubeTitleJudge.Result,
        signals: YtPlaybackSignals
    ) {
        logYouTubeTrace(video, result)
        val key = "${video.title}|${video.channel.orEmpty()}"
        val now = System.currentTimeMillis()
        if (key == lastYtObserveKey && now - lastYtObserveMs < 5000L) return
        lastYtObserveKey = key
        lastYtObserveMs = now
        AiStudyGuardManager.logGuardActivity(
            applicationContext, "YouTube", "youtube-observe", "OBSERVED",
            "surface=${signals.surface} enforceable=${signals.enforceable} " +
                    YouTubeGuardOrchestrator.outcomeName(result),
            video.title
        )
        recordYouTubeEval(
            titleSource = video.source, title = video.title,
            playerEvidence = signals.enforceable,
            pEntBand = youTubePBand(result),
            verdict = "OBSERVED",
            action = if (signals.enforceable) "observed (playing)" else "observed only — browse surface"
        )
    }

    /**
     * THE enforcement choke point for the YouTube AI content filter.
     *
     * Prove the design by grepping for enforceIfPlayingYouTube: the only
     * call sites are the Action.Enforce branch of section 7B and
     * handleYouTubeGeminiTail(). Browse-surface observations can never
     * reach this function — the orchestrator only produces Action.Enforce
     * when YouTubePlaybackGate.mayEnforce() is true, and the gate is
     * re-verified here at fire time (the Gemini tail fires seconds after
     * the original event, when the user may have paused or navigated
     * away).
     *
     * Returns true when enforcement fired (the caller stops processing
     * this event).
     */
    private fun enforceIfPlayingYouTube(
        signals: YtPlaybackSignals,
        video: YouTubeGuardOrchestrator.ActiveVideo,
        reason: String,
        viaBlocklist: Boolean
    ): Boolean {
        if (!YouTubePlaybackGate.mayEnforce(signals)) {
            AiStudyGuardManager.logGuardSkip(applicationContext, "YouTube", "youtube-enforce", "gate-closed-at-fire")
            return false
        }
        if (!YouTubeStudyGuardManager.isFilterEnabled(applicationContext)) return false
        if (!AiStudyGuardManager.isStudyPeriodActive(applicationContext)) return false
        val now = System.currentTimeMillis()
        if (now - lastYouTubeAiInterceptTimeMs <= 1500L) return false
        lastYouTubeAiInterceptTimeMs = now
        Log.w(TAG, "[YouTube Guard] $reason Video: '${video.title}'")
        YouTubeStudyGuardManager.recordBlockedVideo(
            context = applicationContext, title = video.title, channel = video.channel, reason = reason
        )
        pauseMediaPlayback()
        if (viaBlocklist) {
            // v1.2.3 (B): the user's channel BLOCKLIST beats everything —
            // immediate, no warning flow (same semantics as the app
            // blocklist). The lock screen names the real reason.
            com.example.service.BlockOverlayManager.intercept(
                context = applicationContext,
                blockedPackage = "com.google.android.youtube",
                appName = "YouTube",
                durationSec = FocusShieldService.getRemainingSeconds(applicationContext),
                reason = reason,
                isPunishment = false
            )
            recordYouTubeEval(
                titleSource = video.source, title = video.title,
                verdict = "NON_STUDY", action = "blocked (channel blocklist)"
            )
        } else {
            AiStudyGuardManager.onNonStudyDetected(
                context = applicationContext,
                packageName = "com.google.android.youtube",
                messageText = video.title,
                reason = reason
            )
            recordYouTubeEval(
                titleSource = video.source, title = video.title,
                verdict = "NON_STUDY", action = "stage-1 warning"
            )
        }
        // Guard Hub: explicit PLAY_BLOCK enforcement entry.
        AiStudyGuardManager.logGuardActivity(
            applicationContext, "YouTube", "youtube-enforce", "PLAY_BLOCK",
            reason.take(64), video.title
        )
        return true
    }

    /**
     * Gemini tail (launched from the Action.AwaitGemini branch): re-verifies
     * the playback gate on a FRESH tree read at fire time, then routes the
     * arbiter verdict through the orchestrator — enforcement still funnels
     * through enforceIfPlayingYouTube().
     */
    private fun handleYouTubeGeminiTail(
        video: YouTubeGuardOrchestrator.ActiveVideo,
        geminiVerdict: GeminiContentClassifier.ContentVerdict
    ) {
        try {
            // v1.2.2 (B3): honest source handling. AI_UNAVAILABLE means Gemini
            // never ran — fail open with an honest log entry, NEVER a block.
            if (geminiVerdict.source == GeminiContentClassifier.VerdictSource.AI_UNAVAILABLE) {
                AiStudyGuardManager.logGuardActivity(
                    applicationContext, "YouTube", "youtube-gemini", "STUDY",
                    "AI check unavailable — allowed", video.title
                )
                recordYouTubeEval(
                    titleSource = video.source, title = video.title,
                    verdict = "STUDY", action = "allowed (fail-open: AI unavailable)"
                )
                return
            }
            val arbiter = if (!geminiVerdict.isStudyAllowed || geminiVerdict.isAdult) {
                YouTubeGuardOrchestrator.Arbiter.NON_STUDY
            } else {
                YouTubeGuardOrchestrator.Arbiter.STUDY
            }
            // v1.2.2: stage label follows the real source.
            val gemStage = if (geminiVerdict.source == GeminiContentClassifier.VerdictSource.GEMINI) {
                "youtube-gemini"
            } else {
                "youtube-quick"
            }

            val freshRoot = try { rootInActiveWindow } catch (_: Exception) { null }
            if (freshRoot == null) {
                AiStudyGuardManager.logGuardSkip(applicationContext, "YouTube", "youtube-gemini", "gate-closed-at-fire")
                return
            }
            val gateView = snapshotYtGateNode(freshRoot)
            val (dispW, _) = getDisplaySizePx()
            val audioActive = try {
                (applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isMusicActive == true
            } catch (_: Exception) { false }
            val signals = if (gateView != null) {
                YouTubePlaybackGate.detect(gateView, audioActive, dispW)
            } else {
                YtPlaybackSignals(YtPlayerSurface.NONE, false, "surface=NONE playing=false sig=snapshot-failed")
            }
            try { freshRoot.recycle() } catch (_: Exception) {}

            val action = YouTubeGuardOrchestrator.handle(
                YouTubeGuardOrchestrator.Inputs(
                    isYouTubeForeground = true,
                    isStudyPeriod = AiStudyGuardManager.isStudyPeriodActive(applicationContext),
                    filterEnabled = YouTubeStudyGuardManager.isFilterEnabled(applicationContext),
                    signals = signals,
                    video = video,
                    judge = youTubeJudgeDeps(),
                    arbiter = arbiter,
                    arbiterReason = "Non-study content detected: \"${video.title}\" [${geminiVerdict.reason}]"
                )
            )
            when (action) {
                is YouTubeGuardOrchestrator.Action.Enforce -> {
                    AiStudyGuardManager.logGuardActivity(
                        applicationContext, "YouTube", gemStage, "NON_STUDY",
                        "conf=${geminiVerdict.confidence} " + geminiVerdict.category.ifBlank { "gemini" }, video.title
                    )
                    enforceIfPlayingYouTube(signals, action.video, action.reason, viaBlocklist = false)
                }
                is YouTubeGuardOrchestrator.Action.Allowed -> {
                    AiStudyGuardManager.logGuardActivity(
                        applicationContext, "YouTube", gemStage, "STUDY", "gemini-allow", video.title
                    )
                    recordYouTubeEval(
                        titleSource = video.source, title = video.title,
                        verdict = "STUDY", action = "allowed"
                    )
                }
                is YouTubeGuardOrchestrator.Action.Observe -> logYouTubeObserved(action.video, action.result, signals)
                else -> { /* Inert / NoVideo / AwaitGemini: nothing to do at fire time. */ }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "[YouTube AI Guard] Gemini tail failed: ${t.message}")
        }
    }

    /**
     * v1.1.6: Fallback title scan for YouTube screens where the player does not expose
     * title view-ids (fullscreen / custom player UI). Picks the longest prominent text
     * node as the candidate video title. Returns null when nothing title-like is found.
     */
    private fun extractFallbackVideoTitle(rootNode: AccessibilityNodeInfo): String? {
        var best: String? = null
        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 10) return
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length in 12..160) {
                val lower = text.lowercase(Locale.ROOT)
                val isUiChrome = lower.contains("youtube") || lower == "search" ||
                        lower.contains("subscribe") || lower.contains("notification") ||
                        lower.startsWith("home") || lower.startsWith("shorts")
                if (!isUiChrome && (best == null || text.length > best!!.length)) {
                    best = text
                }
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
                try { child.recycle() } catch (_: Exception) {}
            }
        }
        traverse(rootNode, 0)
        return best
    }

    /**
     * v1.2.3: scoped, multi-pattern extraction.
     *
     * SCOPE (v1.2.3, owner 2026-10-16): on the dedicated watch screen the
     * search is constrained to the PLAYER subtree (the outermost
     * watch/player fragment node) — never the full tree, whose
     * first-match-wins pass was grabbing recommendation cards below the
     * player. Recommendation containers are additionally excluded in every
     * pass as a safety net. On non-watch surfaces (miniplayer floating over
     * a browse surface, Shorts player) the previous scoping stands: only the
     * miniplayer subtree, or the Shorts player screen, may contribute.
     *
     * CHANNEL (priority): viewId patterns → Subscribe-button sibling →
     * description patterns (" by " split, @handle). The matched source is
     * returned for Guard Hub evidence ("channel seen: X / not detected").
     */
    private fun extractPlayingYouTubeVideo(rootNode: AccessibilityNodeInfo, watchScreen: Boolean): YouTubeActiveVideoInfo? {
        val scopeRoot: AccessibilityNodeInfo = if (watchScreen) {
            findWatchPlayerSubtree(rootNode) ?: rootNode
        } else {
            val mini = findMiniplayerRoot(rootNode)
            if (mini != null) {
                mini
            } else {
                val shortsPlayer = com.example.util.ShortsSurfaceDetector
                    .detectYouTubeShorts(rootNode) ==
                    com.example.util.ShortsSurfaceDetector.ShortsSurface.YOUTUBE_SHORTS_PLAYER
                if (!shortsPlayer) return null
                rootNode
            }
        }

        var foundTitle: String? = null
        var foundMeta: String? = null
        var foundChannel: String? = null
        var channelSrc: String? = null
        fun setChannel(value: String?, src: String) {
            if (foundChannel.isNullOrBlank() && !value.isNullOrBlank()) {
                foundChannel = value!!.trim()
                channelSrc = src
            }
        }

        // Pass 1 — title + channel viewId patterns.
        fun traversePass1(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12 || (foundTitle != null && foundChannel != null)) return
            val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            // v1.2.3 (owner 2026-10-16): recommendation containers can never
            // contribute the playing title — first-match-wins was verdict-
            // scoring rec cards below the player on the watch screen.
            if (isRecommendationsContainer(viewId)) return
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            if (viewId.contains("title") || viewId.contains("video_title") || viewId.contains("watch_title") ||
                viewId.contains("metadata") || viewId.contains("header") || viewId.contains("collapsed_title") ||
                viewId.contains("expandable_title") || viewId.contains("player_title") || viewId.contains("item_title")) {
                if (!text.isNullOrBlank() && text.length >= 4 && foundTitle == null) {
                    val lower = text.lowercase(Locale.ROOT)
                    if (!lower.contains("youtube") && !lower.contains("search") && !lower.contains("explore") &&
                        !lower.contains("subscriptions") && !lower.contains("library") && !lower.contains("notifications") &&
                        !lower.contains("downloads") && !lower.contains("history") && !lower.contains("settings")) {
                        foundTitle = text
                    }
                }
            }

            if (viewId.contains("channel_name") || viewId.contains("owner_name") || viewId.contains("author") ||
                viewId.contains("channel_title") || viewId.contains("byline")) {
                if (!text.isNullOrBlank() && foundChannel == null) {
                    setChannel(text, "viewid")
                }
            }
            if (viewId.contains("channel_handle") && !text.isNullOrBlank()) {
                setChannel(text, "handle")
            }

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                traversePass1(child, depth + 1)
                try { child.recycle() } catch (_: Exception) {}
            }
        }
        traversePass1(scopeRoot, 0)

        // Pass 2 — Subscribe-button sibling: the channel name sits next to
        // the Subscribe button on the watch screen.
        if (foundChannel == null) {
            fun traversePass2(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 12 || foundChannel != null) return
                // v1.2.3: recommendation containers are excluded here too.
                val p2ViewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
                if (isRecommendationsContainer(p2ViewId)) return
                val text = node.text?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                val desc = node.contentDescription?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                val isSubscribe = (text == "subscribe" || desc == "subscribe")
                if (isSubscribe) {
                    val parent = try { node.parent } catch (_: Exception) { null }
                    if (parent != null) {
                        val pc = parent.childCount
                        for (i in 0 until pc) {
                            val sib = try { parent.getChild(i) } catch (_: Exception) { null } ?: continue
                            val st = sib.text?.toString()?.trim()
                            if (!st.isNullOrBlank() && st.length in 2..60 &&
                                !st.equals("subscribe", ignoreCase = true) &&
                                !st.equals("subscribed", ignoreCase = true)
                            ) {
                                setChannel(st, "subscribe-sibling")
                                try { sib.recycle() } catch (_: Exception) {}
                                break
                            }
                            try { sib.recycle() } catch (_: Exception) {}
                        }
                        try { parent.recycle() } catch (_: Exception) {}
                    }
                    return
                }
                val count = node.childCount
                for (i in 0 until count) {
                    val child = node.getChild(i) ?: continue
                    traversePass2(child, depth + 1)
                    try { child.recycle() } catch (_: Exception) {}
                }
            }
            traversePass2(scopeRoot, 0)
        }

        // Pass 3 — description patterns ("Title by Channel …", @handle).
        if (foundTitle == null || foundChannel == null) {
            fun traversePass3(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 12) return
                // v1.2.3: recommendation containers are excluded here too.
                val p3ViewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
                if (isRecommendationsContainer(p3ViewId)) return
                val desc = node.contentDescription?.toString()?.trim()
                if (!desc.isNullOrBlank() && desc.length >= 10) {
                    val lowerDesc = desc.lowercase(Locale.ROOT)
                    if (lowerDesc.contains(" by ") || lowerDesc.contains("views") || lowerDesc.contains("ago") ||
                        lowerDesc.contains("minutes") || lowerDesc.contains("hours")) {
                        if (!lowerDesc.contains("button") && !lowerDesc.contains("tab") && !lowerDesc.contains("search")) {
                            val parts = desc.split(" by ")
                            if (parts.size >= 2) {
                                if (foundTitle == null) foundTitle = parts[0].trim()
                                // v1.3.1: only use the description-derived channel when we don't
                                // already have one from a channel_name/author node (more reliable).
                                // Never split the channel segment on digits — channels like
                                // "3Blue1Brown" or "TED-Ed" must survive intact. Strip trailing
                                // view-count / age metadata with targeted patterns instead.
                                if (foundChannel.isNullOrBlank()) {
                                    val afterBy = parts[1].trim()
                                    val metaCut = afterBy.split(Regex("""\s*[•|]\s*""")).firstOrNull()?.trim() ?: afterBy
                                    val channelOnly = metaCut
                                        .replace(Regex("""\s+\d[\d.,]*[KMB]?\s+views?$""", RegexOption.IGNORE_CASE), "")
                                        .replace(Regex("""\s+\d+\s+(seconds?|minutes?|hours?|days?|weeks?|months?|years?)\s+ago$""", RegexOption.IGNORE_CASE), "")
                                        .trim()
                                    if (channelOnly.isNotBlank()) {
                                        setChannel(channelOnly, "desc-by-split")
                                    }
                                }
                                if (foundMeta == null) foundMeta = desc
                            } else {
                                if (foundTitle == null) foundTitle = desc
                                if (foundMeta == null) foundMeta = desc
                            }
                        }
                    }
                    // @handle in player context.
                    if (foundChannel == null) {
                        val handle = Regex("""@[\w.]{2,30}""").find(desc)?.value
                        if (handle != null) setChannel(handle, "handle-desc")
                    }
                }
                if (foundTitle != null && foundChannel != null) return
                val count = node.childCount
                for (i in 0 until count) {
                    val child = node.getChild(i) ?: continue
                    traversePass3(child, depth + 1)
                    try { child.recycle() } catch (_: Exception) {}
                }
            }
            traversePass3(scopeRoot, 0)
        }

        return if (!foundTitle.isNullOrBlank()) {
            YouTubeActiveVideoInfo(
                title = foundTitle!!,
                channel = foundChannel,
                metadata = foundMeta,
                channelSrc = channelSrc
            )
        } else {
            null
        }
    }

    /**
     * v1.2.3 (owner 2026-10-16): viewId fragments of recommendation / browse
     * containers whose subtrees must NEVER contribute the playing-video
     * title. Kept tight on purpose — the playing video's own metadata
     * container must not match.
     */
    private fun isRecommendationsContainer(viewId: String): Boolean {
        return viewId.contains("recommend") || viewId.contains("related_video") ||
                viewId.contains("watch_next") || viewId.contains("up_next") ||
                viewId.contains("compact_video")
    }

    /**
     * v1.2.3 (owner 2026-10-16): finds the outermost watch/player fragment
     * node — the playing-video context on the watch screen. Pre-order DFS
     * returns the shallowest match, so a full watch-page container wins
     * over the bare video surface inside it (the title lives next to the
     * player, not inside the surface). Null when no fragment id is exposed.
     */
    private fun findWatchPlayerSubtree(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (result != null || depth > 10) return
            val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            if (viewId.contains("watch_fragment") || viewId.contains("player_fragment") ||
                viewId.contains("watch_player") || viewId.contains("watch_while") ||
                viewId.contains("miniplayer") || viewId.contains("mini_player")) {
                result = node
                return
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
                // NOTE: do not recycle children here — the found subtree root
                // must stay usable by the caller.
                if (result == null) {
                    try { child.recycle() } catch (_: Exception) {}
                }
            }
        }
        traverse(rootNode, 0)
        return result
    }

    /** Finds the miniplayer subtree root (viewId contains miniplayer/watch_while). */
    private fun findMiniplayerRoot(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (result != null || depth > 10) return
            val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            if (viewId.contains("miniplayer") || viewId.contains("watch_while") || viewId.contains("mini_player")) {
                result = node
                return
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
                // NOTE: do not recycle children here — the found subtree root
                // must stay usable by the caller.
                if (result == null) {
                    try { child.recycle() } catch (_: Exception) {}
                }
            }
        }
        traverse(rootNode, 0)
        return result
    }

    override fun onInterrupt() {
        Log.d(TAG, "AiStudyAccessibilityService interrupted")
    }
}
