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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
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

enum class SplashVariant(val totalMs: Int) { NEW_USER(8400), RETURNING(3000) }

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
    welcome: WelcomeInfo = WelcomeInfo(),
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
    LaunchedEffect(variant, welcome, testProgress) {
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
        // v1.2.1 (owner order): the lime radial background glow stays REMOVED —
        // backdrop is plain dark #0D110E in BOTH scenes. The WelcomeScene's own
        // animated aurora gradients are the new scene's design and stay.
        if (variant == SplashVariant.NEW_USER) NewUserScene(p, W, cy) else WelcomeScene(p, W, cy, welcome)
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
            // v1.2.1 (owner order): the extra filled backdrop circle behind the
            // radar disc stays REMOVED — rings + sweep on the plain dark bg.
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

/** Signed-in user ke real data (naam, streak, aaj ke minutes). Sab optional. */
data class WelcomeInfo(val name: String? = null, val streakDays: Int = 0, val todayMinutes: Int = 0)

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return if (h < 12) "Good morning," else if (h < 17) "Good afternoon," else "Good evening,"
}

/** Signed-in concept: aurora + greeting + streak pill. 3s, naye user wale radar se bilkul alag. */
@Composable
private fun BoxWithConstraintsScope.WelcomeScene(p: Float, W: Dp, cy: Dp, info: WelcomeInfo) {
    val ex = 1f - seg(p, .86f, 1f)
    val sc = lerp(1f, 1.05f, seg(p, .86f, 1f))
    val li = cl(p / .25f)
    val t = p * 2f * PI.toFloat()
    val w = W.value

    // soft moving aurora
    Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = sc; scaleY = sc; alpha = seg(p, 0f, .3f) * ex }) {
        val pw = W.toPx(); val c0 = Offset(size.width / 2, cy.toPx())
        val c1 = Offset(c0.x + .18f * pw * sin(t), c0.y - .1f * pw + .06f * pw * cos(t * 1.3f))
        val c2 = Offset(c0.x - .2f * pw * sin(t + 1f), c0.y + .12f * pw + .07f * pw * cos(t))
        drawCircle(Brush.radialGradient(listOf(Lime.copy(alpha = .34f), Color.Transparent), c1, .6f * pw), .6f * pw, c1)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF1EBE6E).copy(alpha = .28f), Color.Transparent), c2, .55f * pw), .55f * pw, c2)
    }

    // logo: blur -> sharp + glass ring
    Spot(0.dp, (-.12f * w).dp, (.26f * w).dp, 1.38f * sc, li * ex * .25f, cy) {
        Box(Modifier.fillMaxSize().border(1.dp, Lime, CircleShape))
    }
    Spot(0.dp, (-.12f * w).dp, (.26f * w).dp, lerp(.6f, 1f, ob(li)) * sc, li * ex, cy) {
        Image(painterResource(R.drawable.focivo_logo), "Focivo",
            Modifier.fillMaxSize().blur(((1f - li) * 10f).dp).clip(RoundedCornerShape(22.dp)))
    }

    // greeting words blur-in one by one
    Line((.19f * w).dp - 22.dp, cy, ex) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            greeting().split(" ").forEachIndexed { i, word ->
                val a = seg(p, .18f + i * .07f, .40f + i * .07f)
                Text(word, color = Color.White, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = (.105f * w).sp,
                    modifier = Modifier.graphicsLayer { alpha = a; translationY = (1 - a) * 14f }.blur(((1 - a) * 8f).dp))
            }
        }
    }
    val nm = info.name?.takeIf { it.isNotBlank() } ?: "Welcome back"
    val na = seg(p, .3f, .5f)
    Line((.32f * w + (1 - na) * 12).dp - 24.dp, cy, na * ex) {
        Text(nm, color = Lime, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = (.115f * w).sp)
    }

    // streak + today pill (count-up). v1.2.3 (owner rule, zero unicode emojis):
    // the flame glyph is the app's 3D flame drawable, not a unicode emoji.
    if (info.streakDays > 0 || info.todayMinutes > 0) {
        val pa = seg(p, .5f, .68f); val pc = seg(p, .5f, .75f)
        Line((.5f * w + (1 - pa) * 14).dp - 18.dp, cy, pa * ex) {
            Row(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = .06f)).border(1.dp, Lime.copy(alpha = .25f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (info.streakDays > 0) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Image(painterResource(R.drawable.emoji3d_flame), "streak", Modifier.size(15.dp))
                    Text("${(info.streakDays * pc).toInt()} day streak", color = Color.White.copy(alpha = .85f), fontFamily = Body, fontSize = 13.sp)
                }
                if (info.streakDays > 0 && info.todayMinutes > 0) Box(Modifier.width(1.dp).height(14.dp).background(Color.White.copy(alpha = .2f)))
                if (info.todayMinutes > 0) Text("${(info.todayMinutes * pc).toInt()}m focused today", color = Color.White.copy(alpha = .85f), fontFamily = Body, fontSize = 13.sp)
            }
        }
    }
}

/** Host: auth state + user data yahan lagao (FirebaseAuth / apna session). */
@Composable
fun SplashHost(
    isSignedIn: Boolean,
    welcome: WelcomeInfo = WelcomeInfo(),   // WelcomeInfo(name = user.firstName, streakDays = streak, todayMinutes = todayFocus)
    goToCreateAccount: () -> Unit,          // new user -> Auth screen, "Create Account" tab selected
    goToHome: () -> Unit                    // signed-in -> Home
) {
    FocivoSplash(if (isSignedIn) SplashVariant.RETURNING else SplashVariant.NEW_USER, welcome) { createAcc ->
        if (createAcc) goToCreateAccount() else goToHome()
    }
}

/** v1.2.3: real-data wiring for the WelcomeScene. Reads the same on-device
 *  stores the share cards use (synchronous prefs read, no launch delay):
 *  - name: user_preferences currentUserName (same resolution rules as the
 *    share cards; first name only for the greeting line)
 *  - streakDays: user_preferences currentStreak
 *  - todayMinutes: today's total from focus_sessions (same per-day
 *    aggregation the share-card "Today" card uses)
 *  Null/blank name -> the scene falls back to "Welcome back" by itself.
 */
internal fun loadSplashWelcomeInfo(context: android.content.Context): WelcomeInfo {
    return try {
        val prefs = context.getSharedPreferences("focusly_local_data", android.content.Context.MODE_PRIVATE)
        val obj = prefs.getString("user_preferences", null)?.let { org.json.JSONObject(it) }
        val rawName = obj?.optString("currentUserName", "")?.trim().orEmpty()
        val email = obj?.optString("currentUserEmail", "")?.trim().orEmpty()
        val name = when {
            rawName.isNotBlank() && rawName != "Deep Worker" && rawName != "Student" -> rawName.split(" ").first()
            email.isNotBlank() -> email.substringBefore("@").split(" ").first().takeIf { it.isNotBlank() }
            else -> null
        }
        val streak = obj?.optInt("currentStreak", 0)?.coerceAtLeast(0) ?: 0
        var todayMinutes = 0
        try {
            val arr = org.json.JSONArray(prefs.getString("focus_sessions", "[]") ?: "[]")
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis
            var total = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optLong("completedAt", 0L) >= todayStart) total += o.optInt("durationSeconds", 0)
            }
            todayMinutes = (total / 60).coerceAtLeast(0)
        } catch (_: Exception) { }
        WelcomeInfo(name = name, streakDays = streak, todayMinutes = todayMinutes)
    } catch (_: Exception) { WelcomeInfo() }
}
