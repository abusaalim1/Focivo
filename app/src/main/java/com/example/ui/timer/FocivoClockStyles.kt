package com.example.ui.timer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/* Shared helpers (FtFDisplay/FtFBody/FtLime/FtMint/FtSand/FtBgDark/ftMmss/ftProg/FtT)
   live in FocivoTimerShared.kt in this same package. */

/* ================= CLOCK STYLES ================= */
/** Nine new clock styles, registered alongside the existing faces in TimerFaceManager. */
enum class ClockStyle(val label: String, val desc: String) {
    ORBIT("Orbit", "Thin ring, glowing orbiting dot"),
    FLIP("Flip", "Soft flip-clock cards"),
    LIQUID("Liquid", "Water level slowly drains"),
    EDITORIAL("Editorial", "Huge serif, hairline progress"),
    DIAL("Dial", "Sweeping dial with live wedge"),
    HORIZON("Horizon", "Sun travels across a calm arc"),
    STACK("Stack", "Minutes over seconds, vertical meter"),
    BREATHE("Breathe", "Soft glowing orb that breathes"),
    BARS("Bars", "Quiet equalizer bars fill up")
}

/** Ek hi entry point. remainingSec/totalSec timer se aate hain, accent = app theme ka rang. */
/**
 * v1.3.2: [content] is the backdrop-appropriate ink for time text and labels
 * (the selected timer theme's backdropContent color). Clock designs draw on
 * the theme's full-bleed backdrop, so hard-coded white ink went invisible on
 * light canvases (e.g. Aurora in light app mode).
 */
@Composable
fun FocivoClock(
    style: ClockStyle, remainingSec: Int, totalSec: Int,
    accent: Color = FtLime, content: Color = Color.White,
    modifier: Modifier = Modifier.size(300.dp)
) {
    when (style) {
        ClockStyle.ORBIT -> OrbitClock(remainingSec, totalSec, accent, content, modifier)
        ClockStyle.FLIP -> FlipClock(remainingSec, totalSec, accent, content, modifier)
        ClockStyle.LIQUID -> LiquidClock(remainingSec, totalSec, accent, content, modifier)
        ClockStyle.EDITORIAL -> EditorialClock(remainingSec, totalSec, accent, content, modifier)
        ClockStyle.DIAL -> DialClock(remainingSec, totalSec, accent, content, modifier)
        ClockStyle.HORIZON -> HorizonClock(remainingSec, totalSec, accent, content, modifier)
        ClockStyle.STACK -> StackClock(remainingSec, totalSec, accent, content, modifier)
        ClockStyle.BREATHE -> BreatheClock(remainingSec, totalSec, accent, content, modifier)
        ClockStyle.BARS -> BarsClock(remainingSec, totalSec, accent, content, modifier)
    }
}

@Composable
private fun OrbitClock(rem: Int, total: Int, acc: Color, content: Color, m: Modifier) {
    val p = ftProg(rem, total)
    Box(m, Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center; val s = size.minDimension
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0 - 90); val r = s * .46f
                drawCircle(Color.White.copy(.28f), if (i % 5 == 0) 2.dp.toPx() else 1.dp.toPx(), Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()))
            }
            val r = s * .39f
            drawCircle(Color.White.copy(.08f), r, style = Stroke(2.dp.toPx()))
            drawArc(acc, -90f, 360f * p, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            val a = Math.toRadians(-90.0 + 360.0 * p); val d = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            drawCircle(acc.copy(.25f), 16.dp.toPx(), d); drawCircle(acc, 8.dp.toPx(), d)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FtT(ftMmss(rem), 60, content, weight = FontWeight.Light, spacing = -1f)
            FtT("MIN REMAINING", 11, content.copy(.5f), spacing = 3f)
        }
    }
}

@Composable
private fun FlipClock(rem: Int, total: Int, acc: Color, content: Color, m: Modifier) {
    val s = ftMmss(rem); val p = ftProg(rem, total)
    Box(m, Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FlipDigit(s[0]); FlipDigit(s[1]); FtT(":", 46, acc, FontWeight.SemiBold); FlipDigit(s[3]); FlipDigit(s[4])
            }
            Spacer(Modifier.height(30.dp))
            Box(Modifier.width(260.dp).height(7.dp)) {
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp).background(Color.White.copy(.14f)))
                Box(Modifier.offset(x = (253.dp * p)).size(7.dp).clip(CircleShape).background(acc))
            }
        }
    }
}

@Composable
private fun FlipDigit(ch: Char) {
    Box(
        Modifier.size(62.dp, 92.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(0.5f to Color(0xFF202722), 0.5f to Color(0xFF1A201C))),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(ch, transitionSpec = {
            (slideInVertically { -it / 2 } + fadeIn(tween(300))) togetherWith (slideOutVertically { it / 2 } + fadeOut(tween(200)))
        }, label = "flip") { FtT("$it", 62, weight = FontWeight.SemiBold) }
        Box(Modifier.fillMaxWidth().height(2.dp).background(FtBgDark))
    }
}

@Composable
private fun LiquidClock(rem: Int, total: Int, acc: Color, content: Color, m: Modifier) {
    val fill = if (total > 0) (rem.toFloat() / total).coerceIn(0f, 1f) else 0f
    val ph by rememberInfiniteTransition(label = "w").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4500, easing = LinearEasing)), label = "ph")
    Box(m, Alignment.Center) {
        Box(Modifier.size(236.dp).clip(CircleShape).background(Color(0xFF0F1411)).border(1.5.dp, acc.copy(.5f), CircleShape)) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * (1f - fill * .94f)
                fun wave(off: Float, amp: Float, col: Color, ph2: Float) {
                    val path = Path().apply {
                        moveTo(0f, size.height); lineTo(0f, y)
                        var x = 0f; while (x <= size.width) { lineTo(x, y + amp * sin(x / size.width * 2f * PI.toFloat() * 1.5f + ph2 + off)); x += 4f }
                        lineTo(size.width, size.height); close()
                    }
                    drawPath(path, col)
                }
                wave(0f, 7.dp.toPx(), acc.copy(.55f), -ph); wave(1.5f, 6.dp.toPx(), acc, ph)
            }
            Box(Modifier.fillMaxSize(), Alignment.Center) { FtT(ftMmss(rem), 56, if (fill > .55f) FtBgDark else Color.White, FontWeight.SemiBold, spacing = -1f) }
        }
    }
}

@Composable
private fun EditorialClock(rem: Int, total: Int, acc: Color, content: Color, m: Modifier) {
    val p = ftProg(rem, total); val v = rem.coerceAtLeast(0)
    Box(m, Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(buildAnnotatedString {
                append("%02d".format(v / 60)); withStyle(SpanStyle(color = acc)) { append(":") }; append("%02d".format(v % 60))
            }, style = TextStyle(color = content, fontFamily = FtFDisplay, fontStyle = FontStyle.Italic, fontSize = 112.sp, letterSpacing = (-3).sp, fontFeatureSettings = "tnum"))
            FtT("MINUTES LEFT", 11, content.copy(.5f), spacing = 4f)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.width(240.dp).height(7.dp)) {
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp).background(Color.White.copy(.14f)))
                Box(Modifier.offset(x = 233.dp * p).size(7.dp).clip(CircleShape).background(acc))
            }
        }
    }
}

@Composable
private fun DialClock(rem: Int, total: Int, acc: Color, content: Color, m: Modifier) {
    val p = ftProg(rem, total)
    Box(m, Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center; val s = size.minDimension
            drawArc(acc.copy(.26f), -90f, 360f * p, true, Offset(c.x - s * .38f, c.y - s * .38f), Size(s * .76f, s * .76f))
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0 - 90); val M = i % 5 == 0
                val r1 = s * (if (M) .333f else .36f); val r2 = s * .387f
                drawLine(Color.White.copy(if (M) .7f else .25f), Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                    Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()), if (M) 2.5.dp.toPx() else 1.2.dp.toPx(), StrokeCap.Round)
            }
            rotate(360f * p, c) { drawLine(acc, c, Offset(c.x, c.y - s * .36f), 3.dp.toPx(), StrokeCap.Round) }
            drawCircle(acc, 7.dp.toPx(), c)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 54.dp).clip(CircleShape).background(Color.White.copy(.07f))
            .border(1.dp, Color.White.copy(.1f), CircleShape).padding(horizontal = 18.dp, vertical = 8.dp)) { FtT(ftMmss(rem), 26, content, weight = FontWeight.SemiBold) }
    }
}

@Composable
private fun HorizonClock(rem: Int, total: Int, acc: Color, content: Color, m: Modifier) {
    val p = ftProg(rem, total)
    Box(m, Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension; val r = s * .42f; val c = Offset(size.width / 2, size.height * .56f)
            drawArc(Color.White.copy(.1f), 180f, 180f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawArc(acc, 180f, 180f * p, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawLine(Color.White.copy(.14f), Offset(c.x - r - 14.dp.toPx(), c.y), Offset(c.x + r + 14.dp.toPx(), c.y), 1.dp.toPx())
            val a = Math.toRadians(180.0 + 180.0 * p); val sun = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            drawCircle(Brush.radialGradient(listOf(acc.copy(.45f), Color.Transparent), sun, 46.dp.toPx()), 46.dp.toPx(), sun)
            drawCircle(acc, 10.dp.toPx(), sun)
        }
        Column(Modifier.align(Alignment.Center).offset(y = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FtT(ftMmss(rem), 64, content, weight = FontWeight.Light, family = FtFDisplay, italic = true, spacing = -1f)
            FtT("UNTIL SUNSET", 11, content.copy(.5f), spacing = 4f)
        }
    }
}

@Composable
private fun StackClock(rem: Int, total: Int, acc: Color, content: Color, m: Modifier) {
    val p = ftProg(rem, total); val v = rem.coerceAtLeast(0)
    Box(m, Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Box(Modifier.width(3.dp).height(210.dp).clip(CircleShape).background(Color.White.copy(.1f)), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(1f - p).background(acc))
            }
            Column {
                FtT("%02d".format(v / 60), 104, content, weight = FontWeight.Thin, spacing = -4f, lh = 100f)
                FtT("%02d".format(v % 60), 104, acc, FontWeight.Thin, spacing = -4f, lh = 100f)
            }
        }
    }
}

@Composable
private fun BreatheClock(rem: Int, total: Int, acc: Color, content: Color, m: Modifier) {
    val p = ftProg(rem, total)
    val b by rememberInfiniteTransition(label = "b").animateFloat(.82f, 1f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Box(m, Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center; val s = size.minDimension
            drawCircle(Brush.radialGradient(listOf(acc.copy(.42f), acc.copy(.08f), Color.Transparent), c, s * .5f * b), s * .5f * b, c)
            drawCircle(acc.copy(.12f), s * .3f * b, c); drawCircle(acc.copy(.9f), s * .3f * b, c, style = Stroke(1.dp.toPx()))
            val r = s * .4f
            drawArc(Color.White.copy(.08f), -90f, 360f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(1.dp.toPx()))
            drawArc(Color.White.copy(.8f), -90f, 360f * p, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FtT(ftMmss(rem), 54, content, weight = FontWeight.Light, spacing = -1f)
            FtT("BREATHE", 11, content.copy(.55f), spacing = 4f)
        }
    }
}

@Composable
private fun BarsClock(rem: Int, total: Int, acc: Color, content: Color, m: Modifier) {
    val p = ftProg(rem, total)
    Box(m, Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FtT(ftMmss(rem), 64, content, weight = FontWeight.Light, spacing = -1f)
            Spacer(Modifier.height(36.dp))
            Canvas(Modifier.width(264.dp).height(96.dp)) {
                val n = 32; val gap = 4.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n; val done = (p * n).toInt()
                for (i in 0 until n) {
                    val h = size.height * (.3f + .7f * abs(sin(i * .55f)))
                    drawRoundRect(if (i < done) acc else Color.White.copy(.14f), Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(bw / 2))
                }
            }
        }
    }
}

/* ================= GALLERY (sirf preview/test ke liye - app me zaroori nahi) ================= */
@Composable
fun ClockStyleGallery() {
    var style by remember { mutableStateOf(ClockStyle.ORBIT) }
    var rem by remember { mutableIntStateOf(1500) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(33); rem -= 1; if (rem <= 0) rem = 1500 } }   // demo: 30x speed
    Column(Modifier.fillMaxSize().background(FtBgDark), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(90.dp))
        FocivoClock(style, rem, 1500)
        Spacer(Modifier.height(12.dp)); FtT(style.desc, 13, Color.White.copy(.55f))
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(bottom = 40.dp, start = 16.dp, end = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockStyle.values().forEach { s ->
                Box(Modifier.clip(CircleShape).background(if (s == style) FtLime else Color.White.copy(.07f)).clickable { style = s }.padding(horizontal = 15.dp, vertical = 9.dp)) {
                    FtT(s.label, 13, if (s == style) FtBgDark else Color.White, FontWeight.Medium)
                }
            }
        }
    }
}
