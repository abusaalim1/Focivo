package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.util.AmbientKind
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ambient themes (v1.5.0): living animated backdrops drawn entirely in
 * code (Compose Canvas) — no video, no image assets, so the APK stays
 * under 50 MB. Battery-friendly by design:
 * - a single shared time driver (one infinite transition per backdrop),
 * - low particle counts (18-60),
 * - particle positions are pure functions of time (no per-frame state
 *   allocation), so GC churn is near zero,
 * - the animation pauses automatically when the composable leaves the
 *   composition (infiniteTransition is composition-bound).
 *
 * Design language: rich gradient base + soft radial glows + one gentle
 * motion idea per theme. Slow easing, no harsh movement.
 */
@Composable
fun AmbientBackdrop(kind: AmbientKind, modifier: Modifier = Modifier) {
    // Shared clock: 2-minute loop so every layer moves on the same beat.
    // t is in seconds (0..120); all layers read it directly.
    val transition = rememberInfiniteTransition(label = "ambient_clock")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing)),
        label = "ambient_t"
    )
    // One-time random seeds per theme; positions derive from t.
    val particles = remember(kind) { makeParticles(kind) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        when (kind) {
            AmbientKind.SAKURA_DRIFT -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF2A0F1C),
                        1f to Color(0xFF12070D)
                    )
                )
                // Soft rose glow drifting across the top third.
                val gx = w * (0.5f + 0.28f * sin(t * 0.13f))
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0x66F48FB1),
                        1f to Color.Transparent,
                        center = Offset(gx, h * 0.22f),
                        radius = w * 0.55f
                    ),
                    radius = w * 0.55f,
                    center = Offset(gx, h * 0.22f)
                )
                for (p in particles) {
                    val y = ((p.y + t * p.speed * 0.028f) % 1.15f - 0.075f) * h
                    val x = (p.x + sin(t * 0.5f + p.phase) * 0.04f) * w
                    val s = 6f + p.s * 10f
                    val petal = Color(
                        red = 1f,
                        green = 0.63f + p.s * 0.16f,
                        blue = 0.75f + p.s * 0.12f,
                        alpha = 0.75f
                    )
                    withTransform({
                        translate(x, y)
                        rotate((t * 28f * p.speed + p.phase * 57f) % 360f, Offset.Zero)
                    }) {
                        drawOval(
                            color = petal,
                            topLeft = Offset(-s, -s * 0.55f),
                            size = Size(s * 2f, s * 1.1f)
                        )
                    }
                }
            }

            AmbientKind.FIREFLY_FOREST -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF07170F),
                        1f to Color(0xFF030A06)
                    )
                )
                // Moonlit glow low in the scene.
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0x3366BB6A),
                        1f to Color.Transparent,
                        center = Offset(w * 0.5f, h * 0.85f),
                        radius = w * 0.8f
                    ),
                    radius = w * 0.8f,
                    center = Offset(w * 0.5f, h * 0.85f)
                )
                for (p in particles) {
                    val x = (p.x + sin(t * 0.32f + p.phase) * 0.06f) * w
                    val y = (p.y + sin(t * 0.26f + p.phase * 1.7f) * 0.05f) * h
                    // Firefly blink: slow sine, gentle.
                    val a = 0.25f + 0.75f * (0.5f + 0.5f * sin(t * 1.4f + p.phase * 3f))
                    val r = (2.2f + p.s * 2.2f) * density
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color(0xFFD2FF78).copy(alpha = a),
                            0.4f to Color(0x99BFF54A).copy(alpha = a * 0.6f),
                            1f to Color.Transparent,
                            center = Offset(x, y),
                            radius = r * 6f
                        ),
                        radius = r * 6f,
                        center = Offset(x, y)
                    )
                }
            }

            AmbientKind.SUNSET_DUNES -> {
                // Warm breathing gradient: shifts amber <-> rose on a slow sine.
                val k = 0.5f + 0.5f * sin(t * 0.2f)
                val top = Color(
                    red = 0.23f + 0.12f * k,
                    green = 0.086f + 0.04f * k,
                    blue = 0.031f + 0.12f * (1f - k)
                )
                drawRect(
                    Brush.verticalGradient(
                        0f to top,
                        0.55f to Color(0xFF2A1206),
                        1f to Color(0xFF12070A)
                    )
                )
                // Distant dune band: two layered sine silhouettes.
                val dune = Path().apply {
                    moveTo(0f, h * 0.66f)
                    val steps = 24
                    for (i in 0..steps) {
                        val fx = i.toFloat() / steps
                        val fy = h * 0.66f + sin(fx * 6.28f + t * 0.05f) * h * 0.02f -
                                sin(fx * 12.56f + 1.3f) * h * 0.008f
                        lineTo(fx * w, fy)
                    }
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(dune, Color(0xFF1B0B04).copy(alpha = 0.85f))
                // Heat shimmer orbs rising slowly.
                for (p in particles) {
                    val y = ((p.y - t * p.speed * 0.016f) % 1.1f)
                    val yy = (if (y < 0f) y + 1.1f else y - 0.05f) * h
                    val x = (p.x + sin(t * 0.3f + p.phase) * 0.05f) * w
                    val r = (16f + p.s * 44f) * density
                    val a = 0.20f * (1f - (yy / h))
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color(0xFFFF9660).copy(alpha = a),
                            1f to Color.Transparent,
                            center = Offset(x, yy),
                            radius = r
                        ),
                        radius = r,
                        center = Offset(x, yy)
                    )
                }
            }

            AmbientKind.AURORA_VEIL -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF0D0B16),
                        0.6f to Color(0xFF080710),
                        1f to Color(0xFF05050A)
                    )
                )
                // Three slow aurora ribbons: sine-band paths, violet -> teal.
                val ribbons = listOf(
                    Triple(0.30f, 0.16f, Color(0xFF7C4DFF)),
                    Triple(0.44f, 0.12f, Color(0xFF4DD0E1)),
                    Triple(0.56f, 0.20f, Color(0xFFB388FF))
                )
                for ((idx, rib) in ribbons.withIndex()) {
                    val (baseY, band, col) = rib
                    val path = Path().apply {
                        val steps = 32
                        for (i in 0..steps) {
                            val fx = i.toFloat() / steps
                            val wave = sin(fx * 6.28f * 1.5f + t * 0.35f + idx * 1.9f) *
                                    h * 0.045f +
                                    sin(fx * 6.28f * 3f - t * 0.22f + idx) * h * 0.018f
                            val cy = baseY * h + wave
                            if (i == 0) moveTo(fx * w, cy) else lineTo(fx * w, cy)
                        }
                        for (i in steps downTo 0) {
                            val fx = i.toFloat() / steps
                            val wave = sin(fx * 6.28f * 1.5f + t * 0.35f + idx * 1.9f) *
                                    h * 0.045f +
                                    sin(fx * 6.28f * 3f - t * 0.22f + idx) * h * 0.018f
                            lineTo(fx * w, baseY * h + wave + band * h)
                        }
                        close()
                    }
                    val pulse = 0.10f + 0.05f * sin(t * 0.5f + idx * 2.1f)
                    drawPath(
                        path,
                        Brush.verticalGradient(
                            0f to col.copy(alpha = pulse * 1.6f),
                            0.5f to col.copy(alpha = pulse),
                            1f to Color.Transparent
                        )
                    )
                }
                // Faint drifting stars.
                for (p in particles.take(18)) {
                    val a = 0.15f + 0.35f * (0.5f + 0.5f * sin(t * 0.8f + p.phase * 4f))
                    drawCircle(
                        Color(0xFFFFFFFF).copy(alpha = a),
                        radius = (0.8f + p.s) * density,
                        center = Offset(p.x * w, p.y * h * 0.6f)
                    )
                }
            }

            AmbientKind.MONSOON_RAIN -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF16202B),
                        0.7f to Color(0xFF0E151D),
                        1f to Color(0xFF0B1118)
                    )
                )
                // Cool top glow, like clouded sky light.
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0x33547A9E),
                        1f to Color.Transparent,
                        center = Offset(w * 0.5f, h * 0.08f),
                        radius = w * 0.7f
                    ),
                    radius = w * 0.7f,
                    center = Offset(w * 0.5f, h * 0.08f)
                )
                // Slanted rain lines falling.
                for (p in particles) {
                    val yy = ((p.y + t * p.speed * 0.5f) % 1.2f - 0.1f) * h
                    val x = (p.x) * w
                    val len = (26f + p.s * 40f) * density
                    val slant = 8f * density
                    drawLine(
                        Color(0xFF8FB4D6).copy(alpha = 0.28f + p.s * 0.22f),
                        start = Offset(x, yy),
                        end = Offset(x - slant, yy + len),
                        strokeWidth = (1.2f + p.s) * density
                    )
                }
            }

            AmbientKind.EMBER_RISE -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF1C0E06),
                        0.65f to Color(0xFF120903),
                        1f to Color(0xFF0D0703)
                    )
                )
                // Warm hearth glow at the bottom.
                val gy = h * (0.86f + 0.02f * sin(t * 0.4f))
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0x66FF6E40),
                        1f to Color.Transparent,
                        center = Offset(w * 0.5f, gy),
                        radius = w * 0.65f
                    ),
                    radius = w * 0.65f,
                    center = Offset(w * 0.5f, gy)
                )
                // Rising ember sparks with flicker and sway.
                for (p in particles) {
                    val y = ((p.y - t * p.speed * 0.05f) % 1.25f)
                    val yy = (if (y < 0f) y + 1.25f else y - 0.125f) * h
                    val x = (p.x + sin(t * 0.7f + p.phase) * 0.045f) * w
                    val flicker = 0.5f + 0.5f * sin(t * (3f + p.s * 4f) + p.phase * 5f)
                    val a = 0.35f + 0.55f * flicker
                    val r = (2f + p.s * 3.2f) * density
                    val col = if (p.s > 0.66f) Color(0xFFFFB74D) else Color(0xFFFF6E40)
                    drawCircle(
                        Brush.radialGradient(
                            0f to col.copy(alpha = a),
                            0.5f to col.copy(alpha = a * 0.35f),
                            1f to Color.Transparent,
                            center = Offset(x, yy),
                            radius = r * 5f
                        ),
                        radius = r * 5f,
                        center = Offset(x, yy)
                    )
                }
            }
        }
    }
}

/** Lightweight seeded particle; x/y are 0..1 normalized, s is size/speed factor. */
private data class Particle(
    val x: Float,
    val y: Float,
    val s: Float,
    val phase: Float,
    val speed: Float
)

private fun makeParticles(kind: AmbientKind): List<Particle> {
    val count = when (kind) {
        AmbientKind.SAKURA_DRIFT -> 40
        AmbientKind.FIREFLY_FOREST -> 30
        AmbientKind.SUNSET_DUNES -> 18
        AmbientKind.AURORA_VEIL -> 24
        AmbientKind.MONSOON_RAIN -> 60
        AmbientKind.EMBER_RISE -> 36
    }
    // Deterministic seed per kind so the first frame is never blank.
    val rnd = kotlin.random.Random(kind.ordinal * 7919 + 13)
    return List(count) {
        Particle(
            x = rnd.nextFloat(),
            y = rnd.nextFloat(),
            s = rnd.nextFloat(),
            phase = rnd.nextFloat() * (2f * PI.toFloat()),
            speed = 0.6f + rnd.nextFloat() * 0.8f
        )
    }
}
