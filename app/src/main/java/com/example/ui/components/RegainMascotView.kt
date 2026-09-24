package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Renders mascot pose variants (welcome, empty_state, celebration, achievement, grateful, concerned, blocked, study, idle)
 * with clean transparency and soft glowing halo aura.
 */
@Composable
fun RegainMascotView(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    width: Dp? = null,
    height: Dp? = null,
    pose: MascotPose = MascotPose.IDLE
) {
    val context = LocalContext.current
    val isStudying = pose == MascotPose.STUDYING
    val isBlocked = pose == MascotPose.STOP_SIGN || pose == MascotPose.ANGRY

    val isStaticPng = pose in listOf(
        MascotPose.WELCOME,
        MascotPose.EMPTY_STATE,
        MascotPose.CELEBRATING,
        MascotPose.ACHIEVEMENT,
        MascotPose.GRATEFUL,
        MascotPose.CONCERNED,
        MascotPose.STOP_SIGN,
        MascotPose.ANGRY
    )

    val targetResId = when (pose) {
        MascotPose.WELCOME -> R.drawable.mascot_welcome
        MascotPose.EMPTY_STATE -> R.drawable.mascot_empty_state
        MascotPose.CELEBRATING -> R.drawable.mascot_celebration
        MascotPose.ACHIEVEMENT -> R.drawable.mascot_achievement
        MascotPose.GRATEFUL -> R.drawable.mascot_grateful
        MascotPose.CONCERNED -> R.drawable.mascot_concerned
        MascotPose.STOP_SIGN, MascotPose.ANGRY -> R.drawable.mascot_blocked
        MascotPose.STUDYING -> R.drawable.mascot_active_study
        MascotPose.IDLE -> R.drawable.mascot_idle_new
    }

    val actualWidth = width ?: size
    val actualHeight = height ?: size

    Box(
        modifier = modifier
            .width(actualWidth)
            .height(actualHeight),
        contentAlignment = Alignment.Center
    ) {
        // Soft glowing radial halo aura behind the mascot character
        Box(
            modifier = Modifier
                .width(actualWidth * 0.85f)
                .height(actualHeight * 0.85f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isBlocked) Color(0xFFE53935).copy(alpha = 0.22f)
                            else if (pose == MascotPose.CONCERNED) Color(0xFFFF9800).copy(alpha = 0.22f)
                            else RegainLimePrimary.copy(alpha = if (isStudying) 0.35f else 0.18f),
                            if (isBlocked) Color(0xFFFF5252).copy(alpha = 0.08f)
                            else if (pose == MascotPose.CONCERNED) Color(0xFFFFB74D).copy(alpha = 0.08f)
                            else RegainLimeLight.copy(alpha = if (isStudying) 0.12f else 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        if (isStaticPng) {
            Image(
                painter = painterResource(id = targetResId),
                contentDescription = "Focivo Mascot $pose",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            var animatedDrawable by remember(targetResId) { mutableStateOf<Drawable?>(null) }

            LaunchedEffect(targetResId) {
                withContext(Dispatchers.IO) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(context.resources, targetResId)
                            val drawable = ImageDecoder.decodeDrawable(source)
                            if (drawable is AnimatedImageDrawable) {
                                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                                drawable.start()
                                withContext(Dispatchers.Main) {
                                    animatedDrawable = drawable
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    animatedDrawable = null
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            animatedDrawable = null
                        }
                    }
                }
            }

            val painter = rememberAsyncImagePainter(model = animatedDrawable ?: targetResId)

            Image(
                painter = painter,
                contentDescription = "Focivo Mascot Image",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(if (isStudying) 1.75f else 1.05f),
                contentScale = ContentScale.Fit
            )
        }
    }
}
