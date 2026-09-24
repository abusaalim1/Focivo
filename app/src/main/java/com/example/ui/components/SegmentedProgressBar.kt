package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme

@Composable
fun SegmentedProgressBar(
    focusedMinutes: Int,
    goalMinutes: Int,
    modifier: Modifier = Modifier,
    segments: Int = 12
) {
    val isDark = isAppInDarkTheme()
    val progress = if (goalMinutes > 0) {
        (focusedMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val percentage = (progress * 100).toInt()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "segmented_progress"
    )

    val hours = focusedMinutes / 60
    val mins = focusedMinutes % 60
    val formattedFocused = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    val goalHours = goalMinutes / 60
    val formattedGoal = "${goalHours}h"

    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "$formattedFocused focused",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 16.sp
                    )
                )
                Text(
                    text = "Goal: $formattedGoal",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) RegainLimePrimary else RegainLimeDeepText,
                    fontSize = 20.sp
                )
            )
        }

        Box(modifier = Modifier.height(14.dp))

        // Sophisticated Segmented Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val filledSegments = animatedProgress * segments

            for (i in 0 until segments) {
                val segmentFraction = (filledSegments - i).coerceIn(0f, 1f)
                val segmentTrackColor = if (isDark) Color(0x30FFFFFF) else Color(0xFFE2EBD6)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(segmentTrackColor)
                ) {
                    if (segmentFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(segmentFraction)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(RegainLimePrimary, RegainLimeDeepText)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
