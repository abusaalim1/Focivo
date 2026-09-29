package com.example.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
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
    val isStudying = pose == MascotPose.STUDYING
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

    val actualWidth = width ?: size
    val actualHeight = height ?: size

    Box(
        modifier = modifier
            .width(actualWidth)
            .height(actualHeight),
        contentAlignment = Alignment.Center
    ) {
        if (isStudying) {
            // High-resolution clean transparent studying mascot with zero black background
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "study_mascot_float")
            val floatOffset by infiniteTransition.animateFloat(
                initialValue = -3f,
                targetValue = 3f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "mascot_y_offset"
            )
            val scaleEffect by infiniteTransition.animateFloat(
                initialValue = 0.98f,
                targetValue = 1.02f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "mascot_scale"
            )

            Image(
                painter = painterResource(id = R.drawable.mascot_studying),
                contentDescription = "Focivo Mascot Studying",
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = floatOffset.dp)
                    .graphicsLayer(scaleX = scaleEffect, scaleY = scaleEffect),
                contentScale = ContentScale.Fit
            )
        } else {
            Image(
                painter = painterResource(id = targetResId),
                contentDescription = "Focivo Mascot $pose",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * Plays the mascot study video animation in a seamless muted loop using TextureView.
 * Directly streams the looping video without any static mascot placeholder image.
 */
@Composable
fun MascotVideoPlayer(
    modifier: Modifier = Modifier,
    @RawRes videoResId: Int = R.raw.mascot_study_timer,
    zoomFactor: Float = 0.94f
) {
    AndroidView(
        factory = { ctx ->
            val textureView = TextureView(ctx)
            textureView.apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var mediaPlayer: MediaPlayer? = null
                    private var vWidth = 720
                    private var vHeight = 1280

                    private fun updateTransform(tv: TextureView) {
                        val viewW = tv.width
                        val viewH = tv.height
                        if (viewW <= 0 || viewH <= 0 || vWidth <= 0 || vHeight <= 0) return

                        val videoAspect = vWidth.toFloat() / vHeight.toFloat()
                        val viewAspect = viewW.toFloat() / viewH.toFloat()

                        // Full vertical scene fit: ensures the entire graduation cap at the top
                        // and the study table & books at the bottom are 100% completely visible
                        val verticalSpanFraction = 0.72f
                        val scaleY = (1.0f / verticalSpanFraction) * zoomFactor
                        val scaleX = scaleY * (videoAspect / viewAspect)

                        val matrix = android.graphics.Matrix()
                        val pivotY = viewH * 0.54f
                        matrix.setScale(scaleX, scaleY, viewW / 2f, pivotY)
                        tv.setTransform(matrix)
                    }

                    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                        try {
                            val surface = Surface(surfaceTexture)
                            mediaPlayer = MediaPlayer().apply {
                                setSurface(surface)
                                val afd = ctx.resources.openRawResourceFd(videoResId)
                                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()
                                isLooping = true
                                setVolume(0f, 0f)
                                setOnVideoSizeChangedListener { _, vw, vh ->
                                    if (vw > 0 && vh > 0) {
                                        vWidth = vw
                                        vHeight = vh
                                        textureView.post { updateTransform(textureView) }
                                    }
                                }
                                setOnPreparedListener { mp ->
                                    if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                        vWidth = mp.videoWidth
                                        vHeight = mp.videoHeight
                                    }
                                    textureView.post { updateTransform(textureView) }
                                    mp.start()
                                }
                                prepareAsync()
                            }
                        } catch (_: Exception) {}
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        textureView.post { updateTransform(textureView) }
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        try {
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                            mediaPlayer = null
                        } catch (_: Exception) {}
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier
    )
}
