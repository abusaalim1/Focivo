package com.example

import com.example.service.AiStudyAccessibilityService

/**
 * Test-only helper: flips AiStudyAccessibilityService's service-connected
 * flag so guard tests can simulate the granted accessibility permission.
 *
 * Uses reflection deliberately: the flag lives in the service's companion
 * and its Kotlin visibility has churned while other workers edit the file —
 * reflection keeps these tests compiling regardless.
 */
fun setAiStudyServiceConnectedForTest(connected: Boolean) {
    val companion = AiStudyAccessibilityService.Companion
    val field = companion.javaClass.getDeclaredField("isServiceConnected")
    field.isAccessible = true
    field.setBoolean(companion, connected)
}
