package com.example

import com.example.util.brain.BrainChannels
import com.example.util.brain.BrainCorrectionsLookup
import com.example.util.brain.BrainLexicon
import com.example.util.brain.BrainModel
import com.example.util.brain.BrainNormalize
import com.example.util.brain.FocivoBrainEngine
import com.example.util.brain.FocivoCombinedBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Worker E (v1.3.0) parity + behaviour tests for the ported Focivo brain.
 *
 * PART 1 — PARITY: the expected values in /focivo_brain_parity.csv are the
 * exact outputs of the Python reference implementation (focivo_brain, repo
 * defaults: uncertain_action="block") on eval/dev.tsv + eval/blind.tsv +
 * tricky_test.csv + tricky_content.csv (719 rows). Matching p_study within
 * 1e-9 plus identical label/allow/reason/signals asserts the Kotlin port
 * (normalize + model + lexicon + channels + engine) replicates Python
 * end to end. Local JVM check: max |diff| = 4.98e-13 across all 719 rows.
 *
 * PART 2 — APP BEHAVIOUR: corrections-first, fail-open uncertain band
 * (the app's deliberate deviation from the repo's uncertain_action="block"),
 * channel block/allow, chat mood inheritance, browser search-query judging,
 * and the combined-brain formula + bands.
 */
class FocivoBrainParityTest {

    companion object {
        private lateinit var engine: FocivoBrainEngine
        private lateinit var rows: List<Map<String, String>>

        private fun resource(name: String): String =
            FocivoBrainParityTest::class.java.getResource("/$name")?.readText()
                ?: error("test resource $name not found")

        /** Minimal CSV reader handling quoted fields. */
        private fun readCsv(text: String): List<Map<String, String>> {
            val outRows = mutableListOf<List<String>>()
            val cur = StringBuilder()
            var row = mutableListOf<String>()
            var inQ = false
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    inQ && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        cur.append('"'); i += 2
                    }
                    c == '"' -> {
                        inQ = !inQ; i++
                    }
                    !inQ && c == ',' -> {
                        row.add(cur.toString()); cur.clear(); i++
                    }
                    !inQ && (c == '\n' || c == '\r') -> {
                        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        row.add(cur.toString()); cur.clear()
                        if (row.any { it.isNotEmpty() }) outRows.add(row)
                        row = mutableListOf(); i++
                    }
                    else -> {
                        cur.append(c); i++
                    }
                }
            }
            val header = outRows[0]
            return outRows.drop(1).map { r ->
                header.mapIndexed { k, h -> h to (r.getOrNull(k) ?: "") }.toMap()
            }
        }

        @BeforeClass
        @JvmStatic
        fun load() {
            val model = BrainModel.Classifier.load(resource("focivo_brain_model.json"))
            val lexicon = BrainLexicon.fromJson(resource("focivo_brain_lexicon.json"))
            val channels = BrainChannels.fromJson(resource("focivo_brain_channels.json"))
            // repo defaults for parity (uncertain_action="block")
            engine = FocivoBrainEngine(
                model, lexicon, channels,
                BrainCorrectionsLookup { null },
                uncertainActionAllow = false
            )
            rows = readCsv(resource("focivo_brain_parity.csv"))
            assertEquals(719, rows.size)
        }
    }

    @Test
    fun kotlinEngineMatchesPythonReference() {
        var maxDiff = 0.0
        for ((idx, r) in rows.withIndex()) {
            val kind = r.getValue("kind")
            val text = r.getValue("text")
            val v = when (kind) {
                "yt" -> engine.checkYoutube(text)
                "chat" -> engine.checkChat(text, session = text)
                "url" -> {
                    val parts = text.split("||", limit = 2)
                    engine.checkBrowser(parts[0], parts.getOrNull(1))
                }
                else -> error("kind $kind")
            }
            val expectedP = r.getValue("engine_p").toDouble()
            val diff = Math.abs(v.pStudy - expectedP)
            if (diff > maxDiff) maxDiff = diff
            assertTrue(
                "p_study mismatch row $idx [${r["src"]}] '$text' (Kotlin=${v.pStudy}, Python=$expectedP)",
                diff <= 1e-9
            )
            assertEquals("label mismatch row $idx '$text'", r.getValue("verdict_label"), v.label)
            assertEquals(
                "allow mismatch row $idx '$text'",
                r.getValue("verdict_allow").toBoolean(), v.allow
            )
            assertEquals("reason mismatch row $idx '$text'", r.getValue("reason"), v.reason)
            assertEquals(
                "signals mismatch row $idx '$text'",
                r.getValue("signals")!!.split("|"), v.signals
            )
        }
        // documented local JVM result: max |diff| = 4.98e-13
        assertTrue("max p diff too large: $maxDiff", maxDiff <= 1e-9)
    }

    @Test
    fun normalizeSpotChecks() {
        // (raw, Python normalize() output)
        val spots = listOf(
            "𝗟𝗮𝘄𝘀 𝗼𝗳 𝗠𝗼𝘁𝗶𝗼𝗻 | 𝗖𝗹𝗮𝘀𝘀 𝟵 𝗦𝗰𝗶𝗲𝗻𝗰𝗲" to
                "laws of motion class 9 science",
            "LIMITS in 1 SHOT 🔥 JEE MAINS 2027" to
                "limits in 1 shot jee mains 2027",
            "कक्षा 12 भौतिकी: विद्युत धारा" to
                "कक्षा 12 भौतिकी विद्युत धारा",
            "Padhai kaise karein? 😂📚" to
                "padhai kaise karein funny study",
            "one-shot revision http://example.com/x" to
                "one shot revision",
            "don't stop" to "dont stop"
        )
        for ((raw, expected) in spots) {
            assertEquals("normalize('$raw')", expected, BrainNormalize.normalize(raw))
        }
        assertEquals("", BrainNormalize.normalize("  "))
        assertEquals("", BrainNormalize.normalize(""))
    }

    @Test
    fun channelBlocklistBeatsStudyTitle() {
        val v = engine.checkYoutube("Maths trick", channel = "T-Series")
        assertFalse(v.allow)
        assertEquals("non_study", v.label)
        assertTrue(v.reason.contains("T-Series"))
    }

    @Test
    fun studyChannelGivesBonusButFunnyTitleStillCaught() {
        val study = engine.checkYoutube("Newton's Laws of Motion | Class 11 Physics", channel = "Physics Wallah")
        assertTrue(study.allow)
        val funTitle = engine.checkYoutube("Teacher funny moments compilation", channel = "Physics Wallah")
        assertFalse("funny title on a study channel must still be caught", funTitle.allow)
    }

    @Test
    fun handleLookupWorks() {
        val v = engine.checkYoutube("some neutral title here", handle = "@tseries")
        assertFalse(v.allow)
        assertEquals("non_study", v.label)
    }

    @Test
    fun correctionsAlwaysWin() {
        val mem = HashMap<String, Boolean>()
        val e = FocivoBrainEngine(
            engine.model, engine.lexicon, engine.channels,
            BrainCorrectionsLookup { mem[it] },
            uncertainActionAllow = false
        )
        // user says this entertainment title IS study -> allowed with 0.97
        mem[BrainNormalize.normalize("funny prank compilation")] = true
        val v1 = e.checkYoutube("Funny Prank Compilation!!!")
        assertTrue(v1.allow)
        assertEquals(0.97, v1.pStudy, 1e-12)
        assertEquals(listOf("your earlier correction"), v1.signals)
        // user says this study title is NOT study -> blocked with 0.03
        mem[BrainNormalize.normalize("physics lecture")] = false
        val v2 = e.checkYoutube("Physics Lecture")
        assertFalse(v2.allow)
        assertEquals(0.03, v2.pStudy, 1e-12)
    }

    @Test
    fun failOpenUncertainBandAllows() {
        // The app's deliberate deviation: uncertain_action="block" is NOT
        // adopted. With uncertainActionAllow=true the middle band allows.
        // "dance ke saath yaad karo formulas" is genuinely uncertain for the
        // repo brain (p=0.4027, between 0.38 and 0.62).
        val e = FocivoBrainEngine(
            engine.model, engine.lexicon, engine.channels,
            BrainCorrectionsLookup { null },
            uncertainActionAllow = true
        )
        val v = e.checkYoutube("dance ke saath yaad karo formulas")
        assertEquals("uncertain", v.label)
        assertTrue("fail-open: uncertain must allow", v.allow)
        // ...while the repo default (used for parity) blocks it
        val vRepo = engine.checkYoutube("dance ke saath yaad karo formulas")
        assertEquals("uncertain", vRepo.label)
        assertFalse(vRepo.allow)
    }

    @Test
    fun chatMoodInheritance() {
        val v1 = engine.checkChat("Explain photosynthesis for class 10", session = "mood-t1")
        assertTrue(v1.allow)
        // short follow-up inherits the session mood
        val v2 = engine.checkChat("aur batao", session = "mood-t1")
        assertTrue(v2.allow)
        assertEquals("study", v2.label)
        assertTrue(v2.reason.contains("follow-up"))
        // ...but a fresh session with no history allows short prompts openly
        val v3 = engine.checkChat("aur batao", session = "mood-fresh")
        assertTrue(v3.allow)
        assertEquals("uncertain", v3.label)
    }

    @Test
    fun browserSearchQueryIsJudged() {
        val v = engine.checkBrowser(
            "https://www.google.com/search?q=salman+khan+new+movie+trailer",
            "salman khan new movie trailer - Google Search"
        )
        assertFalse(v.allow)
        assertEquals("non_study", v.label)
        val blocked = engine.checkBrowser("https://www.instagram.com/reels/", "Instagram")
        assertFalse(blocked.allow)
        assertTrue(blocked.reason.contains("block list"))
        val study = engine.checkBrowser("https://www.khanacademy.org/math", "Math | Khan Academy")
        assertTrue(study.allow)
    }

    @Test
    fun combinedFormulaAndBands() {
        // exact formula: 0.5*p_repo + 0.5*p_v3, single-brain fallback
        assertEquals(0.75, FocivoCombinedBrain.combineScores(0.9, 0.6)!!, 1e-12)
        assertEquals(0.9, FocivoCombinedBrain.combineScores(0.9, null)!!, 1e-12)
        assertEquals(0.6, FocivoCombinedBrain.combineScores(null, 0.6)!!, 1e-12)
        assertEquals(null, FocivoCombinedBrain.combineScores(null, null))
        // title bands: allow >= 0.80, block <= 0.35, else uncertain
        assertEquals(FocivoCombinedBrain.Decision.ALLOW, FocivoCombinedBrain.decideTitleBand(0.80))
        assertEquals(FocivoCombinedBrain.Decision.ALLOW, FocivoCombinedBrain.decideTitleBand(0.95))
        assertEquals(FocivoCombinedBrain.Decision.BLOCK, FocivoCombinedBrain.decideTitleBand(0.35))
        assertEquals(FocivoCombinedBrain.Decision.BLOCK, FocivoCombinedBrain.decideTitleBand(0.10))
        assertEquals(FocivoCombinedBrain.Decision.UNCERTAIN, FocivoCombinedBrain.decideTitleBand(0.50))
        assertEquals(FocivoCombinedBrain.Decision.UNCERTAIN, FocivoCombinedBrain.decideTitleBand(0.79))
        // content bands: allow >= 0.80, block <= 0.31, else uncertain
        assertEquals(FocivoCombinedBrain.Decision.ALLOW, FocivoCombinedBrain.decideContentBand(0.80))
        assertEquals(FocivoCombinedBrain.Decision.BLOCK, FocivoCombinedBrain.decideContentBand(0.31))
        assertEquals(FocivoCombinedBrain.Decision.UNCERTAIN, FocivoCombinedBrain.decideContentBand(0.50))
        assertEquals(FocivoCombinedBrain.Decision.UNCERTAIN, FocivoCombinedBrain.decideContentBand(0.32))
        // band constants (guards against accidental drift)
        assertEquals(0.80, FocivoCombinedBrain.TITLE_ALLOW_P, 1e-12)
        assertEquals(0.35, FocivoCombinedBrain.TITLE_BLOCK_P, 1e-12)
        assertEquals(0.80, FocivoCombinedBrain.CONTENT_ALLOW_P, 1e-12)
        assertEquals(0.31, FocivoCombinedBrain.CONTENT_BLOCK_P, 1e-12)
    }
}
