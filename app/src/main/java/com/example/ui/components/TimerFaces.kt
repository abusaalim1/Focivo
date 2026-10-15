package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.ui.timer.ClockStyle
import com.example.ui.timer.FocivoClock
import com.example.util.TimerFace
import com.example.util.TimerFaceManager
import com.example.util.TimerTheme

/**
 * v1.1 — switchable timer face templates (Apple/Notion minimal aesthetic).
 *
 * Template 1 "Aurora Ring" is the existing [AuroraTimer] flagship. The four
 * new faces are GENUINELY DIFFERENT layouts, not recolors:
 * - Minimal Digits: huge type, no ring, hairline progress
 * - Glass Card: frosted centered card holding everything
 * - Half Arc: half-donut progress arc with digits beneath
 * - Editorial: oversized left-aligned numerals + thin progress line
 *
 * Every face keeps: the themed backdrop (drawn by the caller behind),
 * themed mascot ([MascotPose]), the session/subject chip, readable contrast
 * in light AND dark, and the mint shift on break phases.
 */

private val FaceLime = Color(0xFF8FD400)
private val FaceMint = Color(0xFF69F0AE)

data class TimerFaceParams(
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val isRunning: Boolean,
    val isBreak: Boolean,
    val subjectTitle: String,
    val mascotPose: MascotPose,
    val theme: TimerTheme?,
    val onTogglePlay: () -> Unit,
    val onReset: () -> Unit,
    val onTagClick: (() -> Unit)?,
    val onTimeClick: (() -> Unit)? = null,
    val mascotContent: @Composable (() -> Unit)? = null,
    val onMascotClick: (() -> Unit)? = null
)

fun formatFaceTime(remainingSeconds: Int): String {
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun TimerFaceHost(
    params: TimerFaceParams,
    faceId: String,
    modifier: Modifier = Modifier,
    dialSize: Dp = 352.dp
) {
    when (faceId) {
        "digits" -> MinimalDigitsFace(params, modifier)
        "glass" -> GlassCardFace(params, modifier)
        "arc" -> HalfArcFace(params, modifier)
        "editorial" -> EditorialFace(params, modifier)
        // v1.2.3 — nine new clock styles. Each renders FocivoClock in the
        // dial slot with the equipped-study mascot beneath it.
        "clock_orbit" -> ClockStyleFace(params, ClockStyle.ORBIT, modifier)
        "clock_flip" -> ClockStyleFace(params, ClockStyle.FLIP, modifier)
        "clock_liquid" -> ClockStyleFace(params, ClockStyle.LIQUID, modifier)
        "clock_editorial" -> ClockStyleFace(params, ClockStyle.EDITORIAL, modifier)
        "clock_dial" -> ClockStyleFace(params, ClockStyle.DIAL, modifier)
        "clock_horizon" -> ClockStyleFace(params, ClockStyle.HORIZON, modifier)
        "clock_stack" -> ClockStyleFace(params, ClockStyle.STACK, modifier)
        "clock_breathe" -> ClockStyleFace(params, ClockStyle.BREATHE, modifier)
        "clock_bars" -> ClockStyleFace(params, ClockStyle.BARS, modifier)
        else -> AuroraTimer(
            totalSeconds = params.totalSeconds,
            remainingSeconds = params.remainingSeconds,
            isRunning = params.isRunning,
            isBreak = params.isBreak,
            subjectTitle = params.subjectTitle,
            modifier = modifier,
            dialSize = dialSize,
            mascotPose = params.mascotPose,
            theme = params.theme,
            onTogglePlay = params.onTogglePlay,
            onReset = params.onReset,
            onTagClick = params.onTagClick,
            onTimeClick = params.onTimeClick,
            mascotContent = params.mascotContent,
            onMascotClick = params.onMascotClick
        )
    }
}

/**
 * v1.2.3 — host for the nine new FocivoClock styles. Renders the clock in
 * the dial slot, then the equipped-study mascot beneath it at modest size
 * (tap still opens the Mascot screen), then the shared control row.
 * The clock itself is tappable when idle/paused (tap-to-edit durations,
 * same rule as the other faces).
 */
@Composable
private fun ClockStyleFace(
    params: TimerFaceParams,
    style: ClockStyle,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val accent = params.theme?.accent ?: FaceLime
    val tappable = params.onTimeClick != null && !params.isRunning

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FacePhasePill(
            subjectTitle = params.subjectTitle,
            isBreak = params.isBreak,
            isRunning = params.isRunning,
            onTagClick = params.onTagClick
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier.then(
                if (tappable) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { params.onTimeClick?.invoke() } else Modifier
            ),
            contentAlignment = Alignment.Center
        ) {
            FocivoClock(
                style = style,
                remainingSec = params.remainingSeconds,
                totalSec = params.totalSeconds,
                accent = accent,
                modifier = Modifier.size(300.dp)
            )
        }
        if (tappable) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "tap to edit",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                color = faceTextSecondary(isDark).copy(alpha = 0.75f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        // The equipped-study buddy, preserved under every new clock style.
        if (params.mascotContent != null) {
            Box(
                modifier = Modifier.then(
                    if (params.onMascotClick != null) {
                        Modifier
                            .pressFeedback()
                            .clickable(onClick = params.onMascotClick)
                    } else Modifier
                ),
                contentAlignment = Alignment.Center
            ) {
                params.mascotContent()
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        FaceControlRow(
            isRunning = params.isRunning,
            isBreak = params.isBreak,
            onTogglePlay = params.onTogglePlay,
            onReset = params.onReset
        )
    }
}

/**
 * v1.2: the generic "study" illustration (old standalone art) is never shown on
 * the timer — the equipped outfit set renders instead. Themed mascots and all
 * other poses keep their standalone art.
 */
@Composable
private fun TimerFaceMascot(
    pose: MascotPose,
    size: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    if (pose == MascotPose.STUDY_HAPPY) {
        BuddyMascot(
            modifier = Modifier.width(size),
            pose = com.example.util.MascotPose.STUDY
        )
    } else {
        RegainMascotView(
            size = size,
            pose = pose,
            animate = animate,
            modifier = modifier
        )
    }
}

/** Shared session/subject chip — lime for focus, mint shift for breaks. */
@Composable
private fun FacePhasePill(
    subjectTitle: String,
    isBreak: Boolean,
    isRunning: Boolean,
    onTagClick: (() -> Unit)?
) {
    val isDark = isAppInDarkTheme()
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isBreak) {
                    if (isDark) Color(0x3069F0AE) else Color(0xFFE8F5E9)
                } else {
                    if (isDark) FaceLime.copy(alpha = 0.19f) else Color(0xFFE4F2C8)
                }
            )
            .border(
                1.dp,
                if (isBreak) Color(0x4569F0AE) else FaceLime.copy(alpha = 0.27f),
                RoundedCornerShape(14.dp)
            )
            .then(
                if (onTagClick != null && !isRunning && !isBreak) Modifier.clickable(onClick = onTagClick)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRunning) {
                            if (isBreak) Color(0xFF00E676) else FaceLime
                        } else Color.Gray
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isBreak) "SOOTHING BREAK" else subjectTitle.uppercase(),
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = if (isBreak) {
                    if (isDark) FaceMint else Color(0xFF2E7D32)
                } else {
                    if (isDark) FaceLime else RegainLimeDeepText
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


/**
 * v1.1: tap-time-to-modify digits shared by the four new faces.
 * Tappable when idle/paused (onTimeClick set); running sessions keep the
 * existing no-edit rule. A micro "tap to edit" label keeps it discoverable.
 */
@Composable
private fun FaceTappableTime(
    params: TimerFaceParams,
    fontSize: TextUnit,
    letterSpacing: TextUnit = (-1).sp,
    textAlign: TextAlign = TextAlign.Center,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val tappable = params.onTimeClick != null && !params.isRunning
    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
    ) {
        Text(
            text = formatFaceTime(params.remainingSeconds),
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = letterSpacing,
            color = faceTextPrimary(isDark),
            textAlign = textAlign,
            modifier = Modifier.then(
                if (tappable) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { params.onTimeClick?.invoke() } else Modifier
            )
        )
        if (tappable) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "tap to edit",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                color = faceTextSecondary(isDark).copy(alpha = 0.75f)
            )
        }
    }
}

/**
 * Shared control row for the four new faces — consistent placement:
 * primary play/pause pill + quiet reset circle, centered below the visual.
 * (Aurora Ring keeps its own in-dial controls as the flagship template.)
 */
@Composable
private fun FaceControlRow(
    isRunning: Boolean,
    isBreak: Boolean,
    onTogglePlay: () -> Unit,
    onReset: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val accent = if (isBreak) FaceMint else FaceLime
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(8.dp, CircleShape, ambientColor = accent.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isRunning) "Pause" else "Start",
                tint = NearBlack,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0x22FFFFFF) else Color(0x14000000))
                .border(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x22000000), CircleShape)
                .clickable(onClick = onReset),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset timer",
                tint = if (isDark) Color(0xFFDDE3DA) else Color(0xFF3A4038),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun faceTextPrimary(isDark: Boolean): Color =
    if (isDark) Color(0xFFF2F5EE) else NearBlack

private fun faceTextSecondary(isDark: Boolean): Color =
    if (isDark) Color(0xFFA2AAA0) else Color(0xFF5A6258)

// ---------------------------------------------------------------------------
// Face 2 — Minimal Digits: huge type, no ring, hairline progress.
// ---------------------------------------------------------------------------
@Composable
private fun MinimalDigitsFace(
    params: TimerFaceParams,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val progress = if (params.totalSeconds > 0) {
        ((params.totalSeconds - params.remainingSeconds).toFloat() / params.totalSeconds).coerceIn(0f, 1f)
    } else 0f
    val accent = if (params.isBreak) FaceMint else FaceLime

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FacePhasePill(
            subjectTitle = params.subjectTitle,
            isBreak = params.isBreak,
            isRunning = params.isRunning,
            onTagClick = params.onTagClick
        )
        Spacer(modifier = Modifier.height(18.dp))
        FaceTappableTime(params = params, fontSize = 88.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (params.isBreak) "BREAK · RECHARGE" else "FOCUS · STAY WITH IT",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 2.5.sp,
            color = faceTextSecondary(isDark)
        )
        Spacer(modifier = Modifier.height(20.dp))
        // Hairline progress
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(4.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0x33FFFFFF) else Color(0x1A000000))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        TimerFaceMascot(
            size = 84.dp,
            pose = params.mascotPose,
            animate = params.isRunning
        )
        Spacer(modifier = Modifier.height(22.dp))
        FaceControlRow(
            isRunning = params.isRunning,
            isBreak = params.isBreak,
            onTogglePlay = params.onTogglePlay,
            onReset = params.onReset
        )
    }
}

// ---------------------------------------------------------------------------
// Face 3 — Glass Card: frosted centered card holding everything.
// ---------------------------------------------------------------------------
@Composable
private fun GlassCardFace(
    params: TimerFaceParams,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val progress = if (params.totalSeconds > 0) {
        ((params.totalSeconds - params.remainingSeconds).toFloat() / params.totalSeconds).coerceIn(0f, 1f)
    } else 0f
    val accent = if (params.isBreak) FaceMint else FaceLime

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(if (isDark) Color(0xD0171F19) else Color(0xF2FFFFFF))
                .border(
                    1.dp,
                    if (isDark) Color(0x40FFFFFF) else Color(0x90000000).copy(alpha = 0.06f),
                    RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 22.dp, vertical = 26.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FacePhasePill(
                    subjectTitle = params.subjectTitle,
                    isBreak = params.isBreak,
                    isRunning = params.isRunning,
                    onTagClick = params.onTagClick
                )
                Spacer(modifier = Modifier.height(14.dp))
                FaceTappableTime(params = params, fontSize = 64.sp, letterSpacing = (-0.5).sp)
                Spacer(modifier = Modifier.height(12.dp))
                // Rounded progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0x2AFFFFFF) else Color(0x14000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = faceTextSecondary(isDark)
                    )
                    Text(
                        text = if (params.isBreak) "Break" else "Focus",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = faceTextSecondary(isDark)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimerFaceMascot(
                        size = 64.dp,
                        pose = params.mascotPose,
                        animate = params.isRunning
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    // Compact controls inside the card
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .clickable(onClick = params.onTogglePlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (params.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (params.isRunning) "Pause" else "Start",
                            tint = NearBlack,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x22000000), CircleShape)
                            .clickable(onClick = params.onReset),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset timer",
                            tint = faceTextSecondary(isDark),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Face 4 — Half Arc: half-donut progress arc with digits beneath.
// ---------------------------------------------------------------------------
@Composable
private fun HalfArcFace(
    params: TimerFaceParams,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val progress = if (params.totalSeconds > 0) {
        ((params.totalSeconds - params.remainingSeconds).toFloat() / params.totalSeconds).coerceIn(0f, 1f)
    } else 0f
    val accent = if (params.isBreak) FaceMint else FaceLime
    val trackColor = if (isDark) Color(0x2EFFFFFF) else Color(0x16000000)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FacePhasePill(
            subjectTitle = params.subjectTitle,
            isBreak = params.isBreak,
            isRunning = params.isRunning,
            onTagClick = params.onTagClick
        )
        Spacer(modifier = Modifier.height(10.dp))
        // Half-donut arc (top half), digits beneath the baseline
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .height(190.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 26.dp.toPx()
                val arcSize = Size(size.width - stroke, (size.height - stroke / 2) * 2)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(
                    color = trackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = 180f,
                    sweepAngle = 180f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 64.dp)
            ) {
                FaceTappableTime(params = params, fontSize = 52.sp, letterSpacing = (-0.5).sp)
                Text(
                    text = "${(progress * 100).toInt()}% complete",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = faceTextSecondary(isDark)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        TimerFaceMascot(
            size = 72.dp,
            pose = params.mascotPose,
            animate = params.isRunning
        )
        Spacer(modifier = Modifier.height(14.dp))
        FaceControlRow(
            isRunning = params.isRunning,
            isBreak = params.isBreak,
            onTogglePlay = params.onTogglePlay,
            onReset = params.onReset
        )
    }
}

// ---------------------------------------------------------------------------
// Face 5 — Editorial: oversized left-aligned numerals + thin progress line.
// ---------------------------------------------------------------------------
@Composable
private fun EditorialFace(
    params: TimerFaceParams,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val progress = if (params.totalSeconds > 0) {
        ((params.totalSeconds - params.remainingSeconds).toFloat() / params.totalSeconds).coerceIn(0f, 1f)
    } else 0f
    val accent = if (params.isBreak) FaceMint else FaceLime

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 16.dp)
    ) {
        Text(
            text = if (params.isBreak) "BREAK" else "FOCUS",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 4.sp,
            color = accent
        )
        Spacer(modifier = Modifier.height(8.dp))
        FaceTappableTime(
            params = params,
            fontSize = 96.sp,
            letterSpacing = (-2).sp,
            textAlign = TextAlign.Start,
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        // Thin editorial progress line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (isDark) Color(0x2AFFFFFF) else Color(0x14000000))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(accent)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = params.subjectTitle,
                fontFamily = PoppinsFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = faceTextSecondary(isDark),
                modifier = Modifier.weight(1f)
            )
            if (params.onTagClick != null && !params.isRunning && !params.isBreak) {
                Text(
                    text = "Edit",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) FaceLime else RegainLimeDeepText,
                    modifier = Modifier
                        .clickable(onClick = params.onTagClick)
                        .padding(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TimerFaceMascot(
                size = 76.dp,
                pose = params.mascotPose,
                animate = params.isRunning
            )
            Spacer(modifier = Modifier.weight(1f))
            // Left-aligned controls: play pill + reset
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(8.dp, CircleShape, ambientColor = accent.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(onClick = params.onTogglePlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (params.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (params.isRunning) "Pause" else "Start",
                    tint = NearBlack,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x22000000), CircleShape)
                    .clickable(onClick = params.onReset),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset timer",
                    tint = faceTextSecondary(isDark),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Live preview thumbnails for the picker — faithful mini renders of each face.
// ---------------------------------------------------------------------------
@Composable
fun TimerFaceThumbnail(
    faceId: String,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val bg = if (isDark) Color(0xFF141814) else Color(0xFFF4F6F1)
    val sampleProgress = 0.4f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        when (faceId) {
            "digits" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "25:00",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = faceTextPrimary(isDark)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0x33FFFFFF) else Color(0x1A000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(sampleProgress)
                            .height(3.dp)
                            .background(FaceLime)
                    )
                }
            }
            "glass" -> Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xD0171F19) else Color(0xF2FFFFFF))
                    .border(1.dp, if (isDark) Color(0x40FFFFFF) else Color(0x1A000000), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "25:00",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = faceTextPrimary(isDark)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0x2AFFFFFF) else Color(0x14000000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sampleProgress)
                                .height(6.dp)
                                .background(FaceLime)
                        )
                    }
                }
            }
            "arc" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(modifier = Modifier.size(width = 84.dp, height = 48.dp)) {
                    val stroke = 10.dp.toPx()
                    val arcSize = Size(size.width - stroke, (size.height - stroke / 2) * 2)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(
                        color = if (isDark) Color(0x2EFFFFFF) else Color(0x16000000),
                        startAngle = 180f, sweepAngle = 180f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = FaceLime,
                        startAngle = 180f, sweepAngle = 180f * sampleProgress, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    "25:00",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = faceTextPrimary(isDark)
                )
            }
            "editorial" -> Column(
                modifier = Modifier.fillMaxWidth(0.86f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "FOCUS",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    letterSpacing = 3.sp,
                    color = FaceLime
                )
                Text(
                    "25:00",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = (-1).sp,
                    color = faceTextPrimary(isDark)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(2.dp)
                    .background(if (isDark) Color(0x2AFFFFFF) else Color(0x14000000))) {
                    Box(modifier = Modifier.fillMaxWidth(sampleProgress).height(2.dp).background(FaceLime))
                }
            }
            // v1.2.3 — real mini renders of the nine new clock styles.
            // The designs are dark-native (white ink), so each renders on
            // a fixed dark tile that reads in both app themes.
            "clock_orbit" -> ClockStyleThumbnail(ClockStyle.ORBIT)
            "clock_flip" -> ClockStyleThumbnail(ClockStyle.FLIP)
            "clock_liquid" -> ClockStyleThumbnail(ClockStyle.LIQUID)
            "clock_editorial" -> ClockStyleThumbnail(ClockStyle.EDITORIAL)
            "clock_dial" -> ClockStyleThumbnail(ClockStyle.DIAL)
            "clock_horizon" -> ClockStyleThumbnail(ClockStyle.HORIZON)
            "clock_stack" -> ClockStyleThumbnail(ClockStyle.STACK)
            "clock_breathe" -> ClockStyleThumbnail(ClockStyle.BREATHE)
            "clock_bars" -> ClockStyleThumbnail(ClockStyle.BARS)
            else -> {
                // Ring thumbnail: simple glowing ring with digits
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(84.dp)) {
                        val stroke = 9.dp.toPx()
                        drawArc(
                            color = if (isDark) Color(0x2EFFFFFF) else Color(0x16000000),
                            startAngle = 0f, sweepAngle = 360f, useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = FaceLime,
                            startAngle = -90f, sweepAngle = 360f * sampleProgress, useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        "25:00",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = faceTextPrimary(isDark)
                    )
                }
            }
        }
    }
}

/**
 * v1.2.3 — mini render of one new clock style for the picker. The clock is
 * drawn at its natural 300dp size and scaled down to fit the card.
 */
@Composable
private fun ClockStyleThumbnail(style: ClockStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D110E)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .graphicsLayer(scaleX = 0.32f, scaleY = 0.32f)
        ) {
            FocivoClock(
                style = style,
                remainingSec = 1500,
                totalSec = 1500,
                accent = FaceLime
            )
        }
    }
}

/**
 * "Timer Style" picker — lives in the Customize Timer sheet beside Themes.
 * Live preview thumbnails, Focus Points balance in the header (like the
 * theme picker), gold lock + price on locked cards.
 */
@Composable
fun TimerFacePicker(
    focusPoints: Int,
    selectedFaceId: String,
    onFaceTap: (face: TimerFace, isLocked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textPrimary: Color,
    cardBorder: Color
) {
    val isDark = isAppInDarkTheme()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Your Focus Points: $focusPoints",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = textPrimary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("timer_face_picker_row"),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimerFaceManager.FACES.forEach { face ->
                val isSelected = face.id == selectedFaceId
                val isLocked = !face.isUnlocked(focusPoints)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onFaceTap(face, isLocked) }
                ) {
                    Box(
                        modifier = Modifier
                            .width(104.dp)
                            .height(138.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        TimerFaceThumbnail(
                            faceId = face.id,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(2.5.dp, FaceLime, RoundedCornerShape(16.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                            )
                        }
                        if (isLocked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xAA0B0F0B))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC1A221B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            // Price strip
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Color(0xCC0B0F0B))
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${face.unlockPoints} pts",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFD54F)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = face.name,
                        fontFamily = PoppinsFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = if (isSelected) {
                            if (isDark) FaceLime else RegainLimeDeepText
                        } else textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = face.tagline,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 9.5.sp,
                        color = if (isDark) Color(0xFF8E8E93) else SecondaryTextLight,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Same English explainer dialog pattern as the theme picker. */
@Composable
fun LockedTimerFaceDialog(
    lockedFace: TimerFace,
    focusPoints: Int,
    onDismiss: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Locked timer face",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = textPrimary
            )
        },
        text = {
            Text(
                text = "Collect ${(lockedFace.unlockPoints - focusPoints).coerceAtLeast(1)} more Focus Points to unlock ${lockedFace.name}. You earn 1 point per focused minute.",
                fontFamily = PoppinsFontFamily,
                fontSize = 13.sp,
                color = textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Got it",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RegainLimePrimary
                )
            }
        }
    )
}
