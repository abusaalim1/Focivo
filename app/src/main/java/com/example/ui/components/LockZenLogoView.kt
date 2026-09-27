package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Transparent background with subtle radiant neon lime aura so the logo is clean and prominent.
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
                            RegainLimePrimary.copy(alpha = if (isDark) 0.35f else 0.25f),
                            RegainLimeLight.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "Focivo Logo",
            modifier = Modifier.size(size)
        )
    }
}

