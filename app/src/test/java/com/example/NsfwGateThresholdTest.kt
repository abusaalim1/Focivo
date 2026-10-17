package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.util.NsfwScreenGate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.3.2 ITEM 6: the NSFW gate rejects ONLY high-confidence explicit content.
 * Uses the stubbed-score path (no model, no imagery) — the threshold contract
 * itself is what's under test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NsfwGateThresholdTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val blank: Bitmap
        get() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @After
    fun tearDown() {
        NsfwScreenGate.resetForTest()
    }

    @Test
    fun highConfidenceExplicit_isRejected() {
        NsfwScreenGate.setTestOverride(0.95f)
        assertEquals(NsfwScreenGate.Verdict.REJECT, NsfwScreenGate.screenImage(context, blank))
    }

    @Test
    fun atThreshold_isRejected() {
        NsfwScreenGate.setTestOverride(0.85f)
        assertEquals(NsfwScreenGate.Verdict.REJECT, NsfwScreenGate.screenImage(context, blank))
    }

    @Test
    fun borderline_isAllowed() {
        NsfwScreenGate.setTestOverride(0.84f)
        assertEquals(NsfwScreenGate.Verdict.ALLOW, NsfwScreenGate.screenImage(context, blank))
    }

    @Test
    fun clearlyNormal_isAllowed() {
        NsfwScreenGate.setTestOverride(0.05f)
        assertEquals(NsfwScreenGate.Verdict.ALLOW, NsfwScreenGate.screenImage(context, blank))
    }
}
