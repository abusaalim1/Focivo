package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusShieldPermissionSheet(
    onDismiss: () -> Unit,
    onAllGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasUsagePermission by remember {
        mutableStateOf(FocusShieldPermissions.hasUsageStatsPermission(context))
    }
    var hasOverlayPermission by remember {
        mutableStateOf(FocusShieldPermissions.hasOverlayPermission(context))
    }
    var hasAccessibilityPermission by remember {
        mutableStateOf(FocusShieldPermissions.hasAccessibilityPermission(context))
    }
    // v1.1.3: Battery optimization exemption — keeps the guard + timer alive
    // on Xiaomi/Oppo/Vivo/Samsung which aggressively kill background services.
    var hasBatteryExemption by remember {
        mutableStateOf(com.example.util.PermissionUtils.isBatteryOptimizationDisabled(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = FocusShieldPermissions.hasUsageStatsPermission(context)
                hasOverlayPermission = FocusShieldPermissions.hasOverlayPermission(context)
                hasAccessibilityPermission = FocusShieldPermissions.hasAccessibilityPermission(context)
                hasBatteryExemption = com.example.util.PermissionUtils.isBatteryOptimizationDisabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isAllGranted = hasUsagePermission && hasOverlayPermission && hasAccessibilityPermission && hasBatteryExemption

    // v1.2.3 design-unification — owner clean-redesign language:
    // #0A0A0B sheet, #B6F13A lime, Poppins body + Instrument Serif italic
    // for the headline (same icon-tile header as the Focus Shield page).
    val sheetBackground = focivoTokens().bg
    val accentLime = focivoTokens().accent

    val serifItalic = SerifItalicFontFamily

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBackground,
        dragHandle = null,
        modifier = modifier.testTag("focus_shield_permission_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header icon tile — same language as the Focus Shield page.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x1EB6F13A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = accentLime,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Required Shield Permissions",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = serifItalic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            CivoChatBubble(
                text = "Grant these permissions so Focivo can detect distracting apps, block YouTube Shorts & Reels, enforce Strict Study Mode, and keep the guard alive in the background!",
                avatarSize = 42.dp,
                typingSpeedMs = 20L
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Permission 1: Usage Access
            PermissionItemCard(
                title = "1. Usage Access (App Detection)",
                description = if (hasUsagePermission) "Granted · Detects when distracting apps open" else "Allows Focivo to detect when Instagram, Games or distracting apps open.",
                isGranted = hasUsagePermission,
                icon = Icons.Default.Security,
                onClick = {
                    if (!hasUsagePermission) {
                        FocusShieldPermissions.openUsageAccessSettings(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Permission 2: Display Over Other Apps (Overlay)
            PermissionItemCard(
                title = "2. Display Over Apps (Lock Screen)",
                description = if (hasOverlayPermission) "Granted · Displays distraction lock overlay" else "Allows Focivo to draw the focus blocker overlay when apps open.",
                isGranted = hasOverlayPermission,
                icon = Icons.Default.Layers,
                onClick = {
                    if (!hasOverlayPermission) {
                        FocusShieldPermissions.openOverlaySettings(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Permission 3: Accessibility Service (Shorts & Strict Mode)
            PermissionItemCard(
                title = "3. Accessibility Service (Shorts & Strict Guard)",
                description = if (hasAccessibilityPermission) "Granted · YouTube Shorts & Strict Mode active" else "Essential to block YouTube Shorts (allowing lectures) and prevent uninstallation during study.",
                isGranted = hasAccessibilityPermission,
                icon = Icons.Default.AccessibilityNew,
                onClick = {
                    if (!hasAccessibilityPermission) {
                        FocusShieldPermissions.openAccessibilitySettings(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Permission 4: Battery Optimization Exemption (v1.1.3)
            PermissionItemCard(
                title = "4. Battery Optimization OFF",
                description = if (hasBatteryExemption) "Granted · Guard & timer stay alive in background" else "Stops your phone from killing the AI Guard & timer in the background.",
                isGranted = hasBatteryExemption,
                icon = Icons.Default.BatteryChargingFull,
                onClick = {
                    if (!hasBatteryExemption) {
                        com.example.util.PermissionUtils.openBatteryOptimizationSettings(context)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            GlassButton(
                text = if (isAllGranted) "Activate Focivo Shield" else "Grant Missing Permissions",
                onClick = {
                    if (isAllGranted) {
                        onAllGranted()
                    } else if (!hasUsagePermission) {
                        FocusShieldPermissions.openUsageAccessSettings(context)
                    } else if (!hasOverlayPermission) {
                        FocusShieldPermissions.openOverlaySettings(context)
                    } else if (!hasBatteryExemption) {
                        com.example.util.PermissionUtils.openBatteryOptimizationSettings(context)
                    } else {
                        FocusShieldPermissions.openAccessibilitySettings(context)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.5.sp,
                horizontalPadding = 18.dp,
                verticalPadding = 14.dp,
                minHeight = 52.dp,
                isPrimary = true,
                leadingIcon = {
                    Icon(
                        imageVector = if (isAllGranted) Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Cancel",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    // v1.2.3 design-unification: owner clean-redesign lime.
    val accentLime = focivoTokens().accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x18FFFFFF))
            .border(
                1.dp,
                if (isGranted) accentLime.copy(alpha = 0.5f) else Color(0x22FFFFFF),
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isGranted) accentLime.copy(alpha = 0.22f) else Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) accentLime else focivoTokens().muted,
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
                    color = if (isGranted) accentLime else Color.White.copy(alpha = 0.65f),
                    fontSize = 11.5.sp
                )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = accentLime,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentLime)
                    .clickable { onClick() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Enable",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NearBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = NearBlack,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
