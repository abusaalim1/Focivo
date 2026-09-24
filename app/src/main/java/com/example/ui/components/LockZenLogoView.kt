package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary

/**
 * LockZenLogoView - Displays the official Focivo brand logo icon.
 * Features an Apple-style frosted glass container with radiant neon lime aura,
 * and completely transparent, pristine icon display without any black background.
 */
@Composable
fun LockZenLogoView(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    showBackgroundSquircle: Boolean = true
) {
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Soft Radiant Lime Glow Aura behind the logo
        Box(
            modifier = Modifier
                .size(size * 1.15f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            RegainLimePrimary.copy(alpha = if (isDark) 0.32f else 0.22f),
                            RegainLimeLight.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        if (showBackgroundSquircle) {
            // Apple-style Frosted Glass Squircle (Adaptive, absolutely no black background)
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size * 0.26f))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color(0x28FFFFFF),
                                    Color(0x12FFFFFF),
                                    Color(0x0AFFFFFF)
                                )
                            } else {
                                listOf(
                                    Color(0x55FFFFFF),
                                    Color(0x35FFFFFF),
                                    Color(0x18FFFFFF)
                                )
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                RegainLimePrimary.copy(alpha = 0.55f),
                                RegainLimePrimary.copy(alpha = 0.25f),
                                Color(0x30FFFFFF)
                            )
                        ),
                        shape = RoundedCornerShape(size * 0.26f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.focivo_logo),
                    contentDescription = "Focivo Logo",
                    modifier = Modifier.size(size * 0.72f)
                )
            }
        } else {
            Image(
                painter = painterResource(id = R.drawable.focivo_logo),
                contentDescription = "Focivo Logo",
                modifier = Modifier.size(size)
            )
        }
    }
}

