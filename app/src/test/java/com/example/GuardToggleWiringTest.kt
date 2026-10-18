package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.GuardExplainerContent
import com.example.ui.components.GuardExplainerKey
import com.example.ui.components.guardExplainerContent
import com.example.ui.theme.FocuslyTheme
import com.example.util.AiStudyGuardManager
import com.example.util.StrictModeManager
import com.example.util.YouTubeStudyGuardManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.3.3 (ITEM 5): toggle -> state wiring for the Study-screen guard control
 * cluster. The managers are Kotlin `object` singletons, so these tests drive
 * the exact same manager functions the cluster's icon taps call and assert
 * the exact same state sources the cluster collects — the wiring contract.
 * Explainer copy is covered for presence (data) and rendering (compose).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuardToggleWiringTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @get:Rule
    val composeTestRule = createComposeRule()

    // -- Strict Mode: StrictModeManager -------------------------------------

    @Test
    fun strictToggle_drivesStrictModeState() {
        try {
            StrictModeManager.setStrictModeEnabled(context, true)
            assertTrue(StrictModeManager.isStrictModeState.value)
            assertTrue(StrictModeManager.isStrictModeEnabled(context))

            StrictModeManager.setStrictModeEnabled(context, false)
            assertFalse(StrictModeManager.isStrictModeState.value)
            assertFalse(StrictModeManager.isStrictModeEnabled(context))
        } finally {
            // Restore the shipped default (on).
            StrictModeManager.setStrictModeEnabled(context, true)
        }
    }

    // -- AI Guard: AiStudyGuardManager --------------------------------------

    @Test
    fun aiGuardToggle_drivesGuardEnabled() {
        // No accessibility in the test sandbox: effective state stays off,
        // and disabling must persist cleanly.
        setAiStudyServiceConnectedForTest(false)
        AiStudyGuardManager.setAiGuardEnabled(context, false)
        assertFalse(AiStudyGuardManager.isAiGuardEnabled(context))

        // Simulate the granted permission: enabling must take effect.
        setAiStudyServiceConnectedForTest(true)
        try {
            AiStudyGuardManager.setAiGuardEnabled(context, true)
            assertTrue(AiStudyGuardManager.isAiGuardEnabled(context))

            AiStudyGuardManager.setAiGuardEnabled(context, false)
            assertFalse(AiStudyGuardManager.isAiGuardEnabled(context))
        } finally {
            setAiStudyServiceConnectedForTest(false)
            AiStudyGuardManager.setAiGuardEnabled(context, false)
        }
    }

    // -- YouTube Filter: YouTubeStudyGuardManager ---------------------------

    @Test
    fun ytFilterToggle_drivesFilterEnabledState() {
        setAiStudyServiceConnectedForTest(true)
        try {
            YouTubeStudyGuardManager.setFilterEnabled(context, true)
            assertTrue(YouTubeStudyGuardManager.isFilterEnabledState.value)

            YouTubeStudyGuardManager.setFilterEnabled(context, false)
            assertFalse(YouTubeStudyGuardManager.isFilterEnabledState.value)
        } finally {
            setAiStudyServiceConnectedForTest(false)
            YouTubeStudyGuardManager.setFilterEnabled(context, false)
        }
    }

    @Test
    fun ytFilter_withoutPermission_effectiveStateStaysOff() {
        // Documents the gate: the pref is the user's intent, but the cluster
        // shows OFF until the accessibility permission is granted.
        setAiStudyServiceConnectedForTest(false)
        try {
            YouTubeStudyGuardManager.setFilterEnabled(context, true)
            assertFalse(YouTubeStudyGuardManager.isFilterEnabledState.value)
        } finally {
            YouTubeStudyGuardManager.setFilterEnabled(context, false)
        }
    }

    // -- Explainer content: presence (data) ---------------------------------

    @Test
    fun explainerContent_presentForAllToggles() {
        GuardExplainerKey.values().forEach { key ->
            val content = guardExplainerContent(key)
            assertTrue("$key eyebrow missing", content.eyebrow.isNotBlank())
            assertTrue("$key headline missing", content.headline.isNotBlank())
            assertTrue("$key body missing", content.body.isNotBlank())
            assertTrue("$key needs at least 3 points", content.points.size >= 3)
            content.points.forEach { point ->
                assertTrue("$key has a blank point", point.isNotBlank())
            }
            assertTrue("$key footnote missing", content.footnote.isNotBlank())
        }
    }

    @Test
    fun explainerContent_englishOnly() {
        // Printable ASCII plus common typographic punctuation — no emojis,
        // no dingbats, per the standing English-only / zero-emoji rule.
        val allowed = Regex(
            "^[\\u0020-\\u007E\\u00A0\\u2013\\u2014\\u2018\\u2019\\u201C\\u201D\\u2026\\u00B7]*$"
        )
        GuardExplainerKey.values().forEach { key ->
            val content = guardExplainerContent(key)
            val all = listOf(
                content.eyebrow, content.headline, content.body, content.footnote
            ) + content.points
            all.forEach { text ->
                assertTrue(
                    "$key contains non-English characters: \"$text\"",
                    allowed.matches(text)
                )
            }
        }
    }

    // -- Explainer content: presence (rendered) ------------------------------

    @Test
    fun explainerSheet_rendersAllContent_dark() {
        renderExplainer(GuardExplainerKey.STRICT, dark = true)
    }

    @Test
    fun explainerSheet_rendersAllContent_light() {
        renderExplainer(GuardExplainerKey.AI_GUARD, dark = false)
    }

    @Test
    fun explainerSheet_rendersAllContent_ytFilter() {
        renderExplainer(GuardExplainerKey.YT_FILTER, dark = true)
    }

    private fun renderExplainer(key: GuardExplainerKey, dark: Boolean) {
        val content = guardExplainerContent(key)
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                GuardExplainerContent(content = content, onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText(content.eyebrow).assertExists()
        composeTestRule.onNodeWithText(content.headline).assertExists()
        composeTestRule.onNodeWithText(content.body).assertExists()
        content.points.forEach { point ->
            composeTestRule.onNodeWithText(point).assertExists()
        }
        composeTestRule.onNodeWithText(content.footnote).assertExists()
        composeTestRule.onNodeWithText("Got it").assertExists()
    }
}
