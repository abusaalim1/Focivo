package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.util.TimerThemeManager
import com.example.util.TimerTheme

/**
 * Full-bleed theme background (round 3 — full-screen themes).
 *
 * The theme's backdrop image fills the ENTIRE screen edge-to-edge
 * (fillMaxSize + ContentScale.Crop, full alpha) behind ALL content —
 * top bar, cards and the bottom-nav area. A readability scrim sits on
 * top: stronger at the top and bottom edges, open in the middle so the
 * theme artwork breathes through while text stays crisp.
 *
 * Aurora (default) keeps the code-drawn [AuroraBackground] unchanged.
 */
@Composable
fun ThemedScreenBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val theme by TimerThemeManager.selectedTheme.collectAsState()
    val backdropRes = remember(theme.id) { resolveThemeBackdropRes(context, theme) }
    if (backdropRes != null) {
        Box(modifier = modifier) {
            Image(
                painter = painterResource(id = backdropRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Readability scrim: strong at the edges, open in the middle.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0xCC000000),
                            0.35f to Color(0x99000000),
                            0.65f to Color(0x99000000),
                            1.0f to Color(0xCC000000)
                        )
                    )
            )
        }
    } else {
        AuroraBackground(modifier = modifier)
    }
}

/**
 * Resolves a theme's backdrop drawable. Compile-time res id first;
 * falls back to a runtime lookup by name ("theme_backdrop_<id>") so a
 * theme keeps working even if its assets are added after the code.
 * Returns null when no asset exists (callers fall back gracefully).
 */
fun resolveThemeBackdropRes(context: Context, theme: TimerTheme): Int? {
    theme.backdropRes?.let { return it }
    val id = context.resources.getIdentifier(
        "theme_backdrop_" + theme.id, "drawable", context.packageName
    )
    return if (id != 0) id else null
}
