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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.example.ui.components.RollingNumber
import com.example.ui.components.RollingText
import com.example.ui.components.SessionNotificationDigestSheet
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.FocivoDarkTokens
import com.example.util.SessionNotificationDigestManager
import com.example.util.captureComposedBitmapGuarded
import com.example.util.saveBitmapToGallery
import com.example.util.shareCardViaSystemSheet
import com.example.util.shareGeneric
import com.example.util.shareToWhatsApp
import com.example.util.writeShareCardToCache
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.R
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


@Composable
fun SessionCompleteScreen(
    session: FocusSessionEntity,
    onDismiss: () -> Unit,
    /** Screenshot tests pass false for deterministic captures. */
    animateEntrance: Boolean = true
) {
    val minutesSpent = session.durationSeconds / 60
    val pointsEarned = session.focusPointsEarned

    val suppressedNotifs by SessionNotificationDigestManager.digestList.collectAsState()
    var showDigestSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // v1.2.3 (final assembly) — the owner's Session Complete Flow: staged
    // celebration (lab celebration NOT used). Stages: CELEBRATE ("Well
    // done." + points count-up) -> MESSAGE (motivational line) -> FOCUSED
    // ("Yay! You focused for Xm") -> SHARE (the 4 share cards + sheet).
    // Screenshot tests pass animateEntrance=false -> straight to SHARE.
    // (No streak anywhere: the streak feature was removed app-wide.)
    var flowStage by remember { mutableStateOf(if (animateEntrance) 0 else 3) }

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

    // Auto-advance the celebration stages; any tap skips ahead.
    LaunchedEffect(flowStage) {
        if (!animateEntrance) return@LaunchedEffect
        when (flowStage) {
            0 -> { delay(2800); flowStage = 1 }
            1 -> { delay(2400); flowStage = 2 }
            2 -> { delay(2400); flowStage = 3 }
        }
    }

    if (showDigestSheet) {
        SessionNotificationDigestSheet(onDismiss = { showDigestSheet = false })
    }

    // v1.2.2 — the owner's own share-screen design: every value is the user's
    // real data (session minutes/distractions, name, today's totals,
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
                if (toWhatsApp) shareToWhatsApp(context, shareText) else shareGeneric(context, shareText, "Share your focus session")
                return@launch
            }
            val uri = writeShareCardToCache(context, bitmap)
            if (!bitmap.isRecycled) bitmap.recycle()
            if (uri == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Couldn't prepare the image — sharing text instead.", Toast.LENGTH_SHORT).show()
                }
                if (toWhatsApp) shareToWhatsApp(context, shareText) else shareGeneric(context, shareText, "Share your focus session")
                return@launch
            }
            shareCardViaSystemSheet(context, shareText, toWhatsApp, uri, "Share your focus session")
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap anywhere to skip ahead through the celebration stages.
                if (flowStage < 3) flowStage++
            }
    ) {
        when (flowStage) {
            0 -> SessionFlowCelebrateStage(
                pointsEarned = pointsEarned,
                minutesSpent = minutesSpent,
                distractionsCount = session.distractionsCount
            )
            1 -> SessionFlowMessageStage(
                distractionsCount = session.distractionsCount,
                sessionId = session.id
            )
            2 -> SessionFlowFocusedStage(minutesSpent = minutesSpent)
            else -> SessionFlowShareStage(
                suppressedNotifs = suppressedNotifs,
                onOpenDigest = { showDigestSheet = true },
                shareContent = {
                    FocivoShareEntry(
                        data = shareData,
                        onWhatsApp = { shareCard(it, toWhatsApp = true) },
                        onMore = { shareCard(it, toWhatsApp = false) },
                        onDownload = { downloadCard(it) },
                        onContinue = onDismiss,
                        animateEntrance = animateEntrance
                    )
                }
            )
        }
        // v1.2.2 — subtle one-shot confetti over the completed screen.
        // Pointer-transparent: no click handling, so it never blocks input.
        if (showConfetti) {
            CelebrationConfetti(progress = confettiProgress.value)
        }
    }
}

/**
 * v1.2.3 (final assembly) — the owner's Session Complete Flow, implemented
 * as staged celebration composables (the lab celebration is NOT used).
 * Every number shown is the user's real data.
 */

/**
 * Stage 0 — the owner's done screen, beat for beat: lime ring sweeps to
 * full, the cheer mascot pops in inside the ring, "Well done.", "+N Focus
 * Points" counting up, "Ym · N distractions", TAP TO SKIP.
 *
 * v1.3.0 mascot decision: the HTML spec already embeds its own cheer mascot
 * (the white clay graduate, arms up) — the app packages that same art as
 * [R.drawable.mascot_celebration], so it renders here. The separate
 * black-background cheer_4 PNG is deliberately NOT used: per the task it
 * would be a second mascot on top of the spec's own.
 *
 * (No streak anywhere: the streak feature was removed app-wide.)
 */
@Composable
private fun SessionFlowCelebrateStage(
    pointsEarned: Int,
    minutesSpent: Int,
    distractionsCount: Int
) {
    val ringColor = Color(0xFF8CE000) // the HTML's --lime
    // Ring sweep 0 -> full over 0.9s, like the HTML's stroke-dasharray anim.
    val ringProgress = remember { Animatable(0f) }
    // Mascot pop-in: the HTML shows it at t=0.45s, scale 0.5 -> 1.
    val mascotScale = remember { Animatable(0.5f) }
    val mascotAlpha = remember { Animatable(0f) }
    // Staggered text fade-ins (HTML: dh@0.9s, dpts@1.1s, dmm@1.4s).
    val titleAlpha = remember { Animatable(0f) }
    val pointsAlpha = remember { Animatable(0f) }
    val dmmAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { ringProgress.animateTo(1f, tween(900, easing = LinearEasing)) }
        launch {
            delay(450)
            launch { mascotAlpha.animateTo(1f, tween(200)) }
            mascotScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f))
        }
        launch { delay(900); titleAlpha.animateTo(1f, tween(400)) }
        launch { delay(1100); pointsAlpha.animateTo(1f, tween(300)) }
        launch { delay(1400); dmmAlpha.animateTo(1f, tween(400)) }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Ring + mascot (230dp, like the HTML's #dst).
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .drawBehind {
                        // Radial lime glow behind the ring.
                        drawRect(
                            Brush.radialGradient(
                                0f to ringColor.copy(alpha = 0.20f),
                                0.7f to ringColor.copy(alpha = 0.04f),
                                1f to Color.Transparent,
                                center = Offset(size.width / 2f, size.height * 0.38f),
                                radius = size.width / 2f
                            )
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 7.dp.toPx()
                    val r = size.minDimension / 2f - stroke
                    val c = center
                    drawCircle(
                        color = ringColor.copy(alpha = 0.14f),
                        radius = r,
                        center = c,
                        style = Stroke(stroke)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * ringProgress.value,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Image(
                    painter = painterResource(R.drawable.mascot_celebration),
                    contentDescription = "Celebrating mascot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .height(170.dp)
                        .graphicsLayer {
                            scaleX = mascotScale.value
                            scaleY = mascotScale.value
                            alpha = mascotAlpha.value
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                )
            }
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                text = "Well done.",
                fontFamily = SerifItalicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                color = Color(0xFFF4F4F5),
                modifier = Modifier.graphicsLayer { alpha = titleAlpha.value }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { alpha = pointsAlpha.value }
            ) {
                RollingNumber(
                    value = pointsEarned,
                    formatter = { "+$it" },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB6F13A),
                        fontSize = 30.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Focus Points",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF76767C),
                        fontSize = 18.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${hm(minutesSpent)} · " + if (distractionsCount > 0) {
                    "$distractionsCount ${if (distractionsCount == 1) "distraction" else "distractions"}"
                } else {
                    "zero distractions"
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    // v1.3.3 (item 9): this screen is dark-always, so the caption
                    // uses the dark design-system text token at full alpha —
                    // the hardcoded 0xFFF4F4F5 @ 0.55 alpha was below 4.5:1.
                    color = FocivoDarkTokens.text,
                    fontSize = 13.sp
                ),
                modifier = Modifier.graphicsLayer { alpha = dmmAlpha.value }
            )
        }
        SessionFlowTapToSkip(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Stage 1 — the motivational line + the distraction verdict. */
@Composable
private fun SessionFlowMessageStage(distractionsCount: Int, sessionId: Long) {
    // Owner's own lines, rotated deterministically per session.
    val lines = listOf(
        "Small steps still count.",
        "Your future self says thanks.",
        "Focus is a muscle. You just trained it."
    )
    val line = lines[(sessionId % lines.size).toInt().coerceAtLeast(0)]
    val verdict = if (distractionsCount == 0) {
        "Zero distractions. Pure focus."
    } else {
        "$distractionsCount ${if (distractionsCount == 1) "distraction" else "distractions"}, still showed up."
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = verdict,
                fontFamily = SerifItalicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                color = Color(0xFFF4F4F5)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF76767C),
                    fontSize = 16.sp
                )
            )
        }
        SessionFlowTapToSkip(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Stage 2 — "Yay! You focused for Xm". */
@Composable
private fun SessionFlowFocusedStage(minutesSpent: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = "Yay! You focused for",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF76767C),
                    fontSize = 18.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Motion Lab #7 — focused minutes roll like an odometer.
            RollingText(
                text = "${minutesSpent}m",
                style = TextStyle(
                    fontFamily = SerifItalicFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 72.sp,
                    color = Color(0xFFB6F13A)
                )
            )
        }
        SessionFlowTapToSkip(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Stage 3 — the share cards + share sheet (existing plumbing). */
@Composable
private fun SessionFlowShareStage(
    suppressedNotifs: List<*>,
    onOpenDigest: () -> Unit,
    shareContent: @Composable () -> Unit
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
                    .clickable { onOpenDigest() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = Color(0xFFB6F13A),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${suppressedNotifs.size} ${if (suppressedNotifs.size == 1) "notification" else "notifications"} held during session (tap to view)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB6F13A),
                        fontSize = 12.sp
                    )
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            shareContent()
        }
    }
}

/** "TAP TO SKIP" affordance from the owner's flow. */
@Composable
private fun SessionFlowTapToSkip(modifier: Modifier = Modifier) {
    Text(
        text = "TAP TO SKIP",
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF4A4A4F),
            fontSize = 11.sp,
            letterSpacing = 2.sp
        ),
        modifier = modifier.padding(bottom = 36.dp)
    )
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
    FocivoShareCardPng(index = cardIndex, data = data)
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
