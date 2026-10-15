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
     */
    private fun showViaA11y(
        blockedPackage: String,
        appName: String,
        durationSec: Int,
        reason: String?,
        isPunishment: Boolean
    ): Boolean {
        val svc = AiStudyAccessibilityService.connectedInstance ?: return false
        if (svc.isA11yBlockOverlayShowing()) return true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return svc.showA11yBlockOverlay(blockedPackage, appName, durationSec, reason, isPunishment)
        }
        val result = java.util.concurrent.atomic.AtomicBoolean(false)
        val latch = java.util.concurrent.CountDownLatch(1)
        mainHandler.post {
            try {
                result.set(
                    svc.showA11yBlockOverlay(blockedPackage, appName, durationSec, reason, isPunishment)
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
     */
    fun intercept(
        context: Context,
        blockedPackage: String,
        appName: String,
        durationSec: Int = 0,
        reason: String? = null,
        isPunishment: Boolean = false
    ) {
        // v1.2.2 (share screen): record every real intercept so the Session
        // Complete "Shield" card can show the apps blocked during the session.
        SessionBlockRecorder.record(context, blockedPackage, appName)
        val path = resolveOverlayPath(context)
        val shown = when (path) {
            OverlayPath.A11Y -> showViaA11y(blockedPackage, appName, durationSec, reason, isPunishment)
            OverlayPath.APP -> showIfPermitted(context, blockedPackage, appName, durationSec, reason, isPunishment)
            OverlayPath.ACTIVITY -> false
        }
        // v1.2.3 diagnostic: honest, on-device evidence of which render layer
        // actually blocked the app (owner device-test aid).
        try {
            com.example.util.AiStudyGuardManager.logGuardActivity(
                context, appName, "overlay-path",
                if (shown) "shown" else "fallback",
                "path=" + path.name.lowercase()
            )
        } catch (_: Exception) { }
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
