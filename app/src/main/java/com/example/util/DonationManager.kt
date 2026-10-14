package com.example.util

import android.content.Context
import android.util.Log

/**
 * v1.1 — Donation popup trigger logic.
 *
 * Shows at most once every 3 days, on app open or after a session completes,
 * never mid-timer. Last-shown timestamp persisted in existing prefs
 * (focusly_local_data).
 */
object DonationManager {
    private const val TAG = "DonationManager"
    private const val PREFS = "focusly_local_data"
    private const val KEY_LAST_SHOWN = "donation_last_shown_ms"
    private const val MIN_INTERVAL_MS = 3L * 24 * 60 * 60 * 1000 // 3 days

    /**
     * Returns true if the donation popup should be shown now.
     * @param isTimerRunning if true, never show (never interrupt a session)
     */
    fun shouldShow(context: Context, isTimerRunning: Boolean): Boolean {
        if (isTimerRunning) return false
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastShown = prefs.getLong(KEY_LAST_SHOWN, 0L)
            val now = System.currentTimeMillis()
            (now - lastShown) >= MIN_INTERVAL_MS
        } catch (e: Exception) {
            Log.e(TAG, "shouldShow failed", e)
            false
        }
    }

    /** Records that the popup was shown (resets the 3-day clock). */
    fun markShown(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SHOWN, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "markShown failed", e)
        }
    }
}
