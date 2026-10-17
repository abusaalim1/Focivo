package com.example

import com.example.data.squads.MemberProfileStats
import org.junit.Assert.*
import org.junit.Test

/**
 * v1.3.2 item 7: unit tests for the member-profile computations, on synthetic
 * leaderboard data. All values are derived — never invented.
 */
class MemberProfileStatsTest {

    // ── formatDuration ──────────────────────────────────────────────

    @Test fun `formats sub-minute as seconds`() {
        assertEquals("45s", MemberProfileStats.formatDuration(45))
    }

    @Test fun `formats minutes like the reference`() {
        assertEquals("1m", MemberProfileStats.formatDuration(90))
        assertEquals("45m", MemberProfileStats.formatDuration(2700))
    }

    @Test fun `formats hours and hours-plus-minutes`() {
        assertEquals("2h", MemberProfileStats.formatDuration(7200))
        assertEquals("2h 15m", MemberProfileStats.formatDuration(8100))
    }

    @Test fun `zero formats as 0s`() {
        assertEquals("0s", MemberProfileStats.formatDuration(0))
    }

    // ── computeRank ─────────────────────────────────────────────────

    private val synthetic = mapOf(
        "u1" to 7200L,   // top
        "u2" to 3600L,
        "u3" to 3600L,   // tie with u2
        "u4" to 60L,
        "u5" to 0L,
    )

    @Test fun `rank is one plus users strictly above`() {
        assertEquals(1, MemberProfileStats.computeRank(synthetic, "u1"))
        assertEquals(4, MemberProfileStats.computeRank(synthetic, "u4"))
        assertEquals(5, MemberProfileStats.computeRank(synthetic, "u5"))
    }

    @Test fun `ties share the same rank`() {
        assertEquals(2, MemberProfileStats.computeRank(synthetic, "u2"))
        assertEquals(2, MemberProfileStats.computeRank(synthetic, "u3"))
    }

    @Test fun `unknown user ranks below everyone with zero`() {
        assertEquals(5, MemberProfileStats.computeRank(synthetic, "ghost"))
    }

    // ── formatTopPercent ────────────────────────────────────────────

    @Test fun `top percent uses ceil on the real total`() {
        assertEquals("Top 20%", MemberProfileStats.formatTopPercent(1, 5))
        assertEquals("Top 40%", MemberProfileStats.formatTopPercent(2, 5))
        // 3/10 = 30% exactly; 4/10 -> ceil(40%) = 40%
        assertEquals("Top 34%", MemberProfileStats.formatTopPercent(10, 30)) // 33.33 -> 34
    }

    @Test fun `top percent clamps to 1..100`() {
        assertEquals("Top 100%", MemberProfileStats.formatTopPercent(5, 5))
        assertEquals("Top 1%", MemberProfileStats.formatTopPercent(1, 200))
    }

    @Test fun `top percent hides on unknown total`() {
        assertNull(MemberProfileStats.formatTopPercent(1, 0))
        assertNull(MemberProfileStats.formatTopPercent(0, 5))
    }

    // ── formatAvgFocus ──────────────────────────────────────────────

    @Test fun `average is weekly seconds over sessions`() {
        assertEquals("30m", MemberProfileStats.formatAvgFocus(3600, 2))
        assertEquals("1h", MemberProfileStats.formatAvgFocus(7200, 2))
    }

    @Test fun `average hides when sessions unknown or zero`() {
        assertNull(MemberProfileStats.formatAvgFocus(3600, null))
        assertNull(MemberProfileStats.formatAvgFocus(3600, 0))
        assertNull(MemberProfileStats.formatAvgFocus(0, 0))
    }
}
