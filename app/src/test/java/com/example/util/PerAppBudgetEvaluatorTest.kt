package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.3.3 — pure per-app budget evaluation.
 */
class PerAppBudgetEvaluatorTest {

    private val now = 1_790_000_000_000L

    @Test
    fun `usage at the limit is exceeded`() {
        assertTrue(PerAppBudgetEvaluator.isExceeded(60, 60, 0L, now))
    }

    @Test
    fun `usage above the limit is exceeded`() {
        assertTrue(PerAppBudgetEvaluator.isExceeded(61, 60, 0L, now))
    }

    @Test
    fun `usage below the limit is not exceeded`() {
        assertFalse(PerAppBudgetEvaluator.isExceeded(59, 60, 0L, now))
    }

    @Test
    fun `zero or negative budget never exceeded`() {
        assertFalse(PerAppBudgetEvaluator.isExceeded(10_000, 0, 0L, now))
        assertFalse(PerAppBudgetEvaluator.isExceeded(10_000, -5, 0L, now))
    }

    @Test
    fun `active emergency bypass suppresses exceeded`() {
        assertFalse(PerAppBudgetEvaluator.isExceeded(10_000, 60, now + 60_000L, now))
    }

    @Test
    fun `expired emergency bypass does not suppress`() {
        assertTrue(PerAppBudgetEvaluator.isExceeded(10_000, 60, now - 1L, now))
    }
}
