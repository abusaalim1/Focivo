package com.example.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * v1.2.3 (final assembly) — Motion Lab "Lock-in start".
 *
 * Full-screen study-start transition: the user pressed Start -> this
 * animation plays -> the apps actually lock. Concentric lime rings sweep
 * in while the user's REAL blocked apps (launcher icons) arrange around
 * the ring and each gets its lock badge popping in with a stagger.
 *
 * [onComplete] fires once (~2.4s) and the caller starts the timer for
 * real. [onDismiss] (system back) cancels the start entirely.
 */
@Composable
fun LockInStartOverlay(
    blockedPackages: List<String>,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    // Screenshot-test hook: freeze the animation at a fixed sweep progress
    // (0..1). When set, the master clock never starts, so the composition
    // reaches idleness and Roborazzi can capture a deterministic frame.
    testFreezeProgress: Float? = null
) {
    val context = LocalContext.current
    BackHandler(onBack = onDismiss)

    // Ring sweep 0 -> 360 over the first 1.6s; icons pop in staggered.
    val sweep = remember { Animatable(testFreezeProgress ?: 0f) }
    val pulse = remember { Animatable(0f) }
    val shown = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (testFreezeProgress != null) return@LaunchedEffect
        shown.value = true
        launch {
            sweep.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
        }
        launch {
            // Gentle infinite pulse while the animation plays.
            while (true) {
                pulse.animateTo(1f, tween(900, easing = LinearEasing))
                pulse.snapTo(0f)
            }
        }
        delay(2400)
        onComplete()
    }

    // Real launcher icons for the blocked packages (up to 8), resolved
    // off the main thread; falls back to a shield glyph per missing icon.
    var iconBitmaps by remember { mutableStateOf<Map<String, ImageBitmap?>>(emptyMap()) }
    LaunchedEffect(blockedPackages) {
        val pm = context.packageManager
        val map = mutableMapOf<String, ImageBitmap?>()
        for (pkg in blockedPackages.take(8)) {
            map[pkg] = runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationIcon(info).toBitmap(width = 96, height = 96).asImageBitmap()
            }.getOrNull()
        }
        iconBitmaps = map
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0B))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* absorb taps during the transition */ },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Lock in",
                fontFamily = SerifItalicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = Color(0xFFF4F4F5)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Locking your apps\u2026",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = Color(0xFF76767C)
            )
            Spacer(modifier = Modifier.height(36.dp))

            Box(
                modifier = Modifier.size(300.dp),
                contentAlignment = Alignment.Center
            ) {
                // Animated concentric rings (Motion Lab lock-in language).
                // Ring radius matches the icon circle below: 0.87 of half box.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lime = Color(0xFFB6F13A)
                    val ringR = size.minDimension / 2f * 0.87f
                    // Static faint full rings.
                    drawCircle(
                        color = lime.copy(alpha = 0.14f),
                        radius = ringR,
                        style = Stroke(width = 3f)
                    )
                    drawCircle(
                        color = lime.copy(alpha = 0.10f),
                        radius = ringR * 0.74f,
                        style = Stroke(width = 2f)
                    )
                    // Sweeping arc.
                    drawArc(
                        color = lime,
                        startAngle = -90f,
                        sweepAngle = 360f * sweep.value,
                        useCenter = false,
                        style = Stroke(width = 7f, cap = StrokeCap.Round)
                    )
                    // Pulse ring.
                    val pr = ringR * 0.74f + (ringR * 0.26f) * pulse.value
                    drawCircle(
                        color = lime.copy(alpha = 0.28f * (1f - pulse.value)),
                        radius = pr,
                        style = Stroke(width = 4f)
                    )
                }

                // Center shield.
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF131315))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFB6F13A),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Blocked app icons arranged in a circle; each lock badge
                // pops in with a stagger as the sweep passes.
                val shownPkgs = blockedPackages.take(8)
                val n = shownPkgs.size.coerceAtLeast(1)
                shownPkgs.forEachIndexed { i, pkg ->
                    val angle = (360f / n) * i - 90f
                    val rad = Math.toRadians(angle.toDouble())
                    // Same circle as the canvas ring: 0.87 of the 150dp half-box.
                    val iconR = 150.dp * 0.87f
                    val dx = (cos(rad) * iconR.value).dp
                    val dy = (sin(rad) * iconR.value).dp
                    // Stagger: icons pop in as the sweep travels.
                    val iconScale = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        delay((300 + i * 180).toLong())
                        iconScale.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = dx, y = dy)
                            .scale(iconScale.value.coerceAtLeast(0.01f)),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        val bmp = iconBitmaps[pkg]
                        if (bmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bmp,
                                contentDescription = pkg,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1F1F22)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = pkg,
                                    tint = Color(0xFF76767C),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .offset(x = 6.dp, y = 6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB6F13A))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF0A0A0B),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
            val count = blockedPackages.size
            Text(
                text = if (count > 0) {
                    "$count ${if (count == 1) "app" else "apps"} locked for this session"
                } else {
                    "Focus shield armed"
                },
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFFB6F13A)
            )
        }
    }
}

/** Resolve app labels for the lock-in overlay (best-effort). */
fun appLabelFor(context: Context, packageName: String): String {
    return runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
