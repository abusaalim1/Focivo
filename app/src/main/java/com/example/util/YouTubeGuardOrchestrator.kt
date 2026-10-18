package com.example.util

/**
 * v1.3.3 (ITEM 2 — YouTube guard over-blocking fix, owner order 2026-10-18).
 *
 * PURE routing between the playback gate ([YouTubePlaybackGate]), the
 * title pipeline ([YouTubeTitleJudge]) and the single enforcement choke
 * point in AiStudyAccessibilityService.enforceIfPlayingYouTube().
 *
 * Guarantees (all unit-tested in YouTubePlaybackGateTest):
 * - Browsing surfaces (home / search / subscriptions / channel pages —
 *   gate surfaces NONE / INLINE_PREVIEW) can only ever produce
 *   Action.Observe / Action.Allowed: OBSERVED entries for the Guard Hub.
 *   The block overlay is UNREACHABLE from these paths.
 * - Action.Enforce is produced ONLY when mayEnforce() is true (real
 *   player surface + playback in progress) AND the pipeline verdict is
 *   Block. The service's choke point is the ONLY function that performs
 *   enforcement, and it is called only from the Enforce / Gemini-tail
 *   branches — grep "enforceIfPlayingYouTube" to prove it.
 * - The OFF-by-default "Educational Whitelist" AI-filter toggle is
 *   preserved: filterEnabled=false -> Action.Inert, the judge is never
 *   even consulted.
 *
 * PRE-CLASSIFICATION CACHE: feed titles are judged quietly on the Observe
 * path and cached (100 entries, 5-minute TTL) so that when playback of the
 * same video starts, the verdict is instantly available and the Guard Hub
 * can show the full trace. The cache is best-effort: a miss just re-runs
 * the (<2ms) on-device pipeline.
 */
object YouTubeGuardOrchestrator {

    data class ActiveVideo(
        val title: String,
        val channel: String?,
        val metadata: String?,
        /** Extraction provenance for the hub ("watch-screen" | "miniplayer/shorts" | "fallback-scan"). */
        val source: String
    )

    /** Gemini tail result (async re-entry only). */
    enum class Arbiter { NONE, NON_STUDY, STUDY, UNAVAILABLE }

    data class Inputs(
        val isYouTubeForeground: Boolean,
        val isStudyPeriod: Boolean,
        val filterEnabled: Boolean,
        val signals: YtPlaybackSignals,
        val video: ActiveVideo?,
        val judge: YouTubeTitleJudge.Deps,
        val arbiter: Arbiter = Arbiter.NONE,
        /** Arbiter-supplied reason (Gemini's own reason text), used for Enforce. */
        val arbiterReason: String? = null
    )

    sealed interface Action {
        /** Toggle off / not study period / not YouTube: the filter is fully inert. */
        object Inert : Action
        /** Browse surface: judged quietly for the hub + cache; enforcement unreachable. */
        data class Observe(val video: ActiveVideo, val result: YouTubeTitleJudge.Result) : Action
        /** Playing video allowed: quiet hub entry only. */
        data class Allowed(val video: ActiveVideo, val result: YouTubeTitleJudge.Result) : Action
        /** THE only action that may reach the enforcement choke point. */
        data class Enforce(
            val video: ActiveVideo,
            val reason: String,
            val viaBlocklist: Boolean,
            val result: YouTubeTitleJudge.Result
        ) : Action
        /** Playing video, sync pipeline uncertain: caller launches the Gemini tail. */
        data class AwaitGemini(val video: ActiveVideo) : Action
        /** Nothing title-like on screen: fail-open. */
        object NoVideo : Action
    }

    fun handle(i: Inputs): Action {
        if (!i.isYouTubeForeground || !i.isStudyPeriod || !i.filterEnabled) return Action.Inert
        val video = i.video ?: return Action.NoVideo
        val enforceable = YouTubePlaybackGate.mayEnforce(i.signals)

        // Gemini arbiter re-entry: the sync pipeline already ran; the
        // arbiter only overrides an UNCERTAIN sync verdict.
        if (i.arbiter != Arbiter.NONE) {
            val cached = YouTubeVerdictCache.get(cacheKey(video))
            return when (i.arbiter) {
                Arbiter.NON_STUDY -> if (enforceable) {
                    Action.Enforce(
                        video,
                        i.arbiterReason ?: "Non-study content detected: \"${video.title}\"",
                        viaBlocklist = false,
                        result = cached ?: syntheticAllow("gemini-nonstudy")
                    )
                } else {
                    Action.Observe(video, cached ?: syntheticAllow("gemini-nonstudy-but-gate-closed"))
                }
                Arbiter.STUDY, Arbiter.UNAVAILABLE ->
                    Action.Allowed(video, cached ?: syntheticAllow("gemini-allow"))
                else -> Action.Inert
            }
        }

        val result = YouTubeVerdictCache.get(cacheKey(video))
            ?: YouTubeTitleJudge.judge(video.title, video.channel, video.metadata, i.judge)
                .also { YouTubeVerdictCache.put(cacheKey(video), it) }

        return when (val outcome = result.outcome) {
            is YouTubeTitleJudge.Outcome.Block ->
                if (enforceable) Action.Enforce(video, outcome.reason, outcome.viaBlocklist, result)
                else Action.Observe(video, result)
            is YouTubeTitleJudge.Outcome.Allow -> Action.Allowed(video, result)
            is YouTubeTitleJudge.Outcome.NeedsArbiter ->
                if (enforceable) Action.AwaitGemini(video)
                else Action.Observe(video, result)
        }
    }

    private fun cacheKey(video: ActiveVideo): String = "${video.title}|${video.channel.orEmpty()}"

    private fun syntheticAllow(detail: String): YouTubeTitleJudge.Result =
        YouTubeTitleJudge.Result(YouTubeTitleJudge.Outcome.Allow(detail), emptyList(), brainTag = "", brainP = null)

    /** Exposed for the service's hub "observed" entries. */
    fun outcomeName(result: YouTubeTitleJudge.Result): String = when (result.outcome) {
        is YouTubeTitleJudge.Outcome.Block -> "BLOCK"
        is YouTubeTitleJudge.Outcome.Allow -> "ALLOW"
        is YouTubeTitleJudge.Outcome.NeedsArbiter -> "UNCERTAIN"
    }
}

/**
 * Tiny bounded pre-classification cache for YouTube verdicts.
 * Keyed by "title|channel"; 100 entries max (oldest evicted), 5-minute TTL.
 */
object YouTubeVerdictCache {
    private const val MAX_ENTRIES = 100
    private const val TTL_MS = 5 * 60 * 1000L

    private data class Entry(val result: YouTubeTitleJudge.Result, val atMs: Long)
    private val map = LinkedHashMap<String, Entry>()

    @Synchronized
    fun get(key: String): YouTubeTitleJudge.Result? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.atMs > TTL_MS) {
            map.remove(key)
            return null
        }
        return e.result
    }

    @Synchronized
    fun put(key: String, result: YouTubeTitleJudge.Result) {
        map[key] = Entry(result, System.currentTimeMillis())
        while (map.size > MAX_ENTRIES) {
            map.remove(map.keys.first())
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}
