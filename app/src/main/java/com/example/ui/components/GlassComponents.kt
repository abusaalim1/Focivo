package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppleLinearFontFamily
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.PriorityHigh
import com.example.ui.theme.PriorityLow
import com.example.ui.theme.PriorityMedium
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme

/**
 * High-performance Apple & Linear tactile press feedback animation.
 * Features fast damping spring physics, subtle elastic bounce, and smooth alpha reduction.
 */
fun Modifier.pressFeedback(
    scaleDown: Float = 0.965f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 380f
        ),
        label = "press_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 450f
        ),
        label = "press_alpha"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
}

/**
 * Linear-style ambient glowing rim card with razor-sharp micro-borders,
 * inner refraction highlight, and elastic tactile press physics.
 */
@Composable
fun LinearGlowCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    glowColor: Color = RegainLimePrimary,
    glowAlpha: Float = 0.15f,
    accentBorder: Boolean = false,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isAppInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    val cardBg = if (isDark) Color(0xF21C1C1E) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0x22FFFFFF) else Color(0x12000000)
    val topHighlight = if (isDark) Color(0x28FFFFFF) else Color(0x90FFFFFF)

    val effectiveBorder = border ?: if (accentBorder) {
        BorderStroke(1.dp, RegainLimePrimary)
    } else {
        BorderStroke(0.8.dp, cardBorder)
    }

    val clickModifier = if (onClick != null) {
        Modifier
            .pressFeedback(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else Modifier

    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = if (isDark) glowColor.copy(alpha = glowAlpha) else Color(0x0C000000),
                spotColor = if (isDark) Color(0x35000000) else Color(0x14000000)
            )
            .clip(shape)
            .background(cardBg)
            .drawBehind {
                // Subtle top edge highlight simulating real physical glass refraction
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            topHighlight,
                            topHighlight.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 1.5f
                )
            }
            .border(
                border = effectiveBorder,
                shape = shape
            )
            .then(clickModifier),
        content = content
    )
}

/**
 * Smooth entrance animation: cards fade in and slide up gracefully on initial display.
 */
fun Modifier.glassEntrance(delayMs: Int = 0): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "glass_entrance_alpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "glass_entrance_offset"
    )
    this.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}

/**
 * Universal Frosted Glass Card with subtle inner glow, translucent dark/light background,
 * delicate top-edge highlight for true depth, and soft ambient drop shadow.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    borderAlpha: Float = 0.6f,
    accentBorder: Boolean = false,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isAppInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    // Apple-inspired minimal glass surface
    val cardBg = if (isDark) Color(0xF21C1C1E) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0x22FFFFFF) else Color(0x12000000)
    val topHighlight = if (isDark) Color(0x28FFFFFF) else Color(0x90FFFFFF)

    val effectiveBorder = border ?: if (accentBorder) {
        BorderStroke(1.dp, RegainLimePrimary)
    } else {
        BorderStroke(0.8.dp, cardBorder.copy(alpha = borderAlpha.coerceAtLeast(0.75f)))
    }

    val clickModifier = if (onClick != null) {
        Modifier
            .pressFeedback(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else Modifier

    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = if (isDark) Color(0x28000000) else Color(0x10000000),
                spotColor = if (isDark) Color(0x38000000) else Color(0x18000000)
            )
            .clip(shape)
            .background(cardBg)
            .drawBehind {
                // Subtle top edge highlight simulating real physical glass refraction
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            topHighlight,
                            topHighlight.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 1.5f
                )
            }
            .border(
                border = effectiveBorder,
                shape = shape
            )
            .then(clickModifier),
        content = content
    )
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    accentGlow: Boolean = true,
    accentBorder: Boolean = false,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isAppInDarkTheme()
    val infiniteTransition = rememberInfiniteTransition(label = "refraction")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "light_refraction"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier
            .pressFeedback(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else Modifier

    val cardBg = if (isDark) Color(0xF21C1C1E) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0x22FFFFFF) else Color(0x12000000)

    val effectiveBorder = border ?: if (accentBorder) {
        BorderStroke(1.dp, RegainLimePrimary)
    } else {
        BorderStroke(0.8.dp, cardBorder)
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = if (isDark) Color(0x288CE000) else Color(0x0C000000),
                spotColor = if (isDark) Color(0x35000000) else Color(0x14000000)
            )
            .clip(shape)
            .background(cardBg)
            .drawBehind {
                if (accentGlow) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x148CE000),
                                Color(0x24FFFFFF),
                                Color.Transparent
                            ),
                            start = Offset(shimmerOffset, 0f),
                            end = Offset(shimmerOffset + 260f, size.height)
                        )
                    )
                }
                // Subtle top edge highlight
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isDark) Color(0x28FFFFFF) else Color(0x90FFFFFF),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 1.5f
                )
            }
            .border(
                border = effectiveBorder,
                shape = shape
            )
            .then(clickModifier),
        content = content
    )
}

/**
 * Apple-style minimal and premium card container modifier with continuous squircle clipping,
 * ultra-refined hairline border, physical specular highlight refraction, and soft ambient shadow.
 */
fun Modifier.appleCard(
    shape: Shape = RoundedCornerShape(22.dp),
    isDark: Boolean,
    accentBorder: Boolean = false,
    accentColor: Color = RegainLimePrimary,
    elevation: Dp = 4.dp
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = if (isDark) Color(0x18000000) else Color(0x08000000),
        spotColor = if (isDark) Color(0x35000000) else Color(0x0E000000)
    )
    .clip(shape)
    .background(if (isDark) Color(0xF21C1C1E) else Color(0xFFFFFFFF))
    .drawBehind {
        val topHighlight = if (isDark) Color(0x28FFFFFF) else Color(0x90FFFFFF)
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    topHighlight,
                    topHighlight.copy(alpha = 0.85f),
                    Color.Transparent
                )
            ),
            start = Offset(0f, 1f),
            end = Offset(size.width, 1f),
            strokeWidth = 1.5f
        )
    }
    .border(
        width = 0.8.dp,
        color = if (accentBorder) accentColor.copy(alpha = 0.85f) else (if (isDark) Color(0x22FFFFFF) else Color(0x12000000)),
        shape = shape
    )

/**
 * Premium Glass Toggle Switch with smooth animated thumb movement,
 * spring physics, and subtle size expansion on toggle.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isDark = isAppInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    val trackWidth = 48.dp
    val trackHeight = 28.dp
    val thumbSize = 22.dp

    val trackBg by animateColorAsState(
        targetValue = when {
            !enabled -> if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
            checked -> RegainLimePrimary
            else -> if (isDark) Color(0xFF2C322B) else Color(0xFFE2E8F0)
        },
        animationSpec = tween(durationMillis = 220),
        label = "switch_track_bg"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "switch_thumb_offset"
    )

    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF8E8E93)
            checked -> NearBlack
            else -> PureWhite
        },
        animationSpec = tween(durationMillis = 200),
        label = "switch_thumb_color"
    )

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(CircleShape)
            .background(trackBg)
            .border(
                width = 1.dp,
                color = if (checked) RegainLimeDeepText.copy(alpha = 0.25f) else Color(0x18000000),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                onCheckedChange(!checked)
            }
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .shadow(
                    elevation = 3.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x20000000),
                    spotColor = Color(0x28000000)
                )
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

enum class LinearButtonVariant {
    PRIMARY,
    SECONDARY,
    GHOST,
    DANGER
}

@Composable
fun LinearButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: LinearButtonVariant = LinearButtonVariant.PRIMARY,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    fontSize: TextUnit = 13.5.sp
) {
    val isDark = isAppInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val shape = CircleShape

    val bg = when (variant) {
        LinearButtonVariant.PRIMARY -> if (enabled) RegainLimePrimary else (if (isDark) Color(0xFF232A20) else Color(0xFFE5EBE0))
        LinearButtonVariant.SECONDARY -> if (isDark) Color(0xEB1C231C) else Color(0xF5FFFFFF)
        LinearButtonVariant.GHOST -> Color.Transparent
        LinearButtonVariant.DANGER -> if (isDark) Color(0x33EF4444) else Color(0xFFFFEBEE)
    }

    val textColor = when (variant) {
        LinearButtonVariant.PRIMARY -> if (enabled) NearBlack else (if (isDark) Color(0xFF6A7367) else Color(0xFFA0A89D))
        LinearButtonVariant.SECONDARY -> if (isDark) Color(0xFFF0F4ED) else NearBlack
        LinearButtonVariant.GHOST -> if (isDark) Color(0xFFCBD5E1) else SecondaryTextLight
        LinearButtonVariant.DANGER -> Color(0xFFEF4444)
    }

    val border = when (variant) {
        LinearButtonVariant.PRIMARY -> if (enabled) BorderStroke(1.dp, RegainLimePrimary) else null
        LinearButtonVariant.SECONDARY -> BorderStroke(0.8.dp, if (isDark) Color(0x24FFFFFF) else Color(0x14000000))
        LinearButtonVariant.GHOST -> null
        LinearButtonVariant.DANGER -> BorderStroke(1.dp, if (isDark) Color(0x55EF4444) else Color(0xFFFFCDD2))
    }

    Box(
        modifier = modifier
            .pressFeedback(interactionSource = interactionSource)
            .shadow(
                elevation = if (variant == LinearButtonVariant.PRIMARY && enabled) 3.dp else 0.dp,
                shape = shape,
                ambientColor = Color(0x188CE000),
                spotColor = Color(0x228CE000)
            )
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = AppleLinearFontFamily,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    letterSpacing = 0.2.sp
                )
            )
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    fontSize: TextUnit = 14.sp,
    horizontalPadding: Dp = 20.dp,
    verticalPadding: Dp = 12.dp,
    minHeight: Dp = 48.dp,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val pillShape = CircleShape
    val isDark = isAppInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    val backgroundModifier = if (!enabled) {
        Modifier.background(if (isDark) Color(0xFF262C24) else Color(0xFFE8EFE2))
    } else if (isPrimary) {
        Modifier.background(RegainLimePrimary)
    } else {
        Modifier
            .background(if (isDark) Color(0xD81D221C) else PureWhite)
            .border(
                width = 1.5.dp,
                color = if (isDark) RegainLimePrimary else RegainLimeDeepText,
                shape = pillShape
            )
    }

    val textColor = if (!enabled) {
        if (isDark) Color(0xFF6E756C) else Color(0xFFA0A89B)
    } else if (isPrimary) {
        Color(0xFF021207)
    } else {
        if (isDark) Color(0xFFF0F4ED) else NearBlack
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .pressFeedback(interactionSource = interactionSource)
            .shadow(
                elevation = if (isPrimary && enabled) 4.dp else 0.dp,
                shape = pillShape,
                ambientColor = Color(0x208CE000),
                spotColor = Color(0x288CE000)
            )
            .clip(pillShape)
            .then(backgroundModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .testTag("glass_button_${text.lowercase().replace(' ', '_')}"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Box(modifier = Modifier.size(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = AppleLinearFontFamily,
                    fontSize = fontSize,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PriorityBadge(priority: Int, modifier: Modifier = Modifier) {
    val (label, color) = when (priority) {
        1 -> "HIGH" to PriorityHigh
        2 -> "MEDIUM" to PriorityMedium
        else -> "LOW" to PriorityLow
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = " $label",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = AppleLinearFontFamily,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            )
        }
    }
}

@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val containerBg = if (isDark) Color(0xFF262C24) else Color(0xFFEFF6E6)
    val selectedBg = if (isDark) Color(0xFF1D221C) else PureWhite
    val selectedText = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val unselectedText = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight

    Surface(
        color = containerBg,
        shape = CircleShape,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                val itemBg = if (isSelected) selectedBg else Color.Transparent
                val textColor = if (isSelected) selectedText else unselectedText

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            elevation = if (isSelected) 1.5.dp else 0.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x0A000000),
                            spotColor = Color(0x0F000000)
                        )
                        .clip(CircleShape)
                        .background(itemBg)
                        .clickable { onSelectIndex(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = AppleLinearFontFamily,
                            fontSize = 13.sp,
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
