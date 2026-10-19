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
 *     (stock AOSP, Google, Samsung, Xiaomi, OPPO/realme/OnePlus, Huawei,
 *     vivo installers; launcher-driven uninstall dialogs that name the
 *     app are covered too, incl. Xiaomi's com.miui.home).
 *  2. The Settings app-info page for Focivo (Settings -> Apps -> Focivo),
 *     incl. OEM builds that host app-info as a fragment in a generic
 *     Settings activity (uninstall button + app name is the same screen).
 *  3. The device-admin management/deactivation screen while it names
 *     Focivo (incl. the in-window deactivation confirmation dialog —
 *     deactivation requires an explicit system confirmation, so we can
 *     never silently complete it; this guard back-presses out of the
 *     screen mid-session).
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
        "com.bbk.packageinstaller",
        // v1.3.x (item 3) — documented OEM installer packages. Every one of
        // these also matches the "packageinstaller" catch-all below; the
        // explicit list is belt-and-braces plus a readable audit surface.
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.miui.global.packageinstaller",
        "com.oppo.packageinstaller",
        "com.coloros.packageinstaller",
        "com.realme.packageinstaller",
        "com.oplus.packageinstaller",
        "com.oneplus.packageinstaller",
        "com.huawei.packageinstaller",
        "com.hihonor.packageinstaller",
        "com.motorola.packageinstaller",
        "com.asus.packageinstaller",
        "com.sonyericsson.packageinstaller",
        "com.nothing.packageinstaller",
        "com.transsion.packageinstaller"
    )
    private val SETTINGS_PKGS = setOf(
        "com.android.settings",
        "com.vivo.settings",
        // v1.3.x (item 3): other OEMs keep AOSP's package name, but Honor /
        // ColorOS variants use their own — the ".settings" catch-all below
        // covers them (com.hihonor.settings, com.coloros.settings, ...).
        "com.hihonor.settings",
        "com.coloros.settings"
    )
    // vivo's launcher drives its own uninstall confirmation dialog.
    // v1.3.x (item 3): any package with "launcher" in the name is treated
    // the same (Samsung/OPPO/Huawei/realme/OnePlus launchers all host
    // long-press uninstall dialogs); Xiaomi's System Launcher is
    // com.miui.home (no "launcher" in the name) so it is listed explicitly.
    private val LAUNCHER_PKGS = setOf(
        "com.bbk.launcher2",
        "com.vivo.launcher",
        "com.miui.home"
    )
    // Research (2026-10-17): vivo's iManager hosts the system app-manager /
    // security uninstall flow; the Play Store app page also carries an
    // Uninstall button. Both get the same conservative uninstall+naming
    // predicate below.
    // v1.3.x (item 3): other OEM app stores have the same per-app
    // Uninstall action (Samsung Galaxy Store, Xiaomi GetApps, OPPO App
    // Market, Huawei AppGallery, vivo App Store).
    private val STORE_PKGS = setOf(
        "com.vivo.imanager",
        "com.android.vending",
        "com.sec.android.app.samsungapps",
        "com.xiaomi.mipicks",
        "com.heytap.market",
        "com.oppo.market",
        "com.huawei.appmarket",
        "com.vivo.appstore"
    )

    /**
     * v1.3.x (item 3) — pure predicate: can this package plausibly host a
     * tamper screen? Used by AiStudyAccessibilityService as a CHEAP
     * pre-filter (string ops only) before the (slightly expensive)
     * session-gate reads, and inside [detect] so the two can never drift
     * apart.
     */
    fun isTamperCandidatePackage(pkg: String): Boolean {
        if (INSTALLER_PKGS.contains(pkg)) return true
        if (LAUNCHER_PKGS.contains(pkg)) return true
        if (STORE_PKGS.contains(pkg)) return true
        if (SETTINGS_PKGS.contains(pkg)) return true
        val lower = pkg.lowercase()
        if (lower.contains("packageinstaller") || lower.contains("uninstaller")) return true
        if (lower.contains(".settings")) return true
        if (lower.contains("launcher")) return true
        return false
    }

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
        // Launcher-driven uninstall dialog naming Focivo (long-press flow on
        // vivo/Samsung/Xiaomi/OPPO/Huawei/realme launchers), plus OEM app
        // stores — their per-app pages carry an Uninstall action.
        val isLauncher = LAUNCHER_PKGS.contains(pkg) ||
                pkg.contains("launcher", ignoreCase = true)
        if (isLauncher || STORE_PKGS.contains(pkg)) {
            val looksLikeUninstall = screenText.contains("uninstall")
            if (looksLikeUninstall && namesFocivo) return TamperKind.UNINSTALL_DIALOG
        }

        // 2. Settings app-info page for Focivo.
        val isSettings = SETTINGS_PKGS.contains(pkg) ||
                pkg.contains(".settings", ignoreCase = true) ||
                pkg == "com.android.settings"
        if (isSettings) {
            val isAppInfo = lowerClass.contains("installedappdetails") ||
                    lowerClass.contains("appinfodashboard") ||
                    lowerClass.contains("appinfo")
            if (isAppInfo && screenText.contains(ownPackage.lowercase())) {
                return TamperKind.APP_INFO_UNINSTALL
            }
            // 3. Device-admin management screen naming Focivo. Checked before
            // the uninstall-text fallback below: a "deviceadmin" class is the
            // stronger signal, and the management screen's own text can also
            // mention uninstalling.
            val isAdminScreen = lowerClass.contains("deviceadmin")
            if (isAdminScreen && namesFocivo) {
                // Never trap the admin GRANT flow: an "Activate device
                // administrator?" prompt is consent the user must be able to
                // give (the 1.4.0 round-2 trap rule). Only management /
                // deactivation wording trips the guard.
                val looksLikeActivation = screenText.contains("activate") &&
                        !screenText.contains("deactivate")
                if (!looksLikeActivation) return TamperKind.DEVICE_ADMIN_TAMPER
            }
            // v1.3.x (item 3): the deactivation CONFIRMATION is an in-window
            // dialog (class = the dialog, no "deviceadmin" hint). Its text is
            // unmistakable: "Deactivate device administrator?" naming the
            // app. Activation screens say "Activate", never "Deactivate", so
            // the grant flow stays untouched.
            val isDeactivationDialog = screenText.contains("deactivate") &&
                    (screenText.contains("administrator") || screenText.contains("device admin")) &&
                    namesFocivo
            if (isDeactivationDialog) return TamperKind.DEVICE_ADMIN_TAMPER
            // v1.3.x (item 3): some OEMs (newer vivo OriginOS, Honor) host
            // app-info as a fragment inside a generic Settings activity, so
            // the class name carries no "appinfo" hint. The app-info page is
            // still the only Settings screen with an Uninstall button, so a
            // Settings screen that names Focivo AND shows uninstall text is
            // the same tamper screen.
            if (screenText.contains("uninstall") && namesFocivo) {
                return TamperKind.APP_INFO_UNINSTALL
            }
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
