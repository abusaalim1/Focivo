package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.Context
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import com.example.service.FocivoDeviceAdminReceiver
import com.example.util.TimerThemeManager
import com.example.util.SessionNotificationDigestManager
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
    modifier: Modifier = Modifier,
    // v1.4.0-round2: test hook — screenshot tests open the picker directly
    initialShowThemePicker: Boolean = false
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isStrictModeEnabled by StrictModeManager.isStrictModeState.collectAsState()
    val uninstallProtection by StrictModeManager.uninstallProtectionState.collectAsState()
    val blockHomeScreen by StrictModeManager.blockHomeScreenState.collectAsState()
    var blockNotifications by remember {
        mutableStateOf(StudyNotificationBlockerManager.isBlockNotificationsDuringStudyEnabled(context))
    }

    val isDarkMode = isAppInDarkTheme()
    val isDark = isDarkMode
    val sheetBackground = if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7)
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val rowBg = if (isDark) Color(0xFF1D221C) else Color.White
    val rowBorder = if (isDark) Color(0x228CE000) else Color(0xFFDCE6D2)

    var showDeviceAdminPrompt by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(initialShowThemePicker) }
    val selectedTheme by TimerThemeManager.selectedTheme.collectAsState()
    var showDigestSheet by remember { mutableStateOf(false) }
    val digestList by SessionNotificationDigestManager.digestList.collectAsState()

    if (showDigestSheet) {
        SessionNotificationDigestSheet(onDismiss = { showDigestSheet = false })
    }

    if (showDeviceAdminPrompt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeviceAdminPrompt = false },
            title = {
                Text(
                    text = "Activate Uninstall Protection?",
                    fontFamily = BalooBrandFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "Strict Mode is on! You can optionally activate Device Admin to ensure Focivo cannot be uninstalled or bypassed while a strict study session is running.",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = textSecondary
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeviceAdminPrompt = false
                        try {
                            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(
                                    android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                    FocivoDeviceAdminReceiver.getComponentName(context)
                                )
                                putExtra(
                                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "Prevents accidental uninstallation while a strict focus session is in progress."
                                )
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("DeepFocus", "Could not launch device admin request: ${e.message}")
                        }
                    }
                ) {
                    Text(
                        text = "Enable Admin",
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) RegainLimePrimary else RegainLimeDeepText
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeviceAdminPrompt = false }
                ) {
                    Text("Skip", color = textSecondary)
                }
            },
            containerColor = rowBg
        )
    }

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
                        onCheckedChange = { enabled ->
                            StrictModeManager.setStrictModeEnabled(context, enabled)
                            if (enabled) {
                                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                                val adminComponent = FocivoDeviceAdminReceiver.getComponentName(context)
                                val isAdminActive = dpm?.isAdminActive(adminComponent) == true
                                if (!isAdminActive) {
                                    showDeviceAdminPrompt = true
                                }
                            }
                        },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "deep_focus_strict_toggle"
                    )
                }
                item {
                    SettingsActionRow(
                        icon = Icons.Default.Palette,
                        title = "Timer theme",
                        subtitle = selectedTheme.name + " — full-screen backdrop, mascot and glow",
                        onClick = { showThemePicker = !showThemePicker },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "deep_focus_theme_row"
                    )
                }
                if (showThemePicker) {
                    item {
                        // Round-3: visual theme cards — each theme shows its real
                        // backdrop artwork so users SEE it is a full-screen theme.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TimerThemeManager.THEMES.forEach { theme ->
                                val isSelected = theme.id == selectedTheme.id
                                val cardBackdrop = remember(theme.id) {
                                    resolveThemeBackdropRes(context, theme)
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        TimerThemeManager.selectTheme(context, theme.id)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(104.dp)
                                            .height(138.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) theme.accent else rowBorder,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                    ) {
                                        if (cardBackdrop != null) {
                                            Image(
                                                painter = painterResource(id = cardBackdrop),
                                                contentDescription = theme.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            // Aurora (code-drawn) or an asset not packaged yet:
                                            // accent-tinted gradient placeholder.
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = listOf(
                                                                theme.accent,
                                                                Color(0xFF0E1512)
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                        // Name strip on a scrim so it reads on any artwork.
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(
                                                            Color.Transparent,
                                                            Color(0xCC000000)
                                                        )
                                                    )
                                                )
                                                .padding(top = 18.dp, bottom = 8.dp),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Text(
                                                text = theme.name,
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = Color.White
                                            )
                                        }
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(theme.accent),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF061505),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SettingsActionRow(
                        icon = Icons.Default.NotificationsOff,
                        title = "Suppressed notifications digest",
                        subtitle = if (digestList.isEmpty()) "No notifications held yet in this session" else "${digestList.size} notifications held during focus sessions",
                        onClick = { showDigestSheet = true },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        badgeCount = digestList.size,
                        testTag = "deep_focus_digest_row"
                    )
                }
                item {
                    SettingsToggleRow(
                        icon = Icons.Default.NotificationsOff,
                        title = "Block notifications",
                        subtitle = "Silence notifications during focus sessions so pings don't pull you out. Needs notification access + Do Not Disturb.",
                        checked = blockNotifications,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                StudyNotificationBlockerManager.setBlockNotificationsDuringStudyEnabled(context, false)
                                blockNotifications = false
                            } else if (!StudyNotificationBlockerManager.hasNotificationListenerAccess(context)) {
                                // v1.4.0-round2: without listener access the suppressor
                                // never runs — send the user to grant it first.
                                StudyNotificationBlockerManager.openNotificationListenerSettings(context)
                            } else if (!StudyNotificationBlockerManager.hasDndPermission(context)) {
                                // Ask for Do Not Disturb access next; keep the toggle off until granted.
                                StudyNotificationBlockerManager.openDndSettings(context)
                            } else {
                                StudyNotificationBlockerManager.setBlockNotificationsDuringStudyEnabled(context, true)
                                blockNotifications = true
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

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    rowBg: Color,
    rowBorder: Color,
    badgeCount: Int = 0,
    testTag: String
) {
    val isDark = isAppInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .border(1.dp, rowBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (badgeCount > 0) RegainLimePrimary.copy(alpha = 0.18f)
                    else if (isDark) Color(0xFF2C3238) else Color(0xFFF3F4F6)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (badgeCount > 0) (if (isDark) RegainLimePrimary else RegainLimeDeepText) else textSecondary,
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
        if (badgeCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(RegainLimePrimary)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

