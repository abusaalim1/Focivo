package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.focivoTokens
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * v1.5.0 — floating countdown pill shown over other apps while a focus
 * session runs and the user has left the app.
 *
 * Dark pill: lime play circle, big "MM:SS" + "Time remaining", X button.
 * Tapping the pill returns to the app; the X button only hides the pill
 * (it ends nothing — the session keeps running). Real countdown bound to
 * the running session: starts at [initialRemainingSeconds] and ticks
 * locally, auto-hiding at zero.
 *
 * Zero unicode emojis — the play glyph is drawn with Canvas.
 */
@Composable
fun FloatingTimerPill(
    initialRemainingSeconds: Int,
    onReturnToApp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var remainingSeconds by remember(initialRemainingSeconds) {
        mutableIntStateOf(initialRemainingSeconds.coerceAtLeast(0))
    }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        }
        onDismiss()
    }

    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    val timeText = if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    val pillInteraction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
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
        // Lime play circle (glyph drawn, not emoji)
        val pillOnAccent = focivoTokens().onAccent
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(focivoTokens().accent),
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
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Time remaining",
                color = focivoTokens().muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
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
