package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppAccent
import com.example.ui.theme.OnAccentDark
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.isAppInDarkTheme

/**
 * v1.4.0-round2: shared premium button system.
 *
 * - 18dp radius, subtle vertical gradient, 1dp top light edge
 * - Poppins semibold label, pressed (0.97x) + disabled states
 * - Accent follows the full-screen theme (LocalAppAccent)
 */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    accent: Color = LocalAppAccent.current,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val radius = 18.dp
    val top = accent.copy(alpha = if (enabled) 1f else 0.45f)
    val bottom = accent.copy(alpha = if (enabled) 0.8f else 0.38f)

    Box(
        modifier = modifier
            .scale(if (pressed && enabled) 0.97f else 1f)
            .clip(RoundedCornerShape(radius))
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawBehind {
                // 1dp top light edge
                val edgeY = 1.dp.toPx()
                val inset = radius.toPx()
                drawLine(
                    color = Color.White.copy(alpha = if (enabled) 0.4f else 0.15f),
                    start = Offset(inset, edgeY),
                    end = Offset(size.width - inset, edgeY),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(radius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(
                horizontal = if (compact) 18.dp else 24.dp,
                vertical = if (compact) 10.dp else 15.dp
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 13.sp else 16.sp,
            color = OnAccentDark.copy(alpha = if (enabled) 1f else 0.5f)
        )
    }
}

/** Ghost/secondary variant: transparent fill, 1dp border, accent label. */
@Composable
fun PremiumSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = LocalAppAccent.current,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDark = isAppInDarkTheme()

    Box(
        modifier = modifier
            .scale(if (pressed && enabled) 0.97f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.dp,
                (if (isDark) Color.White else Color.Black).copy(alpha = if (enabled) 0.22f else 0.1f),
                RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = (if (isDark) Color.White else Color(0xFF1A1A1A)).copy(alpha = if (enabled) 1f else 0.45f)
        )
    }
}

/** Circular glass icon button matching the premium system. */
@Composable
fun PremiumIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconTint: Color = Color.White,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDark = isAppInDarkTheme()

    Box(
        modifier = modifier
            .size(size)
            .scale(if (pressed && enabled) 0.92f else 1f)
            .clip(CircleShape)
            .background(
                (if (isDark) Color.White else Color.Black).copy(alpha = if (enabled) 0.08f else 0.04f)
            )
            .border(
                0.8.dp,
                (if (isDark) Color.White else Color.Black).copy(alpha = 0.14f),
                CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}
