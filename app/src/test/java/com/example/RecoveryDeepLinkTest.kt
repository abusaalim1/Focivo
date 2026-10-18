package com.example

import android.net.Uri
import com.example.data.AuthLinkType
import com.example.data.SupabaseService
import com.example.data.isRecoveryDeeplink
import com.example.data.isSignupDeeplink
import com.example.data.linkTypeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.3.3 (ITEM 10): the deep-link fallback path is kept working next to the
 * OTP code flow. Classifies recovery/signup links (pure functions - no client
 * needed) and asserts the handler entry point is still present via a
 * compile-time callable reference.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecoveryDeepLinkTest {

    @Test
    fun `recovery deep link is classified as RECOVERY`() {
        val uri = Uri.parse("focivo://auth-callback?type=recovery&token=abc")
        assertTrue(isRecoveryDeeplink(uri))
        assertFalse(isSignupDeeplink(uri))
        assertEquals(AuthLinkType.RECOVERY, linkTypeOf(uri))
    }

    @Test
    fun `signup deep link is classified as SIGNUP`() {
        val uri = Uri.parse("focivo://auth-callback?type=signup&token=abc")
        assertTrue(isSignupDeeplink(uri))
        assertFalse(isRecoveryDeeplink(uri))
        assertEquals(AuthLinkType.SIGNUP, linkTypeOf(uri))
    }

    @Test
    fun `fragment type param is classified`() {
        val uri = Uri.parse("focivo://auth-callback#type=recovery&access_token=x")
        assertEquals(AuthLinkType.RECOVERY, linkTypeOf(uri))
        assertTrue(isRecoveryDeeplink(uri))
    }

    @Test
    fun `link with no type is UNKNOWN`() {
        val uri = Uri.parse("focivo://auth-callback?code=xyz")
        assertEquals(AuthLinkType.UNKNOWN, linkTypeOf(uri))
        assertFalse(isRecoveryDeeplink(uri))
        assertFalse(isSignupDeeplink(uri))
    }

    @Test
    fun `handleAuthDeeplink fallback path still present`() {
        // Compile-time callable reference: fails to compile if the handler is
        // ever removed, and asserts it is still a suspend function.
        val ref = SupabaseService::handleAuthDeeplink
        assertTrue(ref.isSuspend)
    }
}
