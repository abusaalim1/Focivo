package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.MainActivity
import com.example.util.AiStudyGuardManager
import com.example.util.EssentialAppsGuard
import com.example.util.GeminiContentClassifier
import com.example.util.OnDeviceStudyClassifier
import com.example.util.ShortsLockManager
import com.example.util.StrictModeManager
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
        private var lastShortsInterceptTimeMs: Long = 0L
        private var lastYouTubeAiInterceptTimeMs: Long = 0L
        private var lastScreenAiInterceptTimeMs: Long = 0L
        private var lastDeepFocusInterceptTimeMs: Long = 0L
        private var lastGeneralAppInterceptTimeMs: Long = 0L
        private var lastGeneralInterceptedPkg: String? = null
        private var lastPunishmentInterceptTimeMs: Long = 0L
        private var lastPunishedPackageIntercepted: String? = null
        private val lastEvaluatedScreenSignatures = LinkedHashMap<String, Long>()
    }

    override fun onDestroy() {
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

            // 2. Essential apps, system launchers, folders, emergency, dialer, camera, gallery are PERMANENTLY protected
            if (EssentialAppsGuard.isEssentialApp(applicationContext, pkg)) {
                return
            }

            val isWindowStateChanged = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            val isScheduleActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(applicationContext)
            val isShieldActive = FocusShieldService.isShieldActive.value && !FocusShieldService.isPunishmentLock.value
            val isStudyPeriod = isScheduleActive || isShieldActive || AiStudyGuardManager.isStudyPeriodActive(applicationContext)
            val isStrictActive = StrictModeManager.shouldBlockUninstallation(applicationContext)

            val nowTime = System.currentTimeMillis()

            // 3. Strict Mode: Guard uninstaller apps only during active study schedule / timer / shield
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
                        reason = "🔒 Strict Mode Active: App Uninstallation is protected during your active study session.",
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
            if (isWindowStateChanged && isStudyPeriod) {
                val blockedList = FocusShieldService.getActiveBlockedPackages(applicationContext)
                if (blockedList.any { blocked -> pkg.equals(blocked, ignoreCase = true) || pkg.startsWith(blocked, ignoreCase = true) }) {
                    if (nowTime - lastGeneralAppInterceptTimeMs > 2000L || lastGeneralInterceptedPkg != pkg) {
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

            // 7. YouTube Shorts & AI Study Filter
            val isYouTube = pkg == "com.google.android.youtube" || pkg.contains("youtube", ignoreCase = true)
            if (isYouTube) {
                // If YouTube is explicitly in the user's blocked list during active study, block the entire app!
                val blockedList = FocusShieldService.getActiveBlockedPackages(applicationContext)
                val isYouTubeBlocked = isStudyPeriod && blockedList.any { it.contains("youtube", ignoreCase = true) }
                if (isYouTubeBlocked && isWindowStateChanged) {
                    val now = System.currentTimeMillis()
                    if (now - lastYouTubeAiInterceptTimeMs > 1500L) {
                        lastYouTubeAiInterceptTimeMs = now
                        Log.w(TAG, "[Focus Shield] YouTube is blocked during active study! Intercepting...")
                        forceStopMediaAndDismissToFocivo(
                            context = applicationContext,
                            blockedPackage = pkg,
                            appName = "YouTube",
                            reason = "YouTube is blocked during your active focus session.",
                            isGeminiIntercept = false
                        )
                    }
                    return
                }

                // If Shorts Lock is on, check active screen
                val isShortsLockOn = ShortsLockManager.isShortsLockEnabled(applicationContext) || isStudyPeriod
                val isYouTubeAiFilterOn = YouTubeStudyGuardManager.isFilterEnabled(applicationContext) || isStudyPeriod

                if (isShortsLockOn || isYouTubeAiFilterOn) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        try {
                            // 7A. Shorts Lock (Blocks Shorts viewer & shelf)
                            if (isShortsLockOn && isYouTubeShortsScreen(rootNode)) {
                                val now = System.currentTimeMillis()
                                if (now - lastShortsInterceptTimeMs > 1500L) {
                                    lastShortsInterceptTimeMs = now
                                    Log.w(TAG, "[Shorts Lock] YouTube Shorts detected on screen! Intercepting...")
                                    forceStopMediaAndDismissToFocivo(
                                        context = applicationContext,
                                        blockedPackage = "com.google.android.youtube.shorts",
                                        appName = "YouTube Shorts",
                                        reason = "YouTube Shorts are locked to protect your focus! Normal educational lectures and tutorials are allowed.",
                                        isGeminiIntercept = true
                                    )
                                }
                                return
                            }

                            // 7B. Intelligent YouTube AI Content Filter (Analyzes active video title & channel with Gemini AI)
                            if (isYouTubeAiFilterOn) {
                                val activeVideo = extractPlayingYouTubeVideo(rootNode)
                                if (activeVideo != null && activeVideo.title.isNotBlank() && activeVideo.title.length >= 4) {
                                    val quickVerdict = GeminiContentClassifier.quickEvaluate(
                                        title = activeVideo.title,
                                        channel = activeVideo.channel,
                                        metadata = activeVideo.metadata
                                    )

                                    if (!quickVerdict.isStudyAllowed || quickVerdict.isAdult) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastYouTubeAiInterceptTimeMs > 1500L) {
                                            lastYouTubeAiInterceptTimeMs = now
                                            Log.w(TAG, "[YouTube AI Guard] Quick intercepting non-study video: '${activeVideo.title}' (${activeVideo.channel}) -> ${quickVerdict.reason}")
                                            YouTubeStudyGuardManager.recordBlockedVideo(
                                                context = applicationContext,
                                                title = activeVideo.title,
                                                channel = activeVideo.channel,
                                                reason = quickVerdict.reason
                                            )
                                            val blockLabel = if (quickVerdict.isAdult) "🔞 Inappropriate / 18+ Content" else "🔒 Non-Study Entertainment Blocked"
                                            val reasonMsg = "$blockLabel:\n\"${activeVideo.title}\"\n\nCategory: ${quickVerdict.category}\nReason: ${quickVerdict.reason}\n\nOnly educational lectures, exam prep, school/college subjects & coding tutorials are allowed!"
                                            forceStopMediaAndDismissToFocivo(
                                                context = applicationContext,
                                                blockedPackage = "com.google.android.youtube.entertainment",
                                                appName = "YouTube AI Guard",
                                                reason = reasonMsg,
                                                isGeminiIntercept = true
                                            )
                                        }
                                        return
                                    } else {
                                        // Launch background Gemini check for deep verification
                                        serviceScope.launch {
                                            val geminiVerdict = GeminiContentClassifier.evaluateWithGemini(
                                                title = activeVideo.title,
                                                channel = activeVideo.channel,
                                                metadata = activeVideo.metadata
                                            )
                                            if (!geminiVerdict.isStudyAllowed || geminiVerdict.isAdult) {
                                                val now = System.currentTimeMillis()
                                                if (now - lastYouTubeAiInterceptTimeMs > 1500L) {
                                                    lastYouTubeAiInterceptTimeMs = now
                                                    Log.w(TAG, "[Gemini AI Guard] Intercepting non-study video: '${activeVideo.title}' (${activeVideo.channel}) -> ${geminiVerdict.reason}")
                                                    YouTubeStudyGuardManager.recordBlockedVideo(
                                                        context = applicationContext,
                                                        title = activeVideo.title,
                                                        channel = activeVideo.channel,
                                                        reason = geminiVerdict.reason
                                                    )
                                                    val blockLabel = if (geminiVerdict.isAdult) "🔞 Inappropriate / 18+ Content" else "🔒 Non-Study Entertainment Blocked"
                                                    val reasonMsg = "$blockLabel:\n\"${activeVideo.title}\"\n\nCategory: ${geminiVerdict.category}\nReason: ${geminiVerdict.reason}\n\nOnly educational lectures, exam prep, school/college subjects & coding tutorials are allowed!"
                                                    forceStopMediaAndDismissToFocivo(
                                                        context = applicationContext,
                                                        blockedPackage = "com.google.android.youtube.entertainment",
                                                        appName = "YouTube AI Guard",
                                                        reason = reasonMsg,
                                                        isGeminiIntercept = true
                                                    )
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

            // 8. Instagram Reels Detection
            val isInstagram = pkg == "com.instagram.android" || pkg.contains("instagram", ignoreCase = true)
            if (isInstagram) {
                val blockedList = FocusShieldService.getActiveBlockedPackages(applicationContext)
                val isFullInstagramBlocked = isStudyPeriod && blockedList.any { it.contains("instagram", ignoreCase = true) }

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

                if (ShortsLockManager.isReelsLockEnabled(applicationContext) || isStudyPeriod) {
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
                                        reason = "Instagram Reels are locked to protect your focus!",
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

            // 9. AI Study Guard on AI chat apps (ChatGPT, Claude, Copilot, Perplexity)
            val isAiApp = pkg == "com.openai.chatgpt" ||
                    pkg == "com.anthropic.claude" ||
                    pkg == "com.google.android.apps.bard" ||
                    pkg.contains("chatgpt", ignoreCase = true) ||
                    pkg.contains("claude", ignoreCase = true) ||
                    pkg.contains("openai", ignoreCase = true) ||
                    pkg.contains("anthropic", ignoreCase = true) ||
                    pkg.contains("perplexity", ignoreCase = true) ||
                    pkg.contains("copilot", ignoreCase = true)

            if (isAiApp && (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || isWindowStateChanged) && AiStudyGuardManager.isAiGuardEnabled(applicationContext)) {
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
                            for (candidate in newCandidates) {
                                val result = OnDeviceStudyClassifier.evaluateContent(candidate)
                                if (result.isCasual) {
                                    Log.w(TAG, "[Non-Study Message] App: $pkg, Message: '$candidate', Reason: ${result.reason}")
                                    AiStudyGuardManager.onNonStudyDetected(
                                        context = applicationContext,
                                        packageName = pkg,
                                        messageText = candidate,
                                        reason = result.reason
                                    )
                                } else if (!result.reason.contains("benefit of doubt")) {
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
                            val afterBy = parts[1].trim()
                            val channelParts = afterBy.split(Regex("""[\-–•,\d]"""))
                            if (channelParts.isNotEmpty()) {
                                foundChannel = channelParts[0].trim()
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
