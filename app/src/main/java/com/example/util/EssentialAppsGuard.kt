package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Telephony

object EssentialAppsGuard {

    /**
     * Hardcoded list of permanently protected essential system packages:
     * - Home Launchers & System UI (folders, home screen, app drawer)
     * - Phone / Dialer
     * - Contacts
     * - Camera
     * - Gallery & Photos
     * - YouTube & YouTube Music
     * - Payment & UPI & Banking apps
     * - Clock & Alarm
     * - Messages / SMS
     * - Calculator & Notes
     */
    val PERMANENTLY_PROTECTED_PACKAGES: Set<String> = setOf(
        // System UI & Home Screen Launchers (Never block launchers or folders!)
        "com.android.systemui",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.sec.android.app.launcher",
        "com.samsung.android.app.launcher",
        "com.samsung.android.honeyboard",
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.oneplus.launcher",
        "com.huawei.android.launcher",
        "com.transsion.hilauncher",
        "com.transsion.XOSLauncher",
        "com.motorola.launcher3",
        "com.teslacoilsw.launcher",
        "com.actionlauncher.playstore",
        "com.smartlauncher",
        "com.lge.launcher2",
        "com.lge.launcher3",
        "com.bbk.launcher2",
        "com.vivo.launcher",
        "com.google.android.googlequicksearchbox",

        // Phone / Dialer / Telecom
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.google.android.apps.tachyon",
        "com.google.android.apps.meetings",

        // Contacts
        "com.google.android.contacts",
        "com.android.contacts",
        "com.samsung.android.app.contacts",
        "com.samsung.android.contacts",

        // Camera
        "com.google.android.GoogleCamera",
        "com.android.camera",
        "com.android.camera2",
        "com.sec.android.app.camera",
        "com.samsung.android.camera",
        "com.motorola.camera2",
        "com.oneplus.camera",
        "com.oppo.camera",
        "com.xiaomi.camera",
        "org.codeaurora.snapcam",

        // Gallery & Photos (Never Blocked)
        "com.google.android.apps.photos",
        "com.sec.android.gallery3d",
        "com.samsung.android.gallery",
        "com.android.gallery3d",
        "com.miui.gallery",
        "com.coloros.gallery3d",
        "com.oneplus.gallery",
        "com.oppo.gallery3d",
        "com.vivo.gallery",
        "com.simplemobiletools.gallery",
        "com.simplemobiletools.gallery.pro",

        // YouTube & YouTube Music
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids",

        // Payment, UPI & Banking Apps (Never Blocked)
        "com.paypal.android.p2pmobile",
        "net.one97.paytm",
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",
        "com.google.android.apps.walletnfcrel",
        "in.org.npci.upiapp",
        "com.dreamplug.androidapp", // Cred
        "com.mobikwik_new",
        "org.altruist.BharatPe",
        "com.freecharge.android",
        "com.sbi.lotusintouch",
        "com.msf.karing.sbi",
        "com.snapwork.hdfc",
        "com.csam.icici.bank.imobile",
        "com.axis.mobile",
        "com.msf.koc.mobilebanking",
        "com.infrasofttech.indianbank",
        "com.finacus.canarabank",
        "com.fss.pnb",
        "com.bankofbaroda.mconnect",
        "com.chase.sig.android",
        "com.wf.wellsfargomobile",
        "com.infonow.bofa",
        "com.citibank.mobile.us",
        "com.revolut.revolut",
        "co.uk.getmondo",

        // Clock & Alarm
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.samsung.android.app.clockpackage",

        // Messages / SMS
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.simplemobiletools.sms.messenger",

        // Basic Utilities (Calculator, Notes, Calendar)
        "com.google.android.calculator",
        "com.android.calculator2",
        "com.sec.android.app.popupcalculator",
        "com.google.android.keep",
        "com.google.android.calendar",
        "com.samsung.android.app.notes"
    )

    /**
     * Checks if a package is a permanently protected essential app or system launcher.
     */
    fun isEssentialApp(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val lower = packageName.lowercase().trim()
        val selfPkg = context.packageName.lowercase()

        // 1. Never block self (Focivo)
        if (lower == selfPkg) return true

        // 2. Direct package lookup
        if (PERMANENTLY_PROTECTED_PACKAGES.contains(lower)) return true

        // 3. Safety package name heuristic checks (Launchers, System UI, Calls, Banking)
        if (lower.contains("launcher") ||
            lower.contains("systemui") ||
            lower.contains("dialer") ||
            lower.contains("incallui") ||
            lower.contains("telephony") ||
            lower.contains("contact") ||
            lower.contains(".camera") ||
            lower.contains("emergency") ||
            lower.contains("gallery") ||
            lower.contains("photos") ||
            lower.contains("paytm") ||
            lower.contains("phonepe") ||
            lower.contains("paypal") ||
            lower.contains("paisa") ||
            lower.contains(".bank") ||
            lower.contains("banking") ||
            lower.contains("upi") ||
            lower.contains("wallet") ||
            lower.contains("deskclock") ||
            lower.contains("clockpackage") ||
            lower.contains("calculator") ||
            lower.contains("inputmethod") ||
            lower.contains("keyboard")
        ) {
            return true
        }

        // 4. Check dynamic default Home Launcher
        try {
            val pm = context.packageManager
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val homeActivities = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in homeActivities) {
                if (resolveInfo.activityInfo?.packageName?.lowercase() == lower) {
                    return true
                }
            }

            // Default Dialer
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val dialActivity = pm.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (dialActivity?.activityInfo?.packageName?.lowercase() == lower) return true

            // Default SMS
            val smsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            if (smsPackage?.lowercase() == lower) return true

            // Default Camera
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val cameraActivity = pm.resolveActivity(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (cameraActivity?.activityInfo?.packageName?.lowercase() == lower) return true
        } catch (_: Exception) {}

        return false
    }

    /**
     * Strips any permanently protected essential apps from a list or set of package names.
     */
    fun sanitizeBlockedPackages(context: Context, packages: Collection<String>): Set<String> {
        return packages.map { it.trim() }
            .filter { it.isNotBlank() && !isEssentialApp(context, it) }
            .toSet()
    }
}
