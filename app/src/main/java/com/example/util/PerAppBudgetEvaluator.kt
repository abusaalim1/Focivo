package com.example.util

/**
 * v1.3.3 — pure per-app budget evaluation, extracted from
 * [AppDailyLimitsManager] so the decision is unit-testable without Android.
 *
 * Rule: a per-app budget is exceeded when today's real usage reaches the
 * configured daily limit AND no emergency bypass window is currently active.
 */
object PerAppBudgetEvaluator {

    /**
     * @param usageMinutesToday today's real foreground minutes for the app
     * @param dailyLimitMinutes the configured budget (0 / negative = no budget)
     * @param emergencyBypassUntilMs end of an active emergency bypass window
     *        (0 when none); while now < this the app is never "exceeded"
     * @param nowMs current time (injectable for tests)
     */
    fun isExceeded(
        usageMinutesToday: Int,
        dailyLimitMinutes: Int,
        emergencyBypassUntilMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (dailyLimitMinutes <= 0) return false
        if (emergencyBypassUntilMs > nowMs) return false
        return usageMinutesToday >= dailyLimitMinutes
    }
}
