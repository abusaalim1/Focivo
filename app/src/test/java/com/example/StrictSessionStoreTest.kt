package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.StrictModeManager
import com.example.util.StrictSessionStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.3.1 hotfix (owner order 2026-10-17, item A2) — durable strict-session
 * state with expiry validation.
 *
 * Rules under test:
 *  - arm() makes the session active (strict+protection default on);
 *  - an expired record NEVER resurrects: isStrictSessionActive() returns
 *    false and purges the record;
 *  - disarm() clears unconditionally.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StrictSessionStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Fresh Robolectric prefs default to strict on + protection on.
        StrictModeManager.setStrictModeEnabled(context, true)
        StrictModeManager.setUninstallProtectionEnabled(context, true)
        StrictSessionStore.disarm(context)
    }

    @Test
    fun `armed session with future end time is active`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        assertTrue(StrictSessionStore.isStrictSessionActive(context))
    }

    @Test
    fun `expired record never resurrects and is purged`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() - 1_000L)
        // Expired end time: must report inactive...
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
        // ...and must STAY inactive on a second read (purged, not revived).
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
    }

    @Test
    fun `disarm clears an armed session`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        assertTrue(StrictSessionStore.isStrictSessionActive(context))
        StrictSessionStore.disarm(context)
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
    }

    @Test
    fun `strict off reports inactive even when armed`() {
        StrictSessionStore.arm(context, System.currentTimeMillis() + 3_600_000L)
        StrictModeManager.setStrictModeEnabled(context, false)
        assertFalse(StrictSessionStore.isStrictSessionActive(context))
    }
}
