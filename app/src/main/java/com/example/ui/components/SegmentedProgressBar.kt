package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.VioletAccent

@Composable
fun SegmentedProgressBar(
    focusedMinutes: Int,
    goalMinutes: Int,
    modifier: Modifier = Modifier,
    segments: Int = 12
) {
    val isDark = isSystemInDarkTheme()
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
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                )
                Text(
                    text = "Goal: $formattedGoal",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }

            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Light,
                    color = VioletAccent,
                    fontSize = 22.sp
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
                val segmentTrackColor = if (isDark) Color(0x18FFFFFF) else Color(0x14000000)

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
                                        colors = listOf(VioletAccent, LavenderSoft)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
