package com.example

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider

/**
 * Test-only helper: simulates the granted accessibility permission by
 * registering Focivo's AiStudyAccessibilityService in Settings.Secure —
 * the exact signal AiStudyGuardManager.isAccessibilityPermissionGranted()
 * reads on a real device (after the live-service fast path, which never
 * fires in Robolectric).
 */
fun setAiStudyServiceConnectedForTest(connected: Boolean) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val value = if (connected) {
        "${context.packageName}/com.example.service.AiStudyAccessibilityService"
    } else {
        ""
    }
    Settings.Secure.putString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        value
    )
}
