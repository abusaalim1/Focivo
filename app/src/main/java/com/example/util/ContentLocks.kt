package com.example.util

import android.content.Context
import com.example.data.AndroidPreferenceSessionManager

/**
 * v1.2.4 — central content-lock authority.
 *
 * ADMIN ACCOUNT (owner order 2026-10-16 19:57 IST): the exact signed-in
 * email abusalim202322@gmail.com unlocks EVERYTHING (themes, clock faces,
 * Pomodoro designs, outfit sets, all point-locked content) regardless of
 * Focus Points. Every other account keeps the normal lock ladder.
 *
 * Single choke point: [isAdminUnlocked] performs the one email comparison;
 * every lock check (TimerFace/TimerTheme/MascotOutfitSet/PomodoroStyle
 * isUnlocked) takes an isAdmin flag consumed via default params, so no
 * call site scatters its own email conditional.
 */
object ContentLocks {
    const val ADMIN_EMAIL = "abusalim202322@gmail.com"

    /** Pure email comparison — unit-testable without a Context. */
    fun isAdminEmail(email: String?): Boolean =
        ADMIN_EMAIL.equals(email?.trim(), ignoreCase = true)

    /** True when the device's signed-in account is the owner's admin account. */
    fun isAdminUnlocked(context: Context): Boolean =
        isAdminEmail(
            runCatching { AndroidPreferenceSessionManager.getStoredEmail(context) }.getOrNull()
        )
}
