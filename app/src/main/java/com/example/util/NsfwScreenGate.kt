package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
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
 * Inference runs on the plain `org.tensorflow:tensorflow-lite` Interpreter
 * (no task-vision — keeps the APK under 50 MB). Input: uint8 RGB 224x224.
 * Output classes, in index order: drawings, hentai, neutral, porn, sexy
 * (uint8, dequantized with scale 1/256). explicitScore = p(porn) + p(hentai).
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

    // Class indices in the bundled model (drawings, hentai, neutral, porn, sexy).
    private const val IDX_HENTAI = 1
    private const val IDX_PORN = 3

    /** Output dequantization: uint8 value * 1/256 (zero point 0). */
    private const val OUTPUT_SCALE = 1f / 256f

    private val lock = ReentrantLock()

    @Volatile private var interpreter: Interpreter? = null

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

    /** Test-only: reset cached interpreter / failure flag / override. */
    @VisibleForTesting
    fun resetForTest() {
        lock.withLock {
            try {
                interpreter?.close()
            } catch (_: Throwable) {
                // Best effort only.
            }
            interpreter = null
            loadFailed = false
        }
        testOverride = null
    }

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

    /**
     * Screens [bitmap] and returns [Verdict.ALLOW] or [Verdict.REJECT].
     * Fails open (ALLOW) on any model load / inference failure.
     */
    fun screenImage(context: Context, bitmap: Bitmap): Verdict {
        testOverride?.let { stubbed ->
            return gate(stubbed)
        }
        val active = getOrLoadInterpreter(context) ?: return Verdict.ALLOW
        return try {
            val scores = classify(active, bitmap)
            val hentai = scores[IDX_HENTAI]
            val porn = scores[IDX_PORN]
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

    /**
     * Runs the quantized model on [bitmap] and returns the 5 dequantized class
     * probabilities in index order (drawings, hentai, neutral, porn, sexy).
     */
    private fun classify(active: Interpreter, bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        try {
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            val input = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
                .order(ByteOrder.nativeOrder())
            for (pixel in pixels) {
                input.put(((pixel shr 16) and 0xFF).toByte())
                input.put(((pixel shr 8) and 0xFF).toByte())
                input.put((pixel and 0xFF).toByte())
            }
            input.rewind()
            val output = Array(1) { ByteArray(5) }
            lock.withLock { active.run(input, output) }
            return FloatArray(5) { i -> (output[0][i].toInt() and 0xFF) * OUTPUT_SCALE }
        } finally {
            try { resized.recycle() } catch (_: Exception) { }
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

    private fun getOrLoadInterpreter(context: Context): Interpreter? = lock.withLock {
        interpreter?.let { return it }
        if (loadFailed) return null
        return try {
            val mapped = context.assets.openFd(MODEL_ASSET_PATH).use { afd ->
                FileInputStream(afd.fileDescriptor).channel.map(
                    FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength
                )
            }
            Interpreter(mapped, Interpreter.Options().setNumThreads(2))
                .also { interpreter = it }
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
