package com.example.util

/**
 * v1.3.0 follow-up (owner order 2026-10-17, item 2) — uninstall protection
 * truth table.
 *
 * The owner device-tested that uninstalling Focivo mid-study still SUCCEEDED:
 * the old "Uninstall protection" toggle in Deep Focus settings claimed
 * protection while the device-admin layer was never activated. This gate is
 * the single source of truth for what the UI reports — and it is HONEST:
 * PROTECTED is returned only when Android's own device-admin layer is
 * actually active.
 *
 * Decision order (first match wins):
 *  1. Strict Mode off              -> STRICT_OFF        (uninstall fully normal)
 *  2. User opted out of protection -> OPTED_OUT         (uninstall fully normal)
 *  3. No study session running     -> NO_SESSION        (uninstall fully normal)
 *  4. Device admin not active      -> ADMIN_INACTIVE    (uninstall POSSIBLE —
 *                                                       shown as OFF in the UI)
 *  5. Otherwise                   -> PROTECTED          (Android itself blocks
 *                                                       uninstall; deactivate
 *                                                       the admin first)
 *
 * PROTECTED mirrors StrictModeManager.shouldBlockUninstallation() plus the
 * device-admin-active check. Pure decision unit (unit-testable): decide()
 * never touches DPM, prefs, or the service layer — callers compute the four
 * booleans and pass them in.
 */
object UninstallProtectionGate {

    enum class State {
        /** Strict Mode is off — uninstall works completely normally. */
        STRICT_OFF,

        /** The user explicitly opted out — uninstall works completely normally. */
        OPTED_OUT,

        /** Strict Mode is on but no study session is running — uninstall normal. */
        NO_SESSION,

        /**
         * Strict + opted-in + session running, but the device admin was never
         * activated (or was revoked) — uninstall is CURRENTLY POSSIBLE.
         * The settings row must show this as OFF and offer activation.
         */
        ADMIN_INACTIVE,

        /**
         * Strict + opted-in + session + device admin ACTIVE — Android itself
         * routes every uninstall attempt through deactivation first.
         */
        PROTECTED
    }

    fun decide(
        strictEnabled: Boolean,
        protectionEnabled: Boolean,
        sessionActive: Boolean,
        adminActive: Boolean
    ): State = when {
        !strictEnabled -> State.STRICT_OFF
        !protectionEnabled -> State.OPTED_OUT
        !sessionActive -> State.NO_SESSION
        !adminActive -> State.ADMIN_INACTIVE
        else -> State.PROTECTED
    }
}
