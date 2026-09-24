package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LavenderSoft
import com.example.ui.theme.VioletAccent
import com.example.ui.components.CivoChatBubble
import com.example.util.PermissionUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationAlarmPermissionSheet(
    onDismiss: () -> Unit,
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasNotifPermission by remember {
        mutableStateOf(PermissionUtils.hasNotificationPermission(context))
    }
    var hasExactAlarm by remember {
        mutableStateOf(PermissionUtils.hasExactAlarmPermission(context))
    }

    fun dismissSheet(action: () -> Unit) {
        coroutineScope.launch {
            try {
                sheetState.hide()
            } catch (_: Exception) {}
        }.invokeOnCompletion {
            action()
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotifPermission = isGranted
        if (isGranted && !PermissionUtils.hasExactAlarmPermission(context)) {
            PermissionUtils.openExactAlarmSettings(context)
        }
        dismissSheet(onPermissionGranted)
    }

    val sheetBackground = Color(0xFF100D1C)
    val accentViolet = VioletAccent
    val accentGreen = Color(0xFF69F0AE)

    ModalBottomSheet(
        onDismissRequest = { dismissSheet(onDismiss) },
        sheetState = sheetState,
        containerColor = sheetBackground,
        dragHandle = null,
        modifier = modifier.testTag("notification_alarm_permission_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Close "X" Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { dismissSheet(onDismiss) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .testTag("close_permission_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Glowing Alarm/Bell Icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF7C4DFF), Color(0xFFFF5252))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            CivoChatBubble(
                text = "Almost there! Allow notifications and alarms so I can wake you up for breaks and study sprints.",
                avatarSize = 48.dp,
                typingSpeedMs = 30L
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Rationale Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PermissionFeatureRow(
                    icon = Icons.Default.Alarm,
                    iconTint = Color(0xFFFF5252),
                    title = "Precise Focus & Break Alarms",
                    description = "Plays your custom Zen Bell loudly when a 25m or 50m sprint finishes, even if your phone screen is turned off."
                )

                PermissionFeatureRow(
                    icon = Icons.Default.Notifications,
                    iconTint = accentViolet,
                    title = "Live Background Countdown",
                    description = "Keeps remaining session time visible in your notification tray so you never lose track of deep work."
                )

                PermissionFeatureRow(
                    icon = Icons.Default.Vibration,
                    iconTint = accentGreen,
                    title = "Haptic Transition Signals",
                    description = "Subtle tactile pulses notify you when moving from deep focus to relaxation breaks."
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Primary Action Button
            GlassButton(
                text = if (hasNotifPermission) "Alarms & Notifications Enabled" else "Allow Alarms & Notifications",
                onClick = {
                    if (hasNotifPermission) {
                        dismissSheet(onPermissionGranted)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } catch (_: Exception) {
                            dismissSheet(onPermissionGranted)
                        }
                        // Also dismiss sheet so it doesn't stay stuck on UI
                        dismissSheet(onPermissionGranted)
                    } else {
                        if (!PermissionUtils.hasExactAlarmPermission(context)) {
                            PermissionUtils.openExactAlarmSettings(context)
                        }
                        dismissSheet(onPermissionGranted)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("allow_alarms_notifications_button"),
                fontSize = 14.sp,
                horizontalPadding = 18.dp,
                verticalPadding = 14.dp,
                minHeight = 52.dp,
                isPrimary = true,
                leadingIcon = {
                    Icon(
                        imageVector = if (hasNotifPermission) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary "Maybe Later" Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x22FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                    .clickable { dismissSheet(onDismiss) }
                    .testTag("maybe_later_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Maybe Later",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun PermissionFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x18FFFFFF))
            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            )
        }
    }
}
