package com.example

import com.example.util.UninstallProtectionGate
import com.example.util.UninstallProtectionGate.State.ADMIN_INACTIVE
import com.example.util.UninstallProtectionGate.State.NO_SESSION
import com.example.util.UninstallProtectionGate.State.OPTED_OUT
import com.example.util.UninstallProtectionGate.State.PROTECTED
import com.example.util.UninstallProtectionGate.State.STRICT_OFF
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.3.0 follow-up (owner order 2026-10-17, item 2).
 *
 * The owner device-tested that uninstalling Focivo mid-study still SUCCEEDED:
 * the old settings toggle claimed protection while the device admin was
 * never activated. The gate is the single source of truth the UI reports,
 * and it is honest — PROTECTED requires the device-admin layer to be
 * actually active.
 *
 * Precedence (first match wins): strict off > opted out > no session >
 * admin inactive > protected.
 */
class UninstallProtectionGateTest {

    @Test
    fun `strict off always reports strict off even when everything else is on`() {
        assertEquals(
            STRICT_OFF,
            UninstallProtectionGate.decide(
                strictEnabled = false,
                protectionEnabled = true,
                sessionActive = true,
                adminActive = true
            )
        )
    }

    @Test
    fun `opted out reports opted out even mid session with admin active`() {
        assertEquals(
            OPTED_OUT,
            UninstallProtectionGate.decide(
                strictEnabled = true,
                protectionEnabled = false,
                sessionActive = true,
                adminActive = true
            )
        )
    }

    @Test
    fun `no session reports no session even with admin active`() {
        assertEquals(
            NO_SESSION,
            UninstallProtectionGate.decide(
                strictEnabled = true,
                protectionEnabled = true,
                sessionActive = false,
                adminActive = true
            )
        )
    }

    @Test
    fun `admin inactive mid strict session is honestly not protected`() {
        // THE regression case: strict on + session running + user opted in,
        // but the admin was never activated. The old UI showed "protected".
        assertEquals(
            ADMIN_INACTIVE,
            UninstallProtectionGate.decide(
                strictEnabled = true,
                protectionEnabled = true,
                sessionActive = true,
                adminActive = false
            )
        )
    }

    @Test
    fun `fully armed reports protected`() {
        assertEquals(
            PROTECTED,
            UninstallProtectionGate.decide(
                strictEnabled = true,
                protectionEnabled = true,
                sessionActive = true,
                adminActive = true
            )
        )
    }

    @Test
    fun `everything off reports strict off`() {
        assertEquals(
            STRICT_OFF,
            UninstallProtectionGate.decide(
                strictEnabled = false,
                protectionEnabled = false,
                sessionActive = false,
                adminActive = false
            )
        )
    }
}
