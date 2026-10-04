package com.example.ui.components

import android.graphics.ImageDecoder
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.annotation.RawRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary

enum class MascotPose {
    IDLE,
    STUDYING,
    ANGRY,
    STOP_SIGN,
    CELEBRATING,
    WELCOME,
    EMPTY_STATE,
    ACHIEVEMENT,
    GRATEFUL,
    CONCERNED
}

/**
 * LockZen / Focivo Official Mascot ("Zenny")
 * Renders the studying video animation during active focus sessions,
 * and clean static mascot PNG assets for other states.
 */
@Composable
fun RegainMascotView(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    width: Dp? = null,
    height: Dp? = null,
    pose: MascotPose = MascotPose.IDLE
) {
    val actualWidth = width ?: size
    val actualHeight = height ?: size

    if (pose == MascotPose.STUDYING) {
        Box(
            modifier = modifier
                .width(actualWidth)
                .height(actualHeight),
            contentAlignment = Alignment.Center
        ) {
            MascotVideoPlayer(
                modifier = Modifier.fillMaxSize(),
                videoResId = R.raw.mascot_study_timer,
                zoomFactor = 0.96f
            )
        }
        return
    }

    val isBlocked = pose == MascotPose.STOP_SIGN || pose == MascotPose.ANGRY

    val targetResId = when (pose) {
        MascotPose.WELCOME -> R.drawable.mascot_welcome
        MascotPose.EMPTY_STATE -> R.drawable.mascot_empty_state
        MascotPose.CELEBRATING -> R.drawable.mascot_celebration
        MascotPose.ACHIEVEMENT -> R.drawable.mascot_achievement
        MascotPose.GRATEFUL -> R.drawable.mascot_grateful
        MascotPose.CONCERNED -> R.drawable.mascot_concerned
        MascotPose.STOP_SIGN, MascotPose.ANGRY -> R.drawable.mascot_blocked
        MascotPose.STUDYING -> R.drawable.mascot_studying
        MascotPose.IDLE -> R.drawable.mascot_base
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mascot_anim")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isBlocked) 2f else -2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Box(
        modifier = modifier
            .width(actualWidth)
            .height(actualHeight)
            .graphicsLayer {
                translationY = floatOffset
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = targetResId),
            contentDescription = "Focivo Mascot $pose",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Plays the mascot study video animation in a seamless muted loop without any black background.
 * Uses hardware-accelerated animated WebP with 100% transparent alpha channel.
 * Centers the graduation cap at the top and study desk at the bottom perfectly.
 */
@Composable
fun MascotVideoPlayer(
    modifier: Modifier = Modifier,
    @RawRes videoResId: Int = R.raw.mascot_study_timer,
    zoomFactor: Float = 0.96f
) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.MATRIX

                fun applyTransform(vw: Int, vh: Int) {
                    if (vw <= 0 || vh <= 0) return
                    // Content region inside 400x711 transparent webp:
                    // Character and study desk are 400px wide, 505px tall, top padding 147px
                    val contentW = 400f
                    val contentH = 505f
                    val topPadding = 147f

                    val scale = minOf(vw.toFloat() / contentW, vh.toFloat() / contentH) * zoomFactor
                    val dx = (vw.toFloat() - contentW * scale) / 2f
                    val dy = (vh.toFloat() - contentH * scale) / 2f - (topPadding * scale)

                    val matrix = android.graphics.Matrix()
                    matrix.setScale(scale, scale)
                    matrix.postTranslate(dx, dy)
                    imageMatrix = matrix
                }

                addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                    val w = right - left
                    val h = bottom - top
                    applyTransform(w, h)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val source = ImageDecoder.createSource(ctx.resources, R.drawable.mascot_active_study)
                        val drawable = ImageDecoder.decodeDrawable(source)
                        setImageDrawable(drawable)
                        if (drawable is AnimatedImageDrawable) {
                            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            drawable.start()
                        }
                    } catch (_: Exception) {
                        setImageResource(R.drawable.mascot_studying)
                    }
                } else {
                    setImageResource(R.drawable.mascot_studying)
                }
            }
        },
        modifier = modifier
    )
}
