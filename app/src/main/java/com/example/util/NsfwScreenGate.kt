package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * On-device NSFW / sensitive-image gate for profile pictures and group pictures
 * (Focivo 1.3.2, item 6 — owner order 2026-10-17).
 *
 * Runs a small, fully on-device image classifier — no network, no paid APIs, no
 * image ever leaves the phone.
 *
 * Model: "nsfw_model" MobileNetV2-140 224x224 by GantMan / Infinite Red
 * (https://github.com/GantMan/nsfw_model), MIT licensed
 * (Copyright (c) 2020 The nsfw_model Developers — see LICENSE.md in that repo).
 * The weights are bundled at `app/src/main/assets/nsfw_model.tflite`, converted
 * to full-int8 TFLite (5,013,856 bytes) from the project's own 1.2.0 release.
 *
 * Output classes, in index order: drawings, hentai, neutral, porn, sexy.
 * explicitScore = p(porn) + p(hentai).
 *
 * Calibration contract (owner, non-negotiable):
 * - REJECT only when explicitScore >= [REJECT_THRESHOLD] (0.85) — high confidence only.
 * - Uncertain / borderline scores ALWAYS allow.
 * - If the model fails to load or inference throws, the gate FAILS OPEN (ALLOW)
 *   and logs the failure — a broken gate must never trap the user.
 *
 * Threading: safe to call from any thread; the model loads lazily exactly once
 * under a lock. Call from a background thread — inference takes tens of ms.
 */
object NsfwScreenGate {

    enum class Verdict { ALLOW, REJECT }

    /** Reject only at/above this explicit-class probability. Borderline always allows. */
    const val REJECT_THRESHOLD = 0.85f

    private const val TAG = "NsfwScreenGate"
    private const val MODEL_ASSET_PATH = "nsfw_model.tflite"
    private const val INPUT_SIZE = 224

    // Class indices in the bundled model (see class_labels.txt in the release).
    private const val IDX_HENTAI = 1
    private const val IDX_PORN = 3

    private val lock = ReentrantLock()

    @Volatile private var classifier: ImageClassifier? = null

    @Volatile private var loadFailed = false

    /**
     * TEST-ONLY override. When non-null, [screenImage] skips the model entirely
     * and gates the stubbed explicit score. Never set from production code.
     */
    @Volatile private var testOverride: Float? = null

    /** Test-only: stub the explicit score used by [screenImage]; null clears it. */
    @VisibleForTesting
    fun setTestOverride(score: Float?) {
        testOverride = score
    }

    /** Test-only: reset cached classifier / failure flag / override. */
    @VisibleForTesting
    fun resetForTest() {
        lock.withLock {
            try {
                classifier?.close()
            } catch (_: Throwable) {
                // Best effort only.
            }
            classifier = null
            loadFailed = false
        }
        testOverride = null
    }

    /**
     * Screens [bitmap] and returns [Verdict.ALLOW] or [Verdict.REJECT].
     * Fails open (ALLOW) on any model load / inference failure.
     */
    /**
     * Convenience for image pickers: decodes [uri] (downsampled, capped at
     * 1024px) and screens it. An undecodable uri fails open (ALLOW) — the
     * caller still shows its own "could not read" notice.
     */
    fun screenUri(context: Context, uri: android.net.Uri): Verdict {
        val bitmap = try {
            decodeUri(context, uri)
        } catch (_: Exception) {
            null
        } ?: return Verdict.ALLOW
        return try {
            screenImage(context, bitmap)
        } finally {
            try { bitmap.recycle() } catch (_: Exception) { }
        }
    }

    private fun decodeUri(context: Context, uri: android.net.Uri): android.graphics.Bitmap? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                    decoder.setTargetSampleSize(4)
                }
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (_: Exception) { null }
    }

    fun screenImage(context: Context, bitmap: Bitmap): Verdict {
        testOverride?.let { stubbed ->
            return gate(stubbed)
        }
        val active = getOrLoadClassifier(context) ?: return Verdict.ALLOW
        return try {
            val tensorImage = ImageProcessor.Builder()
                .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .build()
                .process(TensorImage.fromBitmap(bitmap))
            var hentai = 0f
            var porn = 0f
            lock.withLock {
                for (classifications in active.classify(tensorImage)) {
                    for (category in classifications.categories) {
                        when (category.index) {
                            IDX_HENTAI -> hentai = maxOf(hentai, category.score)
                            IDX_PORN -> porn = maxOf(porn, category.score)
                        }
                    }
                }
            }
            val explicitScore = hentai + porn
            Log.d(TAG, "screened image: hentai=$hentai porn=$porn explicit=$explicitScore")
            val verdict = gate(explicitScore)
            if (verdict == Verdict.REJECT) {
                logGuardHub(context, "REJECT", "explicit picture rejected (score=%.2f)".format(explicitScore))
            }
            verdict
        } catch (t: Throwable) {
            // Fail open: a broken gate must never trap the user.
            Log.w(TAG, "NSFW inference failed — failing open (ALLOW)", t)
            logGuardHub(context, "ALLOW", "gate failed-open: inference error")
            Verdict.ALLOW
        }
    }

    /** Guard Hub audit trail for gate failures and rejections (never blocks). */
    private fun logGuardHub(context: Context, verdict: String, reason: String) {
        try {
            AiStudyGuardManager.logGuardActivity(
                context = context,
                app = "Picture Check",
                stage = "NSFW gate",
                verdict = verdict,
                reason = reason
            )
        } catch (_: Exception) { }
    }

    private fun gate(explicitScore: Float): Verdict =
        if (explicitScore >= REJECT_THRESHOLD) Verdict.REJECT else Verdict.ALLOW

    private fun getOrLoadClassifier(context: Context): ImageClassifier? = lock.withLock {
        classifier?.let { return it }
        if (loadFailed) return null
        return try {
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setMaxResults(5)
                .build()
            ImageClassifier.createFromFileAndOptions(context, MODEL_ASSET_PATH, options)
                .also { classifier = it }
        } catch (t: Throwable) {
            // Fail open permanently for this process; the user is never blocked.
            loadFailed = true
            Log.e(TAG, "Failed to load NSFW model asset — gate failing open (ALLOW)", t)
            try {
                AiStudyGuardManager.logGuardActivity(
                    context = context,
                    app = "Picture Check",
                    stage = "NSFW gate",
                    verdict = "ALLOW",
                    reason = "gate failed-open: model load error"
                )
            } catch (_: Exception) { }
            null
        }
    }
}
