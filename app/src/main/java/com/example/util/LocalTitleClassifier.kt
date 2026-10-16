package com.example.util

import android.content.Context
import android.util.Log
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sqrt
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Focivo on-device title classifier (v2, Task 6).
 *
 * A fully offline study-vs-entertainment judge for extracted YouTube video
 * titles: TF-IDF (word 1-2 grams + char_wb 3-5 grams) + logistic regression,
 * exported as a ~418 KB JSON bundle (assets/classifier_bundle.json).
 * No API key, no network, no permissions. Typical title scores in <1 ms.
 *
 * Trained metrics (v2, Round 2 English expansion, 5,100 titles): held-out
 * 99.71% accuracy with ZERO false-blocks at p_entertainment >= 0.65;
 * English-entertainment recall 99.37%; tricky set ~91%.
 *
 * Verdict bands (the block threshold is read from the bundle itself so a
 * retrain never needs a code change; the strong-study band was validated on
 * the 5233-title v2 train+tricky corpus):
 *   p_ent >= blockThreshold (0.65) -> ENTERTAINMENT (block candidate;
 *       zero false-blocks on the 1,020-title held-out set)
 *   p_ent <= STRONG_STUDY_MAX_P_ENT (0.20) -> STRONG_STUDY (allow locally;
 *       zero entertainment titles at or below 0.20 in the whole corpus,
 *       98.7% of study titles decided without a network call)
 *   in between -> UNCERTAIN (caller falls through to the existing
 *       knowledge -> quick -> Gemini chain; Gemini arbitrates when a key
 *       exists, otherwise the existing fail-open rules apply)
 *
 * INFERENCE PARITY (inference_spec.md): the Kotlin below replicates the
 * Python/sklearn mirror EXACTLY —
 *  - lowercase via lowercase(Locale.ROOT) (Python str.lower())
 *  - word tokens: maximal runs of 2+ "word characters", where a word char is
 *    '_' or a Unicode Letter/Number. This deliberately does NOT use Java
 *    regex (?u)\w: Java's \w includes Mark characters (e.g. Devanagari vowel
 *    signs like U+093F), but Python's \w is alphanumeric-only, so on "गति"
 *    Python tokenizes ["गत"] while Java regex would give ["गति"]. The manual
 *    code-point scan matches Python exactly.
 *  - char n-grams: code-point based (NOT UTF-16 units), so non-BMP characters
 *    slice exactly like Python's str slicing. Words split on
 *    Character.isWhitespace, padded with U+0020, substrings of length 3..5.
 *  - tfidf = tf * idf, L2-normalized SEPARATELY per feature kind
 *    (norm = 1.0 when a kind has no features), score = intercept +
 *    sum(normed * coef), p_study = sigmoid(score), p_ent = 1 - p_study.
 * Parity is asserted by LocalTitleClassifierTest: 21 sample titles must match
 * the Python reference scores within 1e-6, plus exact token-stream equality
 * on word/char tokenization samples (incl. Devanagari).
 */
object LocalTitleClassifier {

    private const val TAG = "LocalTitleClassifier"
    private const val ASSET_PATH = "classifier_bundle.json"

    /**
     * Allow locally iff p_entertainment <= this. Zero entertainment titles at
     * or below 0.20 across the 5233-title v2 train+tricky corpus, so the
     * allow-side is fail-safe by construction.
     */
    const val STRONG_STUDY_MAX_P_ENT = 0.20

    /** Bundle version this code was verified against (v2). */
    const val EXPECTED_BUNDLE_VERSION = 2

    enum class TitleDecision { ENTERTAINMENT, STRONG_STUDY, UNCERTAIN }

    data class TermEntry(val isWord: Boolean, val idf: Double, val coef: Double)

    /**
     * Pure-JVM classifier: no Android imports, no I/O. Build via
     * [loadFromJson]; unit-testable on its own.
     */
    class Classifier internal constructor(
        val version: Int,
        val intercept: Double,
        /**
         * Block threshold, read from the bundle's own "threshold" field
         * (v2: 0.65) so a retrained bundle never needs a code change.
         */
        val blockThreshold: Double,
        private val wordTerms: Map<String, TermEntry>,
        private val charTerms: Map<String, TermEntry>
    ) {
        /** P(entertainment) for a title, per inference_spec.md. */
        fun pEntertainment(title: String): Double {
            val cps = title.lowercase(Locale.ROOT).codePoints().toArray()

            // Word features: unigrams + adjacent bigrams over 2+ char tokens.
            val wordTf = LinkedHashMap<String, Int>()
            val tokens = wordTokens(cps)
            for (i in tokens.indices) {
                val uni = tokens[i]
                if (wordTerms.containsKey(uni)) wordTf[uni] = (wordTf[uni] ?: 0) + 1
                if (i + 1 < tokens.size) {
                    val bi = uni + " " + tokens[i + 1]
                    if (wordTerms.containsKey(bi)) wordTf[bi] = (wordTf[bi] ?: 0) + 1
                }
            }

            // Char features: char_wb 3..5-grams over space-padded words.
            val charTf = LinkedHashMap<String, Int>()
            var wStart = 0
            for (i in cps.indices) {
                if (Character.isWhitespace(cps[i])) {
                    addCharNgrams(cps, wStart, i, charTf)
                    wStart = i + 1
                }
            }
            addCharNgrams(cps, wStart, cps.size, charTf)

            // Per-kind L2 norms (1.0 when the kind has no features).
            var wSum = 0.0
            for ((t, c) in wordTf) {
                val v = c * wordTerms.getValue(t).idf
                wSum += v * v
            }
            var cSum = 0.0
            for ((t, c) in charTf) {
                val v = c * charTerms.getValue(t).idf
                cSum += v * v
            }
            val wNorm = sqrt(wSum).let { if (it == 0.0) 1.0 else it }
            val cNorm = sqrt(cSum).let { if (it == 0.0) 1.0 else it }

            var score = intercept
            for ((t, c) in wordTf) {
                val e = wordTerms.getValue(t)
                score += (c * e.idf / wNorm) * e.coef
            }
            for ((t, c) in charTf) {
                val e = charTerms.getValue(t)
                score += (c * e.idf / cNorm) * e.coef
            }
            val pStudy = 1.0 / (1.0 + exp(-score))
            return 1.0 - pStudy
        }

        /** Band decision for a p_entertainment value. */
        fun decide(pEntertainment: Double): TitleDecision = when {
            pEntertainment >= blockThreshold -> TitleDecision.ENTERTAINMENT
            pEntertainment <= STRONG_STUDY_MAX_P_ENT -> TitleDecision.STRONG_STUDY
            else -> TitleDecision.UNCERTAIN
        }

        /** Full pipeline: score then band. Blank titles are never judged. */
        fun classify(title: String): TitleDecision {
            if (title.isBlank()) return TitleDecision.UNCERTAIN
            return decide(pEntertainment(title))
        }

        private fun addCharNgrams(
            cps: IntArray, from: Int, to: Int, out: MutableMap<String, Int>
        ) {
            if (to <= from) return
            val len = to - from + 2
            val padded = IntArray(len)
            padded[0] = 0x20 // space
            System.arraycopy(cps, from, padded, 1, to - from)
            padded[len - 1] = 0x20
            for (n in 3..5) {
                for (i in 0..padded.size - n) {
                    val ng = String(padded, i, n)
                    if (charTerms.containsKey(ng)) out[ng] = (out[ng] ?: 0) + 1
                }
            }
        }

        /** Exposed for tokenization-parity tests: unigrams + bigrams, unfiltered. */
        internal fun debugWordTokens(title: String): List<String> {
            val cps = title.lowercase(Locale.ROOT).codePoints().toArray()
            val toks = wordTokens(cps)
            val out = ArrayList<String>(toks)
            for (i in 0 until toks.size - 1) out.add(toks[i] + " " + toks[i + 1])
            return out
        }

        /** Exposed for tokenization-parity tests: char_wb 3..5-grams, unfiltered. */
        internal fun debugCharNgrams(title: String): List<String> {
            val cps = title.lowercase(Locale.ROOT).codePoints().toArray()
            val out = ArrayList<String>()
            var wStart = 0
            fun emit(from: Int, to: Int) {
                if (to <= from) return
                val len = to - from + 2
                val padded = IntArray(len)
                padded[0] = 0x20
                System.arraycopy(cps, from, padded, 1, to - from)
                padded[len - 1] = 0x20
                for (n in 3..5) {
                    for (i in 0..padded.size - n) out.add(String(padded, i, n))
                }
            }
            for (i in cps.indices) {
                if (Character.isWhitespace(cps[i])) {
                    emit(wStart, i)
                    wStart = i + 1
                }
            }
            emit(wStart, cps.size)
            return out
        }
    }

    /**
     * Python re (?u)\b\w\w+\b equivalent over code points: maximal runs of 2+
     * word chars, where a word char is '_' or a Unicode Letter/Number
     * (== Python str.isalnum() + underscore). Implemented as a manual scan
     * because Java regex (?u)\w wrongly includes Mark characters
     * (see class doc).
     */
    private fun wordTokens(cps: IntArray): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < cps.size) {
            if (!isWordChar(cps[i])) {
                i++
                continue
            }
            var j = i + 1
            while (j < cps.size && isWordChar(cps[j])) j++
            if (j - i >= 2) out.add(String(cps, i, j - i))
            i = j
        }
        return out
    }

    private fun isWordChar(cp: Int): Boolean {
        if (cp == 0x5F) return true // '_'
        // Note: Character.getType returns Int but the type constants are
        // Java bytes, so compare in Byte space.
        return when (Character.getType(cp).toByte()) {
            Character.UPPERCASE_LETTER,
            Character.LOWERCASE_LETTER,
            Character.TITLECASE_LETTER,
            Character.MODIFIER_LETTER,
            Character.OTHER_LETTER,
            Character.DECIMAL_DIGIT_NUMBER,
            Character.LETTER_NUMBER,
            Character.OTHER_NUMBER -> true
            else -> false
        }
    }

    /**
     * Parses classifier_bundle.json into a [Classifier]. Pure function —
     * the unit tests load the bundle from test resources through this.
     */
    fun loadFromJson(json: String): Classifier {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
        val version = root.getValue("version").jsonPrimitive.int
        val intercept = root.getValue("intercept").jsonPrimitive.double
        val blockThreshold = root.getValue("threshold").jsonPrimitive.double
        val wordTerms = HashMap<String, TermEntry>(9000)
        val charTerms = HashMap<String, TermEntry>(9000)
        for (row in root.getValue("terms").jsonArray) {
            val a = row.jsonArray
            val term = a[0].jsonPrimitive.content
            val isWord = a[1].jsonPrimitive.int == 1
            val entry = TermEntry(isWord, a[2].jsonPrimitive.double, a[3].jsonPrimitive.double)
            if (isWord) wordTerms[term] = entry else charTerms[term] = entry
        }
        return Classifier(version, intercept, blockThreshold, wordTerms, charTerms)
    }

    @Volatile
    private var cached: Classifier? = null

    @Volatile
    private var loadFailed = false

    /**
     * Returns the shared classifier, loading the asset bundle once on first
     * use (call off the main thread at service start to warm it). Null when
     * the bundle cannot be loaded — callers must fall through to the existing
     * chain (fail-safe: never block on a missing bundle).
     */
    fun get(context: Context): Classifier? {
        cached?.let { return it }
        if (loadFailed) return null
        synchronized(this) {
            cached?.let { return it }
            if (loadFailed) return null
            return try {
                val json = context.applicationContext.assets.open(ASSET_PATH)
                    .bufferedReader().use { it.readText() }
                loadFromJson(json).also {
                    cached = it
                    Log.i(TAG, "bundle loaded: v${it.version} threshold=${it.blockThreshold}")
                }
            } catch (t: Throwable) {
                loadFailed = true
                Log.w(TAG, "bundle load failed; guard falls back to existing chain", t)
                null
            }
        }
    }
}
