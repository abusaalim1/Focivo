package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.requiredWidth
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
import com.example.ui.components.CelebrateBuddyMascot
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
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import androidx.core.view.doOnLayout
import com.example.data.model.FocusSessionEntity
import com.example.ui.components.GlassButton
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

                // Center Celebrating Mascot — equipped outfit set (never undressed).
                CelebrateBuddyMascot(
                    modifier = Modifier.size(180.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Well done.",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = PoppinsFontFamily,
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

            // Points & Distractions Glass Capsule (requiredWidth: never clip mid-word)
            Box(
                modifier = Modifier
                    .requiredWidth(IntrinsicSize.Max)
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
                        maxLines = 1,
                        softWrap = false,
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

    // v1.1: streak + date for the minimal share card.
    val streakDays = remember {
        try {
            val prefs = context.getSharedPreferences("focusly_local_data", Context.MODE_PRIVATE)
            val userPrefsJson = prefs.getString("user_preferences", null)
            if (userPrefsJson != null) {
                org.json.JSONObject(userPrefsJson).optInt("currentStreak", 1).coerceAtLeast(1)
            } else 1
        } catch (_: Exception) { 1 }
    }
    val dateLine = remember {
        try {
            java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.getDefault())
                .format(java.util.Date())
        } catch (_: Exception) { "" }
    }

    val headerText = if (distractionsCount == 0) {
        "You focused for ${minutesSpent}m without distractions!"
    } else {
        val dWord = if (distractionsCount == 1) "distraction" else "distractions"
        "You focused for ${minutesSpent}m with only $distractionsCount $dWord!"
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
            scope.launch { saveShareCard(context, minutesSpent, streakDays, dateLine) }
        } else {
            Toast.makeText(context, "Storage permission is needed to save the image.", Toast.LENGTH_SHORT).show()
        }
    }

    // v1.2.1: shares the card PNG via FileProvider through the system sheet
    // (WhatsApp/Instagram/every installed app); text-only fallback on failure.
    fun shareWithImage(toWhatsApp: Boolean) {
        scope.launch {
            shareCardImageViaSystem(context, minutesSpent, streakDays, dateLine, shareText, toWhatsApp)
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
                        scope.launch { saveShareCard(context, minutesSpent, streakDays, dateLine) }
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
 * v1.1 — Minimal aesthetic share card.
 *
 * ONLY the essentials: total study time (hero), session/date line, small
 * streak. The mascot appears in the CELEBRATION pose as the USER'S BUDDY
 * (equipped wardrobe items + level styling via CelebrateBuddyMascot).
 * No emoji, no extra blocks.
 */
@Composable
private fun ShareCardContent(
    minutes: Int,
    streakDays: Int,
    dateLine: String
) {
    val lime = Color(0xFF8CE000)
    val ink = Color(0xFF121511)
    val cardBg = Color(0xFF1D221C)
    val softText = Color(0xFFA0A89E)
    val brightText = Color(0xFFF0F4ED)

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
                .background(cardBg)
                .border(1.5.dp, lime.copy(alpha = 0.5f), RoundedCornerShape(36.dp))
                .padding(horizontal = 48.dp, vertical = 40.dp)
        ) {
            // Dressed buddy in celebration pose — the user's ONE buddy identity.
            CelebrateBuddyMascot(
                modifier = Modifier.fillMaxWidth(0.55f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            // Hero: total study time
            Text(
                text = "${minutes}m",
                color = lime,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PoppinsFontFamily,
                letterSpacing = (-2).sp
            )
            Text(
                text = "FOCUS TIME",
                color = brightText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Session/date line
            Text(
                text = dateLine,
                color = softText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Small streak
            Text(
                text = "$streakDays day streak",
                color = lime.copy(alpha = 0.9f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
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
 * v1.1 FIX — renders the card IN-PROCESS by briefly attaching a ComposeView to
 * the Activity's decor view (INVISIBLE), awaiting one layout pass, then drawing
 * it to a bitmap.
 *
 * ROOT CAUSE of the "app exits on Save" bug: the old path used
 * TYPE_APPLICATION_OVERLAY via WindowManager.addView(), which requires
 * SYSTEM_ALERT_WINDOW. On Android 16 (SDK 36), when the overlay permission is
 * missing/revoked — or when the OverlayLifecycleHost's lifecycle teardown
 * races the Activity's window token — addView/removeView throws or the host
 * teardown tears down MainActivity's window, killing the app on Save/Share.
 *
 * New path: no overlay, no WindowManager, no lifecycle host. The ComposeView
 * inherits the Activity's lifecycle owner automatically, renders invisibly,
 * and is detached after capture. The Activity is never at risk.
 *
 * Must be called from a coroutine (suspends on Main until the frame is drawn).
 * Returns null when anything fails.
 */
private suspend fun captureShareCardBitmap(
    context: Context,
    minutes: Int,
    streakDays: Int,
    dateLine: String
): Bitmap? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
        val activity = context as? Activity
        if (activity == null) {
            if (!cont.isCompleted) cont.resume(null) {}
            return@suspendCancellableCoroutine
        }
        var composeView: ComposeView? = null
        fun cleanup() {
            try {
                composeView?.let { v ->
                    (v.parent as? android.view.ViewGroup)?.removeView(v)
                }
            } catch (_: Exception) { }
            composeView = null
        }
        cont.invokeOnCancellation { cleanup() }
        try {
            val decor = activity.window.decorView as android.view.ViewGroup
            composeView = ComposeView(context).apply {
                // INVISIBLE (not GONE): measures/layouts/composes, never flashes.
                visibility = android.view.View.INVISIBLE
                setContent { ShareCardContent(minutes, streakDays, dateLine) }
            }
            val view = composeView!!
            decor.addView(
                view,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
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
        } catch (e: Exception) {
            cleanup()
            if (!cont.isCompleted) cont.resume(null) {}
        }
    }
}

/**
 * v1.2.1 — capture with a watchdog. The old path could suspend forever when
 * the invisible ComposeView never laid out (both Share and Download then
 * looked "broken" with zero feedback). Now a missed frame fails loud with
 * a toast instead of hanging.
 */
private suspend fun captureShareCardBitmapGuarded(
    context: Context,
    minutes: Int,
    streakDays: Int,
    dateLine: String
): Bitmap? {
    val bitmap = withTimeoutOrNull(8000L) {
        captureShareCardBitmap(context, minutes, streakDays, dateLine)
    }
    if (bitmap == null) {
        Log.e("ShareCard", "Card capture failed or timed out (8s)")
    }
    return bitmap
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
            val dir = java.io.File(context.cacheDir, "recap_shares").apply { mkdirs() }
            // Prune older share files so the cache never grows.
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)
                ?.forEach { runCatching { it.delete() } }
            val file = java.io.File(dir, "focivo-share-${System.currentTimeMillis()}.png")
            var ok = false
            file.outputStream().use { out -> ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (!ok) {
                runCatching { file.delete() }
                return@withContext null
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Log.e("ShareCard", "Cache write failed: ${e.message}")
            null
        }
    }

/**
 * Renders the card and saves the PNG to the gallery (Pictures/Focivo).
 * @return the MediaStore Uri of the saved image, or null on failure.
 */
private suspend fun saveShareCardImage(
    context: Context,
    minutes: Int,
    streakDays: Int,
    dateLine: String
): Uri? {
    val bitmap = captureShareCardBitmapGuarded(context, minutes, streakDays, dateLine) ?: return null
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
            Log.e("ShareCard", "Gallery save failed: ${e.message}")
            null
        }
    }
}

private suspend fun saveShareCard(
    context: Context,
    minutes: Int,
    streakDays: Int,
    dateLine: String
) {
    val uri = saveShareCardImage(context, minutes, streakDays, dateLine)
    withContext(Dispatchers.Main) {
        Toast.makeText(
            context,
            if (uri != null) "Image saved to gallery." else "Could not save the image. Please try again.",
            Toast.LENGTH_SHORT
        ).show()
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
            Log.e("ShareCard", "Share sheet failed: ${e.message}")
            Toast.makeText(context, "No app available to share.", Toast.LENGTH_SHORT).show()
            // WhatsApp missing — fall back to the generic sheet / text.
            if (toWhatsApp) shareToWhatsApp(context, text)
        }
    }
}

/**
 * v1.2.1 — Share entry point: capture -> FileProvider cache PNG -> system
 * share sheet. Text-only fallback with a toast when the image can't be made.
 */
private suspend fun shareCardImageViaSystem(
    context: Context,
    minutes: Int,
    streakDays: Int,
    dateLine: String,
    text: String,
    toWhatsApp: Boolean
) {
    val bitmap = captureShareCardBitmapGuarded(context, minutes, streakDays, dateLine)
    if (bitmap == null) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Couldn't render the card — sharing text instead.", Toast.LENGTH_SHORT).show()
        }
        if (toWhatsApp) shareToWhatsApp(context, text) else shareGeneric(context, text)
        return
    }
    val uri = writeShareCardToCache(context, bitmap)
    if (!bitmap.isRecycled) bitmap.recycle()
    if (uri == null) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Couldn't prepare the image — sharing text instead.", Toast.LENGTH_SHORT).show()
        }
        if (toWhatsApp) shareToWhatsApp(context, text) else shareGeneric(context, text)
        return
    }
    shareCardViaSystemSheet(context, text, toWhatsApp, uri)
}
