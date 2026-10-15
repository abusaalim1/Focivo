package com.example.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.model.FocusSessionEntity
import com.example.service.SessionBlockRecorder
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * v1.2.2 — the owner's OWN share-screen design, built natively in Compose.
 *
 * Source of truth: the owner's HTML "Focivo Share Screen"
 * (focivo-share-screen-preview_2_8g42.html). Every number below is ported from
 * that file's CSS/JS: card 360x450 design units, radius 30, padding 26,
 * Outfit + Instrument Serif italic, dark #0D110E family + lime #8CE000,
 * the exact MSG() quote lines, hm() formatting, chip copy, ghost number,
 * lock chips, dots, and the entrance choreography (count-up ~1s ease-out
 * cubic; cards rise staggered translateY 40dp->0 + fade .8s
 * cubic-bezier(.2,.8,.2,1)).
 *
 * ALL values are the user's real data (minutes/distractions of the
 * just-finished session, profile name, today's totals, streak, last-7-day
 * minutes, apps the shield actually blocked during this session). Nothing
 * is faked. Zero unicode emojis anywhere (the HTML's flame is the
 * emoji3d_flame asset; the check mark is the plain U+2713 dingbat).
 */

// ---------------------------------------------------------------------------
// Fonts / palette (design tokens straight from the HTML)
// ---------------------------------------------------------------------------

private val ShareOutfit = FontFamily(Font(R.font.outfit))
private val ShareSerif = FontFamily(
    Font(R.font.instrument_serif_italic, weight = FontWeight.Normal, style = FontStyle.Italic)
)

private val ShareLime = Color(0xFF8CE000)
private val ShareInk = Color(0xFF0D110E)
private val SharePage = Color(0xFF0A0C0B)
private val ShareChipBg = Color(0xFF161B17)
private val ShareChipBorder = Color(0xFF27381f)
private val ShareCream = Color(0xFFF3F1E8)
private val ShareCreamInk = Color(0xFF14180F)
private val ShareDeepGreen = Color(0xFF4E8F00)
private val ShareK4Bg = Color(0xFF121714)
private val ShareDotIdle = Color(0xFF3a413b)
private val ShareActBg = Color(0xFF1b211c)

private val EaseOutCubic: Easing = Easing { t -> 1f - (1f - t).pow(3) }
private val CardRiseEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

// ---------------------------------------------------------------------------
// HTML logic, ported verbatim
// ---------------------------------------------------------------------------

/** MSG(m) from the owner's HTML — quote selection by minutes. */
fun shareQuoteFor(minutes: Int): String = when {
    minutes < 10 -> "Small steps still count."
    minutes < 30 -> "Your future self says thanks."
    minutes < 60 -> "Focus is a muscle. You just trained it."
    else -> "Deep work: unlocked."
}

/** hm(m) from the owner's HTML: "45m" / "1h 05m". */
fun shareHm(minutes: Int): String =
    if (minutes < 60) "${minutes}m"
    else "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

// ---------------------------------------------------------------------------
// Data — everything real, from the user's own device data
// ---------------------------------------------------------------------------

data class ShareBlockedApp(
    val packageName: String,
    val appName: String,
    /** Optional drawable fallback for the icon (used in screenshot tests where
     * PackageManager lookups fail; in prod the real launcher icon is used). */
    val fallbackRes: Int? = null
)

data class ShareScreenData(
    val minutes: Int,
    val distractions: Int,
    /** "Oct 15" (en-US, like the HTML). */
    val dateLabel: String,
    /** Null when unknown — card renders "Well done." instead. */
    val userName: String?,
    val todayTotalMinutes: Int,
    val todaySessions: Int,
    val streakDays: Int,
    /** 7 entries, oldest..today. */
    val weekMinutes: List<Int>,
    /** 7 single-letter weekday initials, matching weekMinutes. */
    val weekLabels: List<String>,
    /** Apps the shield really intercepted during this session (deduped). */
    val blockedApps: List<ShareBlockedApp>
)

private const val SHARE_PREFS = "focusly_local_data"

private data class LocalSession(val id: Long, val completedAt: Long, val durationSeconds: Int)

private fun readLocalSessions(prefs: android.content.SharedPreferences): List<LocalSession> {
    val out = mutableListOf<LocalSession>()
    try {
        val arr = JSONArray(prefs.getString("focus_sessions", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                LocalSession(
                    id = o.optLong("id", 0L),
                    completedAt = o.optLong("completedAt", 0L),
                    durationSeconds = o.optInt("durationSeconds", 0)
                )
            )
        }
    } catch (_: Exception) { }
    return out
}

private fun startOfDay(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/**
 * Builds the share data from real device state. The just-finished [session]
 * is merged in when it is not yet persisted (so "Today" includes it).
 */
fun loadShareScreenData(context: Context, session: FocusSessionEntity): ShareScreenData {
    val prefs = context.getSharedPreferences(SHARE_PREFS, Context.MODE_PRIVATE)
    val minutes = (session.durationSeconds / 60).coerceAtLeast(0)
    val distractions = session.distractionsCount.coerceAtLeast(0)

    val dateLabel = try {
        SimpleDateFormat("MMM d", Locale.US).format(Date(session.completedAt))
    } catch (_: Exception) { "" }

    // Name: same resolution as ProfileScreen (currentUserName -> email prefix),
    // but "Student"/blank means "no name" for the card headline.
    var userName: String? = null
    var streakDays = 0
    try {
        val obj = prefs.getString("user_preferences", null)?.let { JSONObject(it) }
        val raw = obj?.optString("currentUserName", "")?.trim().orEmpty()
        val email = obj?.optString("currentUserEmail", "")?.trim().orEmpty()
        userName = when {
            raw.isNotBlank() && raw != "Deep Worker" && raw != "Student" -> raw
            email.isNotBlank() -> email.substringBefore("@").takeIf { it.isNotBlank() }
            else -> null
        }
        streakDays = obj?.optInt("currentStreak", 0) ?: 0
    } catch (_: Exception) { }

    val sessions = readLocalSessions(prefs).toMutableList()
    val hasCurrent = sessions.any { it.id == session.id }
    if (!hasCurrent) {
        sessions.add(LocalSession(session.id, session.completedAt, session.durationSeconds))
    }

    val now = System.currentTimeMillis()
    val todayStart = startOfDay(now)
    val todaySessions = sessions.filter { it.completedAt >= todayStart }
    val todayTotalMinutes = todaySessions.sumOf { it.durationSeconds } / 60

    val weekMinutes = mutableListOf<Int>()
    val weekLabels = mutableListOf<String>()
    val dayNames = arrayOf("S", "M", "T", "W", "T", "F", "S") // Calendar.SUNDAY=1
    for (back in 6 downTo 0) {
        val dayStart = todayStart - back * 24L * 3600 * 1000
        val dayEnd = dayStart + 24L * 3600 * 1000
        val mins = sessions.filter { it.completedAt in dayStart until dayEnd }
            .sumOf { it.durationSeconds } / 60
        weekMinutes.add(mins)
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        weekLabels.add(dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1])
    }

    // Apps the shield intercepted inside this session's window.
    val endMs = if (session.endTime > 0) session.endTime else session.completedAt
    val startMs = when {
        session.startTime > 0 -> session.startTime
        else -> endMs - session.durationSeconds * 1000L
    }
    val blockedApps = SessionBlockRecorder.forWindow(context, startMs, endMs)
        .map { ShareBlockedApp(it.packageName, it.appName) }

    return ShareScreenData(
        minutes = minutes,
        distractions = distractions,
        dateLabel = dateLabel,
        userName = userName,
        todayTotalMinutes = todayTotalMinutes,
        todaySessions = todaySessions.size,
        streakDays = streakDays.coerceAtLeast(0),
        weekMinutes = weekMinutes,
        weekLabels = weekLabels,
        blockedApps = blockedApps
    )
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

/**
 * The full share experience (owner's HTML, natively): animated header,
 * swipeable 4-card carousel with dots, WhatsApp / More / Download acting on
 * the currently visible card, full-width Continue.
 */
@Composable
fun ShareExperienceScreen(
    data: ShareScreenData,
    onWhatsApp: (cardIndex: Int) -> Unit,
    onMore: (cardIndex: Int) -> Unit,
    onDownload: (cardIndex: Int) -> Unit,
    onContinue: () -> Unit,
    animateEntrance: Boolean = true
) {
    val pagerState = rememberPagerState(pageCount = { 4 })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SharePage)
    ) {
        // Screen radial glow: rgba(140,224,0,.14) at 50% 30% -> transparent 60%
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(ShareLime.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(w / 2f, h * 0.30f),
                    radius = w * 0.95f
                )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            ShareHeader(
                minutes = data.minutes,
                distractions = data.distractions,
                animate = animateEntrance
            )

            // .trk: gap 14, padding 26 36 8, slot 288x360 (card at 0.8 scale)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height((360 + 34).dp),
                contentPadding = PaddingValues(horizontal = 36.dp),
                pageSpacing = 14.dp,
                pageSize = PageSize.Fixed(288.dp),
                verticalAlignment = Alignment.Top
            ) { page ->
                ShareCardSlot(
                    index = page,
                    data = data,
                    animate = animateEntrance
                )
            }

            // .dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(4) { i ->
                    val on = i == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (on) 20.dp else 6.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (on) ShareLime else ShareDotIdle)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // .act row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top
            ) {
                ShareActButton(
                    label = "WhatsApp",
                    onClick = { onWhatsApp(pagerState.currentPage) }
                ) {
                    // Real WhatsApp logo: brand-green disc + white glyph (v1.2.2).
                    Icon(
                        painter = painterResource(id = R.drawable.ic_whatsapp),
                        contentDescription = "WhatsApp",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(56.dp)
                    )
                }
                Spacer(modifier = Modifier.width(34.dp))
                ShareActButton(
                    label = "More",
                    onClick = { onMore(pagerState.currentPage) }
                ) {
                    ShareActCircle {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(34.dp))
                ShareActButton(
                    label = "Download",
                    onClick = { onDownload(pagerState.currentPage) }
                ) {
                    ShareActCircle {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // .go — full-width Continue
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF95EA00), Color(0xFF7ED000))
                        )
                    )
                    .clickable(onClick = onContinue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Continue",
                    fontFamily = ShareOutfit,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ShareInk
                )
            }
        }
    }
}

@Composable
private fun ShareActCircle(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(ShareActBg)
            .border(1.dp, ShareChipBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun ShareActButton(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        icon()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontFamily = ShareOutfit,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.65f)
        )
    }
}

/** .ttl header with the ~1s ease-out-cubic count-up. */
@Composable
private fun ShareHeader(minutes: Int, distractions: Int, animate: Boolean) {
    var shown by remember { mutableStateOf(!animate) }
    LaunchedEffect(animate) {
        if (animate) {
            // .ttl transition .7s — fade/slide in with the header.
            shown = true
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(700), label = "ttlAlpha"
    )
    val slide by animateFloatAsState(
        targetValue = if (shown) 0f else 12f,
        animationSpec = tween(700), label = "ttlSlide"
    )

    val progress = remember { androidx.compose.animation.core.Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate, minutes) {
        if (animate) progress.animateTo(1f, tween(1000, easing = EaseOutCubic))
        else progress.snapTo(1f)
    }
    val counted = (minutes * EaseOutCubic.transform(progress.value)).roundToInt()
        .coerceIn(0, minutes.coerceAtLeast(0))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 52.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = slide.dp.toPx()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Yay! You focused for",
            fontFamily = ShareOutfit,
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.65f)
        )
        Text(
            text = shareHm(counted),
            fontFamily = ShareSerif,
            fontStyle = FontStyle.Italic,
            fontSize = 62.sp,
            lineHeight = (62 * 1.05f).sp,
            color = ShareLime,
            modifier = Modifier.padding(vertical = 2.dp)
        )
        Text(
            text = if (distractions == 0) "without a single distraction."
            else "$distractions distraction${if (distractions == 1) "" else "s"}, and you still showed up.",
            fontFamily = ShareOutfit,
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/** One carousel slot: 288x360, staggered rise-in (translateY 40->0, fade). */
@Composable
private fun ShareCardSlot(index: Int, data: ShareScreenData, animate: Boolean) {
    var entered by remember { mutableStateOf(!animate) }
    LaunchedEffect(animate, index) {
        if (animate) {
            kotlinx.coroutines.delay(index * 120L)
            entered = true
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(800, easing = CardRiseEasing), label = "cardAlpha$index"
    )
    val rise by animateFloatAsState(
        targetValue = if (entered) 0f else 40f,
        animationSpec = tween(800, easing = CardRiseEasing), label = "cardRise$index"
    )
    Box(
        modifier = Modifier
            .size(288.dp, 360.dp)
            .padding(top = 26.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = rise.dp.toPx()
                scaleX = 0.96f + 0.04f * alpha
                scaleY = 0.96f + 0.04f * alpha
            }
    ) {
        ShareCard(index = index, data = data, scale = 0.8f)
    }
}

// ---------------------------------------------------------------------------
// Cards — design units are 360x450; [scale] multiplies every metric.
// scale=0.8 on screen (288x360 slots), scale=3.0 for 1080x1350 share PNGs.
// ---------------------------------------------------------------------------

@Composable
fun ShareCard(index: Int, data: ShareScreenData, scale: Float) {
    when (index) {
        0 -> ShareRingCard(data, scale)
        1 -> ShareTodayCard(data, scale)
        2 -> ShareGhostCard(data, scale)
        else -> ShareShieldCard(data, scale)
    }
}

@Composable
private fun CardShell(
    scale: Float,
    background: Brush,
    borderColor: Color? = null,
    content: @Composable () -> Unit
) {
    val s = scale
    Box(
        modifier = Modifier
            .size((360 * s).dp, (450 * s).dp)
            .shadow(
                elevation = (20 * s).dp,
                shape = RoundedCornerShape((30 * s).dp),
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape((30 * s).dp))
            .background(background)
            .then(if (borderColor != null) Modifier.border((1 * s).dp, borderColor, RoundedCornerShape((30 * s).dp)) else Modifier)
            .padding((26 * s).dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    }
}

/** .top row: logo + FOCIVO left, label right. */
@Composable
private fun ShareTopRow(scale: Float, label: String, brandColor: Color, textColor: Color) {
    val s = scale
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.focivo_logo),
                contentDescription = "Focivo",
                modifier = Modifier
                    .size((26 * s).dp)
                    .clip(RoundedCornerShape((8 * s).dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width((8 * s).dp))
            Text(
                text = "Focivo",
                fontFamily = ShareOutfit,
                fontWeight = FontWeight.Bold,
                fontSize = (11 * s).sp,
                letterSpacing = (2.4 * s).sp,
                color = brandColor
            )
        }
        Text(
            text = label,
            fontFamily = ShareOutfit,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * s).sp,
            letterSpacing = (0.5 * s).sp,
            color = textColor.copy(alpha = 0.6f)
        )
    }
}

// --- Card 1: RING ------------------------------------------------------------

@Composable
private fun ShareRingCard(data: ShareScreenData, scale: Float) {
    val s = scale
    val m = data.minutes
    val d = data.distractions
    val pr = (m / 60f).coerceIn(0f, 1f)
    val lime = ShareLime

    CardShell(
        scale = s,
        background = Brush.verticalGradient(listOf(ShareInk, ShareInk))
    ) {
        // k1 radial glow behind the card body.
        // (Drawn as the shell's own background layer via Box overlay.)
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(lime.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.38f),
                        radius = size.width * 0.62f
                    )
                )
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShareTopRow(s, data.dateLabel, brandColor = lime, textColor = Color.White)

                // .ring 150x150, margin-top 24
                Spacer(modifier = Modifier.height((24 * s).dp))
                Box(
                    modifier = Modifier.size((150 * s).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = (68 * s).toPx()
                        val sw = (8 * s).toPx()
                        // track
                        drawCircle(
                            color = lime.copy(alpha = 0.14f),
                            radius = r, center = Offset(cx, cy),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw)
                        )
                        // progress arc
                        drawArc(
                            color = lime,
                            startAngle = -90f,
                            sweepAngle = 360f * pr,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = sw,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                        // end dot at angle a = pr * 2PI from 12 o'clock
                        val a = pr * 2f * Math.PI.toFloat()
                        val dx = r * kotlin.math.sin(a)
                        val dy = -r * kotlin.math.cos(a)
                        val dotC = Offset(cx + dx, cy + dy)
                        drawCircle(color = lime, radius = (7 * s).toPx(), center = dotC)
                        drawCircle(
                            color = ShareInk, radius = (7 * s).toPx(), center = dotC,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = (3 * s).toPx())
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (m >= 60) {
                            Text(
                                text = shareHm(m),
                                fontFamily = ShareOutfit,
                                fontWeight = FontWeight.Bold,
                                fontSize = (28 * s).sp,
                                letterSpacing = (-1.5 * s).sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height((6 * s).dp))
                            Text(
                                text = "FOCUSED",
                                fontFamily = ShareOutfit,
                                fontSize = (10 * s).sp,
                                letterSpacing = (2.6 * s).sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        } else {
                            Text(
                                text = "$m",
                                fontFamily = ShareOutfit,
                                fontWeight = FontWeight.Bold,
                                fontSize = (50 * s).sp,
                                letterSpacing = (-1.5 * s).sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height((6 * s).dp))
                            Text(
                                text = if (m == 1) "MINUTE" else "MINUTES",
                                fontFamily = ShareOutfit,
                                fontSize = (10 * s).sp,
                                letterSpacing = (2.6 * s).sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // .h — serif headline
                Spacer(modifier = Modifier.height((20 * s).dp))
                val headline = buildAnnotatedString {
                    append("Well done")
                    if (data.userName != null) {
                        append(", ")
                        withStyle(SpanStyle(color = lime)) { append(data.userName) }
                        append(".")
                    } else {
                        withStyle(SpanStyle(color = lime)) { append(".") }
                    }
                }
                Text(
                    text = headline,
                    fontFamily = ShareSerif,
                    fontStyle = FontStyle.Italic,
                    fontSize = (36 * s).sp,
                    lineHeight = (36 * 1.05f * s).sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                // .sb
                Spacer(modifier = Modifier.height((8 * s).dp))
                Text(
                    text = if (d == 0) "Zero distractions. Pure focus."
                    else "$d distraction${if (d == 1) "" else "s"}, still showed up.",
                    fontFamily = ShareOutfit,
                    fontSize = (13 * s).sp,
                    lineHeight = (13 * 1.4f * s).sp,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width((250 * s).dp)
                )

                // .chips
                Spacer(modifier = Modifier.height((16 * s).dp))
                Row(horizontalArrangement = Arrangement.spacedBy((7 * s).dp)) {
                    ShareChip(s, "\u2713 Completed", chipBg = ShareChipBg, chipBorder = ShareChipBorder, textColor = Color.White)
                    ShareChip(
                        s,
                        "$d distraction${if (d == 1) "" else "s"}",
                        chipBg = ShareChipBg, chipBorder = ShareChipBorder, textColor = Color.White
                    )
                    if (data.streakDays > 0) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(ShareChipBg)
                                .border((1 * s).dp, ShareChipBorder, RoundedCornerShape(99.dp))
                                .padding(horizontal = (12 * s).dp, vertical = (7 * s).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.emoji3d_flame),
                                contentDescription = null,
                                modifier = Modifier.size((14 * s).dp)
                            )
                            Spacer(modifier = Modifier.width((4 * s).dp))
                            Text(
                                text = "${data.streakDays}d",
                                fontFamily = ShareOutfit,
                                fontWeight = FontWeight.Medium,
                                fontSize = (11.5f * s).sp,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                // .q — bottom quote
                Text(
                    text = "\u201C${shareQuoteFor(m)}\u201D",
                    fontFamily = ShareSerif,
                    fontStyle = FontStyle.Italic,
                    fontSize = (18 * s).sp,
                    lineHeight = (18 * 1.25f * s).sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width((260 * s).dp)
                )
            }
        }
    }
}

@Composable
private fun ShareChip(
    s: Float,
    text: String,
    chipBg: Color,
    chipBorder: Color,
    textColor: Color
) {
    Text(
        text = text,
        fontFamily = ShareOutfit,
        fontWeight = FontWeight.Medium,
        fontSize = (11.5f * s).sp,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(chipBg)
            .border((1 * s).dp, chipBorder, RoundedCornerShape(99.dp))
            .padding(horizontal = (12 * s).dp, vertical = (7 * s).dp)
    )
}

// --- Card 2: TODAY -----------------------------------------------------------

@Composable
private fun ShareTodayCard(data: ShareScreenData, scale: Float) {
    val s = scale
    val ink = ShareCreamInk
    val week = data.weekMinutes
    val mx = (week.maxOrNull() ?: 0).coerceAtLeast(1)

    CardShell(
        scale = s,
        background = Brush.verticalGradient(listOf(ShareCream, ShareCream))
    ) {
        ShareTopRow(s, "Today", brandColor = ShareDeepGreen, textColor = ink)

        // .lab
        Spacer(modifier = Modifier.height((30 * s).dp))
        Text(
            text = "TOTAL FOCUS TODAY",
            fontFamily = ShareOutfit,
            fontSize = (11 * s).sp,
            letterSpacing = (3 * s).sp,
            color = ink.copy(alpha = 0.55f)
        )

        // .big
        Spacer(modifier = Modifier.height((34 * s).dp))
        Text(
            text = shareHm(data.todayTotalMinutes),
            fontFamily = ShareSerif,
            fontStyle = FontStyle.Italic,
            fontSize = (76 * s).sp,
            lineHeight = (76 * s).sp,
            color = ink
        )

        // .sb
        Spacer(modifier = Modifier.height((8 * s).dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${data.todaySessions} session${if (data.todaySessions == 1) "" else "s"}",
                fontFamily = ShareOutfit,
                fontSize = (13 * s).sp,
                color = ink.copy(alpha = 0.65f)
            )
            if (data.streakDays > 0) {
                Text(
                    text = " \u00B7 ",
                    fontFamily = ShareOutfit,
                    fontSize = (13 * s).sp,
                    color = ink.copy(alpha = 0.65f)
                )
                Image(
                    painter = painterResource(id = R.drawable.emoji3d_flame),
                    contentDescription = null,
                    modifier = Modifier.size((14 * s).dp)
                )
                Text(
                    text = " ${data.streakDays} day streak",
                    fontFamily = ShareOutfit,
                    fontSize = (13 * s).sp,
                    color = ink.copy(alpha = 0.65f)
                )
            }
        }

        // .bars
        Spacer(modifier = Modifier.height((26 * s).dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height((110 * s).dp)
                .padding(horizontal = (6 * s).dp),
            horizontalArrangement = Arrangement.spacedBy((12 * s).dp),
            verticalAlignment = Alignment.Bottom
        ) {
            week.forEachIndexed { i, v ->
                val pct = (v.toFloat() / mx * 100f).coerceAtLeast(8f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((110 * pct / 100f * s).dp)
                        .clip(RoundedCornerShape((7 * s).dp))
                        .background(
                            if (i == 6) ShareLime
                            else ShareDeepGreen.copy(alpha = 0.22f)
                        )
                )
            }
        }

        // .days
        Spacer(modifier = Modifier.height((8 * s).dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = (6 * s).dp),
            horizontalArrangement = Arrangement.spacedBy((12 * s).dp)
        ) {
            data.weekLabels.forEach { day ->
                Text(
                    text = day,
                    fontFamily = ShareOutfit,
                    fontSize = (11 * s).sp,
                    color = ink.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// --- Card 3: GHOST -----------------------------------------------------------

@Composable
private fun ShareGhostCard(data: ShareScreenData, scale: Float) {
    val s = scale
    val ink = ShareInk
    val m = data.minutes
    val d = data.distractions

    CardShell(
        scale = s,
        background = Brush.linearGradient(
            colors = listOf(Color(0xFF9BEA10), Color(0xFFD4F56A)),
            start = Offset(0f, 0f),
            end = Offset(360f * s, 450f * s)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // .ghost — giant background number
            Text(
                text = "$m",
                fontFamily = ShareOutfit,
                fontWeight = FontWeight.Bold,
                fontSize = (270 * s).sp,
                letterSpacing = (-12 * s).sp,
                color = Color.White.copy(alpha = 0.13f),
                modifier = Modifier.offset(x = (-6 * s).dp, y = (60 * s).dp)
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShareTopRow(s, data.dateLabel, brandColor = ink, textColor = ink)
                // .msg — vertically centred
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shareQuoteFor(m),
                        fontFamily = ShareSerif,
                        fontStyle = FontStyle.Italic,
                        fontSize = (46 * s).sp,
                        lineHeight = (46 * 1.02f * s).sp,
                        color = ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = (8 * s).dp)
                    )
                }
                // .bt
                Text(
                    text = "${shareHm(m)} \u00B7 " +
                        if (d == 0) "zero distractions"
                        else "$d distraction${if (d == 1) "" else "s"}",
                    fontFamily = ShareOutfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (13 * s).sp,
                    letterSpacing = (0.5 * s).sp,
                    color = ink.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = (6 * s).dp)
                )
            }
        }
    }
}

// --- Card 4: SHIELD ----------------------------------------------------------

@Composable
private fun ShareShieldCard(data: ShareScreenData, scale: Float) {
    val s = scale
    val n = data.blockedApps.size
    val lime = ShareLime

    CardShell(
        scale = s,
        background = Brush.verticalGradient(listOf(ShareK4Bg, ShareK4Bg)),
        borderColor = ShareChipBorder
    ) {
        ShareTopRow(s, "Shield", brandColor = lime, textColor = Color.White)

        // .h — 40px serif
        Spacer(modifier = Modifier.height((34 * s).dp))
        val headline = buildAnnotatedString {
            append("$n app${if (n == 1) "" else "s"} stayed ")
            withStyle(SpanStyle(color = lime)) { append("locked") }
        }
        Text(
            text = headline,
            fontFamily = ShareSerif,
            fontStyle = FontStyle.Italic,
            fontSize = (40 * s).sp,
            lineHeight = (40 * 1.05f * s).sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        // .apps grid — real launcher icons + lock chips
        if (n > 0) {
            Spacer(modifier = Modifier.height((26 * s).dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy((16 * s).dp)
            ) {
                data.blockedApps.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((14 * s).dp)
                    ) {
                        row.forEach { app ->
                            ShieldAppTile(app = app, scale = s, modifier = Modifier.weight(1f))
                        }
                        // keep grid alignment on a short last row
                        repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            // Honest empty state, same design language.
            Spacer(modifier = Modifier.height((26 * s).dp))
            Text(
                text = "Nothing tried to distract you.",
                fontFamily = ShareOutfit,
                fontSize = (13 * s).sp,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        // .q footer
        Text(
            text = "while you focused for ${shareHm(data.minutes)}.",
            fontFamily = ShareSerif,
            fontStyle = FontStyle.Italic,
            fontSize = (18 * s).sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Curated fallback icons for the most common distracting apps. The REAL
 * launcher icon via PackageManager is always tried first; these official
 * app icons (already in the app for the splash) are used only when that
 * lookup fails (e.g. screenshot tests), so a tile never renders blank.
 */
private fun knownAppIconRes(packageName: String): Int? = when (packageName) {
    "com.instagram.android" -> R.drawable.ic_instagram
    "com.google.android.youtube", "com.google.android.youtube.tv" -> R.drawable.ic_youtube
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> R.drawable.ic_tiktok
    "com.twitter.android" -> R.drawable.ic_x
    "com.snapchat.android" -> R.drawable.ic_snapchat
    "com.reddit.frontpage" -> R.drawable.ic_reddit
    else -> null
}

/** One shield tile: real app icon (grayscaled like the HTML) + lime lock chip. */
@Composable
private fun ShieldAppTile(app: ShareBlockedApp, scale: Float, modifier: Modifier = Modifier) {
    val s = scale
    val context = LocalContext.current
    val painter: Painter? = remember(app.packageName) {
        try {
            val pm = context.packageManager
            val drawable: Drawable? = try {
                pm.getApplicationIcon(app.packageName)
            } catch (_: PackageManager.NameNotFoundException) { null }
                ?: app.fallbackRes?.let { ContextCompat.getDrawable(context, it) }
                ?: knownAppIconRes(app.packageName)?.let { ContextCompat.getDrawable(context, it) }
                ?: ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
            val bmp: Bitmap? = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                null -> null
                else -> try {
                    drawable.toBitmap(96, 96, Bitmap.Config.ARGB_8888)
                } catch (_: Exception) { null }
            }
            bmp?.asImageBitmap()?.let { BitmapPainter(it) }
        } catch (_: Exception) { null }
    }

    // grayscale(1) brightness(.6) — the HTML's tile treatment.
    val grayDim = remember {
        ColorMatrix(
            floatArrayOf(
                0.1794f, 0.3522f, 0.0684f, 0f, 0f,
                0.1794f, 0.3522f, 0.0684f, 0f, 0f,
                0.1794f, 0.3522f, 0.0684f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = app.appName,
                    modifier = Modifier.size((56 * s).dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(grayDim)
                )
            }
        }
        // .ap u — lime lock chip, offset right -5 bottom -5
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (5 * s).dp, y = (5 * s).dp)
                .size((20 * s).dp)
                .clip(CircleShape)
                .background(ShareLime)
                .border((2 * s).dp, ShareK4Bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = ShareInk,
                modifier = Modifier.size((11 * s).dp)
            )
        }
    }
}
