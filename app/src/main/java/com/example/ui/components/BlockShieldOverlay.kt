package com.example.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RegainAuroraGreen
import com.example.ui.theme.RegainNeonEmerald

@Composable
fun BlockShieldOverlay(
    blockedAppName: String,
    remainingSeconds: Int,
    onReturnToFocus: () -> Unit,
    onEmergencyBypass: () -> Unit,
    modifier: Modifier = Modifier,
    isPunishment: Boolean = false,
    reason: String? = null,
    isGeminiDetected: Boolean = false
) {
    // Prevent bypass via Android hardware / gesture back button
    BackHandler(enabled = true) {
        onReturnToFocus()
    }

    val transition = rememberInfiniteTransition(label = "pulse_block")
    val pulseScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = if (hours > 0) {
        String.format("%dh %02dm %02ds", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    val effectivePunishment = isPunishment || remainingSeconds > 3600

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume clicks to prevent background leakage */ }
            .testTag("block_shield_overlay"),
        contentAlignment = Alignment.Center
    ) {
        // Aurora starry backdrop
        AuroraBackground {
            // Floating Decorative App Icons
            Box(modifier = Modifier.fillMaxSize()) {
                FloatingDistractionBadge(
                    text = "👻",
                    bgColor = Color(0xFFFFFC00),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 36.dp, top = 90.dp)
                )

                FloatingDistractionBadge(
                    text = "▶",
                    bgColor = Color(0xFFFF0000),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 40.dp, top = 110.dp)
                )

                FloatingDistractionBadge(
                    text = "📸",
                    bgColor = Color(0xFFE1306C),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 44.dp, bottom = 140.dp)
                )

                FloatingDistractionBadge(
                    text = "💬",
                    bgColor = Color(0xFF25D366),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 44.dp, bottom = 130.dp)
                )
            }

            // Central Mascot & Lock Intercept Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Character Mascot with Stop Sign & Pulsing Aura
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(130.dp)
                        .scale(pulseScale)
                ) {
                    // Outer warning glow
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (isGeminiDetected) Color(0x6080CBC4) else Color(0x60E53935),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Inner mascot circle
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2836))
                            .border(
                                2.dp,
                                if (isGeminiDetected) RegainNeonEmerald else Color(0xFFFF5252),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        RegainMascotView(
                            pose = MascotPose.STOP_SIGN,
                            size = 72.dp
                        )
                    }

                    // Floating Mini Shield
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isGeminiDetected) Color(0xFF004D40) else Color(0xFFB71C1C))
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGeminiDetected) Icons.Default.AutoAwesome else Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isGeminiDetected) Color(0x3500E676)
                            else if (effectivePunishment) Color(0x45E53935)
                            else Color(0x35E53935)
                        )
                        .border(
                            1.dp,
                            if (isGeminiDetected) RegainNeonEmerald.copy(alpha = 0.8f) else Color(0x80E53935),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isGeminiDetected) RegainNeonEmerald else Color(0xFFFF5252))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isGeminiDetected) "✨ GEMINI AI REAL-TIME SCREEN GUARD"
                            else if (effectivePunishment) "⚠️ 6-HOUR AI DISCIPLINE LOCK"
                            else "STRICT STUDY LOCK ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isGeminiDetected) RegainNeonEmerald else Color(0xFFFF8A80),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bold Title: "{App Name} is Blocked"
                Text(
                    text = "$blockedAppName is Blocked",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 22.sp,
                        lineHeight = 28.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle / Gemini AI Reason
                if (!reason.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x25004D40))
                            .border(1.dp, RegainNeonEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = RegainNeonEmerald,
                                modifier = Modifier
                                    .size(15.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFE0F2F1),
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (effectivePunishment) {
                            "AI detected non-study distraction during your study routine. Distracting apps are locked so you can complete your syllabus."
                        } else {
                            "Stay focused on your student study goals.\nApps unlock automatically when study timer ends."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFB0BEC5),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Remaining time countdown pill
                if (remainingSeconds > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x301B3B24))
                            .border(1.dp, RegainNeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "REMAINING STUDY TIME",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = RegainNeonEmerald,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 9.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timeFormatted,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 24.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Student Safe Pass Notice
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x20FFFFFF))
                        .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = RegainNeonEmerald,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Incoming phone calls are never blocked",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RegainNeonEmerald,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Academic Sentinel · Educational Lectures & Study Tools Allowed",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Return to Study Action Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(28.dp),
                            spotColor = RegainNeonEmerald.copy(alpha = 0.5f)
                        )
                ) {
                    Button(
                        onClick = onReturnToFocus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("return_to_focus_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RegainNeonEmerald,
                            contentColor = Color(0xFF0A1F14)
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(
                            text = "Return to Study",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }
                }

                // Emergency Bypass Button (only if not punishment lock)
                if (!effectivePunishment) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onEmergencyBypass,
                        modifier = Modifier.testTag("emergency_bypass_button")
                    ) {
                        Text(
                            text = "Dismiss Shield",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF90A4AE),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingDistractionBadge(
    text: String,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = Color.White
        )
    }
}

@Composable
private fun AuroraBackground(
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_bg")
    val shiftX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift_x"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1B1E))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Deep space radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1B4931).copy(alpha = 0.45f),
                        Color(0xFF0F2B22).copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(width * (0.3f + 0.4f * shiftX), height * 0.35f),
                    radius = width * 0.85f
                )
            )

            // Accent emerald aurora wave
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E676).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(width * (0.7f - 0.4f * shiftX), height * 0.7f),
                    radius = width * 0.7f
                )
            )
        }

        content()
    }
}
