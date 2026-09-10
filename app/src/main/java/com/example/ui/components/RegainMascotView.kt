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
import androidx.compose.foundation.layout.size
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
    CELEBRATING
}

/**
 * LockZen / Focivo Official Mascot ("Zenny")
 * Displays mascot_blocked PNG for distraction/lock overlay screens (STOP_SIGN / ANGRY),
 * and transparent animated WebP mascot videos for idle and study states with smooth crossfades.
 */
@Composable
fun RegainMascotView(
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    pose: MascotPose = MascotPose.IDLE
) {
    val context = LocalContext.current
    val isStudying = pose == MascotPose.STUDYING
    val isBlocked = pose == MascotPose.STOP_SIGN || pose == MascotPose.ANGRY

    val targetResId = when {
        isBlocked -> R.drawable.mascot_blocked
        isStudying -> R.drawable.mascot_active_study
        else -> R.drawable.mascot_idle_new
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Soft glowing radial halo aura behind the mascot character
        Box(
            modifier = Modifier
                .size(size * 0.78f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            RegainLimePrimary.copy(alpha = if (isStudying) 0.35f else 0.18f),
                            RegainLimeLight.copy(alpha = if (isStudying) 0.12f else 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        if (isBlocked) {
            Image(
                painter = painterResource(id = R.drawable.mascot_blocked),
                contentDescription = "Focivo Mascot Blocked",
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
                    .scale(if (isStudying) 1.55f else 1.05f),
                contentScale = ContentScale.Fit
            )
        }
    }
}
