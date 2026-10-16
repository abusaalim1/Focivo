package com.example.ui.components

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import com.example.util.MascotOutfitSet
import com.example.util.TimerTheme
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Motion Lab section 5 "Theme unlock" (2.2s), applied to EVERY unlock moment
 * in the app (timer themes, timer faces, mascot outfit sets).
 *
 * Beats, adapted from the lab:
 *  1. padlock icon wiggles (rotate +/-7deg, ~350ms)
 *  2. shackle springs open (lifts + rotates -28deg, 400-750ms)
 *  3. content card FLIPS (rotateY 0->180, 750-1600ms) revealing [preview]
 *  4. shimmer sweeps across the card (~700ms)
 *  5. 46 sparkle particles burst on canvas (from 1100ms)
 *  6. threshold text counts UP with an odometer feel (900-1700ms)
 *  7. "X unlocked" headline fades in (1550-1950ms)
 *  8. auto-dismiss at ~2.4s; tap anywhere dismisses early.
 *
 * Threshold model: points are never spent — a tier unlocks permanently once
 * lifetime Focus Points reach it. So the subtitle counts UP, e.g.
 * "1,000 Focus Points reached". The overlay extracts the first number in
 * [subtitle] and animates it from 0.
 *
 * Unified design system (focivoTokens), Poppins body + Instrument Serif italic
 * headline, English-only, zero unicode emojis. The lock is drawn with Canvas
 * vector paths (gold, matching the existing gold lock badges).
 */
@Composable
fun UnlockCelebrationOverlay(
    title: String,
    subtitle: String,
    accent: Color,
    preview: @Composable () -> Unit,
    onDone: () -> Unit
) {
    val T = focivoTokens()
    val density = LocalDensity.current

    // Master clock 0->1 over 2.4s; reaching the end auto-dismisses.
    val playhead = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        playhead.animateTo(targetValue = 1f, animationSpec = tween(2400, easing = LinearEasing))
        onDone()
    }
    val t = playhead.value * 2400f // elapsed ms

    // --- beats -----------------------------------------------------------
    // 1. padlock wiggle (lab: rotate(sin((T-.4)*55)*7) while T in .4-.7)
    val wiggleDeg = if (t < 350f) sin(t / 1000f * 55f) * 7f else 0f
    // 2. shackle pops/springs open (lab: up 7px + rotate -28deg, ease-out)
    val shackleP = ((t - 400f) / 350f).coerceIn(0f, 1f)
    val shackleLiftPx = with(density) { (easeOutBack(shackleP) * 10f).dp.toPx() }
    val shackleRotDeg = -28f * easeOutCubic(shackleP)
    // 3. card flip rotateY 0->180 (lab: ease-in-out over .6s at T 1.1-1.7)
    val flipF = FastOutSlowInEasing.transform(((t - 750f) / 850f).coerceIn(0f, 1f))
    val flipDeg = flipF * 180f
    // 4. shimmer sweep ~700ms, alpha = sin(progress * PI)
    val shimmerP = ((t - 750f) / 700f).coerceIn(0f, 1f)
    val shimmerAlpha = sin(shimmerP * PI.toFloat()).coerceIn(0f, 1f)
    val cardWidthPx = with(density) { 200.dp.toPx() }
    // 5. sparkle burst
    val burstAge = ((t - 1100f) / 1100f).coerceIn(0f, 1f)
    // 6. threshold count-up (odometer feel)
    val threshold = remember(subtitle) {
        Regex("[0-9][0-9,]*").find(subtitle)?.value?.replace(",", "")?.toIntOrNull() ?: 0
    }
    val countP = easeOutCubic(((t - 900f) / 800f).coerceIn(0f, 1f))
    val shownPoints = (countP * threshold).toInt()
    val renderedSubtitle = remember(subtitle, shownPoints) {
        Regex("[0-9][0-9,]*").replaceFirst(subtitle, formatPoints(shownPoints))
    }
    // 7. headline fade-in
    val labelAlpha = ((t - 1550f) / 400f).coerceIn(0f, 1f)
    val eyebrowAlpha = ((t - 100f) / 300f).coerceIn(0f, 1f)

    val sparks = remember(accent) {
        val rnd = Random(20261016)
        List(46) { i ->
            Spark(
                angle = rnd.nextFloat() * 360f,
                speed = 90f + rnd.nextFloat() * 260f,
                size = 2f + rnd.nextFloat() * 3.5f,
                color = when (i % 3) {
                    0 -> Color.White
                    1 -> accent
                    else -> UnlockGold
                },
                wobble = rnd.nextFloat() * 6.28f
            )
        }
    }

    // Rendered in its own window (Dialog) so the celebration draws above
    // bottom sheets and other dialogs; tap anywhere / back dismisses early.
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(T.scrim)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDone
                ),
            contentAlignment = Alignment.Center
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = "JUST UNLOCKED",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 2.4.sp,
                color = accent,
                modifier = Modifier.alpha(eyebrowAlpha)
            )
            Spacer(Modifier.height(14.dp))
            // Flipping card
            Box(
                modifier = Modifier
                    .size(200.dp, 250.dp)
                    .graphicsLayer {
                        rotationY = flipDeg
                        cameraDistance = 12f * density.density
                    }
            ) {
                if (flipDeg <= 90f) {
                    UnlockCardFront(
                        title = title,
                        threshold = threshold,
                        wiggleDeg = wiggleDeg,
                        shackleLiftPx = shackleLiftPx,
                        shackleRotDeg = shackleRotDeg
                    )
                } else {
                    Box(Modifier.graphicsLayer { rotationY = 180f }) {
                        UnlockCardBack(title = title, accent = accent, preview = preview)
                    }
                }
                // shimmer sweep across the card
                if (shimmerAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(56.dp)
                                .graphicsLayer {
                                    translationX = lerp(-120f, cardWidthPx + 64f, shimmerP)
                                    rotationZ = -18f
                                    alpha = shimmerAlpha
                                }
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.55f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "$title unlocked",
                fontFamily = SerifItalicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 35.sp,
                color = T.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(labelAlpha)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(T.softAccent)
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .alpha(labelAlpha)
            ) {
                Text(
                    text = renderedSubtitle,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = T.text,
                    textAlign = TextAlign.Center
                )
            }
        }
        // sparkle particles burst above everything
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (burstAge <= 0f) return@Canvas
            val origin = Offset(size.width / 2f, size.height / 2f - 40.dp.toPx())
            sparks.forEach { spark ->
                val rad = spark.angle * PI / 180.0
                val dist = spark.speed * burstAge
                val x = origin.x + (cos(rad) * dist + sin(burstAge * 5.0 + spark.wobble) * 14.0).toFloat()
                val y = origin.y + (sin(rad) * dist - 70.0 * burstAge + 170.0 * burstAge * burstAge).toFloat()
                drawCircle(
                    color = spark.color,
                    radius = spark.size * (1f - burstAge * 0.45f),
                    center = Offset(x, y),
                    alpha = (1f - burstAge).coerceIn(0f, 1f)
                )
            }
        }
        }
    }
}

/** Gold used for the celebration lock — matches the app's gold lock badges. */
private val UnlockGold = Color(0xFFF5A524)

private data class Spark(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val wobble: Float
)

private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

private fun easeOutCubic(p: Float): Float = 1f - (1f - p).pow(3)

private fun easeOutBack(p: Float): Float =
    1f + 2.70158f * (p - 1f).pow(3) + 1.70158f * (p - 1f).pow(2)

/** US-grouped points, e.g. 1000 -> "1,000". */
fun formatPoints(points: Int): String =
    NumberFormat.getNumberInstance(Locale.US).format(points)

// ---------------------------------------------------------------------------
// Card faces
// ---------------------------------------------------------------------------

/** Front: canvas-drawn padlock (wiggle + shackle pop), title, threshold pill. */
@Composable
private fun UnlockCardFront(
    title: String,
    threshold: Int,
    wiggleDeg: Float,
    shackleLiftPx: Float,
    shackleRotDeg: Float
) {
    val T = focivoTokens()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(T.surface)
            .border(1.dp, T.line, RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer { rotationZ = wiggleDeg },
            contentAlignment = Alignment.Center
        ) {
            // shackle pops/springs away from the body
            Canvas(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        translationY = -shackleLiftPx
                        rotationZ = shackleRotDeg
                        transformOrigin = TransformOrigin(0.69f, 0.44f)
                    }
            ) { drawShackle(UnlockGold) }
            Canvas(modifier = Modifier.size(96.dp)) { drawLockBody(UnlockGold, T.bg) }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = T.text,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(UnlockGold.copy(alpha = 0.14f))
                .border(1.dp, UnlockGold.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "${formatPoints(threshold)} pts",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = UnlockGold
            )
        }
    }
}

/** Back (revealed by the flip): accent gradient, [preview], UNLOCKED + title. */
@Composable
private fun UnlockCardBack(
    title: String,
    accent: Color,
    preview: @Composable () -> Unit
) {
    val onAccent = Color(0xFF0A0A0B)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.6f))))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            preview()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "UNLOCKED",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 3.sp,
            color = onAccent.copy(alpha = 0.75f)
        )
        Text(
            text = title,
            fontFamily = SerifItalicFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = onAccent,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Canvas-drawn padlock (vector paths, no emoji)
// ---------------------------------------------------------------------------

/** Shackle arch on a 64-unit viewBox (lab path: M20 28 V20 a12 12 0 0 1 24 0 v8). */
private fun DrawScope.drawShackle(color: Color) {
    val u = size.width / 64f
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(20f * u, 8f * u),
        size = Size(24f * u, 24f * u),
        style = Stroke(width = 5f * u, cap = StrokeCap.Round)
    )
    // legs down into the body
    drawLine(
        color = color,
        start = Offset(20f * u, 19f * u),
        end = Offset(20f * u, 30f * u),
        strokeWidth = 5f * u,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(44f * u, 19f * u),
        end = Offset(44f * u, 30f * u),
        strokeWidth = 5f * u,
        cap = StrokeCap.Round
    )
}

/** Lock body (lab: rect x14 y28 w36 h28 rx8, keyhole circle at 32,42 r4). */
private fun DrawScope.drawLockBody(color: Color, dark: Color) {
    val u = size.width / 64f
    drawRoundRect(
        color = color,
        topLeft = Offset(14f * u, 28f * u),
        size = Size(36f * u, 28f * u),
        cornerRadius = CornerRadius(8f * u)
    )
    drawCircle(
        color = dark,
        radius = 4f * u,
        center = Offset(32f * u, 41f * u)
    )
    drawRoundRect(
        color = dark,
        topLeft = Offset(30.4f * u, 41f * u),
        size = Size(3.2f * u, 8f * u),
        cornerRadius = CornerRadius(1.6f * u)
    )
}

// ---------------------------------------------------------------------------
// Preview cards handed to the overlay by each unlock moment
// ---------------------------------------------------------------------------

/** Theme preview: gradient card from the theme's own arc colors + serif name. */
@Composable
fun ThemeUnlockPreview(theme: TimerTheme, modifier: Modifier = Modifier) {
    val colors = theme.arcColors.ifEmpty { listOf(theme.accent, theme.accent.copy(alpha = 0.55f)) }
    Box(
        modifier = modifier
            .size(150.dp, 168.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = theme.name,
                fontFamily = SerifItalicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "THEME",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

/** Outfit-set preview: the set's mascot on a soft wash. */
@Composable
fun OutfitUnlockPreview(set: MascotOutfitSet, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(150.dp, 168.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = set.standingRes),
            contentDescription = set.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(150.dp)
                .aspectRatio(1408f / 1760f)
        )
    }
}

// ---------------------------------------------------------------------------
// Unlock-moment tracking
// ---------------------------------------------------------------------------

/**
 * Result of [rememberNewlyUnlockedIds]: ids that newly crossed their
 * threshold, plus [consume] to clear the pending list after queueing.
 */
class UnlockTracker(
    val newlyUnlocked: List<String>,
    val consume: () -> Unit
)

/**
 * Diffs the unlocked set whenever [focusPoints] rises and returns the ids
 * that newly crossed their threshold (an "unlock moment" = the user's
 * lifetime Focus Points newly reaching a tier — points are never spent).
 *
 * The first observation seeds silently (persisted under [seenKey] when given,
 * so veteran users are never spammed for tiers they earned long ago — the
 * same seeding pattern as the unlock notifications); afterwards only genuine
 * new crossings are reported. Call [UnlockTracker.consume] once the ids have
 * been queued for celebration.
 */
@Composable
fun rememberNewlyUnlockedIds(
    allIds: List<String>,
    isUnlocked: (String) -> Boolean,
    focusPoints: Int,
    seenKey: String? = null
): UnlockTracker {
    val context = LocalContext.current
    val seenIds = remember(seenKey) { mutableSetOf<String>() }
    var pending by remember(seenKey) { mutableStateOf(listOf<String>()) }
    var seeded by remember(seenKey) { mutableStateOf(false) }

    LaunchedEffect(focusPoints, seenKey) {
        val nowUnlocked = allIds.filter { id ->
            try {
                isUnlocked(id)
            } catch (_: Exception) {
                false
            }
        }
        if (!seeded) {
            val prefs = seenKey?.let {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            val persisted = prefs?.getStringSet(seenKey, null).orEmpty()
            seenIds.clear()
            seenIds.addAll(persisted + nowUnlocked)
            prefs?.edit()?.putStringSet(seenKey, seenIds.toSet())?.apply()
            seeded = true
        } else {
            val fresh = nowUnlocked.filter { it !in seenIds }
            if (fresh.isNotEmpty()) {
                seenIds.addAll(fresh)
                seenKey?.let {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putStringSet(it, seenIds.toSet()).apply()
                }
                pending = (pending + fresh).distinct()
            }
        }
    }

    return UnlockTracker(newlyUnlocked = pending, consume = { pending = emptyList() })
}

/** One queued celebration. [preview] is the revealed content on the card back. */
class PendingUnlock(
    val title: String,
    val subtitle: String,
    val accent: Color,
    val preview: @Composable () -> Unit
)

private const val PREFS_NAME = "focivo_unlock_celebrations"
