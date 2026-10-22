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

    // v1.1.3 FIX 1 — regression: strict off + no session + admin STILL
    // ACTIVE must report STRICT_OFF (ordinary Android uninstall). The admin
    // being present can never resurrect friction on its own.
    @Test
    fun `strict off with admin active and no session is ordinary uninstall`() {
        assertEquals(
            STRICT_OFF,
            UninstallProtectionGate.decide(
                strictEnabled = false,
                protectionEnabled = true,
                sessionActive = false,
                adminActive = true
            )
        )
    }

    // v1.1.3 FIX 1 — regression: a session that ended (paused, discarded,
    // completed) is sessionActive=false -> NO_SESSION even though the
    // admin is still active. Uninstall returns to normal immediately.
    @Test
    fun `ended session with admin active is ordinary uninstall`() {
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

    // v1.1.4d (ROUND D, item D1) — the owner's "biggest bug" acceptance
    // matrix, exercised end-to-end through the gate:
    //   Strict OFF                       -> normal uninstall
    //   Strict ON, no session            -> normal uninstall incl. the
    //                                       admin deactivate step
    //   Strict ON, session running      -> PROTECTED (popup + block)
    //   session paused/discarded/done    -> protection disarms at once
    // The D1 fix adds the in-app release (DeviceAdminGuard.canReleaseAdmin):
    // release is allowed in EVERY state except PROTECTED, so the state
    // that used to strand the owner (NO_SESSION with the admin still
    // active) now has a guaranteed one-tap exit inside the app.
    @Test
    fun `round D acceptance - strict on no session releases admin in app`() {
        val state = UninstallProtectionGate.decide(
            strictEnabled = true,
            protectionEnabled = true,
            sessionActive = false,
            adminActive = true
        )
        assertEquals(NO_SESSION, state)
        org.junit.Assert.assertTrue(
            "no-session state must allow the in-app admin release",
            com.example.util.DeviceAdminGuard.canReleaseAdmin(state)
        )
    }

    @Test
    fun `round D acceptance - live strict session refuses admin release`() {
        val state = UninstallProtectionGate.decide(
            strictEnabled = true,
            protectionEnabled = true,
            sessionActive = true,
            adminActive = true
        )
        assertEquals(PROTECTED, state)
        org.junit.Assert.assertFalse(
            "a live strict session must refuse the in-app admin release",
            com.example.util.DeviceAdminGuard.canReleaseAdmin(state)
        )
    }

    @Test
    fun `round D acceptance - release allowed in every non protected state`() {
        listOf(STRICT_OFF, OPTED_OUT, NO_SESSION, ADMIN_INACTIVE).forEach { state ->
            org.junit.Assert.assertTrue(
                "release must be allowed in $state",
                com.example.util.DeviceAdminGuard.canReleaseAdmin(state)
            )
        }
    }
}
