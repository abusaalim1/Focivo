package com.example.util.brain

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Worker E (v1.3.0): exact Kotlin port of focivo_brain/model.py inference.
 *
 * Tiny text classifier: word + bigram + char-ngram features -> logistic
 * regression. Char n-grams make it robust to Hinglish spelling variations
 * (padhai/padai/pdhai).
 *
 * Parity notes (proven by FocivoBrainParityTest against the Python model on
 * dev.tsv + blind.tsv + both tricky sets):
 * - featurize iterates features in Python Counter insertion order
 *   (words, then bigrams, then char n-grams per token) — LinkedHashMap
 *   preserves the identical order, so the L2 norm and dot product sum in
 *   the same sequence.
 * - char n-grams slice by CODE POINT (Python str slicing), not UTF-16 units.
 * - (1 + ln(v)) * groupWeight, L2-normalised; norm 0 -> 1.0 (Python `or 1.0`).
 * - sigmoid uses the same two-branch form as Python (avoids exp overflow).
 */
object BrainModel {

    const val MAX_TOKENS = 60

    private val GROUP_W = mapOf('w' to 1.0, 'b' to 0.8, 'c' to 0.35)

    fun sigmoid(z: Double): Double {
        return if (z >= 0) {
            1.0 / (1.0 + exp(-z))
        } else {
            val e = exp(z)
            e / (1.0 + e)
        }
    }

    fun logit(p: Double): Double {
        val pc = p.coerceIn(1e-6, 1 - 1e-6)
        return ln(pc / (1 - pc))
    }

    /** Code-point substring, mirroring Python str slicing. */
    private fun cpSubstring(s: String, startCp: Int, lenCp: Int): String {
        val cps = s.codePoints().toArray()
        return String(cps, startCp, lenCp)
    }

    /**
     * Feature map for normalised text, insertion-ordered exactly like
     * Python's Counter: word unigrams, then adjacent bigrams, then char
     * 3/4/5-grams of each "<token>".
     */
    fun featurize(normText: String): LinkedHashMap<String, Double> {
        val toks = normText.split(' ').take(MAX_TOKENS)
        val f = LinkedHashMap<String, Double>()
        for (t in toks) {
            val k = "w:$t"
            f[k] = (f[k] ?: 0.0) + 1.0
        }
        for (i in 0 until toks.size - 1) {
            val k = "b:${toks[i]}_${toks[i + 1]}"
            f[k] = (f[k] ?: 0.0) + 1.0
        }
        for (t in toks) {
            val w = "<$t>"
            val wLen = w.codePointCount(0, w.length)
            for (n in 3..5) {
                for (i in 0..wLen - n) {
                    val k = "c:" + cpSubstring(w, i, n)
                    f[k] = (f[k] ?: 0.0) + 1.0
                }
            }
        }
        val out = LinkedHashMap<String, Double>(f.size)
        var sumSq = 0.0
        for ((k, v) in f) {
            val nv = (1.0 + ln(v)) * (GROUP_W[k[0]] ?: 1.0)
            out[k] = nv
            sumSq += nv * nv
        }
        var norm = sqrt(sumSq)
        if (norm == 0.0) norm = 1.0
        for ((k, v) in out) out[k] = v / norm
        return out
    }

    /**
     * Pure-JVM classifier: no Android imports, no I/O. Build via [load].
     * Weights map: feature -> coefficient; bias added to the logit.
     */
    class Classifier(
        val weights: Map<String, Double>,
        val bias: Double
    ) {
        fun logitOf(feats: Map<String, Double>): Double {
            var z = bias
            for ((k, v) in feats) z += (weights[k] ?: 0.0) * v
            return z
        }

        /** P(study) for normalised text. */
        fun pStudy(normText: String): Double = sigmoid(logitOf(featurize(normText)))

        companion object {
            /**
             * Parses model.json ({"bias": b, "weights": {...}}). Minimal
             * hand parser — the file is flat, so no JSON library is needed
             * (keeps this class dependency-free for the parity runner).
             */
            fun load(json: String): Classifier {
                val weights = HashMap<String, Double>(34000)
                var bias = 0.0
                // "bias":<num>
                val bIdx = json.indexOf("\"bias\"")
                if (bIdx >= 0) {
                    val colon = json.indexOf(':', bIdx)
                    val end = json.indexOf(',', colon)
                    bias = json.substring(colon + 1, end).trim().toDouble()
                }
                // "weights":{"k":v,...} — scan pairs; a pair ends at ',' or '}'
                // at depth 0 (values are plain numbers, keys are JSON strings)
                val wIdx = json.indexOf("\"weights\"")
                if (wIdx >= 0) {
                    var i = json.indexOf('{', wIdx) + 1
                    while (i < json.length) {
                        while (i < json.length && (json[i].isWhitespace() || json[i] == ',')) i++
                        if (i >= json.length || json[i] == '}') break
                        // key: JSON string with escape handling
                        var j = i + 1 // skip opening quote
                        val ksb = StringBuilder()
                        while (true) {
                            val c = json[j]
                            if (c == '\\') {
                                val e = json[j + 1]
                                ksb.append(
                                    when (e) {
                                        'u' -> json.substring(j + 2, j + 6).toInt(16).toChar()
                                        'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'
                                        '\\' -> '\\'; '"' -> '"'; '/' -> '/'
                                        'b' -> '\b'; 'f' -> '\u000C'
                                        else -> e
                                    }
                                )
                                j += if (e == 'u') 6 else 2
                            } else if (c == '"') {
                                j++
                                break
                            } else {
                                ksb.append(c)
                                j++
                            }
                        }
                        val colon = json.indexOf(':', j)
                        var vEnd = colon + 1
                        while (vEnd < json.length && json[vEnd] != ',' && json[vEnd] != '}') vEnd++
                        weights[ksb.toString()] = json.substring(colon + 1, vEnd).trim().toDouble()
                        i = vEnd
                    }
                }
                return Classifier(weights, bias)
            }
        }
    }
}
