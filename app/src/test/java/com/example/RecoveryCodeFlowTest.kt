package com.example

import com.example.data.RecoveryCodeResult
import com.example.ui.viewmodel.RecoveryCodeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * v1.3.3 (ITEM 10): unit tests for the OTP-code recovery state machine.
 * The machine is shared by password recovery (OtpType.Email.RECOVERY) and
 * signup confirmation (OtpType.Email.SIGNUP) - the verifier is injected, so
 * no Android or network is needed.
 */
class RecoveryCodeFlowTest {

    private fun flowWith(
        verifier: suspend (String, String) -> RecoveryCodeResult,
        now: AtomicLong = AtomicLong(0L)
    ) = RecoveryCodeFlow(
        scope = CoroutineScope(Dispatchers.Unconfined),
        verifier = verifier,
        clock = { now.get() }
    )

    private fun awaitStable(flow: RecoveryCodeFlow): RecoveryCodeFlow.CodeState = runBlocking {
        withTimeout(5_000) {
            flow.codeState.first {
                it !is RecoveryCodeFlow.CodeState.Entry &&
                        it !is RecoveryCodeFlow.CodeState.Verifying
            }
        }
    }

    @Test
    fun `correct code transitions to Verified - navigates to reset sheet`() {
        val flow = flowWith(verifier = { _, _ -> RecoveryCodeResult.Verified })
        flow.submitCode("user@example.com", "123456")
        assertEquals(RecoveryCodeFlow.CodeState.Verified, awaitStable(flow))
    }

    @Test
    fun `wrong code yields WrongCode state and does NOT navigate`() {
        val flow = flowWith(verifier = { _, _ -> RecoveryCodeResult.WrongCode })
        flow.submitCode("user@example.com", "000000")
        val state = awaitStable(flow)
        assertTrue(state is RecoveryCodeFlow.CodeState.WrongCode)
        assertNotEquals(RecoveryCodeFlow.CodeState.Verified, state)
        assertEquals(1, (state as RecoveryCodeFlow.CodeState.WrongCode).attempts)
    }

    @Test
    fun `expired code yields ExpiredCode state - resend is offered`() {
        val flow = flowWith(verifier = { _, _ -> RecoveryCodeResult.ExpiredCode })
        flow.submitCode("user@example.com", "000000")
        assertEquals(RecoveryCodeFlow.CodeState.ExpiredCode, awaitStable(flow))
    }

    @Test
    fun `short code never hits the network`() {
        var calls = 0
        val flow = flowWith(verifier = { _, _ -> calls++; RecoveryCodeResult.Verified })
        flow.submitCode("user@example.com", "123")
        val state = runBlocking { flow.codeState.first() }
        assertTrue(state is RecoveryCodeFlow.CodeState.Error)
        assertEquals(0, calls)
    }

    @Test
    fun `verifier exception surfaces as Error state`() {
        val flow = flowWith(verifier = { _, _ -> throw RuntimeException("boom") })
        flow.submitCode("user@example.com", "123456")
        val state = awaitStable(flow)
        assertTrue(state is RecoveryCodeFlow.CodeState.Error)
    }

    @Test
    fun `resend cooldown blocks immediate resend and allows after 60s`() {
        val now = AtomicLong(1_000_000L)
        val flow = flowWith(verifier = { _, _ -> RecoveryCodeResult.Verified }, now = now)
        flow.markCodeSent()
        assertFalse(flow.canResend())
        assertEquals(60L, flow.secondsUntilResend())
        now.addAndGet(59_000L)
        assertFalse(flow.canResend())
        assertEquals(1L, flow.secondsUntilResend())
        now.addAndGet(2_000L) // 61s elapsed
        assertTrue(flow.canResend())
        assertEquals(0L, flow.secondsUntilResend())
    }

    @Test
    fun `signup-mode verifier success also reaches Verified`() {
        // Signup confirmation wires OtpType.Email.SIGNUP into the same machine.
        val flow = flowWith(verifier = { _, _ -> RecoveryCodeResult.Verified })
        flow.submitCode("newuser@example.com", "654321")
        assertEquals(RecoveryCodeFlow.CodeState.Verified, awaitStable(flow))
    }

    @Test
    fun `resetToEntry clears error state back to entry`() {
        val flow = flowWith(verifier = { _, _ -> RecoveryCodeResult.WrongCode })
        flow.submitCode("user@example.com", "000000")
        awaitStable(flow)
        flow.resetToEntry()
        assertEquals(
            RecoveryCodeFlow.CodeState.Entry,
            runBlocking { flow.codeState.first() }
        )
    }
}
