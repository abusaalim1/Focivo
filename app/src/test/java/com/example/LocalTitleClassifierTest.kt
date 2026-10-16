package com.example

import com.example.util.LocalTitleClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Parity tests for the on-device title classifier (Task 6).
 *
 * The expected p_entertainment values are the exact outputs of the Python
 * reference implementation (focivo-local-ai mirror of inference_spec.md),
 * printed at full double precision. Any tokenization or scoring divergence
 * between Kotlin and Python moves these scores, so matching within 1e-6
 * asserts inference parity end to end.
 */
class LocalTitleClassifierTest {

    companion object {
        private lateinit var clf: LocalTitleClassifier.Classifier

        // (title, Python reference p_entertainment — v3 bundle)
        private val samples = listOf(
            "Motion Chapter 1 One Shot | Class 11 Physics" to 0.000078431874,
            "ye topic samajh nahi aa raha sir physics wala" to 0.015513805025,
            "गति अध्याय कक्षा 9 एक शॉट" to 0.074498105069,
            "learn english the fun way | tenses game" to 0.076979244945,
            "study with me 10 hours live" to 0.339534349359,
            "physics fails compilation | funny experiments" to 0.823097838036,
            "exam memes | students relatable" to 0.766240901900,
            "tum hi ho reprise | official music video" to 0.997664635578,
            "bgmi live | rank push" to 0.994096192018,
            "pushpa 2 trailer | official" to 0.999056351015,
            "day in my life vlog" to 0.992798260072,
            "funny comedy video 2026" to 0.999543096308,
            "meme se yaad karo periodic table" to 0.087625081975,
            "teacher vs students funny video" to 0.993900015407,
            "ncert solutions class 10 maths chapter 2" to 0.000128166744,
            "cricket highlights | ind vs pak" to 0.992520613992,
            "how i scored 99 percent in class 10" to 0.008084438676,
            "stand up comedy | zakir khan" to 0.985191577035,
            "doubt: why is current scalar not vector" to 0.027388236996,
            "doraemon new episode hindi" to 0.996388617441,
            // Known ambiguous case: lands ENTERTAINMENT at the v3 threshold
            // (the retrain trades a few ambiguous study-adjacent titles for
            // higher entertainment recall). The Guard Hub "youtube-local"
            // entry with p_ent=0.857 makes it diagnosable on device.
            "10 hour study challenge | no talking" to 0.941472098767
        )

        @BeforeClass
        @JvmStatic
        fun loadBundle() {
            val url = LocalTitleClassifierTest::class.java.getResource("/classifier_bundle.json")
                ?: error("test resource classifier_bundle.json not found")
            clf = LocalTitleClassifier.loadFromJson(url.readText())
        }
    }

    @Test
    fun kotlinScoresMatchPythonReference() {
        for ((title, expected) in samples) {
            val actual = clf.pEntertainment(title)
            assertEquals(
                "p_entertainment mismatch for '$title' (Kotlin=$actual, Python=$expected)",
                expected, actual, 1e-6
            )
        }
    }

    @Test
    fun bundleVersionAndThresholdAreV3() {
        assertEquals(
            LocalTitleClassifier.EXPECTED_TITLE_BUNDLE_VERSION,
            clf.version
        )
        assertEquals(0.65, clf.blockThreshold, 1e-12)
    }

    @Test
    fun blockDecisionsMatchSpecThreshold() {
        for ((title, pEnt) in samples) {
            val expectedBlock = pEnt >= 0.65
            val actualBlock = clf.decide(clf.pEntertainment(title)) ==
                LocalTitleClassifier.TitleDecision.ENTERTAINMENT
            assertEquals("block decision mismatch for '$title'", expectedBlock, actualBlock)
        }
    }

    @Test
    fun uncertainMiddleBandTitleFallsThrough() {
        // 0.20 < 0.450 < 0.65 -> UNCERTAIN: falls through to the existing
        // knowledge -> quick -> Gemini chain (Gemini arbitrates when a key
        // exists; no key -> existing fail-open rules). This is entertainment
        // the local judge deliberately does NOT block on its own.
        // (v3 re-check: "the periodic table of memes" now scores 0.700 and
        // lands ENTERTAINMENT, so the middle-band example moved here.)
        val pEnt = clf.pEntertainment("history of the roman empire podcast")
        assertEquals(0.450253634725, pEnt, 1e-6)
        assertEquals(
            LocalTitleClassifier.TitleDecision.UNCERTAIN,
            clf.decide(pEnt)
        )
    }

    @Test
    fun knownAmbiguousTitleIsDocumented() {
        // Genuinely ambiguous even for a human ("challenge" reads as
        // entertainment). Lands ENTERTAINMENT at the v3 threshold (p_ent=
        // 0.941); the Guard Hub "youtube-local" entry makes it diagnosable.
        val pEnt = clf.pEntertainment("10 hour study challenge | no talking")
        assertEquals(
            LocalTitleClassifier.TitleDecision.ENTERTAINMENT,
            clf.decide(pEnt)
        )
    }

    @Test
    fun strongStudyTitleAllowedLocally() {
        val pEnt = clf.pEntertainment("ncert solutions class 10 maths chapter 2")
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
    fun blankTitleIsNeverJudged() {
        assertEquals(
            LocalTitleClassifier.TitleDecision.UNCERTAIN,
            clf.classify("   ")
        )
    }

    @Test
    fun wordTokenizationMatchesPython() {
        // Python re (?u)\b\w\w+\b on "Hi mom!" -> ['hi', 'mom', 'hi mom']
        assertEquals(
            listOf("hi", "mom", "hi mom"),
            clf.debugWordTokens("Hi mom!")
        )
        // Single letters are dropped; no tokens at all here.
        assertEquals(emptyList<String>(), clf.debugWordTokens("a  b"))
    }

    @Test
    fun devanagariWordTokenizationMatchesPython() {
        // Critical parity case: Python's \w is alphanumeric-only, so the
        // trailing vowel sign U+093F is NOT a word char and "गति" tokenizes
        // as ["गत"]. Java regex (?u)\w would wrongly give ["गति"].
        assertEquals(listOf("गत"), clf.debugWordTokens("गति"))
    }

    @Test
    fun charTokenizationMatchesPython() {
        // Python char_wb 3..5-grams on "Hi mom!"
        assertEquals(
            listOf(
                " hi", "hi ", " hi ",
                " mo", "mom", "om!", "m! ",
                " mom", "mom!", "om! ", " mom!", "mom! "
            ),
            clf.debugCharNgrams("Hi mom!")
        )
    }

    @Test
    fun devanagariCharTokenizationMatchesPython() {
        assertEquals(
            listOf(" गत", "गति", "ति ", " गति", "गति ", " गति "),
            clf.debugCharNgrams("गति")
        )
    }
}
