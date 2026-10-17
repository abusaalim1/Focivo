package com.example.util.brain

import java.text.Normalizer
import java.util.Locale

/**
 * Worker E (v1.3.0): exact Kotlin port of focivo_brain/normalize.py.
 *
 * lowercase, strip urls/emoji/punctuation, canonicalise common variants.
 * Every step mirrors the Python implementation in the same order:
 *   NFKC -> lower -> URL strip -> strip U+FE0F/U+FE0E -> emoji words ->
 *   apostrophe strip -> keep L/M/N code points else space -> CANON map.
 *
 * Parity notes:
 * - Python iterates str by code points; this scans via codePoints() so
 *   astral characters are classified as one unit, exactly like Python.
 * - unicodedata.category(ch)[0] in ("L","M","N") == Character.getType(cp) in
 *   {UPPERCASE..OTHER_LETTER, NON_SPACING/ENCLOSING/COMBINING_SPACING_MARK,
 *   DECIMAL_DIGIT/LETTER/OTHER_NUMBER}.
 * - Proven by FocivoBrainParityTest (normalize spot checks) and the full
 *   engine parity CSV (dev/blind/tricky sets).
 */
object BrainNormalize {

    // Python's re is Unicode-aware by default (\w, \d, \b); Java's is
    // ASCII-only without UNICODE_CHARACTER_CLASS, so every ported pattern
    // carries the inline (?U) flag.
    private val URL = Regex("""(?U)https?://\S+|www\.\S+""")

    private fun cp(codePoint: Int): String = String(Character.toChars(codePoint))

    // a few emojis carry real meaning in social-media style text (insertion
    // order matches normalize.py; keys are disjoint so order is irrelevant)
    private val EMOJI: List<Pair<String, String>> = listOf(
        cp(0x1F602) to " funny ", cp(0x1F923) to " funny ",
        cp(0x1F3AC) to " movie ", cp(0x1F3CF) to " cricket ",
        cp(0x1F3AE) to " gameplay ",
        cp(0x1F3B5) to " song ", cp(0x1F3B6) to " song ",
        cp(0x1F4DA) to " study ", cp(0x1F4D6) to " study ",
        cp(0x270F) to " study ", cp(0x1F4DD) to " notes ",
        cp(0x1F483) to " dance ", cp(0x1F57A) to " dance ",
        cp(0x26BD) to " football ", cp(0x1F37F) to " movie ",
        cp(0x1F9EA) to " science "
    )

    // spelling variants / shorthand -> one canonical form (exact copy of
    // normalize.py CANON, incl. the inert two-word key "oops concepts" which
    // can never match a single token — kept for fidelity)
    private val CANON: Map<String, String> = mapOf(
        "maths" to "math", "mathematics" to "math", "mathematic" to "math",
        "lec" to "lecture", "lect" to "lecture", "lectures" to "lecture", "lactures" to "lecture",
        "chap" to "chapter", "chapters" to "chapter",
        "tut" to "tutorial", "tutorials" to "tutorial",
        "vid" to "video", "videos" to "video",
        "pyqs" to "pyq", "mcqs" to "mcq", "qs" to "questions", "ques" to "questions",
        "numerical" to "numericals", "numerics" to "numericals",
        "padai" to "padhai", "padhaai" to "padhai", "pdhai" to "padhai", "padhaee" to "padhai",
        "kese" to "kaise", "kaisey" to "kaise", "kaisay" to "kaise",
        "samjhaye" to "samjhao", "samjha" to "samjhao", "samjhaiye" to "samjhao",
        "gaana" to "gana", "gaane" to "gana", "gane" to "gana", "songs" to "song",
        "mazedar" to "mazedaar", "mazaa" to "maza", "mazak" to "mazaak",
        "vlogs" to "vlog", "vlogger" to "vlog", "vlogging" to "vlog",
        "memes" to "meme", "reels" to "reel", "pranks" to "prank", "prankd" to "prank",
        "gaming" to "gameplay", "gamer" to "gameplay", "gamers" to "gameplay",
        "livestream" to "live stream", "livestreaming" to "live stream",
        "oneshot" to "one shot", "one-shot" to "one shot",
        "dsa" to "dsa", "oops" to "oop", "oops concepts" to "oop concepts",
        "javascript" to "javascript", "js" to "javascript",
        "bgmi" to "bgmi", "pubgm" to "pubg"
    )

    private val WHITESPACE = Regex("""\s+""")

    /** Python unicodedata.category(ch)[0] in ("L","M","N") over code points. */
    internal fun isLetterMarkNumber(cp: Int): Boolean {
        return when (Character.getType(cp).toByte()) {
            Character.UPPERCASE_LETTER,
            Character.LOWERCASE_LETTER,
            Character.TITLECASE_LETTER,
            Character.MODIFIER_LETTER,
            Character.OTHER_LETTER,
            Character.NON_SPACING_MARK,
            Character.ENCLOSING_MARK,
            Character.COMBINING_SPACING_MARK,
            Character.DECIMAL_DIGIT_NUMBER,
            Character.LETTER_NUMBER,
            Character.OTHER_NUMBER -> true
            else -> false
        }
    }

    fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        // NFKC turns fancy fonts into plain letters, exactly like Python's
        // unicodedata.normalize("NFKC", ...). Python str.lower() is locale-
        // independent, hence Locale.ROOT.
        var t = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        t = URL.replace(t, " ")
            .replace("\uFE0F", "")
            .replace("\uFE0E", "")
        for ((e, w) in EMOJI) {
            if (e in t) t = t.replace(e, w)
        }
        t = t.replace("'", "").replace("\u2019", "")
        val sb = StringBuilder(t.length)
        val cps = t.codePoints().toArray()
        for (cp in cps) {
            sb.appendCodePoint(if (isLetterMarkNumber(cp)) cp else 0x20)
        }
        // CANON maps single tokens; values may contain spaces ("live stream")
        // so the join is re-split, exactly like Python's
        // " ".join(" ".join(toks).split()).
        val toks = WHITESPACE.split(sb.toString()).filter { it.isNotEmpty() }
        val mapped = toks.map { CANON[it] ?: it }
        return WHITESPACE.split(mapped.joinToString(" ")).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
