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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.FocuslyRepository
import com.example.util.TimerTheme
import com.example.util.TimerThemeManager
import com.example.util.SessionNotificationDigestManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.focivoTokens
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
    val T = focivoTokens()
    val sheetBackground = T.bg
    val textPrimary = T.text
    val textSecondary = T.muted
    val rowBg = T.surface
    val rowBorder = T.line

    var showDeviceAdminPrompt by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(initialShowThemePicker) }
    val selectedTheme by TimerThemeManager.selectedTheme.collectAsState()
    // Round-4: Focus Points balance (real currency, 1 pt per focused minute)
    // drives theme locks. Guests / fresh accounts see 0 -> only free themes.
    val prefsFlow = remember(context) { FocuslyRepository.getInstance(context).userPreferences }
    val prefs by prefsFlow.collectAsState(initial = null)
    val focusPoints = prefs?.focusPoints ?: 0
    // v1.2.4: owner admin account unlocks every theme — single choke point.
    val isAdmin = remember(context) { com.example.util.ContentLocks.isAdminUnlocked(context) }
    var lockedDialogTheme by remember { mutableStateOf<TimerTheme?>(null) }

    // Round-4: if the persisted theme is no longer affordable (rules changed),
    // fall back to Aurora gracefully instead of rendering a locked theme.
    LaunchedEffect(focusPoints, selectedTheme.id) {
        TimerThemeManager.validateSelection(context, focusPoints)
    }

    var showDigestSheet by remember { mutableStateOf(false) }
    if (showDigestSheet) {
        SessionNotificationDigestSheet(onDismiss = { showDigestSheet = false })
    }
    val digestList by SessionNotificationDigestManager.digestList.collectAsState()

    // Round-4: locked-theme explainer dialog (shared with the study hub).
    lockedDialogTheme?.let { lockedTheme ->
        LockedThemeDialog(
            lockedTheme = lockedTheme,
            focusPoints = focusPoints,
            onDismiss = { lockedDialogTheme = null }
        )
    }

    if (showDeviceAdminPrompt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeviceAdminPrompt = false },
            title = {
                Text(
                    text = "Activate Uninstall Protection?",
                    fontFamily = PoppinsFontFamily,
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
                        color = T.accentText
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
            SheetDragHandle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Deep focus settings",
                        fontFamily = PoppinsFontFamily,
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
                        icon3dRes = com.example.R.drawable.ic3d_lock,
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
                    // v1.4.2: the theme row shows the selected theme's own backdrop
                    // art as its icon tile. Aurora is code-drawn (no asset), so it
                    // gets a live aurora-gradient thumbnail instead of a glyph.
                    val themeTileRes = resolveThemeBackdropRes(context, selectedTheme)
                    SettingsActionRow(
                        icon = Icons.Default.Palette,
                        icon3dRes = themeTileRes,
                        iconCustom = if (themeTileRes == null) {
                            val ambientKind = selectedTheme.ambientKind
                            if (ambientKind != null) {
                                { AmbientBackdrop(kind = ambientKind, modifier = Modifier.fillMaxSize()) }
                            } else {
                                { AuroraBackground(modifier = Modifier.fillMaxSize()) }
                            }
                        } else null,
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
                        // v1.5.0: shared picker (also used by the study hub) —
                        // same points-lock behavior, lock + price, tap explains.
                        TimerThemePicker(
                            focusPoints = focusPoints,
                            selectedThemeId = selectedTheme.id,
                            textPrimary = textPrimary,
                            cardBorder = rowBorder,
                            onThemeTap = { theme, isLocked ->
                                if (isLocked) {
                                    lockedDialogTheme = theme
                                } else {
                                    TimerThemeManager.trySelectTheme(context, theme.id, focusPoints)
                                }
                            }
                        )
                    }
                }
                item {
                    SettingsActionRow(
                        icon = Icons.Default.NotificationsOff,
                        icon3dRes = com.example.R.drawable.ic3d_bell,
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
                        icon3dRes = com.example.R.drawable.ic3d_bell,
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
                        icon3dRes = com.example.R.drawable.ic3d_shield,
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
                        icon3dRes = com.example.R.drawable.ic3d_home,
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
    testTag: String,
    // Final phase: 3D feature icon (drawable tile); falls back to the vector
    // circle when null.
    icon3dRes: Int? = null,
    // v1.4.2: arbitrary composable art for the tile (e.g. aurora thumbnail).
    iconCustom: (@Composable () -> Unit)? = null
) {
    val isDark = isAppInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .border(1.dp, rowBorder, RoundedCornerShape(18.dp))
            .pressFeedback()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Final phase: 3D feature tile when a drawable is supplied, else the
        // legacy vector circle.
        FeatureIcon(
            drawableRes = icon3dRes,
            imageVector = if (icon3dRes == null && iconCustom == null) icon else null,
            contentDescription = null,
            size = 44.dp,
            customContent = iconCustom,
            vectorCircleColor = if (checked) RegainLimePrimary.copy(alpha = 0.18f)
            else focivoTokens().surface2,
            vectorTint = if (checked) focivoTokens().accentText
            else textSecondary
        )
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
                uncheckedTrackColor = focivoTokens().trackOff
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
    testTag: String,
    // Final phase: 3D feature icon (drawable tile); falls back to the vector
    // circle when null.
    icon3dRes: Int? = null,
    // v1.4.2: arbitrary composable art for the tile (e.g. aurora thumbnail).
    iconCustom: (@Composable () -> Unit)? = null
) {
    val isDark = isAppInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .border(1.dp, rowBorder, RoundedCornerShape(18.dp))
            .pressFeedback()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Final phase: 3D feature tile when a drawable is supplied, else the
        // legacy vector circle.
        FeatureIcon(
            drawableRes = icon3dRes,
            imageVector = if (icon3dRes == null && iconCustom == null) icon else null,
            contentDescription = null,
            size = 44.dp,
            customContent = iconCustom,
            vectorCircleColor = if (badgeCount > 0) RegainLimePrimary.copy(alpha = 0.18f)
            else focivoTokens().surface2,
            vectorTint = if (badgeCount > 0) focivoTokens().accentText else textSecondary
        )
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


/**
 * v1.5.0 — shared locked-theme explainer. Same points-lock rules as the
 * sheet: tapping a locked theme explains how many more Focus Points are
 * needed (1 pt per focused minute). Used by DeepFocusSettingsSheet and the
 * study hub.
 */
@Composable
fun LockedThemeDialog(
    lockedTheme: TimerTheme,
    focusPoints: Int,
    onDismiss: () -> Unit
) {
    val T = focivoTokens()
    val isDark = T.isDark
    val textPrimary = T.text
    val textSecondary = T.muted
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Locked theme",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = textPrimary
            )
        },
        text = {
            Text(
                text = "Collect ${(lockedTheme.unlockPoints - focusPoints).coerceAtLeast(1)} more Focus Points to unlock ${lockedTheme.name}. You earn 1 point per focused minute.",
                fontFamily = PoppinsFontFamily,
                fontSize = 13.sp,
                color = textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Got it",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RegainLimePrimary
                )
            }
        }
    )
}

/**
 * v1.5.0 — shared timer-theme picker grid. Round-4 behavior preserved:
 * visual theme cards with real backdrop artwork, locked cards dimmed with
 * a lock badge + price, tapping a locked card explains how to earn it
 * (via [onThemeTap] with isLocked = true). Used by DeepFocusSettingsSheet
 * and the study hub.
 */
@Composable
fun TimerThemePicker(
    focusPoints: Int,
    selectedThemeId: String,
    onThemeTap: (theme: TimerTheme, isLocked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textPrimary: Color,
    cardBorder: Color
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        // Round-4: Focus Points header — the real earned balance
        // (1 pt per focused minute) that unlocks themes.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Your Focus Points: $focusPoints",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = textPrimary
            )
        }
        // Round-3: visual theme cards — each theme shows its real
        // backdrop artwork so users SEE it is a full-screen theme.
        // Round-4: locked cards show a lock badge + price, and
        // tapping one explains how to earn it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TimerThemeManager.THEMES.forEach { theme ->
                val isSelected = theme.id == selectedThemeId
                val isLocked = !theme.isUnlocked(focusPoints, isAdmin)
                val cardBackdrop = remember(theme.id) {
                    resolveThemeBackdropRes(context, theme)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        onThemeTap(theme, isLocked)
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .width(104.dp)
                            .height(138.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) theme.accent else cardBorder,
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
                        } else if (theme.ambientKind != null) {
                            // v1.5.0 Ambient themes: live mini preview of the
                            // code-drawn canvas, clipped to the card.
                            AmbientBackdrop(
                                kind = theme.ambientKind,
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
                        if (isLocked) {
                            // Dim the artwork so locked cards read as unavailable.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xAA0B0F0B))
                            )
                        }
                        if (isLocked) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC1A221B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        // v1.2.1: the theme's mascot/logo on every picker card
                        // (owner: theme logo was not showing on device).
                        // Same art as the themed timer (theme.mascotPose).
                        Box(
                            modifier = Modifier.align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            RegainMascotView(
                                pose = theme.mascotPose,
                                size = 64.dp,
                                animate = false,
                                modifier = Modifier.testTag("theme_card_mascot_${theme.id}")
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
                                .padding(top = 16.dp, bottom = 8.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = theme.name,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                                if (isLocked) {
                                    Text(
                                        text = "${theme.unlockPoints} pts",
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 9.sp,
                                        color = Color(0xFFFFD54F)
                                    )
                                }
                            }
                        }
                        if (isSelected && !isLocked) {
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
