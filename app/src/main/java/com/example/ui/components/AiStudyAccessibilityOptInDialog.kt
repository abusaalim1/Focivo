package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimePrimary

@Composable
fun AiStudyAccessibilityOptInDialog(
    onConfirmOptIn: () -> Unit,
    onDismiss: () -> Unit
) {
    val darkSurface = Color(0xFF141814)
    val cardBg = Color(0xFF1D241D)
    val limeAccent = RegainLimePrimary
    val textPrimary = Color(0xFFF0F4ED)
    val textSecondary = Color(0xFFA0A89E)
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(26.dp))
                .border(1.dp, limeAccent.copy(alpha = 0.35f), RoundedCornerShape(26.dp)),
            color = darkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Badge
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(limeAccent.copy(alpha = 0.2f))
                        .border(1.dp, limeAccent.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = limeAccent,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "AI Study Guard Permission",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Mandatory Disclosure Before Service Enablement",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content Box
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item 1: What it does
                    DisclosureItemCard(
                        icon = Icons.Default.CheckCircle,
                        title = "1. What It Does",
                        description = "Reads on-screen text ONLY inside Claude and ChatGPT (and AI search) to check if your questions are study-related during active focus sessions. No other app is monitored.",
                        iconTint = limeAccent,
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )

                    // Item 2: What it doesn't do
                    DisclosureItemCard(
                        icon = Icons.Default.Lock,
                        title = "2. What It Doesn't Do",
                        description = "No other app is ever monitored. No screen recording or screenshots. All checking happens 100% on-device — nothing is ever uploaded or sent to any server.",
                        iconTint = Color(0xFF64B5F6),
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )

                    // Item 3: Battery
                    DisclosureItemCard(
                        icon = Icons.Default.BatteryAlert,
                        title = "3. Battery Usage",
                        description = "May slightly increase battery usage during active study monitoring. You can turn it off anytime with zero effect on any other Focivo feature.",
                        iconTint = Color(0xFFFFB74D),
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )

                    // Item 4: If Declined
                    DisclosureItemCard(
                        icon = Icons.Default.DoNotDisturbOn,
                        title = "4. If Declined",
                        description = "AI Study Guard won't detect non-study AI chats, but everything else (Focus Shield app blocker, study schedules, alarms, focus timer) still works normally.",
                        iconTint = Color(0xFFE57373),
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )

                    // Item 5: How to Revoke
                    DisclosureItemCard(
                        icon = Icons.Default.SettingsBackupRestore,
                        title = "5. How to Revoke",
                        description = "Turn off anytime in Android Settings → Accessibility → Installed Apps → Focivo, or directly from Focivo's own Settings menu.",
                        iconTint = Color(0xFFBA68C8),
                        cardBg = cardBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Button: Enable AI Study Guard
                Button(
                    onClick = onConfirmOptIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = limeAccent,
                        contentColor = NearBlack
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable AI Study Guard",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary Button: Not Now
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textSecondary
                    )
                ) {
                    Text(
                        text = "Not now",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun DisclosureItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    iconTint: Color,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                )
            }
        }
    }
}
