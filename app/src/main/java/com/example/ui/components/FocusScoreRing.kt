package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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
fun FocusScoreRing(
    sessions: Int,
    label: String = "STUDY SESSIONS",
    progress: Float = if (sessions > 0) (sessions % 10 / 10f).coerceIn(0.05f, 1f) else 0f,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp
) {
    val isDark = isAppInDarkTheme()
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "score_progress"
    )

    val trackColor = if (isDark) Color(0x30FFFFFF) else Color(0xFFE4ECD8)
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        val scale = (size.value / 190f).coerceIn(0.3f, 2.0f)
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = (10.dp * scale).toPx()
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
                    radius = (8.dp * scale).toPx(),
                    center = Offset(dotX, dotY)
                )
                // Solid dot
                drawCircle(
                    color = Color.White,
                    radius = (4.dp * scale).toPx(),
                    center = Offset(dotX, dotY)
                )
            }
        }

        // Center Score & Mascot Layout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = (4.dp * scale))
        ) {
            Image(
                painter = painterResource(id = R.drawable.mascot_focus_score),
                contentDescription = "Focus Score Mascot",
                modifier = Modifier.size((86.dp * scale)),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "$sessions",
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontSize = (28f * scale).coerceAtLeast(12f).sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            )
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontSize = (8.5f * scale).coerceAtLeast(6f).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (1.2f * scale).sp,
                    color = textSecondary
                )
            )
        }
    }
}
