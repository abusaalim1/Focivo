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

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shadow

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

    val isDark = isSystemInDarkTheme()
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
            val strokeWidth = 12.dp.toPx()
            val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.width - 36.dp.toPx()) / 2f

            // 1. Ambient outer background radial aura (White + Lime)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = pulseGlow * 0.22f),
                        RegainLimePrimary.copy(alpha = pulseGlow * 0.28f),
                        RegainLimeLight.copy(alpha = pulseGlow * 0.12f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = radius + 36.dp.toPx()
                ),
                radius = radius + 36.dp.toPx(),
                center = centerOffset
            )

            // 2. Dial track background (Sleek translucent white glass track gradient)
            drawCircle(
                brush = Brush.sweepGradient(
                    0.0f to Color(0x3BFFFFFF),
                    0.3f to Color(0x1AFFFFFF),
                    0.7f to Color(0x38FFFFFF),
                    1.0f to Color(0x3BFFFFFF),
                    center = centerOffset
                ),
                radius = radius,
                center = centerOffset,
                style = Stroke(width = strokeWidth)
            )

            // 3. Glowing premium white gradient progress arc & soft bleeding halo
            val sweepAngle = 360f * progress
            if (sweepAngle > 0f) {
                val arcTopLeft = Offset(centerOffset.x - radius, centerOffset.y - radius)
                val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)

                // Soft outer blurred halo bleeding outward along progress arc
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.White.copy(alpha = pulseGlow * 0.55f),
                        0.5f to RegainLimeLight.copy(alpha = pulseGlow * 0.40f),
                        1.0f to Color.White.copy(alpha = pulseGlow * 0.25f),
                        center = centerOffset
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth * 2.2f, cap = StrokeCap.Round)
                )

                // Crisp, premium gradient progress ring (Brilliant white blending into ice white and glowing lime)
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.White,
                        0.35f to Color(0xFFF4FFE0),
                        0.70f to RegainLimeLight,
                        1.0f to RegainLimePrimary,
                        center = centerOffset
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Refined multi-layered glowing indicator dot at leading edge
                val angleRad = Math.toRadians((-90.0 + sweepAngle))
                val dotX = centerOffset.x + (radius * cos(angleRad)).toFloat()
                val dotY = centerOffset.y + (radius * sin(angleRad)).toFloat()
                val dotCenter = Offset(dotX, dotY)

                drawCircle(
                    color = Color.White.copy(alpha = 0.65f),
                    radius = 12.dp.toPx(),
                    center = dotCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = dotCenter
                )
                drawCircle(
                    color = RegainLimePrimary,
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
            // 1. "Deep Work Session" / Tag Glass Dropdown Chip
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = RegainLimePrimary.copy(alpha = 0.25f),
                        spotColor = RegainLimePrimary.copy(alpha = 0.35f)
                    )
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x388CFF00),
                                Color(0x228CFF00),
                                Color(0x2B141518)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xA68CFF00),
                                Color(0x388CFF00)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable { onTagClick() }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing green status dot with outer aura
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(RegainLimePrimary.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(RegainLimeLight)
                        )
                    }
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = activeTag,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            color = RegainLimeLight,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 0.3.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Switch tag",
                        tint = RegainLimeLight,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Giant Poppins Bold "25:00" Countdown Text with Soft Glow Shadow
            Text(
                text = timeFormatted,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 50.sp,
                    lineHeight = 56.sp,
                    color = textPrimary,
                    letterSpacing = 1.2.sp,
                    shadow = Shadow(
                        color = RegainLimePrimary.copy(alpha = 0.35f),
                        blurRadius = 14f
                    )
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. "Take a break" Frosted Glass Button
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = RegainLimePrimary.copy(alpha = 0.25f)
                    )
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (isDark) Color(0x28FFFFFF) else Color(0xF2FFFFFF),
                                if (isDark) Color(0x1A141518) else Color(0xE8F0F4EC)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x4DFFFFFF),
                                Color(0x2B8CFF00)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable { onTakeBreakClick() }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Take a break",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = if (isDark) Color.White.copy(alpha = 0.92f) else NearBlack,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 4. Mascot Video Embedded inside Timer Circle Box (Hands touching bottom circle arc)
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(110.dp)
                    .offset(y = 10.dp)
                    .clipToBounds()
                    .clickable { onMascotClick() },
                contentAlignment = Alignment.TopCenter
            ) {
                RegainMascotView(
                    size = 195.dp,
                    pose = mascotPose,
                    modifier = Modifier.offset(y = 2.dp)
                )
            }
        }
    }
}
