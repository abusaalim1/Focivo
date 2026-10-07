package com.example.ui.components

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.service.FocusShieldService
import com.example.service.ScheduledBlockScheduler
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimePrimary
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Aesthetic in-app overlay Composable displayed inside blocked applications.
 * Replaces hard redirection to Focusly, presenting the mascot, lock status,
 * and exact countdown of remaining lock time over a frosted glass backdrop.
 */
@Composable
fun BlockedAppOverlay(
    appName: String,
    blockedPackage: String,
    initialDurationSec: Int = 0,
    reason: String? = null,
    isPunishment: Boolean = false,
    onExitApp: () -> Unit,
    onOpenFocusly: () -> Unit
) {
    val context = LocalContext.current

    // Live remaining timer state
    var remainingSeconds by remember(initialDurationSec) {
        val calculated = if (initialDurationSec > 0) {
            initialDurationSec
        } else {
            val shieldRem = FocusShieldService.getRemainingSeconds(context)
            if (shieldRem > 0) {
                shieldRem
            } else if (ScheduledBlockScheduler.isScheduleCurrentlyActive(context)) {
                ScheduledBlockScheduler.getSecondsUntilNextStateChange(context)
            } else {
                25 * 60
            }
        }
        mutableIntStateOf(calculated)
    }

    // Countdown ticker
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
        }
    }

    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60

    val timeFormatted = if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    val accentColor = if (isPunishment) Color(0xFFFF4D4D) else RegainLimePrimary
    val glowColor = if (isPunishment) Color(0xFFFF5252).copy(alpha = 0.3f) else RegainLimePrimary.copy(alpha = 0.25f)

    // Pulsing lock animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Full screen solid dark backdrop completely obscuring the blocked application
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A08)) // Solid Deep AMOLED Background
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating glassmorphism card with glowing border
        Box(
            modifier = Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF121B14),
                            Color(0xFF0C130E),
                            Color(0xFF070B08)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.55f),
                            Color(0x25FFFFFF),
                            accentColor.copy(alpha = 0.15f)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .shadow(elevation = 28.dp, shape = RoundedCornerShape(32.dp), spotColor = accentColor)
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Top Mascot section (enlarged & prominent as shown in the reference image)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(152.dp)
                ) {
                    // Glow radial background
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(glowColor, Color.Transparent)
                                )
                            )
                    )

                    // Mascot Image (Clear, well-proportioned size)
                    Image(
                        painter = painterResource(id = R.drawable.mascot_blocked),
                        contentDescription = "Focus Mascot Locked",
                        modifier = Modifier
                            .size(136.dp)
                            .scale(pulseScale),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Centered Circular Lock Badge
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .border(1.5.dp, Color(0xFF0F1710), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = NearBlack,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // App Blocked Pill Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isPunishment) Icons.Default.Shield else Icons.Default.Lock,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isPunishment) "DISCIPLINE LOCK ACTIVE" else "APP BLOCKED",
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Blocked App Headline
                Text(
                    text = "$appName is Locked",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 22.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle / Reason strictly inside box
                Text(
                    text = reason ?: "$appName is locked during your active focus session.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFA5B4A3),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Countdown Timer Box (Inner Card)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF131C15))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = Color(0xFF7E8F7F),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "LOCK TIME REMAINING",
                                color = Color(0xFF7E8F7F),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = timeFormatted,
                            color = accentColor,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )

                        Text(
                            text = if (remainingSeconds > 0) "Unlocks automatically when focus ends" else "Session finished!",
                            color = Color(0xFF90A191),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Exit Button: Quits blocked application & goes back safely without opening Focusly
                Button(
                    onClick = onExitApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = NearBlack
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = NearBlack
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Exit App & Resume Study",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = NearBlack
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Focusly Button
                OutlinedButton(
                    onClick = onOpenFocusly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF131C15),
                        contentColor = Color(0xFFF1F5F9)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0x35FFFFFF), Color(0x18FFFFFF))
                        )
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelfImprovement,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = accentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Focivo App",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF1F5F9)
                        )
                    }
                }
            }
        }
    }
}
