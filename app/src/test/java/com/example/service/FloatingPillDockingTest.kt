package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.3.1 — pure JVM tests for the floating pill's drag + auto-dock
 * decisions (no Robolectric needed; FloatingPillDocking is Android-free).
 */
class FloatingPillDockingTest {

    private val dockEdgeLeft = FloatingPillDocking.DockEdge.LEFT
    private val dockEdgeRight = FloatingPillDocking.DockEdge.RIGHT

    // ── nearestEdge ────────────────────────────────────────────────────

    @Test
    fun nearestEdge_centerLeftOfMiddle_docksLeft() {
        // x=100, width=300 -> center 250 <= 540
        assertEquals(dockEdgeLeft, FloatingPillDocking.nearestEdge(100, 300, 1080))
    }

    @Test
    fun nearestEdge_centerRightOfMiddle_docksRight() {
        // x=700, width=300 -> center 850 > 540
        assertEquals(dockEdgeRight, FloatingPillDocking.nearestEdge(700, 300, 1080))
    }

    @Test
    fun nearestEdge_exactTie_goesLeft() {
        // x=0, width=1080 -> center 540 == 540
        assertEquals(dockEdgeLeft, FloatingPillDocking.nearestEdge(0, 1080, 1080))
    }

    @Test
    fun nearestEdge_justPastMiddle_docksRight() {
        // x=1, width=1080 -> center 541 > 540
        assertEquals(dockEdgeRight, FloatingPillDocking.nearestEdge(1, 1080, 1080))
    }

    // ── dockedXPx ──────────────────────────────────────────────────────

    @Test
    fun dockedX_left_isMargin() {
        assertEquals(30, FloatingPillDocking.dockedXPx(dockEdgeLeft, 1080, 192, 30))
    }

    @Test
    fun dockedX_right_isFlushWithMargin() {
        assertEquals(858, FloatingPillDocking.dockedXPx(dockEdgeRight, 1080, 192, 30))
    }

    @Test
    fun dockedX_right_clampedAtZeroWhenScreenTooNarrow() {
        assertEquals(0, FloatingPillDocking.dockedXPx(dockEdgeRight, 100, 192, 30))
    }

    // ── clampExpandedXPx ───────────────────────────────────────────────

    @Test
    fun clamp_insideBounds_unchanged() {
        assertEquals(400, FloatingPillDocking.clampExpandedXPx(400, 300, 1080))
    }

    @Test
    fun clamp_negative_clampedToZero() {
        assertEquals(0, FloatingPillDocking.clampExpandedXPx(-50, 300, 1080))
    }

    @Test
    fun clamp_beyondRightEdge_clampedToMax() {
        // max = 1080 - 300 = 780
        assertEquals(780, FloatingPillDocking.clampExpandedXPx(900, 300, 1080))
    }

    @Test
    fun clamp_viewWiderThanScreen_clampedToZero() {
        assertEquals(0, FloatingPillDocking.clampExpandedXPx(50, 1200, 1080))
    }

    // ── formatDockedCountdown ──────────────────────────────────────────

    @Test
    fun format_secondsOnly_padded() {
        assertEquals("00:59", FloatingPillDocking.formatDockedCountdown(59))
    }

    @Test
    fun format_minutesAndSeconds() {
        assertEquals("12:34", FloatingPillDocking.formatDockedCountdown(12 * 60 + 34))
    }

    @Test
    fun format_zero() {
        assertEquals("00:00", FloatingPillDocking.formatDockedCountdown(0))
    }

    @Test
    fun format_negative_clampedToZero() {
        assertEquals("00:00", FloatingPillDocking.formatDockedCountdown(-7))
    }

    @Test
    fun format_hoursFoldIntoMinutes() {
        // 1h 2m 3s -> 62:03
        assertEquals("62:03", FloatingPillDocking.formatDockedCountdown(3600 + 123))
    }
}
