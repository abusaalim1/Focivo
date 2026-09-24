package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.delay

/**
 * Persistent Mascot Persona Component ("Civo")
 * Renders Civo's circular headshot avatar next to a speech bubble with animated typewriter text.
 */
@Composable
fun CivoChatBubble(
    text: String,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    avatarSize: Dp = 54.dp,
    typingSpeedMs: Long = 35L,
    onTypingFinished: (() -> Unit)? = null
) {
    val darkTheme = isAppInDarkTheme()

    var displayedText by remember(text) { mutableStateOf("") }
    var isTypingFinished by remember(text) { mutableStateOf(false) }

    // Typewriter animation coroutine
    LaunchedEffect(text) {
        displayedText = ""
        isTypingFinished = false
        if (text.isEmpty()) {
            isTypingFinished = true
            onTypingFinished?.invoke()
            return@LaunchedEffect
        }
        for (i in 1..text.length) {
            displayedText = text.substring(0, i)
            delay(typingSpeedMs)
        }
        isTypingFinished = true
        onTypingFinished?.invoke()
    }

    // Blinking cursor state
    val infiniteTransition = rememberInfiniteTransition(label = "civo_cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_blink"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Civo Circular Headshot Avatar with Soft Ambient Halo
        Box(contentAlignment = Alignment.Center) {
            // Ambient outer glow
            Box(
                modifier = Modifier
                    .size(avatarSize + 6.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        spotColor = RegainLimePrimary.copy(alpha = 0.5f),
                        ambientColor = RegainLimePrimary.copy(alpha = 0.25f)
                    )
                    .background(
                        color = if (darkTheme) RegainLimePrimary.copy(alpha = 0.18f) else Color(0x33A3E635),
                        shape = CircleShape
                    )
            )

            // Inner circular cropped image container
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(if (darkTheme) Color(0xFF142418) else Color(0xFFEBF7EE))
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                RegainLimePrimary,
                                Color(0xFF10B981)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mascot_avatar_circle),
                    contentDescription = "Civo Mascot Persona",
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                )
            }
        }

        // Civo Speech Bubble Card
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
                    spotColor = if (darkTheme) RegainLimePrimary.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.08f),
                    ambientColor = Color.Black.copy(alpha = 0.04f)
                )
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                .background(
                    if (darkTheme) Color(0xF0121D15) else Color(0xFAFFFFFF)
                )
                .border(
                    width = 1.dp,
                    color = if (darkTheme) Color(0x30FFFFFF) else Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
                )
                .clickable {
                    // Tap to skip typewriter animation
                    if (!isTypingFinished) {
                        displayedText = text
                        isTypingFinished = true
                        onTypingFinished?.invoke()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                // Name Tag / Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(RegainLimePrimary)
                    )
                    Text(
                        text = "Civo",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (darkTheme) RegainLimePrimary else Color(0xFF15803D),
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Typewriter Main Text
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayedText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    if (!isTypingFinished) {
                        Text(
                            text = " ▌",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = RegainLimePrimary
                            ),
                            modifier = Modifier.alpha(cursorAlpha)
                        )
                    }
                }

                // Subtitle / Hint text below main text (revealed after typing)
                if (!subtext.isNullOrBlank() && isTypingFinished) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtext,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    )
                }
            }
        }
    }
}
