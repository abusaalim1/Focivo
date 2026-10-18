package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import com.example.util.ScreenTimeReadiness

/**
 * v1.1.3 (FIX 2) — "Screen time" readiness card for the Block hub.
 *
 * Shows the three states the usage pipeline needs, in the design-system
 * language of the Block hub (rounded 24dp surface card, Poppins labels,
 * Instrument Serif italic display values, lime accent):
 *
 *  1. Usage access — granted -> green check; missing -> one-tap route to the
 *     system grant screen.
 *  2. Battery exemption — exempt -> green check; not exempt -> one-tap
 *     system request (without it OEM ROMs starve the usage pipeline +
 *     budget enforcement in the background).
 *  3. Pipeline freshness — when the numbers were last computed
 *     ([ScreenTimeReadiness.formatLastUpdated]), with a manual refresh
 *     button; stale data is labelled honestly, never fabricated.
 *
 * English only, no unicode emojis. Test-tagged for the Roborazzi + UI tests.
 */
@Composable
fun ScreenTimeReadinessCard(
    readiness: ScreenTimeReadiness.ReadinessInput,
    nowMs: Long = System.currentTimeMillis(),
    onGrantUsageAccess: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    val stale = ScreenTimeReadiness.isStale(readiness.lastComputedAtMs, nowMs)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("screen_time_readiness_card")
            .clip(RoundedCornerShape(24.dp))
            .background(T.surface)
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // --- Row 1: usage access --------------------------------------
            ReadinessRow(
                title = "Usage access",
                subtitle = if (readiness.usageAccessGranted) {
                    "Granted · Screen time is tracked"
                } else {
                    "Needed to read screen time"
                },
                granted = readiness.usageAccessGranted,
                actionLabel = "Grant",
                onAction = onGrantUsageAccess,
                rowTestTag = "readiness_usage_row",
                actionTestTag = "readiness_grant_usage_button"
            )
            Divider()
            // --- Row 2: battery exemption ---------------------------------
            ReadinessRow(
                title = "Battery exemption",
                subtitle = if (readiness.batteryExemptionGranted) {
                    "Granted · Stats stay fresh in background"
                } else {
                    "Stops your phone killing tracking overnight"
                },
                granted = readiness.batteryExemptionGranted,
                actionLabel = "Request",
                onAction = onRequestBatteryExemption,
                rowTestTag = "readiness_battery_row",
                actionTestTag = "readiness_request_battery_button"
            )
            Divider()
            // --- Row 3: pipeline freshness --------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("readiness_freshness_row")
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Last updated",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = T.text,
                            fontSize = 14.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ScreenTimeReadiness.formatLastUpdated(
                            readiness.lastComputedAtMs,
                            nowMs
                        ) + if (stale && readiness.usageAccessGranted) {
                            " · tap to refresh"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Normal,
                            color = T.muted,
                            fontSize = 12.sp
                        ).copy(lineHeight = 17.4.sp)
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("readiness_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh screen time",
                        tint = T.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(focivoTokens().line)
    )
}

/**
 * One readiness row: granted -> lime check mark; missing -> accent pill
 * button that routes to the grant/request intent.
 */
@Composable
private fun ReadinessRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    rowTestTag: String,
    actionTestTag: String
) {
    val T = focivoTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(rowTestTag)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = T.text,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = T.muted,
                    fontSize = 12.sp
                ).copy(lineHeight = 17.4.sp)
            )
        }
        if (granted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = T.accent,
                modifier = Modifier
                    .size(24.dp)
                    .testTag("$rowTestTag.granted_check")
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(T.accent)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag(actionTestTag),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = T.bg,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}
