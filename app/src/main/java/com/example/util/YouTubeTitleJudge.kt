package com.example.util

/**
 * v1.3.3 (ITEM 2 — YouTube guard over-blocking fix, owner order 2026-10-18).
 *
 * The PURE title-classification pipeline for the YouTube AI content filter.
 * Evaluation order is UNCHANGED from the old
 * AiStudyAccessibilityService.processYouTubeVideoTitle():
 *   1. user's channel BLOCKLIST  -> immediate Block (outranks everything)
 *   2. user's channel allowlist  -> Allow (never verdict-scored)
 *   3. combined brain (repo channels -> user corrections -> DOUBT GATE ->
 *      combined repo+TF-IDF score -> bands) -> Block / Allow / Uncertain
 *   4. offline knowledge pack    -> STUDY -> Allow; confident NON_STUDY
 *      (score <= -4.0) -> Block; weak single hits fall through
 *   5. quick on-device verdict   -> confident (conf >= 0.90) non-study ->
 *      Block; otherwise NeedsArbiter (Gemini tail or fail-open, decided by
 *      the caller)
 *
 * DOUBT-GATE SEMANTICS (preserved): the doubt shield lives inside
 * FocivoCombinedBrain — a doubt phrasing can never come back as BLOCK from
 * the brain. As defense-in-depth this judge ALSO refuses a Block whose
 * brain tag is "doubt" and treats it as Uncertain.
 *
 * OFF-BY-DEFAULT toggle semantics are preserved by the caller
 * (AiStudyAccessibilityService section 7B): the judge is only ever invoked
 * when YouTubeStudyGuardManager.isFilterEnabled() is true; the orchestrator
 * returns Action.Inert otherwise.
 *
 * Android-free: all platform access (prefs, brain bundles) is injected via
 * [Deps], so the full pipeline is unit-testable on the JVM.
 */
object YouTubeTitleJudge {

    enum class BrainDecision { BLOCK, ALLOW, UNCERTAIN }
    data class BrainVerdict(val decision: BrainDecision, val tag: String, val pCombined: Double?)

    enum class KnowledgeDecision { STUDY, NON_STUDY, UNCERTAIN }
    data class KnowledgeVerdict(val decision: KnowledgeDecision, val score: Double, val evidence: String)

    data class QuickVerdict(
        val isStudyAllowed: Boolean,
        val isAdult: Boolean,
        val confidence: Float,
        val reason: String,
        val category: String
    )

    sealed interface Outcome {
        /** Enforcement-worthy. viaBlocklist = the user's own channel blocklist fired. */
        data class Block(val reason: String, val viaBlocklist: Boolean) : Outcome
        data class Allow(val reason: String) : Outcome
        /** Sync pipeline uncertain — caller may run the Gemini tail (playback path) or observe quietly. */
        data class NeedsArbiter(val detail: String) : Outcome
    }

    /** One pipeline stage's contribution, for Guard Hub trace logging. */
    data class StageLog(val stage: String, val verdict: String, val detail: String)

    data class Result(
        val outcome: Outcome,
        val trace: List<StageLog>,
        /** Combined-brain tag ("doubt" | "correction" | "repo-channel" | "combined" | "error"). */
        val brainTag: String,
        val brainP: Double?
    )

    interface Deps {
        fun isChannelBlocklisted(channel: String?): Boolean
        fun isChannelAllowlisted(channel: String?): Boolean
        fun judgeBrain(title: String, channel: String?): BrainVerdict
        fun scoreKnowledge(title: String, channel: String?): KnowledgeVerdict
        fun quickEvaluate(title: String, channel: String?, metadata: String?): QuickVerdict
    }

    fun judge(title: String, channel: String?, metadata: String?, deps: Deps): Result {
        val trace = mutableListOf<StageLog>()

        // 1. User's channel BLOCKLIST beats everything.
        if (deps.isChannelBlocklisted(channel)) {
            trace.add(StageLog("youtube-blocklist", "NON_STUDY", "channel-blocklist"))
            return Result(
                Outcome.Block("Blocked channel: ${channel?.trim()} is on your blocked-channels list.", viaBlocklist = true),
                trace, brainTag = "", brainP = null
            )
        }

        // 2. User's study-channel allowlist always wins.
        if (deps.isChannelAllowlisted(channel)) {
            trace.add(StageLog("youtube-allowlist", "STUDY", "allowlisted-channel"))
            return Result(Outcome.Allow("allowlisted-channel"), trace, brainTag = "", brainP = null)
        }

        // 3. Combined on-device brain (doubt gate lives inside it).
        val brain = deps.judgeBrain(title, channel)
        val pStr = brain.pCombined?.let { "p=%.3f".format(java.util.Locale.ROOT, it) } ?: "no-score"
        val isDoubt = brain.tag == "doubt"
        when {
            // Defense-in-depth: even if a brain implementation ever returned
            // BLOCK for a doubt phrasing, the judge refuses to block it.
            brain.decision == BrainDecision.BLOCK && !isDoubt -> {
                trace.add(StageLog("youtube-local", "NON_STUDY", "combined-brain $pStr [${brain.tag}]"))
                return Result(
                    Outcome.Block("Non-study content detected: \"$title\" [combined-brain $pStr]", viaBlocklist = false),
                    trace, brainTag = brain.tag, brainP = brain.pCombined
                )
            }
            brain.decision == BrainDecision.ALLOW -> {
                trace.add(StageLog("youtube-local", "STUDY", "combined-brain $pStr [${brain.tag}]"))
                return Result(Outcome.Allow("combined-brain $pStr [${brain.tag}]"), trace, brainTag = brain.tag, brainP = brain.pCombined)
            }
            else -> {
                // UNCERTAIN, or BLOCK-with-doubt-tag (never blocks) -> fall through.
                val detail = if (isDoubt) "doubt→existing-chain" else "combined-brain $pStr->existing-chain"
                trace.add(StageLog("youtube-local", "UNCERTAIN", detail))
            }
        }

        // 4. Curated offline knowledge pack.
        val knowledge = deps.scoreKnowledge(title, channel)
        when (knowledge.decision) {
            KnowledgeDecision.STUDY -> {
                trace.add(StageLog("youtube-knowledge", "STUDY", knowledge.evidence))
                return Result(Outcome.Allow(knowledge.evidence), trace, brainTag = brain.tag, brainP = brain.pCombined)
            }
            KnowledgeDecision.NON_STUDY -> {
                if (knowledge.score <= -4.0) {
                    trace.add(StageLog("youtube-knowledge", "NON_STUDY", knowledge.evidence))
                    return Result(
                        Outcome.Block("Non-study content detected: \"$title\" [${knowledge.evidence}]", viaBlocklist = false),
                        trace, brainTag = brain.tag, brainP = brain.pCombined
                    )
                }
                // Weak single-word hit -> treat as uncertain, fall through.
                trace.add(StageLog("youtube-knowledge", "UNCERTAIN", "weak(${knowledge.evidence})→quick"))
            }
            KnowledgeDecision.UNCERTAIN -> { /* fall through */ }
        }

        // 5. Quick on-device verdict — blocks only when CONFIDENT.
        val quick = deps.quickEvaluate(title, channel, metadata)
        val isConfidentQuickBlock = (!quick.isStudyAllowed || quick.isAdult) && quick.confidence >= 0.90f
        if (isConfidentQuickBlock) {
            trace.add(StageLog("youtube-quick", "NON_STUDY", "conf=${quick.confidence} " + quick.category.ifBlank { "quick" }))
            return Result(
                Outcome.Block(quick.reason, viaBlocklist = false),
                trace, brainTag = brain.tag, brainP = brain.pCombined
            )
        }
        trace.add(StageLog("youtube-quick", "UNCERTAIN", "quick-allow→gemini"))
        return Result(Outcome.NeedsArbiter("quick-allow→gemini"), trace, brainTag = brain.tag, brainP = brain.pCombined)
    }
}
