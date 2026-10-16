package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoTokens
import com.example.util.MascotPose
import kotlinx.coroutines.delay

/**
 * v1.2.3 (Task 13.1) — the 10-minute strict lock-in warning, shown as a
 * SYSTEM overlay (a11y layer or app overlay) fired from the alarm path, so
 * it appears even when Focivo is not open.
 *
 * Unified design system: tokens (bg #0A0A0B / #F7F7F5, accent #B6F13A),
 * Poppins body + Instrument Serif italic display accent, lime accent, Buddy
 * mascot with idle animation, generous spacing, gentle fade+scale card
 * entrance and a live mm:ss countdown. Zero emojis, English-only.
 */
@Composable
fun StrictWarningOverlay(
    subjectLabel: String,
    startMs: Long,
    onDismiss: () -> Unit
) {
    val T = focivoTokens()

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    var remainingMs by remember(startMs) {
        mutableLongStateOf((startMs - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    LaunchedEffect(startMs) {
        while (remainingMs > 0L) {
            delay(1000L)
            remainingMs = (startMs - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        onDismiss()
    }
    val minutes = (remainingMs / 60_000L).toInt()
    val seconds = ((remainingMs % 60_000L) / 1000L).toInt()
    val countdownText = "%02d:%02d".format(minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.scrim),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(animationSpec = tween(420, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(T.surface)
                    .padding(horizontal = 26.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Eyebrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RegainLimePrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STRICT STUDY LOCK-IN",
                        style = focivoEyebrow()
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                BuddyMascot(
                    modifier = Modifier.size(104.dp),
                    pose = MascotPose.STUDY,
                    idleAnimation = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Strict study starts in 10 minutes.",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = SerifItalicFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal,
                        color = T.text,
                        fontSize = 27.sp,
                        lineHeight = 33.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Your $subjectLabel session is about to begin. " +
                        "This schedule is now locked — it can no longer be " +
                        "turned off or cancelled before it starts.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = T.muted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Live countdown
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = T.accentText,
                        fontSize = 44.sp,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "until your session begins",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = T.muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Lock note
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(T.softAccent)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = T.accentText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Schedule toggle locked until the session starts",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = T.accentText,
                            fontSize = 12.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegainLimePrimary,
                        contentColor = NearBlack
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Got it — I'll be ready",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}
