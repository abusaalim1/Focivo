package com.example.util

import android.content.Context
import com.example.data.AndroidPreferenceSessionManager

/**
 * v1.2.1 (owner-ordered RE-LOCK — the v1.2 test-unlock is over): Focus-Points
 * / level-gated content is locked again — outfit sets
 * (MascotBuddy.MascotOutfitSet), timer themes (TimerThemeManager.TimerTheme)
 * and timer faces (TimerFaceManager.TimerFace).
 *
 * All isUnlocked() checks short-circuit on this flag, so every lock
 * badge, lock dialog and price label in the UI reappears automatically
 * (they all derive from isUnlocked()). Threshold model: points are never
 * spent, a tier unlocks permanently once lifetime Focus Points reach it.
 *
 * v1.2.4: the owner admin account bypasses the ladder via
 * [ContentLocks.isAdminUnlocked] — see below.
 */
const val ALL_CONTENT_UNLOCKED = false

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
