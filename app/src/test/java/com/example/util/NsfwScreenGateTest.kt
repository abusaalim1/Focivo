package com.example.util

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [NsfwScreenGate].
 *
 * The gating logic is exercised with stubbed inference scores via the
 * test-only [NsfwScreenGate.setTestOverride] — no model file and no imagery
 * of any kind are needed. No explicit imagery exists anywhere in this repo,
 * ever; the calibration against real normal images lives outside the repo
 * (see the 1.3.2 round report).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NsfwScreenGateTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun tinyBitmap(): Bitmap =
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @After
    fun tearDown() {
        NsfwScreenGate.resetForTest()
    }

    @Test
    fun `threshold constant is 0_85`() {
        assertEquals(0.85f, NsfwScreenGate.REJECT_THRESHOLD)
    }

    @Test
    fun `stubbed explicit score 0_95 rejects`() {
        NsfwScreenGate.setTestOverride(0.95f)
        assertEquals(
            NsfwScreenGate.Verdict.REJECT,
            NsfwScreenGate.screenImage(context, tinyBitmap())
        )
    }

    @Test
    fun `stubbed explicit score 0_84 allows`() {
        NsfwScreenGate.setTestOverride(0.84f)
        assertEquals(
            NsfwScreenGate.Verdict.ALLOW,
            NsfwScreenGate.screenImage(context, tinyBitmap())
        )
    }

    @Test
    fun `stubbed explicit score 0_5 allows`() {
        NsfwScreenGate.setTestOverride(0.5f)
        assertEquals(
            NsfwScreenGate.Verdict.ALLOW,
            NsfwScreenGate.screenImage(context, tinyBitmap())
        )
    }

    @Test
    fun `model load failure fails open`() {
        // No override: under Robolectric the bundled model asset is absent and the
        // TFLite native runtime is unavailable, so the load must fail and the gate
        // must fail open to ALLOW instead of trapping the user.
        NsfwScreenGate.resetForTest()
        assertEquals(
            NsfwScreenGate.Verdict.ALLOW,
            NsfwScreenGate.screenImage(context, tinyBitmap())
        )
    }
}
