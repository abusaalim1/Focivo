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
    CONCERNED,
    GUARD_WARNING,
    GUARD,
    STUDY_POSE,
    CELEBRATE_NEW,
    BREAK,
    FOREST,
    SUNSET,
    SAKURA,
    MIDNIGHT,
    DESERT,
    ROYAL,
    CRIMSON,
    LAVENDER,
    AMBER,
    MINT,
    LAGOON,
    RAIN,
    BAMBOO,
    AUTUMN,
    COFFEE,
    WINTER,
    ROSE,
    EMBER,
    STORM,
    CRYSTAL,
    GALAXY,
    LANTERN,
    STUDY_HAPPY
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
    pose: MascotPose = MascotPose.IDLE,
    animate: Boolean = true
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
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val isBlocked = pose == MascotPose.STOP_SIGN || pose == MascotPose.ANGRY
    val shouldAnimate = animate && !isBlocked

    val targetResId = when (pose) {
        MascotPose.WELCOME -> R.drawable.mascot_welcome
        MascotPose.EMPTY_STATE -> R.drawable.mascot_empty_state
        MascotPose.CELEBRATING -> R.drawable.mascot_celebration
        MascotPose.ACHIEVEMENT -> R.drawable.mascot_achievement
        MascotPose.GRATEFUL -> R.drawable.mascot_grateful
        MascotPose.CONCERNED -> R.drawable.mascot_concerned
        MascotPose.STOP_SIGN, MascotPose.ANGRY -> R.drawable.mascot_blocked
        MascotPose.STUDYING -> R.drawable.mascot_studying
        MascotPose.GUARD_WARNING -> R.drawable.mascot_warning_transparent
        MascotPose.GUARD -> R.drawable.mascot_guard_transparent
        MascotPose.STUDY_POSE -> R.drawable.mascot_study_transparent
        MascotPose.CELEBRATE_NEW -> R.drawable.mascot_celebrate_transparent
        MascotPose.BREAK -> R.drawable.mascot_break
        MascotPose.FOREST -> R.drawable.theme_mascot_forest
        MascotPose.SUNSET -> R.drawable.theme_mascot_sunset
        MascotPose.SAKURA -> R.drawable.theme_mascot_sakura
        MascotPose.MIDNIGHT -> R.drawable.theme_mascot_midnight
        MascotPose.DESERT -> R.drawable.theme_mascot_desert
        MascotPose.ROYAL -> R.drawable.theme_mascot_royal
        // Round-3/4 themes resolve their mascot at runtime (theme_mascot_<id>);
        // falls back to the study mascot when the asset is not packaged yet.
        MascotPose.CRIMSON, MascotPose.LAVENDER, MascotPose.AMBER, MascotPose.MINT,
        MascotPose.LAGOON, MascotPose.RAIN, MascotPose.BAMBOO, MascotPose.AUTUMN,
        MascotPose.COFFEE, MascotPose.WINTER, MascotPose.ROSE, MascotPose.EMBER,
        MascotPose.STORM, MascotPose.CRYSTAL, MascotPose.GALAXY, MascotPose.LANTERN ->
            resolveThemeMascotResId(LocalContext.current, pose)
        // Round-3: happy default study mascot for the Aurora timer.
        MascotPose.STUDY_HAPPY -> R.drawable.mascot_study_happy
        MascotPose.IDLE -> R.drawable.mascot_base
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mascot_anim")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldAnimate) -2.5f else 0f,
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
            .then(
                if (shouldAnimate) {
                    Modifier.graphicsLayer { translationY = floatOffset }
                } else Modifier
            ),
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
 * Cleanly centered and fitted inside the timer dial.
 */
@Composable
fun MascotVideoPlayer(
    modifier: Modifier = Modifier,
    @RawRes videoResId: Int = R.raw.mascot_study_timer
) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true

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
        onRelease = { view ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    (view.drawable as? AnimatedImageDrawable)?.stop()
                } catch (_: Exception) {}
            }
        },
        modifier = modifier
    )
}

/**
 * Round-3: resolves a theme mascot drawable at runtime by name
 * ("theme_mascot_<pose>", e.g. theme_mascot_crimson). Returns the study
 * mascot when the asset is not packaged yet, so new themes never crash.
 */
private fun resolveThemeMascotResId(
    context: android.content.Context,
    pose: MascotPose
): Int {
    val id = context.resources.getIdentifier(
        "theme_mascot_" + pose.name.lowercase(),
        "drawable",
        context.packageName
    )
    return if (id != 0) id else R.drawable.mascot_study_transparent
}
