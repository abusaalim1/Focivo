package com.example.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Focivo Study-Guard Knowledge Pack (v1.1.8).
 *
 * Curated offline classifier for YouTube videos and AI-chat messages.
 * Decides STUDY / NON_STUDY / UNCERTAIN in well under 1ms with zero network.
 * UNCERTAIN verdicts fall through to the existing Gemini path — this file never
 * replaces Gemini, it just answers the obvious cases instantly.
 *
 * HEALTH DESIGN (per owner instruction — only what is healthy):
 * - Curated subsets only (~80 channels, ~155 allow phrases incl. Devanagari,
 *   ~127 block phrases). The pack's full 2504 keywords are deliberately NOT imported.
 * - Whole-word/phrase matching only: text is padded with spaces and every phrase
 *   is matched as " phrase " via plain contains(). No regex over the phrase lists,
 *   so there is zero catastrophic-backtracking risk ("art" can never match "party").
 * - Pure Kotlin: no Android imports, no Context, no I/O. Unit-testable, tiny
 *   crash surface. All callers already run off the UI thread / behind throttles.
 *
 * Scoring (simplified from Knowledge Pack section 1):
 *   channel in allowlist                    -> STUDY immediately
 *   each ALLOW phrase hit                   -> +1 (single word) / +2 (2+ words)
 *   each BLOCK phrase hit                   -> -1.5 (single word) / -2.5 (2+ words)
 *   STRONG STUDY OVERRIDE                   -> +4 (class/chapter + number, or
 *                                              jee|neet|upsc|ncert|one shot|full lecture)
 *   score >= 3  -> STUDY
 *   score <= -2 -> NON_STUDY
 *   else        -> UNCERTAIN (caller falls through to Gemini)
 */
object StudyGuardKnowledge {

    enum class Verdict { STUDY, NON_STUDY, UNCERTAIN }

    data class ScoreResult(val verdict: Verdict, val score: Double)

    // ------------------------------------------------------------------
    // 1. Education channel allowlist (~80). Matched as
    //    channel.lowercase().contains(entry) — channel names are short,
    //    so contains() here is safe and fast.
    // ------------------------------------------------------------------
    private val EDUCATION_CHANNELS: Set<String> = hashSetOf(
        "physics wallah", "alakh pandey", "khan gs research centre", "khan sir",
        "prashant kirad", "magnet brains", "vedantu", "unacademy", "study iq",
        "adda247", "byju's", "byjus", "allen", "aakash", "pw vidyapeeth",
        "codewithharry", "apna college", "love babbar", "take u forward", "striver",
        "chai aur code", "hitesh choudhary", "khan academy", "3blue1brown",
        "ncert official", "nptel", "swayam", "crashcourse", "ted-ed", "tedx",
        "mit opencourseware", "stanford online", "doubtnut", "toppr", "wifistudy",
        "exampur", "rojgar with ankit", "careerwill", "utkarsh classes",
        "parmar ssc", "rankers gurukul", "gagan pratap", "gate smashers",
        "neso academy", "jenny's lectures", "abdul bari", "telusko",
        "freecodecamp", "kunal kushwaha", "ws cube tech", "amoeba sisters",
        "numberphile", "professor leonard", "organic chemistry tutor",
        "physics galaxy", "ashish arora", "mohit tyagi", "competishun",
        "dear sir", "ekeeda", "gate academy", "made easy", "testbook",
        "oliveboard", "vikas divyakirti", "drishti ias", "vision ias",
        "sleepy classes", "mrunal patel", "study iq ias", "byju's exam prep",
        "gradeup", "ixambee", "practicemock", "catking", "ims cat",
        "hitbullseye", "indigo learn", "ca wallah", "gate wallah"
    )

    // ------------------------------------------------------------------
    // 2. ALLOW phrases — strongest academic signals.
    // ------------------------------------------------------------------
    private val RAW_ALLOW: List<String> = listOf(
        "lecture", "tutorial", "one shot", "revision", "pyq", "pyqs",
        "crash course", "handwritten notes", "sample paper", "derivation",
        "formula sheet", "mind map", "ncert", "cbse", "icse",
        "class 10", "class 12", "class 11", "class 9", "class 8",
        "jee", "neet", "upsc", "ssc", "gate", "cat", "board exam",
        "previous year", "mock test", "test series", "important questions",
        "physics", "chemistry", "chemical", "maths", "biology", "algebra",
        "calculus", "trigonometry", "organic chemistry", "human physiology",
        "genetics", "cell division", "photosynthesis", "thermodynamics",
        "kinematics", "laws of motion", "electrostatics", "optics",
        "probability", "statistics", "vectors", "coordinate geometry",
        "python", "java", "c++", "javascript", "data structures", "algorithms",
        "dsa", "leetcode", "dbms", "sql", "operating system",
        "computer networks", "machine learning", "web development",
        "app development", "kotlin", "flutter", "system design",
        "interview preparation", "study with me", "how to study",
        "study plan", "study tips", "pomodoro study", "active recall",
        "spaced repetition", "time management", "exam strategy", "topper",
        "syllabus", "curriculum", "chapter", "full chapter", "chapter wise",
        "full lecture", "concept", "explained", "step by step",
        "solved examples", "numericals", "practice questions", "worksheet",
        "assignment", "homework", "doubt solving", "mcq", "mcqs",
        "question paper", "semester", "btech", "mbbs lecture",
        "nursing lecture", "grammar", "tenses", "essay writing",
        "letter writing", "vocabulary", "ielts", "spoken english",
        "accountancy", "balance sheet", "business studies", "economics",
        "history", "geography", "civics", "polity", "constitution",
        "current affairs", "general knowledge", "static gk",
        "padhai", "kaise padhe", "pariksha", "board pariksha",
        "sarkari naukri", "samanya gyan", "live class", "educator",
        "professor", "exam preparation", "revision series", "rapid revision",
        "ncert solutions", "rd sharma", "hc verma", "dc pandey",
        "marathon", "mock drill",
        // Devanagari set
        "पढ़ाई", "गणित", "विज्ञान", "भौतिकी", "रसायन", "जीव विज्ञान",
        "इतिहास", "भूगोल", "परीक्षा", "कक्षा", "अध्याय", "एनसीईआरटी",
        "सूत्र", "समीकरण", "प्रश्न", "नोट्स", "सिलेबस", "टॉपर", "क्लास",
        "चैप्टर", "लेक्चर", "तैयारी", "हल", "महत्वपूर्ण प्रश्न", "मॉक टेस्ट"
    )

    // ------------------------------------------------------------------
    // 3. BLOCK phrases — highest-signal only: phrases that are almost
    //    never academic. The pack's long tail is deliberately skipped.
    // ------------------------------------------------------------------
    private val RAW_BLOCK: List<String> = listOf(
        "vlog", "prank", "roast", "meme", "try not to laugh", "gameplay",
        "gaming", "walkthrough", "speedrun", "bgmi", "pubg", "free fire",
        "valorant", "gta", "minecraft", "roblox", "fortnite", "call of duty",
        "esports", "mod apk", "full movie", "movie", "film", "trailer",
        "teaser", "web series", "big boss", "kapil sharma", "music video",
        "lyrical video", "dj remix", "slowed and reverb", "8d audio",
        "dance cover", "mukbang", "asmr", "unboxing", "haul", "day in my life",
        "grwm", "get ready with me", "outfit of the day", "storytime",
        "exposed", "gossip", "diss track", "trolling", "epic fails",
        "viral video", "trending now", "24 hour challenge", "hide and seek",
        "last to leave", "100 layers", "reaction", "reacting to", "challenge",
        "skit", "stand up comedy", "comedy video", "bloopers",
        "fail compilation", "funny moments", "satisfying video",
        "oddly satisfying", "slime", "room tour", "house tour",
        "morning routine vlog", "night routine vlog", "makeup tutorial",
        "skincare routine", "hairstyle tutorial", "lookbook",
        "transition video", "tea spill", "controversy", "beef",
        "behind the scenes", "box office", "movie review", "ending explained",
        "movie recap", "movie songs", "item song", "song reaction",
        "red carpet", "paparazzi", "award show", "kdrama", "anime", "cartoon",
        "shinchan", "doraemon", "tom and jerry", "naruto", "one piece",
        "bts", "blackpink", "kpop", "whatsapp status", "attitude status",
        "love status", "sad status", "jukebox", "remix", "mashup",
        "bass boosted", "party songs", "wedding dance", "choreography",
        "instagram reels", "tiktok", "reels", "celebrity gossip",
        "movie explained in hindi", "full episode", "latest episode",
        "tv serial", "daily soap", "dance deewane", "indian idol",
        "splitsvilla", "roadies", "film gossip", "actor interview",
        "cricket highlights"
    )

    // ------------------------------------------------------------------
    // Normalized once at class-load. Matching is plain contains() on
    // space-padded text — no regex over the lists, no backtracking risk.
    // ------------------------------------------------------------------
    private val ALLOW: List<String> = RAW_ALLOW.map(::normalize)
    private val BLOCK: List<String> = RAW_BLOCK.map(::normalize)

    // Strong-study override: "class 10", "chapter 4" style tokens.
    // Tiny bounded regex — linear, no backtracking risk.
    private val CLASS_CHAPTER_RE = Regex("\\b(?:class|chapter)\\s+\\d{1,2}\\b")
    private val OVERRIDE_LITERALS: List<String> =
        listOf("jee", "neet", "upsc", "ncert", "one shot", "full lecture").map(::normalize)

    // Sticky verdict cache for AI chats: package -> (verdict, timestamp).
    // Short follow-ups ("continue", "ok", ...) inherit the last confident
    // verdict for ~10 minutes instead of being re-evaluated with no signal.
    private val stickyVerdicts = ConcurrentHashMap<String, Pair<Verdict, Long>>()
    private const val STICKY_TTL_MS = 10 * 60 * 1000L

    private val FOLLOWUP_HINTS: List<String> = listOf(
        "continue", "explain more", "next", "ok", "yes", "thanks", "thank you",
        "go on", "and then", "more", "elaborate", "example", "please"
    ).map(::normalize)

    /** Lowercase + collapse whitespace. No regex, no allocations beyond one pass. */
    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        var prevSpace = true // also trims leading whitespace
        for (c in s) {
            if (c.isWhitespace()) {
                if (!prevSpace) {
                    sb.append(' ')
                    prevSpace = true
                }
            } else {
                sb.append(c.lowercaseChar())
                prevSpace = false
            }
        }
        if (prevSpace && sb.isNotEmpty()) sb.setLength(sb.length - 1) // trim trailing
        return sb.toString()
    }

    /** Core scorer. Text must already be normalized; padding is applied here. */
    fun scoreText(rawText: String): ScoreResult {
        val norm = normalize(rawText)
        if (norm.isEmpty()) return ScoreResult(Verdict.UNCERTAIN, 0.0)
        val padded = " $norm "
        var score = 0.0
        for (p in ALLOW) {
            if (padded.contains(" $p ")) score += if (p.contains(' ')) 2.0 else 1.0
        }
        for (p in BLOCK) {
            if (padded.contains(" $p ")) score -= if (p.contains(' ')) 2.5 else 1.5
        }
        var strongOverride = CLASS_CHAPTER_RE.containsMatchIn(padded)
        if (!strongOverride) {
            for (lit in OVERRIDE_LITERALS) {
                if (padded.contains(" $lit ")) {
                    strongOverride = true
                    break
                }
            }
        }
        if (strongOverride) score += 4.0
        val verdict = when {
            score >= 3.0 -> Verdict.STUDY
            score <= -2.0 -> Verdict.NON_STUDY
            else -> Verdict.UNCERTAIN
        }
        return ScoreResult(verdict, score)
    }

    /** YouTube entry point: channel allowlist short-circuits to STUDY. */
    fun scoreVideo(title: String?, channel: String?): Verdict {
        val ch = normalize(channel.orEmpty())
        if (ch.isNotEmpty()) {
            val allowed = effectiveChannels()
            if (allowed.any { ch.contains(it) }) {
                return Verdict.STUDY
            }
        }
        return scoreText(listOfNotNull(title, channel).joinToString(" ")).verdict
    }

    // ------------------------------------------------------------------
    // v1.3.0 — user-customizable study channels (managed by
    // StudyChannelsManager, which pushes prefs state here).
    // ------------------------------------------------------------------
    private val customChannels: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val disabledChannels: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Built-in education channels, sorted — for the Study channels UI. */
    fun getBuiltInChannels(): List<String> = EDUCATION_CHANNELS.sorted()

    /** Replace the user-added channel set (entries are normalized). */
    fun setCustomChannels(channels: Collection<String>) {
        customChannels.clear()
        for (c in channels) {
            val n = normalize(c)
            if (n.isNotEmpty()) customChannels.add(n)
        }
    }

    /** Replace the set of built-in channels the user removed. */
    fun setDisabledChannels(channels: Collection<String>) {
        disabledChannels.clear()
        for (c in channels) {
            val n = normalize(c)
            if (n.isNotEmpty()) disabledChannels.add(n)
        }
    }

    private fun effectiveChannels(): Set<String> =
        (EDUCATION_CHANNELS - disabledChannels) + customChannels

    /**
     * AI-chat entry point. Handles the sticky verdict internally:
     * low-signal follow-ups (< 25 chars, no phrase hits) inherit the last
     * confident verdict for this package instead of being re-evaluated —
     * EXCEPT a cached STUDY verdict is never inherited (v1.3.1: inheriting
     * STUDY laundered short casual follow-ups like "acha"/"sahi hai" into
     * study verdicts, hiding real non-study chat). Only NON_STUDY stickies
     * are inherited; everything else is re-scored (UNCERTAIN still goes to
     * the Gemini tie-breaker in the service).
     * Confident verdicts refresh the sticky cache.
     */
    fun scoreChatMessage(message: String, pkg: String): Verdict {
        val norm = normalize(message)
        if (norm.isEmpty()) return Verdict.UNCERTAIN
        val now = System.currentTimeMillis()
        val cached = stickyVerdicts[pkg]
        if (cached != null && cached.first == Verdict.NON_STUDY &&
            now - cached.second <= STICKY_TTL_MS
        ) {
            val padded = " $norm "
            val isFollowUp = norm.length < 25 && !hasAnyPhraseHit(padded) &&
                (FOLLOWUP_HINTS.any { padded.contains(" $it ") } || norm.length <= 10)
            if (isFollowUp) return cached.first
        }
        val verdict = scoreText(norm).verdict
        if (verdict != Verdict.UNCERTAIN) {
            stickyVerdicts[pkg] = verdict to now
            if (stickyVerdicts.size > 60) {
                val cutoff = now - STICKY_TTL_MS
                stickyVerdicts.entries.removeIf { it.value.second < cutoff }
            }
        }
        return verdict
    }

    /**
     * Lets the service feed confident verdicts from other classifiers
     * (on-device keywords, Gemini) into the sticky cache so follow-ups
     * inherit them too.
     */
    fun noteExternalChatVerdict(pkg: String, verdict: Verdict) {
        if (verdict == Verdict.UNCERTAIN || pkg.isBlank()) return
        stickyVerdicts[pkg] = verdict to System.currentTimeMillis()
    }

    private fun hasAnyPhraseHit(padded: String): Boolean {
        for (p in ALLOW) if (padded.contains(" $p ")) return true
        for (p in BLOCK) if (padded.contains(" $p ")) return true
        return false
    }
}
