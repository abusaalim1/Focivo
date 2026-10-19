package com.example

import com.example.util.StrictTamperGuard
import com.example.util.StrictTamperGuard.TamperKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.3.1 hotfix (owner order 2026-10-17, item A1) — Focivo-specific tamper
 * screen detection for protected strict sessions.
 *
 * The guard must fire ONLY when a tamper-capable screen NAMES this app;
 * our own app screens (and unrelated screens) must never trip it.
 */
class StrictTamperGuardTest {

    private val OWN = "com.aistudio.focusly.kqpzxr"
    private val LABEL = "Focivo"

    @Test
    fun `stock installer uninstall dialog naming focivo is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.android.packageinstaller",
                className = "com.android.packageinstaller.UninstallAlertActivity",
                screenText = "do you want to uninstall this app? focivo",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `vivo installer naming focivo package is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.vivo.packageinstaller",
                className = "com.vivo.packageinstaller.UninstallActivity",
                screenText = "uninstall com.aistudio.focusly.kqpzxr",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `launcher uninstall dialog naming focivo is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.bbk.launcher2",
                className = "com.bbk.launcher2.UninstallDialog",
                screenText = "uninstall focivo cancel",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `vivo imanager uninstall flow naming focivo is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.vivo.imanager",
                className = "com.vivo.imanager.AppManagerActivity",
                screenText = "uninstall focivo confirm",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `play store page with uninstall for focivo is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.android.vending",
                className = "com.android.vending.AssetBrowserActivity",
                screenText = "focivo uninstall open",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `settings app info page for focivo is detected`() {
        assertEquals(
            TamperKind.APP_INFO_UNINSTALL,
            StrictTamperGuard.detect(
                pkg = "com.android.settings",
                className = "com.android.settings.applications.InstalledAppDetailsTop",
                screenText = "focivo com.aistudio.focusly.kqpzxr uninstall force stop",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `device admin screen naming focivo is detected`() {
        assertEquals(
            TamperKind.DEVICE_ADMIN_TAMPER,
            StrictTamperGuard.detect(
                pkg = "com.android.settings",
                className = "com.android.settings.DeviceAdminSettings",
                screenText = "device admin apps focivo uninstall protection",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `installer screen for another app is ignored`() {
        assertNull(
            StrictTamperGuard.detect(
                pkg = "com.android.packageinstaller",
                className = "com.android.packageinstaller.UninstallAlertActivity",
                screenText = "do you want to uninstall this app? instagram",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `settings app info for another app is ignored`() {
        assertNull(
            StrictTamperGuard.detect(
                pkg = "com.android.settings",
                className = "com.android.settings.applications.InstalledAppDetailsTop",
                screenText = "instagram com.zhiliaoapp.musically uninstall",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `own app screens never trip the guard`() {
        assertNull(
            StrictTamperGuard.detect(
                pkg = OWN,
                className = "com.example.MainActivity",
                screenText = "uninstall protection strict mode device admin",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `unrelated settings screen is ignored`() {
        assertNull(
            StrictTamperGuard.detect(
                pkg = "com.android.settings",
                className = "com.android.settings.wifi.WifiSettings",
                screenText = "wifi focivo hotspot",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    // v1.3.x (item 3) — OEM coverage and fragment-hosted screens.

    @Test
    fun `samsung installer uninstall naming focivo is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.samsung.android.packageinstaller",
                className = "com.samsung.android.packageinstaller.UninstallActivity",
                screenText = "do you want to uninstall focivo?",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `miui installer uninstall naming focivo is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.miui.packageinstaller",
                className = "com.miui.packageinstaller.ui.UninstallActivity",
                screenText = "uninstall com.aistudio.focusly.kqpzxr",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `oppo installer uninstall naming focivo is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.oppo.packageinstaller",
                className = "com.oppo.packageinstaller.UninstallConfirm",
                screenText = "uninstall focivo cancel ok",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `samsung launcher long-press uninstall naming focivo is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.sec.android.app.launcher",
                className = "android.app.AlertDialog",
                screenText = "uninstall focivo?",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `xiaomi system launcher uninstall naming focivo is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.miui.home",
                className = "android.app.AlertDialog",
                screenText = "uninstall focivo cancel",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `fragment-hosted app info with uninstall button naming focivo is detected`() {
        // Newer vivo OriginOS / Honor host app-info in a generic Settings
        // activity — no "appinfo" in the class name — but the page still has
        // the Uninstall button and names the app.
        assertEquals(
            TamperKind.APP_INFO_UNINSTALL,
            StrictTamperGuard.detect(
                pkg = "com.vivo.settings",
                className = "com.vivo.settings.SubSettings",
                screenText = "focivo com.aistudio.focusly.kqpzxr uninstall force stop clear data",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `device admin deactivation confirm dialog naming focivo is detected`() {
        assertEquals(
            TamperKind.DEVICE_ADMIN_TAMPER,
            StrictTamperGuard.detect(
                pkg = "com.vivo.settings",
                className = "com.vivo.settings.VivoAlertDialog",
                screenText = "deactivate device administrator? focivo will no longer be a device administrator",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `device admin activation screen is never caught`() {
        // The grant flow says "Activate", never "Deactivate" — it must stay
        // untouched so the user can always activate the admin.
        assertNull(
            StrictTamperGuard.detect(
                pkg = "com.android.settings",
                className = "com.android.settings.DeviceAdminAdd",
                screenText = "activate device administrator? focivo",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `galaxy store page with uninstall for focivo is detected`() {
        assertEquals(
            TamperKind.UNINSTALL_DIALOG,
            StrictTamperGuard.detect(
                pkg = "com.sec.android.app.samsungapps",
                className = "com.sec.android.app.samsungapps.DetailActivity",
                screenText = "focivo uninstall open",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `unrelated launcher screen without uninstall text is ignored`() {
        assertNull(
            StrictTamperGuard.detect(
                pkg = "com.bbk.launcher2",
                className = "com.bbk.launcher2.Launcher",
                screenText = "focivo chrome camera",
                ownPackage = OWN,
                appLabel = LABEL
            )
        )
    }

    @Test
    fun `candidate package predicate covers oem tamper hosts`() {
        assertTrue(StrictTamperGuard.isTamperCandidatePackage("com.samsung.android.packageinstaller"))
        assertTrue(StrictTamperGuard.isTamperCandidatePackage("com.miui.packageinstaller"))
        assertTrue(StrictTamperGuard.isTamperCandidatePackage("com.bbk.launcher2"))
        assertTrue(StrictTamperGuard.isTamperCandidatePackage("com.miui.home"))
        assertTrue(StrictTamperGuard.isTamperCandidatePackage("com.vivo.settings"))
        assertTrue(StrictTamperGuard.isTamperCandidatePackage("com.hihonor.settings"))
        assertTrue(StrictTamperGuard.isTamperCandidatePackage("com.vivo.imanager"))
        assertTrue(StrictTamperGuard.isTamperCandidatePackage("com.sec.android.app.samsungapps"))
    }

    @Test
    fun `candidate package predicate rejects ordinary apps`() {
        assertFalse(StrictTamperGuard.isTamperCandidatePackage("com.google.android.youtube"))
        assertFalse(StrictTamperGuard.isTamperCandidatePackage("com.whatsapp"))
        assertFalse(StrictTamperGuard.isTamperCandidatePackage(OWN))
    }
}
