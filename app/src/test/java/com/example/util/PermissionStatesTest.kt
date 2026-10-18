package com.example.util

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Process
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppOpsManager
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowSettings

/**
 * v1.3.3 — unit tests for the shared live permission predicates and the
 * ON_RESUME refresh mechanism every permission row/sheet/screen now uses.
 *
 * Honest limits (documented, not hidden):
 * - isAccessibilityEnabled needs a real AccessibilityManager / running
 *   service — not meaningfully unit-testable under Robolectric, so it is
 *   covered by a real-device screenshot instead.
 * - isDeviceAdminActive needs a real DevicePolicyManager with an active
 *   admin — ShadowDevicePolicyManager has no active-admin setter in
 *   Robolectric 4.16, so it is covered by a real-device screenshot instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PermissionStatesTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun lifecycleOwner(): Pair<LifecycleOwner, LifecycleRegistry> {
        val owner = object : LifecycleOwner {
            private val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        return owner to (owner.lifecycle as LifecycleRegistry)
    }

    @Test
    fun `canDrawOverlays reflects the live system state`() {
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(PermissionStates.canDrawOverlays(context))

        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(PermissionStates.canDrawOverlays(context))
    }

    @Test
    @Config(sdk = [32])
    fun `isNotificationGranted pre-33 follows NotificationManager`() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm: ShadowNotificationManager = shadowOf(nm)

        shadowNm.setNotificationsEnabled(false)
        assertFalse(PermissionStates.isNotificationGranted(context))

        shadowNm.setNotificationsEnabled(true)
        assertTrue(PermissionStates.isNotificationGranted(context))
    }

    @Test
    @Config(sdk = [34])
    fun `isNotificationGranted 33-plus follows the runtime permission`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp: ShadowApplication = shadowOf(app)

        shadowApp.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(PermissionStates.isNotificationGranted(context))

        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(PermissionStates.isNotificationGranted(context))
    }

    @Test
    fun `isUsageAccessGranted reflects the live AppOps mode`() {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val shadowAppOps: ShadowAppOpsManager = shadowOf(appOps)
        val op = AppOpsManager.strOpToOp(AppOpsManager.OPSTR_GET_USAGE_STATS)

        shadowAppOps.setMode(op, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED)
        assertFalse(PermissionStates.isUsageAccessGranted(context))

        shadowAppOps.setMode(op, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
        assertTrue(PermissionStates.isUsageAccessGranted(context))
    }

    @Test
    fun `observeResume fires the callback on ON_RESUME only`() {
        val (_, registry) = lifecycleOwner()
        var calls = 0
        val observer = observeResume(registry) { calls++ }

        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertEquals(0, calls)

        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(1, calls)

        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertEquals(1, calls)

        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(2, calls)

        registry.removeObserver(observer)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(2, calls)
    }

    @Test
    fun `monitor state flow refreshes after a simulated resume`() {
        // The exact pattern every fixed screen/sheet uses:
        // stale-at-first snapshot -> system state changes behind our back ->
        // ON_RESUME -> refresh() -> collectors see the fresh values.
        ShadowSettings.setCanDrawOverlays(false)
        val monitor = PermissionStateMonitor(context)
        assertFalse(monitor.states.value.overlay)

        ShadowSettings.setCanDrawOverlays(true)
        // Nothing re-read yet: still stale, exactly the reported bug shape.
        assertFalse(monitor.states.value.overlay)

        val (_, registry) = lifecycleOwner()
        observeResume(registry) { monitor.refresh() }

        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertFalse(monitor.states.value.overlay)

        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertTrue(monitor.states.value.overlay)
    }
}
