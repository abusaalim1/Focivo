package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppleLinearFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.LocalAppAccent
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import kotlin.math.abs
import kotlin.math.roundToInt

enum class NavTab(
    val title: String,
    val icon: ImageVector
) {
    HOME("Home", Icons.Rounded.Home),
    FOCUS("Study", Icons.Rounded.CenterFocusStrong),
    LEADERBOARD("Ranking", Icons.Rounded.EmojiEvents),
    INSIGHTS("Stats", Icons.Rounded.BarChart),
    PROFILE("Profile", Icons.Rounded.Person)
}

@Composable
fun FloatingNavigation(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true
) {
    val view = LocalView.current
    val isDark = isAppInDarkTheme()
    val density = LocalDensity.current

    val barBg = focivoTokens().surface.copy(alpha = 0.92f)
    val barBorder = focivoTokens().line
    // v1.4.0-round2: active pill follows the full-screen theme accent
    // (Aurora accent == RegainLimePrimary, so the default look is unchanged).
    val activeBg = LocalAppAccent.current
    val activeContent = focivoTokens().onAccent
    val inactiveContent = focivoTokens().muted

    // Liquid nav pill state (Motion Lab section 4): one pill slides behind the
    // active tab with spring physics (lab: vel += ((target-pos)*180 - vel*16)*dt
    // -> spring(dampingRatio = 0.6f, stiffness = 180f)) and stretches mid-travel
    // (lab: stretch = min(1, |vel|/900)).
    // Root X (in window) -> (root X, width px) of each measured tab.
    var navOriginX by remember { mutableStateOf<Float?>(null) }
    val tabLayout = remember { mutableStateMapOf<NavTab, Pair<Float, Float>>() }
    val pillX = remember { Animatable(0f) }
    var didSnap by remember { mutableStateOf(false) }

    // Center X of the selected tab, in the nav container's coordinates.
    val targetCenterX: Float? = navOriginX?.let { originX ->
        tabLayout[selectedTab]?.let { (x, w) -> x - originX + w / 2f }
    }

    LaunchedEffect(targetCenterX) {
        val target = targetCenterX ?: return@LaunchedEffect
        if (!didSnap) {
            // First measurement: snap to the initial tab, no animation on first frame.
            pillX.snapTo(target)
            didSnap = true
        } else {
            pillX.animateTo(
                target,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 180f)
            )
        }
    }

    // Pill width tracks the selected tab's measured width with a fast tween so
    // it never fights the spring.
    val targetWidthPx = tabLayout[selectedTab]?.second ?: 0f
    val pillWidthPx by animateFloatAsState(
        targetValue = targetWidthPx,
        animationSpec = tween(150),
        label = "pill_width"
    )
    val pillWidthDp = with(density) { pillWidthPx.toDp() }

    // Read pillX.value in composition so the stretch recomputes every animation frame.
    val pillPos = pillX.value
    val stretch = (abs(pillX.velocity) / 900f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Apple-inspired Floating Dock
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = if (isDark) Color(0x18000000) else Color(0x0C000000),
                    spotColor = if (isDark) Color(0x35000000) else Color(0x14000000)
                )
                .clip(CircleShape)
                .background(barBg)
                .border(0.8.dp, barBorder, CircleShape)
                .padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
            // Coordinate space for the liquid pill.
            Box(
                modifier = Modifier.onGloballyPositioned { navOriginX = it.boundsInRoot().left }
            ) {
                // Single liquid pill drawn BEHIND the tabs (first child = back).
                // It has no click handling, so tabs on top receive all touches.
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .offset { IntOffset((pillPos - pillWidthPx / 2f).roundToInt(), 0) }
                        .graphicsLayer {
                            scaleX = 1f + stretch * 0.4f
                            transformOrigin = TransformOrigin.Center
                        }
                        .clip(CircleShape)
                        .background(activeBg)
                        .width(pillWidthDp)
                )

                Row(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavTab.entries.forEach { tab ->
                        val isSelected = tab == selectedTab
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.94f else 1f,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
                            label = "tab_scale"
                        )

                        val activeContentColor by animateColorAsState(
                            targetValue = if (isSelected) activeContent else inactiveContent,
                            animationSpec = tween(180),
                            label = "tab_content"
                        )

                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .onGloballyPositioned { coords ->
                                    val pos = coords.boundsInRoot().left
                                    tabLayout[tab] = pos to coords.size.width.toFloat()
                                }
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) {
                                    if (hapticsEnabled) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                    onTabSelected(tab)
                                }
                                .padding(
                                    horizontal = if (isSelected) 14.dp else 10.dp,
                                    vertical = 9.dp
                                )
                                .testTag("nav_tab_${tab.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = activeContentColor,
                                modifier = Modifier.size(19.dp)
                            )

                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(animationSpec = tween(150)) + expandHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                exit = fadeOut(animationSpec = tween(100)) + shrinkHorizontally(
                                    animationSpec = tween(120)
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = AppleLinearFontFamily,
                                            color = activeContentColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.1.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
