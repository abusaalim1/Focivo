package com.example

import com.example.util.LocalTitleClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Parity tests for the on-device SCREEN-CONTENT classifier (Task 9, second
 * brain). Same inference engine as the title brain; different bundle
 * (classifier_bundle_content.json, v1, threshold 0.80).
 *
 * Expected p_entertainment values are the exact outputs of the Python
 * reference implementation (focivo-local-ai mirror of
 * inference_spec_content.md) at full double precision. Matching within
 * 1e-6 asserts inference parity end to end.
 */
class LocalContentClassifierTest {

    companion object {
        private lateinit var clf: LocalTitleClassifier.Classifier

        // (snippet, Python reference p_entertainment — content bundle v1)
        private val samples = listOf(
            "Quick revision \u2014 Newton's laws of motion: force equals mass times acceleration" to 0.004922657396,
            "Doubt: sir, how is inertia different from momentum?" to 0.019684424857,
            "CBSE class 11 physics syllabus: Newton's laws of motion is in chapter 5" to 0.005422675019,
            "POV: you finally finished the syllabus and it's 3am" to 0.884560850004,
            "MI beat CSK by 6 wickets in a last-over thriller" to 0.985167790029,
            "iPhone 17 at \u20B979,900 \u2014 link in bio, limited stock" to 0.987676351689,
            "gg wp, that 1v4 clutch was insane" to 0.966374320635,
            "Pull to refresh" to 0.925663459102,
            "me explaining quantum physics to my mom at 3am" to 0.959284781323,
            "the economics of video games explained" to 0.075392937087,
            "why horror movies are actually about grief \u2014 film studies lecture 4" to 0.532770954182,
            "success is not final, failure is not fatal \u2014 keep going" to 0.806659049794,
            "CBSE board exam datesheet released for 2026" to 0.200017518744,
            "spaced repetition beats rereading for long-term retention" to 0.522467458464,
            "teacher reacts to student memes" to 0.602713795741,
            "your reel got 10k views!" to 0.973302336118,
            "sir, ye topic samajh nahi aa raha, physics wala" to 0.265874192186,
            "exam tip: mole concept ko flashcards se revise karo" to 0.009491418436,
            "bhai ye reel dekho, full comedy hai" to 0.861922271397,
            "kal ka match kya tha yaar, last over thriller" to 0.974912006106,
            "mehnat ka phal meetha hota hai" to 0.882188205828,
            "math rizz party \u2014 comedy skit" to 0.729980018223,
            "\u0917\u0924\u093F \u0915\u0947 \u0928\u093F\u092F\u092E: \u092C\u0932 = \u0926\u094D\u0930\u0935\u094D\u092F\u092E\u093E\u0928 \u00D7 \u0924\u094D\u0935\u0930\u0923" to 0.132324872957,
            "\u0928\u092F\u093E \u0939\u093F\u0902\u0926\u0940 \u0917\u093E\u0928\u093E 2026 \u2014 \u0938\u0941\u0928\u094B" to 0.710815561599,
            "\u092A\u0930\u0940\u0915\u094D\u0937\u093E \u0938\u0942\u091A\u0928\u093E: \u0915\u0932 \u0917\u0923\u093F\u0924 \u0915\u0940 \u092A\u0930\u0940\u0915\u094D\u0937\u093E \u0939\u0948" to 0.200974024156,
            "discipline beats motivation. show up every day." to 0.898893912732,
            "studygram but make it aesthetic" to 0.486219879141,
            "session complete \u2014 25 minutes focused, 0 distractions" to 0.047024424032,
            "new phone launch: price and specs revealed" to 0.337913542075,
            "how minecraft teaches resource management" to 0.562933485777
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
    fun bundleVersionAndThresholdAreContentV1() {
        assertEquals(
            LocalTitleClassifier.EXPECTED_CONTENT_BUNDLE_VERSION,
            clf.version
        )
        assertEquals(0.80, clf.blockThreshold, 1e-12)
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
            val expectedBlock = pEnt >= 0.80
            val actualBlock = clf.decide(clf.pEntertainment(snippet)) ==
                LocalTitleClassifier.TitleDecision.ENTERTAINMENT
            assertEquals("block decision mismatch for '$snippet'", expectedBlock, actualBlock)
        }
    }

    @Test
    fun middleBandSnippetFallsThroughToGemini() {
        // 0.20 < 0.603 < 0.80 -> UNCERTAIN: Gemini arbitrates (or fail-open
        // allow with no key). Entertainment the local brain won't block alone.
        val pEnt = clf.pEntertainment("teacher reacts to student memes")
        assertEquals(0.602713795741, pEnt, 1e-6)
        assertEquals(
            LocalTitleClassifier.TitleDecision.UNCERTAIN,
            clf.decide(pEnt)
        )
    }

    @Test
    fun boundarySnippetJustAboveStrongStudy() {
        // 0.20002 > 0.20 -> NOT strong-study: goes to the middle band.
        val pEnt = clf.pEntertainment("CBSE board exam datesheet released for 2026")
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
