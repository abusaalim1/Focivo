package com.example.ui.viewmodel

import com.example.data.RecoveryCodeResult
import com.example.data.SupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * v1.3.3 (ITEM 10): testable OTP-code recovery state machine.
 *
 * The app no longer depends on the recovery email LINK at all: the user types
 * the 6-digit code from the email, the [verifier] calls
 * `SupabaseService.verifyRecoveryCode` (gotrue
 * `Auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, ...)`), and on success
 * the recovery session is already stored, so the UI can open the new-password
 * sheet directly.
 *
 * All time and network dependencies are injected ([clock], [verifier]), so
 * this class is unit-testable without Android or Robolectric.
 */
class RecoveryCodeFlow(
    private val scope: CoroutineScope,
    private val verifier: suspend (email: String, code: String) -> RecoveryCodeResult = { email, code ->
        SupabaseService.getInstance().verifyRecoveryCode(email, code)
    },
    private val clock: () -> Long = { System.currentTimeMillis() },
    val resendCooldownMs: Long = RESEND_COOLDOWN_MS
) {

    companion object {
        /** Resend throttle: a new code may be requested once per minute. */
        const val RESEND_COOLDOWN_MS = 60_000L
        const val CODE_LENGTH = 6
    }

    /** State of the code-entry step. `Verified` means: open the new-password sheet. */
    sealed interface CodeState {
        data object Entry : CodeState
        data object Verifying : CodeState
        data object Verified : CodeState
        data class WrongCode(val attempts: Int) : CodeState
        data object ExpiredCode : CodeState
        data class Error(val message: String) : CodeState
    }

    private val _codeState = MutableStateFlow<CodeState>(CodeState.Entry)
    val codeState: StateFlow<CodeState> = _codeState.asStateFlow()

    /** Live countdown (seconds) until resend is allowed again; 0 = allowed. */
    private val _resendCooldownSeconds = MutableStateFlow(0L)
    val resendCooldownSeconds: StateFlow<Long> = _resendCooldownSeconds.asStateFlow()

    private var lastSentAtMs: Long = -RESEND_COOLDOWN_MS
    private var tickerJob: Job? = null

    /** True when enough time has passed since the last sent code. */
    fun canResend(nowMs: Long = clock()): Boolean =
        nowMs - lastSentAtMs >= resendCooldownMs

    /** Whole seconds remaining until resend is allowed; 0 when allowed now. */
    fun secondsUntilResend(nowMs: Long = clock()): Long {
        val remaining = resendCooldownMs - (nowMs - lastSentAtMs)
        return if (remaining <= 0L) 0L else ceil(remaining / 1000.0).toLong()
    }

    /** Call whenever the recovery email is (re)sent; starts the resend cooldown. */
    fun markCodeSent(nowMs: Long = clock()) {
        lastSentAtMs = nowMs
        _codeState.value = CodeState.Entry
        startCooldownTicker()
    }

    private fun startCooldownTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                val remaining = secondsUntilResend()
                _resendCooldownSeconds.value = remaining
                if (remaining <= 0L) break
                delay(1000L)
            }
        }
    }

    /**
     * Submits the typed code. Malformed input (< 6 digits) never hits the
     * network and lands in [CodeState.Error]. Double-submits while a verify
     * is in flight are ignored.
     */
    fun submitCode(email: String, code: String) {
        val digits = code.filter { it.isDigit() }
        if (digits.length != CODE_LENGTH) {
            _codeState.value = CodeState.Error("Please enter all 6 digits.")
            return
        }
        if (_codeState.value == CodeState.Verifying) return
        val priorAttempts = (_codeState.value as? CodeState.WrongCode)?.attempts ?: 0
        _codeState.value = CodeState.Verifying
        scope.launch {
            val result = runCatching { verifier(email, digits) }
                .getOrElse { RecoveryCodeResult.Error("Could not verify the code. Please try again.") }
            _codeState.value = when (result) {
                RecoveryCodeResult.Verified -> CodeState.Verified
                RecoveryCodeResult.WrongCode -> CodeState.WrongCode(priorAttempts + 1)
                RecoveryCodeResult.ExpiredCode -> CodeState.ExpiredCode
                is RecoveryCodeResult.Error -> CodeState.Error(result.message)
            }
        }
    }

    /** Back to the entry state (e.g. user dismissed the sheet). */
    fun resetToEntry() {
        tickerJob?.cancel()
        _codeState.value = CodeState.Entry
        _resendCooldownSeconds.value = 0L
    }

    fun dispose() {
        tickerJob?.cancel()
    }
}
