package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.util.AiStudyGuardManager
import com.example.util.OnDeviceStudyClassifier

class AiStudyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AiAccessibility"
        private var lastEvaluatedTextHash: Int = 0
        private var lastEvaluatedTimeMs: Long = 0L
    }

    private fun isSettingsOrPackageInstallerApp(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.android.settings" ||
               lower == "com.google.android.settings" ||
               lower == "com.samsung.android.settings" ||
               lower == "com.google.android.packageinstaller" ||
               lower == "com.android.packageinstaller" ||
               lower.contains("packageinstaller") ||
               lower.contains("securitycenter") ||
               lower.contains("uninstaller") ||
               lower.contains("appmanager") ||
               lower.contains("deviceadmin")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        val isScheduleActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(applicationContext)
        val isShieldActive = FocusShieldService.isShieldActive.value

        // Strictly block settings/uninstallation during active schedule or shield!
        if ((isScheduleActive || isShieldActive) && isSettingsOrPackageInstallerApp(pkg)) {
            Log.w(TAG, "[Uninstallation Guard] Attempted to open Settings/Installer ($pkg) during active study!")
            performGlobalAction(GLOBAL_ACTION_HOME)
            val intent = android.content.Intent(applicationContext, com.example.MainActivity::class.java).apply {
                action = FocusShieldService.ACTION_INTERCEPT_BLOCKED_APP
                putExtra(FocusShieldService.EXTRA_BLOCKED_PACKAGE, pkg)
                putExtra(FocusShieldService.EXTRA_BLOCKED_NAME, "Settings / Uninstaller")
                putExtra(FocusShieldService.EXTRA_PUNISHMENT_REASON, "Settings & Uninstallation locked during active Study Schedule! Focus on your study goals.")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            applicationContext.startActivity(intent)
            return
        }

        // Strictly block active scheduled blocked apps immediately!
        if (isScheduleActive || isShieldActive) {
            val blockedList = ScheduledBlockScheduler.getCurrentlyActiveBlockedPackages(applicationContext)
            if (blockedList.any { blocked -> pkg.equals(blocked, ignoreCase = true) || pkg.startsWith(blocked, ignoreCase = true) }) {
                Log.w(TAG, "[Strict Schedule Lock] Attempted to open blocked app ($pkg) during active study!")
                performGlobalAction(GLOBAL_ACTION_HOME)
                val intent = android.content.Intent(applicationContext, com.example.MainActivity::class.java).apply {
                    action = FocusShieldService.ACTION_INTERCEPT_BLOCKED_APP
                    putExtra(FocusShieldService.EXTRA_BLOCKED_PACKAGE, pkg)
                    putExtra(FocusShieldService.EXTRA_BLOCKED_NAME, pkg)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                applicationContext.startActivity(intent)
                return
            }
        }

        // Check if package matches YouTube, Web Browsers, or AI Assistant apps
        val isYouTubeOrBrowser = pkg == "com.google.android.youtube" ||
                pkg == "com.android.chrome" ||
                pkg.contains("chrome", ignoreCase = true) ||
                pkg.contains("browser", ignoreCase = true) ||
                pkg.contains("youtube", ignoreCase = true)

        val isTargetApp = isYouTubeOrBrowser ||
                pkg == "com.openai.chatgpt" ||
                pkg == "com.anthropic.claude" ||
                pkg.contains("chatgpt", ignoreCase = true) ||
                pkg.contains("claude", ignoreCase = true) ||
                pkg.contains("openai", ignoreCase = true) ||
                pkg.contains("anthropic", ignoreCase = true)

        if (!isTargetApp) return

        // Check if current time is an active study period (schedule window, session, or Focus Shield)
        val isStudyActive = isScheduleActive || isShieldActive || AiStudyGuardManager.isStudyPeriodActive(applicationContext)
        if (!isStudyActive) return

        AiStudyGuardManager.updateGuardStatusNotification(applicationContext)

        // Extract visible text from the root node
        val rootNode = rootInActiveWindow ?: return

        try {
            val textBuilder = StringBuilder()
            collectTextNodes(rootNode, textBuilder)
            val combinedText = textBuilder.toString().trim()

            if (combinedText.isNotBlank()) {
                val textHash = combinedText.hashCode()
                val now = System.currentTimeMillis()

                // Cooldown: If exact same on-screen text was evaluated less than 2 seconds ago, skip!
                if (textHash == lastEvaluatedTextHash && (now - lastEvaluatedTimeMs < 2000L)) {
                    return
                }
                lastEvaluatedTextHash = textHash
                lastEvaluatedTimeMs = now

                val textPreview = if (combinedText.length > 80) combinedText.take(80) + "..." else combinedText
                Log.d(TAG, "[Text Captured] pkg=$pkg, length=${combinedText.length}, preview='$textPreview'")

                // Classify captured text
                val result = OnDeviceStudyClassifier.evaluateContent(combinedText)
                Log.d(TAG, "[Classified] isCasual=${result.isCasual}, confidence=${result.confidence}, reason='${result.reason}'")

                // If non-study content detected in ChatGPT, Claude, or Chrome/Browser during active study schedule:
                if (result.isCasual) {
                    Log.w(TAG, "[Non-Study Observed] Non-study usage detected in $pkg: ${result.reason}")

                    AiStudyGuardManager.onNonStudyDetected(
                        context = applicationContext,
                        packageName = pkg,
                        reason = result.reason,
                        textHash = textHash
                    )
                } else if (result.reason.contains("Study")) {
                    Log.d(TAG, "[Study Observed] Educational study activity confirmed in $pkg")
                    AiStudyGuardManager.onStudyActivityDetected(
                        context = applicationContext,
                        packageName = pkg
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating node text: ${e.message}", e)
        } finally {
            try {
                rootNode.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo, builder: StringBuilder, depth: Int = 0) {
        if (depth > 12) return // Guard against deep node trees
        val text = node.text
        if (!text.isNullOrBlank()) {
            builder.append(text).append(" ")
        }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            collectTextNodes(child, builder, depth + 1)
            try {
                child.recycle()
            } catch (_: Exception) {}
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AiStudyAccessibilityService interrupted")
    }
}
