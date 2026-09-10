package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * LockZenLogoView - Displays the official Focivo brand logo icon.
 */
@Composable
fun LockZenLogoView(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    showBackgroundSquircle: Boolean = true
) {
    Image(
        painter = painterResource(id = R.drawable.focivo_logo),
        contentDescription = "Focivo Logo",
        modifier = modifier.size(size)
    )
}

