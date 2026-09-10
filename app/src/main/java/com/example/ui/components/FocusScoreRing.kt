package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FocusScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp
) {
    val isDark = isSystemInDarkTheme()
    val animatedProgress by animateFloatAsState(
        targetValue = (score / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "score_progress"
    )

    val trackColor = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(this.size.width - strokeWidth * 2, this.size.height - strokeWidth * 2)
            val topLeft = Offset(strokeWidth, strokeWidth)

            // 1. Background Track
            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Active Lime Gradient Arc
            val sweep = 260f * animatedProgress
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            RegainLimeLight,
                            RegainLimePrimary,
                            RegainLimeDeepText
                        )
                    ),
                    startAngle = 140f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 3. Glowing end dot
                val currentAngleRad = Math.toRadians((140f + sweep).toDouble())
                val radius = (this.size.width - strokeWidth * 2) / 2f
                val centerX = this.size.width / 2f
                val centerY = this.size.height / 2f
                val dotX = centerX + radius * cos(currentAngleRad).toFloat()
                val dotY = centerY + radius * sin(currentAngleRad).toFloat()

                // Glow halo
                drawCircle(
                    color = RegainLimePrimary.copy(alpha = 0.45f),
                    radius = 10.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
                // Solid dot
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }
        }

        // Center Score Typography
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            )
            Text(
                text = "FOCUS SCORE",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    color = textSecondary
                )
            )
        }
    }
}
