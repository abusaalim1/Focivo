package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.StrictModeManager
import com.example.util.StrictSessionStore
import com.example.util.StrictTamperAlertNotifier
import com.example.util.StrictTamperGuard
import com.example.util.UninstallProtectionGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.3.3 (owner order 2026-10-18, item 6b) — uninstall-attempt alerts.
 *
 * Rules under test:
 *  - the intercept path posts exactly one notification per throttle window;
 *  - no notification posts when the session is not protected
 *    (gate != PROTECTED);
 *  - the notification is cancelled at session end;
 *  - the notification text carries the real session end time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StrictTamperAlertTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Notification permission + strict/protection flags on.
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        StrictModeManager.setStrictModeEnabled(context, true)
        StrictModeManager.setUninstallProtectionEnabled(context, true)
        StrictSessionStore.disarm(context)
        // Fresh throttle state per test.
        context.getSharedPreferences("focivo_strict_tamper_alert", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun postedAlert(): Notification? {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return shadowOf(nm).getNotification(9160)
    }

    // ------------------------------------------------------------ pure policy

    @Test
    fun `exactly one post per throttle window`() {
        val t = 1_000_000L
        assertTrue(StrictTamperAlertNotifier.shouldNotify(t, 0L))
        // Second attempt 500ms later is inside the window: suppressed.
        assertFalse(StrictTamperAlertNotifier.shouldNotify(t + 500L, t))
        // Once the window has fully elapsed it may fire again.
        assertTrue(StrictTamperAlertNotifier.shouldNotify(t + 1500L, t))
    }

    @Test
    fun `only the PROTECTED gate may post`() {
        assertTrue(StrictTamperAlertNotifier.shouldPostForGate(UninstallProtectionGate.State.PROTECTED))
        for (s in UninstallProtectionGate.State.values()) {
            if (s != UninstallProtectionGate.State.PROTECTED) {
                assertFalse("gate $s must not post", StrictTamperAlertNotifier.shouldPostForGate(s))
            }
        }
    }

    @Test
    fun `titles are english and emoji-free`() {
        val t1 = StrictTamperAlertNotifier.titleFor(StrictTamperGuard.TamperKind.UNINSTALL_DIALOG)
        val t2 = StrictTamperAlertNotifier.titleFor(StrictTamperGuard.TamperKind.APP_INFO_UNINSTALL)
        val t3 = StrictTamperAlertNotifier.titleFor(StrictTamperGuard.TamperKind.DEVICE_ADMIN_TAMPER)
        assertEquals("Uninstall attempt blocked", t1)
        assertEquals("Uninstall attempt blocked", t2)
        assertEquals("Admin change blocked", t3)
        for (t in listOf(t1, t2, t3)) {
            assertTrue("title must be ASCII: $t", t.all { it.code < 128 })
        }
    }

    @Test
    fun `body contains the formatted real end time`() {
        val endMs = System.currentTimeMillis() + 3_600_000L
        val expected = SimpleDateFormat("h:mm a", Locale.US).format(Date(endMs))
        val body = StrictTamperAlertNotifier.bodyFor(endMs)
        assertTrue("body must contain end time '$expected': $body", body.contains(expected))
        assertTrue("body must be ASCII: $body", body.all { it.code < 128 })
    }

    // --------------------------------------------------------- intercept path

    @Test
    fun `intercept posts exactly one notification per throttle window`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        assertTrue(
            StrictTamperAlertNotifier.notifyIntercept(
                context, StrictTamperGuard.TamperKind.UNINSTALL_DIALOG
            )
        )
        assertNotNull(postedAlert())
        // Immediate second intercept: throttled, so no second post.
        assertFalse(
            StrictTamperAlertNotifier.notifyIntercept(
                context, StrictTamperGuard.TamperKind.UNINSTALL_DIALOG
            )
        )
    }

    @Test
    fun `no notification when the session is not protected`() {
        // No armed session -> the store reports inactive -> gate is not PROTECTED.
        assertFalse(
            StrictTamperAlertNotifier.notifyIntercept(
                context, StrictTamperGuard.TamperKind.UNINSTALL_DIALOG
            )
        )
        assertNull(postedAlert())
    }

    @Test
    fun `notification is cancelled at session end`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        assertTrue(
            StrictTamperAlertNotifier.notifyIntercept(
                context, StrictTamperGuard.TamperKind.DEVICE_ADMIN_TAMPER
            )
        )
        assertNotNull(postedAlert())
        StrictSessionStore.disarm(context)
        assertNull("alert must be cancelled when the session ends", postedAlert())
    }

    @Test
    fun `notification text contains the real session end time`() {
        val endMs = System.currentTimeMillis() + 3_600_000L
        StrictSessionStore.arm(context, endMs)
        assertTrue(
            StrictTamperAlertNotifier.notifyIntercept(
                context, StrictTamperGuard.TamperKind.UNINSTALL_DIALOG
            )
        )
        val n = postedAlert()
        assertNotNull(n)
        val title = n!!.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val expected = SimpleDateFormat("h:mm a", Locale.US).format(Date(endMs))
        assertEquals("Uninstall attempt blocked", title)
        assertTrue("text must contain real end time '$expected': $text", text.contains(expected))
    }
}
