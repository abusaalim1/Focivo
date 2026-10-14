package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Final phase: unified feature icon. 3D PNG icons render as rounded tiles
 * (28% corner clip trims the solid corners of the source art); legacy
 * vector glyphs keep the old circular tile for backwards compatibility.
 * Utility glyphs (chevrons, close X, play/pause, toggles) intentionally stay
 * vectors — only FEATURE icons become 3D.
 */
@Composable
fun FeatureIcon(
    @DrawableRes drawableRes: Int? = null,
    imageVector: ImageVector? = null,
    contentDescription: String? = null,
    size: Dp = 46.dp,
    vectorCircleColor: Color = Color(0xFF2C3238),
    vectorTint: Color = Color(0xFFA0A89E),
    modifier: Modifier = Modifier
) {
    if (drawableRes != null) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(28))
        )
    } else if (imageVector != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(vectorCircleColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = vectorTint,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}
