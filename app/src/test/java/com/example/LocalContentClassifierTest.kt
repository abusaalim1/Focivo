package com.example

import com.example.util.LocalTitleClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Parity tests for the on-device SCREEN-CONTENT classifier (Task 9, second
 * brain). Same inference engine as the title brain; different bundle
 * (classifier_bundle_content.json, v3, threshold 0.69).
 *
 * Expected p_entertainment values are the exact outputs of the Python
 * reference implementation (focivo-local-ai mirror of
 * inference_spec_content.md) at full double precision. Matching within
 * 1e-6 asserts inference parity end to end.
 */
class LocalContentClassifierTest {

    companion object {
        private lateinit var clf: LocalTitleClassifier.Classifier

        // (snippet, Python reference p_entertainment — content bundle v3)
        private val samples = listOf(
            "Quick revision \u2014 Newton's laws of motion: force equals mass times acceleration" to 0.000149679309,
            "Doubt: sir, how is inertia different from momentum?" to 0.008693605488,
            "CBSE class 11 physics syllabus: Newton's laws of motion is in chapter 5" to 0.000154331066,
            "POV: you finally finished the syllabus and it's 3am" to 0.827806604430,
            "MI beat CSK by 6 wickets in a last-over thriller" to 0.993792430682,
            "iPhone 17 at \u20B979,900 \u2014 link in bio, limited stock" to 0.998036026260,
            "gg wp, that 1v4 clutch was insane" to 0.989545735840,
            "Pull to refresh" to 0.904997289963,
            "me explaining quantum physics to my mom at 3am" to 0.934945719640,
            "the economics of video games explained" to 0.107901016782,
            "why horror movies are actually about grief \u2014 film studies lecture 4" to 0.579682646479,
            "success is not final, failure is not fatal \u2014 keep going" to 0.639123000075,
            "CBSE board exam datesheet released for 2026" to 0.014066328401,
            "spaced repetition beats rereading for long-term retention" to 0.147535868668,
            "teacher reacts to student memes" to 0.198790024087,
            "your reel got 10k views!" to 0.997064046552,
            "sir, ye topic samajh nahi aa raha, physics wala" to 0.023265699808,
            "exam tip: mole concept ko flashcards se revise karo" to 0.000135960488,
            "bhai ye reel dekho, full comedy hai" to 0.878805920008,
            "kal ka match kya tha yaar, last over thriller" to 0.993532539795,
            "mehnat ka phal meetha hota hai" to 0.761339092811,
            "math rizz party \u2014 comedy skit" to 0.530682691978,
            "\u0917\u0924\u093F \u0915\u0947 \u0928\u093F\u092F\u092E: \u092C\u0932 = \u0926\u094D\u0930\u0935\u094D\u092F\u092E\u093E\u0928 \u00D7 \u0924\u094D\u0935\u0930\u0923" to 0.064910562949,
            "\u0928\u092F\u093E \u0939\u093F\u0902\u0926\u0940 \u0917\u093E\u0928\u093E 2026 \u2014 \u0938\u0941\u0928\u094B" to 0.799380895192,
            "\u092A\u0930\u0940\u0915\u094D\u0937\u093E \u0938\u0942\u091A\u0928\u093E: \u0915\u0932 \u0917\u0923\u093F\u0924 \u0915\u0940 \u092A\u0930\u0940\u0915\u094D\u0937\u093E \u0939\u0948" to 0.066410239231,
            "discipline beats motivation. show up every day." to 0.941562100008,
            "studygram but make it aesthetic" to 0.865053738256,
            "session complete \u2014 25 minutes focused, 0 distractions" to 0.004038033136,
            "new phone launch: price and specs revealed" to 0.484557921380,
            "how minecraft teaches resource management" to 0.262985106579
        )

        @BeforeClass
        @JvmStatic
        fun loadBundle() {
            val url = LocalContentClassifierTest::class.java.getResource("/classifier_bundle_content.json")
                ?: error("test resource classifier_bundle_content.json not found")
            clf = LocalTitleClassifier.loadFromJson(url.readText())
        }
    }

    @Test
    fun bundleVersionAndThresholdAreContentV3() {
        assertEquals(
            LocalTitleClassifier.EXPECTED_CONTENT_BUNDLE_VERSION,
            clf.version
        )
        assertEquals(0.69, clf.blockThreshold, 1e-12)
    }

    @Test
    fun kotlinScoresMatchPythonReference() {
        for ((snippet, expected) in samples) {
            val actual = clf.pEntertainment(snippet)
            assertEquals(
                "p_entertainment mismatch for '$snippet' (Kotlin=$actual, Python=$expected)",
                expected, actual, 1e-6
            )
        }
    }

    @Test
    fun blockDecisionsMatchSpecThreshold() {
        for ((snippet, pEnt) in samples) {
            val expectedBlock = pEnt >= 0.69
            val actualBlock = clf.decide(clf.pEntertainment(snippet)) ==
                LocalTitleClassifier.TitleDecision.ENTERTAINMENT
            assertEquals("block decision mismatch for '$snippet'", expectedBlock, actualBlock)
        }
    }

    @Test
    fun middleBandSnippetFallsThroughToGemini() {
        // 0.20 < 0.580 < 0.69 -> UNCERTAIN: Gemini arbitrates (or fail-open
        // allow with no key). Entertainment the local brain won't block alone.
        // (v3 re-check: "teacher reacts to student memes" now scores 0.199
        // and lands STRONG_STUDY, so the middle-band example moved here.)
        val pEnt = clf.pEntertainment("why horror movies are actually about grief — film studies lecture 4")
        assertEquals(0.579682646479, pEnt, 1e-6)
        assertEquals(
            LocalTitleClassifier.TitleDecision.UNCERTAIN,
            clf.decide(pEnt)
        )
    }

    @Test
    fun boundarySnippetJustAboveStrongStudy() {
        // 0.236 > 0.20 -> NOT strong-study: goes to the middle band.
        // (v3 re-check: "study memes for final exams week" now scores 0.134
        // and lands STRONG_STUDY, so the boundary example moved here.)
        val pEnt = clf.pEntertainment("topper interview study routine vlog")
        assertEquals(0.236138297744, pEnt, 1e-6)
        assertTrue(
            "expected just above strong-study threshold",
            pEnt > LocalTitleClassifier.STRONG_STUDY_MAX_P_ENT
        )
        assertEquals(
            LocalTitleClassifier.TitleDecision.UNCERTAIN,
            clf.decide(pEnt)
        )
    }

    @Test
    fun strongStudySnippetAllowedLocally() {
        val pEnt = clf.pEntertainment("exam tip: mole concept ko flashcards se revise karo")
        assertTrue(
            "expected at or below strong-study threshold",
            pEnt <= LocalTitleClassifier.STRONG_STUDY_MAX_P_ENT
        )
        assertEquals(
            LocalTitleClassifier.TitleDecision.STRONG_STUDY,
            clf.decide(pEnt)
        )
    }

    @Test
    fun blankSnippetIsNeverJudged() {
        assertEquals(
            LocalTitleClassifier.TitleDecision.UNCERTAIN,
            clf.classify("   ")
        )
    }
}
