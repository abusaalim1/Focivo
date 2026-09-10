package com.example.util

import java.util.Locale

object OnDeviceStudyClassifier {

    private val EDUCATIONAL_KEYWORDS = setOf(
        "physics wallah", "pw", "unacademy", "khan academy", "ncert", "vedantu", "byju", "cbse", "icse",
        "jee", "neet", "upsc", "gate", "ssc", "cat", "nda", "ias", "lecture", "one shot", "marathon",
        "revision", "sample paper", "pyq", "previous year", "solve", "equation", "formula", "calculus",
        "derivative", "integral", "physics", "chemistry", "biology", "mathematics", "math", "algebra",
        "geometry", "algorithm", "function", "variable", "code", "compile", "debug", "theorem", "proof",
        "syllabus", "homework", "assignment", "textbook", "chapter", "exam", "test", "quiz", "definition",
        "explain", "concept", "theory", "summary", "analysis", "grammar", "vocabulary", "history",
        "geography", "economics", "organic", "inorganic", "mechanics", "thermodynamics", "optics",
        "quantum", "molecule", "reaction", "acid", "base", "photosynthesis", "genetics", "evolution",
        "cell", "mitosis", "meiosis", "syntax", "complexity", "data structure", "database", "sql",
        "query", "lecture", "question", "solution", "answer", "doubt", "clarify", "study", "revise",
        "revision", "geeksforgeeks", "stackoverflow", "w3schools", "tutorialspoint", "chatgpt", "claude",
        "gemini", "mdn", "github", "arxiv", "wikipedia", "course", "tutorial", "documentation"
    )

    private val NON_STUDY_KEYWORDS = setOf(
        "shorts", "#shorts", "reels", "tiktok", "vlog", "vlogs", "roast", "roasting", "funny", "prank",
        "challenge", "gaming", "gameplay", "pubg", "bgmi", "freefire", "gta", "minecraft", "roblox",
        "valorant", "stream", "live stream", "song", "songs", "music", "dance", "movie", "movies",
        "film", "trailer", "teaser", "web series", "episode", "season", "serial", "drama", "anime",
        "manga", "gossip", "celebrity", "unboxing", "review", "fashion", "shopping", "flipkart",
        "amazon", "myntra", "meesho", "instagram", "facebook", "twitter", "x.com", "reddit",
        "pinterest", "snapchat", "disney", "hotstar", "zee5", "jiocinema", "prime video", "netflix",
        "match", "ipl", "cricket score", "highlights", "status", "whatsapp status", "flirt", "crush",
        "dating", "girlfriend", "boyfriend", "roleplay", "fanfic", "horoscope", "zodiac", "astrology",
        "bored", "love", "game", "tea", "idle", "pickup", "story", "poem", "clothes", "outfit", "recipe",
        "cooking", "restaurant", "meme", "jokes", "roast me", "tell a joke", "love advice", "spill the tea"
    )

    fun evaluateContent(text: String): ClassificationResult {
        if (text.isBlank()) return ClassificationResult(isCasual = false, confidence = 0f, reason = "Empty")

        val lower = text.lowercase(Locale.ROOT)

        // Direct check for YouTube Shorts
        if (lower.contains("shorts") || lower.contains("#shorts") || lower.contains("/shorts/")) {
            return ClassificationResult(
                isCasual = true,
                confidence = 1.0f,
                reason = "YouTube Shorts detected during active study schedule"
            )
        }

        val words = lower.split(Regex("[\\s,.;:!?\"'()\\[\\]{}]+")).filter { it.isNotBlank() }

        var studyScore = 0
        var casualScore = 0

        val foundStudyKeywords = mutableListOf<String>()
        val foundCasualKeywords = mutableListOf<String>()

        for (word in words) {
            if (EDUCATIONAL_KEYWORDS.contains(word)) {
                studyScore++
                foundStudyKeywords.add(word)
            }
            if (NON_STUDY_KEYWORDS.contains(word)) {
                casualScore++
                foundCasualKeywords.add(word)
            }
        }

        // Multi-word phrase checks
        for (phrase in NON_STUDY_KEYWORDS) {
            if (phrase.contains(" ") && lower.contains(phrase)) {
                casualScore += 2
                foundCasualKeywords.add(phrase)
            }
        }

        for (phrase in EDUCATIONAL_KEYWORDS) {
            if (phrase.contains(" ") && lower.contains(phrase)) {
                studyScore += 2
                foundStudyKeywords.add(phrase)
            }
        }

        val isCasual = (casualScore >= 1 && studyScore == 0) || (casualScore > studyScore)

        return ClassificationResult(
            isCasual = isCasual,
            confidence = if (isCasual) (casualScore.toFloat() / (casualScore + studyScore + 1)).coerceIn(0.6f, 1f) else 0f,
            reason = if (isCasual) "Non-study content detected: ${foundCasualKeywords.distinct().take(3).joinToString(", ")}" else "Study related content"
        )
    }

    data class ClassificationResult(
        val isCasual: Boolean,
        val confidence: Float,
        val reason: String
    )
}
