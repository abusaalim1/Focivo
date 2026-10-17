package com.example.ui.overlay

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.5.1 — guards the overlay lifecycle host that replaced the crash where a
 * ComposeView hosted in a TYPE_APPLICATION_OVERLAY window had no
 * ViewTreeLifecycleOwner (IllegalStateException on Android 16).
 *
 * v1.3.0 assembly: Robolectric runner — LifecycleRegistry touches
 * Looper.getMainLooper(), which is not mocked in plain JVM unit tests.
 */
@RunWith(RobolectricTestRunner::class)
class OverlayLifecycleHostTest {

    @Test
    fun show_drivesLifecycleToResumed() {
        val host = OverlayLifecycleHost()
        assertEquals(Lifecycle.State.INITIALIZED, host.lifecycle.currentState)

        host.show()

        assertEquals(Lifecycle.State.RESUMED, host.lifecycle.currentState)
        // All three owners must be served by the same host instance.
        assertNotNull(host.savedStateRegistry)
        assertNotNull(host.viewModelStore)
    }

    @Test
    fun dismiss_drivesLifecycleToDestroyed_andReShowIsIgnoredSafely() {
        val host = OverlayLifecycleHost()
        host.show()
        assertTrue(host.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))

        host.dismiss()

        assertEquals(Lifecycle.State.DESTROYED, host.lifecycle.currentState)
        // A second dismiss on a destroyed registry must not crash.
        host.dismiss()
        assertEquals(Lifecycle.State.DESTROYED, host.lifecycle.currentState)
    }
}
