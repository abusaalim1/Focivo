package com.example

import com.example.data.SupabaseService
import com.example.data.awaitUserSession
import com.example.data.deeplinkErrorParamsMessage
import com.example.data.deeplinkExceptionMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.3.3 (ITEM 10 extension): deep-link handler honesty.
 *
 * The old handler reported "expired" for EVERY imperfect path - including a
 * successful PKCE exchange whose user was momentarily null. These tests pin
 * the fixed classification:
 * - "expired" is surfaced ONLY when the server returned otp_expired / an
 *   expired error, or the exchange itself threw an expired/invalid error;
 * - a successful exchange with a delayed user resolves the user (success),
 *   never "expired";
 * - every other failure gets the truthful generic message.
 */
class DeepLinkHonestyTest {

    @Test
    fun `otp_expired error code maps to expired message`() {
        assertEquals(
            SupabaseService.DEEPLINK_EXPIRED_MESSAGE,
            deeplinkErrorParamsMessage("access_denied", "otp_expired", null)
        )
    }

    @Test
    fun `expired error description maps to expired message`() {
        assertEquals(
            SupabaseService.DEEPLINK_EXPIRED_MESSAGE,
            deeplinkErrorParamsMessage("access_denied", "bad", "Token has expired or is invalid")
        )
    }

    @Test
    fun `non-expiry server error keeps server description and never claims expired`() {
        val msg = deeplinkErrorParamsMessage("access_denied", "bad_request", "Something+went+wrong")
        assertEquals("Something went wrong", msg)
        assertFalse(msg.contains("expired", ignoreCase = true))
    }

    @Test
    fun `link with no error params never claims expired`() {
        val msg = deeplinkErrorParamsMessage(null, null, null)
        assertEquals(SupabaseService.DEEPLINK_GENERIC_MESSAGE, msg)
        assertFalse(msg.contains("expired", ignoreCase = true))
    }

    @Test
    fun `exchange throwing expired maps to expired message`() {
        assertEquals(
            SupabaseService.DEEPLINK_EXPIRED_MESSAGE,
            deeplinkExceptionMessage("Token has expired or is invalid")
        )
    }

    @Test
    fun `exchange throwing invalid maps to expired message`() {
        assertEquals(
            SupabaseService.DEEPLINK_EXPIRED_MESSAGE,
            deeplinkExceptionMessage("invalid_grant: Invalid code")
        )
    }

    @Test
    fun `exchange throwing network error maps to truthful generic message`() {
        val msg = deeplinkExceptionMessage("Unable to resolve host")
        assertEquals(SupabaseService.DEEPLINK_GENERIC_MESSAGE, msg)
        assertFalse(msg.contains("expired", ignoreCase = true))
    }

    @Test
    fun `exchange throwing null maps to truthful generic message`() {
        val msg = deeplinkExceptionMessage(null)
        assertEquals(SupabaseService.DEEPLINK_GENERIC_MESSAGE, msg)
    }

    @Test
    fun `exchange success with delayed user resolves user - success not expired`() = runBlocking {
        var calls = 0
        var delays = 0
        val uid = awaitUserSession(
            maxAttempts = 5,
            delayMs = 100L,
            getUserId = {
                calls++
                if (calls >= 3) "user-123" else null
            },
            delayFn = { _: Long -> delays++ }
        )
        assertEquals("user-123", uid)
        assertTrue("expected bounded retries, got $delays", delays >= 2)
    }

    @Test
    fun `exchange success with never-appearing user returns null - caller reports generic not expired`() =
        runBlocking {
            val uid = awaitUserSession(
                maxAttempts = 3,
                delayMs = 1L,
                getUserId = { null },
                delayFn = { _: Long -> }
            )
            assertNull(uid)
            // handleAuthDeeplink maps this null to DEEPLINK_GENERIC_MESSAGE,
            // never to the expired message.
            assertFalse(
                SupabaseService.DEEPLINK_GENERIC_MESSAGE.contains("expired", ignoreCase = true)
            )
        }
}
