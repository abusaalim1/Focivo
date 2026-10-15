package com.example.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/* ---------- RES: in PNGs ko res/drawable me copy karo (lowercase naam) ----------
   focivo_logo, ic_instagram, ic_youtube, ic_tiktok, ic_x, ic_snapchat,
   ic_reddit, ic_age18 (18_.png), ic_notification
   FONTS (optional, aesthetic): res/font me Instrument Serif Italic + Outfit daalo
   aur neeche Display / Body ko swap kar do. */
private val Display = FontFamily.Serif      // -> FontFamily(Font(R.font.instrument_serif_italic))
private val Body = FontFamily.SansSerif     // -> FontFamily(Font(R.font.outfit_medium))

private val BgDark = Color(0xFF0D110E)
private val Lime = Color(0xFF8CE000)

enum class SplashVariant(val totalMs: Int) { NEW_USER(8400), RETURNING(2800) }

private class AppDot(val res: Int, val angle: Float, val frac: Float)
private val dots = listOf(
    AppDot(R.drawable.ic_instagram, 22f, .74f), AppDot(R.drawable.ic_youtube, 70f, .84f),
    AppDot(R.drawable.ic_tiktok, 115f, .66f), AppDot(R.drawable.ic_x, 160f, .82f),
    AppDot(R.drawable.ic_snapchat, 205f, .70f), AppDot(R.drawable.ic_reddit, 250f, .84f),
    AppDot(R.drawable.ic_age18, 295f, .64f), AppDot(R.drawable.ic_notification, 340f, .80f),
)

private fun cl(x: Float) = x.coerceIn(0f, 1f)
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun eo(t: Float) = 1f - (1f - t).pow(3)
private fun ob(t: Float): Float { val c = 1.70158f; return 1f + (c + 1) * (t - 1).pow(3) + c * (t - 1).pow(2) }
private fun seg(p: Float, a: Float, b: Float) = eo(cl((p - a) / (b - a)))

/** NEW_USER -> onFinished(true)  => Auth screen "Create Account" tab pe kholo
 *  RETURNING -> onFinished(false) => Home */
@Composable
fun FocivoSplash(
    variant: SplashVariant,
    /**
     * v1.2.1 screenshot-test seam: when non-null the clock animation is
     * skipped and the scene renders at this fixed progress (0..1).
     * Production callers leave it null. Kept BEFORE onFinished so existing
     * trailing-lambda call sites keep compiling unchanged.
     */
    testProgress: Float? = null,
    onFinished: (openCreateAccount: Boolean) -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(variant, testProgress) {
        if (testProgress == null) {
            progress.animateTo(1f, tween(variant.totalMs, easing = LinearEasing))
            onFinished(variant == SplashVariant.NEW_USER)
        } else {
            progress.snapTo(testProgress.coerceIn(0f, 1f))
        }
    }
    val p = progress.value

    BoxWithConstraints(Modifier.fillMaxSize().background(BgDark)) {
        val W = maxWidth
        val cy = maxHeight * 0.43f
        // v1.2.1: the lime radial background glow is removed (owner) — the
        // backdrop is the plain dark #0D110E. Animation timing/scenes unchanged.
        if (variant == SplashVariant.NEW_USER) NewUserScene(p, W, cy) else ReturningScene(p, W, cy)
    }
}

@Composable
private fun BoxWithConstraintsScope.Spot(
    x: Dp, y: Dp, size: Dp, scale: Float, alpha: Float, cy: Dp, content: @Composable BoxScope.() -> Unit
) {
    if (alpha <= 0f) return
    Box(
        Modifier.align(Alignment.TopCenter).offset(x, cy + y - size / 2).size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
        contentAlignment = Alignment.Center, content = content
    )
}

@Composable
private fun BoxWithConstraintsScope.Line(y: Dp, cy: Dp, alpha: Float, content: @Composable () -> Unit) {
    if (alpha <= 0f) return
    Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).offset(y = cy + y).graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.TopCenter) { content() }
}

@Composable
private fun AppDotView(d: AppDot, size: Dp, reveal: Float, block: Float) {
    val sat = ColorMatrix().apply { setToSaturation(1f - block) }
    Box(Modifier.size(size)) {
        Image(painterResource(d.res), null, Modifier.fillMaxSize().graphicsLayer { alpha = 1f - .4f * block },
            colorFilter = ColorFilter.colorMatrix(sat))
        Box(
            Modifier.align(Alignment.BottomEnd).offset(size * .08f, size * .08f).size(size * .46f)
                .graphicsLayer { scaleX = block; scaleY = block; alpha = block }
                .clip(CircleShape).background(Lime),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Lock, null, tint = BgDark, modifier = Modifier.size(size * .27f)) }
    }
}

@Composable
private fun BoxWithConstraintsScope.NewUserScene(p: Float, W: Dp, cy: Dp) {
    val li = cl(p / .14f)
    val shr = seg(p, .2f, .3f)
    val ls = lerp(.34f, .2f, shr) * W.value
    val radarR = .78f * W.value; val r = radarR / 2
    val out = 1f - seg(p, .82f, .92f)
    val fin = 1f - seg(p, .9f, 1f)
    val sf = cl((p - .26f) / .54f) * 2f
    val ang = (sf * 360f) % 360f

    // intro ripples
    Canvas(Modifier.fillMaxSize()) {
        for (i in 0..1) {
            val t = cl((p - .03f - i * .04f) / .2f)
            if (t > 0f && t < 1f) drawCircle(Lime.copy(alpha = (1 - t) * .5f * fin), (ls.dp.toPx() / 2) * lerp(1f, 4.2f, eo(t)),
                Offset(size.width / 2, cy.toPx()), style = Stroke(1.5.dp.toPx()))
        }
        // radar
        val ro = seg(p, .2f, .3f) * out
        if (ro > 0f) {
            val c = Offset(size.width / 2, cy.toPx()); val rr = r.dp.toPx() * lerp(.85f, 1f, seg(p, .2f, .3f)) * lerp(.92f, 1f, out)
            // v1.2.1: the extra filled backdrop circle behind the radar disc is
            // removed (owner) — the disc rings + sweep draw straight on the
            // plain dark background.
            val st = Stroke(1.dp.toPx())
            drawCircle(Lime.copy(alpha = .15f * ro), rr, c, style = st)
            drawCircle(Lime.copy(alpha = .07f * ro), rr * .66f, c, style = st)
            drawCircle(Lime.copy(alpha = .07f * ro), rr * .33f, c, style = st)
            val so = seg(p, .24f, .3f) * out
            rotate(ang - 90f, c) {
                drawCircle(Brush.sweepGradient(0f to Color.Transparent, .78f to Color.Transparent,
                    1f to Lime.copy(alpha = .36f * so), center = c), rr, c)
                drawLine(Lime.copy(alpha = .85f * so), c, Offset(c.x + rr, c.y), 1.5.dp.toPx())
            }
        }
    }

    // app dots (real icons) - reveal then BLOCK
    dots.forEach { d ->
        val a = d.angle / 360f
        val rv = cl((sf - a) / .08f); val bl = cl((sf - a - .2f) / .08f)
        val q = d.angle * PI.toFloat() / 180f
        Spot((r * d.frac * sin(q)).dp, (-r * d.frac * cos(q)).dp, (.115f * W.value).dp,
            lerp(.4f, 1f, rv) - .12f * bl, rv * (1f - .3f * bl) * out, cy) { AppDotView(d, (.115f * W.value).dp, rv, bl) }
    }

    // logo (centre)
    Spot(0.dp, 0.dp, ls.dp, lerp(.5f, 1f, ob(li)), li * fin, cy) {
        Image(painterResource(R.drawable.focivo_logo), "Focivo", Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)))
    }

    // name + tagline
    val ni = seg(p, .08f, .2f)
    Line(lerp(ls / 2 + .1f * W.value, r + .14f * W.value, shr).dp - 18.dp, cy, ni * fin) {
        Text("Focivo", color = Lime, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = (.1f * W.value).sp,
            letterSpacing = lerp(10f, .5f, seg(p, .08f, .24f)).sp)
    }
    val ty = r + .14f * W.value + .1f * W.value
    val a1 = seg(p, .34f, .46f); val a2 = seg(p, .46f, .58f)
    Line((ty + (1 - a1) * 12).dp - 8.dp, cy, a1 * out) {
        Text("Block distractions.", color = Color.White, fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = (.056f * W.value).sp)
    }
    Line((ty + 26 + (1 - a2) * 12).dp - 8.dp, cy, a2 * out * .6f) {
        Text("Focus better.", color = Color.White, fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = (.056f * W.value).sp)
    }
}

@Composable
private fun BoxWithConstraintsScope.ReturningScene(p: Float, W: Dp, cy: Dp) {
    val li = cl(p / .22f); val ex = 1f - seg(p, .88f, 1f)
    val ls = .24f * W.value; val radarR = .38f * W.value; val ly = -.04f * W.value

    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, cy.toPx() + ly.dp.toPx())
        // shield ring draws itself
        val ringR = ls.dp.toPx() * .75f
        drawArc(Lime.copy(alpha = seg(p, .02f, .2f) * ex), -90f, 360f * seg(p, .05f, .5f), false,
            Offset(c.x - ringR, c.y - ringR), Size(ringR * 2, ringR * 2), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        // ripples
        for (i in 0..2) {
            val t = (p * 2.2f + i / 3f) % 1f
            drawCircle(Lime.copy(alpha = seg(p, .1f, .3f) * (1 - t) * .3f * ex), ls.dp.toPx() / 2 * lerp(1f, 3.4f, t), c, style = Stroke(1.5.dp.toPx()))
        }
    }
    // orbiting, already-blocked apps
    dots.take(6).forEachIndexed { i, d ->
        val rv = seg(p, .12f + i * .04f, .3f + i * .04f); val bl = cl((p - .3f - i * .05f) / .15f)
        val q = (i * 60f + p * 120f) * PI.toFloat() / 180f
        Spot((radarR * sin(q)).dp, (ly - radarR * cos(q)).dp, (.09f * W.value).dp, lerp(.4f, 1f, rv), rv * (1f - .3f * bl) * ex, cy) {
            AppDotView(d, (.09f * W.value).dp, rv, bl)
        }
    }
    Spot(0.dp, ly.dp, ls.dp, lerp(.5f, 1f, ob(li)), li * ex, cy) {
        Image(painterResource(R.drawable.focivo_logo), "Focivo", Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)))
    }
    val a = seg(p, .3f, .5f); val b = seg(p, .45f, .65f)
    Line((.4f * W.value + (1 - a) * 14).dp - 20.dp, cy, a * ex) {
        Text("Welcome back", color = Color.White, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = (.1f * W.value).sp)
    }
    Line((.54f * W.value + (1 - b) * 14).dp - 10.dp, cy, b * ex * .8f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Lime))
            Spacer(Modifier.width(8.dp))
            Text("Your focus shield is on", color = Color(0xFFCCD5CE), fontFamily = Body, fontSize = (.05f * W.value).sp)
        }
    }
}

/** Host: auth state check yahan lagao (FirebaseAuth etc.) */
@Composable
fun SplashHost(
    isSignedIn: Boolean,
    goToCreateAccount: () -> Unit,  // new user -> Auth screen, "Create Account" tab selected
    goToHome: () -> Unit            // signed-in -> Home
) {
    FocivoSplash(if (isSignedIn) SplashVariant.RETURNING else SplashVariant.NEW_USER) { createAcc ->
        if (createAcc) goToCreateAccount() else goToHome()
    }
}
