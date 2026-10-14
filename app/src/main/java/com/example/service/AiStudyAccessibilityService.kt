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
import com.example.util.StrictModeManager
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
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isServiceConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isServiceConnected = false
        super.onDestroy()
        serviceScope.cancel()
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
                    val intent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                        context = ctx,
                        packageName = pkg,
                        appName = friendlyName,
                        durationSec = try { FocusShieldService.getRemainingSeconds(ctx) } catch (_: Exception) { 0 },
                        reason = "$friendlyName is locked during your active focus session ($mode windows are blocked during focus).",
                        isPunishment = false
                    )
                    try { ctx.startActivity(intent) } catch (_: Exception) {}
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
        if (com.example.ui.screens.BlockedAppLockActivity.isCurrentlyShowing) return

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
        val intent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
            context = context,
            packageName = blockedPackage,
            appName = appName,
            durationSec = remainingSec,
            reason = reason,
            isPunishment = false
        )
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BlockedAppLockActivity: ${e.message}")
        }
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
                    val intent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                        context = applicationContext,
                        packageName = pkg,
                        appName = "Home Screen",
                        durationSec = FocusShieldService.getRemainingSeconds(applicationContext),
                        reason = "Deep Focus is on — the home screen stays blocked until your session ends. Get back in there!",
                        isPunishment = false
                    )
                    try {
                        applicationContext.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start home-screen block overlay: ${e.message}")
                    }
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
                    val intent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                        context = applicationContext,
                        packageName = pkg,
                        appName = "App Installer",
                        durationSec = FocusShieldService.getRemainingSeconds(applicationContext),
                        reason = "Strict Mode Active: App Uninstallation is protected during your active study session.",
                        isPunishment = false
                    )
                    try {
                        applicationContext.startActivity(intent)
                    } catch (_: Exception) {}
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
                    val intent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                        context = applicationContext,
                        packageName = pkg,
                        appName = friendlyName,
                        durationSec = remainingSec,
                        reason = "3-Hour Penalty: $friendlyName is locked for non-study conversation.",
                        isPunishment = true
                    )
                    try {
                        applicationContext.startActivity(intent)
                    } catch (_: Exception) {}
                }
                return
            }

            // 5. Strictly block active scheduled blocked apps or active Focus Shield blocked apps immediately on launch
            if (isWindowStateChanged && isStudyPeriod && !isMonitoredYouTube) {
                val blockedList = FocusShieldService.getActiveBlockedPackages(applicationContext)
                if (blockedList.any { blocked -> pkg.equals(blocked, ignoreCase = true) || pkg.startsWith(blocked, ignoreCase = true) }) {
                    if (!com.example.ui.screens.BlockedAppLockActivity.isCurrentlyShowing || (nowTime - lastGeneralAppInterceptTimeMs > 1000L) || lastGeneralInterceptedPkg != pkg) {
                        lastGeneralAppInterceptTimeMs = nowTime
                        lastGeneralInterceptedPkg = pkg
                        Log.w(TAG, "[Focus Shield Lock] Opening blocked app ($pkg) during active study!")
                        val friendlyName = FocusShieldService.friendlyAppName(applicationContext, pkg)
                        val remainingSec = FocusShieldService.getRemainingSeconds(applicationContext)
                        val intent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                            context = applicationContext,
                            packageName = pkg,
                            appName = friendlyName,
                            durationSec = remainingSec,
                            reason = "$friendlyName is locked during your active focus session.",
                            isPunishment = false
                        )
                        try {
                            applicationContext.startActivity(intent)
                        } catch (_: Exception) {}
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
                val intent = com.example.ui.screens.BlockedAppLockActivity.createIntent(
                    context = applicationContext,
                    packageName = pkg,
                    appName = friendlyName,
                    durationSec = 0,
                    reason = "Daily screen time limit reached today for $friendlyName.",
                    isPunishment = false
                )
                try {
                    applicationContext.startActivity(intent)
                } catch (_: Exception) {}
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
                            // 7A. Shorts Lock (Blocks Shorts viewer & shelf)
                            if (isShortsLockOn && isYouTubeShortsScreen(rootNode)) {
                                val now = System.currentTimeMillis()
                                if (now - lastShortsInterceptTimeMs > 2000L) {
                                    lastShortsInterceptTimeMs = now
                                    Log.w(TAG, "[Shorts Lock] YouTube Shorts detected on screen! Intercepting...")
                                    pauseMediaPlayback()
                                    AiStudyGuardManager.onNonStudyDetected(
                                        context = applicationContext,
                                        packageName = "com.google.android.youtube",
                                        messageText = "YouTube Shorts",
                                        reason = "YouTube Shorts are locked during active study sessions."
                                    )
                                }
                                return
                            }

                            // 7B. Intelligent YouTube AI Content Filter (Analyzes active video title & channel with Gemini AI)
                            if (isYouTubeAiFilterOn) {
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

                if (ShortsLockManager.isReelsLockEnabled(applicationContext)) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        try {
                            if (isInstagramReelsScreen(rootNode)) {
                                val now = System.currentTimeMillis()
                                if (now - lastShortsInterceptTimeMs > 2000L) {
                                    lastShortsInterceptTimeMs = now
                                    Log.w(TAG, "[Reels Lock] Instagram Reels detected! Intercepting...")
                                    forceStopMediaAndDismissToFocivo(
                                        context = applicationContext,
                                        blockedPackage = "com.instagram.android.reels",
                                        appName = "Instagram Reels",
                                        reason = "Instagram Reels are locked during active study sessions.",
                                        isGeminiIntercept = true
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
                                                        if (!gv.isStudyAllowed || gv.isAdult) {
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
                                                        // v1.1.9 diagnostic
                                                        AiStudyGuardManager.logGuardActivity(applicationContext, chatLabel, "chat-gemini", "UNCERTAIN", "gemini-error", msgCopy)
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
                                            if (gv.isDistraction || !gv.isStudyAllowed) {
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

    /**
     * Inspects accessibility nodes to check if current screen inside YouTube is Shorts.
     */
    private fun isYouTubeShortsScreen(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 12) return false

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        if (viewId.contains("reel_player") ||
            viewId.contains("reel_recycler") ||
            viewId.contains("reel_watch_fragment") ||
            viewId.contains("shorts_player") ||
            viewId.contains("shorts_container") ||
            viewId.contains("pivot_shorts") ||
            viewId.contains("reel_viewer") ||
            viewId.contains("reel_item_layout") ||
            viewId.contains("shorts_sound_metadata") ||
            viewId.contains("shorts_chip")
        ) {
            return true
        }

        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""

        if (text.contains("remix with this audio") ||
            text.contains("dislike this short") ||
            text.contains("like this short") ||
            text.contains("#shorts") ||
            desc.contains("shorts player") ||
            desc.contains("dislike this short") ||
            desc.contains("like this short") ||
            desc.contains("remix this short") ||
            desc.contains("shorts, selected")
        ) {
            return true
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            val found = isYouTubeShortsScreen(child, depth + 1)
            try { child.recycle() } catch (_: Exception) {}
            if (found) return true
        }
        return false
    }

    /**
     * Inspects accessibility nodes to check if current screen inside Instagram is Reels.
     */
    private fun isInstagramReelsScreen(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 10) return false

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        if (viewId.contains("clips_video_container") ||
            viewId.contains("reel_viewer_container") ||
            viewId.contains("clips_viewer_container") ||
            viewId.contains("clips_tab")
        ) {
            return true
        }

        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        if (desc.contains("reels viewer") || desc.contains("reels tab") || desc == "reels, selected") {
            return true
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            val found = isInstagramReelsScreen(child, depth + 1)
            try { child.recycle() } catch (_: Exception) {}
            if (found) return true
        }
        return false
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

        if (!quickVerdict.isStudyAllowed || quickVerdict.isAdult) {
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
                if (!geminiVerdict.isStudyAllowed || geminiVerdict.isAdult) {
                    // v1.1.9 diagnostic
                    AiStudyGuardManager.logGuardActivity(
                        applicationContext, "YouTube", "youtube-gemini", "NON_STUDY",
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
                    // v1.1.9 diagnostic
                    AiStudyGuardManager.logGuardActivity(applicationContext, "YouTube", "youtube-gemini", "STUDY", "gemini-allow", title)
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
