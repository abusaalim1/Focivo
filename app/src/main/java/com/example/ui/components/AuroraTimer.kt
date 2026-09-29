package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aurora Timer UI Component
 *
 * Visualizes a soothing, atmospheric countdown timer featuring:
 * - Living atmospheric Aurora Borealis gradient drift (emerald, mint, deep oceanic cyan, soft twilight indigo)
 * - iOS-inspired minimal frosted glass backdrop with subtle light refraction
 * - Perfectly balanced, silky-smooth progress line without harsh white hotspots or glaring artifacts
 * - Ambient rhythmic breathing halo and elegant head indicator
 */
@Composable
fun AuroraTimer(
    totalSeconds: Int,
    remainingSeconds: Int,
    isRunning: Boolean,
    isBreak: Boolean = false,
    subjectTitle: String = "Deep Study",
    modifier: Modifier = Modifier,
    dialSize: Dp = 290.dp,
    onTogglePlay: () -> Unit = {},
    onReset: () -> Unit = {},
    onTagClick: (() -> Unit)? = null,
    onSwitchMode: (() -> Unit)? = null
) {
    val isDark = isAppInDarkTheme()
    val progress = if (totalSeconds > 0) {
        ((totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Format remaining time MM:SS or HH:MM:SS
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val hours = minutes / 60
    val timeFormatted = if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    // Infinite transitions for atmospheric organic aurora shifts
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_cycle")

    // Slow orbital rotation for aurora light wave (12 seconds cycle)
    val auroraRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_rotation"
    )

    // Breathing pulse for gentle ambient halo
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_pulse"
    )

    // Smooth subtle shimmer phase
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_phase"
    )

    // Harmonious Aurora Palettes (balanced, no blinding white)
    val auroraColors = if (isDark) {
        listOf(
            Color(0xFF8CE000), // Vibrant Regain Lime
            Color(0xFF00E5FF), // Ethereal Cyan
            Color(0xFF69F0AE), // Mint Green
            Color(0xFF7C4DFF), // Soft Twilight Violet
            Color(0xFF00B0FF), // Ocean Azure
            Color(0xFF8CE000)  // Seamless loop to Lime
        )
    } else {
        listOf(
            Color(0xFF6BB800), // Deep energetic lime
            Color(0xFF00B4D8), // Vibrant clean cyan
            Color(0xFF2EC4B6), // Mint teal
            Color(0xFF5E60CE), // Soft royal purple
            Color(0xFF6BB800)  // Loop
        )
    }

    // Glass backdrop styling (iOS-inspired minimal frosted glass)
    val glassBg = if (isDark) Color(0xD0171F19) else Color(0xF2FFFFFF)
    val glassBorderTop = if (isDark) Color(0x40FFFFFF) else Color(0x90FFFFFF)
    val glassBorderBottom = if (isDark) Color(0x158CE000) else Color(0x308CE000)
    val textPrimary = if (isDark) Color(0xFFF2F5EE) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA2AAA0) else Color(0xFF5A6258)

    Box(
        modifier = modifier
            .size(dialSize)
            .shadow(
                elevation = if (isDark) 12.dp else 8.dp,
                shape = CircleShape,
                ambientColor = if (isDark) Color(0x35000000) else Color(0x14000000),
                spotColor = if (isDark) Color(0x408CE000) else Color(0x188CE000)
            )
            .clip(CircleShape)
            .background(glassBg)
            // iOS Minimal Frosted Glass Top Refraction Line
            .drawBehind {
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(glassBorderTop, glassBorderBottom)
                    ),
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. Ambient Living Aurora Halo Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) - 18.dp.toPx()

            // Ethereal background radial mesh glow (breathing behind dial)
            if (isRunning) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            (if (isDark) Color(0x288CE000) else Color(0x1A8CE000)),
                            (if (isDark) Color(0x1500E5FF) else Color(0x1000E5FF)),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * breathingPulse
                    ),
                    center = center,
                    radius = radius * breathingPulse
                )
            }

            // 2. Track Ring (Subtle translucent glass groove)
            val trackColor = if (isDark) Color(0x20FFFFFF) else Color(0x18000000)
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = 8.dp.toPx())
            )

            // 3. Dynamic Aurora Progress Arc (Continuous sweep, rotated smoothly to 12 o'clock)
            val sweepAngle = 360f * progress
            if (sweepAngle > 0f) {
                val arcTopLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2f, radius * 2f)

                // Atmospheric halo diffusion behind the line (soft, non-glaring)
                rotate(degrees = -90f, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = auroraColors.map { it.copy(alpha = if (isRunning) 0.35f * shimmerPhase else 0.22f) },
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Crisp foreground progress arc (smooth aurora chromatic shift)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = auroraColors,
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 8.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 4. Glowing Pearl Indicator at the active arc tip (smooth traveling node)
                val headAngleRad = Math.toRadians((-90f + sweepAngle).toDouble())
                val headX = center.x + (radius * cos(headAngleRad)).toFloat()
                val headY = center.y + (radius * sin(headAngleRad)).toFloat()
                val headCenter = Offset(headX, headY)

                // Soft outer aura
                drawCircle(
                    color = (if (isDark) Color(0x558CE000) else Color(0x408CE000)),
                    radius = 9.dp.toPx(),
                    center = headCenter
                )
                // Crisp pearl core
                drawCircle(
                    color = if (isDark) Color(0xFFF4FFE0) else Color.White,
                    radius = 5.dp.toPx(),
                    center = headCenter
                )
            }
        }

        // 2. Central Timer Display & iOS Minimal Controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // Atmospheric Phase Pill (Deep Focus / Break / Sprint / Custom Subject)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isBreak) {
                            if (isDark) Color(0x3069F0AE) else Color(0xFFE8F5E9)
                        } else {
                            if (isDark) Color(0x308CE000) else RegainLimeContainer.copy(alpha = 0.5f)
                        }
                    )
                    .border(
                        1.dp,
                        if (isBreak) Color(0x4569F0AE) else Color(0x458CE000),
                        RoundedCornerShape(14.dp)
                    )
                    .then(
                        if (onTagClick != null && !isRunning) {
                            Modifier.clickable(onClick = onTagClick)
                        } else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRunning) {
                                    if (isBreak) Color(0xFF00E676) else RegainLimePrimary
                                } else {
                                    Color.Gray
                                }
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
                            if (isDark) Color(0xFF69F0AE) else Color(0xFF2E7D32)
                        } else {
                            if (isDark) RegainLimePrimary else RegainLimeDeepText
                        },
                        maxLines = 1
                    )
                    if (!isRunning && onTagClick != null && !isBreak) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change Subject & Timer",
                            tint = if (isDark) RegainLimePrimary else RegainLimeDeepText,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Digits: Soothing, Ultra-Crisp Countdown
            Text(
                text = timeFormatted,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = if (hours > 0) 36.sp else 46.sp,
                color = textPrimary,
                textAlign = TextAlign.Center,
                letterSpacing = (-1).sp,
                modifier = Modifier.testTag("aurora_timer_countdown_text")
            )

            // Progress percentage & phase cue
            Text(
                text = if (isRunning) "${(progress * 100).toInt()}% completed" else if (progress > 0f) "Paused" else "Ready to Focus",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = textSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // iOS-Style Glass Action Buttons (Play/Pause, Reset)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Reset Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0x25FFFFFF) else Color(0x14000000))
                        .clickable(onClick = onReset),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Timer",
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Primary Play / Pause Button with Aurora Glow
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            ambientColor = if (isDark) Color(0x408CE000) else Color(0x208CE000)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (isRunning) {
                                    listOf(RegainLimePrimary, Color(0xFF69F0AE))
                                } else {
                                    listOf(RegainLimeLight, RegainLimePrimary)
                                }
                            )
                        )
                        .clickable(onClick = onTogglePlay)
                        .testTag("aurora_timer_toggle_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Pause" else "Start",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
