package com.example.util.brain

import java.net.URLDecoder
import java.util.Locale

/**
 * Lookup for the user's own corrections (layer 4). Returns true (study) /
 * false (non-study), or null when no correction exists for the normalised
 * title. Implemented by [BrainCorrectionsLookup] in FocivoBrainCorrections.kt
 * on Android; the parity runner supplies its own.
 */
fun interface BrainCorrectionsLookup {
    /** Returns true (study) / false (non-study), or null when no correction exists. */
    fun get(normalizedTitle: String): Boolean?
}

/**
 * Worker E (v1.3.0): exact Kotlin port of focivo_brain/brain.py.
 *
 * Decides whether a YouTube video / chatbot prompt / web page is study
 * related. Deterministic and explainable: every verdict lists the cues that
 * caused it. No internet, no paid API, no generated text.
 *
 * 4 layers (same as the Python brain):
 *  1. Channel lists (463 study / 397 blocked) — blocked channel = block,
 *     study channel = logit bonus (a funny title from a study channel is
 *     still caught).
 *  2. Cue rules: 354 weighted words/phrases (English + Hinglish).
 *  3. ML classifier: word + bigram + char-ngram logistic regression.
 *  4. User corrections: the user's own override always wins (checked FIRST
 *     in [score], exactly like the Python feedback map).
 *
 * Result: allow, label (study / non_study / uncertain), p_study,
 * confidence, reason, signals.
 *
 * DELIBERATE DEVIATION (owner's fail-open philosophy): the repo's
 * config.json sets uncertain_action="block". The app does NOT adopt it —
 * this engine defaults [uncertainActionAllow] to true, and the guard
 * pipeline (FocivoCombinedBrain) treats the middle band as UNCERTAIN ->
 * Gemini arbiter when a key exists, else ALLOW. Documented, not silent.
 */
class FocivoBrainEngine(
    val model: BrainModel.Classifier,
    val lexicon: BrainLexicon,
    val channels: BrainChannels,
    val corrections: BrainCorrectionsLookup,
    val allowThreshold: Double = 0.62,
    val blockThreshold: Double = 0.38,
    /** Repo default is "block"; the app runs fail-open (see class doc). */
    val uncertainActionAllow: Boolean = true,
    val youtubeBlockShorts: Boolean = true,
    val minChatTokens: Int = 3,
    val allowDomains: List<String> = DEFAULT_ALLOW_DOMAINS,
    val blockDomains: List<String> = DEFAULT_BLOCK_DOMAINS,
    val searchEngines: List<String> = DEFAULT_SEARCH_ENGINES
) {
    data class Verdict(
        val allow: Boolean,
        val label: String, // "study" | "non_study" | "uncertain"
        val pStudy: Double,
        val confidence: Double,
        val reason: String,
        val signals: List<String> = emptyList()
    )

    /** Intermediate of [score]: null p means "no text to judge". */
    data class Scored(val pStudy: Double?, val norm: String, val signals: List<String>)

    companion object {
        val DEFAULT_ALLOW_DOMAINS = listOf(
            "wikipedia.org", "khanacademy.org", "ncert.nic.in", "geeksforgeeks.org", "w3schools.com",
            "stackoverflow.com", "github.com", "coursera.org", "nptel.ac.in", "byjus.com", "vedantu.com",
            "leetcode.com", "docs.python.org", "developer.android.com", "kotlinlang.org", "scholar.google.com",
            "britannica.com", "mit.edu", "edx.org", "brilliant.org", "desmos.com", "wolframalpha.com",
            "unacademy.com", "pw.live", "testbook.com", "tutorialspoint.com", "mdn.io", "developer.mozilla.org"
        )
        val DEFAULT_BLOCK_DOMAINS = listOf(
            "instagram.com", "facebook.com", "netflix.com", "primevideo.com", "hotstar.com", "jiocinema.com",
            "tiktok.com", "twitter.com", "x.com", "9gag.com", "twitch.tv", "pinterest.com", "snapchat.com",
            "sonyliv.com", "zee5.com", "spotify.com", "gaana.com", "wynk.in", "roblox.com", "miniclip.com",
            "crazygames.com", "poki.com", "dream11.com", "bet365.com", "pornhub.com", "xvideos.com"
        )
        val DEFAULT_SEARCH_ENGINES = listOf(
            "google.com", "bing.com", "duckduckgo.com", "search.brave.com", "yahoo.com"
        )

        private fun domainOf(host: String): String {
            var h = host.lowercase(Locale.ROOT).substringBefore(":")
            for (pre in listOf("www.", "m.", "mobile.")) {
                if (h.startsWith(pre)) {
                    h = h.substring(pre.length)
                    break
                }
            }
            return h
        }

        private fun domainIn(host: String, domains: List<String>): Boolean =
            domains.any { host == it || host.endsWith(".$it") }
    }

    // chat session mood memory: session -> last 4 p values (deque maxlen 4)
    private val sessions = HashMap<String, ArrayDeque<Double>>()

    /**
     * Scoring core: normalise -> corrections (FIRST, like Python feedback) ->
     * ML + lexicon + code hints + extra logit. Returns null p when there is
     * no text to judge.
     */
    fun score(
        text: String?,
        extraLogit: Double = 0.0,
        extraSignals: List<String> = emptyList(),
        raw: String? = null
    ): Scored {
        val norm = BrainNormalize.normalize(text ?: "")
        val signals = ArrayList(extraSignals)
        if (norm.isEmpty()) return Scored(null, norm, signals)
        val corrected = corrections.get(norm)
        if (corrected != null) {
            // the user's own correction always wins (Python: 0.97 / 0.03)
            return Scored(if (corrected) 0.97 else 0.03, norm, listOf("your earlier correction"))
        }
        val pMl = model.pStudy(norm)
        var rule = 0.0
        rule += lexicon.scoreStudy(norm, signals)
        rule += lexicon.scoreNonStudy(norm, signals)
        if (raw != null && lexicon.codeHints.containsMatchIn(raw)) {
            rule += 1.5
            signals.add("+code")
        }
        rule = rule.coerceIn(-5.0, 5.0)
        val z = BrainModel.logit(pMl) * 0.8 + rule + extraLogit
        signals.add(lexicon.mlSignal(pMl))
        return Scored(BrainModel.sigmoid(z), norm, signals)
    }

    fun verdict(p: Double, signals: List<String>, what: String): Verdict {
        val conf = Math.abs(p - 0.5) * 2
        val cues = signals.filter { !it.startsWith("ml=") }
        val cueTxt = if (cues.isNotEmpty()) cues.take(6).joinToString(", ") else "model score"
        return when {
            p >= allowThreshold -> Verdict(
                true, "study", p, conf,
                "$what looks study related ($cueTxt)", signals
            )
            p <= blockThreshold -> Verdict(
                false, "non_study", p, conf,
                "$what looks non-study ($cueTxt)", signals
            )
            else -> Verdict(
                uncertainActionAllow, "uncertain", p, conf,
                "Not sure about this $what ($cueTxt). Ask the user to confirm.", signals
            )
        }
    }

    // ------------------------------------------------------------ public checks

    /** channel = display name, handle = '@PhysicsWallah' (either or both). */
    fun checkYoutube(
        title: String?,
        channel: String? = null,
        description: String? = null,
        isShort: Boolean = false,
        handle: String? = null
    ): Verdict {
        if (isShort && youtubeBlockShorts) {
            return Verdict(
                false, "non_study", 0.05, 0.9,
                "YouTube Shorts are blocked during study time", listOf("shorts")
            )
        }
        var extra = 0.0
        val sig = ArrayList<String>()
        val hit = channels.lookup(handle, channel)
        if (hit != null) {
            if (hit.group == "black") {
                return Verdict(
                    false, "non_study", 0.03, 0.94,
                    "Channel '${hit.displayName}' is on the block list",
                    listOf("blocked channel")
                )
            }
            extra += hit.bonus
            sig.add("study channel")
        }
        var text = title ?: ""
        if (!description.isNullOrEmpty()) text = text + " . " + description.take(200)
        val s = score(text, extra, sig, raw = title)
        if (s.pStudy == null) {
            return Verdict(
                uncertainActionAllow, "uncertain", 0.5, 0.0,
                "No title found yet", emptyList()
            )
        }
        return verdict(s.pStudy, s.signals, "video")
    }

    fun checkChat(prompt: String?, session: String = "default"): Verdict {
        val norm = BrainNormalize.normalize(prompt ?: "")
        val hist = sessions.getOrPut(session) { ArrayDeque() }
        val nTok = if (norm.isEmpty()) 0 else norm.split(' ').size
        if (nTok < minChatTokens && corrections.get(norm) == null) {
            if (hist.isNotEmpty()) {
                // "why?", "ok thanks", "aur batao" -> follow the recent mood
                val p = hist.sum() / hist.size
                return verdict(p, listOf("follow-up of recent prompts"), "follow-up")
            }
            return Verdict(true, "uncertain", 0.5, 0.0, "Too short to judge, allowed for now", listOf("short"))
        }
        val s = score(prompt, raw = prompt)
        if (s.pStudy == null) {
            return Verdict(true, "uncertain", 0.5, 0.0, "Empty prompt", emptyList())
        }
        hist.addLast(s.pStudy)
        while (hist.size > 4) hist.removeFirst()
        return verdict(s.pStudy, s.signals, "prompt")
    }

    fun checkBrowser(url: String?, pageTitle: String? = null): Verdict {
        val withScheme = if ((url ?: "").contains("://")) (url ?: "") else "https://" + (url ?: "")
        // Python _domain(u.netloc): raw authority, lowercased, cut at the
        // FIRST ':' (so userinfo quirks replicate exactly), www./m./mobile.
        // stripped. java.net.URI.host would parse userinfo correctly and
        // DIVERGE from Python — hence rawAuthority.
        val netloc = try {
            java.net.URI(withScheme).rawAuthority ?: ""
        } catch (_: Exception) {
            ""
        }
        val host = domainOf(netloc)
        if (domainIn(host, blockDomains)) {
            return Verdict(
                false, "non_study", 0.02, 0.96,
                "$host is on the block list", listOf("blocked domain")
            )
        }
        // search pages: judge what the user is searching for
        var q: String? = null
        val query = try {
            java.net.URI(withScheme).rawQuery ?: ""
        } catch (_: Exception) {
            ""
        }
        if (query.isNotEmpty()) {
            val qs = parseQuery(query)
            if (domainIn(host, searchEngines) && qs.containsKey("q")) {
                q = qs["q"]
            } else if (host.endsWith("youtube.com") && qs.containsKey("search_query")) {
                q = qs["search_query"]
            }
        }
        if (q != null) {
            val s = score(q, raw = q)
            if (s.pStudy != null) return verdict(s.pStudy, s.signals, "search")
        }
        var extra = 0.0
        val sig = ArrayList<String>()
        if (domainIn(host, allowDomains)) {
            extra += 2.0
            sig.add("study site")
        }
        val title = pageTitle ?: ""
        if (BrainNormalize.normalize(title).isEmpty()) {
            if (extra > 0) {
                return Verdict(true, "study", 0.8, 0.6, "$host is a study site", sig)
            }
            return Verdict(
                uncertainActionAllow, "uncertain", 0.5, 0.0,
                "Page title not available for $host", emptyList()
            )
        }
        val s = score(title, extra, sig, raw = title)
        return verdict(s.pStudy ?: 0.5, s.signals, "page")
    }

    /** Python urllib.parse.parse_qs semantics: '+' -> space, %-decoding, blank values dropped. */
    private fun parseQuery(rawQuery: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val name: String
            val value: String
            if (eq < 0) {
                name = decode(pair)
                value = ""
            } else {
                name = decode(pair.substring(0, eq))
                value = decode(pair.substring(eq + 1))
            }
            if (value.isNotEmpty() && name !in out) out[name] = value
        }
        return out
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        s
    }
}
