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
 * Block overlay manager — the single choke point for every block-screen
 * intercept. History:
 * - v1.2.1 (fix #5): replaced the fullscreen Activity with a
 *   TYPE_APPLICATION_OVERLAY window. Worked on stock Android, but on the
 *   owner's vivo V2403 the OEM freeform layer can sit ABOVE app overlays —
 *   small-window locks still failed.
 *
 * v1.2.3 — window-proof path (owner's vivo V2403, "small window lock nhi
 * hota hai"): the accessibility service now renders the SAME block UI on
 * TYPE_ACCESSIBILITY_OVERLAY, a layer reserved for accessibility services
 * that the system composites above every app window on every OEM —
 * fullscreen, split-screen AND freeform/small windows. It needs no
 * "Display over other apps" permission. Path priority: A11Y (service
 * connected) → APP (overlay permission) → legacy Activity.
 *
 * HONEST LIMITS (see report):
 * - Detection of the floating window itself still rides the accessibility
 *   service's interactive-window scan (flagRetrieveInteractiveWindows);
 *   without the accessibility permission the usage-stats poll is the
 *   fallback and a freeform resume can be missed. Some OEMs restrict
 *   getWindows() data — a window the service cannot see cannot be
 *   intercepted; the scan logs what it finds.
 * - Every intercept writes its render path (a11y/app/activity) to the
 *   Guard Hub activity feed so the layer is verifiable on-device.
 */
object BlockOverlayManager {

    private const val TAG = "BlockOverlay"

    /**
     * v1.2.3 — overlay path priority (strongest first).
     *
     * A11Y: TYPE_ACCESSIBILITY_OVERLAY hosted by AiStudyAccessibilityService.
     *   Reserved layer, composited above every app window on every OEM —
     *   fullscreen, split-screen AND freeform/small windows (this is what
     *   serious blockers use). Needs no overlay permission: it works exactly
     *   when the detection that triggers it works (the a11y service itself).
     * APP:  TYPE_APPLICATION_OVERLAY from the app process (v1.2.1 path).
     *   Needs the "Display over other apps" permission; on some OEMs
     *   (vivo freeform) the OEM's small-window layer can sit above it.
     * ACTIVITY: legacy fullscreen BlockedAppLockActivity (no overlay at all).
     */
    enum class OverlayPath { A11Y, APP, ACTIVITY }

    /** Pure decision function — unit-testable. */
    fun resolveOverlayPath(a11yServiceConnected: Boolean, canDrawOverlays: Boolean): OverlayPath {
        return when {
            a11yServiceConnected -> OverlayPath.A11Y
            canDrawOverlays -> OverlayPath.APP
            else -> OverlayPath.ACTIVITY
        }
    }

    private fun resolveOverlayPath(context: Context): OverlayPath =
        resolveOverlayPath(
            AiStudyAccessibilityService.isServiceConnected &&
                AiStudyAccessibilityService.connectedInstance != null,
            Settings.canDrawOverlays(context)
        )

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

    fun isShowing(): Boolean =
        overlayView != null ||
            AiStudyAccessibilityService.connectedInstance?.isA11yBlockOverlayShowing() == true

    /**
     * v1.2.3 — show through the accessibility service's overlay layer.
     * Synchronous boolean on any thread: the service's view work always runs
     * on the main thread; background callers wait briefly on a latch.
     *
     * v1.2.3 (owner 2026-10-16): [warningVariant] renders the Stage-1 guard
     * warning (lime accent + warning mascot + warning copy — not a
     * punishment) through this same window-proof layer.
     */
    private fun showViaA11y(
        blockedPackage: String,
        appName: String,
        durationSec: Int,
        reason: String?,
        isPunishment: Boolean,
        warningVariant: Boolean = false,
        // v1.2.4: offending window bounds (screen coords) — clips the
        // overlay to exactly that window. Null = fullscreen path.
        windowBounds: android.graphics.Rect? = null
    ): Boolean {
        val svc = AiStudyAccessibilityService.connectedInstance ?: return false
        if (svc.isA11yBlockOverlayShowing()) return true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return svc.showA11yBlockOverlay(blockedPackage, appName, durationSec, reason, isPunishment, warningVariant, windowBounds)
        }
        val result = java.util.concurrent.atomic.AtomicBoolean(false)
        val latch = java.util.concurrent.CountDownLatch(1)
        mainHandler.post {
            try {
                result.set(
                    svc.showA11yBlockOverlay(blockedPackage, appName, durationSec, reason, isPunishment, warningVariant, windowBounds)
                )
            } finally {
                latch.countDown()
            }
        }
        latch.await(4, java.util.concurrent.TimeUnit.SECONDS)
        return result.get()
    }

    private fun showShortsViaA11y(surfaceLabel: String): Boolean {
        val svc = AiStudyAccessibilityService.connectedInstance ?: return false
        if (svc.isA11yShortsOverlayShowing()) return true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return svc.showA11yShortsOverlay(surfaceLabel)
        }
        val result = java.util.concurrent.atomic.AtomicBoolean(false)
        val latch = java.util.concurrent.CountDownLatch(1)
        mainHandler.post {
            try {
                result.set(svc.showA11yShortsOverlay(surfaceLabel))
            } finally {
                latch.countDown()
            }
        }
        latch.await(4, java.util.concurrent.TimeUnit.SECONDS)
        return result.get()
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else mainHandler.post(action)
    }

    /**
     * v1.2.3: this is the APP-layer path (TYPE_APPLICATION_OVERLAY).
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
        isPunishment: Boolean = false,
        warningVariant: Boolean = false
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
        show(context.applicationContext, blockedPackage, appName, durationSec, reason, isPunishment, warningVariant)
        return isShowing()
    }

    private fun show(
        appContext: Context,
        blockedPackage: String,
        appName: String,
        durationSec: Int,
        reason: String?,
        isPunishment: Boolean,
        warningVariant: Boolean = false
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
                                mascotRes = if (warningVariant) R.drawable.mascot_warning_transparent
                                else R.drawable.mascot_blocked,
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

    /** Hide the overlay if visible (both layers). Safe to call any time, any thread. */
    fun hide(context: Context) {
        // v1.2.3: the accessibility-layer overlay lives in the service.
        AiStudyAccessibilityService.connectedInstance?.hideA11yBlockOverlay()
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
     * v1.2.3 — single choke point for every block-screen intercept.
     * Path priority: accessibility-layer overlay (window-proof on every OEM,
     * incl. freeform/small windows and split-screen) → app-process overlay →
     * legacy fullscreen Activity. The path actually used is logged to the
     * Guard Hub activity feed so it is visible on-device which layer ran.
     *
     * Belt + braces (vivo V2403, build-39): in a multi-window context the
     * accessibility overlay is shown AND the fullscreen BlockedAppActivity is
     * also launched when the overlay can't be confirmed — freeform contexts
     * always (the OEM small-window layer question), other modes only if no
     * draw callback arrives within ~800ms. Single choke point + visibility
     * dedupe: no loops, no flicker.
     */
    fun intercept(
        context: Context,
        blockedPackage: String,
        appName: String,
        durationSec: Int = 0,
        reason: String? = null,
        isPunishment: Boolean = false,
        multiWindowMode: String? = null,
        // v1.2.4: offending window bounds (screen coords) for split-pane /
        // freeform interceptions — the block UI covers ONLY that window.
        windowBounds: android.graphics.Rect? = null
    ) {
        // v1.1.3: Guard Hub diagnostics — every intercept is recorded with
        // the concrete reason so device tests are evidence-based.
        // v1.2.2 (share screen): record every real intercept so the Session
        // Complete "Shield" card can show the apps blocked during the session.
        SessionBlockRecorder.record(context, blockedPackage, appName)
        val alreadyShowing = isBlockUiShowing()
        val path = resolveOverlayPath(context)
        val shown = when (path) {
            OverlayPath.A11Y -> showViaA11y(blockedPackage, appName, durationSec, reason, isPunishment, windowBounds = windowBounds)
            OverlayPath.APP -> showIfPermitted(context, blockedPackage, appName, durationSec, reason, isPunishment)
            OverlayPath.ACTIVITY -> false
        }
        // v1.2.3: braces — a second, guaranteed-top layer for multi-window.
        // v1.2.4: when the overlay is clipped to the offending window's
        // bounds (split/freeform), it IS the correctly-placed block — do not
        // launch the fullscreen braces Activity on top of it. Braces stay
        // for the no-bounds floating case and the no-draw watchdog below.
        var bracesLaunched = false
        if (shown && !alreadyShowing) {
            if (multiWindowMode == "floating" && windowBounds == null) {
                bracesLaunched = launchBracesActivity(
                    context, blockedPackage, appName, durationSec, reason, isPunishment, "floating"
                )
            } else if (path == OverlayPath.A11Y) {
                // No-draw watchdog: addView succeeding doesn't prove the frame
                // composited. If nothing drew in 800ms, bring the Activity.
                // v1.2.4: now also covers floating/split interceptions whose
                // bounds-clipped overlay is the placed block.
                mainHandler.postDelayed({
                    try {
                        val svc = AiStudyAccessibilityService.connectedInstance
                        if (isBlockUiShowing() && svc?.isA11yBlockOverlayDrawn() != true) {
                            launchBracesActivity(
                                context, blockedPackage, appName, durationSec,
                                reason, isPunishment, "no-draw-800ms"
                            )
                        }
                    } catch (_: Exception) { }
                }, 800)
            }
        }
        // v1.2.3 diagnostic: honest, on-device evidence of which render layer
        // actually blocked the app (owner device-test aid). Transition-only:
        // refreshes of an already-visible overlay don't spam the feed.
        if (!alreadyShowing) {
            try {
                com.example.util.AiStudyGuardManager.logGuardActivity(
                    context, appName, "overlay-path",
                    if (shown) "shown" else "fallback",
                    "path=" + path.name.lowercase() +
                        (if (multiWindowMode != null) " mode=$multiWindowMode" else "") +
                        (if (windowBounds != null)
                            " overlay=bounds ${windowBounds.width()}x${windowBounds.height()}@${windowBounds.left},${windowBounds.top}"
                        else "") +
                        if (bracesLaunched) " braces=activity" else ""
                )
            } catch (_: Exception) { }
        }
        if (shown) return
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

    // v1.2.3 — braces Activity bookkeeping.
    private var lastBracesLaunchMs = 0L
    private var lastBracesPkg: String? = null

    /**
     * Launches the fullscreen BlockedAppActivity as the guaranteed top layer
     * behind/around the overlay. Deduped: never twice for the same package
     * within 5s, never while it's already showing. Every attempt (and its
     * failure reason) is logged to the Guard Hub.
     */
    private fun launchBracesActivity(
        context: Context,
        blockedPackage: String,
        appName: String,
        durationSec: Int,
        reason: String?,
        isPunishment: Boolean,
        why: String
    ): Boolean {
        val now = System.currentTimeMillis()
        if (com.example.ui.screens.BlockedAppLockActivity.isCurrentlyShowing) return true
        if (now - lastBracesLaunchMs < 5000L && lastBracesPkg == blockedPackage) return false
        lastBracesLaunchMs = now
        lastBracesPkg = blockedPackage
        return try {
            val intent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                context = context,
                packageName = blockedPackage,
                appName = appName,
                durationSec = durationSec,
                reason = reason,
                isPunishment = isPunishment
            )
            context.startActivity(intent)
            try {
                com.example.util.AiStudyGuardManager.logGuardActivity(
                    context, appName, "overlay-path", "braces",
                    "activity launched why=$why"
                )
            } catch (_: Exception) { }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Braces block Activity failed: ${e.message}")
            try {
                com.example.util.AiStudyGuardManager.logGuardActivity(
                    context, appName, "overlay-path", "braces-failed",
                    (e.message ?: "start failed").take(120)
                )
            } catch (_: Exception) { }
            false
        }
    }

    /** Finishes the braces Activity if it is up (overlay exit paths call this). */
    fun dismissBracesActivity() {
        try {
            com.example.ui.screens.BlockedAppLockActivity.currentInstance?.finish()
        } catch (_: Exception) { }
    }

    /** True when any block UI (overlay or legacy Activity) is on screen. */
    fun isBlockUiShowing(): Boolean =
        isShowing() || com.example.ui.screens.BlockedAppLockActivity.isCurrentlyShowing

    // ------------------------------------------------------------------
    // v1.2.3 (owner 2026-10-16): Stage-1 guard warning as a WARNING variant
    // of the block overlay, through the TYPE_ACCESSIBILITY_OVERLAY path.
    //
    // ROOT CAUSE: AiStudyGuardManager.triggerStage1Warning launched the
    // warning Activity with a background startActivity — Android 10+
    // background activity launch (BAL) restrictions silently drop that
    // launch while the user is inside the offending app, so the warning
    // never appeared and the first visible guard surface was the stage-2
    // block. The accessibility overlay layer needs no BAL exemption: it
    // renders above the offending app exactly when detection works (the
    // a11y service is connected).
    //
    // The warning shares the block overlay UI as a warning variant (lime
    // accent, warning mascot, warning reason copy) — never a punishment.
    // ------------------------------------------------------------------

    /** Package of the currently-shown Stage-1 warning overlay, null when none. */
    private var warningOverlayPackage: String? = null

    /**
     * Shows the Stage-1 guard warning. Path priority mirrors [intercept]:
     * accessibility layer → app overlay → legacy warning Activity.
     * Returns true when any path rendered, so the caller can log a real
     * Guard Hub failure instead of swallowing it.
     */
    fun showWarningOverlay(
        context: Context,
        packageName: String,
        appName: String,
        durationSec: Int,
        reason: String
    ): Boolean {
        // A real block UI already on screen wins — never downgrade it to a
        // warning. The warning state (grace timer, hub entry) still applies.
        if (isShowing()) {
            warningOverlayPackage = packageName
            try {
                com.example.util.AiStudyGuardManager.logGuardActivity(
                    context, appName, "warning-overlay", "shown", "kept-existing-block-ui"
                )
            } catch (_: Exception) { }
            return true
        }
        var shown = false
        var usedPath = "none"
        if (AiStudyAccessibilityService.connectedInstance != null) {
            shown = showViaA11y(packageName, appName, durationSec, reason, isPunishment = false, warningVariant = true)
            if (shown) usedPath = "a11y"
        }
        if (!shown && Settings.canDrawOverlays(context)) {
            shown = showIfPermitted(context, packageName, appName, durationSec, reason, isPunishment = false, warningVariant = true)
            if (shown) usedPath = "app"
        }
        if (!shown) {
            shown = launchLegacyWarningActivity(context, packageName, appName, durationSec, reason)
            if (shown) usedPath = "activity"
        }
        if (shown) {
            warningOverlayPackage = packageName
            try {
                com.example.util.AiStudyGuardManager.logGuardActivity(
                    context, appName, "warning-overlay", "shown", "path=$usedPath"
                )
            } catch (_: Exception) { }
        } else {
            Log.e(TAG, "Stage-1 warning: every overlay path failed for $packageName")
        }
        return shown
    }

    /**
     * Dismisses the Stage-1 warning overlay ONLY when it is still the
     * warning this package showed (stage-3 resolve). Never touches an
     * overlay that has since become a real block for another package.
     */
    fun hideWarningOverlay(context: Context, packageName: String) {
        val expected = warningOverlayPackage ?: return
        if (!expected.equals(packageName, ignoreCase = true)) return
        warningOverlayPackage = null
        val currentPkg = currentOverlayPackage()
        if (currentPkg != null && !currentPkg.equals(packageName, ignoreCase = true)) return
        hide(context)
        dismissBracesActivity()
    }

    /** Package currently named by the block/warning overlay, or null when hidden. */
    private fun currentOverlayPackage(): String? {
        val a11yPkg = try {
            AiStudyAccessibilityService.connectedInstance?.a11yBlockOverlayPackage()
        } catch (_: Exception) { null }
        if (!a11yPkg.isNullOrBlank()) return a11yPkg
        val appPkg = blockedPackageState.value
        return appPkg.ifBlank { null }
    }

    /** Last-resort warning surface: the legacy warning Activity (may be BAL-dropped). */
    private fun launchLegacyWarningActivity(
        context: Context,
        packageName: String,
        appName: String,
        durationSec: Int,
        reason: String
    ): Boolean {
        return try {
            val intent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                context = context,
                packageName = packageName,
                appName = appName,
                durationSec = durationSec,
                reason = reason,
                isPunishment = false,
                guardVariant = com.example.ui.screens.BlockedAppLockActivity.GUARD_VARIANT_WARNING
            )
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Stage-1 warning: legacy Activity fallback failed: ${t.message}")
            false
        }
    }

    // ------------------------------------------------------------------
    // v1.2.2 (C): Shorts & Reels surface overlay.
    //
    // Standalone surface lock: shown when the Shorts & Reels toggle is on and
    // ShortsSurfaceDetector finds a real Shorts/Reels surface. The
    // accessibility service performs a back-press first, so "Return" simply
    // dismisses the overlay back into the app (long-form stays watchable).
    // Never touches the AI discipline flow.
    // ------------------------------------------------------------------

    /** True when the Shorts & Reels overlay is on screen (either layer). */
    fun isShortsBlockShowing(): Boolean =
        shortsOverlayView != null ||
            AiStudyAccessibilityService.connectedInstance?.isA11yShortsOverlayShowing() == true

    /**
     * Shows the Shorts & Reels overlay. Prefers the accessibility layer
     * (v1.2.3, window-proof); then the app overlay; false when neither is
     * available (the caller still performed the back-press, so the surface
     * is gone regardless).
     */
    fun showShortsBlock(context: Context, surfaceLabel: String): Boolean {
        if (isShortsBlockShowing()) {
            shortsLabelState.value = surfaceLabel
            return true
        }
        // v1.2.3: accessibility layer first — same window-proofing as the
        // main block overlay.
        if (showShortsViaA11y(surfaceLabel)) {
            try {
                com.example.util.AiStudyGuardManager.logGuardActivity(
                    context, surfaceLabel, "overlay-path", "shown", "path=a11y"
                )
            } catch (_: Exception) { }
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

    /** Hide the Shorts & Reels overlay if visible (both layers). Safe to call any time, any thread. */
    fun hideShortsBlock() {
        AiStudyAccessibilityService.connectedInstance?.hideA11yShortsOverlay()
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

/**
 * v1.2.2 (share screen) — per-session record of apps the shield actually
 * intercepted. Written on every real block (intercept() choke point +
 * Shorts & Reels surface intercepts), read by the Session Complete "Shield"
 * card. SharedPreferences ring (cap 80, prune > 45 days). Real data only —
 * never seeded with fake apps.
 */
object SessionBlockRecorder {
    private const val TAG = "SessionBlockRec"
    private const val PREFS = "focusly_local_data"
    private const val KEY = "session_blocked_apps_json"
    private const val CAP = 80
    private const val PRUNE_MS = 45L * 24 * 3600 * 1000

    data class BlockedApp(val packageName: String, val appName: String, val ts: Long)

    fun record(context: Context, packageName: String, appName: String) {
        try {
            if (packageName.isBlank()) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = org.json.JSONArray(prefs.getString(KEY, "[]") ?: "[]")
            arr.put(
                org.json.JSONObject()
                    .put("ts", System.currentTimeMillis())
                    .put("pkg", packageName)
                    .put("name", appName.ifBlank { packageName })
            )
            val cutoff = System.currentTimeMillis() - PRUNE_MS
            val kept = org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optLong("ts", 0L) >= cutoff) kept.put(o)
            }
            while (kept.length() > CAP) kept.remove(0)
            prefs.edit().putString(KEY, kept.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "record failed", e)
        }
    }

    /** Apps blocked within [fromMs, toMs], deduplicated by package (first hit kept). */
    fun forWindow(context: Context, fromMs: Long, toMs: Long): List<BlockedApp> {
        try {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = org.json.JSONArray(prefs.getString(KEY, "[]") ?: "[]")
            val seen = LinkedHashMap<String, BlockedApp>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ts = o.optLong("ts", 0L)
                if (ts < fromMs || ts > toMs) continue
                val pkg = o.optString("pkg", "")
                if (pkg.isBlank() || seen.containsKey(pkg)) continue
                seen[pkg] = BlockedApp(pkg, o.optString("name", pkg), ts)
            }
            return seen.values.toList()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "forWindow failed", e)
            return emptyList()
        }
    }
}
