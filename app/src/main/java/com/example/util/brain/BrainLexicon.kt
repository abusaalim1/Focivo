package com.example.util.brain

import java.util.Locale

/**
 * Worker E (v1.3.0): weighted cue rules ported from focivo_brain/lexicon.py.
 *
 * Positive = study, negative = non-study. Phrases are matched on NORMALISED
 * text on word boundaries; regex is allowed. Weights are in logit units
 * added on top of the ML score.
 *
 * The 354 rules live in assets/focivo_brain_lexicon.json (generated verbatim
 * from lexicon.py — see /tmp/gen_ref.py), compiled here with the exact
 * Python wrapper: `(?<!\w)(?:PATTERN)(?!\w)`.
 *
 * Parity notes:
 * - Python's \w is Unicode by default; Java's is ASCII-only without
 *   UNICODE_CHARACTER_CLASS, so every compiled rule carries the inline
 *   (?U) flag (incl. CODE_HINTS, which is matched on the RAW title like
 *   Python).
 * - Signal names use Python's _pretty(): `\d` variants -> "N"
 *   (e.g. 'class \d{1,2}(th)?' renders as '+class N').
 */
class BrainLexicon private constructor(
    val studyRules: List<Rule>,
    val nonStudyRules: List<Rule>,
    val codeHints: Regex
) {
    data class Rule(val pattern: Regex, val weight: Double, val prettyName: String)

    companion object {
        private val PRETTY_RX = Regex("""\\d(\{[^}]*\}|\+)?(\(th\)\?)?""")

        /** Python _pretty(): show regex cues as readable text. */
        fun prettyName(pattern: String): String = PRETTY_RX.replace(pattern, "N")

        // (?U) = UNICODE_CHARACTER_CLASS: Python's \w is Unicode by
        // default, Java's is ASCII-only without this flag.
        private const val UNI = "(?U)"

        /** Exact port of lexicon.py _compile(): `(?<!\w)(?:p)(?!\w)`. */
        fun compileRule(pattern: String, weight: Double): Rule {
            val rx = Regex("$UNI(?<!\\w)(?:$pattern)(?!\\w)")
            return Rule(rx, weight, prettyName(pattern))
        }

        fun compileCodeHints(pattern: String): Regex =
            Regex("$UNI$pattern")

        /**
         * Build from already-parsed rule tables (used by the JVM parity
         * runner, which supplies its own tiny JSON reader).
         */
        fun fromTables(
            study: List<Pair<String, Double>>,
            nonStudy: List<Pair<String, Double>>,
            codeHintsPattern: String
        ): BrainLexicon = BrainLexicon(
            study.map { compileRule(it.first, it.second) },
            nonStudy.map { compileRule(it.first, it.second) },
            compileCodeHints(codeHintsPattern)
        )

        /**
         * Parse assets/focivo_brain_lexicon.json:
         * {"study": [[pattern, weight], ...], "non_study": [...],
         *  "code_hints": "..."}. Small hand parser — the schema is fixed.
         */
        fun fromJson(json: String): BrainLexicon {
            val p = JsonCursor(json)

            fun parseString(): String {
                p.expect('"')
                val sb = StringBuilder()
                while (true) {
                    when (val c = p.next()) {
                        '\\' -> {
                            val e = p.next()
                            sb.append(
                                when (e) {
                                    'u' -> p.take(4).toInt(16).toChar()
                                    'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'
                                    '\\' -> '\\'; '"' -> '"'; '/' -> '/'
                                    'b' -> '\b'; 'f' -> '\u000C'
                                    else -> e
                                }
                            )
                        }
                        '"' -> return sb.toString()
                        else -> sb.append(c)
                    }
                }
            }

            fun parseNumber(): Double {
                p.skipWs()
                val start = p.pos
                while (p.pos < p.s.length && p.s[p.pos] !in ",]} \t\r\n") p.pos++
                return p.s.substring(start, p.pos).toDouble()
            }

            fun parseTable(): List<Pair<String, Double>> {
                val out = ArrayList<Pair<String, Double>>()
                p.skipWs(); p.expect('[')
                while (true) {
                    p.skipWs()
                    if (p.peek() == ']') {
                        p.pos++
                        return out
                    }
                    p.expect('[')
                    p.skipWs()
                    val pattern = parseString()
                    p.skipWs(); p.expect(',')
                    val weight = parseNumber()
                    p.skipWs(); p.expect(']')
                    out.add(pattern to weight)
                    p.skipWs()
                    if (p.peek() == ',') p.pos++
                }
            }

            p.skipWs(); p.expect('{')
            var study: List<Pair<String, Double>> = emptyList()
            var nonStudy: List<Pair<String, Double>> = emptyList()
            var codeHints = ""
            while (true) {
                p.skipWs()
                if (p.peek() == '}') {
                    p.pos++
                    break
                }
                when (val key = parseString()) {
                    "study" -> {
                        p.skipWs(); p.expect(':'); study = parseTable()
                    }
                    "non_study" -> {
                        p.skipWs(); p.expect(':'); nonStudy = parseTable()
                    }
                    "code_hints" -> {
                        p.skipWs(); p.expect(':'); p.skipWs(); codeHints = parseString()
                    }
                    else -> error("unexpected key $key")
                }
                p.skipWs()
                if (p.peek() == ',') p.pos++
            }
            return fromTables(study, nonStudy, codeHints)
        }

        /** Tiny cursor over the JSON text. */
        private class JsonCursor(val s: String) {
            var pos = 0
            fun peek(): Char = s[pos]
            fun next(): Char = s[pos++]
            fun take(n: Int): String = s.substring(pos, pos + n).also { pos += n }
            fun skipWs() {
                while (pos < s.length && s[pos] in " \t\r\n") pos++
            }
            fun expect(c: Char) {
                if (s[pos] != c) error("expected '$c' at $pos, found '${s[pos]}'")
                pos++
            }
        }
    }

    /**
     * Score the STUDY rules against normalised text, appending "+name"
     * signals. Returns the summed weight.
     */
    fun scoreStudy(normText: String, signals: MutableList<String>): Double {
        var rule = 0.0
        for (r in studyRules) {
            if (r.pattern.containsMatchIn(normText)) {
                rule += r.weight
                signals.add("+${r.prettyName}")
            }
        }
        return rule
    }

    /** Score the NON_STUDY rules, appending "-name" signals. */
    fun scoreNonStudy(normText: String, signals: MutableList<String>): Double {
        var rule = 0.0
        for (r in nonStudyRules) {
            if (r.pattern.containsMatchIn(normText)) {
                rule += r.weight
                signals.add("-${r.prettyName}")
            }
        }
        return rule
    }

    /** Locale-root "%.2f" formatting, matching Python's "%.2f" % p_ml. */
    fun mlSignal(pMl: Double): String = "ml=%.2f".format(Locale.ROOT, pMl)
}
