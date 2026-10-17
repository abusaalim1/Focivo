package com.example

import com.example.util.StrictTamperGuard
import com.example.util.StrictTamperGuard.TamperKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
