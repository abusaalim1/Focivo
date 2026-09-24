package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WeeklySubjectStat(
    val subject: String,
    val totalMinutes: Int,
    val sessionCount: Int,
    val percentage: Float
)

data class WeeklyDailyStat(
    val dayName: String, // "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
    val totalMinutes: Int,
    val isBestDay: Boolean = false
)

data class WeeklySessionDetail(
    val sessionId: Long,
    val title: String,
    val subject: String,
    val durationMinutes: Int,
    val completedAt: Long,
    val formattedTime: String,
    val notes: String = "",
    val mode: String = "Deep Work"
)

data class WeeklyRecapSummary(
    val weekIdentifier: String, // e.g., "Sep 10 – Sep 16, 2026"
    val weekKey: String, // e.g., "2026-W37"
    val weekStartTimestamp: Long,
    val weekEndTimestamp: Long,
    val totalMinutesFocused: Int,
    val totalHoursFormatted: String, // e.g., "14.5"
    val sessionCount: Int,
    val currentStreak: Int,
    val pointsEarned: Int = 0,
    val bestDayName: String,
    val bestDayMinutes: Int,
    val topSubject: String,
    val subjectBreakdowns: List<WeeklySubjectStat> = emptyList(),
    val dailyBreakdowns: List<WeeklyDailyStat> = emptyList(),
    val sessionDetails: List<WeeklySessionDetail> = emptyList(),
    val motivationalQuote: String = "Consistency is the key to mastery. Outstanding work this week!"
) {
    /**
     * Formats the weekly recap into a clean, shareable card for WhatsApp / Telegram / Socials.
     */
    fun toShareableText(): String {
        val sb = StringBuilder()
        sb.append("⚡ *My Sunday Study Recap on Focivo* 🚀\n")
        sb.append("📅 $weekIdentifier\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("⏱️ *Total Focus Time:* $totalHoursFormatted hrs ($totalMinutesFocused mins)\n")
        sb.append("📚 *Sessions Completed:* $sessionCount\n")
        if (currentStreak > 0) {
            sb.append("🔥 *Study Streak:* $currentStreak Day${if (currentStreak > 1) "s" else ""}\n")
        }
        if (topSubject.isNotBlank() && topSubject != "General Study") {
            sb.append("🌟 *Top Subject:* $topSubject\n")
        }
        sb.append("🏆 *Best Day:* $bestDayName (${bestDayMinutes}m logged)\n\n")

        if (subjectBreakdowns.isNotEmpty()) {
            sb.append("📖 *What I Studied This Week:*\n")
            for (sub in subjectBreakdowns.take(5)) {
                val hours = if (sub.totalMinutes >= 60) {
                    String.format(Locale.US, "%.1fh", sub.totalMinutes / 60.0)
                } else {
                    "${sub.totalMinutes}m"
                }
                sb.append("• ${sub.subject}: $hours (${sub.sessionCount} session${if (sub.sessionCount > 1) "s" else ""})\n")
            }
            sb.append("\n")
        }

        if (sessionDetails.isNotEmpty()) {
            sb.append("✨ *Highlights:*\n")
            for (session in sessionDetails.take(4)) {
                val noteSnippet = if (session.notes.isNotBlank()) " — \"${session.notes.take(30)}\"" else ""
                sb.append("✓ ${session.title.ifBlank { session.subject }} (${session.durationMinutes}m)$noteSnippet\n")
            }
            sb.append("\n")
        }

        sb.append("Building deep focus and crushing goals with Focivo ⚡\n")
        return sb.toString().trim()
    }
}
