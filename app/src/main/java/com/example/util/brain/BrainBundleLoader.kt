package com.example.util.brain

import android.content.Context
import android.util.Log

/**
 * Worker E (v1.3.0): lazy, once-only loading of the Focivo brain assets.
 *
 * Assets (all fully offline, no network, no Chaquopy):
 *  - focivo_brain_model.json    — 33,240 word/bigram/char-ngram weights + bias (710 KB)
 *  - focivo_brain_lexicon.json  — 354 weighted cue rules, English + Hinglish
 *  - focivo_brain_channels.json — 397 blocked / 463 study channels
 *
 * Loads off the main thread at service start (warm it in
 * AiStudyAccessibilityService.onCreate alongside the v3 bundles).
 * Null on any failure — callers MUST fall through to the existing chain
 * (fail-safe: never block on a missing bundle).
 */
object BrainBundleLoader {

    private const val TAG = "BrainBundleLoader"
    private const val MODEL_ASSET = "focivo_brain_model.json"
    private const val LEXICON_ASSET = "focivo_brain_lexicon.json"
    private const val CHANNELS_ASSET = "focivo_brain_channels.json"

    data class BrainBundle(
        val engine: FocivoBrainEngine,
        val model: BrainModel.Classifier,
        val lexicon: BrainLexicon,
        val channels: BrainChannels
    )

    @Volatile
    private var cached: BrainBundle? = null

    @Volatile
    private var loadFailed = false

    private fun readAsset(context: Context, name: String): String =
        context.applicationContext.assets.open(name).bufferedReader().use { it.readText() }

    /**
     * Returns the shared brain bundle, loading the three assets once.
     * The engine is built WITHOUT corrections here — callers that want the
     * user's corrections memory pass their own engine via [engineWith]
     * (the guard pipeline always does).
     */
    fun get(context: Context): BrainBundle? {
        cached?.let { return it }
        if (loadFailed) return null
        synchronized(this) {
            cached?.let { return it }
            if (loadFailed) return null
            return try {
                val t0 = System.currentTimeMillis()
                val model = BrainModel.Classifier.load(readAsset(context, MODEL_ASSET))
                val lexicon = BrainLexicon.fromJson(readAsset(context, LEXICON_ASSET))
                val channels = BrainChannels.fromJson(readAsset(context, CHANNELS_ASSET))
                // fail-open default: the repo's uncertain_action="block" is
                // deliberately NOT adopted (see FocivoBrainEngine docs)
                val engine = FocivoBrainEngine(
                    model, lexicon, channels,
                    corrections = BrainCorrectionsLookup { null },
                    uncertainActionAllow = true
                )
                BrainBundle(engine, model, lexicon, channels).also {
                    cached = it
                    Log.i(
                        TAG,
                        "brain bundle loaded in ${System.currentTimeMillis() - t0}ms: " +
                            "${model.weights.size} weights, " +
                            "${lexicon.studyRules.size + lexicon.nonStudyRules.size} rules, " +
                            "${channels.index.size} channel keys"
                    )
                }
            } catch (t: Throwable) {
                loadFailed = true
                Log.w(TAG, "brain bundle load failed; guard falls back to existing chain", t)
                null
            }
        }
    }

    /**
     * An engine sharing the loaded model/lexicon/channels but consulting the
     * given corrections store FIRST (the user's overrides always win).
     */
    fun engineWith(context: Context, corrections: BrainCorrectionsLookup): FocivoBrainEngine? {
        val b = get(context) ?: return null
        return FocivoBrainEngine(
            b.model, b.lexicon, b.channels,
            corrections = corrections,
            uncertainActionAllow = true
        )
    }

    /** Test seam: build a bundle from raw JSON strings without assets. */
    fun fromJsonStrings(
        modelJson: String,
        lexiconJson: String,
        channelsJson: String,
        corrections: BrainCorrectionsLookup = BrainCorrectionsLookup { null }
    ): BrainBundle {
        val model = BrainModel.Classifier.load(modelJson)
        val lexicon = BrainLexicon.fromJson(lexiconJson)
        val channels = BrainChannels.fromJson(channelsJson)
        val engine = FocivoBrainEngine(model, lexicon, channels, corrections, uncertainActionAllow = true)
        return BrainBundle(engine, model, lexicon, channels)
    }
}
