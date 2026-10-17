package com.example.util

import android.view.accessibility.AccessibilityNodeInfo

/**
 * v1.3.1 hotfix (owner order 2026-10-17, item A1) — Focivo-specific tamper
 * detection for strict sessions.
 *
 * The expected real gap (verified against Android's authority model): a
 * normal device admin only forces a DEACTIVATE-FIRST step, and nothing
 * guarded the deactivation path itself mid-session — two taps defeated it.
 * Regain-class protection therefore watches the tamper screens and kills
 * them the moment they target THIS app, scoped strictly to the protected
 * session window.
 *
 * Three tamper screens are detected, and ONLY when they name THIS app:
 *  1. The package-installer's uninstall confirmation for Focivo
 *     (stock AOSP, Google, and vivo installers; launcher-driven uninstall
 *     dialogs that name the app are covered too).
 *  2. The Settings app-info page for Focivo (Settings -> Apps -> Focivo).
 *  3. The device-admin management/deactivation screen while it names
 *     Focivo (deactivation requires an explicit system confirmation — we
 *     can never silently complete it, so this guard back-presses out of
 *     the screen mid-session).
 *
 * Detection is deliberately conservative: the screen must (a) belong to a
 * known tamper-capable package (installer/settings/launcher), (b) have a
 * tamper-plausible window class, AND (c) contain THIS app's package name or
 * label in its visible text. Our own app's screens (pkg == ownPackage) are
 * always excluded — Focivo's own UI mentions "Uninstall protection" a lot.
 *
 * All predicates are pure and unit-tested. The caller
 * (AiStudyAccessibilityService) fires the premium
 * StrictUninstallBlockedOverlay + GLOBAL_ACTION_BACK and logs every
 * intercept to Guard Hub.
 */
object StrictTamperGuard {

    /** What kind of tamper was spotted. */
    enum class TamperKind(val hubStage: String, val userReason: String) {
        UNINSTALL_DIALOG(
            "uninstall-guard",
            "You can't uninstall Focivo during your strict study session — the app is locked until the session ends."
        ),
        APP_INFO_UNINSTALL(
            "appinfo-guard",
            "Focivo is protected while your strict study session runs — the session must finish first."
        ),
        DEVICE_ADMIN_TAMPER(
            "admin-guard",
            "Your focus is guarded: device-admin changes are paused until the strict session ends."
        )
    }

    private val INSTALLER_PKGS = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.vivo.packageinstaller",
        "com.bbk.packageinstaller"
    )
    private val SETTINGS_PKGS = setOf(
        "com.android.settings",
        "com.vivo.settings"
    )
    // vivo's launcher drives its own uninstall confirmation dialog.
    private val LAUNCHER_PKGS = setOf(
        "com.bbk.launcher2",
        "com.vivo.launcher"
    )
    // Research (2026-10-17): vivo's iManager hosts the system app-manager /
    // security uninstall flow; the Play Store app page also carries an
    // Uninstall button. Both get the same conservative uninstall+naming
    // predicate below.
    private val STORE_PKGS = setOf(
        "com.vivo.imanager",
        "com.android.vending"
    )

    /**
     * Pure decision. Returns the tamper kind, or null when this screen is
     * not a Focivo-tamper screen. [screenText] is the lowercased visible
     * text of the window (bounded collection by the caller).
     */
    fun detect(
        pkg: String,
        className: String,
        screenText: String,
        ownPackage: String,
        appLabel: String
    ): TamperKind? {
        if (pkg.equals(ownPackage, ignoreCase = true)) return null
        val lowerClass = className.lowercase()
        val namesFocivo = screenText.contains(ownPackage.lowercase()) ||
                screenText.contains(appLabel.lowercase())

        // 1. Installer uninstall confirmation naming Focivo.
        val isInstaller = INSTALLER_PKGS.contains(pkg) ||
                pkg.contains("packageinstaller", ignoreCase = true) ||
                pkg.contains("uninstaller", ignoreCase = true)
        if (isInstaller) {
            val looksLikeUninstall = lowerClass.contains("uninstall") ||
                    screenText.contains("uninstall")
            if (looksLikeUninstall && namesFocivo) return TamperKind.UNINSTALL_DIALOG
        }
        // Launcher-driven uninstall dialog naming Focivo (vivo long-press
        // flow), plus vivo iManager and the Play Store app page — both can
        // host an Uninstall action for this app.
        if (LAUNCHER_PKGS.contains(pkg) || STORE_PKGS.contains(pkg)) {
            val looksLikeUninstall = screenText.contains("uninstall")
            if (looksLikeUninstall && namesFocivo) return TamperKind.UNINSTALL_DIALOG
        }

        // 2. Settings app-info page for Focivo.
        val isSettings = SETTINGS_PKGS.contains(pkg) || pkg == "com.android.settings"
        if (isSettings) {
            val isAppInfo = lowerClass.contains("installedappdetails") ||
                    lowerClass.contains("appinfodashboard") ||
                    lowerClass.contains("appinfo")
            if (isAppInfo && screenText.contains(ownPackage.lowercase())) {
                return TamperKind.APP_INFO_UNINSTALL
            }
            // 3. Device-admin management screen naming Focivo.
            val isAdminScreen = lowerClass.contains("deviceadmin")
            if (isAdminScreen && namesFocivo) return TamperKind.DEVICE_ADMIN_TAMPER
        }
        return null
    }

    /**
     * Collects the visible text of a window, bounded so a huge node tree can
     * never stall the accessibility service. Pure traversal; the caller
     * recycles the root.
     */
    fun collectScreenText(root: AccessibilityNodeInfo?, maxSnippets: Int = 60): String {
        if (root == null) return ""
        val sb = StringBuilder()
        var count = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 8 || count >= maxSnippets) return
            try {
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrBlank() && text.length <= 120) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(text.lowercase())
                    count++
                }
                val desc = node.contentDescription?.toString()?.trim()
                if (!desc.isNullOrBlank() && desc.length <= 120) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(desc.lowercase())
                    count++
                }
            } catch (_: Exception) { }
            if (count >= maxSnippets) return
            val n = try { node.childCount } catch (_: Exception) { 0 }
            for (i in 0 until n) {
                val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                walk(child, depth + 1)
                try { child.recycle() } catch (_: Exception) { }
            }
        }
        try { walk(root, 0) } catch (_: Exception) { }
        return sb.toString()
    }
}
