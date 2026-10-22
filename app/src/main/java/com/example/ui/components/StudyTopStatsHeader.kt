package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens

/**
 * v1.3.3 (item 1) — the two stat cards at the TOP of the Study screen.
 *
 * - "Usage Time": today's total device screen time from [com.example.util.DailyUsagePipeline].
 *   When usage permission is missing the value reads "--" with the sub-label
 *   "Usage access needed" (honest grant state, never a fabricated number);
 *   tapping the card opens the premium usage-permission sheet (caller-owned).
 * - "Focus Time": today's completed focus minutes from the session log.
 *
 * Design system: cream/dark token surfaces, lime accent, Poppins labels +
 * Instrument Serif italic display values. English only, no emojis.
 */
@Composable
fun StudyTopStatsHeader(
    usageMillis: Long?,
    hasUsagePermission: Boolean,
    focusMinutesToday: Int,
    onUsageCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("study_top_stats_header"),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            label = "Usage Time",
            displayValue = if (!hasUsagePermission || usageMillis == null) {
                "--"
            } else {
                formatStatDuration(usageMillis)
            },
            // v1.1.3 (FIX 2) HONESTY rule: when PACKAGE_USAGE_STATS is missing
            // the card names the missing grant ("Usage access needed") instead
            // of implying a number or a stale value. Tapping opens system
            // usage access settings (ACTION_USAGE_ACCESS_SETTINGS).
            subLabel = if (!hasUsagePermission || usageMillis == null) "Usage access needed" else "screen time today",
            icon = Icons.Default.Timer,
            onClick = onUsageCardClick,
            clickable = true,
            valueTestTag = "study_usage_time_value",
            cardTestTag = "study_usage_time_card",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Focus Time",
            displayValue = formatStatMinutes(focusMinutesToday),
            subLabel = "focused today",
            icon = Icons.Default.Bolt,
            onClick = {},
            clickable = false,
            valueTestTag = "study_focus_time_value",
            cardTestTag = "study_focus_time_card",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    displayValue: String,
    subLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    clickable: Boolean,
    valueTestTag: String,
    cardTestTag: String,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    GlassCard(
        modifier = modifier.testTag(cardTestTag),
        shape = RoundedCornerShape(20.dp),
        onClick = if (clickable) onClick else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = T.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = T.muted,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp,
                        fontSize = 10.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = displayValue,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = SerifItalicFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = T.text
                ),
                maxLines = 1,
                modifier = Modifier.testTag(valueTestTag)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = PoppinsFontFamily,
                    color = T.muted,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}

/** "3h 12m" / "48m" from millis. */
fun formatStatDuration(millis: Long): String {
    val totalMins = (millis / 60_000L).coerceAtLeast(0L)
    val h = totalMins / 60
    val m = totalMins % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/** "2h 05m" / "45m" from minutes. */
fun formatStatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
