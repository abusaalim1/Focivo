package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme

data class AchievementItem(
    val id: String,
    val title: String,
    val description: String,
    val isUnlocked: Boolean,
    val unlockedDate: String? = null
)

@Composable
fun AchievementBadgeCard(
    achievement: AchievementItem,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minimal Geometric Badge Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (achievement.isUnlocked) {
                            if (isDark) Color(0x358CE000) else RegainLimeContainer
                        } else {
                            if (isDark) Color(0x15FFFFFF) else Color(0xFFEFF4EA)
                        }
                    )
                    .border(
                        1.dp,
                        if (achievement.isUnlocked) (if (isDark) RegainLimePrimary else RegainLimeDeepText.copy(alpha = 0.4f)) else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(26.dp)) {
                    val strokeW = 1.6.dp.toPx()
                    val badgeColor = if (achievement.isUnlocked) {
                        if (isDark) RegainLimePrimary else RegainLimeDeepText
                    } else Color.Gray.copy(alpha = 0.5f)

                    when (achievement.id) {
                        "first_focus" -> {
                            // Concentric Orbit & Center Point
                            drawCircle(color = badgeColor, radius = 10.dp.toPx(), style = Stroke(strokeW))
                            drawCircle(color = badgeColor, radius = 3.dp.toPx())
                        }
                        "one_hour" -> {
                            // Minimal Hourglass Geometry
                            val path = Path().apply {
                                moveTo(4.dp.toPx(), 4.dp.toPx())
                                lineTo(22.dp.toPx(), 4.dp.toPx())
                                lineTo(4.dp.toPx(), 22.dp.toPx())
                                lineTo(22.dp.toPx(), 22.dp.toPx())
                                close()
                            }
                            drawPath(path, color = badgeColor, style = Stroke(strokeW, cap = StrokeCap.Round))
                        }
                        "streak_7" -> {
                            // Diamond / Hexagon Geometry
                            val path = Path().apply {
                                moveTo(13.dp.toPx(), 2.dp.toPx())
                                lineTo(24.dp.toPx(), 13.dp.toPx())
                                lineTo(13.dp.toPx(), 24.dp.toPx())
                                lineTo(2.dp.toPx(), 13.dp.toPx())
                                close()
                            }
                            drawPath(path, color = badgeColor, style = Stroke(strokeW))
                            drawCircle(color = badgeColor, radius = 2.5.dp.toPx(), center = Offset(13.dp.toPx(), 13.dp.toPx()))
                        }
                        else -> {
                            // Precision geometric rings
                            drawCircle(color = badgeColor, radius = 11.dp.toPx(), style = Stroke(strokeW))
                            drawCircle(color = badgeColor.copy(alpha = 0.5f), radius = 6.dp.toPx(), style = Stroke(strokeW))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (achievement.isUnlocked) textPrimary else textSecondary
                        )
                    )
                    if (achievement.isUnlocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(RegainLimeContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "UNLOCKED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = RegainLimeDeepText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                )

                if (achievement.unlockedDate != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Achieved ${achievement.unlockedDate}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}
