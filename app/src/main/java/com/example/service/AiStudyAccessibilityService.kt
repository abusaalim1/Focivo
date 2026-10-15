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
import com.example.util.DeepFocusManager
import com.example.util.EssentialAppsGuard
import com.example.util.GeminiContentClassifier
import com.example.util.OnDeviceStudyClassifier
import com.example.util.ShortsLockManager
import com.example.util.ShortsSurfaceDetector
import com.example.util.StrictModeManager
import com.example.util.StudyAllowlistManager
import com.example.util.StudyGuardKnowledge
import com.example.util.YouTubeStudyGuardManager
import com.example.util.YouTubeVideoClassifier
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
            private set

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
        // v1.3.1: deep-focus home-screen block throttle
        private var lastHomeScreenInterceptMs: Long = 0L
        private var lastHomeInterceptedPkg: String? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        connectedInstance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isServiceConnected = false
        connectedInstance = null
        // v1.2.3: never leave a system-level block overlay orphaned when the
        // service goes away.
        runCatching { hideA11yBlockOverlay() }
        runCatching { hideA11yShortsOverlay() }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isServiceConnected = false
        connectedInstance = null
        runCatching { hideA11yBlockOverlay() }
        runCatching { hideA11yShortsOverlay() }
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
    private var a11yBlockView: androidx.compose.ui.platform.ComposeView? = null
    private var a11yBlockHost: com.example.ui.overlay.OverlayLifecycleHost? = null
    private var a11yBlockWm: WindowManager? = null
    private val a11yBlockedPackage = androidx.compose.runtime.mutableStateOf("")
    private val a11yAppName = androidx.compose.runtime.mutableStateOf("")
    private val a11yDurationSec = androidx.compose.runtime.mutableStateOf(0)
    private val a11yReason = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val a11yPunishment = androidx.compose.runtime.mutableStateOf(false)

    private var a11yShortsView: androidx.compose.ui.platform.ComposeView? = null
    private var a11yShortsHost: com.example.ui.overlay.OverlayLifecycleHost? = null
    private var a11yShortsWm: WindowManager? = null
    private val a11yShortsLabel = androidx.compose.runtime.mutableStateOf("Shorts")

    /** True when the accessibility-layer block overlay is on screen. */
    fun isA11yBlockOverlayShowing(): Boolean = a11yBlockView != null

    /** True when the accessibility-layer Shorts overlay is on screen. */
    fun isA11yShortsOverlayShowing(): Boolean = a11yShortsView != null

    private fun runOnA11yMain(action: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) action()
        else a11yMainHandler.post(action)
    }

    private fun a11yOverlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            // Renders above ALL app windows (incl. OEM freeform layers) —
            // the strongest layer a third-party app can use.
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
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
     */
    fun showA11yBlockOverlay(
        blockedPackage: String,
        appName: String,
        durationSec: Int = 0,
        reason: String? = null,
        isPunishment: Boolean = false
    ): Boolean {
        if (a11yBlockView != null) {
            a11yBlockedPackage.value = blockedPackage
            a11yAppName.value = appName
            a11yDurationSec.value = durationSec
            a11yReason.value = reason
            a11yPunishment.value = isPunishment
            return true
        }
        val host = com.example.ui.overlay.OverlayLifecycleHost()
        return try {
            a11yBlockedPackage.value = blockedPackage
            a11yAppName.value = appName
            a11yDurationSec.value = durationSec
            a11yReason.value = reason
            a11yPunishment.value = isPunishment
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
                            mascotRes = com.example.R.drawable.mascot_blocked,
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
            wm.addView(view, a11yOverlayParams())
            a11yBlockView = view
            a11yBlockHost = host
            a11yBlockWm = wm
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
        runOnA11yMain {
            val view = a11yBlockView ?: return@runOnA11yMain
            try { a11yBlockWm?.removeView(view) } catch (_: Exception) { }
            runCatching { a11yBlockHost?.dismiss() }
            a11yBlockView = null
            a11yBlockHost = null
            a11yBlockWm = null
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
    private fun enforceMultiWindowProtections(nowTime: Long) {
        val ctx = applicationContext
        // v1.1 final: during an active focus period a blocked app must be
        // intercepted in EVERY window mode — fullscreen, split-screen and
        // floating/small windows alike. The old StrictMode opt-in toggles
        // (blockSplit/blockFloat) no longer gate this: if the app is blocked
        // during focus, it is blocked wherever it appears. The toggles are
        // retained for standalone-shield use outside focus periods.
        if (nowTime - lastMultiWindowScanMs < 3000L) return
        lastMultiWindowScanMs = nowTime
        try {
            val blockedList = FocusShieldService.getActiveBlockedPackages(ctx)
            if (blockedList.isEmpty()) return
            val wins = try { windows } catch (_: Exception) { null } ?: return
            val appWins = wins.filter {
                try { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } catch (_: Exception) { false }
            }
            if (appWins.isEmpty()) return
            val (dispW, dispH) = getDisplaySizePx()
            if (dispW <= 0 || dispH <= 0) return
            val multiWindowActive = appWins.size >= 2
            val rect = Rect()
            for (w in appWins) {
                val pkg = try { w.root?.packageName?.toString() } catch (_: Exception) { null }
                    ?: continue
                if (pkg.equals(ctx.packageName, ignoreCase = true)) continue
                val isBlocked = blockedList.any { b ->
                    pkg.equals(b, ignoreCase = true) || pkg.startsWith(b, ignoreCase = true)
                }
                if (!isBlocked) continue
                try { w.getBoundsInScreen(rect) } catch (_: Exception) { continue }
                if (rect.width() <= 0 || rect.height() <= 0) continue
                val isFloating = rect.width() < dispW * 0.85f && rect.height() < dispH * 0.85f
                val inSplit = multiWindowActive && !isFloating
                // v1.1 final: a blocked app is blocked in every window mode
                // during focus — fullscreen, split-screen, floating.
                val shouldBlock = isFloating || inSplit
                if (!shouldBlock) continue
                if (nowTime - lastGeneralAppInterceptTimeMs > 2500L || lastGeneralInterceptedPkg != pkg) {
                    lastGeneralAppInterceptTimeMs = nowTime
                    lastGeneralInterceptedPkg = pkg
                    val mode = if (isFloating) "floating window" else "split-screen"
                    Log.w(TAG, "[MultiWindow] Blocked app ($pkg) in $mode during focus — intercepting.")
                    val friendlyName = try { FocusShieldService.friendlyAppName(ctx, pkg) } catch (_: Exception) { pkg }
                    // v1.2.1: overlay-first intercept — the overlay covers
                    // floating windows too; Activity is the no-overlay fallback.
                    BlockOverlayManager.intercept(
                        context = ctx,
                        blockedPackage = pkg,
                        appName = friendlyName,
                        durationSec = try { FocusShieldService.getRemainingSeconds(ctx) } catch (_: Exception) { 0 },
                        reason = "$friendlyName is locked during your active focus session ($mode windows are blocked during focus).",
                        isPunishment = false
                    )
                }
                return
            }
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
            val pkg = event.packageName?.toString() ?: return
            val eventType = event.eventType

            // 1. Never block self (Focivo)
            if (pkg.equals(applicationContext.packageName, ignoreCase = true)) {
                return
            }

            // 1b. v1.3.1: Deep-focus "Block phone home screen" (Regain parity, opt-in).
            // Scoped to active deep-focus sessions only. Best-effort: the OS Home key
            // itself can't be intercepted, but landing on the launcher re-shows the
            // focus overlay. Placed BEFORE the essential-apps early return because
            // launchers are permanently protected everywhere else.
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                DeepFocusManager.isDeepFocusSessionActive(applicationContext) &&
                StrictModeManager.isBlockHomeScreenEnabled(applicationContext) &&
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
                        reason = "3-Hour Penalty: $friendlyName is locked for non-study conversation.",
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

            // 5b. v1.3.0: multi-window protections — a blocked app opened in
            // split-screen or a floating window can bypass the fullscreen
            // intercept above, so scan interactive windows (best-effort).
            if (isStudyPeriod && (isWindowStateChanged || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
                enforceMultiWindowProtections(nowTime)
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
                                }
                            }

                            // 7B. Intelligent YouTube AI Content Filter (Analyzes active video title & channel with Gemini AI)
                            if (isYouTubeAiFilterOn) {
                                // v1.2.2 (B2): evidence gate — the filter evaluates
                                // ONLY a video that is actually playing. Opening
                                // YouTube (home feed, search) can never produce a
                                // verdict, no matter what the recommendations say.
                                if (!ShortsSurfaceDetector.isYouTubeVideoPlayerSurface(rootNode)) {
                                    // v1.1.9 diagnostic
                                    AiStudyGuardManager.logGuardSkip(applicationContext, "YouTube", "youtube-title", "no-player-evidence")
                                } else {
                                    val activeVideo = extractPlayingYouTubeVideo(rootNode)
                                    if (activeVideo != null && activeVideo.title.isNotBlank() && activeVideo.title.length >= 4) {
                                        val blocked = processYouTubeVideoTitle(activeVideo.title, activeVideo.channel, activeVideo.metadata)
                                        if (blocked) return
                                    } else {
                                        // v1.1.6 fallback: title view-ids not exposed (e.g. fullscreen/custom player).
                                        // Scan prominent on-screen text instead of silently skipping detection.
                                        val fallbackTitle = extractFallbackVideoTitle(rootNode)
                                        if (!fallbackTitle.isNullOrBlank()) {
                                            Log.d(TAG, "[YouTube AI Guard] Primary title extraction missed; using fallback title: '$fallbackTitle'")
                                            val blocked = processYouTubeVideoTitle(fallbackTitle, null, "fallback_title_scan")
                                            if (blocked) return
                                        } else {
                                            // v1.1.9 diagnostic: nothing title-like on screen.
                                            AiStudyGuardManager.logGuardSkip(applicationContext, "YouTube", "youtube-title", "title-null")
                                        }
                                    }
                                }
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
            val isAiApp = pkg == "com.openai.chatgpt" ||
                    pkg == "com.anthropic.claude" ||
                    pkg == "com.google.android.apps.bard" ||
                    pkg.contains("chatgpt", ignoreCase = true) ||
                    pkg.contains("claude", ignoreCase = true) ||
                    pkg.contains("openai", ignoreCase = true) ||
                    pkg.contains("anthropic", ignoreCase = true) ||
                    pkg.contains("perplexity", ignoreCase = true) ||
                    pkg.contains("copilot", ignoreCase = true) ||
                    pkg.contains("meta.ai", ignoreCase = true) ||
                    pkg.contains("metaai", ignoreCase = true) ||
                    pkg.contains("deepseek", ignoreCase = true) ||
                    pkg == "com.x.ai" ||
                    pkg.contains("grok", ignoreCase = true)

            // v1.1.9 diagnostic: guard master switch off (throttled).
            if (isAiApp && !AiStudyGuardManager.isAiGuardEnabled(applicationContext)) {
                AiStudyGuardManager.logGuardSkip(applicationContext, guardMonitoredLabel(pkg) ?: pkg.take(24), "chat", "guard-disabled")
            }

            if (isAiApp && isStudyPeriod && (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                        || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        || isWindowStateChanged) && AiStudyGuardManager.isAiGuardEnabled(applicationContext)) {
                // Throttle full tree-walks: at most one scan per package per 1.5s
                val lastScan = lastAiChatScanMs[pkg] ?: 0L
                if (nowTime - lastScan < 1500L) {
                    // v1.1.9 diagnostic (throttled)
                    AiStudyGuardManager.logGuardSkip(applicationContext, guardMonitoredLabel(pkg) ?: pkg.take(24), "chat", "throttled")
                    return
                }
                lastAiChatScanMs[pkg] = nowTime
                if (lastAiChatScanMs.size > 40) {
                    val cutoff = nowTime - 120_000L
                    lastAiChatScanMs.entries.removeIf { it.value < cutoff }
                }
                AiStudyGuardManager.updateGuardStatusNotification(applicationContext)
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    try {
                        val candidates = extractCandidateMessages(rootNode)
                        val now = System.currentTimeMillis()

                        val newCandidates = candidates.filter { msg ->
                            val key = "$pkg:$msg"
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
                            val chatLabel = guardMonitoredLabel(pkg) ?: pkg.take(24)
                            for (candidate in newCandidates) {
                                val result = OnDeviceStudyClassifier.evaluateContent(candidate)
                                if (result.isCasual) {
                                    Log.w(TAG, "[Non-Study Message] App: $pkg, Message: '$candidate', Reason: ${result.reason}")
                                    // v1.1.9 diagnostic
                                    AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-ondevice", "NON_STUDY", result.reason.take(64), candidate)
                                    // v1.1.8: feed confident verdict into the sticky cache so
                                    // low-signal follow-ups ("ok", "lol", ...) inherit it.
                                    StudyGuardKnowledge.noteExternalChatVerdict(pkg, StudyGuardKnowledge.Verdict.NON_STUDY)
                                    AiStudyGuardManager.onNonStudyDetected(
                                        context = applicationContext,
                                        packageName = pkg,
                                        messageText = candidate,
                                        reason = result.reason
                                    )
                                } else if (result.reason.contains("benefit of doubt")) {
                                    // v1.1.8: curated offline knowledge BEFORE the Gemini
                                    // tie-breaker — instant, no network. Confident verdicts
                                    // act directly; UNCERTAIN keeps the existing flow.
                                    when (StudyGuardKnowledge.scoreChatMessage(candidate, pkg)) {
                                        StudyGuardKnowledge.Verdict.NON_STUDY -> {
                                            Log.w(TAG, "[StudyGuardKnowledge] Non-study message in $pkg: '$candidate'")
                                            // v1.1.9 diagnostic
                                            AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-knowledge", "NON_STUDY", "knowledge", candidate)
                                            AiStudyGuardManager.onNonStudyDetected(
                                                context = applicationContext,
                                                packageName = pkg,
                                                messageText = candidate,
                                                reason = "Non-study content detected by Study Guard"
                                            )
                                        }
                                        StudyGuardKnowledge.Verdict.STUDY -> {
                                            // v1.1.9 diagnostic
                                            AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-knowledge", "STUDY", "knowledge", candidate)
                                            AiStudyGuardManager.onStudyActivityDetected(
                                                context = applicationContext,
                                                packageName = pkg,
                                                messageText = candidate
                                            )
                                        }
                                        StudyGuardKnowledge.Verdict.UNCERTAIN -> {
                                            // v1.1.6: on-device could not decide (no keyword match) — ask Gemini AI
                                            // as tie-breaker so natural Hinglish/casual chat is still judged.
                                            // Throttled: at most one Gemini chat check per package per 8s.
                                            val nowGem = System.currentTimeMillis()
                                            val lastGem = lastAiChatGeminiMs[pkg] ?: 0L
                                            if (nowGem - lastGem >= 8000L && candidate.length >= 4) {
                                                lastAiChatGeminiMs[pkg] = nowGem
                                                // v1.1.9 diagnostic
                                                AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-knowledge", "UNCERTAIN", "→gemini", candidate)
                                                val msgCopy = candidate
                                                serviceScope.launch {
                                                    try {
                                                        val gv = GeminiContentClassifier.evaluateChatMessageWithGemini(
                                                            message = msgCopy,
                                                            appName = pkg
                                                        )
                                                        // v1.2.2 (B3): AI_UNAVAILABLE = Gemini never ran.
                                                        // Fail open with an honest log entry; do NOT feed
                                                        // the sticky cache a verdict that was never made.
                                                        if (gv.source == GeminiContentClassifier.VerdictSource.AI_UNAVAILABLE) {
                                                            AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-gemini", "STUDY", "AI check unavailable — allowed", msgCopy)
                                                        } else if (!gv.isStudyAllowed || gv.isAdult) {
                                                            Log.w(TAG, "[Gemini Chat Guard] Non-study message in $pkg: '$msgCopy' -> ${gv.reason}")
                                                            StudyGuardKnowledge.noteExternalChatVerdict(pkg, StudyGuardKnowledge.Verdict.NON_STUDY)
                                                            // v1.1.9 diagnostic
                                                            AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-gemini", "NON_STUDY", gv.reason.take(64), msgCopy)
                                                            AiStudyGuardManager.onNonStudyDetected(
                                                                context = applicationContext,
                                                                packageName = pkg,
                                                                messageText = msgCopy,
                                                                reason = gv.reason
                                                            )
                                                        } else {
                                                            StudyGuardKnowledge.noteExternalChatVerdict(pkg, StudyGuardKnowledge.Verdict.STUDY)
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
                                    StudyGuardKnowledge.noteExternalChatVerdict(pkg, StudyGuardKnowledge.Verdict.STUDY)
                                    // v1.1.9 diagnostic
                                    AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-ondevice", "STUDY", result.reason.take(64), candidate)
                                    AiStudyGuardManager.onStudyActivityDetected(
                                        context = applicationContext,
                                        packageName = pkg,
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
        return messages
    }

    private fun collectCandidateNodes(node: AccessibilityNodeInfo, list: MutableList<String>, depth: Int = 0) {
        if (depth > 14) return
        val text = node.text?.toString()?.trim()
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
        val metadata: String? = null
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

    private fun processYouTubeVideoTitle(title: String, channel: String?, metadata: String?): Boolean {
        // v1.2.2 (B1): the user's own study-channel allowlist always wins —
        // a channel they explicitly trust is never verdict-scored.
        if (StudyAllowlistManager.isStudyChannel(applicationContext, channel)) {
            AiStudyGuardManager.logGuardActivity(applicationContext, "YouTube", "youtube-allowlist", "STUDY", "allowlisted-channel", title)
            return false
        }

        // v1.1.8: curated offline knowledge first — instant (<1ms), zero network.
        // Confident verdicts are used directly; UNCERTAIN falls through to the
        // existing quickEvaluate -> Gemini flow unchanged.
        when (StudyGuardKnowledge.scoreVideo(title, channel)) {
            StudyGuardKnowledge.Verdict.STUDY -> {
                Log.d(TAG, "[StudyGuardKnowledge] Study video allowed: '$title' ($channel)")
                // v1.1.9 diagnostic
                AiStudyGuardManager.logGuardActivity(applicationContext, "YouTube", "youtube-knowledge", "STUDY", "knowledge", title)
                return false
            }
            StudyGuardKnowledge.Verdict.NON_STUDY -> {
                // v1.1.9 diagnostic (log every evaluation, before the action throttle)
                AiStudyGuardManager.logGuardActivity(applicationContext, "YouTube", "youtube-knowledge", "NON_STUDY", "knowledge", title)
                val now = System.currentTimeMillis()
                if (now - lastYouTubeAiInterceptTimeMs > 1500L) {
                    lastYouTubeAiInterceptTimeMs = now
                    Log.w(TAG, "[StudyGuardKnowledge] Non-study video: '$title' ($channel)")
                    YouTubeStudyGuardManager.recordBlockedVideo(
                        context = applicationContext,
                        title = title,
                        channel = channel,
                        reason = "Non-study content detected: \"$title\""
                    )
                    pauseMediaPlayback()
                    AiStudyGuardManager.onNonStudyDetected(
                        context = applicationContext,
                        packageName = "com.google.android.youtube",
                        messageText = title,
                        reason = "Non-study content detected on YouTube: \"$title\""
                    )
                }
                return true
            }
            StudyGuardKnowledge.Verdict.UNCERTAIN -> {
                // fall through to existing flow below
            }
        }

        val quickVerdict = GeminiContentClassifier.quickEvaluate(
            title = title,
            channel = channel,
            metadata = metadata
        )

        // v1.2.2 (B2): a quick on-device block needs a CONFIDENT verdict
        // (confidence >= 0.90 — real extracted-content evidence). Uncertain
        // verdicts (e.g. "Uncertain / Review Required") never block by
        // themselves — they fall through to the Gemini check below, and if
        // Gemini is unavailable the result is FAIL-OPEN (allow).
        val isConfidentQuickBlock = (!quickVerdict.isStudyAllowed || quickVerdict.isAdult) &&
                quickVerdict.confidence >= 0.90f

        if (isConfidentQuickBlock) {
            // v1.1.9 diagnostic (log every evaluation, before the action throttle)
            AiStudyGuardManager.logGuardActivity(
                applicationContext, "YouTube", "youtube-quick", "NON_STUDY",
                quickVerdict.category.ifBlank { "quick" }, title
            )
            val now = System.currentTimeMillis()
            if (now - lastYouTubeAiInterceptTimeMs > 1500L) {
                lastYouTubeAiInterceptTimeMs = now
                Log.w(TAG, "[YouTube AI Guard] Quick intercepting non-study video: '$title' ($channel) -> ${quickVerdict.reason}")
                YouTubeStudyGuardManager.recordBlockedVideo(
                    context = applicationContext,
                    title = title,
                    channel = channel,
                    reason = quickVerdict.reason
                )
                pauseMediaPlayback()
                AiStudyGuardManager.onNonStudyDetected(
                    context = applicationContext,
                    packageName = "com.google.android.youtube",
                    messageText = title,
                    reason = quickVerdict.reason
                )
            }
            return true
        } else {
            // v1.1.9 diagnostic
            AiStudyGuardManager.logGuardActivity(applicationContext, "YouTube", "youtube-quick", "UNCERTAIN", "quick-allow→gemini", title)
            // Launch background Gemini check for deep verification
            serviceScope.launch {
                val geminiVerdict = GeminiContentClassifier.evaluateWithGemini(
                    title = title,
                    channel = channel,
                    metadata = metadata
                )
                // v1.2.2 (B3): honest source handling. AI_UNAVAILABLE means
                // Gemini never ran — fail open with an honest log entry, and
                // NEVER treat it as a block.
                if (geminiVerdict.source == GeminiContentClassifier.VerdictSource.AI_UNAVAILABLE) {
                    AiStudyGuardManager.logGuardActivity(
                        applicationContext, "YouTube", "youtube-gemini", "STUDY",
                        "AI check unavailable — allowed", title
                    )
                    return@launch
                }
                if (!geminiVerdict.isStudyAllowed || geminiVerdict.isAdult) {
                    // v1.1.9 diagnostic — v1.2.2: stage label follows the real
                    // source (a confident on-device verdict never needed Gemini).
                    val gemStage = if (geminiVerdict.source == GeminiContentClassifier.VerdictSource.GEMINI) "youtube-gemini" else "youtube-quick"
                    AiStudyGuardManager.logGuardActivity(
                        applicationContext, "YouTube", gemStage, "NON_STUDY",
                        geminiVerdict.category.ifBlank { "gemini" }, title
                    )
                    val now = System.currentTimeMillis()
                    if (now - lastYouTubeAiInterceptTimeMs > 1500L) {
                        lastYouTubeAiInterceptTimeMs = now
                        Log.w(TAG, "[Gemini AI Guard] Intercepting non-study video: '$title' ($channel) -> ${geminiVerdict.reason}")
                        YouTubeStudyGuardManager.recordBlockedVideo(
                            context = applicationContext,
                            title = title,
                            channel = channel,
                            reason = geminiVerdict.reason
                        )
                        pauseMediaPlayback()
                        AiStudyGuardManager.onNonStudyDetected(
                            context = applicationContext,
                            packageName = "com.google.android.youtube",
                            messageText = title,
                            reason = geminiVerdict.reason
                        )
                    }
                } else {
                    // v1.1.9 diagnostic — v1.2.2: honest source label.
                    val allowStage = if (geminiVerdict.source == GeminiContentClassifier.VerdictSource.GEMINI) "youtube-gemini" else "youtube-quick"
                    val allowReason = if (geminiVerdict.source == GeminiContentClassifier.VerdictSource.GEMINI) "gemini-allow" else "quick-allow"
                    AiStudyGuardManager.logGuardActivity(applicationContext, "YouTube", allowStage, "STUDY", allowReason, title)
                }
            }
            return false
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

    private fun extractPlayingYouTubeVideo(rootNode: AccessibilityNodeInfo): YouTubeActiveVideoInfo? {
        var foundTitle: String? = null
        var foundChannel: String? = null
        var foundMeta: String? = null

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12) return

            val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            // 1. Direct watch view title / channel resource matching
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
                    foundChannel = text
                }
            }

            // 2. YouTube Accessibility Content Descriptions
            if (foundTitle == null && !desc.isNullOrBlank() && desc.length >= 10) {
                val lowerDesc = desc.lowercase(Locale.ROOT)
                if (lowerDesc.contains(" by ") || lowerDesc.contains("views") || lowerDesc.contains("ago") || lowerDesc.contains("minutes") || lowerDesc.contains("hours")) {
                    if (!lowerDesc.contains("button") && !lowerDesc.contains("tab") && !lowerDesc.contains("search")) {
                        val parts = desc.split(" by ")
                        if (parts.size >= 2) {
                            foundTitle = parts[0].trim()
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
                                    foundChannel = channelOnly
                                }
                            }
                            foundMeta = desc
                        } else {
                            foundTitle = desc
                            foundMeta = desc
                        }
                    }
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

        return if (!foundTitle.isNullOrBlank()) {
            YouTubeActiveVideoInfo(
                title = foundTitle!!,
                channel = foundChannel,
                metadata = foundMeta
            )
        } else {
            null
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AiStudyAccessibilityService interrupted")
    }
}
