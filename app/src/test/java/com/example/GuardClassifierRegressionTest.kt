package com.example

import com.example.service.BlockOverlayManager
import com.example.util.StudyGuardKnowledge
import com.example.util.YouTubeVideoClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.2.3 — guard classifier regression tests.
 *
 * Locks in the two guard directions the owner cares about:
 *  1. Real study content (esp. doubt-solving / "Game Plan" vocabulary) is
 *     NEVER confidently blocked by the on-device classifier.
 *  2. Obvious entertainment is still confidently caught.
 *  3. Ambiguous titles are UNCERTAIN / low-confidence — they must fall
 *     through to Gemini and, if Gemini is unavailable, FAIL OPEN (allow),
 *     never block by themselves.
 *  4. Overlay path priority: a11y service > app overlay > activity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuardClassifierRegressionTest {

    // ------------------------------------------------------------------
    // YouTubeVideoClassifier
    // ------------------------------------------------------------------

    @Test
    fun `study doubt title is allowed`() {
        val v = YouTubeVideoClassifier.evaluate(
            "Doubt Session: Electrostatics Numericals | Class 12 Physics",
            "Physics Wallah"
        )
        assertTrue("doubt title blocked: ${v.reason}", v.isEducational)
    }

    @Test
    fun `most expected game plan title is allowed (Case B regression)`() {
        // "Game Plan" is study vocabulary. The old Case B substring match on
        // "game" hard-blocked this at 0.95 — the exact false-positive class
        // from the owner's report.
        val v = YouTubeVideoClassifier.evaluate(
            "Most Expected Questions Game Plan | Rotational Motion Class 11",
            "Competishun"
        )
        assertTrue("game-plan title blocked: ${v.reason}", v.isEducational)
    }

    @Test
    fun `jee game plan title is allowed`() {
        val v = YouTubeVideoClassifier.evaluate(
            "JEE 2026 Game Plan for Physics | Strategy Video",
            "Mohit Tyagi"
        )
        assertTrue("jee game-plan blocked: ${v.reason}", v.isEducational)
    }

    @Test
    fun `entertainment song is confidently blocked`() {
        val v = YouTubeVideoClassifier.evaluate(
            "Arijit Singh Latest Song | Official Music Video",
            "T-Series"
        )
        assertFalse("song allowed?!", v.isEducational)
        assertTrue("song block not confident: ${v.confidence}", v.confidence >= 0.90f)
    }

    @Test
    fun `funny vlog is confidently blocked`() {
        val v = YouTubeVideoClassifier.evaluate(
            "Funny College Vlog | Day In My Life",
            "Some Vlogger"
        )
        assertFalse("vlog allowed?!", v.isEducational)
        assertTrue("vlog block not confident: ${v.confidence}", v.confidence >= 0.90f)
    }

    @Test
    fun `gta gameplay is confidently blocked`() {
        val v = YouTubeVideoClassifier.evaluate(
            "GTA 5 Gameplay Hindi | Free Roam Fun",
            "Techno Gamerz"
        )
        assertFalse("gameplay allowed?!", v.isEducational)
        assertTrue("gameplay block not confident: ${v.confidence}", v.confidence >= 0.90f)
    }

    @Test
    fun `ambiguous title is never a confident block`() {
        // "The Game of Life" contains the whole word "game" but has zero
        // academic signal and zero entertainment signal. It must come back
        // UNCERTAIN / low-confidence so the caller falls through to Gemini
        // and fail-opens on unavailability — never a 0.90+ block.
        val v = YouTubeVideoClassifier.evaluate(
            "The Game of Life - Best Motivation",
            "Motivation Channel"
        )
        assertTrue(
            "ambiguous title confidently blocked (${v.confidence}): ${v.reason}",
            v.confidence < 0.90f
        )
    }

    // ------------------------------------------------------------------
    // StudyGuardKnowledge (the first layer in the YouTube/chat path)
    // ------------------------------------------------------------------

    @Test
    fun `knowledge - doubt solving is study`() {
        assertEquals(
            StudyGuardKnowledge.Verdict.STUDY,
            StudyGuardKnowledge.scoreVideo("Doubt: Thermodynamics numericals", "Some Channel")
        )
    }

    @Test
    fun `knowledge - game plan with expected questions is study`() {
        assertEquals(
            StudyGuardKnowledge.Verdict.STUDY,
            StudyGuardKnowledge.scoreVideo("Most Expected Questions Game Plan", null)
        )
    }

    @Test
    fun `knowledge - bgmi gameplay is non study`() {
        assertEquals(
            StudyGuardKnowledge.Verdict.NON_STUDY,
            StudyGuardKnowledge.scoreVideo("bgmi gameplay live custom room", null)
        )
    }

    @Test
    fun `knowledge - ambiguous text is uncertain not blocked`() {
        assertEquals(
            StudyGuardKnowledge.Verdict.UNCERTAIN,
            StudyGuardKnowledge.scoreVideo("The Game of Life motivation", null)
        )
    }

    @Test
    fun `knowledge - study chat message is study`() {
        assertEquals(
            StudyGuardKnowledge.Verdict.STUDY,
            StudyGuardKnowledge.scoreChatMessage(
                "doubt hai electrostatics ke numericals me physics",
                "com.openai.chatgpt"
            )
        )
    }

    // ------------------------------------------------------------------
    // Overlay path priority (BlockOverlayManager.resolveOverlayPath)
    // ------------------------------------------------------------------

    @Test
    fun `overlay path - a11y service wins`() {
        assertEquals(
            BlockOverlayManager.OverlayPath.A11Y,
            BlockOverlayManager.resolveOverlayPath(
                a11yServiceConnected = true,
                canDrawOverlays = true
            )
        )
        assertEquals(
            BlockOverlayManager.OverlayPath.A11Y,
            BlockOverlayManager.resolveOverlayPath(
                a11yServiceConnected = true,
                canDrawOverlays = false
            )
        )
    }

    @Test
    fun `overlay path - app overlay when no a11y service`() {
        assertEquals(
            BlockOverlayManager.OverlayPath.APP,
            BlockOverlayManager.resolveOverlayPath(
                a11yServiceConnected = false,
                canDrawOverlays = true
            )
        )
    }

    @Test
    fun `overlay path - activity fallback when nothing available`() {
        assertEquals(
            BlockOverlayManager.OverlayPath.ACTIVITY,
            BlockOverlayManager.resolveOverlayPath(
                a11yServiceConnected = false,
                canDrawOverlays = false
            )
        )
    }
}
