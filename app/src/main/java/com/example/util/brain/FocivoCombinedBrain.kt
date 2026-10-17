package com.example.util.brain

import android.content.Context
import android.util.Log
import com.example.util.LocalTitleClassifier
import com.example.util.OnDeviceStudyClassifier
import java.util.Locale

/**
 * Worker E (v1.3.0): the enhanced on-device judge.
 *
 * Combines the ported Focivo repo brain (4 layers: channel lists, 354
 * weighted cue rules, 33k-weight ML model, user corrections) with the
 * existing v3 TF-IDF brains, then applies the app's own verdict bands.
 *
 * Evaluation order (owner order 2026-10-17):
 *   1. channels — the USER's own lists (BlockedChannelsManager /
 *      StudyAllowlistManager) run BEFORE this judge in the service and
 *      always win; the repo's channels.json (397 blocked / 463 study) is
 *      the fallback here (blocked -> BLOCK, study -> logit bonus).
 *   2. corrections — the user's own override, checked FIRST inside the
 *      engine score (0.97 / 0.03), exactly like the Python feedback map.
 *   3. doubt gate — regional not-understanding phrasings can NEVER block.
 *   4. combined score — repo brain + existing v3 brain -> our bands.
 *
 * COMBINATION FORMULA (exact):
 *   p_repo = P_study from the repo engine
 *            (ML + lexicon + code-hints + channel bonus, in logit space:
 *             z = logit(p_ml)*0.8 + clamp(rule,-5,5) + extra_logit)
 *   p_v3   = 1 - p_entertainment from the v3 TF-IDF brain
 *   p_combined = 0.5 * p_repo + 0.5 * p_v3
 *   (single-brain fallback when the other bundle is missing; UNCERTAIN
 *   when neither can score)
 *
 * Justification: both brains estimate the same quantity, P(study), and both
 * are logistic outputs calibrated around a 0.5 decision boundary with
 * comparable held-out accuracy (v3 titles 99.73%; repo brain 95.7% blind /
 * 98% held-out). No labelled meta-corpus exists to fit optimal weights, so
 * the equal-weight average is the standard variance-reducing default — and
 * it is transparently explainable in the Guard Hub log
 * ("p=0.5*repo+0.5*v3").
 *
 * BANDS (the app's own — the repo's 0.62/0.38 cutoffs are used only for the
 * engine's explanatory label, never for the block decision):
 *   titles:  p >= 0.80 -> ALLOW   (mirrors p_ent <= 0.20)
 *            p <= 0.35 -> BLOCK    (mirrors p_ent >= 0.65; high-confidence only)
 *            else      -> UNCERTAIN -> Gemini arbiter if a key exists, else ALLOW
 *   screen:  p >= 0.80 -> ALLOW   (mirrors p_ent < 0.20)
 *            p <= 0.31 -> BLOCK    (mirrors p_ent >= 0.69; high-confidence only)
 *            else      -> UNCERTAIN -> Gemini arbiter if a key exists, else ALLOW
 *
 * FAIL-OPEN everywhere: any exception -> UNCERTAIN (the caller then applies
 * the existing fail-open rules); the repo config's uncertain_action="block"
 * is DELIBERATELY NOT ADOPTED.
 */
object FocivoCombinedBrain {

    private const val TAG = "FocivoCombinedBrain"

    enum class Decision { ALLOW, BLOCK, UNCERTAIN }

    /**
     * tag: which stage decided — "user-channels" (service level),
     * "repo-channel", "correction", "doubt", "combined", "error".
     * The service maps it to Guard Hub labels.
     */
    data class Judgment(
        val decision: Decision,
        val tag: String,
        val pCombined: Double?,
        val pRepo: Double?,
        val pV3: Double?,
        val detail: String
    )

    /** Title bands (P_study): allow >= 0.80, block <= 0.35. */
    const val TITLE_ALLOW_P = 0.80
    const val TITLE_BLOCK_P = 0.35

    /** Screen-content bands (P_study): allow >= 0.80, block <= 0.31. */
    const val CONTENT_ALLOW_P = 0.80
    const val CONTENT_BLOCK_P = 0.31

    private fun combine(pRepo: Double?, pV3: Double?): Double? = when {
        pRepo != null && pV3 != null -> 0.5 * pRepo + 0.5 * pV3
        pRepo != null -> pRepo
        pV3 != null -> pV3
        else -> null
    }

    private fun parts(pRepo: Double?, pV3: Double?): String = buildString {
        if (pRepo != null) append("repo=%.3f".format(Locale.ROOT, pRepo))
        if (pV3 != null) {
            if (isNotEmpty()) append(" ")
            append("v3=%.3f".format(Locale.ROOT, pV3))
        }
        if (pRepo != null && pV3 != null) append(" (0.5*repo+0.5*v3)")
    }

    /** Guard Hub band label for a combined P_study. */
    fun pBandLabel(p: Double?, blockP: Double, allowP: Double): String = when {
        p == null -> "no-score"
        p <= blockP -> "<= %.2f non-study".format(Locale.ROOT, blockP)
        p >= allowP -> ">= %.2f study".format(Locale.ROOT, allowP)
        else -> "%.2f-%.2f uncertain".format(Locale.ROOT, blockP, allowP)
    }

    /**
     * Pure decision core (unit-testable, no Android): the exact combination
     * formula. pRepo / pV3 may each be null when that brain could not score.
     */
    internal fun combineScores(pRepo: Double?, pV3: Double?): Double? = combine(pRepo, pV3)

    /** Pure title band for a combined P_study (unit-testable). */
    internal fun decideTitleBand(pCombined: Double): Decision = when {
        pCombined >= TITLE_ALLOW_P -> Decision.ALLOW
        pCombined <= TITLE_BLOCK_P -> Decision.BLOCK
        else -> Decision.UNCERTAIN
    }

    /** Pure screen-content band for a combined P_study (unit-testable). */
    internal fun decideContentBand(pCombined: Double): Decision = when {
        pCombined >= CONTENT_ALLOW_P -> Decision.ALLOW
        pCombined <= CONTENT_BLOCK_P -> Decision.BLOCK
        else -> Decision.UNCERTAIN
    }

    /**
     * Judge one YouTube title. Call with the user's own channel lists
     * already applied (they take precedence — see class doc).
     */
    fun judgeTitle(
        context: Context,
        title: String,
        channel: String? = null,
        handle: String? = null
    ): Judgment {
        return try {
            judgeTitleInner(context, title, channel, handle)
        } catch (t: Throwable) {
            Log.w(TAG, "judgeTitle failed; failing open", t)
            Judgment(Decision.UNCERTAIN, "error", null, null, null, "combined-brain error: ${t.message}")
        }
    }

    private fun judgeTitleInner(
        context: Context,
        title: String,
        channel: String?,
        handle: String?
    ): Judgment {
        val appCtx = context.applicationContext
        val engine = BrainBundleLoader.engineWith(appCtx, FocivoBrainCorrections.asLookup(appCtx))
        val v3 = runCatching { LocalTitleClassifier.get(appCtx) }.getOrNull()

        // 1. repo channel lists (user's own lists already ran before this)
        var extraLogit = 0.0
        val extraSignals = ArrayList<String>()
        if (engine != null) {
            val hit = engine.channels.lookup(handle, channel)
            if (hit != null) {
                if (hit.group == "black") {
                    return Judgment(
                        Decision.BLOCK, "repo-channel", 0.03, 0.03, null,
                        "repo blocked channel '${hit.displayName}'"
                    )
                }
                extraLogit += hit.bonus
                extraSignals.add("study channel")
            }
        }

        // 2. corrections are checked FIRST inside engine.score() (0.97/0.03)
        // 3. doubt gate — can never block
        if (OnDeviceStudyClassifier.isStudyDoubt(title)) {
            return Judgment(
                Decision.ALLOW, "doubt", null, null, null,
                "study-doubt-allow"
            )
        }

        // 4. combined score (engine.score checks corrections FIRST, like the
        //    Python feedback map: 0.97 / 0.03 with the "your earlier
        //    correction" signal)
        val scored = engine?.score(title, extraLogit, extraSignals, raw = title)
        val pRepo = scored?.pStudy
        val corrected = scored?.signals == listOf("your earlier correction")
        val pV3 = v3?.let { 1.0 - it.pEntertainment(title) }
        val p = combine(pRepo, pV3)
        if (p == null) {
            return Judgment(
                Decision.UNCERTAIN, "combined", null, pRepo, pV3,
                "no brain could score -> existing chain"
            )
        }
        val detail = "combined p=%.3f (%s)".format(Locale.ROOT, p, parts(pRepo, pV3))
        // correction tag for the Hub log when the engine short-circuited
        val tag = if (corrected) "correction" else "combined"
        return Judgment(decideTitleBand(p), tag, p, pRepo, pV3, detail)
    }

    /**
     * Judge general screen text (browser path). Order: corrections ->
     * doubt gate -> combined score -> content bands.
     */
    fun judgeScreen(context: Context, screenText: String): Judgment {
        return try {
            judgeScreenInner(context, screenText)
        } catch (t: Throwable) {
            Log.w(TAG, "judgeScreen failed; failing open", t)
            Judgment(Decision.UNCERTAIN, "error", null, null, null, "combined-brain error: ${t.message}")
        }
    }

    private fun judgeScreenInner(context: Context, screenText: String): Judgment {
        val appCtx = context.applicationContext
        val engine = BrainBundleLoader.engineWith(appCtx, FocivoBrainCorrections.asLookup(appCtx))
        val v3 = runCatching { LocalTitleClassifier.getContent(appCtx) }.getOrNull()

        // 1. corrections are checked FIRST inside engine.score()
        // 2. doubt gate — can never block
        if (screenText.isNotBlank() && OnDeviceStudyClassifier.isStudyDoubt(screenText)) {
            return Judgment(
                Decision.ALLOW, "doubt", null, null, null,
                "study-doubt-allow"
            )
        }

        // 3. combined score (engine.score checks corrections FIRST, like the
        //    Python feedback map: 0.97 / 0.03 with the "your earlier
        //    correction" signal)
        val scored = if (engine != null && screenText.isNotBlank()) {
            engine.score(screenText, raw = screenText)
        } else null
        val pRepo = scored?.pStudy
        val corrected = scored?.signals == listOf("your earlier correction")
        val pV3 = if (v3 != null && screenText.isNotBlank()) {
            1.0 - v3.pEntertainment(screenText)
        } else null
        val p = combine(pRepo, pV3)
        if (p == null) {
            return Judgment(
                Decision.UNCERTAIN, "combined", null, pRepo, pV3,
                "no brain could score -> existing chain"
            )
        }
        val detail = "combined p=%.3f (%s)".format(Locale.ROOT, p, parts(pRepo, pV3))
        val tag = if (corrected) "correction" else "combined"
        return Judgment(decideContentBand(p), tag, p, pRepo, pV3, detail)
    }
}
