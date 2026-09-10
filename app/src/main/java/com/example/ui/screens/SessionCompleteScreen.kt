package com.example.ui.screens

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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusSessionEntity
import com.example.ui.components.GlassButton
import com.example.ui.components.MascotPose
import com.example.ui.components.RegainMascotView
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.VioletAccent
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SessionCompleteScreen(
    session: FocusSessionEntity,
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val minutesSpent = session.durationSeconds / 60

    // Particle / Light Orbit Animation
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xF20D0E11) else Color(0xEEF7F7F4)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Completed Circular Transformation with Particle Ring
            Box(
                modifier = Modifier.size(170.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(170.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = (size.width - 24.dp.toPx()) / 2f

                    // Halo Ring
                    drawCircle(
                        color = VioletAccent.copy(alpha = 0.2f),
                        radius = radius * pulseScale,
                        center = center,
                        style = Stroke(width = 12.dp.toPx())
                    )

                    // Complete Solid Circular Ring
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(VioletAccent, LavenderSoft, VioletAccent),
                            center = center
                        ),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Floating Particle Nodes
                    for (i in 0..4) {
                        val angle = Math.toRadians((orbitAngle + i * 72).toDouble())
                        val px = center.x + (radius + 14.dp.toPx()) * cos(angle).toFloat()
                        val py = center.y + (radius + 14.dp.toPx()) * sin(angle).toFloat()
                        drawCircle(
                            color = VioletAccent.copy(alpha = 0.6f),
                            radius = (2 + (i % 3)).dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                // Center Celebrating Mascot
                RegainMascotView(
                    pose = MascotPose.CELEBRATING,
                    size = 110.dp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Well done.",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 38.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$minutesSpent minutes of focused work.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Points & Distractions Glass Capsule
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = VioletAccent.copy(alpha = 0.2f),
                        spotColor = VioletAccent.copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isDark) Color(0xFF1B1E28) else Color.White)
                    .border(
                        1.dp,
                        if (isDark) Color(0x33FFFFFF) else Color(0x18000000),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = VioletAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+${session.focusPointsEarned} Focus Points",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = VioletAccent,
                                fontSize = 15.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 20.dp)
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0x18000000))
                    )

                    Text(
                        text = if (session.distractionsCount == 0) "0 Distractions" else "${session.distractionsCount} Distractions",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            GlassButton(
                text = "Continue",
                onClick = onDismiss,
                isPrimary = true,
                modifier = Modifier.fillMaxWidth(0.7f)
            )
        }
    }
}
