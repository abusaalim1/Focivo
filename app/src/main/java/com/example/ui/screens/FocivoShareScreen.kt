package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/* ═══════════════════════════ Design tokens (CSS :root + card colors) ═══════════════════════════ */

private val Lime = Color(0xFF8CE000)          // --lime
private val Ink = Color(0xFF0D110E)
private val ScreenBg = Color(0xFF0A0C0B)      // .scr background
private val Edge = Color(0xFF27381F)          // chip / card-4 / action border
private val DarkLime = Color(0xFF4E8F00)      // card-2 accent

// Fonts: the app ships a single static Outfit (res/font/outfit.ttf) plus
// instrument_serif_italic.ttf — see the Outfit note below.
private val Serif = FontFamily(Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic))
// NOTE: the app ships a single static Outfit file (R.font.outfit), so every
// weight resolves to it — the HTML's 400/500/600/700 nuance is collapsed.
private val Outfit = FontFamily(Font(R.font.outfit))

private fun ts(
    family: FontFamily,
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    lh: Float? = null,
    ls: Float = 0f,
    color: Color = Color.White,
    align: TextAlign = TextAlign.Center,
    italic: Boolean = false,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    fontFamily = family,
    letterSpacing = ls.sp,
    lineHeight = if (lh != null) (size * lh).sp else TextUnit.Unspecified,
    textAlign = align,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Instrument Serif italic 400 */
private fun sf(size: Float, lh: Float, color: Color = Color.White) =
    ts(Serif, size, FontWeight.Normal, lh, 0f, color, italic = true)

/* ═══════════════════════════ Timeline (CSS transitions .in) ═══════════════════════════ */

private val Ease = CubicBezierEasing(.25f, .1f, .25f, 1f)        // CSS "ease"
private val SlotEase = CubicBezierEasing(.2f, .8f, .2f, 1f)      // .slot cubic-bezier
private const val END_MS = 1300f                                  // go button: .6s delay + .7s
private val SlotDelays = listOf(0, 120, 240, 360)

private typealias Clock = Animatable<Float, AnimationVector1D>

private fun prog(t: Float, delayMs: Int, durMs: Int, e: Easing): Float =
    e.transform(((t - delayMs) / durMs).coerceIn(0f, 1f))

/* ═══════════════════════════ Helpers (same logic as the JS) ═══════════════════════════ */

/** "45m" / "1h 05m" — shared with SessionCompleteScreen's celebrate stage. */
internal fun hm(m: Int) =
    if (m < 60) "${m}m" else "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m"

private fun message(m: Int) = when {
    m < 10 -> "Small steps still count."
    m < 30 -> "Your future self says thanks."
    m < 60 -> "Focus is a muscle. You just trained it."
    else -> "Deep work: unlocked."
}

private const val LOCK_PATH =
    "M18 8h-1V6A5 5 0 007 6v2H6a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V10a2 2 0 00-2-2zM9 6a3 3 0 016 0v2H9z"
private const val SHARE_LINES = "M8.600 13.500l6.800 4M15.400 6.500l-6.800 4"
private const val DOWNLOAD_PATH = "M12 4v11M7 11l5 5 5-5M5 20h14"

/* ═══════════════════════════ THE SCREEN ═══════════════════════════ */

/**
 * Focivo share screen (360dp-wide design, same as the HTML).
 *
 * @param animationKey  change to replay the entry animation
 * @param instant       true => skip the animation, jump to the final state
 * @param toast         non-null => a small toast bubble shows above the button
 */
@Composable
fun FocivoShareScreen(
    minutes: Int,
    distractions: Int,
    modifier: Modifier = Modifier,
    name: String = "",
    /** Real data (owner: every value must be the user's own, never faked). */
    todayTotalMin: Int = minutes,
    todaySessions: Int = 1,
    /** Last-7-day focus minutes, oldest..today — the HTML highlights the last bar. */
    weekMinutes: List<Int> = List(7) { 0 },
    /** Apps the shield really intercepted during THIS session. */
    blockedApps: List<ShareBlockedApp> = emptyList(),
    animationKey: Int = 0,
    instant: Boolean = false,
    toast: String? = null,
    /** Reports the currently visible card so share/download act on it. */
    onPageChange: (Int) -> Unit = {},
    onContinue: () -> Unit = {},
    onWhatsApp: () -> Unit = {},
    onMore: () -> Unit = {},
    onDownload: () -> Unit = {},
) {
    val m = minutes
    val d = distractions
    val n = name.trim()
    val date = remember { shareDateLabel() }

    val clock = remember { Animatable(0f) }
    val listState = rememberLazyListState()

    LaunchedEffect(animationKey, instant) {
        if (instant) {
            clock.snapTo(END_MS)
        } else {
            clock.snapTo(0f)
            listState.scrollToItem(0)
            delay(32)
            clock.animateTo(END_MS, tween(END_MS.toInt(), easing = LinearEasing))
        }
    }

    val stepPx = with(LocalDensity.current) { 302.dp.toPx() } // 288 slot + 14 gap
    val page by remember {
        derivedStateOf {
            ((listState.firstVisibleItemIndex * stepPx + listState.firstVisibleItemScrollOffset) / stepPx)
                .roundToInt().coerceIn(0, 3)
        }
    }
    LaunchedEffect(page) { onPageChange(page) }

    val wd = if (d > 0) "$d distraction${if (d > 1) "s" else ""}, and you still showed up."
    else "without a single distraction."

    val cardList: List<@Composable () -> Unit> = listOf(
        { RingCard(date, m, d, n) },
        { TodayCard(todayTotalMin, todaySessions, weekMinutes) },
        { MessageCard(date, m, d) },
        { ShieldCard(blockedApps, m) },
    )

    Box(
        modifier
            .fillMaxSize()
            .background(ScreenBg)
            .drawBehind { // .scr:before radial glow
                drawRect(
                    Brush.radialGradient(
                        0f to Lime.copy(alpha = .14f),
                        .6f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height * .3f),
                        radius = hypot(size.width / 2f, size.height * .7f),
                    )
                )
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── .ttl
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 52.dp)
                    .graphicsLayer {
                        val p = prog(clock.value, 0, 700, Ease)
                        alpha = p
                        translationY = (1f - p) * 12.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val small = ts(Outfit, 15f, color = Color.White.copy(alpha = .65f))
                Text("Yay! You focused for", style = small)
                CountText(clock, m)
                Text(wd, style = small)
            }

            // ── .trk (snap carousel)
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = 26.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(),
            ) {
                itemsIndexed(cardList) { i, card -> CardSlot(SlotDelays[i], clock, card) }
            }

            // ── .dots
            Dots(count = cardList.size, current = page)

            // ── .act
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp)
                    .graphicsLayer { alpha = prog(clock.value, 500, 700, Ease) },
                horizontalArrangement = Arrangement.spacedBy(34.dp, Alignment.CenterHorizontally),
            ) {
                // Real WhatsApp logo (owner requirement; v1.2.2 added the asset).
                ActionItem("WhatsApp", Color(0xFF25D366), null, onWhatsApp) {
                    Image(
                        painterResource(R.drawable.ic_whatsapp), null,
                        Modifier.size(28.dp), contentScale = ContentScale.Fit,
                    )
                }
                ActionItem("More", Color(0xFF1B211C), Edge, onMore) {
                    Svg24(24.dp) {
                        val st = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        drawCircle(Color.White, 3f, Offset(18f, 5f), style = st)
                        drawCircle(Color.White, 3f, Offset(6f, 12f), style = st)
                        drawCircle(Color.White, 3f, Offset(18f, 19f), style = st)
                        drawPath(parsePath(SHARE_LINES), Color.White, style = st)
                    }
                }
                ActionItem("Download", Color(0xFF1B211C), Edge, onDownload) {
                    Svg24(24.dp) {
                        drawPath(
                            parsePath(DOWNLOAD_PATH), Color.White,
                            style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
        }

        // ── .go (Continue)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .fillMaxWidth()
                .height(50.dp)
                .graphicsLayer { alpha = prog(clock.value, 600, 700, Ease) }
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF95EA00), Color(0xFF7ED000))))
                .clickable(onClick = onContinue),
            contentAlignment = Alignment.Center,
        ) {
            Text("Continue", style = ts(Outfit, 15f, FontWeight.Bold, color = Ink))
        }

        // ── .toast
        ToastBubble(toast, Modifier.align(Alignment.BottomCenter))
    }
}

/* ═══════════════════════════ Small pieces ═══════════════════════════ */

@Composable
private fun CountText(clock: Clock, minutes: Int) {
    val p = (clock.value / 1000f).coerceIn(0f, 1f)
    val v = (minutes * (1f - (1f - p).pow(3))).roundToInt()
    Text(
        hm(v),
        style = sf(62f, 1.05f, Lime),
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
    )
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == current
            val w by animateDpAsState(if (on) 20.dp else 6.dp, tween(300, easing = Ease), label = "dotW")
            val c by animateColorAsState(
                if (on) Lime else Color(0xFF3A413B), tween(300, easing = Ease), label = "dotC",
            )
            Box(
                Modifier
                    .width(w)
                    .height(6.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(c)
            )
        }
    }
}

@Composable
private fun ActionItem(
    label: String,
    bg: Color,
    border: Color?,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(bg)
                .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(8.dp))
        Text(label, style = ts(Outfit, 12f, color = Color.White.copy(alpha = .65f)))
    }
}

@Composable
private fun ToastBubble(text: String?, modifier: Modifier) {
    val shown = remember { mutableStateOf("") }
    if (text != null) shown.value = text
    val a by animateFloatAsState(if (text != null) 1f else 0f, tween(300, easing = Ease), label = "toast")
    Text(
        shown.value,
        style = ts(Outfit, 12.5f),
        maxLines = 1,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 92.dp)
            .graphicsLayer {
                alpha = a
                translationY = (1f - a) * 10.dp.toPx()
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF242A25))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** 24x24 viewBox canvas (SVG jaisa) — andar ke coordinates SVG ke same hain. */
@Composable
private fun Svg24(dp: Dp, draw: DrawScope.() -> Unit) {
    Canvas(Modifier.size(dp)) {
        scale(this.size.width / 24f, Offset.Zero) { draw() }
    }
}

private fun parsePath(d: String) = PathParser().parsePathString(d).toPath()

/* ═══════════════════════════ Card slot + frame ═══════════════════════════ */

/** .slot (288x360) — andar 360x450 ka card 0.8 scale (top-left origin) */
@Composable
private fun CardSlot(delayMs: Int, clock: Clock, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(288.dp, 360.dp)
            .graphicsLayer {
                val p = prog(clock.value, delayMs, 800, SlotEase)
                alpha = p
                translationY = (1f - p) * 40.dp.toPx()
                val sc = .96f + .04f * p
                scaleX = sc
                scaleY = sc
            }
    ) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .graphicsLayer {
                    scaleX = .8f
                    scaleY = .8f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .size(360.dp, 450.dp)
        ) { content() }
    }
}

/** .card — radius 30, padding 26, shadow, overflow hidden */
@Composable
private fun CardFrame(
    drawBg: DrawScope.() -> Unit,
    borderColor: Color? = null,
    underlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxSize()
            .shadow(
                24.dp, shape,
                ambientColor = Color.Black.copy(alpha = .45f),
                spotColor = Color.Black.copy(alpha = .45f),
            )
            .clip(shape)
            .drawBehind(drawBg)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
    ) {
        underlay()
        Column(
            Modifier
                .fillMaxSize()
                .padding(if (borderColor != null) 27.dp else 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** .top — logo + FOCIVO + label */
@Composable
private fun TopBar(label: String, brand: Color, textColor: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.focivo_logo), null,
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Text("FOCIVO", style = ts(Outfit, 11f, FontWeight.Bold, ls = 2.4f, color = brand))
        }
        Text(label, style = ts(Outfit, 12f, FontWeight.Medium, ls = .5f, color = textColor.copy(alpha = .6f)))
    }
}

@Composable
private fun Chip(text: String, bg: Color, border: Color, fg: Color) {
    Text(
        text,
        style = ts(Outfit, 11.5f, FontWeight.Medium, color = fg),
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    )
}

private fun distractionLabel(d: Int) = "$d distraction${if (d == 1) "" else "s"}"

/* ═══════════════════════════ Card 1 — ring ═══════════════════════════ */

@Composable
private fun RingCard(date: String, m: Int, d: Int, n: String) {
    val pr = min(1f, m / 60f)
    CardFrame(
        drawBg = {
            drawRect(Ink)
            drawRect(
                Brush.radialGradient(
                    0f to Lime.copy(alpha = .18f),
                    .62f to Color.Transparent,
                    center = Offset(size.width / 2f, size.height * .38f),
                    radius = hypot(size.width / 2f, size.height * .62f),
                )
            )
        },
    ) {
        TopBar(date, Lime, Color.White)

        // .ring
        Box(Modifier.padding(top = 24.dp).size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val u = size.width / 150f
                val c = Offset(75f * u, 75f * u)
                drawCircle(Lime.copy(alpha = .14f), 68f * u, c, style = Stroke(8f * u))
                drawArc(
                    Lime, -90f, 360f * pr, false,
                    topLeft = Offset(7f * u, 7f * u), size = Size(136f * u, 136f * u),
                    style = Stroke(8f * u, cap = StrokeCap.Round),
                )
                val a = pr * 6.2832f
                val tip = Offset((75f + 68f * sin(a)) * u, (75f - 68f * cos(a)) * u)
                drawCircle(Lime, 7f * u, tip)
                drawCircle(Ink, 7f * u, tip, style = Stroke(3f * u))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (m >= 60) hm(m) else "$m",
                    style = ts(Outfit, if (m >= 60) 28f else 50f, FontWeight.Bold, lh = 1f, ls = -1.5f),
                )
                Text(
                    if (m >= 60) "FOCUSED" else if (m == 1) "MINUTE" else "MINUTES",
                    modifier = Modifier.padding(top = 6.dp),
                    style = ts(Outfit, 10f, lh = 1f, ls = 2.6f, color = Color.White.copy(alpha = .6f)),
                )
            }
        }

        // .h  →  Well done, <em>name</em>.
        Text(
            buildAnnotatedString {
                append("Well done")
                if (n.isNotEmpty()) {
                    append(", ")
                    withStyle(SpanStyle(color = Lime)) { append(n) }
                    append(".")
                } else {
                    withStyle(SpanStyle(color = Lime)) { append(".") }
                }
            },
            style = sf(36f, 1.05f),
            modifier = Modifier.padding(top = 20.dp),
        )

        // .sb
        Text(
            if (d > 0) "$d distractions, still showed up." else "Zero distractions. Pure focus.",
            style = ts(Outfit, 13f, lh = 1.4f, color = Color.White.copy(alpha = .65f)),
            modifier = Modifier
                .padding(top = 8.dp)
                .widthIn(max = 250.dp),
        )

        // .chips
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Chip("✓ Completed", Color(0xFF161B17), Edge, Color.White)
            Chip(distractionLabel(d), Color(0xFF161B17), Edge, Color.White)
        }

        Spacer(Modifier.weight(1f))
        Quote(message(m), Color.White)
    }
}

/** .q */
@Composable
private fun Quote(text: String, color: Color) {
    Text(
        text,
        style = sf(18f, 1.25f, color.copy(alpha = .9f)),
        modifier = Modifier.widthIn(max = 260.dp),
    )
}

/* ═══════════════════════════ Card 2 — today ═══════════════════════════ */

/**
 * v1.3.0 — week-graph bar fractions for the "Total focus today" card.
 * [week] = 7 per-day study minutes (oldest..today) from real session data.
 * Returns 7 fill fractions (0..1): each bar is STRICTLY proportional to that
 * day's real minutes (v / max), with a minimal 8% floor so a zero day still
 * renders a stub (same floor as the owner's HTML: Math.max(8, v/mx*100)).
 * Shorter lists are padded with zero days at the end (today side), matching
 * the pre-v1.3.0 behavior; production always passes exactly 7.
 * Unit-tested in WeekBarFractionsTest.
 */
internal fun weekBarFractions(week: List<Int>): List<Float> {
    val wk = (week + List(7) { 0 }).take(7)
    val mx = wk.max().coerceAtLeast(1)
    return wk.map { v -> max(8f, v.toFloat() / mx * 100f) / 100f }
}

@Composable
private fun TodayCard(todayTotal: Int, sessions: Int, week: List<Int>) {
    // Real data only: today's total minutes, today's session count, and the
    // last-7-day minutes (oldest..today; the last bar is today, lime).
    val fractions = weekBarFractions(week)
    val fg = Color(0xFF14180F)

    CardFrame(drawBg = { drawRect(Color(0xFFF3F1E8)) }) {
        TopBar("Today", DarkLime, fg)

        Text(
            "TOTAL FOCUS TODAY",
            style = ts(Outfit, 11f, ls = 3f, color = fg.copy(alpha = .55f)),
            modifier = Modifier.padding(top = 30.dp),
        )
        Text(hm(todayTotal), style = sf(76f, 1f, fg), modifier = Modifier.padding(top = 34.dp))

        // .sb — session count, like the owner's HTML ("N sessions").
        Text(
            "$sessions ${if (sessions == 1) "session" else "sessions"}",
            style = ts(Outfit, 13f, lh = 1.4f, color = fg.copy(alpha = .65f)),
            modifier = Modifier.padding(top = 8.dp),
        )

        // .bars
        Row(
            Modifier
                .padding(top = 26.dp)
                .fillMaxWidth()
                .height(110.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            fractions.forEachIndexed { i, frac ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(frac)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (i == 6) Lime else DarkLime.copy(alpha = .22f))
                )
            }
        }

        // .days
        Row(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            "MTWTFSS".forEach { ch ->
                Text(
                    ch.toString(),
                    modifier = Modifier.weight(1f),
                    style = ts(Outfit, 11f, color = fg.copy(alpha = .5f)),
                )
            }
        }
    }
}

/* ═══════════════════════════ Card 3 — message ═══════════════════════════ */

@Composable
private fun MessageCard(date: String, m: Int, d: Int) {
    val measurer = rememberTextMeasurer()
    CardFrame(
        drawBg = {
            // linear-gradient(160deg,#9BEA10,#D4F56A)
            val a = Math.toRadians(160.0)
            val dx = sin(a).toFloat()
            val dy = (-cos(a)).toFloat()
            val len = abs(size.width * dx) + abs(size.height * dy)
            val c = Offset(size.width / 2f, size.height / 2f)
            val dir = Offset(dx, dy) * (len / 2f)
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0xFF9BEA10), Color(0xFFD4F56A)),
                    start = c - dir, end = c + dir,
                )
            )
        },
        underlay = {
            // .ghost — 270px Outfit 700, opacity .13, mix-blend-mode: overlay
            Canvas(Modifier.fillMaxSize()) {
                drawText(
                    textMeasurer = measurer,
                    text = "$m",
                    topLeft = Offset((-6).dp.toPx(), 60.dp.toPx()),
                    style = ts(
                        Outfit, 270f, FontWeight.Bold, lh = 1f, ls = -12f,
                        color = Color.White.copy(alpha = .13f), align = TextAlign.Start,
                    ),
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    blendMode = BlendMode.Overlay,
                )
            }
        },
    ) {
        TopBar(date, Ink, Ink)
        Spacer(Modifier.weight(1f))
        Text(message(m), style = sf(46f, 1.02f, Ink)) // .msg
        Spacer(Modifier.weight(1f))
        Text(
            "${hm(m)} · " + if (d > 0) distractionLabel(d).let { if (d > 1) it else "$d distraction" } else "zero distractions",
            style = ts(Outfit, 13f, FontWeight.SemiBold, ls = .5f, color = Ink.copy(alpha = .75f)),
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/* ═══════════════════════════ Card 4 — shield ═══════════════════════════ */

@Composable
private fun ShieldCard(apps: List<ShareBlockedApp>, m: Int) {
    // Real data: the apps the shield actually intercepted during THIS
    // session. Honest empty state (same design language) when N=0.
    val n = apps.size
    CardFrame(drawBg = { drawRect(Color(0xFF121714)) }, borderColor = Edge) {
        TopBar("Shield", Lime, Color.White)

        Text(
            buildAnnotatedString {
                append("$n app${if (n == 1) "" else "s"} stayed ")
                withStyle(SpanStyle(color = Lime)) { append("locked") }
            },
            style = sf(40f, 1.05f),
            modifier = Modifier.padding(top = 34.dp),
        )

        if (n > 0) {
            AppGrid(apps)
        } else {
            Text(
                "Nothing tried to distract you.",
                style = ts(Outfit, 13f, lh = 1.4f, color = Color.White.copy(alpha = .55f)),
                modifier = Modifier.padding(top = 26.dp),
            )
        }

        Spacer(Modifier.weight(1f))
        Quote("while you focused for ${hm(m)}.", Color.White)
    }
}

// filter: grayscale(1) brightness(.6)
private val DimGray = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            .12756f, .42912f, .04332f, 0f, 0f,
            .12756f, .42912f, .04332f, 0f, 0f,
            .12756f, .42912f, .04332f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

@Composable
private fun AppGrid(apps: List<ShareBlockedApp>) {
    val context = LocalContext.current
    Column(
        Modifier
            .padding(top = 26.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { app ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        val painter = remember(app.packageName) { appTilePainter(context, app) }
                        if (painter != null) {
                            Image(
                                painter, app.appName,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                colorFilter = DimGray,
                            )
                        }
                        // lock badge (.ap u)
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(7.dp, 7.dp)
                                .size(24.dp)
                                .background(Color(0xFF121714), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(20.dp).background(Lime, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Svg24(11.dp) { drawPath(parsePath(LOCK_PATH), Ink) }
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The app's REAL launcher icon via PackageManager first (owner
 * requirement); a curated official icon when that lookup fails (screenshot
 * tests), so a tile never renders blank.
 */
private fun appTilePainter(context: android.content.Context, app: ShareBlockedApp): Painter? {
    return try {
        val pm = context.packageManager
        val drawable: Drawable? = try {
            pm.getApplicationIcon(app.packageName)
        } catch (_: PackageManager.NameNotFoundException) { null }
            ?: knownAppIconRes(app.packageName)?.let { ContextCompat.getDrawable(context, it) }
            ?: app.fallbackRes?.let { ContextCompat.getDrawable(context, it) }
            ?: ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
        val bmp: Bitmap? = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            null -> null
            else -> try { drawable.toBitmap(96, 96, Bitmap.Config.ARGB_8888) } catch (_: Exception) { null }
        }
        bmp?.asImageBitmap()?.let { BitmapPainter(it) }
    } catch (_: Exception) { null }
}

/* ═══════════════════════════ Production additions ═══════════════════════════ */

private fun shareDateLabel(): String = try {
    SimpleDateFormat("MMM d", Locale.US).format(Date())
} catch (_: Exception) { "" }

/**
 * Production entry point: the owner's share screen wired to the user's real
 * data. WhatsApp / More / Download act on the currently visible card.
 */
@Composable
fun FocivoShareEntry(
    data: ShareScreenData,
    onWhatsApp: (cardIndex: Int) -> Unit,
    onMore: (cardIndex: Int) -> Unit,
    onDownload: (cardIndex: Int) -> Unit,
    onContinue: () -> Unit,
    animateEntrance: Boolean,
    modifier: Modifier = Modifier,
) {
    var currentCard by remember { mutableIntStateOf(0) }
    FocivoShareScreen(
        minutes = data.minutes,
        distractions = data.distractions,
        name = data.userName.orEmpty(),
        todayTotalMin = data.todayTotalMinutes,
        todaySessions = data.todaySessions,
        weekMinutes = data.weekMinutes,
        blockedApps = data.blockedApps,
        modifier = modifier,
        instant = !animateEntrance,
        onPageChange = { currentCard = it },
        onContinue = onContinue,
        onWhatsApp = { onWhatsApp(currentCard) },
        onMore = { onMore(currentCard) },
        onDownload = { onDownload(currentCard) },
    )
}

/**
 * Renders ONE share card at exactly 360x450dp (no carousel chrome, no entry
 * animation) for the 1080x1350 share/download PNG.
 */
@Composable
fun FocivoShareCardPng(index: Int, data: ShareScreenData) {
    val date = remember { data.dateLabel.ifBlank { shareDateLabel() } }
    Box(Modifier.size(360.dp, 450.dp)) {
        when (index.coerceIn(0, 3)) {
            0 -> RingCard(date, data.minutes, data.distractions, data.userName.orEmpty())
            1 -> TodayCard(data.todayTotalMinutes, data.todaySessions, data.weekMinutes)
            2 -> MessageCard(date, data.minutes, data.distractions)
            else -> ShieldCard(data.blockedApps, data.minutes)
        }
    }
}
