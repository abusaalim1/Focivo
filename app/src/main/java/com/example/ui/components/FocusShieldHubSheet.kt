package com.example.ui.components

// v1.2.3 (2026-10-16) — Focus Shield rebuilt to the owner's exact clean-redesign
// HTML (Focus_Shield___Clean_Redesign_4_c28b.html): full-page layout, same
// section order, card/chip/toggle language, #0A0A0B + #B6F13A palette, Poppins
// body + Instrument Serif italic for the page headline. Every control is wired
// to the same real setting/feature as before — visual rebuild only.

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
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
import com.example.data.SupabasePunishmentLogDto
import com.example.data.SupabaseScheduledBlockDto
import com.example.service.ScheduledBlockScheduler
import com.example.ui.screens.StudyChannelsScreen
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.focivoTokens
import com.example.util.AiStudyGuardManager
import com.example.util.AppIconView
import com.example.util.DeviceAppInfo

object FocusShieldPermissions {
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun hasAccessibilityPermission(context: Context): Boolean {
        return AiStudyGuardManager.isAccessibilityPermissionGranted(context)
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                val generalIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(generalIntent)
            }
        }
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}

data class BlockableAppItem(
    val id: String,
    val name: String,
    val category: String,
    val packageName: String,
    val isBlocked: Boolean
)

// ---------------------------------------------------------------------------
// Design tokens — owner's Shield clean-redesign, now dark/light aware.
// The dark values are exactly the tokens above (bg #0A0A0B, surface #131315,
// line #1F1F22, text #F4F4F5, muted #76767C, accent #B6F13A); light mode uses
// the system counterpart set via focivoTokens().
// ---------------------------------------------------------------------------

private val ShieldSerifItalic = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic)
)

@Composable
private fun shieldBody(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified
) = TextStyle(
    fontFamily = PoppinsFontFamily,
    fontSize = size.sp,
    fontWeight = weight,
    color = if (color == Color.Unspecified) focivoTokens().text else color
)

/** The exact toggle from the design: 48x28, dark track / grey thumb, lime when on. */
@Composable
private fun ShieldToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.size(width = 48.dp, height = 28.dp),
        colors = SwitchDefaults.colors(
            checkedThumbColor = focivoTokens().bg,
            checkedTrackColor = focivoTokens().accent,
            uncheckedThumbColor = focivoTokens().thumbOff,
            uncheckedTrackColor = focivoTokens().trackOff,
            uncheckedBorderColor = Color.Transparent,
            checkedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = focivoTokens().bg.copy(alpha = 0.7f),
            disabledCheckedTrackColor = focivoTokens().accent.copy(alpha = 0.5f)
        )
    )
}

/** Uppercase section eyebrow, optionally with an action on the right. */
@Composable
private fun ShieldEyebrow(
    title: String,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = shieldBody(11, FontWeight.SemiBold, focivoTokens().muted).copy(letterSpacing = 1.76.sp)
        )
        action?.invoke()
    }
}

/** One row inside a grouped list card. */
@Composable
private fun ShieldGroupRow(
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = true,
    tag: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = title, style = shieldBody(14, FontWeight.SemiBold))
                    tag?.invoke()
                }
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = subtitle, style = shieldBody(12, FontWeight.Normal, focivoTokens().muted).copy(lineHeight = 17.4.sp))
                }
            }
            trailing()
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(focivoTokens().line))
        }
    }
}

/** Small tag pill, e.g. "Optional" or "Active". */
@Composable
private fun ShieldTag(text: String, active: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (active) focivoTokens().softAccent else Color.Transparent)
            .border(1.dp, if (active) focivoTokens().accent.copy(alpha = 0.18f) else focivoTokens().chipBorder, RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = shieldBody(9, FontWeight.SemiBold, if (active) focivoTokens().accent else focivoTokens().muted).copy(letterSpacing = 0.72.sp)
        )
    }
}

@Composable
private fun PulsingDot() {
    val alpha by rememberInfiniteTransition(label = "shieldPulse").animateFloat(
        initialValue = 1f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(focivoTokens().accent.copy(alpha = alpha))
    )
}

@Composable
fun FocusShieldHubSheet(
    isShieldEnabled: Boolean,
    isShieldTimerRunning: Boolean,
    remainingSeconds: Int,
    blockedPackages: Set<String>,
    blockedAttemptsCount: Int,
    installedApps: List<DeviceAppInfo> = emptyList(),
    onToggleMasterShield: (Boolean) -> Unit,
    onToggleAppBlocked: (packageName: String, isBlocked: Boolean) -> Unit,
    onBlockAllSocial: () -> Unit = {},
    onUnblockAll: () -> Unit = {},
    onStartStandaloneShield: (durationMinutes: Int) -> Unit,
    onStopStandaloneShield: () -> Unit,
    onTriggerTestIntercept: (appName: String) -> Unit,
    scheduledBlocks: List<SupabaseScheduledBlockDto> = emptyList(),
    punishmentLogs: List<SupabasePunishmentLogDto> = emptyList(),
    onSaveSchedule: (SupabaseScheduledBlockDto) -> Unit = {},
    onToggleSchedule: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteSchedule: (String) -> Unit = {},
    onOpenAppLimits: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var editingSchedule by remember { mutableStateOf<SupabaseScheduledBlockDto?>(null) }
    var showScheduleEditSheet by remember { mutableStateOf(false) }

    var hasUsagePermission by remember {
        mutableStateOf(FocusShieldPermissions.hasUsageStatsPermission(context))
    }
    var hasOverlayPermission by remember {
        mutableStateOf(FocusShieldPermissions.hasOverlayPermission(context))
    }
    var hasAccessibilityPermission by remember {
        mutableStateOf(AiStudyGuardManager.isAccessibilityPermissionGranted(context))
    }
    var isScheduledBlockActive by remember {
        mutableStateOf(ScheduledBlockScheduler.isScheduleCurrentlyActive(context))
    }
    var isAiGuardEnabled by remember {
        mutableStateOf(AiStudyGuardManager.isAiGuardEnabled(context))
    }

    var selectedDurationMinutes by remember { mutableIntStateOf(25) }
    var showPermissionSheet by remember { mutableStateOf(false) }
    var showStudyChannels by remember { mutableStateOf(false) }
    var showAiAccessibilityOptInDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryTab by remember { mutableStateOf("All") }
    var activityExpanded by remember { mutableStateOf(false) }
    var showFilterTester by remember { mutableStateOf(false) }

    var testYouTubeQuery by remember { mutableStateOf("") }
    var testYouTubeResult by remember { mutableStateOf<com.example.util.YouTubeVideoClassifier.VideoVerdict?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = FocusShieldPermissions.hasUsageStatsPermission(context)
                hasOverlayPermission = FocusShieldPermissions.hasOverlayPermission(context)
                hasAccessibilityPermission = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
                isScheduledBlockActive = ScheduledBlockScheduler.isScheduleCurrentlyActive(context)
                isAiGuardEnabled = AiStudyGuardManager.isAiGuardEnabled(context)
                com.example.util.ShortsLockManager.init(context)
                com.example.util.YouTubeStudyGuardManager.init(context)
                com.example.util.DeepFocusManager.init(context)
                if (hasAccessibilityPermission) {
                    val queued = pendingAction
                    if (queued != null) {
                        pendingAction = null
                        queued()
                        isAiGuardEnabled = AiStudyGuardManager.isAiGuardEnabled(context)
                    }
                } else {
                    pendingAction = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isStrictModeEnabled by com.example.util.StrictModeManager.isStrictModeState.collectAsState()
    val isBlockSplitScreen by com.example.util.StrictModeManager.blockSplitScreenState.collectAsState()
    val isBlockFloatingWindow by com.example.util.StrictModeManager.blockFloatingWindowState.collectAsState()
    val isDeepFocusEnabled by com.example.util.DeepFocusManager.isDeepFocusEnabledState.collectAsState()
    val isShortsLockEnabled by com.example.util.ShortsLockManager.isShortsLockState.collectAsState()
    val isReelsLockEnabled by com.example.util.ShortsLockManager.isReelsLockState.collectAsState()
    val isYouTubeFilterEnabled by com.example.util.YouTubeStudyGuardManager.isFilterEnabledState.collectAsState()

    val isAllPermissionsGranted = hasUsagePermission && hasOverlayPermission && hasAccessibilityPermission
    val masterArmed = isShieldEnabled && isAllPermissionsGranted

    fun enableWithGate(enable: () -> Unit) {
        pendingAction = { enable() }
        showPermissionSheet = true
    }

    val defaultApps = remember(blockedPackages) {
        listOf(
            BlockableAppItem("instagram", "Instagram & Reels", "Social Media", "com.instagram.android", blockedPackages.contains("com.instagram.android")),
            BlockableAppItem("youtube", "YouTube & Shorts", "Video Streaming", "com.google.android.youtube", blockedPackages.contains("com.google.android.youtube")),
            BlockableAppItem("games", "Games (Free Fire, BGMI, etc.)", "Mobile Gaming", "com.dts.freefireth,com.pubg.imobile,com.king.candycrushsaga", blockedPackages.any { it.contains("freefire") || it.contains("pubg") || it.contains("game") }),
            BlockableAppItem("tiktok", "TikTok", "Short Videos", "com.zhiliaoapp.musically", blockedPackages.contains("com.zhiliaoapp.musically")),
            BlockableAppItem("twitter", "X (Twitter)", "Social Media", "com.twitter.android", blockedPackages.contains("com.twitter.android")),
            BlockableAppItem("snapchat", "Snapchat", "Social Messaging", "com.snapchat.android", blockedPackages.contains("com.snapchat.android")),
            BlockableAppItem("facebook", "Facebook", "Social Media", "com.facebook.katana", blockedPackages.contains("com.facebook.katana")),
            BlockableAppItem("reddit", "Reddit", "Social Media", "com.reddit.frontpage", blockedPackages.contains("com.reddit.frontpage"))
        )
    }

    val displayApps = remember(installedApps, blockedPackages, searchQuery, selectedCategoryTab) {
        val baseList = if (installedApps.isNotEmpty()) {
            installedApps.filterNot { com.example.util.EssentialAppsGuard.isEssentialApp(context, it.packageName) }.map { app ->
                BlockableAppItem(
                    id = app.packageName,
                    name = app.appName,
                    category = app.category,
                    packageName = app.packageName,
                    isBlocked = blockedPackages.contains(app.packageName) || app.isBlocked
                )
            }
        } else {
            defaultApps.filterNot { com.example.util.EssentialAppsGuard.isEssentialApp(context, it.packageName) }
        }

        baseList.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.packageName.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedCategoryTab) {
                "Social" -> item.category.contains("Social", ignoreCase = true)
                "Games" -> item.category.contains("Game", ignoreCase = true)
                "Entertainment" -> item.category.contains("Entertainment", ignoreCase = true) || item.category.contains("Video", ignoreCase = true)
                "Messaging" -> item.category.contains("Messaging", ignoreCase = true) || item.category.contains("Chat", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesCategory
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(focivoTokens().bg)
            .testTag("focus_shield_hub_sheet")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("focus_shield_list"),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 60.dp)
        ) {
            // ---------------------------------------------------------------
            // Header — matches the design's .top
            // ---------------------------------------------------------------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(focivoTokens().softAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Focus Shield",
                            tint = focivoTokens().accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Focus Shield",
                            style = TextStyle(
                                fontFamily = ShieldSerifItalic,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = focivoTokens().text,
                                letterSpacing = (-0.44).sp,
                                lineHeight = 26.sp
                            )
                        )
                        Text(text = "Distraction blocker", style = shieldBody(12, FontWeight.Normal, focivoTokens().muted))
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(focivoTokens().surface)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = focivoTokens().muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ---------------------------------------------------------------
            // Hero — "Shield is armed" + master switch
            // ---------------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(26.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0x24B6F13A), Color(0x08B6F13A), focivoTokens().surface)
                            )
                        )
                        .border(1.dp, focivoTokens().accent.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
                        .padding(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (masterArmed) "Shield is armed" else "Shield is off",
                                style = shieldBody(17, FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                if (masterArmed) PulsingDot()
                                Text(
                                    text = if (isShieldTimerRunning) {
                                        "Lock active · apps blocked until timer ends"
                                    } else if (!isAllPermissionsGranted) {
                                        "Grant permissions to arm the shield"
                                    } else if (masterArmed) {
                                        "Distracting apps blocked during focus"
                                    } else {
                                        "Distracting apps stay open"
                                    },
                                    style = shieldBody(12, FontWeight.Medium, focivoTokens().accent)
                                )
                            }
                            if (blockedAttemptsCount > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(focivoTokens().softAccent)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$blockedAttemptsCount intercepted",
                                        style = shieldBody(10, FontWeight.SemiBold, focivoTokens().accent).copy(letterSpacing = 0.4.sp)
                                    )
                                }
                            }
                        }
                        ShieldToggle(
                            checked = masterArmed,
                            enabled = !isShieldTimerRunning,
                            onCheckedChange = { isChecked ->
                                if (isChecked && !isAllPermissionsGranted) {
                                    enableWithGate { onToggleMasterShield(true) }
                                } else {
                                    onToggleMasterShield(isChecked)
                                }
                            }
                        )
                    }
                }
            }

            // ---------------------------------------------------------------
            // Quick lock
            // ---------------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(34.dp))
                ShieldEyebrow(title = "Quick lock")
                Spacer(modifier = Modifier.height(12.dp))
                if (isShieldTimerRunning) {
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(focivoTokens().surface)
                            .border(1.dp, focivoTokens().line, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Lock active",
                                    style = shieldBody(15, FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    style = TextStyle(
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = focivoTokens().text
                                    )
                                )
                                Text(
                                    text = "Unlocks strictly at 00:00",
                                    style = shieldBody(12, FontWeight.Normal, focivoTokens().muted)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(focivoTokens().softAccent)
                                    .border(1.dp, focivoTokens().accent.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Locked",
                                    style = shieldBody(12, FontWeight.Bold, focivoTokens().accent)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(focivoTokens().surface)
                            .padding(20.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Lock selected apps", style = shieldBody(15, FontWeight.SemiBold))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Strictly block for a custom duration",
                                style = shieldBody(12, FontWeight.Normal, focivoTokens().muted)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(focivoTokens().bg)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                listOf(15, 25, 45, 60).forEach { mins ->
                                    val selected = selectedDurationMinutes == mins
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(if (selected) focivoTokens().accent else Color.Transparent)
                                            .clickable { selectedDurationMinutes = mins }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${mins}m",
                                            style = shieldBody(
                                                13,
                                                if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                if (selected) focivoTokens().bg else focivoTokens().muted
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(focivoTokens().accent)
                                    .clickable {
                                        if (!isAllPermissionsGranted) {
                                            enableWithGate { onStartStandaloneShield(selectedDurationMinutes) }
                                        } else {
                                            onStartStandaloneShield(selectedDurationMinutes)
                                        }
                                    }
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Start ${selectedDurationMinutes}m lock",
                                    style = shieldBody(15, FontWeight.Bold, focivoTokens().bg)
                                )
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------
            // Protection
            // ---------------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(34.dp))
                ShieldEyebrow(title = "Protection")
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(focivoTokens().surface)
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ShieldGroupRow(
                            title = "Strict focus",
                            subtitle = "Keeps apps locked until your session ends",
                            trailing = {
                                ShieldToggle(
                                    checked = isStrictModeEnabled,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked && !isAllPermissionsGranted) {
                                            enableWithGate {
                                                com.example.util.StrictModeManager.setStrictModeEnabled(context, true)
                                            }
                                        } else {
                                            com.example.util.StrictModeManager.setStrictModeEnabled(context, isChecked)
                                        }
                                    }
                                )
                            }
                        )
                        ShieldGroupRow(
                            title = "Block split screen",
                            subtitle = "Blocked apps can't open in split-screen",
                            trailing = {
                                ShieldToggle(
                                    checked = isBlockSplitScreen,
                                    onCheckedChange = {
                                        com.example.util.StrictModeManager.setBlockSplitScreenEnabled(context, it)
                                    }
                                )
                            }
                        )
                        ShieldGroupRow(
                            title = "Block floating window",
                            subtitle = "Blocked apps can't open as floating windows",
                            trailing = {
                                ShieldToggle(
                                    checked = isBlockFloatingWindow,
                                    onCheckedChange = {
                                        com.example.util.StrictModeManager.setBlockFloatingWindowEnabled(context, it)
                                    }
                                )
                            }
                        )
                        ShieldGroupRow(
                            title = "Deep focus",
                            subtitle = "Prevent switching to other apps while studying",
                            tag = { ShieldTag(text = "Optional", active = false) },
                            trailing = {
                                ShieldToggle(
                                    checked = isDeepFocusEnabled,
                                    onCheckedChange = {
                                        com.example.util.DeepFocusManager.setDeepFocusEnabled(context, it)
                                    }
                                )
                            }
                        )
                        ShieldGroupRow(
                            title = "YouTube Shorts",
                            subtitle = "Full lectures allowed, Shorts locked",
                            // v1.2.3: honest arming state — the lock only fires
                            // during an active study period (scope unchanged).
                            tag = {
                                val armed = isShortsLockEnabled
                                val live = armed && com.example.util.AiStudyGuardManager.isStudyPeriodActive(context)
                                if (armed) ShieldTag(
                                    text = if (live) "Active now" else "Armed",
                                    active = live
                                )
                            },
                            trailing = {
                                ShieldToggle(
                                    checked = isShortsLockEnabled,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked && !isAllPermissionsGranted) {
                                            enableWithGate {
                                                com.example.util.ShortsLockManager.setShortsLockEnabled(context, true)
                                            }
                                        } else {
                                            com.example.util.ShortsLockManager.setShortsLockEnabled(context, isChecked)
                                        }
                                    }
                                )
                            }
                        )
                        ShieldGroupRow(
                            title = "Instagram Reels",
                            subtitle = "Intercepts the Reels tab and viewer",
                            showDivider = false,
                            // v1.2.3: honest arming state — the lock only fires
                            // during an active study period (scope unchanged).
                            tag = {
                                val armed = isReelsLockEnabled
                                val live = armed && com.example.util.AiStudyGuardManager.isStudyPeriodActive(context)
                                if (armed) ShieldTag(
                                    text = if (live) "Active now" else "Armed",
                                    active = live
                                )
                            },
                            trailing = {
                                ShieldToggle(
                                    checked = isReelsLockEnabled,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked && !isAllPermissionsGranted) {
                                            enableWithGate {
                                                com.example.util.ShortsLockManager.setReelsLockEnabled(context, true)
                                            }
                                        } else {
                                            com.example.util.ShortsLockManager.setReelsLockEnabled(context, isChecked)
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // ---------------------------------------------------------------
            // AI protection
            // ---------------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(34.dp))
                ShieldEyebrow(title = "AI protection")
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(focivoTokens().surface)
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = "AI Study Guard", style = shieldBody(14, FontWeight.SemiBold))
                                    ShieldTag(
                                        text = if (isAiGuardEnabled && hasAccessibilityPermission) "Active" else "Off",
                                        active = isAiGuardEnabled && hasAccessibilityPermission
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ChatGPT & Claude academic sentinel",
                                    style = shieldBody(12, FontWeight.Normal, focivoTokens().muted).copy(lineHeight = 17.4.sp)
                                )
                            }
                            ShieldToggle(
                                checked = isAiGuardEnabled,
                                onCheckedChange = { isEnabled ->
                                    if (isEnabled && !hasAccessibilityPermission) {
                                        pendingAction = {
                                            AiStudyGuardManager.setAiGuardEnabled(context, true)
                                            isAiGuardEnabled = true
                                        }
                                        showAiAccessibilityOptInDialog = true
                                    } else {
                                        AiStudyGuardManager.setAiGuardEnabled(context, isEnabled)
                                        isAiGuardEnabled = isEnabled
                                    }
                                }
                            )
                        }

                        // The 3-stage discipline timeline, per the design
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                Triple("Warning", "3-minute grace window", focivoTokens().warning),
                                Triple("Lockout", "3 hours if casual chatter continues", focivoTokens().danger),
                                Triple("Resolved", "Instantly when you return to study", focivoTokens().accent)
                            ).forEachIndexed { index, (title, sub, dot) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
                                                .clip(CircleShape)
                                                .background(dot)
                                        )
                                        if (index < 2) {
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .height(16.dp)
                                                    .background(focivoTokens().line)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.padding(bottom = if (index < 2) 14.dp else 0.dp)) {
                                        Text(text = title, style = shieldBody(13, FontWeight.SemiBold))
                                        Text(text = sub, style = shieldBody(12, FontWeight.Normal, focivoTokens().muted))
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(focivoTokens().line))

                        ShieldGroupRow(
                            title = "YouTube content filter",
                            subtitle = "Only educational lectures & coding tutorials. Blocks songs, movies, gaming and vlogs.",
                            trailing = {
                                ShieldToggle(
                                    checked = isYouTubeFilterEnabled,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked && !isAllPermissionsGranted) {
                                            enableWithGate {
                                                com.example.util.YouTubeStudyGuardManager.setFilterEnabled(context, true)
                                            }
                                        } else {
                                            com.example.util.YouTubeStudyGuardManager.setFilterEnabled(context, isChecked)
                                        }
                                    }
                                )
                            }
                        )

                        // Live classifier tester, kept from the previous UI in design language
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFilterTester = !showFilterTester }
                                .padding(vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Test the content filter",
                                    style = shieldBody(12, FontWeight.Medium, focivoTokens().muted)
                                )
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = focivoTokens().muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (showFilterTester) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(focivoTokens().bg)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Physics Class 12 — Electrostatics Full Chapter",
                                    "Arijit Singh Latest Song",
                                    "JEE Main 2027 Physics Preparation",
                                    "Funny College Vlog"
                                ).forEach { sample ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, focivoTokens().line, RoundedCornerShape(10.dp))
                                            .clickable {
                                                testYouTubeQuery = sample
                                                testYouTubeResult = com.example.util.YouTubeVideoClassifier.evaluate(sample)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(text = sample, style = shieldBody(12, FontWeight.Normal, focivoTokens().text))
                                    }
                                }
                                if (testYouTubeResult != null) {
                                    val res = testYouTubeResult!!
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (res.isEducational) "Study content" else "Non-study content",
                                        style = shieldBody(13, FontWeight.SemiBold, if (res.isEducational) focivoTokens().success else focivoTokens().danger)
                                    )
                                    Text(
                                        text = "Confidence: ${(res.confidence * 100).toInt()}%",
                                        style = shieldBody(11, FontWeight.Normal, focivoTokens().muted)
                                    )
                                    Text(
                                        text = "Category: ${res.category}",
                                        style = shieldBody(11, FontWeight.Normal, focivoTokens().muted)
                                    )
                                    Text(
                                        text = "Reason: ${res.reason}",
                                        style = shieldBody(11, FontWeight.Normal, focivoTokens().muted)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showStudyChannels = true }
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Study channels", style = shieldBody(14, FontWeight.SemiBold))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Allowlist + blocked channels",
                                    style = shieldBody(12, FontWeight.Normal, focivoTokens().muted).copy(lineHeight = 17.4.sp)
                                )
                            }
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Open",
                                tint = focivoTokens().muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------------
            // Schedules
            // ---------------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(34.dp))
                ShieldEyebrow(
                    title = "Schedules",
                    action = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(focivoTokens().softAccent)
                                .clickable {
                                    editingSchedule = null
                                    showScheduleEditSheet = true
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(text = "+ Add", style = shieldBody(12, FontWeight.SemiBold, focivoTokens().accent))
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (scheduledBlocks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(focivoTokens().surface)
                            .padding(horizontal = 20.dp, vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(focivoTokens().softAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = focivoTokens().accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "No schedules yet", style = shieldBody(14, FontWeight.SemiBold))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Set study hours, break windows and active days to block distractions automatically.",
                                style = shieldBody(12, FontWeight.Normal, focivoTokens().muted).copy(
                                    textAlign = TextAlign.Center, lineHeight = 18.sp
                                )
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(focivoTokens().surface)
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            scheduledBlocks.forEachIndexed { index, block ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editingSchedule = block
                                            showScheduleEditSheet = true
                                        }
                                        .padding(vertical = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = block.label, style = shieldBody(14, FontWeight.SemiBold))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${ScheduledBlockScheduler.formatDisplayTime(block.start_time)} – ${ScheduledBlockScheduler.formatDisplayTime(block.end_time)} · ${block.days_active}",
                                                style = shieldBody(12, FontWeight.Normal, focivoTokens().muted)
                                            )
                                            if (isScheduledBlockActive) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                ShieldTag(text = "Active now", active = true)
                                            }
                                        }
                                        ShieldToggle(
                                            checked = block.is_enabled,
                                            onCheckedChange = { onToggleSchedule(block.id, it) }
                                        )
                                    }
                                }
                                if (index < scheduledBlocks.size - 1) {
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(focivoTokens().line))
                                }
                            }
                        }
                    }
                }

                // Per-app daily limits row, same card language
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(focivoTokens().surface)
                        .clickable {
                            onDismiss()
                            onOpenAppLimits()
                        }
                        .testTag("open_app_limits_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Per-app daily limits", style = shieldBody(14, FontWeight.SemiBold))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Set a time budget for each app",
                                style = shieldBody(12, FontWeight.Normal, focivoTokens().muted)
                            )
                        }
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Open",
                            tint = focivoTokens().muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ---------------------------------------------------------------
            // Apps to block
            // ---------------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(34.dp))
                val protectedCount = displayApps.count { it.isBlocked }
                ShieldEyebrow(
                    title = "Apps to block",
                    action = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(focivoTokens().softAccent)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$protectedCount protected",
                                style = shieldBody(10, FontWeight.SemiBold, focivoTokens().accent).copy(letterSpacing = 0.4.sp)
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Search
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(99.dp))
                        .background(focivoTokens().surface)
                        .padding(horizontal = 18.dp, vertical = 13.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = focivoTokens().muted,
                            modifier = Modifier.size(18.dp)
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = shieldBody(13, FontWeight.Normal, focivoTokens().text),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("app_search_input"),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(text = "Search installed apps", style = shieldBody(13, FontWeight.Normal, focivoTokens().muted))
                                }
                                inner()
                            }
                        )
                    }
                }

                // Category chips — exactly the design's five
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Social", "Games", "Entertainment", "Messaging").forEach { cat ->
                        val selected = selectedCategoryTab == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (selected) focivoTokens().accent else focivoTokens().surface)
                                .clickable { selectedCategoryTab = cat }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cat,
                                style = shieldBody(
                                    12,
                                    if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    if (selected) focivoTokens().bg else focivoTokens().muted
                                )
                            )
                        }
                    }
                }

                // Bulk actions
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Block all social",
                        style = shieldBody(12, FontWeight.SemiBold, focivoTokens().danger),
                        modifier = Modifier.clickable { onBlockAllSocial() }
                    )
                    Text(
                        text = "Unblock all",
                        style = shieldBody(12, FontWeight.SemiBold, focivoTokens().muted),
                        modifier = Modifier.clickable { onUnblockAll() }
                    )
                }

                // App rows
                if (displayApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No matching apps found", style = shieldBody(13, FontWeight.Normal, focivoTokens().muted))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(focivoTokens().surface)
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            displayApps.forEachIndexed { index, app ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(focivoTokens().bg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val fallback = if (app.category.contains("Game", ignoreCase = true)) {
                                                Icons.Filled.Games
                                            } else if (app.category.contains("Video", ignoreCase = true) || app.category.contains("Media", ignoreCase = true)) {
                                                Icons.Filled.Videocam
                                            } else {
                                                Icons.Filled.Lock
                                            }
                                            AppIconView(
                                                packageName = app.packageName,
                                                contentDescription = app.name,
                                                modifier = Modifier.size(28.dp),
                                                fallbackVector = fallback,
                                                fallbackTint = focivoTokens().muted
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = app.name, style = shieldBody(14, FontWeight.SemiBold))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(text = app.category, style = shieldBody(12, FontWeight.Normal, focivoTokens().muted))
                                        }
                                        ShieldToggle(
                                            checked = app.isBlocked,
                                            onCheckedChange = { isChecked ->
                                                if (isChecked && !isAllPermissionsGranted) {
                                                    enableWithGate { onToggleAppBlocked(app.packageName, true) }
                                                } else {
                                                    onToggleAppBlocked(app.packageName, isChecked)
                                                }
                                            }
                                        )
                                    }
                                    if (index < displayApps.size - 1) {
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(focivoTokens().line))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------
            // Activity
            // ---------------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(34.dp))
                ShieldEyebrow(
                    title = "Activity",
                    action = {
                        if (punishmentLogs.size > 2) {
                            Text(
                                text = if (activityExpanded) "Show less" else "View all",
                                style = shieldBody(12, FontWeight.SemiBold, focivoTokens().accent).copy(letterSpacing = 0.48.sp),
                                modifier = Modifier.clickable { activityExpanded = !activityExpanded }
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                val shownLogs = if (activityExpanded) punishmentLogs else punishmentLogs.take(2)
                if (shownLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(focivoTokens().surface)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "No guard activity yet. Warnings and resolutions will appear here.",
                            style = shieldBody(12, FontWeight.Normal, focivoTokens().muted)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(focivoTokens().surface)
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            shownLogs.forEachIndexed { index, log ->
                                val (statusFg, statusLabel) = when (log.status) {
                                    "blocked" -> Pair(focivoTokens().danger, "BLOCKED (3h)")
                                    "warned_resolved" -> Pair(focivoTokens().success, "RESOLVED")
                                    else -> Pair(focivoTokens().warning, "WARNED")
                                }
                                val friendlyApp = when (log.app_package) {
                                    "com.openai.chatgpt" -> "ChatGPT"
                                    "com.anthropic.claude" -> "Claude"
                                    else -> log.app_package.substringAfterLast('.')
                                }
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = friendlyApp, style = shieldBody(14, FontWeight.SemiBold))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = log.reason,
                                                style = shieldBody(12, FontWeight.Normal, focivoTokens().muted),
                                                maxLines = 2
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(focivoTokens().softAccent)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                style = shieldBody(10, FontWeight.SemiBold, statusFg).copy(letterSpacing = 0.4.sp)
                                            )
                                        }
                                    }
                                    if (index < shownLogs.size - 1) {
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(focivoTokens().line))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------
            // Test shield
            // ---------------------------------------------------------------
            item {
                Spacer(modifier = Modifier.height(34.dp))
                ShieldEyebrow(title = "Test shield")
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(99.dp))
                            .border(1.dp, focivoTokens().chipBorder.copy(alpha = 0.6f), RoundedCornerShape(99.dp))
                            .clickable {
                                if (!isAllPermissionsGranted) {
                                    enableWithGate { onTriggerTestIntercept("Instagram & Reels") }
                                } else {
                                    onTriggerTestIntercept("Instagram & Reels")
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Test Instagram lock", style = shieldBody(12, FontWeight.Medium, focivoTokens().text))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(99.dp))
                            .border(1.dp, focivoTokens().chipBorder.copy(alpha = 0.6f), RoundedCornerShape(99.dp))
                            .clickable {
                                if (!isAllPermissionsGranted) {
                                    enableWithGate { onTriggerTestIntercept("YouTube & Shorts") }
                                } else {
                                    onTriggerTestIntercept("YouTube & Shorts")
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Test Shorts lock", style = shieldBody(12, FontWeight.Medium, focivoTokens().text))
                    }
                }
            }
        }

        // ---------------------------------------------------------------
        // Nested overlays — unchanged behaviour
        // ---------------------------------------------------------------
        if (showStudyChannels) {
            Box(modifier = Modifier.fillMaxSize().background(focivoTokens().bg)) {
                StudyChannelsScreen(onBack = { showStudyChannels = false })
            }
        }

        if (showScheduleEditSheet) {
            ScheduleEditSheet(
                schedule = editingSchedule,
                existingSchedules = scheduledBlocks,
                installedApps = installedApps,
                onSave = { schedule ->
                    onSaveSchedule(schedule)
                    showScheduleEditSheet = false
                },
                onDelete = { scheduleId ->
                    onDeleteSchedule(scheduleId)
                    showScheduleEditSheet = false
                },
                onDismiss = { showScheduleEditSheet = false }
            )
        }

        if (showAiAccessibilityOptInDialog) {
            AiStudyAccessibilityOptInDialog(
                onConfirmOptIn = {
                    showAiAccessibilityOptInDialog = false
                    AiStudyGuardManager.openAccessibilitySettings(context)
                },
                onDismiss = {
                    showAiAccessibilityOptInDialog = false
                }
            )
        }

        if (showPermissionSheet) {
            FocusShieldPermissionSheet(
                onDismiss = {
                    showPermissionSheet = false
                    pendingAction = null
                },
                onAllGranted = {
                    showPermissionSheet = false
                    pendingAction?.invoke()
                    pendingAction = null
                }
            )
        }
    }
}
