package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.util.TimerThemeManager

/**
 * v1.4.0-round2: full-screen theme background.
 *
 * Aurora (default) renders the existing code-drawn [AuroraBackground]
 * unchanged. Other themes draw their generated backdrop image with a dark
 * readability scrim. Static image = cheap; no heavy blur anywhere.
 */
@Composable
fun ThemedScreenBackground(modifier: Modifier = Modifier) {
    val theme by TimerThemeManager.selectedTheme.collectAsState()
    val backdropRes = theme.backdropRes
    if (backdropRes != null) {
        Box(modifier = modifier) {
            Image(
                painter = painterResource(id = backdropRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.6f),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xB3000000),
                                Color(0xE6000000),
                                Color(0xF2000000)
                            )
                        )
                    )
            )
        }
    } else {
        AuroraBackground(modifier = modifier)
    }
}
