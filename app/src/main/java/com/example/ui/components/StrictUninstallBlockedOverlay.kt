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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoTokens

/**
 * v1.2.3 (owner 2026-10-16) — premium full-screen overlay shown when an
 * uninstall is attempted while Strict Mode blocks it (Regain-style).
 *
 * Replaces the static [StrictUninstallBlockedDialog]: the user's OWN
 * equipped mascot ([BuddyMascot] with idle breathing, never bald — hood
 * cap is part of the art), a serif-italic display headline, staged
 * animated text (fade + slide reveals), a lime eyebrow pill, a live
 * "focus lock ends in mm:ss" line, and a pulsing lime CTA.
 *
 * Shows ONLY when strict blocks uninstall (Strict ON + protection toggle
 * ON + genuine running session). Non-dismissible by back/outside tap —
 * single CTA dismisses.
 */
@Composable
fun StrictUninstallBlockedOverlay(
    remainingSeconds: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    // Staged reveal: each line appears after the previous one.
    var stage by remember { mutableStateOf(0) }
    LaunchedEffect(entered) {
        if (!entered) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        stage = 1
        kotlinx.coroutines.delay(280)
        stage = 2
        kotlinx.coroutines.delay(280)
        stage = 3
        kotlinx.coroutines.delay(280)
        stage = 4
    }

    // CTA pulse glow.
    val pulse = rememberInfiniteTransition(label = "uninstall_cta_pulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cta_glow"
    )

    val mm = (remainingSeconds / 60).coerceAtLeast(0)
    val ss = (remainingSeconds % 60).coerceAtLeast(0)
    val countdown = "%02d:%02d".format(mm, ss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(T.scrim),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(animationSpec = tween(380, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.94f,
                    animationSpec = tween(380, easing = FastOutSlowInEasing)
                ),
            exit = fadeOut(animationSpec = tween(200)) +
                scaleIn(initialScale = 0.96f)
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
                // Eyebrow pill
                AnimatedVisibility(
                    visible = stage >= 1,
                    enter = slideInVertically(
                        initialOffsetY = { 18 },
                        animationSpec = tween(380, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(300))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(RegainLimePrimary.copy(alpha = 0.16f))
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(RegainLimePrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "STRICT MODE IS ON", style = focivoEyebrow())
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // The user's own mascot, breathing gently.
                AnimatedVisibility(
                    visible = stage >= 1,
                    enter = fadeIn(animationSpec = tween(420))
                ) {
                    BuddyMascot(
                        modifier = Modifier.size(112.dp),
                        pose = MascotPose.STUDY,
                        idleAnimation = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Headline — serif italic display accent.
                AnimatedVisibility(
                    visible = stage >= 2,
                    enter = slideInVertically(
                        initialOffsetY = { 22 },
                        animationSpec = tween(420, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(320))
                ) {
                    Text(
                        text = "I can't let you uninstall me right now.",
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
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Body.
                AnimatedVisibility(
                    visible = stage >= 3,
                    enter = slideInVertically(
                        initialOffsetY = { 22 },
                        animationSpec = tween(420, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(320)),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Text(
                        text = "Strict Mode is protecting your focus lock. " +
                            "Finish your session first — then you can turn " +
                            "Strict Mode off in Settings and uninstall.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = T.muted,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Live countdown.
                AnimatedVisibility(
                    visible = stage >= 3,
                    enter = fadeIn(animationSpec = tween(320))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = countdown,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = T.accentText,
                                fontSize = 42.sp,
                                letterSpacing = 1.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "until your focus lock ends",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = T.muted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // CTA.
                AnimatedVisibility(
                    visible = stage >= 4,
                    enter = slideInVertically(
                        initialOffsetY = { 22 },
                        animationSpec = tween(420, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(320))
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                RegainLimePrimary.copy(alpha = glowAlpha)
                            )
                            .pressFeedback()
                            .clip(RoundedCornerShape(18.dp))
                            .background(RegainLimePrimary)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 34.dp, vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Got it — back to focus",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = T.onAccent,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
