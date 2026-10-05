package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppleLinearFontFamily
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.RegainNeonEmerald
import com.example.ui.theme.isAppInDarkTheme

@Composable
fun DeepFocusToggleCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isSessionActive: Boolean = false
) {
    val isDark = isAppInDarkTheme()
    var showInfoDialog by remember { mutableStateOf(false) }

    val cardBg = if (isDark) {
        if (isEnabled) Color(0xEB162419) else Color(0xF21C1C1E)
    } else {
        if (isEnabled) Color(0xFFF2FBE9) else Color(0xFFFFFFFF)
    }

    val cardBorder = if (isEnabled) {
        RegainLimePrimary.copy(alpha = 0.75f)
    } else {
        if (isDark) Color(0x22FFFFFF) else Color(0x12000000)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isEnabled) 5.dp else 3.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = if (isEnabled) Color(0x258CE000) else (if (isDark) Color(0x15000000) else Color(0x08000000)),
                spotColor = if (isEnabled) Color(0x308CE000) else (if (isDark) Color(0x30000000) else Color(0x0E000000))
            )
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(0.8.dp, cardBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("deep_focus_toggle_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Glowing Icon Container
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEnabled) RegainLimePrimary.copy(alpha = 0.2f)
                                else if (isDark) Color(0xFF2C3238)
                                else Color(0xFFF3F4F6)
                            )
                            .border(
                                1.dp,
                                if (isEnabled) RegainLimePrimary else Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isEnabled) Icons.Default.Lock else Icons.Default.Bolt,
                            contentDescription = "Deep Focus Lock",
                            tint = if (isEnabled) (if (isDark) RegainNeonEmerald else Color(0xFF2E7D32)) else Color(0xFF9CA3AF),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Deep Focus",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = AppleLinearFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isDark) Color.White else NearBlack
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Status Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isEnabled) RegainLimeContainer else (if (isDark) Color(0xFF263238) else Color(0xFFE5E7EB))
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isEnabled) "TASK LOCK" else "OPTIONAL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = AppleLinearFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = if (isEnabled) RegainLimeDeepText else (if (isDark) Color(0xFFB0BEC5) else Color(0xFF6B7280))
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (isEnabled) {
                                "Locks Focivo to screen · Prevents app switching & minimizing"
                            } else {
                                "Prevent minimizing or switching to other apps during study"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = AppleLinearFontFamily,
                                fontSize = 11.5.sp,
                                color = if (isDark) Color(0xFFB0BEC5) else Color(0xFF6B7280),
                                lineHeight = 15.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("deep_focus_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Deep Focus Info",
                            tint = if (isDark) Color(0xFF90A4AE) else Color(0xFF9E9E9E),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { onToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NearBlack,
                            checkedTrackColor = RegainLimePrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB)
                        ),
                        modifier = Modifier.testTag("deep_focus_switch")
                    )
                }
            }

            // Interactive helper note when enabled
            AnimatedVisibility(
                visible = isEnabled,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0x2500E676) else Color(0x208CE000))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isDark) RegainNeonEmerald else Color(0xFF2E7D32),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Screen Pinning + Task Lock will engage automatically when you tap Start Study.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = if (isDark) Color(0xFFE0F2F1) else Color(0xFF1B5E20),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        DeepFocusInfoDialog(onDismiss = { showInfoDialog = false })
    }
}

@Composable
fun DeepFocusActiveBanner(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulse_deep_focus")
    val pulseScale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(pulseScale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF0F3D24),
                        Color(0xFF1A5A35),
                        Color(0xFF0F3D24)
                    )
                )
            )
            .border(1.dp, RegainNeonEmerald.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag("deep_focus_active_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(RegainNeonEmerald)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = RegainNeonEmerald,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "DEEP FOCUS LOCKED · NO APP SWITCHING",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
fun DeepFocusInfoDialog(
    onDismiss: () -> Unit
) {
    val isDark = isAppInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = if (isDark) Color(0xFF1E293B) else Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(RegainLimeContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = RegainLimeDeepText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "How Deep Focus Works",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = if (isDark) Color.White else NearBlack
                    )
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Deep Focus turns Focivo into an unbreakable study station by combining two layers of Android hardware and software protection:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF4B5563),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                )

                // Point 1: Screen Pinning
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Android Screen Pinning",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else NearBlack,
                                fontSize = 13.sp
                            )
                        )
                        Text(
                            text = "Pins the Focivo screen to the front so accidental gestures or home swipes cannot exit the study timer.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280),
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        )
                    }
                }

                // Point 2: Accessibility Task Locking
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(RegainLimeContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = RegainLimeDeepText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Real-Time Task Locking",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else NearBlack,
                                fontSize = 13.sp
                            )
                        )
                        Text(
                            text = "If any other application, social feed, or launcher attempts to open, Focivo instantly dismisses it and returns to your timer.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280),
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        )
                    }
                }

                // Point 3: Safe Pass
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Emergency & Phone Calls Allowed",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else NearBlack,
                                fontSize = 13.sp
                            )
                        )
                        Text(
                            text = "Incoming phone calls from parents or family are never blocked. Deep Focus automatically unlocks when the session ends.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280),
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RegainLimePrimary,
                    contentColor = NearBlack
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("deep_focus_info_ok_button")
            ) {
                Text(
                    text = "Got it",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    )
}
