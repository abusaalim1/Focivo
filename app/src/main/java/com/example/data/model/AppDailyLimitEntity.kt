package com.example.data.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class AppDailyLimitEntity(
    val id: String = java.util.UUID.randomUUID().toString(),
    val userId: String = "",
    val appPackage: String = "",
    val appName: String = "",
    val dailyLimitMinutes: Int = 60,
    val emergencyUsesAllowed: Int = 2,
    val emergencyUsesRemainingToday: Int = 2,
    val showReminders: Boolean = true,
    val strictModeEnabled: Boolean = false,
    val minutesUsedToday: Int = 0,
    val lastResetDate: String = "",
    val isEnabled: Boolean = true,
    val emergencyBypassUntilMs: Long = 0L,
    val reminder80SentToday: Boolean = false,
    val reminder15MinSentToday: Boolean = false,
    val limit100SentToday: Boolean = false
) {
    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    /**
     * Resets minutesUsedToday to 0 and emergencyUsesRemainingToday to emergencyUsesAllowed
     * if lastResetDate does not match today's date (YYYY-MM-DD).
     */
    fun checkAndResetDailyUsage(): AppDailyLimitEntity {
        val todayStr = getTodayDateString()
        if (lastResetDate != todayStr) {
            return copy(
                minutesUsedToday = 0,
                emergencyUsesRemainingToday = emergencyUsesAllowed,
                lastResetDate = todayStr,
                reminder80SentToday = false,
                reminder15MinSentToday = false,
                limit100SentToday = false,
                emergencyBypassUntilMs = 0L
            )
        }
        return this
    }

    fun isLimitReached(): Boolean = minutesUsedToday >= dailyLimitMinutes
}
