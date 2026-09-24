package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FocusTimerRing(
    remainingSeconds: Int,
    totalSeconds: Int,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val isDark = isAppInDarkTheme()
    val progress = if (totalSeconds > 0) {
        (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else 1f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = if (isRunning) 1000 else 250, easing = LinearEasing),
        label = "timer_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "timer_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = if (isRunning) 0.42f else 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_breathing"
    )

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRunning) 1.03f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_breathing"
    )

    val trackColor = if (isDark) Color(0x30FFFFFF) else Color(0xFFE2EBD6)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 8.dp.toPx()
            val canvasCenter = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.width - 24.dp.toPx()) / 2f

            // 1. Soft Breathing Ambient Glow Ring
            drawCircle(
                color = RegainLimePrimary.copy(alpha = glowAlpha * 0.45f),
                radius = radius * breathingScale,
                center = canvasCenter,
                style = Stroke(width = 16.dp.toPx())
            )

            // 2. Track Ring
            drawCircle(
                color = trackColor,
                radius = radius,
                center = canvasCenter,
                style = Stroke(width = strokeWidth)
            )

            // 3. Progress Arc (smooth continuous sweep with balanced aurora gradient)
            val sweepAngle = 360f * animatedProgress
            if (sweepAngle > 0f) {
                val ringColors = if (isDark) {
                    listOf(
                        RegainLimePrimary,
                        Color(0xFF00E5FF),
                        Color(0xFF69F0AE),
                        RegainLimeLight,
                        RegainLimePrimary
                    )
                } else {
                    listOf(
                        RegainLimeDeepText,
                        RegainLimePrimary,
                        Color(0xFF00B4D8),
                        RegainLimeDeepText
                    )
                }

                rotate(degrees = -90f, pivot = canvasCenter) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = ringColors,
                            center = canvasCenter
                        ),
                        startAngle = 0f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(canvasCenter.x - radius, canvasCenter.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = strokeWidth + 1.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Head indicator pearl (soft luminous pearl, no glaring harsh white)
                val headAngleRad = Math.toRadians((-90f + sweepAngle).toDouble())
                val dotX = canvasCenter.x + radius * cos(headAngleRad).toFloat()
                val dotY = canvasCenter.y + radius * sin(headAngleRad).toFloat()

                drawCircle(
                    color = RegainLimePrimary.copy(alpha = 0.45f),
                    radius = 8.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
                drawCircle(
                    color = if (isDark) Color(0xFFF4FFE0) else RegainLimeLight,
                    radius = 4.5.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }
        }

        // Center Time Display
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val formattedTime = String.format("%02d:%02d", minutes, seconds)

        val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
        val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formattedTime,
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 52.sp,
                    lineHeight = 58.sp,
                    color = textPrimary
                )
            )
            Text(
                text = if (isRunning) "FOCUSING" else "PAUSED",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    color = if (isRunning) RegainLimeDeepText else textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
        }
    }
}
