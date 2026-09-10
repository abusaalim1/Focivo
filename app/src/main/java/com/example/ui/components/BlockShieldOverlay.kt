package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    isPunishment: Boolean = false
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
            // Floating Decorative App Icons (like Screenshot 3)
            Box(modifier = Modifier.fillMaxSize()) {
                // Floating top-left Snapchat-style yellow ghost badge
                FloatingDistractionBadge(
                    text = "👻",
                    bgColor = Color(0xFFFFFC00),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 36.dp, top = 90.dp)
                )

                // Floating top-right Reddit-style orange badge
                FloatingDistractionBadge(
                    text = "🤖",
                    bgColor = Color(0xFFFF4500),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 40.dp, top = 120.dp)
                )

                // Floating mid-left Facebook-style blue badge
                FloatingDistractionBadge(
                    text = "f",
                    bgColor = Color(0xFF1877F2),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp, top = 80.dp)
                )

                // Floating mid-right TikTok-style badge
                FloatingDistractionBadge(
                    text = "🎵",
                    bgColor = Color(0xFF111111),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 28.dp, bottom = 40.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Mascot waving "Stop!" next to App Block Icon
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .scale(pulseScale),
                    contentAlignment = Alignment.Center
                ) {
                    // Center Mascot with Stop Sign pose
                    RegainMascotView(
                        size = 160.dp,
                        pose = MascotPose.STOP_SIGN
                    )

                    // Floating Lock Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (effectivePunishment) Color(0x45E53935) else Color(0x35E53935))
                        .border(1.dp, Color(0x80E53935), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (effectivePunishment) "⚠️ 6-HOUR AI DISCIPLINE LOCK" else "STRICT STUDY LOCK ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFFF8A80),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bold Title: "{App Name} is Blocked"
                Text(
                    text = "$blockedAppName is Blocked",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 24.sp,
                        lineHeight = 30.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle
                Text(
                    text = if (effectivePunishment) {
                        "AI detected distraction during study routine. Distracting apps are locked for 6 hours so you can complete your syllabus."
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

                Spacer(modifier = Modifier.height(16.dp))

                // Remaining time countdown pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x301B3B24))
                        .border(1.dp, RegainNeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "REMAINING LOCK TIME",
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
                                fontSize = 26.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Student Safe Pass Notice (Calls & ChatGPT/Claude allowed)
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
                                text = "Incoming phone calls will never be blocked",
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
                                text = "Claude & ChatGPT allowed for educational doubts",
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
                            ambientColor = RegainAuroraGreen.copy(alpha = 0.3f),
                            spotColor = RegainAuroraGreen.copy(alpha = 0.5f)
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(RegainAuroraGreen, RegainNeonEmerald)
                            )
                        )
                        .clickable { onReturnToFocus() }
                        .padding(vertical = 14.dp)
                        .testTag("return_to_study_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = Color(0xFF04200C),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Return to Study",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF04200C),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Strict Lock Note
                Text(
                    text = "🔒 Cannot be bypassed or backed out until timer expires",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )
                )
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
            .size(46.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
