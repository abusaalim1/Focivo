package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.AppUpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.3.2 ITEM 2 regression: a given version triggers AT MOST ONE "update
 * available" notification, across every check path (app-open + scheduled).
 * A genuinely newer version may notify once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateNotifyDedupeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("focivo_app_update_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun sameVersion_notifiesAtMostOnce() {
        assertTrue(AppUpdateManager.claimNotifyForVersion(context, 28))
        // Second claim for the same version — even from another check path — is denied.
        assertFalse(AppUpdateManager.claimNotifyForVersion(context, 28))
        assertFalse(AppUpdateManager.claimNotifyForVersion(context, 28))
    }

    @Test
    fun newerVersion_mayNotifyOnce() {
        assertTrue(AppUpdateManager.claimNotifyForVersion(context, 28))
        assertFalse(AppUpdateManager.claimNotifyForVersion(context, 28))
        assertTrue(AppUpdateManager.claimNotifyForVersion(context, 29))
        assertFalse(AppUpdateManager.claimNotifyForVersion(context, 29))
    }

    @Test
    fun olderVersion_neverRenotifies() {
        assertTrue(AppUpdateManager.claimNotifyForVersion(context, 29))
        assertFalse(AppUpdateManager.claimNotifyForVersion(context, 28))
    }
}
