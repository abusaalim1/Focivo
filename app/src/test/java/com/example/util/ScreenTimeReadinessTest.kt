package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.1.3 (FIX 2) — ScreenTimeReadiness: the pure readiness decision logic
 * behind the Block hub's "Screen time" card.
 *
 * Hermetic (no Context, no Android APIs) — plain JUnit, no Robolectric.
 */
class ScreenTimeReadinessTest {

    // --- isReady -----------------------------------------------------------

    @Test
    fun `ready requires both usage access and battery exemption`() {
        assertTrue(
            ScreenTimeReadiness.isReady(
                ScreenTimeReadiness.ReadinessInput(
                    usageAccessGranted = true,
                    batteryExemptionGranted = true,
                    lastComputedAtMs = 1L
                )
            )
        )
        assertFalse(
            ScreenTimeReadiness.isReady(
                ScreenTimeReadiness.ReadinessInput(
                    usageAccessGranted = false,
                    batteryExemptionGranted = true,
                    lastComputedAtMs = 1L
                )
            )
        )
        assertFalse(
            ScreenTimeReadiness.isReady(
                ScreenTimeReadiness.ReadinessInput(
                    usageAccessGranted = true,
                    batteryExemptionGranted = false,
                    lastComputedAtMs = 1L
                )
            )
        )
        assertFalse(
            ScreenTimeReadiness.isReady(
                ScreenTimeReadiness.ReadinessInput(
                    usageAccessGranted = false,
                    batteryExemptionGranted = false,
                    lastComputedAtMs = null
                )
            )
        )
    }

    // --- isStale -----------------------------------------------------------

    @Test
    fun `never computed is stale`() {
        assertTrue(ScreenTimeReadiness.isStale(null, nowMs = 1_000_000L))
    }

    @Test
    fun `fresh snapshot is not stale`() {
        val now = 1_000_000L
        assertFalse(ScreenTimeReadiness.isStale(now - 60_000L, now))
    }

    @Test
    fun `snapshot older than threshold is stale`() {
        val now = 1_000_000L
        assertTrue(
            ScreenTimeReadiness.isStale(
                now - ScreenTimeReadiness.STALE_AFTER_MS - 1L,
                now
            )
        )
    }

    // --- formatLastUpdated -------------------------------------------------

    @Test
    fun `null formats as Never`() {
        assertEquals("Never", ScreenTimeReadiness.formatLastUpdated(null, 1_000_000L))
    }

    @Test
    fun `sub-minute formats as Just now`() {
        val now = 1_000_000L
        assertEquals("Just now", ScreenTimeReadiness.formatLastUpdated(now, now))
        assertEquals("Just now", ScreenTimeReadiness.formatLastUpdated(now - 30_000L, now))
    }

    @Test
    fun `minutes format as N min ago`() {
        val now = 1_000_000L
        assertEquals("3 min ago", ScreenTimeReadiness.formatLastUpdated(now - 3 * 60_000L, now))
        assertEquals("59 min ago", ScreenTimeReadiness.formatLastUpdated(now - 59 * 60_000L, now))
    }

    @Test
    fun `hours format as N h ago`() {
        val now = 100_000_000L
        assertEquals("2 h ago", ScreenTimeReadiness.formatLastUpdated(now - 2 * 3_600_000L, now))
    }

    @Test
    fun `days format as N d ago`() {
        val now = 1_000_000_000L
        assertEquals("2 d ago", ScreenTimeReadiness.formatLastUpdated(now - 2 * 86_400_000L, now))
    }

    @Test
    fun `future timestamp from clock skew formats as Just now`() {
        val now = 1_000_000L
        assertEquals("Just now", ScreenTimeReadiness.formatLastUpdated(now + 60_000L, now))
    }
}
