package com.example.ui.timer

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import com.example.ui.components.pressFeedback
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/* ================= DATA (the UI only consumes this state — the timer logic itself is untouched here) ================= */
enum class PomoPhase(val title: String, val sub: String, val color: Color) {
    FOCUS("Focus", "Stay with it.", FtLime),
    SHORT("Short break", "Breathe. Stretch.", FtMint),
    LONG("Long break", "You earned this.", FtSand)
}

data class PomodoroUiState(
    val phase: PomoPhase,
    val remainingSec: Int,
    val totalSec: Int,
    val sessionIndex: Int,          // 0-based: abhi kaunsa focus session (0..sessions-1)
    val sessions: Int = 4,          // focus sessions per cycle
    val running: Boolean = true,
    val nextPhase: PomoPhase = PomoPhase.SHORT,
    val nextMinutes: Int = 5
) {
    val progress get() = ftProg(remainingSec, totalSec)
    val mins get() = totalSec / 60
}

enum class PomodoroStyle(val label: String, val unlockPoints: Int = 0) {
    TICK_RING("Tick Ring", 0),
    ACTIVITY_RINGS("Activity Rings", 1000),
    SESSION_STACK("Session Stack", 2500),
    TYPOGRAPHIC("Typographic", 5000);

    /**
     * v1.2.4 — lock model matches the timer faces exactly (threshold-based,
     * points never spent; isAdmin = owner admin account frees everything).
     * Previously these four designs were lock-free.
     */
    fun isUnlocked(userPoints: Int, isAdmin: Boolean = false): Boolean =
        isAdmin || com.example.util.ALL_CONTENT_UNLOCKED || userPoints >= unlockPoints
}

/* ================= ENTRY POINT ================= */
@Composable
fun FocivoPomodoro(
    state: PomodoroUiState, style: PomodoroStyle,
    onToggle: () -> Unit, onSkip: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier
) {
    val c by animateColorAsState(state.phase.color, tween(900), label = "phaseColor")   // the color shifts smoothly as soon as the phase changes
    Box(modifier.fillMaxSize().background(FtBgDark)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(c.copy(.2f), Color.Transparent), Offset(size.width / 2, size.height * .36f), size.width * .9f))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 16.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Header(state, c)
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                when (style) {
                    PomodoroStyle.TICK_RING -> TickRingStyle(state, c)
                    PomodoroStyle.ACTIVITY_RINGS -> ActivityRingsStyle(state, c)
                    PomodoroStyle.SESSION_STACK -> SessionStackStyle(state, c)
                    PomodoroStyle.TYPOGRAPHIC -> TypographicStyle(state, c)
                }
            }
            Controls(state, c, onToggle, onSkip, onReset)
        }
    }
}

/* ================= SHARED PARTS ================= */
@Composable
private fun GlassPill(content: @Composable RowScope.() -> Unit) = Row(
    Modifier.clip(CircleShape).background(Color.White.copy(.06f)).border(1.dp, Color.White.copy(.12f), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content
)

@Composable
private fun Header(s: PomodoroUiState, c: Color) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    GlassPill { FtT("Pomodoro", 12, spacing = .4f, weight = FontWeight.Medium) }
    GlassPill { FtT("Session ", 12, spacing = .4f); FtT("${s.sessionIndex + 1}", 12, c, FontWeight.Bold); FtT(" of ${s.sessions}", 12) }
}

@Composable
private fun PhaseTitle(s: PomodoroUiState, size: Int = 40) = Crossfade(targetState = s.phase, animationSpec = tween(600), label = "title") { p ->
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FtT(p.title, size, family = FtFDisplay, italic = true)
        Spacer(Modifier.height(4.dp)); FtT(p.sub, 13, Color.White.copy(.55f))
    }
}

@Composable
private fun CycleTrack(s: PomodoroUiState, c: Color) = Row(Modifier.width(300.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    val inBreak = s.phase != PomoPhase.FOCUS
    repeat(s.sessions) { k ->
        val fill = if (s.sessionIndex > k) 1f else if (s.sessionIndex == k) (if (inBreak) 1f else s.progress) else 0f
        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color.White.copy(.1f))) { Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(FtLime)) }
        val reached = s.sessionIndex > k || (s.sessionIndex == k && inBreak)
        Box(Modifier.size(if (s.sessionIndex == k && inBreak) 12.dp else 9.dp).clip(CircleShape)
            .background(if (reached) (if (k == s.sessions - 1) FtSand else FtMint) else Color.White.copy(.12f)))
    }
}

@Composable
private fun NextUp(s: PomodoroUiState) = Box(
    Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.05f)).border(1.dp, Color.White.copy(.08f), RoundedCornerShape(18.dp)).padding(horizontal = 20.dp, vertical = 12.dp)
) { Row { FtT("Up next · ", 13, Color.White.copy(.7f)); FtT(s.nextPhase.title, 13, weight = FontWeight.Bold); FtT(" · ${s.nextMinutes} min", 13, Color.White.copy(.7f)) } }

@Composable
private fun Controls(s: PomodoroUiState, c: Color, onToggle: () -> Unit, onSkip: () -> Unit, onReset: () -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        // v1.3.2 (item 12): real press states — each button shares one
        // interaction source between pressFeedback and clickable (separate
        // sources meant the press animation never fired). Visuals only.
        val resetSource = remember { MutableInteractionSource() }
        val toggleSource = remember { MutableInteractionSource() }
        val skipSource = remember { MutableInteractionSource() }
        Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(.06f)).border(1.dp, Color.White.copy(.1f), CircleShape).pressFeedback(interactionSource = resetSource).clickable(interactionSource = resetSource, onClick = onReset), Alignment.Center) {
            Icon(Icons.Filled.Refresh, "Reset", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Box(Modifier.size(78.dp).clip(CircleShape).background(c).border(1.dp, Color.White.copy(.35f), CircleShape).pressFeedback(interactionSource = toggleSource).clickable(interactionSource = toggleSource, onClick = onToggle), Alignment.Center) {
            Canvas(Modifier.size(28.dp)) {
                if (s.running) { drawRoundRect(FtBgDark, Offset(size.width * .14f, 0f), Size(size.width * .24f, size.height), CornerRadius(4.dp.toPx())); drawRoundRect(FtBgDark, Offset(size.width * .62f, 0f), Size(size.width * .24f, size.height), CornerRadius(4.dp.toPx())) }
                else drawPath(Path().apply { moveTo(size.width * .18f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * .18f, size.height); close() }, FtBgDark)
            }
        }
        Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(.06f)).border(1.dp, Color.White.copy(.1f), CircleShape).pressFeedback(interactionSource = skipSource).clickable(interactionSource = skipSource, onClick = onSkip), Alignment.Center) {
            Canvas(Modifier.size(20.dp)) {
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width * .75f, size.height / 2); lineTo(0f, size.height); close() }, Color.White)
                drawRoundRect(Color.White, Offset(size.width * .85f, 0f), Size(size.width * .15f, size.height), CornerRadius(2.dp.toPx()))
            }
        }
    }

/* ================= 1) TICK RING ================= */
@Composable
private fun TickRingStyle(s: PomodoroUiState, c: Color) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    PhaseTitle(s); Spacer(Modifier.height(14.dp))
    Box(Modifier.size(272.dp), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cur = (s.progress * 60).toInt(); val cc = center; val r2 = size.minDimension * .464f
            for (i in 0 until 60) {
                val M = i % 5 == 0; val r1 = r2 - (if (M) 18.dp.toPx() else 12.dp.toPx()); val a = Math.toRadians(i * 6.0 - 90)
                drawLine(if (i < cur) c else if (i == cur) Color.White else Color.White.copy(.14f),
                    Offset(cc.x + r1 * cos(a).toFloat(), cc.y + r1 * sin(a).toFloat()), Offset(cc.x + r2 * cos(a).toFloat(), cc.y + r2 * sin(a).toFloat()),
                    if (M) 3.dp.toPx() else 2.dp.toPx(), StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FtT(ftMmss(s.remainingSec), 68, weight = FontWeight.SemiBold, spacing = -2f)
            FtT("OF ${s.mins} MIN", 11, Color.White.copy(.5f), spacing = 3f)
        }
    }
    Spacer(Modifier.height(20.dp)); CycleTrack(s, c); Spacer(Modifier.height(14.dp)); NextUp(s)
}

/* ================= 2) ACTIVITY RINGS (phase / cycle / seconds) ================= */
@Composable
private fun ActivityRingsStyle(s: PomodoroUiState, c: Color) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(284.dp), Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 16.dp.toPx(); val cc = center
            fun ring(frac: Float, p: Float, col: Color) {
                val r = size.minDimension * frac - sw / 2
                drawCircle(col.copy(.14f), r, cc, style = Stroke(sw))
                drawArc(col, -90f, 360f * p.coerceIn(0f, 1f), false, Offset(cc.x - r, cc.y - r), Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
            }
            val done = s.sessionIndex + (if (s.phase == PomoPhase.FOCUS) s.progress else 1f)
            ring(.5f, s.progress, c)                                    // outer: this phase's progress
            ring(.395f, done / s.sessions, Color.White)                 // middle: whole-cycle progress
            ring(.29f, 1f - (s.remainingSec % 60) / 60f, c.copy(.65f))  // inner: live seconds
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FtT(ftMmss(s.remainingSec), 50, weight = FontWeight.SemiBold, spacing = -1f)
            FtT(s.phase.title, 20, family = FtFDisplay, italic = true, color = c)
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        listOf("Phase" to c, "Cycle" to Color.White, "Seconds" to c.copy(.65f)).forEach { (n, col) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(col)); FtT(n, 12, Color.White.copy(.6f))
            }
        }
    }
    Spacer(Modifier.height(16.dp)); NextUp(s)
}

/* ================= 3) SESSION STACK (cards list, the current one expands) ================= */
@Composable
private fun SessionStackStyle(s: PomodoroUiState, c: Color) = Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    repeat(s.sessions) { i ->
        val cur = i == s.sessionIndex; val done = i < s.sessionIndex
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth().animateContentSize(tween(500)).clip(shape)
                .background(if (cur) c.copy(.12f) else Color.White.copy(if (done) .03f else .05f))
                .border(1.dp, if (cur) c.copy(.5f) else Color.White.copy(.07f), shape).padding(horizontal = 20.dp, vertical = if (cur) 20.dp else 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) Icon(Icons.Filled.Check, null, tint = FtLime, modifier = Modifier.size(18.dp)) else Box(Modifier.size(9.dp).clip(CircleShape).background(if (cur) c else Color.White.copy(.25f)))
                Spacer(Modifier.width(10.dp))
                FtT("Session ${i + 1}", 15, if (done || cur) Color.White else Color.White.copy(.45f), FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                FtT(if (cur) s.phase.title else if (done) "Done" else "Waiting", 12, if (cur) c else Color.White.copy(.4f))
            }
            if (cur) {
                Spacer(Modifier.height(12.dp))
                FtT(ftMmss(s.remainingSec), 56, weight = FontWeight.SemiBold, spacing = -1.5f)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(.1f))) { Box(Modifier.fillMaxHeight().fillMaxWidth(s.progress).background(c)) }
            }
        }
    }
    Spacer(Modifier.height(4.dp)); Box(Modifier.align(Alignment.CenterHorizontally)) { NextUp(s) }
}

/* ================= 4) TYPOGRAPHIC (editorial, super minimal) ================= */
@Composable
private fun TypographicStyle(s: PomodoroUiState, c: Color) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Crossfade(targetState = s.phase, animationSpec = tween(600), label = "big") { p ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FtT(p.title + ".", 62, family = FtFDisplay, italic = true, color = Color.White); Spacer(Modifier.height(6.dp)); FtT(p.sub, 14, Color.White.copy(.55f))
        }
    }
    Spacer(Modifier.height(26.dp))
    FtT(ftMmss(s.remainingSec), 92, weight = FontWeight.Light, spacing = -3f)
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(s.sessions) { k ->
            val on = s.sessionIndex > k || (s.sessionIndex == k && s.phase != PomoPhase.FOCUS)
            Box(Modifier.size(if (k == s.sessionIndex) 12.dp else 9.dp).clip(CircleShape).background(if (on) FtLime else if (k == s.sessionIndex) c else Color.White.copy(.15f)))
        }
    }
    Spacer(Modifier.height(24.dp))
    Box(Modifier.width(220.dp).height(7.dp)) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp).background(Color.White.copy(.14f)))
        Box(Modifier.offset(x = 213.dp * s.progress).size(7.dp).clip(CircleShape).background(c))
    }
}

/* ================= DEMO (preview/test ke liye, app me real timer state do) ================= */
@Composable
fun PomodoroDemo(style: PomodoroStyle = PomodoroStyle.TICK_RING, speed: Int = 30) {
    val seq = remember { listOf(PomoPhase.FOCUS, PomoPhase.SHORT, PomoPhase.FOCUS, PomoPhase.SHORT, PomoPhase.FOCUS, PomoPhase.SHORT, PomoPhase.FOCUS, PomoPhase.LONG) }
    fun secs(p: PomoPhase) = when (p) { PomoPhase.FOCUS -> 1500; PomoPhase.SHORT -> 300; PomoPhase.LONG -> 900 }
    var idx by remember { mutableIntStateOf(0) }; var rem by remember { mutableFloatStateOf(1500f) }; var running by remember { mutableStateOf(true) }
    LaunchedEffect(speed) {
        var last = withFrameNanos { it }
        while (true) { val n = withFrameNanos { it }; val dt = (n - last) / 1e9f; last = n
            if (running) { rem -= dt * speed; if (rem <= 0) { idx = (idx + 1) % 8; rem = secs(seq[idx]).toFloat() } } }
    }
    val nxt = seq[(idx + 1) % 8]
    FocivoPomodoro(
        PomodoroUiState(seq[idx], rem.toInt(), secs(seq[idx]), idx / 2, 4, running, nxt, secs(nxt) / 60), style,
        onToggle = { running = !running }, onSkip = { idx = (idx + 1) % 8; rem = secs(seq[idx]).toFloat() }, onReset = { rem = secs(seq[idx]).toFloat() }
    )
}
