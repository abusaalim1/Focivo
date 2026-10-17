package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared share-card pipeline (v1.3.0) — extracted from SessionCompleteScreen's
 * battle-tested v1.2.2 implementation so every share surface (session cards,
 * squad cards, …) uses the SAME code path:
 *
 *   captureComposedBitmapGuarded → writeShareCardToCache (FileProvider) /
 *   saveBitmapToGallery (MediaStore) → shareCardViaSystemSheet / shareToWhatsApp
 *
 * Do not fork these functions per-feature: fix bugs here once.
 */
const val SHARE_CARD_TAG = "ShareCard"
private const val CAPTURE_TIMEOUT_MS = 8000L
private const val CAPTURE_POLL_MS = 120L
private const val CAPTURE_POLL_MAX = 40 // ~4.8s of polling; the outer watchdog stays 8s

/**
 * Renders ANY composable IN-PROCESS by briefly attaching a ComposeView to the
 * Activity's decor view (INVISIBLE), then drawing it to a bitmap of exactly
 * [widthPx] x [heightPx].
 *
 * Must be called from a coroutine (suspends on Main until the frame is
 * drawn). Returns null when anything fails (never hangs silently — the
 * guarded wrapper below fails loud with a toast instead).
 */
suspend fun captureComposedBitmap(
    context: Context,
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit
): Bitmap? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
        val activity = context as? Activity
        if (activity == null) {
            Log.e(SHARE_CARD_TAG, "capture: context is not an Activity — cannot render card")
            if (!cont.isCompleted) cont.resume(null) {}
            return@suspendCancellableCoroutine
        }
        val decor = activity.window?.decorView as? android.view.ViewGroup
        if (decor == null) {
            Log.e(SHARE_CARD_TAG, "capture: no decor view to attach the card to")
            if (!cont.isCompleted) cont.resume(null) {}
            return@suspendCancellableCoroutine
        }
        val composeView = ComposeView(activity).apply {
            // INVISIBLE (not GONE): measures/layouts/composes, never flashes.
            visibility = android.view.View.INVISIBLE
            setContent { content() }
        }
        var attempts = 0
        fun cleanup() {
            try {
                (composeView.parent as? android.view.ViewGroup)?.removeView(composeView)
            } catch (e: Exception) {
                Log.w(SHARE_CARD_TAG, "capture: detach failed", e)
            }
        }
        cont.invokeOnCancellation { cleanup() }
        fun finish(value: Bitmap?) {
            if (!cont.isCompleted) cont.resume(value) {}
            cleanup()
        }
        // Polls on the main thread until the first composition has produced a
        // real measured size, then draws. Bounded: the outer watchdog fails
        // loud if composition never produces content.
        fun attemptCapture() {
            if (cont.isCompleted) return // timed out / cancelled — stop polling
            val w = composeView.width
            val h = composeView.height
            if (w > 0 && h > 0) {
                try {
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    // Direct draw(): the 1-arg View.draw() ignores the view's
                    // own INVISIBLE flag (visibility is only consulted by
                    // parents), so the card renders into our bitmap.
                    composeView.draw(android.graphics.Canvas(bmp))
                    Log.d(SHARE_CARD_TAG, "capture: card bitmap ${w}x${h}")
                    finish(bmp)
                } catch (e: Exception) {
                    Log.e(SHARE_CARD_TAG, "capture: draw failed (w=$w h=$h)", e)
                    finish(null)
                }
                return
            }
            attempts++
            if (attempts >= CAPTURE_POLL_MAX) {
                Log.e(
                    SHARE_CARD_TAG,
                    "capture: view never measured after $attempts polls " +
                        "(w=$w h=$h) — composition produced no content"
                )
                finish(null)
                return
            }
            composeView.postDelayed({ attemptCapture() }, CAPTURE_POLL_MS)
        }
        try {
            decor.addView(
                composeView,
                android.view.ViewGroup.LayoutParams(widthPx, heightPx)
            )
            // Kick off after attach; the poll loop waits for the composition.
            composeView.post { attemptCapture() }
        } catch (e: Exception) {
            Log.e(SHARE_CARD_TAG, "capture: setup failed", e)
            finish(null)
        }
    }
}

/** Capture with a watchdog: a missed frame fails loud instead of hanging. */
suspend fun captureComposedBitmapGuarded(
    context: Context,
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit
): Bitmap? {
    val bitmap = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
        captureComposedBitmap(context, widthPx, heightPx, content)
    }
    if (bitmap == null) {
        Log.e(
            SHARE_CARD_TAG,
            "capture: FAILED — null bitmap after up to ${CAPTURE_TIMEOUT_MS}ms " +
                "(see capture logs above for the step that failed)"
        )
    }
    return bitmap
}

/**
 * Writes a bitmap PNG into the app cache and returns a FileProvider URI.
 * FileProvider cache URIs with an explicit ClipData grant are the reliable
 * mechanism for the share intent (MediaStore-URI grants were dropped on some
 * OEM ROMs — the old 1.2.1 failure mode).
 */
suspend fun writeShareCardToCache(context: Context, bitmap: Bitmap): Uri? =
    withContext(Dispatchers.IO) {
        try {
            val dir = java.io.File(context.cacheDir, "recap_shares")
            if (!dir.isDirectory && !dir.mkdirs()) {
                Log.e(SHARE_CARD_TAG, "share: cannot create cache dir ${dir.absolutePath}")
                return@withContext null
            }
            // Prune older share files so the cache never grows.
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)
                ?.forEach { runCatching { it.delete() } }
            val file = java.io.File(dir, "focivo-share-${System.currentTimeMillis()}.png")
            var ok = false
            file.outputStream().use { out -> ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (!ok || file.length() == 0L) {
                Log.e(SHARE_CARD_TAG, "share: PNG compress failed (ok=$ok, bytes=${file.length()})")
                runCatching { file.delete() }
                return@withContext null
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            // getUriForFile throws IllegalArgumentException when the file is
            // not under a configured <paths> root — that surfaces here too.
            Log.e(SHARE_CARD_TAG, "share: cache write / FileProvider URI failed", e)
            null
        }
    }

/**
 * Saves a bitmap PNG to the gallery (Pictures/Focivo).
 * @return the MediaStore Uri of the saved image, or null on failure.
 */
suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
    return withContext(Dispatchers.IO) {
        var uri: Uri? = null
        try {
            val filename = "Focivo-Share-${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Focivo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Log.e(SHARE_CARD_TAG, "save: MediaStore insert returned null")
                return@withContext null
            }
            val pendingUri = uri
            var ok = false
            val stream = resolver.openOutputStream(pendingUri)
            if (stream == null) {
                Log.e(SHARE_CARD_TAG, "save: MediaStore openOutputStream returned null for $pendingUri")
            } else {
                stream.use { out -> ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            if (!ok) {
                Log.e(SHARE_CARD_TAG, "save: PNG compress to MediaStore failed")
                runCatching { resolver.delete(pendingUri, null, null) }
                return@withContext null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                val rows = resolver.update(pendingUri, done, null, null)
                Log.d(SHARE_CARD_TAG, "save: IS_PENDING cleared (rows=$rows) -> $pendingUri")
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            pendingUri
        } catch (e: Exception) {
            Log.e(SHARE_CARD_TAG, "save: gallery save failed", e)
            uri?.let { failed -> runCatching { context.contentResolver.delete(failed, null, null) } }
            null
        }
    }
}

/**
 * Shares a card PNG via FileProvider through the system chooser (WhatsApp,
 * Instagram and every installed app appear automatically). When
 * [toWhatsApp] is true the intent is pinned to WhatsApp with a generic-sheet
 * fallback.
 */
suspend fun shareCardViaSystemSheet(
    context: Context,
    text: String,
    toWhatsApp: Boolean,
    imageUri: Uri,
    chooserTitle: String,
) {
    withContext(Dispatchers.Main) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, text)
            // Explicit ClipData: the read grant reliably reaches the target
            // chosen in the system sheet.
            clipData = ClipData.newUri(context.contentResolver, "Focivo share card", imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (toWhatsApp) setPackage("com.whatsapp")
        }
        val launch = if (toWhatsApp) intent else Intent.createChooser(intent, chooserTitle)
        if (context !is Activity) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
        } catch (e: Exception) {
            Log.e(SHARE_CARD_TAG, "share: share sheet failed", e)
            Toast.makeText(context, "No app available to share.", Toast.LENGTH_SHORT).show()
            // WhatsApp missing — fall back to the generic sheet / text.
            if (toWhatsApp) shareToWhatsApp(context, text)
        }
    }
}

/** Text-only share pinned to WhatsApp (falls back to the generic sheet). */
fun shareToWhatsApp(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.whatsapp")
    }
    try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        // WhatsApp not installed — fall back to the generic sheet
        shareGeneric(context, text)
    }
}

/** Text-only share through the system chooser. */
fun shareGeneric(context: Context, text: String, chooserTitle: String = "Share") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, chooserTitle)
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to share.", Toast.LENGTH_SHORT).show()
    }
}
