package com.example.util

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Real-Time Gemini AI Content Safety & Study Intelligence Guard.
 * Leverages Gemini 3.5 Flash via Google AI Studio REST API with sub-second JSON reasoning
 * to detect non-study content, social media scrolling, distracting videos, gaming, and 18+ content
 * in real-time during study sessions, paired with an instant on-device cache and offline fail-safe.
 */
object GeminiContentClassifier {

    private const val TAG = "GeminiClassifier"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    data class ContentVerdict(
        val isStudyAllowed: Boolean,
        val isAdult: Boolean,
        val category: String,
        val reason: String,
        val confidence: Float = 0.95f
    )

    data class ScreenContentVerdict(
        val isStudyAllowed: Boolean,
        val isDistraction: Boolean,
        val isAdult: Boolean,
        val category: String,
        val reason: String,
        val confidence: Float = 0.95f
    )

    // Ultra-fast memory cache for evaluated content (1500 entries)
    private val classificationCache = LruCache<String, ContentVerdict>(1500)
    private val screenCache = LruCache<String, ScreenContentVerdict>(1500)

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    // Known pure entertainment / social media packages that are strictly distractions during study
    private val KNOWN_DISTRACTION_PACKAGES = mapOf(
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.snapchat.android" to "Snapchat",
        "com.tinder" to "Tinder",
        "com.bumble.app" to "Bumble",
        "com.netflix.mediaclient" to "Netflix",
        "tv.twitch.android.app" to "Twitch",
        "com.disney.disneyplus" to "Disney+",
        "in.startv.hotstar" to "Disney+ Hotstar",
        "com.amazon.avod.thirdpartyclient" to "Prime Video",
        "com.crunchyroll.crunchyroid" to "Crunchyroll",
        "com.spotify.music" to "Spotify (Music)",
        "com.pinterest" to "Pinterest",
        "com.supercell.clashofclans" to "Clash of Clans",
        "com.supercell.clashroyale" to "Clash Royale",
        "com.supercell.brawlstars" to "Brawl Stars",
        "com.dts.freefireth" to "Free Fire",
        "com.dts.freefiremax" to "Free Fire Max",
        "com.tencent.ig" to "PUBG Mobile",
        "com.pubg.imobile" to "BGMI",
        "com.roblox.client" to "Roblox",
        "com.mojang.minecraftpe" to "Minecraft",
        "com.kiloo.subwaysurf" to "Subway Surfers",
        "com.king.candycrushsaga" to "Candy Crush Saga"
    )

    // Known verified educational apps
    private val KNOWN_EDUCATIONAL_PACKAGES = mapOf(
        "org.khanacademy.android" to "Khan Academy",
        "org.coursera.android" to "Coursera",
        "org.edx.mobile" to "edX",
        "com.udemy.android" to "Udemy",
        "com.duolingo" to "Duolingo",
        "com.wolfram.android.alpha" to "WolframAlpha",
        "com.microblink.photomath" to "Photomath",
        "com.instructure.candroid" to "Canvas Student",
        "com.blackboard.android.bbmat" to "Blackboard",
        "com.moodle.moodlemobile" to "Moodle",
        "com.quizlet.quizletandroid" to "Quizlet",
        "pw.live" to "Physics Wallah",
        "com.byjus.thelearningapp" to "BYJU'S",
        "com.unacademyapp" to "Unacademy",
        "com.google.android.apps.classroom" to "Google Classroom"
    )

    /**
     * Synchronously checks memory cache or performs instant heuristic on-device evaluation,
     * returning immediate verdict so accessibility service is never blocked.
     */
    fun quickEvaluate(title: String, channel: String? = null, metadata: String? = null): ContentVerdict {
        val cacheKey = buildCacheKey(title, channel)
        val cached = classificationCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        // On-device heuristic evaluation
        val onDeviceVerdict = YouTubeVideoClassifier.evaluate(title, channel, metadata)
        val verdict = ContentVerdict(
            isStudyAllowed = onDeviceVerdict.isEducational,
            isAdult = onDeviceVerdict.category.contains("Adult") || onDeviceVerdict.category.contains("18+"),
            category = onDeviceVerdict.category,
            reason = onDeviceVerdict.reason,
            confidence = onDeviceVerdict.confidence
        )
        return verdict
    }

    /**
     * Instant on-device screen evaluation before invoking network Gemini model.
     */
    fun quickEvaluateScreen(
        packageName: String,
        appName: String,
        screenTitle: String?,
        visibleSnippets: List<String>
    ): ScreenContentVerdict {
        val cacheKey = buildScreenCacheKey(packageName, screenTitle, visibleSnippets)
        val cached = screenCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val pkgLower = packageName.lowercase(Locale.ROOT)

        // 1. Direct match on known pure distraction apps
        if (KNOWN_DISTRACTION_PACKAGES.containsKey(pkgLower)) {
            val appLabel = KNOWN_DISTRACTION_PACKAGES[pkgLower] ?: appName
            val verdict = ScreenContentVerdict(
                isStudyAllowed = false,
                isDistraction = true,
                isAdult = false,
                category = "SOCIAL_MEDIA_ENTERTAINMENT",
                reason = "🔒 $appLabel is locked during study sessions to protect your focus.",
                confidence = 1.0f
            )
            screenCache.put(cacheKey, verdict)
            return verdict
        }

        // 2. Direct match on verified educational apps
        if (KNOWN_EDUCATIONAL_PACKAGES.containsKey(pkgLower)) {
            val appLabel = KNOWN_EDUCATIONAL_PACKAGES[pkgLower] ?: appName
            val verdict = ScreenContentVerdict(
                isStudyAllowed = true,
                isDistraction = false,
                isAdult = false,
                category = "ACADEMIC_STUDY",
                reason = "📚 Verified educational learning app ($appLabel).",
                confidence = 1.0f
            )
            screenCache.put(cacheKey, verdict)
            return verdict
        }

        // 3. Inspect visible text snippets for explicit non-study signals
        val combinedText = buildString {
            if (!screenTitle.isNullOrBlank()) append(screenTitle).append(". ")
            visibleSnippets.forEach { append(it).append(" ") }
        }.trim()

        if (combinedText.isNotBlank()) {
            val heuristic = OnDeviceStudyClassifier.evaluateContent(combinedText)
            if (heuristic.isCasual && heuristic.confidence >= 0.90f) {
                val isAdult = heuristic.reason.contains("18+") || heuristic.reason.contains("Adult") || heuristic.reason.contains("Sensual")
                val verdict = ScreenContentVerdict(
                    isStudyAllowed = false,
                    isDistraction = true,
                    isAdult = isAdult,
                    category = if (isAdult) "ADULT_18_PLUS" else "NON_STUDY_DISTRACTION",
                    reason = heuristic.reason,
                    confidence = heuristic.confidence
                )
                screenCache.put(cacheKey, verdict)
                return verdict
            }
        }

        // Neutral / requires Gemini verification
        return ScreenContentVerdict(
            isStudyAllowed = true,
            isDistraction = false,
            isAdult = false,
            category = "PENDING_VERIFICATION",
            reason = "Awaiting Gemini AI screen analysis",
            confidence = 0.5f
        )
    }

    /**
     * Evaluates full screen content (App, Screen title, visible text snippets, Web URLs)
     * using Gemini 3.5 Flash backend intelligence.
     */
    suspend fun evaluateScreenWithGemini(
        packageName: String,
        appName: String,
        screenTitle: String?,
        visibleSnippets: List<String>,
        metadata: String? = null,
        studySubject: String? = null
    ): ScreenContentVerdict = withContext(Dispatchers.IO) {
        val cacheKey = buildScreenCacheKey(packageName, screenTitle, visibleSnippets)
        val cached = screenCache.get(cacheKey)
        if (cached != null) {
            return@withContext cached
        }

        // 1. Quick check first
        val quick = quickEvaluateScreen(packageName, appName, screenTitle, visibleSnippets)
        if (quick.isDistraction || quick.confidence >= 0.95f) {
            screenCache.put(cacheKey, quick)
            return@withContext quick
        }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "Gemini API key is placeholder or blank. Using on-device screen classifier.")
            screenCache.put(cacheKey, quick)
            return@withContext quick
        }

        try {
            val systemInstruction = """
                You are a strict, ultra-fast real-time AI Study Guard and Screen Content Analyzer for an academic student study session.
                Analyze the provided on-screen content (App, Window Title, UI Text Snippets, Post Headings, Video Titles, Tabs, or Messages) to determine if the student is engaged in ACADEMIC STUDY vs NON-STUDY DISTRACTION.

                Classification Guidelines:
                1. "ACADEMIC_STUDY" (is_study_allowed = true, is_distraction = false):
                   - School/college subjects (Physics, Chemistry, Math, Biology, History, Civics, Economics, Literature)
                   - Coding/programming tutorials, documentation, GitHub, StackOverflow, LeetCode
                   - Exam preparation (JEE, NEET, UPSC, SAT, GRE, MCAT, GCSE, etc.)
                   - Researching educational articles, Wikipedia, academic dictionaries, textbook PDFs, LMS
                   - Legitimate educational doubt solving in AI chats

                2. "NON_STUDY_DISTRACTION" (is_study_allowed = false, is_distraction = true):
                   - Social media feeds, memes, reels, gossip, celebrity updates, influencer drama, viral trends (Instagram, TikTok, Twitter/X, Reddit casual, Facebook, Snapchat)
                   - Entertainment & music videos, movie trailers, gameplay, comedy, pranks, anime, web series (YouTube entertainment, Netflix, Twitch, Disney+, Hotstar)
                   - Video games, betting, esports, game cheats
                   - Non-study web browsing: Online shopping, fashion, gadget reviews, sports scores, casual forum debates
                   - Adult, 18+, NSFW, or romantic chit-chat

                Return ONLY a valid JSON object matching this exact schema:
                {
                  "is_study_allowed": boolean,
                  "is_distraction": boolean,
                  "is_adult": boolean,
                  "category": string ("ACADEMIC_STUDY", "SOCIAL_MEDIA", "ENTERTAINMENT_VIDEO", "GAMING", "NON_STUDY_BROWSING", "ADULT_18_PLUS"),
                  "reason": string (a concise 1-sentence explanation of what was detected and why it is allowed or blocked)
                }
            """.trimIndent()

            val snippetsSummary = visibleSnippets.take(12).joinToString("\n• ") { it.take(160) }
            val userPrompt = buildString {
                appendLine("Foreground App: \"$appName\" ($packageName)")
                if (!screenTitle.isNullOrBlank()) appendLine("Screen/Window Title: \"$screenTitle\"")
                if (!studySubject.isNullOrBlank()) appendLine("Student Current Study Subject: \"$studySubject\"")
                if (!metadata.isNullOrBlank()) appendLine("Metadata/Context: \"$metadata\"")
                appendLine("Visible Screen Text & Content Snippets:")
                if (snippetsSummary.isNotBlank()) {
                    appendLine("• $snippetsSummary")
                } else {
                    appendLine("• (No readable text nodes, viewing app interface)")
                }
            }

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", userPrompt))
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$GEMINI_ENDPOINT?key=$apiKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val respJson = JSONObject(responseBody)
                val candidates = respJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val textPart = parts?.optJSONObject(0)?.optString("text")

                if (!textPart.isNullOrBlank()) {
                    val parsed = JSONObject(textPart.trim())
                    val isStudy = parsed.optBoolean("is_study_allowed", true)
                    val isDistraction = parsed.optBoolean("is_distraction", !isStudy)
                    val isAdult = parsed.optBoolean("is_adult", false)
                    val category = parsed.optString("category", if (isDistraction) "NON_STUDY_DISTRACTION" else "ACADEMIC_STUDY")
                    val reason = parsed.optString("reason", if (isDistraction) "Distracting content detected by Gemini AI on $appName" else "Educational content verified by Gemini AI")

                    val verdict = ScreenContentVerdict(
                        isStudyAllowed = isStudy && !isDistraction && !isAdult,
                        isDistraction = isDistraction || !isStudy || isAdult,
                        isAdult = isAdult,
                        category = category,
                        reason = reason,
                        confidence = 0.98f
                    )
                    screenCache.put(cacheKey, verdict)
                    Log.i(TAG, "[Gemini Screen Verdict] App: $appName -> Distraction=${verdict.isDistraction} ($reason)")
                    return@withContext verdict
                }
            } else {
                Log.w(TAG, "Gemini API screen call failed HTTP ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini API screen evaluation exception: ${e.message}")
        }

        screenCache.put(cacheKey, quick)
        return@withContext quick
    }

    /**
     * Evaluates YouTube video content with Gemini 3.5 Flash intelligence.
     */
    suspend fun evaluateWithGemini(
        title: String,
        channel: String? = null,
        metadata: String? = null
    ): ContentVerdict = withContext(Dispatchers.IO) {
        val cacheKey = buildCacheKey(title, channel)
        val cached = classificationCache.get(cacheKey)
        if (cached != null) {
            return@withContext cached
        }

        // First, check instant high-confidence local rules (e.g. 18+ keyword matches or verified education channels)
        val quick = quickEvaluate(title, channel, metadata)
        if (quick.isAdult || (quick.isStudyAllowed && quick.confidence >= 0.95f)) {
            classificationCache.put(cacheKey, quick)
            return@withContext quick
        }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "Gemini API key is placeholder or blank. Using on-device classifier.")
            classificationCache.put(cacheKey, quick)
            return@withContext quick
        }

        try {
            val systemInstruction = """
                You are a strict real-time AI Study Guard and Content Safety Moderator for a student focus app.
                Classify the given YouTube video / content into one of three categories:
                1. "ADULT_18_PLUS": Any 18+, sexual, NSFW, erotic, sensational, bold/hot scenes, or vulgar content.
                2. "ENTERTAINMENT": Songs, music videos, movies, trailers, vlogs, comedy, standup, gaming/gameplay, web series, gossips, pranks, memes, casual entertainment.
                3. "ACADEMIC_STUDY": School, college, university lectures, exam prep (JEE, NEET, UPSC, SSC, SAT, etc.), coding tutorials, STEM subjects, language learning, history, educational science documentaries.

                Return ONLY a JSON object with this exact schema:
                {
                  "is_study_allowed": boolean (true ONLY if strictly ACADEMIC_STUDY, false otherwise),
                  "is_adult": boolean (true if ADULT_18_PLUS),
                  "category": string,
                  "reason": string (brief 1-sentence reason)
                }
            """.trimIndent()

            val userPrompt = "Video Title: \"$title\"\nChannel: \"${channel ?: "Unknown"}\"\nMetadata: \"${metadata ?: ""}\""

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", userPrompt))
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$GEMINI_ENDPOINT?key=$apiKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val respJson = JSONObject(responseBody)
                val candidates = respJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val textPart = parts?.optJSONObject(0)?.optString("text")

                if (!textPart.isNullOrBlank()) {
                    val parsed = JSONObject(textPart.trim())
                    val isStudy = parsed.optBoolean("is_study_allowed", false)
                    val isAdult = parsed.optBoolean("is_adult", false)
                    val category = parsed.optString("category", if (isStudy) "Academic Study" else "Entertainment")
                    val reason = parsed.optString("reason", if (isStudy) "Educational content verified by Gemini AI" else "Non-study content detected by Gemini AI")

                    val geminiVerdict = ContentVerdict(
                        isStudyAllowed = isStudy && !isAdult,
                        isAdult = isAdult,
                        category = category,
                        reason = reason,
                        confidence = 0.98f
                    )
                    classificationCache.put(cacheKey, geminiVerdict)
                    Log.i(TAG, "[Gemini AI Verdict] Title: '$title' -> Allowed=$isStudy, Adult=$isAdult ($reason)")
                    return@withContext geminiVerdict
                }
            } else {
                Log.w(TAG, "Gemini API call failed with HTTP ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini API classification exception: ${e.message}")
        }

        classificationCache.put(cacheKey, quick)
        return@withContext quick
    }

    private fun buildCacheKey(title: String, channel: String?): String {
        return "${title.trim().lowercase(Locale.ROOT)}|${channel?.trim()?.lowercase(Locale.ROOT) ?: ""}"
    }

    private fun buildScreenCacheKey(
        packageName: String,
        screenTitle: String?,
        snippets: List<String>
    ): String {
        val snippetDigest = snippets.take(5).joinToString("|") { it.trim().lowercase(Locale.ROOT) }.hashCode()
        return "${packageName.lowercase(Locale.ROOT)}|${screenTitle?.trim()?.lowercase(Locale.ROOT) ?: ""}|$snippetDigest"
    }
}
