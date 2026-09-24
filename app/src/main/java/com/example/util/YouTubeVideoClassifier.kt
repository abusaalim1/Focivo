package com.example.util

import android.util.Log
import android.util.LruCache
import java.util.Locale

/**
 * Intelligent Multi-Signal Contextual Classifier for YouTube Videos.
 * Analyzes video title, channel name, metadata, and intent signals to determine
 * whether content is genuinely Educational/Academic or Entertainment/Distraction.
 */
object YouTubeVideoClassifier {

    private const val TAG = "YouTubeClassifier"

    data class VideoVerdict(
        val isEducational: Boolean,
        val confidence: Float,
        val category: String,
        val reason: String
    )

    // In-memory LRU Cache for evaluated video titles/channels (up to 500 recent items)
    private val verdictCache = LruCache<String, VideoVerdict>(500)

    // Explicit 18+ / Adult / NSFW / Inappropriate Markers (Maximum penalty - zero tolerance)
    private val ADULT_AND_NSFW_KEYWORDS = listOf(
        "18+", "18 plus", "adult", "nsfw", "sexy", "hot scene", "bold scene", "kiss scene", "kissing scene",
        "love making", "bed scene", "romance scene", "bikini", "cleavage", "exposed", "erotic", "porn",
        "xvideo", "xxx", "sex", "nude", "nudity", "hot girl", "hot model", "viral mms", "leaked mms",
        "web series 18+", "ullu", "kooku", "rabbit", "altbalaji", "primeplay", "hotshots", "uncensored",
        "dirty talk", "bhabhi", "devar bhabhi", "hot photoshoot", "navel", "bathing scene",
        "intimate scene", "steamy scene", "sensual", "hot dance", "strip", "onlyfans", "boobs", "breast",
        "erotica", "lust", "desire", "item song hot", "bold photoshoot", "hot reels", "erotic thriller",
        "romantic hot", "bold web series", "hot song", "sensual romance", "adult movie", "adult video",
        "hot clip", "adult joke", "dirty jokes", "bold actress", "hot actress", "exposed video"
    )

    // Explicit Entertainment and Distraction Markers (Strong negative penalty)
    private val ENTERTAINMENT_KEYWORDS = listOf(
        // Music & Songs
        "official music video", "official video song", "lyric video", "lyrics video", "audio song",
        "full song", "remix song", "lofi remix", "slowed and reverb", "dj remix", "party song",
        "romantic song", "sad song", "punjabi song", "bollywood song", "bhojpuri song",
        "album song", "music video", "t-series", "zee music", "speed records", "sonymusic",
        "arijit singh", "neha kakkar", "badshah", "honey singh", "karan aujla", "shubh",
        "sidhu moose wala", "diljit dosanjh", "taylor swift", "justin bieber", "k-pop", "bts",
        "blackpink", "drake", "eminem", "ariana grande", "billie eilish",

        // Movies, Shows, Trailers & Drama
        "full movie", "full hindi movie", "south movie in hindi", "movie scene", "trailer",
        "official trailer", "teaser", "first look", "web series episode", "full episode",
        "episode 1", "episode 2", "episode 3", "ott release", "box office collection",
        "film review", "movie review", "movie explained", "action scene", "climax scene",
        "netflix india", "prime video", "disney+ hotstar", "hotstar specials", "alt balaji",

        // Comedy, Roasts, Pranks & Memes
        "stand up comedy", "stand-up comedy", "roast video", "roasting", "prank video",
        "funny prank", "prank on", "meme review", "dank memes", "funny moments", "try not to laugh",
        "laughter challenge", "comedy video", "funny video", "chutkule", "hasne wala video",
        "carryminati", "ashish chanchlani", "bb ki vines", "round2hell", "amit bhadana",
        "harsh beniwal", "zakir khan", "anubhav singh bassi", "samay raina", "tanmay bhat comedy",

        // Vlogs & Lifestyle Entertainment
        "vlog", "daily vlog", "travel vlog", "family vlog", "lifestyle vlog", "my daily routine vlog",
        "college vlog", "funny vlog", "shopping haul", "room tour", "house tour", "car collection tour",
        "expensive shopping", "wedding vlog", "birthday celebration vlog", "flying beast", "sourav joshi vlogs",
        "gaurav taneja", "mumbiker nikhil", "armann malik vlogs", "uk07 rider", "elvis yadav",
        "elvish yadav vlogs", "munawar faruqui vlog", "big boss", "bigg boss", "lock upp",

        // Gaming & Esports Entertainment
        "gameplay walkthrough", "gameplay hindi", "funny gameplay", "gta 5 gameplay", "gta 5 funny",
        "gta 5 thug life", "bgmi live", "bgmi gameplay", "free fire live", "free fire gameplay",
        "free fire headshot montage", "minecraft survival hindi", "minecraft hard mode", "roblox funny",
        "total gaming", "techno gamerz", "mortal live", "scout live", "dynamo gaming", "mythpat",
        "live stream gameplay", "custom room bgmi", "rank push gameplay", "esports highlights",

        // Gossip, Reality TV & Casual Distraction
        "celebrity gossip", "bollywood news", "shocking revelation", "paparazzi", "airport look",
        "fashion show", "ramp walk", "dance cover", "dance performance", "dance tutorial",
        "zumba dance", "tiktok viral", "instagram reels viral", "trending reels audio", "viral dance"
    )

    // Explicit Educational, Academic & STEM Signals (Strong positive reward)
    private val ACADEMIC_KEYWORDS = listOf(
        // Lecture & Academic structure
        "lecture", "full chapter", "one shot", "one-shot", "detailed lecture", "crash course",
        "revision class", "full syllabus", "syllabus discussion", "marathon class", "complete course",
        "chapter summary", "class 12", "class 11", "class 10", "class 9", "class 8", "class 7", "class 6",
        "cbse class", "icse class", "state board", "ncert line by line", "ncert solutions",
        "exercise solution", "in-text questions", "previous year questions", "pyq series", "pyqs",
        "sample paper discussion", "mock test solution", "derivations", "formula revision",
        "important questions", "concept explanation", "mind map", "roadmap",

        // Competitive Exams
        "jee main", "jee advanced", "neet 2024", "neet 2025", "neet 2026", "neet 2027", "jee 2025",
        "jee 2026", "jee 2027", "upsc cse", "upsc prelims", "upsc mains", "ias preparation",
        "ssc cgl", "ssc chsl", "ssc gd", "ssc mts", "nda exam", "cds exam", "gate cse",
        "gate ece", "gate mechanical", "cat exam", "cat quant", "clat preparation", "banking exam",
        "ibps po", "sbi po", "ca foundation", "ca intermediate", "ugc net", "cuet ug", "cuet pg",

        // STEM Subjects & Topics
        "physics", "chemistry", "mathematics", "maths", "biology", "zoology", "botany",
        "electrostatics", "current electricity", "magnetism", "optics", "thermodynamics",
        "kinematics", "rotational motion", "gravitation", "quantum mechanics", "organic chemistry",
        "inorganic chemistry", "physical chemistry", "chemical bonding", "hydrocarbons", "coordination compounds",
        "calculus", "differential equations", "integration", "differentiation", "matrices and determinants",
        "trigonometry", "coordinate geometry", "probability", "statistics", "vectors and 3d geometry",
        "photosynthesis", "genetics and evolution", "biotechnology", "human reproduction", "cell biology",

        // Computer Science & Programming
        "data structures and algorithms", "dsa", "leetcode", "leetcode solution", "coding interview",
        "system design", "machine learning", "deep learning", "artificial intelligence", "web development",
        "android development", "flutter tutorial", "react js tutorial", "next js tutorial", "python tutorial",
        "java full course", "c++ full course", "kotlin tutorial", "sql tutorial", "database management",
        "operating systems", "computer networks", "compiler design", "backend engineering", "frontend tutorial",
        "api development", "git and github", "docker tutorial", "devops full course",

        // Humanities, Languages & Knowledge
        "indian polity", "m laxmikanth", "modern indian history", "spectrum history", "ancient history",
        "world history", "geography lectures", "indian economy", "environment and ecology",
        "english grammar", "active and passive voice", "direct and indirect speech", "tenses in english",
        "ielts preparation", "toefl preparation", "spoken english lesson", "vocabulary builder"
    )

    /**
     * Contextually classifies a YouTube video.
     */
    fun evaluate(
        title: String,
        channelName: String? = null,
        metadata: String? = null
    ): VideoVerdict {
        val cacheKey = "${title.trim().lowercase()}|${channelName?.trim()?.lowercase() ?: ""}"
        val cached = verdictCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val verdict = computeVerdict(title, channelName, metadata)
        verdictCache.put(cacheKey, verdict)
        return verdict
    }

    private fun computeVerdict(
        title: String,
        channelName: String?,
        metadata: String?
    ): VideoVerdict {
        val cleanTitle = title.lowercase(Locale.ROOT).trim()
        val cleanChannel = channelName?.lowercase(Locale.ROOT)?.trim() ?: ""
        val cleanMeta = metadata?.lowercase(Locale.ROOT)?.trim() ?: ""
        val fullCombined = "$cleanTitle $cleanChannel $cleanMeta"

        // 0. Zero Tolerance Check: 18+ / Adult / NSFW / Sensual Content
        for (adultKey in ADULT_AND_NSFW_KEYWORDS) {
            if (fullCombined.contains(adultKey)) {
                return VideoVerdict(
                    isEducational = false,
                    confidence = 1.0f,
                    category = "🔞 Adult & 18+ Content Blocked",
                    reason = "Explicit adult or inappropriate content detected ($adultKey)"
                )
            }
        }
        if (cleanTitle.contains(Regex("""\b(18\+|adult|porn|xxx|xvideos|nude|nudity|erotic|sex|sexy|hot\s+scenes?|kiss(ing)?\s+scenes?|love\s+making|intimate\s+scenes?|ullu|kooku|altbalaji|hotshots|uncensored)\b"""))) {
            return VideoVerdict(
                isEducational = false,
                confidence = 1.0f,
                category = "🔞 Adult & 18+ Content Blocked",
                reason = "Adult / 18+ restricted pattern detected"
            )
        }

        // 1. Check if channel is recognized trusted educational platform
        val isTrustedChannel = TrustedEducationalChannels.isKnownEducationalChannel(cleanChannel)

        // 2. Count Academic Indicators
        var academicScore = 0.0f
        var academicMatches = mutableListOf<String>()

        for (keyword in ACADEMIC_KEYWORDS) {
            if (fullCombined.contains(keyword)) {
                academicScore += 0.35f
                academicMatches.add(keyword)
            }
        }

        // Additional Regex detection for class / chapter / subject patterns
        if (cleanTitle.contains(Regex("""\b(class\s+[0-9]{1,2}|ch(\.|\s+)[0-9]{1,2}|chapter\s+[0-9]{1,2}|exercise\s+[0-9]{1,2})\b"""))) {
            academicScore += 0.40f
            academicMatches.add("structured_chapter_format")
        }
        if (cleanTitle.contains(Regex("""\b(jee|neet|upsc|ssc|gate|cat|clat|nda|cbse|ncert|pyq|pyqs|dsa|leetcode)\b"""))) {
            academicScore += 0.45f
            academicMatches.add("exam_or_dsa_tag")
        }

        // 3. Count Entertainment & Distraction Indicators
        var entertainmentScore = 0.0f
        var entertainmentMatches = mutableListOf<String>()

        for (keyword in ENTERTAINMENT_KEYWORDS) {
            if (fullCombined.contains(keyword)) {
                entertainmentScore += 0.50f
                entertainmentMatches.add(keyword)
            }
        }

        // Additional entertainment regex patterns (song, vlog, gameplay, roast)
        if (cleanTitle.contains(Regex("""\b(song|music|dance|vlog|roast|prank|movie|trailer|teaser|bgmi|gameplay|funny)\b"""))) {
            entertainmentScore += 0.40f
            entertainmentMatches.add("entertainment_pattern")
        }

        // 4. Combine Signals with Priority Logic
        // Case A: Strong Entertainment Signal (Even if posted by academic creator, e.g. "My College Vlog" or "Car Unboxing")
        if (entertainmentScore >= 0.70f && academicScore < 0.50f) {
            return VideoVerdict(
                isEducational = false,
                confidence = 0.95f,
                category = "Entertainment / Distraction",
                reason = "Identified as entertainment/music/vlog content (${entertainmentMatches.take(2).joinToString()})"
            )
        }

        // Case B: Explicit Vlog or Song Title Pattern
        if (cleanTitle.contains("vlog") || cleanTitle.contains("song") || cleanTitle.contains("official video") || cleanTitle.contains("gameplay")) {
            // Even if academic keyword is falsely present, e.g. "Funny College Life Vlog"
            if (!cleanTitle.contains("lecture") && !cleanTitle.contains("chapter") && !cleanTitle.contains("tutorial") && !cleanTitle.contains("ncert")) {
                return VideoVerdict(
                    isEducational = false,
                    confidence = 0.90f,
                    category = "Entertainment Vlog / Music",
                    reason = "Vlog, music, or gameplay detected"
                )
            }
        }

        // Case C: Strong Academic Signal
        if (academicScore >= 0.40f && entertainmentScore <= 0.25f) {
            val category = if (isTrustedChannel) "Verified Academic Lecture" else "Educational / Study Content"
            return VideoVerdict(
                isEducational = true,
                confidence = 0.92f + (if (isTrustedChannel) 0.06f else 0.0f),
                category = category,
                reason = "Academic / exam / tutorial concepts detected (${academicMatches.take(3).joinToString()})"
            )
        }

        // Case D: Trusted Channel with Neutral or Positive Title
        if (isTrustedChannel && entertainmentScore <= 0.20f) {
            return VideoVerdict(
                isEducational = true,
                confidence = 0.88f,
                category = "Trusted Education Channel",
                reason = "Published by recognized education platform ($cleanChannel)"
            )
        }

        // Case E: Moderate Academic Signals
        if (academicScore >= 0.30f && entertainmentScore == 0.0f) {
            return VideoVerdict(
                isEducational = true,
                confidence = 0.80f,
                category = "Educational Content",
                reason = "Educational topic detected (${academicMatches.joinToString()})"
            )
        }

        // Case F: FAIL-SAFE LOCK RULE
        // If content cannot be confirmed as genuinely educational with high confidence,
        // lock it as review-required/distraction to protect study time.
        return VideoVerdict(
            isEducational = false,
            confidence = 0.75f,
            category = "Non-Educational / Review Required",
            reason = "Content does not meet educational criteria during active study session"
        )
    }

    /**
     * Pre-populates or overrides verdict in cache (e.g. from Gemini AI backend analysis).
     */
    fun cacheVerdict(title: String, channelName: String?, verdict: VideoVerdict) {
        val cacheKey = "${title.trim().lowercase()}|${channelName?.trim()?.lowercase() ?: ""}"
        verdictCache.put(cacheKey, verdict)
    }
}
