package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.ScreenTimeBudgetStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.3.3 (ITEM 5): ScreenTimeBudgetStore contract — defaults, round-trips,
 * coercion, and StateFlow mirroring. The Study-screen guard cluster's
 * "Limits" entry and ScreenTimeBudgetsScreen both drive this store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenTimeBudgetStoreTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun reset() {
        ScreenTimeBudgetStore.setGlobalLimitEnabled(context, false)
        ScreenTimeBudgetStore.setGlobalLimitMinutes(context, ScreenTimeBudgetStore.NO_LIMIT_MINUTES)
        ScreenTimeBudgetStore.init(context)
    }

    @Test
    fun defaults_disabledAndUnset() {
        reset()
        assertFalse(ScreenTimeBudgetStore.isGlobalLimitEnabled(context))
        assertEquals(
            ScreenTimeBudgetStore.NO_LIMIT_MINUTES,
            ScreenTimeBudgetStore.getGlobalLimitMinutes(context)
        )
        assertFalse(ScreenTimeBudgetStore.globalLimitEnabledState.value)
        assertEquals(
            ScreenTimeBudgetStore.NO_LIMIT_MINUTES,
            ScreenTimeBudgetStore.globalLimitMinutesState.value
        )
    }

    @Test
    fun setEnabled_roundTripsToPrefsAndFlow() {
        try {
            ScreenTimeBudgetStore.setGlobalLimitEnabled(context, true)
            assertTrue(ScreenTimeBudgetStore.isGlobalLimitEnabled(context))
            assertTrue(ScreenTimeBudgetStore.globalLimitEnabledState.value)

            ScreenTimeBudgetStore.setGlobalLimitEnabled(context, false)
            assertFalse(ScreenTimeBudgetStore.isGlobalLimitEnabled(context))
            assertFalse(ScreenTimeBudgetStore.globalLimitEnabledState.value)
        } finally {
            reset()
        }
    }

    @Test
    fun setMinutes_roundTripsToPrefsAndFlow() {
        try {
            ScreenTimeBudgetStore.setGlobalLimitMinutes(context, 180)
            assertEquals(180, ScreenTimeBudgetStore.getGlobalLimitMinutes(context))
            assertEquals(180, ScreenTimeBudgetStore.globalLimitMinutesState.value)
        } finally {
            reset()
        }
    }

    @Test
    fun setMinutes_coercesOutOfRange() {
        try {
            ScreenTimeBudgetStore.setGlobalLimitMinutes(context, -30)
            assertEquals(
                ScreenTimeBudgetStore.NO_LIMIT_MINUTES,
                ScreenTimeBudgetStore.getGlobalLimitMinutes(context)
            )
            assertEquals(
                ScreenTimeBudgetStore.NO_LIMIT_MINUTES,
                ScreenTimeBudgetStore.globalLimitMinutesState.value
            )

            ScreenTimeBudgetStore.setGlobalLimitMinutes(context, 10_000)
            assertEquals(
                ScreenTimeBudgetStore.MAX_LIMIT_MINUTES,
                ScreenTimeBudgetStore.getGlobalLimitMinutes(context)
            )
            assertEquals(
                ScreenTimeBudgetStore.MAX_LIMIT_MINUTES,
                ScreenTimeBudgetStore.globalLimitMinutesState.value
            )
        } finally {
            reset()
        }
    }

    @Test
    fun init_reloadsPersistedValuesIntoFlows() {
        try {
            ScreenTimeBudgetStore.setGlobalLimitEnabled(context, true)
            ScreenTimeBudgetStore.setGlobalLimitMinutes(context, 240)
            // Simulate a cold start: re-init must restore the persisted state.
            ScreenTimeBudgetStore.init(context)
            assertTrue(ScreenTimeBudgetStore.globalLimitEnabledState.value)
            assertEquals(240, ScreenTimeBudgetStore.globalLimitMinutesState.value)
        } finally {
            reset()
        }
    }

    @Test
    fun routeId_isStableContract() {
        assertEquals("screen_time_budgets", ScreenTimeBudgetStore.SCREEN_TIME_BUDGETS_ROUTE_ID)
    }
}
