package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.TextStyle
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
 * v1.3.0 — the "Aurora Ring" face was REMOVED by owner order (2026-10-17):
 * its registry entry, [TimerFaceHost]/[TimerFaceThumbnail] branches and
 * AuroraTimer.kt are gone. "Minimal Digits" is now the default face and
 * the safe fallback for unknown/legacy ids.
 *
 * The faces are GENUINELY DIFFERENT layouts, not recolors:
 * - Minimal Digits: huge type, no ring, hairline progress
 * - Glass Card: frosted centered card holding everything
 * - Half Arc: half-donut progress arc with digits beneath
 * - Editorial: oversized left-aligned numerals + thin progress line
 * - nine v1.2.3 FocivoClock styles (Orbit … Bars)
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
    modifier: Modifier = Modifier
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
        // v1.3.0 — Aurora Ring removed; unknown/legacy ids (e.g. a persisted
        // "ring") fall back to the free default face, Minimal Digits.
        else -> MinimalDigitsFace(params, modifier)
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
        // v1.3.4 (item 7) — FIT, never crop: the clock designs are authored
        // at a fixed 300dp (fixed-dp digits/bars inside), but the slot can be
        // narrower than 300dp on small phones (page padding + 24dp face
        // padding). The old fixed size(300.dp) overflowed the slot; the clock
        // now uniformly scales DOWN to fit when the slot is narrower, so the
        // FULL clock is always visible. Never scales up (fit <= 1).
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (tappable) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { params.onTimeClick?.invoke() } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            val fit = (maxWidth / 300.dp).coerceAtMost(1f)
            Box(
                modifier = Modifier
                    .size(300.dp * fit)
                    .graphicsLayer(scaleX = fit, scaleY = fit),
                contentAlignment = Alignment.Center
            ) {
                FocivoClock(
                    style = style,
                    remainingSec = params.remainingSeconds,
                    totalSec = params.totalSeconds,
                    accent = accent,
                    // v1.3.2: backdrop-appropriate ink (Aurora light canvas gets dark
                    // digits; dark backdrops keep white). Fixes invisible digits.
                    content = backdropPrimary(params.theme, isDark),
                    modifier = Modifier.size(300.dp)
                )
            }
        }
        if (tappable) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "tap to edit",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                color = backdropSecondary(params.theme, isDark)
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
    modifier: Modifier = Modifier,
    // v1.3.2: explicit ink override for faces that draw the digits on a CARD
    // surface (GlassCard) rather than the backdrop. Null = backdrop ink.
    color: Color? = null
) {
    val isDark = isAppInDarkTheme()
    val tappable = params.onTimeClick != null && !params.isRunning
    // v1.3.2 (QA): hour-long sessions format as "H:MM:SS" (7 chars) and
    // overflowed at the big sizes — digits clipped at the face bounds.
    // Scale down whenever hours are present.
    val resolvedSize = if (params.remainingSeconds >= 3600) fontSize * 0.72f else fontSize
    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
    ) {
        Text(
            text = formatFaceTime(params.remainingSeconds),
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = resolvedSize,
            letterSpacing = letterSpacing,
            color = color ?: backdropPrimary(params.theme, isDark),
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
                color = backdropSecondary(params.theme, isDark)
            )
        }
    }
}

/**
 * v1.3.2 (item 12) — the RUNNING timer's pause and restart buttons, one
 * shared implementation for every face: lime (focus) / mint (break) filled
 * play-pause with a glass rim-light + soft accent shadow, and a frosted
 * glass reset circle. The press state is a real spring scale-down: the
 * interaction source is SHARED between pressFeedback and clickable (the old
 * per-face copies each created their own source, so the press animation
 * never fired). Sizes stay exactly as each face had them; visuals only,
 * no behaviour change. Public so screenshot tests can capture them.
 */
@Composable
fun FacePlayPauseButton(
    isRunning: Boolean,
    isBreak: Boolean,
    onClick: () -> Unit,
    buttonSize: Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    val accent = if (isBreak) FaceMint else FaceLime
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(buttonSize)
            .shadow(8.dp, CircleShape, ambientColor = accent.copy(alpha = 0.35f))
            .clip(CircleShape)
            .background(accent)
            // Glass rim-light: a thin white edge catches the light, matching
            // the rest of the design system's glass treatment.
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .pressFeedback(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isRunning) "Pause" else "Start",
            tint = NearBlack,
            modifier = Modifier.size(buttonSize * 0.42f)
        )
    }
}

@Composable
fun FaceResetButton(
    onClick: () -> Unit,
    buttonSize: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(if (isDark) Color(0x22FFFFFF) else Color(0x14000000))
            .border(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x22000000), CircleShape)
            .pressFeedback(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Reset timer",
            tint = if (isDark) Color(0xFFDDE3DA) else Color(0xFF3A4038),
            modifier = Modifier.size(buttonSize * 0.42f)
        )
    }
}

/**
 * Shared control row for the non-clock faces — consistent placement:
 * primary play/pause pill + quiet reset circle, centered below the visual.
 */
@Composable
private fun FaceControlRow(
    isRunning: Boolean,
    isBreak: Boolean,
    onTogglePlay: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        FacePlayPauseButton(
            isRunning = isRunning,
            isBreak = isBreak,
            onClick = onTogglePlay,
            buttonSize = 72.dp
        )
        Spacer(modifier = Modifier.width(14.dp))
        FaceResetButton(
            onClick = onReset,
            buttonSize = 48.dp
        )
    }
}

private fun faceTextPrimary(isDark: Boolean): Color =
    if (isDark) Color(0xFFF2F5EE) else NearBlack

private fun faceTextSecondary(isDark: Boolean): Color =
    if (isDark) Color(0xFFA2AAA0) else Color(0xFF5A6258)

/**
 * v1.3.0 assembly: backdrop-aware text colors. Timer faces are drawn on the
 * SELECTED TIMER THEME's full-bleed backdrop, not on the app surface — so in
 * light app mode with a dark theme (e.g. Midnight), isAppInDarkTheme() picks
 * dark digits that vanish on the dark backdrop. The theme's onBackdropContent
 * is the designed content color for its backdrop; fall back to the app-theme
 * heuristic only when no theme is attached.
 */
private fun backdropPrimary(theme: TimerTheme?, isDark: Boolean): Color =
    theme?.backdropContent(isDark) ?: faceTextPrimary(isDark)

/**
 * v1.3.3 (item 9): backdrop-aware SECONDARY text (hints, captions) drawn on
 * the selected timer theme's full-bleed backdrop. Previously this reduced
 * the content color to 0.72 alpha; small text then sat below the 4.5:1
 * contrast floor on several backdrops, so it now resolves to the full-alpha
 * [TimerTheme.backdropCaptionContent] token. The no-theme fallback keeps the
 * app-theme heuristic.
 */
private fun backdropSecondary(theme: TimerTheme?, isDark: Boolean): Color =
    theme?.backdropCaptionContent(isDark) ?: faceTextSecondary(isDark)

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
            color = backdropSecondary(params.theme, isDark)
        )
        Spacer(modifier = Modifier.height(20.dp))
        // Hairline progress — track tint follows the backdrop, not the app theme.
        val onBackdrop = params.theme?.backdropContent(isDark)
        val trackColor = when {
            onBackdrop != null -> onBackdrop.copy(alpha = 0.18f)
            isDark -> Color(0x33FFFFFF)
            else -> Color(0x1A000000)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(4.dp)
                .clip(CircleShape)
                .background(trackColor)
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
                // v1.3.2: the glass card is a LIGHT surface in light app mode —
                // backdrop ink (light) rendered the digits invisible on it.
                FaceTappableTime(
                    params = params,
                    fontSize = 64.sp,
                    letterSpacing = (-0.5).sp,
                    color = faceTextPrimary(isDark)
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Rounded progress bar — v1.3.2 (QA): the old track used the
                // BACKDROP ink, which went invisible on the light card when a
                // dark backdrop theme (e.g. Midnight) was selected in light
                // app mode. Card-surface ink (same as the % labels below).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(faceTextSecondary(isDark).copy(alpha = 0.35f))
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
                    // Compact controls inside the card — v1.3.2 (item 12):
                    // the shared buttons (same glass/lime styling + press
                    // states as every other face; sizes unchanged).
                    FacePlayPauseButton(
                        isRunning = params.isRunning,
                        isBreak = params.isBreak,
                        onClick = params.onTogglePlay,
                        buttonSize = 60.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    FaceResetButton(
                        onClick = params.onReset,
                        buttonSize = 44.dp
                    )
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
    val trackColor = backdropSecondary(params.theme, isDark).copy(alpha = 0.4f)

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
                // v1.3.2 (QA): a TRUE semicircle on every screen width — the
                // radius comes from the measured width. The old box-proportioned
                // ellipse stretched tall on narrow phones.
                val r = (size.width - stroke) / 2f
                val topLeft = Offset(size.width / 2f - r, stroke / 2f)
                val arcSize = Size(r * 2f, r * 2f)
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
                    color = backdropSecondary(params.theme, isDark)
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
    // v1.3.2 (QA): the lime/mint eyebrow + "Edit" ink followed the APP theme,
    // so they went low-contrast on light backdrops (Aurora in light mode:
    // lime-on-cream) and dark-on-dark (Midnight backdrop in light app mode).
    // Pick the label ink from the backdrop's luminance, mirroring
    // FacePhasePill. The filled play button + progress line keep the accent.
    val bg = backdropPrimary(params.theme, isDark)
    val bgLuminance = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    val eyebrowColor = if (bgLuminance > 0.5f) {
        if (params.isBreak) FaceMint else FaceLime
    } else {
        if (params.isBreak) Color(0xFF2E7D32) else RegainLimeDeepText
    }

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
            color = eyebrowColor
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
                .background(backdropSecondary(params.theme, isDark).copy(alpha = 0.35f))
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
                color = backdropSecondary(params.theme, isDark),
                modifier = Modifier.weight(1f)
            )
            if (params.onTagClick != null && !params.isRunning && !params.isBreak) {
                Text(
                    text = "Edit",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = eyebrowColor,
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
            // Left-aligned controls — v1.3.2 (item 12): the shared buttons
            // (same glass/lime styling + press states; sizes unchanged).
            FacePlayPauseButton(
                isRunning = params.isRunning,
                isBreak = params.isBreak,
                onClick = params.onTogglePlay,
                buttonSize = 64.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            FaceResetButton(
                onClick = params.onReset,
                buttonSize = 46.dp
            )
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
            // v1.3.0 — Aurora Ring removed; the fallback thumbnail is the
            // Minimal Digits mini render (also used for legacy "ring").
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        }
    }
}

/**
 * v1.2.3 — mini render of one new clock style for the picker. The clock is
 * drawn at its natural 300dp size and scaled down to fit the card.
 *
 * v1.3.2 (item 13) — EXACT fit: the 300dp clock center-scales to exactly
 * 96dp and the tile is exactly 96dp, so no face clips at the card bounds.
 * The old 88dp tile cropped ~4dp per side (FLIP's wide digits far more).
 *
 * v1.3.4 (item 7) — BREATHING ROOM: the exact 96dp fit drew the clock
 * edge-to-edge, so outer details (orbit tick ring, FLIP's wide digit row)
 * kissed the tile's rounded-corner clip and read as "zoomed". The clock now
 * renders at 88dp inside the 96dp tile (4dp margin per side) — the FULL
 * clock is always visible with clear space around it.
 */
@Composable
private fun ClockStyleThumbnail(style: ClockStyle) {
    val tile = 96.dp
    // 88/300 — the render target inside the tile; keep in sync with the
    // graphicsLayer scale below.
    val fit = 88f / 300f
    Box(
        modifier = Modifier
            .size(tile)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0D110E)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .graphicsLayer(scaleX = fit, scaleY = fit)
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
    cardBorder: Color,
    // v1.2.3: test hook — lets screenshot tests pre-scroll the row.
    scrollState: ScrollState = rememberScrollState(),
    // v1.2.4: admin account (owner) unlocks every face.
    isAdmin: Boolean = false
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
            // Motion Lab #7 — points balance rolls like an odometer.
            val pointsStyle = TextStyle(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = textPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Your Focus Points: ", style = pointsStyle)
                RollingNumber(value = focusPoints, style = pointsStyle)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .testTag("timer_face_picker_row"),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimerFaceManager.FACES.forEach { face ->
                val isSelected = face.id == selectedFaceId
                val isLocked = !face.isUnlocked(focusPoints, isAdmin)
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
