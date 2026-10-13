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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BalooBrandFontFamily
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.StrictModeManager
import com.example.util.StudyNotificationBlockerManager

/**
 * v1.3.1 — Deep Focus Settings sheet (Regain parity, ref-7).
 * Four toggles, all FREE (no PRO badges anywhere in Focivo):
 *  - Strict mode: ending a session early asks for confirmation (session can't end silently)
 *  - Block notifications: silence notifications during focus sessions (DND)
 *  - Uninstall protection: Focivo can't be uninstalled while a focus session runs
 *  - Block phone home screen: launcher stays covered during deep-focus sessions (opt-in)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepFocusSettingsSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isStrictModeEnabled by StrictModeManager.isStrictModeState.collectAsState()
    val uninstallProtection by StrictModeManager.uninstallProtectionState.collectAsState()
    val blockHomeScreen by StrictModeManager.blockHomeScreenState.collectAsState()
    var blockNotifications by remember {
        mutableStateOf(StudyNotificationBlockerManager.isBlockNotificationsDuringStudyEnabled(context))
    }

    val isDark = isAppInDarkTheme()
    val sheetBackground = if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7)
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val rowBg = if (isDark) Color(0xFF1D221C) else Color.White
    val rowBorder = if (isDark) Color(0x228CE000) else Color(0xFFDCE6D2)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("deep_focus_settings_sheet"),
        containerColor = sheetBackground,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Deep focus settings",
                        fontFamily = BalooBrandFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = textPrimary
                    )
                    Text(
                        text = "Extra protection while you focus. All free.",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SettingsToggleRow(
                        icon = Icons.Default.Lock,
                        title = "Strict mode",
                        subtitle = "You cannot end your session early without confirming. Ending early discards the session.",
                        checked = isStrictModeEnabled,
                        onCheckedChange = { StrictModeManager.setStrictModeEnabled(context, it) },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "deep_focus_strict_toggle"
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = Icons.Default.NotificationsOff,
                        title = "Block notifications",
                        subtitle = "Silence notifications during focus sessions so pings don't pull you out.",
                        checked = blockNotifications,
                        onCheckedChange = { enabled ->
                            if (enabled && !StudyNotificationBlockerManager.hasDndPermission(context)) {
                                // Ask for Do Not Disturb access first; keep the toggle off until granted.
                                StudyNotificationBlockerManager.openDndSettings(context)
                            } else {
                                StudyNotificationBlockerManager.setBlockNotificationsDuringStudyEnabled(context, enabled)
                                blockNotifications = enabled
                            }
                        },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "deep_focus_notifications_toggle"
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = Icons.Default.Security,
                        title = "Uninstall protection",
                        subtitle = "Focivo cannot be uninstalled while a focus session is running.",
                        checked = uninstallProtection,
                        onCheckedChange = { StrictModeManager.setUninstallProtectionEnabled(context, it) },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "deep_focus_uninstall_toggle"
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = Icons.Default.Home,
                        title = "Block phone home screen",
                        subtitle = "During deep focus, going to the home screen shows the focus overlay instead. Best-effort on some phones.",
                        checked = blockHomeScreen,
                        onCheckedChange = { StrictModeManager.setBlockHomeScreenEnabled(context, it) },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "deep_focus_homescreen_toggle"
                    )
                }
                item { Spacer(modifier = Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    rowBg: Color,
    rowBorder: Color,
    testTag: String
) {
    val isDark = isAppInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .border(1.dp, rowBorder, RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (checked) RegainLimePrimary.copy(alpha = 0.18f)
                    else if (isDark) Color(0xFF2C3238) else Color(0xFFF3F4F6)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) (if (isDark) RegainLimePrimary else RegainLimeDeepText)
                else textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = PoppinsFontFamily,
                fontSize = 11.5.sp,
                color = textSecondary,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = RegainLimePrimary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = if (isDark) Color(0xFF3A3F3A) else Color(0xFFE0E0E0)
            )
        )
    }
}
