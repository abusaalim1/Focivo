package com.example.data.squads

import kotlin.math.ceil

/**
 * Pure computations for the group member profile (v1.3.2, item 7).
 * All inputs are REAL synced values — these functions never invent numbers;
 * null means "unknown/absent" and the UI hides the corresponding tile.
 */
object MemberProfileStats {

    /**
     * Formats a focus duration like the owner's Regain reference:
     * "1m", "45m", "2h", "2h 15m".
     */
    fun formatDuration(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        if (s < 60) return "${s}s"
        val m = s / 60
        if (m < 60) return "${m}m"
        val h = m / 60
        val remM = m % 60
        return if (remM == 0L) "${h}h" else "${h}h ${remM}m"
    }

    /**
     * Global rank by weekly study seconds: 1 + (users strictly above).
     * Ties share the same rank (competition ranking).
     */
    fun computeRank(weeklySecondsByUser: Map<String, Long>, userId: String): Int {
        val mine = weeklySecondsByUser[userId] ?: 0L
        return 1 + weeklySecondsByUser.values.count { it > mine }
    }

    /**
     * "Top X%" from rank and the REAL total user count.
     * Returns null when the total is unknown — the UI hides the badge.
     */
    fun formatTopPercent(rank: Int, totalUsers: Int): String? {
        if (totalUsers <= 0 || rank <= 0) return null
        val pct = ceil(rank.toDouble() / totalUsers.toDouble() * 100.0).toInt().coerceIn(1, 100)
        return "Top $pct%"
    }

    /**
     * Average focus per session, formatted like [formatDuration].
     * Returns null when the session count is unknown (column absent) or zero —
     * the UI hides the tile instead of dividing by zero or inventing a number.
     */
    fun formatAvgFocus(weeklySeconds: Long, weeklySessions: Int?): String? {
        if (weeklySessions == null || weeklySessions <= 0) return null
        return formatDuration(weeklySeconds / weeklySessions)
    }
}
