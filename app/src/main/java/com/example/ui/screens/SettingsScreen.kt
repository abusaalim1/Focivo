package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.FeatureIcon
import com.example.R
import com.example.BuildConfig
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.SegmentedControl
import com.example.ui.theme.backdropContentColor
import com.example.ui.theme.backdropTitleShadow
import com.example.ui.theme.isAppInDarkTheme
import com.example.ui.theme.SecondaryTextLight
import com.example.util.TimerThemeManager
import com.example.util.StudyNotificationBlockerManager
import androidx.compose.material.icons.filled.NotificationsOff

import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens

// Owner's Full App Redesign token system (dark + light aware).
private object SettingsT {
    val Lime @Composable get() = focivoTokens().accent
    val TrackOff @Composable get() = focivoTokens().trackOff
    val ThumbOff @Composable get() = focivoTokens().thumbOff
    val Bg @Composable get() = focivoTokens().bg
    val Surface @Composable get() = focivoTokens().surface
    val Line @Composable get() = focivoTokens().line
    val Text @Composable get() = focivoTokens().text
    val Muted @Composable get() = focivoTokens().muted
    val SerifItalic @Composable get() = SerifItalicFontFamily
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userPreferences: UserPreferencesEntity?,
    onBack: () -> Unit,
    onUpdateTheme: (String) -> Unit,
    onUpdateDailyGoal: (Int) -> Unit,
    onResetDemoData: () -> Unit,
    onClearAllData: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenAlarmStudio: () -> Unit = {},
    onOpenShieldHub: () -> Unit = {},
    onOpenAiGuardHub: () -> Unit = {},
    onUpdateHaptics: (Boolean) -> Unit = {},
    onUpdateChimes: (Boolean) -> Unit = {},
    onLogout: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    isCheckingForUpdates: Boolean = false,
    manualUpdateMessage: String? = null,
    // v1.3.3 (item 8b): global daily screen-time budget (0 = off).
    screenTimeBudgetMinutes: Int = 0,
    onUpdateScreenTimeBudget: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val context = LocalContext.current

    // v1.4.2: explicit theme-derived text colors. Default (unspecified) text
    // renders dark on this screen's dark cards, so every title/subtitle gets
    // an explicit color from the proven isDark pattern (same convention as
    // DeepFocusSettingsSheet) instead of relying on MaterialTheme defaults.
    val textPrimary = SettingsT.Text
    val textSecondary = SettingsT.Muted
    // Full App Redesign: flat token background; title uses token text.

    var notificationsEnabled by remember { mutableStateOf(userPreferences?.notificationsEnabled ?: true) }
    var hapticsEnabled by remember { mutableStateOf(userPreferences?.hapticsEnabled ?: true) }
    var blockStudyNotifications by remember { mutableStateOf(StudyNotificationBlockerManager.isBlockNotificationsDuringStudyEnabled(context)) }
    var hasDndPermission by remember { mutableStateOf(StudyNotificationBlockerManager.hasDndPermission(context)) }
    var showPrivacyPolicySheet by remember { mutableStateOf(false) }
    // v1.3.3 (item 8b): global screen-time budget sheet.
    var showScreenTimeBudgetSheet by remember { mutableStateOf(false) }
    // v1.3.4 (item 9): merged-entry sheets — one entry opens one premium
    // sheet (the Block-sheet pattern). Every control from the old scattered
    // cards lives inside its merged sheet; nothing was removed.
    var showDailyTargetsSheet by remember { mutableStateOf(false) }
    var showSessionFeedbackSheet by remember { mutableStateOf(false) }
    var showStudyNotificationsSheet by remember { mutableStateOf(false) }
    var liveBudgetMinutes by remember(screenTimeBudgetMinutes) {
        mutableStateOf(screenTimeBudgetMinutes)
    }

    // Refresh permission states when returning from system settings
    val settingsLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(settingsLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasDndPermission = StudyNotificationBlockerManager.hasDndPermission(context)
            }
        }
        settingsLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { settingsLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // v1.3.3 — Focus Shield grant row: live state, refreshed on ON_RESUME so
    // grants made in system Settings show instantly when the user returns.
    var shieldPermissionsOk by remember {
        mutableStateOf(com.example.util.PermissionStates.hasCoreShieldPermissions(context))
    }
    com.example.util.RefreshOnResume {
        shieldPermissionsOk = com.example.util.PermissionStates.hasCoreShieldPermissions(context)
    }

    val themeOptions = listOf("System", "Light", "Dark")
    val currentThemeIndex = when (userPreferences?.themeMode) {
        "light" -> 1
        "dark" -> 2
        else -> 0
    }


    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SettingsT.Bg)
            .padding(horizontal = 20.dp)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SettingsT.Surface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = SettingsT.SerifItalic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = SettingsT.Text
                    )
                )
            }
        }

        // Appearance
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = SettingsT.Lime,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "APPEARANCE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.76.sp,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SegmentedControl(
                        items = themeOptions,
                        selectedIndex = currentThemeIndex,
                        onSelectIndex = { idx ->
                            val selected = when (idx) {
                                1 -> "light"
                                2 -> "dark"
                                else -> "system"
                            }
                            onUpdateTheme(selected)
                        }
                    )
                }
            }
        }

        // v1.3.4 (item 9): MERGED entry — "Daily Targets". The old
        // "Daily Focus Target" card (goal chips) and the old "Daily Screen
        // Time" card (budget row) served one feature (your daily time plan);
        // both controls live in the one premium sheet now.
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDailyTargetsSheet = true }
                    .testTag("settings_daily_targets_entry"),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FeatureIcon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Daily Targets",
                            size = 40.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Daily Targets",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Focus goal and screen-time limit",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // v1.3.4 (item 9): MERGED entries from the old "Feedback &
        // Notifications" card. "Haptic Feedback" + "Local Session Chimes"
        // were two entries for one feature (session feedback) -> one entry,
        // one sheet. "Block Notifications in Study" (+ its DND/test-silence
        // action) is its own feature -> its own entry, one sheet.
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSessionFeedbackSheet = true }
                    .testTag("settings_session_feedback_entry"),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FeatureIcon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Session Feedback",
                            size = 40.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Session Feedback",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Haptics and chimes during sessions",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showStudyNotificationsSheet = true }
                    .testTag("settings_study_notifications_entry"),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FeatureIcon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "Study Notifications",
                            size = 40.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Study Notifications",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (blockStudyNotifications) "Blocking on during study sessions" else "Block app alerts while you study",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Sound & Alarm Studio
        item {
            val ringtoneName = userPreferences?.alarmRingtone ?: "Zen Bell"
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAlarmStudio() },
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FeatureIcon(
                            drawableRes = R.drawable.ic3d_alarm,
                            contentDescription = "Alarm Studio",
                            size = 40.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Alarm Studio",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "10 Ringtones · Active: $ringtoneName",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Focus Shield (App Blocker) Hub — v1.3.3: shield grant state is
        // hoisted and re-read from the REAL system grants on every ON_RESUME,
        // so a permission granted in Settings updates the row on return.
        item {
            val isShieldEnabled = (userPreferences?.isAppBlockerEnabled ?: false) && shieldPermissionsOk
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenShieldHub() },
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FeatureIcon(
                            drawableRes = R.drawable.ic3d_shield,
                            contentDescription = "Focus Shield",
                            size = 40.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Focus Shield & App Blocker",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isShieldEnabled) "Armed · Distractions Blocked During Study" else if (!shieldPermissionsOk) "Permissions Needed · Tap to setup shield" else "Configured · Tap to manage apps & timer",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // AI Study Guard Hub Link
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAiGuardHub() },
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FeatureIcon(
                            drawableRes = R.drawable.ic3d_lock,
                            contentDescription = "AI Study Guard",
                            size = 40.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "AI Study Guard",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Filters, locks, warnings & history",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // v1.3.4 (item 9): the old "AI Study Guard Disclosure" entry is
        // merged into "AI Study Guard" above — same feature, one entry. The
        // plain-language disclosure dialog still opens from inside the AI
        // Study Guard hub (permission rows), so nothing was removed.

        // Data Management & Diagnostics
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DATA MANAGEMENT (OFFLINE-FIRST)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.76.sp,
                            fontSize = 11.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassButton(
                            text = "Reset Demo Data",
                            onClick = {
                                onResetDemoData()
                                Toast.makeText(context, "Demo tasks and sessions restored", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        )

                        GlassButton(
                            text = "Replay Intro",
                            onClick = onReplayOnboarding,
                            modifier = Modifier.weight(1f),
                            isPrimary = false
                        )
                    }

                    GlassButton(
                        text = "Clear All Records",
                        onClick = {
                            onClearAllData()
                            Toast.makeText(context, "All session data cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isPrimary = false
                    )

                    GlassButton(
                        text = "Sign Out of Local Account",
                        onClick = {
                            onLogout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isPrimary = false
                    )
                }
            }
        }

        // About Focusly
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = SettingsT.Lime,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ABOUT FOCIVO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.76.sp,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Focivo v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textPrimary
                        )
                    )
                    Text(
                        text = "Crafted for students, deep workers, and thinkers. Real-time study protection, academic AI monitoring, and offline persistence.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // In-App Update Check Button
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = !isCheckingForUpdates,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("check_for_updates_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = SettingsT.Lime
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isCheckingForUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = SettingsT.Lime
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Checking for updates...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = SettingsT.Lime
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Check for Updates",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (!manualUpdateMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = manualUpdateMessage,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (manualUpdateMessage.contains("available", ignoreCase = true)) SettingsT.Lime else textSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // v1.3.4 (item 9): the old standalone "Privacy Policy"
                    // entry is merged here — same feature (about this app),
                    // one entry. The privacy sheet still opens from this row.
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showPrivacyPolicySheet = true }
                            .padding(horizontal = 4.dp, vertical = 10.dp)
                            .testTag("settings_privacy_policy_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FeatureIcon(
                                drawableRes = R.drawable.ic3d_key,
                                contentDescription = "Privacy Policy",
                                size = 38.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Privacy Policy",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        color = textPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Data practices, security & rights",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = textSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

    }

    if (showPrivacyPolicySheet) {
        com.example.ui.components.PrivacyPolicySheet(
            onDismiss = { showPrivacyPolicySheet = false }
        )
    }

    // v1.3.3 (item 8b): global screen-time budget sheet.
    if (showScreenTimeBudgetSheet) {
        com.example.ui.components.ScreenTimeBudgetSheet(
            currentBudgetMinutes = liveBudgetMinutes,
            onBudgetSelected = { minutes ->
                liveBudgetMinutes = minutes
                onUpdateScreenTimeBudget(minutes)
            },
            onDismiss = { showScreenTimeBudgetSheet = false }
        )
    }

    // v1.3.4 (item 9): merged-entry sheets. Every control from the old
    // scattered cards is reachable inside its sheet; no capability removed.
    if (showDailyTargetsSheet) {
        com.example.ui.components.DailyTargetsSheet(
            currentGoalMinutes = userPreferences?.dailyGoalMinutes ?: 360,
            onUpdateDailyGoal = onUpdateDailyGoal,
            budgetMinutes = liveBudgetMinutes,
            onOpenScreenTimeBudget = { showScreenTimeBudgetSheet = true },
            onDismiss = { showDailyTargetsSheet = false }
        )
    }

    if (showSessionFeedbackSheet) {
        com.example.ui.components.SessionFeedbackSheet(
            hapticsEnabled = hapticsEnabled,
            onUpdateHaptics = { hapticsEnabled = it; onUpdateHaptics(it) },
            chimesEnabled = notificationsEnabled,
            onUpdateChimes = { notificationsEnabled = it; onUpdateChimes(it) },
            onDismiss = { showSessionFeedbackSheet = false }
        )
    }

    if (showStudyNotificationsSheet) {
        com.example.ui.components.StudyNotificationsSheet(
            blockEnabled = blockStudyNotifications,
            onBlockChange = { enabled ->
                if (!enabled) {
                    blockStudyNotifications = false
                    StudyNotificationBlockerManager.setBlockNotificationsDuringStudyEnabled(context, false)
                    Toast.makeText(context, "Study notification blocking disabled", Toast.LENGTH_SHORT).show()
                } else if (!StudyNotificationBlockerManager.hasDndPermission(context)) {
                    // v1.1.4e: blocking is DND-only (notification listener removed).
                    StudyNotificationBlockerManager.openDndSettings(context)
                    Toast.makeText(context, "Grant Do Not Disturb access to enable blocking", Toast.LENGTH_LONG).show()
                } else {
                    blockStudyNotifications = true
                    StudyNotificationBlockerManager.setBlockNotificationsDuringStudyEnabled(context, true)
                    Toast.makeText(context, "Notifications will be blocked during study sessions", Toast.LENGTH_SHORT).show()
                }
            },
            hasDndPermission = hasDndPermission,
            onDndButtonClick = {
                if (!hasDndPermission) {
                    StudyNotificationBlockerManager.openDndSettings(context)
                } else {
                    // Toggle test instant silence
                    StudyNotificationBlockerManager.activateStudyNotificationBlock(context)
                    Toast.makeText(context, "Study Notifications Muted Now!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showStudyNotificationsSheet = false }
        )
    }
}
