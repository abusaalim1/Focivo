package com.example

import android.net.Uri
import com.example.data.AuthLinkType
import com.example.data.SupabaseService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.3.3 (ITEM 10): the deep-link fallback path is kept working next to the
 * OTP code flow. Classifies recovery/signup links and asserts the handler
 * entry point is still present.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecoveryDeepLinkTest {

    private val service: SupabaseService by lazy { SupabaseService.getInstance() }

    @Test
    fun `recovery deep link is classified as RECOVERY`() {
        val uri = Uri.parse("focivo://auth-callback?type=recovery&token=abc")
        assertTrue(service.isRecoveryDeeplink(uri))
        assertFalse(service.isSignupDeeplink(uri))
        assertEquals(AuthLinkType.RECOVERY, service.linkTypeOf(uri))
    }

    @Test
    fun `signup deep link is classified as SIGNUP`() {
        val uri = Uri.parse("focivo://auth-callback?type=signup&token=abc")
        assertTrue(service.isSignupDeeplink(uri))
        assertFalse(service.isRecoveryDeeplink(uri))
        assertEquals(AuthLinkType.SIGNUP, service.linkTypeOf(uri))
    }

    @Test
    fun `fragment type param is classified`() {
        val uri = Uri.parse("focivo://auth-callback#type=recovery&access_token=x")
        assertEquals(AuthLinkType.RECOVERY, service.linkTypeOf(uri))
        assertTrue(service.isRecoveryDeeplink(uri))
    }

    @Test
    fun `link with no type is UNKNOWN`() {
        val uri = Uri.parse("focivo://auth-callback?code=xyz")
        assertEquals(AuthLinkType.UNKNOWN, service.linkTypeOf(uri))
        assertFalse(service.isRecoveryDeeplink(uri))
        assertFalse(service.isSignupDeeplink(uri))
    }

    @Test
    fun `handleAuthDeeplink fallback path still present`() {
        // Note: suspend functions carry a synthetic Continuation parameter on
        // the JVM, so match by name instead of exact signature.
        val methods = SupabaseService::class.java.declaredMethods
            .filter { it.name == "handleAuthDeeplink" }
        assertTrue(methods.isNotEmpty())
    }
}
