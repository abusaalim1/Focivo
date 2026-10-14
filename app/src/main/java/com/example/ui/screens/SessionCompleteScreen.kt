package com.example.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.drawToBitmap
import androidx.core.view.doOnLayout
import com.example.data.model.FocusSessionEntity
import com.example.ui.components.GlassButton
import com.example.ui.components.MascotPose
import com.example.ui.components.RegainMascotView
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.BalooBrandFontFamily
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SessionCompleteScreen(
    session: FocusSessionEntity,
    onDismiss: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val minutesSpent = session.durationSeconds / 60

    val suppressedNotifs by SessionNotificationDigestManager.digestList.collectAsState()
    var showDigestSheet by remember { mutableStateOf(false) }

    if (showDigestSheet) {
        SessionNotificationDigestSheet(onDismiss = { showDigestSheet = false })
    }

    // Particle / Light Orbit Animation
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xF2111411) else Color(0xF5FAFBF8)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Completed Circular Transformation with Particle Ring
            Box(
                modifier = Modifier.size(170.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(170.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = (size.width - 24.dp.toPx()) / 2f

                    // Halo Ring
                    drawCircle(
                        color = RegainLimePrimary.copy(alpha = 0.2f),
                        radius = radius * pulseScale,
                        center = center,
                        style = Stroke(width = 12.dp.toPx())
                    )

                    // Complete Solid Circular Ring
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(RegainLimePrimary, RegainLimePrimary.copy(alpha = 0.4f), RegainLimePrimary),
                            center = center
                        ),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Floating Particle Nodes
                    for (i in 0..4) {
                        val angle = Math.toRadians((orbitAngle + i * 72).toDouble())
                        val px = center.x + (radius + 14.dp.toPx()) * cos(angle).toFloat()
                        val py = center.y + (radius + 14.dp.toPx()) * sin(angle).toFloat()
                        drawCircle(
                            color = RegainLimePrimary.copy(alpha = 0.7f),
                            radius = (2 + (i % 3)).dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                // Center Celebrating Mascot (new celebrate art)
                RegainMascotView(
                    pose = MascotPose.CELEBRATE_NEW,
                    width = 180.dp,
                    height = 240.dp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Well done.",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = BalooBrandFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = if (isDark) Color(0xFFF0F4ED) else NearBlack
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$minutesSpent minutes of focused work.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight,
                    fontSize = 16.sp
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Points & Distractions Glass Capsule
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = RegainLimePrimary.copy(alpha = 0.2f),
                        spotColor = RegainLimePrimary.copy(alpha = 0.3f)
                    )
                .clip(RoundedCornerShape(24.dp))
                .background(if (isDark) Color(0xFF1D221C) else Color.White)
                .border(
                    1.dp,
                    if (isDark) Color(0x358CE000) else Color(0xFFDCE6D2),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = if (isDark) RegainLimePrimary else RegainLimeDeepText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Session Completed",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) RegainLimePrimary else RegainLimeDeepText,
                                fontSize = 15.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 20.dp)
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0xFFDCE6D2))
                    )

                    Text(
                        text = if (session.distractionsCount == 0) "0 Distractions" else "${session.distractionsCount} Distractions",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            if (suppressedNotifs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDark) Color(0xFF1D221C) else Color(0xFFF2F6ED))
                        .border(1.dp, if (isDark) Color(0x358CE000) else Color(0xFFDCE6D2), RoundedCornerShape(20.dp))
                        .clickable { showDigestSheet = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = if (isDark) RegainLimePrimary else RegainLimeDeepText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${suppressedNotifs.size} ${if (suppressedNotifs.size == 1) "notification" else "notifications"} held during session (tap to view)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) RegainLimePrimary else RegainLimeDeepText,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // v1.3.0: Share your focus session (share card). v1.3.1: rotating quote line,
            // honest distractions copy, working Download + image share.
            val sessionQuote = com.example.util.FocusQuotes.forSession(session.id)
            ShareSessionCard(
                minutesSpent = minutesSpent,
                distractionsCount = session.distractionsCount,
                quote = sessionQuote
            )

            Spacer(modifier = Modifier.height(24.dp))

            GlassButton(
                text = "Continue",
                onClick = onDismiss,
                isPrimary = true,
                modifier = Modifier.fillMaxWidth(0.7f)
            )
        }
    }
}

/**
 * v1.3.0 — compact share row on the session-complete screen.
 * v1.3.1 — honest distractions copy (never claims "without distractions" when there
 * were some), rotating quote line, working Download, and image-attached sharing.
 */
@Composable
private fun ShareSessionCard(minutesSpent: Int, distractionsCount: Int, quote: String) {
    val context = LocalContext.current
    val isDark = isAppInDarkTheme()
    val scope = rememberCoroutineScope()

    val headerText = if (distractionsCount == 0) {
        "\uD83C\uDF89 You focused for ${minutesSpent}m without distractions!"
    } else {
        val dWord = if (distractionsCount == 1) "distraction" else "distractions"
        "\uD83C\uDF89 You focused for ${minutesSpent}m with only $distractionsCount $dWord!"
    }
    val shareText = if (distractionsCount == 0) {
        "I focused for $minutesSpent minutes without distractions with Focivo! Stay focused, stay sharp."
    } else {
        "I focused for $minutesSpent minutes with Focivo (only $distractionsCount distractions)! Stay focused, stay sharp."
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch { saveShareCard(context, minutesSpent, distractionsCount, quote) }
        } else {
            Toast.makeText(context, "Storage permission is needed to save the image.", Toast.LENGTH_SHORT).show()
        }
    }

    // Shares the card image (saved to gallery first) + text; falls back to text-only.
    fun shareWithImage(toWhatsApp: Boolean) {
        scope.launch {
            val imageUri = saveShareCardImage(context, minutesSpent, distractionsCount, quote)
            if (imageUri != null) {
                shareImageAndText(context, imageUri, shareText, toWhatsApp)
            } else {
                if (toWhatsApp) shareToWhatsApp(context, shareText) else shareGeneric(context, shareText)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = headerText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isDark) Color(0xFFF0F4ED) else NearBlack
            ),
            textAlign = TextAlign.Center
        )

        if (quote.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "\u201C$quote\u201D",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
                ),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.Top
        ) {
            ShareActionButton(
                label = "WhatsApp",
                icon = Icons.Default.Chat,
                tint = Color(0xFF25D366),
                onClick = { shareWithImage(toWhatsApp = true) }
            )
            ShareActionButton(
                label = "More",
                icon = Icons.Default.Share,
                tint = if (isDark) Color(0xFFF0F4ED) else NearBlack,
                onClick = { shareWithImage(toWhatsApp = false) }
            )
            ShareActionButton(
                label = "Download",
                icon = Icons.Default.Download,
                tint = if (isDark) Color(0xFFF0F4ED) else NearBlack,
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) {
                        storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        scope.launch { saveShareCard(context, minutesSpent, distractionsCount, quote) }
                    }
                }
            )
        }
    }
}

@Composable
private fun ShareActionButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF1D221C) else Color.White)
                .border(1.dp, if (isDark) Color(0x358CE000) else Color(0xFFDCE6D2), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                color = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
            )
        )
    }
}

/**
 * The card rendered into the downloaded PNG. Self-contained colors so it
 * renders correctly in an offscreen ComposeView without an Activity theme.
 * v1.3.1 — Regain-level aesthetic: lime card, big Focus Time, rotating quote
 * box, honest distractions line, brand footer.
 */
@Composable
private fun ShareCardContent(minutes: Int, distractionsCount: Int, quote: String) {
    val lime = Color(0xFF8CE000)
    val ink = Color(0xFF121511)
    val cardBg = Color(0xFF1D221C)
    val softText = Color(0xFFA0A89E)
    val brightText = Color(0xFFF0F4ED)

    val focusLine = if (distractionsCount == 0) {
        "without distractions!"
    } else {
        val dWord = if (distractionsCount == 1) "distraction" else "distractions"
        "with only $distractionsCount $dWord"
    }

    Box(
        modifier = Modifier
            .background(ink)
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(36.dp))
                .background(lime)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(cardBg)
                    .border(2.dp, lime.copy(alpha = 0.6f), RoundedCornerShape(30.dp))
                    .padding(horizontal = 56.dp, vertical = 48.dp)
            ) {
                Text(
                    text = "\uD83C\uDF89",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Yay! You focused for",
                    color = softText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${minutes}m",
                    color = lime,
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = BalooBrandFontFamily
                )
                Text(
                    text = "Focus Time",
                    color = brightText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = focusLine,
                    color = brightText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                if (quote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2A3129))
                            .border(1.dp, lime.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = "\u201C$quote\u201D",
                            color = brightText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Focivo \u2014 Stay focused, stay sharp.",
                    color = softText,
                    fontSize = 20.sp
                )
            }
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
 * v1.3.1 — renders the card by briefly attaching a ComposeView to the WindowManager
 * (TYPE_APPLICATION_OVERLAY), awaiting one layout pass, then drawing it to a bitmap.
 *
 * Why: the old approach measured a DETACHED ComposeView, which defers composition
 * until onAttachedToWindow — so nothing ever composed and drawToBitmap() threw on a
 * 0-height bitmap (every Download tap ended at the failure toast).
 *
 * Must be called from a coroutine (suspends on Main until the frame is drawn).
 * Returns null when the overlay permission is missing or anything fails.
 */
private suspend fun captureShareCardBitmap(
    context: Context,
    minutes: Int,
    distractionsCount: Int,
    quote: String
): Bitmap? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
        var composeView: ComposeView? = null
        var wm: WindowManager? = null
        fun cleanup() {
            try {
                composeView?.let { v -> wm?.removeView(v) }
            } catch (_: Exception) {
            }
        }
        cont.invokeOnCancellation { cleanup() }
        try {
            wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            composeView = ComposeView(context).apply {
                setContent { ShareCardContent(minutes, distractionsCount, quote) }
            }
            val params = WindowManager.LayoutParams(
                1080,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            val view = composeView!!
            val manager = wm!!
            view.doOnLayout {
                try {
                    val bmp = it.drawToBitmap()
                    if (!cont.isCompleted) cont.resume(bmp) {}
                } catch (e: Exception) {
                    if (!cont.isCompleted) cont.resume(null) {}
                } finally {
                    cleanup()
                }
            }
            manager.addView(view, params)
        } catch (e: Exception) {
            cleanup()
            if (!cont.isCompleted) cont.resume(null) {}
        }
    }
}

/**
 * Renders the card and saves the PNG to the gallery.
 * @return the MediaStore Uri of the saved image, or null on failure.
 */
private suspend fun saveShareCardImage(
    context: Context,
    minutes: Int,
    distractionsCount: Int,
    quote: String
): Uri? {
    val bitmap = captureShareCardBitmap(context, minutes, distractionsCount, quote) ?: return null
    return withContext(Dispatchers.IO) {
        try {
            val filename = "Focivo-Focus-${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Focivo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null
            var ok = false
            resolver.openOutputStream(uri)?.use { out ->
                ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!ok) {
                runCatching { resolver.delete(uri, null, null) }
                return@withContext null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            uri
        } catch (e: Exception) {
            null
        }
    }
}

private suspend fun saveShareCard(
    context: Context,
    minutes: Int,
    distractionsCount: Int,
    quote: String
) {
    val uri = saveShareCardImage(context, minutes, distractionsCount, quote)
    withContext(Dispatchers.Main) {
        Toast.makeText(
            context,
            if (uri != null) "Image saved to gallery." else "Could not save the image. Please try again.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

/** Shares the card image together with the share text (WhatsApp direct, or system chooser). */
private fun shareImageAndText(context: Context, imageUri: Uri, text: String, toWhatsApp: Boolean) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (toWhatsApp) setPackage("com.whatsapp")
    }
    val launch = if (toWhatsApp) intent else Intent.createChooser(intent, "Share your focus session")
    if (context !is Activity) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(launch)
    } catch (e: Exception) {
        // WhatsApp missing / no app handles image share — fall back to text-only.
        if (toWhatsApp) shareToWhatsApp(context, text) else shareGeneric(context, text)
    }
}
