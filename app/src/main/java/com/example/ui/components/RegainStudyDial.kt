package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppleLinearFontFamily
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import kotlin.math.cos
import kotlin.math.sin

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.rotate

@Composable
fun RegainStudyDial(
    remainingSeconds: Int,
    totalSeconds: Int,
    isRunning: Boolean,
    activeTag: String,
    onTagClick: () -> Unit,
    onTakeBreakClick: () -> Unit,
    onMascotClick: () -> Unit = {},
    mascotPose: MascotPose = MascotPose.IDLE,
    modifier: Modifier = Modifier,
    size: Dp = 340.dp
) {
    val progress = if (totalSeconds > 0) {
        (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else 1f

    val transition = rememberInfiniteTransition(label = "dial_glow")
    val pulseGlow by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = if (isRunning) 0.75f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 10.dp.toPx()
            val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.width - 32.dp.toPx()) / 2f

            // 1. Obsidian Black Dial Disc (Solid, deep premium obsidian background)
            drawCircle(
                color = if (isDark) Color(0xFF0D0E12) else Color(0xFF121316),
                radius = radius + 10.dp.toPx(),
                center = centerOffset
            )

            // Subtle outer ambient obsidian halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = pulseGlow * 0.06f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = radius + 24.dp.toPx()
                ),
                radius = radius + 24.dp.toPx(),
                center = centerOffset
            )

            // 2. Dial track background (Sleek minimalist translucent white track)
            val trackColor = Color(0x1FFFFFFF)
            drawCircle(
                color = trackColor,
                radius = radius,
                center = centerOffset,
                style = Stroke(width = strokeWidth)
            )

            // 3. Minimalist White Gradient Progress Arc
            val sweepAngle = 360f * progress
            if (sweepAngle > 0f) {
                val arcTopLeft = Offset(centerOffset.x - radius, centerOffset.y - radius)
                val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)

                val haloColors = listOf(
                    Color.White.copy(alpha = pulseGlow * 0.22f),
                    Color.White.copy(alpha = pulseGlow * 0.12f),
                    Color.White.copy(alpha = pulseGlow * 0.04f),
                    Color.White.copy(alpha = pulseGlow * 0.18f),
                    Color.White.copy(alpha = pulseGlow * 0.22f)
                )

                val arcGradient = listOf(
                    Color(0xFFFFFFFF),
                    Color(0xDDFFFFFF),
                    Color(0x88FFFFFF),
                    Color(0xCCFFFFFF),
                    Color(0xFFFFFFFF)
                )

                // Rotated smoothly to -90f so the sweep starts at the top
                rotate(degrees = -90f, pivot = centerOffset) {
                    // Soft outer white glow bleeding along progress arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = haloColors,
                            center = centerOffset
                        ),
                        startAngle = 0f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth * 1.8f, cap = StrokeCap.Round)
                    )

                    // Crisp minimal white gradient progress ring
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = arcGradient,
                            center = centerOffset
                        ),
                        startAngle = 0f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Minimal luminous pearl indicator dot at leading edge
                val angleRad = Math.toRadians((-90.0 + sweepAngle))
                val dotX = centerOffset.x + (radius * cos(angleRad)).toFloat()
                val dotY = centerOffset.y + (radius * sin(angleRad)).toFloat()
                val dotCenter = Offset(dotX, dotY)

                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = 8.dp.toPx(),
                    center = dotCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = dotCenter
                )
            }
        }

        // Dial Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Tag Glass Dropdown Chip (Obsidian Glass style)
            Box(
                modifier = Modifier
                    .offset(y = 2.dp)
                    .clip(CircleShape)
                    .background(Color(0x35FFFFFF))
                    .border(
                        width = 1.dp,
                        color = Color(0x38FFFFFF),
                        shape = CircleShape
                    )
                    .clickable(enabled = !isRunning) { onTagClick() }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeTag,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = AppleLinearFontFamily,
                            color = Color.White.copy(alpha = 0.95f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            letterSpacing = 0.3.sp
                        )
                    )
                    if (!isRunning) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch tag",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Giant Bold Apple/Linear Countdown Text with Minimal White Shadow
            Text(
                text = timeFormatted,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = AppleLinearFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isRunning) 40.sp else 48.sp,
                    lineHeight = if (isRunning) 44.sp else 54.sp,
                    color = Color.White,
                    letterSpacing = (-0.5).sp,
                    shadow = Shadow(
                        color = Color.White.copy(alpha = 0.25f),
                        blurRadius = 8f
                    )
                ),
                modifier = Modifier
                    .padding(top = 2.dp)
            )

            if (!isRunning) {
                Spacer(modifier = Modifier.height(10.dp))

                // 3. "Take a break" Frosted Glass Button
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 4.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.3f),
                            spotColor = Color.White.copy(alpha = 0.15f)
                        )
                        .clip(CircleShape)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x28FFFFFF),
                                    Color(0x14FFFFFF)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0x35FFFFFF),
                            shape = CircleShape
                        )
                        .clickable { onTakeBreakClick() }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Take a break",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = AppleLinearFontFamily,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp
                        )
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))

                // Animated mascot video playing on seamless loop during active session (perfectly framed with full cap and table visible)
                RegainMascotView(
                    width = 145.dp,
                    height = 125.dp,
                    pose = MascotPose.STUDYING,
                    modifier = Modifier
                        .clipToBounds()
                        .clickable { onMascotClick() }
                )
            }
        }
    }
}
