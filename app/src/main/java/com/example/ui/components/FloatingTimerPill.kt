package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.FloatingPillDocking
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.timer.FtFDisplay
import com.example.ui.timer.PomoPhase
import com.example.ui.timer.PomodoroStyle
import java.util.Locale

/**
 * v1.3.1 — floating countdown pill shown over other apps while a focus
 * session runs and the user has left the app.
 *
 * Dark pill: lime play circle, big "MM:SS" + "Time remaining", X button.
 * Tapping the pill returns to the app; the X button only hides the pill
 * (it ends nothing — the session keeps running).
 *
 * The countdown is owned by the caller ([remainingSeconds] ticks down in
 * the overlay host) so docking/expanding never resets it. Horizontal drags
 * are reported to [dragController]; small movements stay a tap and return
 * to the app as before. Dragging/docking logic lives in
 * FloatingTimerPillManager + FloatingPillDocking.
 *
 * v1.3.2 (item 14) — pomodoro reflection: while a pomodoro run is active the
 * pill speaks the selected design's phase language — the accent circle, the
 * subtitle and the mini-pill ring take the CURRENT phase's color
 * (Focus = lime, Short break = mint, Long break = sand), the subtitle names
 * the phase + cycle position ("FOCUS · SESSION 2 OF 4"), and a slim bar shows
 * whole-cycle progress. The manager re-reads the live engine state every
 * second, so phase flips reflect in real time. Typographic renders its phase
 * label in Instrument Serif italic, like the design itself.
 *
 * Zero unicode emojis — the play glyph is drawn with Canvas.
 */
@Composable
fun FloatingTimerPill(
    remainingSeconds: Int,
    onReturnToApp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dragController: FloatingPillDragController? = null,
    // v1.3.2 (item 14): pomodoro phase reflection — null = classic timer.
    pomoPhase: PomoPhase? = null,
    pomoSubtitle: String? = null,
    pomoCycleProgress: Float? = null,
    pomoStyle: PomodoroStyle = PomodoroStyle.TICK_RING
) {
    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    val timeText = if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    val pillInteraction = remember { MutableInteractionSource() }

    // Drag detection sits OUTSIDE the clickable: once the horizontal-drag
    // slop is exceeded the drag consumes the gesture and the tap never
    // fires; small movements stay a tap -> return-to-app.
    val dragModifier = if (dragController != null) {
        Modifier.pointerInput(dragController) {
            detectHorizontalDragGestures(
                onDragStart = { dragController.onDragStart() },
                onDragEnd = { dragController.onDragEnd() },
                onDragCancel = { dragController.onDragEnd() },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragController.onDragDelta(dragAmount)
                }
            )
        }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(dragModifier)
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.6f))
            .clip(RoundedCornerShape(22.dp))
            .background(focivoTokens().surface)
            .border(1.dp, focivoTokens().line, RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = pillInteraction,
                indication = null,
                onClick = onReturnToApp
            )
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // v1.3.2 (item 14): accent circle follows the pomodoro phase color
        // (lime Focus / mint Short break / sand Long break); classic timers
        // keep the lime accent. Glyph drawn, not emoji.
        val phaseColor = pomoPhase?.color ?: focivoTokens().accent
        val pillOnAccent = focivoTokens().onAccent
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(phaseColor),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.28f, h * 0.18f)
                    lineTo(w * 0.78f, h * 0.5f)
                    lineTo(w * 0.28f, h * 0.82f)
                    close()
                }
                drawPath(path, pillOnAccent)
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = timeText,
                color = Color.White,
                fontFamily = PoppinsFontFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            if (pomoPhase != null && pomoSubtitle != null) {
                // Phase-appropriate label language, live from the engine.
                val isTypographic = pomoStyle == PomodoroStyle.TYPOGRAPHIC
                Text(
                    text = pomoSubtitle,
                    color = phaseColor,
                    fontFamily = if (isTypographic) FtFDisplay else PoppinsFontFamily,
                    fontStyle = if (isTypographic) FontStyle.Italic else FontStyle.Normal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (pomoCycleProgress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(pomoCycleProgress.coerceIn(0f, 1f))
                                .clip(CircleShape)
                                .background(phaseColor)
                        )
                    }
                }
            } else {
                Text(
                    text = "Time remaining",
                    color = focivoTokens().muted,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(2.dp))

        // X button: hides the pill only, ends nothing.
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(focivoTokens().surface2)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Hide timer",
                tint = focivoTokens().text,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * v1.3.1 — callbacks the floating pill reports horizontal drag gestures
 * to (implemented by FloatingTimerPillManager). Null in previews/tests.
 */
interface FloatingPillDragController {
    fun onDragStart()
    fun onDragDelta(deltaPx: Float)
    fun onDragEnd()
}

/**
 * v1.3.1 — docked form of the floating timer pill: a compact circular
 * mini-pill showing the countdown "MM:SS" with a lime-ring accent, using
 * the same design tokens as the expanded pill.
 *
 * v1.3.2 (item 14): the ring takes the pomodoro phase color while a
 * pomodoro run is active (lime Focus / mint Short break / sand Long break).
 *
 * Tapping the mini-pill expands back to the full pill; it never hides or
 * ends anything.
 */
@Composable
fun FloatingTimerMiniPill(
    remainingSeconds: Int,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    phaseColor: Color? = null
) {
    val tokens = focivoTokens()
    Box(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.6f))
            .clip(CircleShape)
            .background(tokens.surface)
            .border(2.dp, phaseColor ?: tokens.accent, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onExpand
            )
            .size(FloatingPillDocking.DOCKED_SIZE_DP.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = FloatingPillDocking.formatDockedCountdown(remainingSeconds),
            color = tokens.text,
            fontFamily = PoppinsFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
