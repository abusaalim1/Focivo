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
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.focivoTokens

/**
 * Design-system primary button (owner's Full App Redesign, 2026-10-16):
 * flat lime pill, dark label, Poppins semibold — pressed (0.97x) + disabled
 * states. An explicit [accent] still overrides the token.
 */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    accent: Color = Color.Unspecified,
    testTag: String? = null
) {
    val T = focivoTokens()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val effectiveAccent = if (accent == Color.Unspecified) T.accent else accent
    val onAccent = if (accent == Color.Unspecified) T.onAccent else Color(0xFF0A0A0B)

    Box(
        modifier = modifier
            .scale(if (pressed && enabled) 0.97f else 1f)
            .clip(CircleShape)
            .background(effectiveAccent.copy(alpha = if (enabled) 1f else 0.4f))
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
            color = onAccent.copy(alpha = if (enabled) 1f else 0.55f)
        )
    }
}

/** Ghost/secondary variant: surface pill, 1dp line border, primary label. */
@Composable
fun PremiumSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = Color.Unspecified,
    testTag: String? = null
) {
    val T = focivoTokens()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .scale(if (pressed && enabled) 0.97f else 1f)
            .clip(CircleShape)
            .background(T.surface)
            .border(1.dp, T.line, CircleShape)
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
            color = T.text.copy(alpha = if (enabled) 1f else 0.45f)
        )
    }
}

/** Circular icon button on the token system. */
@Composable
fun PremiumIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconTint: Color = Color.Unspecified,
    enabled: Boolean = true
) {
    val T = focivoTokens()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(size)
            .scale(if (pressed && enabled) 0.92f else 1f)
            .clip(CircleShape)
            .background(T.surface)
            .border(1.dp, T.line, CircleShape)
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
            tint = (if (iconTint == Color.Unspecified) T.text else iconTint).copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}
