package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.components.SessionNotificationDigestSheet
import com.example.util.SessionNotificationDigestManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.FocusSessionEntity
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.sin
import kotlin.random.Random

private const val SHARE_CARD_TAG = "ShareCard"
private const val CAPTURE_TIMEOUT_MS = 8000L
private const val CAPTURE_POLL_MS = 120L
private const val CAPTURE_POLL_MAX = 40 // ~4.8s of polling; the outer watchdog stays 8s

@Composable
fun SessionCompleteScreen(
    session: FocusSessionEntity,
    onDismiss: () -> Unit,
    /** Screenshot tests pass false for deterministic captures. */
    animateEntrance: Boolean = true
) {
    val minutesSpent = session.durationSeconds / 60

    val suppressedNotifs by SessionNotificationDigestManager.digestList.collectAsState()
    var showDigestSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // v1.2.2 — celebration moment: subtle one-shot confetti + completion chime.
    var showConfetti by remember { mutableStateOf(true) }
    val confettiProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        runCatching { playCelebrateChime(context) }
        confettiProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
        )
        showConfetti = false
    }

    if (showDigestSheet) {
        SessionNotificationDigestSheet(onDismiss = { showDigestSheet = false })
    }

    // v1.2.2 — the owner's own share-screen design: every value is the user's
    // real data (session minutes/distractions, name, today's totals, streak,
    // last-7-day minutes, apps the shield blocked during THIS session).
    val shareData = remember(session) { loadShareScreenData(context, session) }
    val shareText = if (session.distractionsCount == 0) {
        "I focused for $minutesSpent minutes without distractions with Focivo! Stay focused, stay sharp."
    } else {
        "I focused for $minutesSpent minutes with Focivo (only ${session.distractionsCount} distractions)! Stay focused, stay sharp."
    }

    fun shareCard(cardIndex: Int, toWhatsApp: Boolean) {
        scope.launch {
            val bitmap = captureShareCardPng(context, cardIndex, shareData)
            if (bitmap == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Couldn't render the card — sharing text instead.", Toast.LENGTH_SHORT).show()
                }
                if (toWhatsApp) shareToWhatsApp(context, shareText) else shareGeneric(context, shareText)
                return@launch
            }
            val uri = writeShareCardToCache(context, bitmap)
            if (!bitmap.isRecycled) bitmap.recycle()
            if (uri == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Couldn't prepare the image — sharing text instead.", Toast.LENGTH_SHORT).show()
                }
                if (toWhatsApp) shareToWhatsApp(context, shareText) else shareGeneric(context, shareText)
                return@launch
            }
            shareCardViaSystemSheet(context, shareText, toWhatsApp, uri)
        }
    }

    fun downloadCard(cardIndex: Int) {
        scope.launch {
            val bitmap = captureShareCardPng(context, cardIndex, shareData)
            if (bitmap == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Could not save the image. Please try again.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val uri = saveBitmapToGallery(context, bitmap)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (uri != null) "Image saved to gallery." else "Could not save the image. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // The share experience is dark-always, exactly like the owner's design.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C0B))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (suppressedNotifs.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1D221C))
                        .border(1.dp, Color(0x358CE000), RoundedCornerShape(20.dp))
                        .clickable { showDigestSheet = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = Color(0xFF8CE000),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${suppressedNotifs.size} ${if (suppressedNotifs.size == 1) "notification" else "notifications"} held during session (tap to view)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF8CE000),
                            fontSize = 12.sp
                        )
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                ShareExperienceScreen(
                    data = shareData,
                    onWhatsApp = { shareCard(it, toWhatsApp = true) },
                    onMore = { shareCard(it, toWhatsApp = false) },
                    onDownload = { downloadCard(it) },
                    onContinue = onDismiss,
                    animateEntrance = animateEntrance
                )
            }
        }

        // v1.2.2 — subtle one-shot confetti over the completed screen.
        // Pointer-transparent: no click handling, so it never blocks input.
        if (showConfetti) {
            CelebrationConfetti(progress = confettiProgress.value)
        }
    }
}


private fun shareToWhatsApp(context: Context, text: String) {
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

private fun shareGeneric(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, "Share your focus session")
    if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to share.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * v1.2.2 — renders ANY composable IN-PROCESS by briefly attaching a
 * ComposeView to the Activity's decor view (INVISIBLE), then drawing it to a
 * bitmap of exactly [widthPx] x [heightPx].
 *
 * ROOT CAUSE of the 1.2.1 "still broken on vivo" failure (this function is the
 * single upstream of BOTH Share and Save, so both failed together): the old
 * code used a ONE-SHOT doOnLayout -> drawToBitmap(). That raced the
 * ComposeView's FIRST composition against its FIRST layout pass — when the
 * layout pass won (composition not yet run), the view measured 0 height and
 * Bitmap.createBitmap(w, 0) threw IllegalArgumentException, which a bare
 * catch swallowed with NO log, resuming null. The 1.2.1 FileProvider /
 * MediaStore layer was correct; it never received a bitmap.
 *
 * New path: attach, then poll on the main thread until the first composition
 * has produced a real measured size (bounded retries + the 8s watchdog), and
 * only then draw manually to a bitmap. Every failure logs the FULL exception
 * (Log.e with throwable, never just e.message) and the UI toasts honestly —
 * no silent hangs, no silent nulls.
 *
 * Must be called from a coroutine (suspends on Main until the frame is drawn).
 * Returns null when anything fails.
 */
private suspend fun captureComposedBitmap(
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

/**
 * v1.2.1 — capture with a watchdog. A missed frame fails loud with a toast
 * instead of hanging. v1.2.2 — the timeout cause is now distinguishable in
 * logcat from the per-step capture logs above.
 */
private suspend fun captureComposedBitmapGuarded(
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
 * v1.2.2 — renders the CURRENT share card (the owner's design) at 1080x1350
 * for WhatsApp / system-sheet sharing and gallery download.
 */
private suspend fun captureShareCardPng(
    context: Context,
    cardIndex: Int,
    data: ShareScreenData
): Bitmap? = captureComposedBitmapGuarded(context, 1080, 1350) {
    ShareCard(index = cardIndex, data = data, scale = 3f)
}

/**
 * v1.2.1 — SHARE path: writes the card PNG to the app cache and exposes it
 * through FileProvider (ACTION_SEND, image/png, FLAG_GRANT_READ_URI_PERMISSION
 * with explicit ClipData so the grant survives the chooser on vivo/OEM ROMs).
 *
 * ROOT CAUSE of broken sharing: the old path attached a MediaStore
 * collection URI to the share intent. On several OEM ROMs (vivo's Funtouch
 * included) the read-permission grant for MediaStore URIs does not survive
 * the system chooser, so target apps received an unreadable stream and the
 * share silently failed. FileProvider cache URIs with an explicit ClipData
 * grant are the reliable mechanism.
 */
private suspend fun writeShareCardToCache(context: Context, bitmap: Bitmap): Uri? =
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
private suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
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

/** v1.2.1 — shares the card PNG via FileProvider through the system chooser
 * (WhatsApp, Instagram and every installed app appear automatically). */
private suspend fun shareCardViaSystemSheet(context: Context, text: String, toWhatsApp: Boolean, imageUri: Uri) {
    withContext(Dispatchers.Main) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, text)
            // Explicit ClipData: the read grant reliably reaches the target
            // chosen in the system sheet (MediaStore-URI grants were dropped
            // on some OEM ROMs — the old failure mode).
            clipData = ClipData.newUri(context.contentResolver, "Focivo share card", imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (toWhatsApp) setPackage("com.whatsapp")
        }
        val launch = if (toWhatsApp) intent else Intent.createChooser(intent, "Share your focus session")
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


/**
 * v1.2.2 — completion chime. util.SoundFx is owned by a parallel workstream
 * (`object SoundFx { fun playComplete(context) }`); the call is isolated here
 * so the integration point stays explicit. The call site wraps it in
 * runCatching so the screen can never break on a sound failure.
 */
private fun playCelebrateChime(context: Context) {
    com.example.util.SoundFx.playComplete(context)
}

private data class ConfettiPiece(
    val x0: Float, // 0..1 across the width
    val y0: Float, // start y as a fraction of height (negative = above the top)
    val delay: Float, // 0..1 fraction of total progress before falling
    val duration: Float, // fraction of total progress the fall takes
    val drift: Float, // horizontal drift over the fall (fraction of width)
    val sizeDp: Float,
    val rot0: Float, // degrees
    val spin: Float, // degrees over the fall
    val swayAmpPx: Float,
    val swayFreq: Float,
    val phase: Float,
    val color: Color,
    val round: Boolean
)

/**
 * v1.2.2 — tasteful one-shot confetti burst for the session-complete moment.
 * Subtle by design: ~46 small lime/white/gold pieces (no blue, per visual
 * taste), ~2.4s, fading out, then gone. Pointer-transparent — no click
 * handling — so it never blocks input.
 *
 * @param progress 0..1 driven by the caller (Animatable in SessionCompleteScreen).
 */
@Composable
private fun CelebrationConfetti(progress: Float) {
    val pieces = remember {
        val rnd = Random(20261015)
        val palette = listOf(
            RegainLimePrimary,
            RegainLimeLight,
            Color.White,
            Color(0xFFE8B93E), // warm gold
            Color(0xFFD9F79A) // pale lime
        )
        List(46) {
            ConfettiPiece(
                x0 = 0.04f + rnd.nextFloat() * 0.92f,
                y0 = -0.06f - rnd.nextFloat() * 0.22f,
                delay = rnd.nextFloat() * 0.30f,
                duration = 0.55f + rnd.nextFloat() * 0.40f,
                drift = (rnd.nextFloat() - 0.5f) * 0.22f,
                sizeDp = 4f + rnd.nextFloat() * 6f,
                rot0 = rnd.nextFloat() * 360f,
                spin = (rnd.nextFloat() - 0.5f) * 540f,
                swayAmpPx = 8f + rnd.nextFloat() * 22f,
                swayFreq = 2f + rnd.nextFloat() * 4f,
                phase = rnd.nextFloat() * 6.2832f,
                color = palette[rnd.nextInt(palette.size)],
                round = rnd.nextBoolean()
            )
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        for (p in pieces) {
            val t = ((progress - p.delay) / p.duration).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val alpha = (1f - t * t).coerceIn(0f, 1f)
            if (alpha <= 0.01f) continue
            val y = (p.y0 + t * (1.25f - p.y0)) * h
            val x = (p.x0 + p.drift * t) * w + sin(t * p.swayFreq + p.phase) * p.swayAmpPx
            val s = p.sizeDp.dp.toPx()
            rotate(degrees = p.rot0 + p.spin * t, pivot = Offset(x, y)) {
                if (p.round) {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = s / 2f,
                        center = Offset(x, y)
                    )
                } else {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - s / 2f, y - s * 0.3f),
                        size = androidx.compose.ui.geometry.Size(s, s * 0.6f)
                    )
                }
            }
        }
    }
}
