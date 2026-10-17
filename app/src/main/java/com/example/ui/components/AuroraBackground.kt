package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.RegainBackgroundBrush

import com.example.ui.theme.isAppInDarkTheme

/**
 * Global background wrapper providing the signature Regain soft white-to-pale-lime gradient background
 * with a subtle radial halo glow behind top hero elements.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    // v1.3.2: never infer the mode from a color equality — the light tokens'
    // background (0xFFF7F7F5) is not pure white, so `bg != RegainBgTop` was
    // true in BOTH modes and the canvas drew dark even in light mode (dark
    // digits then rendered invisible on it). Use the theme's dark flag.
    val isDark = isAppInDarkTheme()

    val backgroundBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF111411),
                Color(0xFF161A15),
                Color(0xFF1B2019)
            )
        )
    } else {
        RegainBackgroundBrush
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // Subtle glowing radial halo near upper top (behind AI mascot/header)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x2B8CE000), // ~17% soft lime halo
                        Color(0x0E8CE000),
                        Color(0x008CE000)
                    ),
                    center = Offset(w * 0.5f, h * 0.18f),
                    radius = w * 0.75f
                )
            )
        }

        content()
    }
}
