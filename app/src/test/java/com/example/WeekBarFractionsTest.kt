package com.example

import com.example.ui.screens.weekBarFractions
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.3.0 — the "Total focus today" card's week graph must encode REAL
 * per-day study minutes: bar height strictly proportional to that day's
 * actual minutes, with a zero day rendering a minimal stub (the owner's
 * HTML floor: Math.max(8, v/mx*100)).
 */
class WeekBarFractionsTest {

    @Test fun bars_are_strictly_proportional_to_real_minutes() {
        val week = listOf(0, 10, 20, 30, 40, 50, 60) // oldest..today
        val f = weekBarFractions(week)
        assertEquals(7, f.size)
        // Non-zero days: exact proportionality v / max.
        assertEquals(10f / 60f, f[1], 1e-6f)
        assertEquals(20f / 60f, f[2], 1e-6f)
        assertEquals(30f / 60f, f[3], 1e-6f)
        assertEquals(40f / 60f, f[4], 1e-6f)
        assertEquals(50f / 60f, f[5], 1e-6f)
        assertEquals(1.0f, f[6], 1e-6f) // max day fills the bar
    }

    @Test fun zero_day_gets_minimal_stub_bar() {
        val f = weekBarFractions(listOf(0, 10, 20, 30, 40, 50, 60))
        assertEquals(0.08f, f[0], 1e-6f) // minimal, not zero-height, per the HTML
    }

    @Test fun all_zero_week_renders_stubs() {
        val f = weekBarFractions(List(7) { 0 })
        assertEquals(7, f.size)
        f.forEach { assertEquals(0.08f, it, 1e-6f) }
    }

    @Test fun bar_ratios_match_minute_ratios() {
        val f = weekBarFractions(listOf(0, 15, 45, 0, 0, 0, 0))
        assertEquals(f[1] / f[2], 15f / 45f, 1e-5f)
    }

    @Test fun max_day_need_not_be_today() {
        val f = weekBarFractions(listOf(0, 0, 90, 0, 45, 15, 0))
        assertEquals(1.0f, f[2], 1e-6f)   // 90 min -> full bar
        assertEquals(0.5f, f[4], 1e-6f)   // 45 min -> half
        assertEquals(15f / 90f, f[5], 1e-6f)
        assertEquals(0.08f, f[0], 1e-6f)  // zero days -> stub
        assertEquals(0.08f, f[6], 1e-6f)
    }

    @Test fun short_list_is_padded_with_zero_days() {
        val f = weekBarFractions(listOf(120)) // only today has data
        assertEquals(7, f.size)
        assertEquals(1.0f, f[6], 1e-6f)
        for (i in 0 until 6) assertEquals(0.08f, f[i], 1e-6f)
    }
}
