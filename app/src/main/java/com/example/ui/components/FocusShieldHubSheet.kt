package com.example.ui.components

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.SupabaseScheduledBlockDto
import com.example.data.SupabasePunishmentLogDto
import com.example.ui.components.GlassCard
import com.example.ui.components.LiquidGlassCard
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.AppIconView
import com.example.util.DeviceAppInfo
import com.example.service.ScheduledBlockScheduler
import com.example.util.AiStudyGuardManager

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

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lifecycleOwner = LocalLifecycleOwner.current

    var editingSchedule by remember { mutableStateOf<SupabaseScheduledBlockDto?>(null) }
    var showScheduleEditSheet by remember { mutableStateOf(false) }

    // Live real-time permission tracking with onResume updates
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
    var showAiAccessibilityOptInDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryTab by remember { mutableStateOf("All") }

    val isAllPermissionsGranted = hasUsagePermission && hasOverlayPermission && hasAccessibilityPermission
    val isStrictModeEnabled by com.example.util.StrictModeManager.isStrictModeState.collectAsState()
    val isDeepFocusEnabled by com.example.util.DeepFocusManager.isDeepFocusEnabledState.collectAsState()
    val isShortsLockEnabled by com.example.util.ShortsLockManager.isShortsLockState.collectAsState()
    val isReelsLockEnabled by com.example.util.ShortsLockManager.isReelsLockState.collectAsState()
    val isYouTubeFilterEnabled by com.example.util.YouTubeStudyGuardManager.isFilterEnabledState.collectAsState()
    val youtubeBlockedCount by com.example.util.YouTubeStudyGuardManager.blockedVideosCountState.collectAsState()

    var testYouTubeQuery by remember { mutableStateOf("") }
    var testYouTubeResult by remember { mutableStateOf<com.example.util.YouTubeVideoClassifier.VideoVerdict?>(null) }

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
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
                "Blocked" -> item.isBlocked
                else -> true
            }

            matchesQuery && matchesCategory
        }
    }

    // Dynamic theme palette referencing semantic MaterialTheme color tokens
    val isDark = isAppInDarkTheme()
    val sheetBackground = if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7)
    val cardBackground = if (isDark) Color(0xFF1B221B) else Color(0xFFFFFFFF)
    val cardBorderColor = if (isDark) Color(0x358CE000) else Color(0xFFE2EBD6)
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val textMuted = textSecondary.copy(alpha = 0.7f)
    val accentLime = if (isDark) RegainLimePrimary else RegainLimeDeepText
    val accentGreen = if (isDark) Color(0xFF69F0AE) else Color(0xFF2E7D32)
    val accentAmber = if (isDark) Color(0xFFFFB74D) else Color(0xFFD97706)
    val accentCoral = if (isDark) Color(0xFFFF5252) else Color(0xFFD32F2F)
    val accentCyan = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBackground,
        dragHandle = null,
        modifier = modifier.testTag("focus_shield_hub_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 18.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(RegainLimePrimary, RegainLimeDeepText))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = NearBlack,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Focus Shield",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 21.sp,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Distraction Blocker & App Interceptor",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = accentLime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Master Toggle Card
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    accentBorder = isShieldEnabled && isAllPermissionsGranted
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Distraction Blocker Master Switch",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isShieldTimerRunning) {
                                    "🔒 Strict Lock Active · Master switch locked until timer ends"
                                } else if (!isAllPermissionsGranted) {
                                    "⚠️ Permissions Needed · Tap switch to grant permissions"
                                } else if (isShieldEnabled) {
                                    "Armed · Distracting apps will be blocked during focus"
                                } else {
                                    "Disabled · Distracting apps will remain open"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isShieldTimerRunning) Color(0xFFFF8A80) else if (!isAllPermissionsGranted) accentAmber else if (isShieldEnabled) accentGreen else textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isShieldTimerRunning || (isShieldEnabled && isAllPermissionsGranted) || !isAllPermissionsGranted) FontWeight.Medium else FontWeight.Normal
                                )
                            )
                        }

                        Switch(
                            checked = isShieldEnabled && isAllPermissionsGranted,
                            enabled = !isShieldTimerRunning,
                            onCheckedChange = { isChecked ->
                                if (isChecked && !isAllPermissionsGranted) {
                                    pendingAction = { onToggleMasterShield(true) }
                                    showPermissionSheet = true
                                } else {
                                    onToggleMasterShield(isChecked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NearBlack,
                                checkedTrackColor = accentLime,
                                uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB),
                                uncheckedThumbColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                                disabledCheckedThumbColor = NearBlack.copy(alpha = 0.7f),
                                disabledCheckedTrackColor = accentLime.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            // Per-App Daily Time Limits Section Card
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenAppLimits()
                        }
                        .testTag("open_app_limits_card"),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(accentLime.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = accentLime,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Per-App Daily Time Limits",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = textPrimary
                                    )
                                )
                                Text(
                                    text = "Set continuous daily time budgets per app",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Surface(
                            color = accentLime,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Configure",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NearBlack
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Scheduled Study Blocking Section (Dynamic list from Supabase/Local)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Study Blocking Schedules",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = textPrimary
                                )
                            )
                            Text(
                                text = "Automatic time-based distraction blocking",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                editingSchedule = null
                                showScheduleEditSheet = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentLime),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(accentLime)
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (scheduledBlocks.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            RegainMascotView(
                                width = 130.dp,
                                height = 150.dp,
                                pose = MascotPose.EMPTY_STATE
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Schedules Created Yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ Add' to set your study hours, break windows, active days, and custom blocked apps.",
                                fontSize = 12.sp,
                                color = textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(scheduledBlocks) { block ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingSchedule = block
                                showScheduleEditSheet = true
                            },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = block.label,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textPrimary
                                        )
                                    )
                                    if (!block.subject.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Subject: ${block.subject.trim()}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = textSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = accentLime,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${ScheduledBlockScheduler.formatDisplayTime(block.start_time)} – ${ScheduledBlockScheduler.formatDisplayTime(block.end_time)}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = accentLime,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }

                                Switch(
                                    checked = block.is_enabled,
                                    onCheckedChange = { isEnabled ->
                                        onToggleSchedule(block.id, isEnabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NearBlack,
                                        checkedTrackColor = accentLime,
                                        uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB),
                                        uncheckedThumbColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                                    )
                                )
                            }

                            if (!block.break_start_time.isNullOrBlank() && !block.break_end_time.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Coffee,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Break: ${ScheduledBlockScheduler.formatDisplayTime(block.break_start_time)} – ${ScheduledBlockScheduler.formatDisplayTime(block.break_end_time)}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFCD34D),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Days: ${block.days_active}",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )

                                val appCount = if (block.blocked_apps_list.isBlank()) {
                                    0
                                } else {
                                    block.blocked_apps_list.split(",").filter { it.isNotBlank() }.size
                                }
                                Text(
                                    text = "$appCount apps blocked",
                                    fontSize = 11.sp,
                                    color = accentLime,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // AI Study Guard Card (ChatGPT & Claude)
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    accentBorder = isAiGuardEnabled
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "AI Study Guard",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = textPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (hasAccessibilityPermission) Color(0x354CAF50) else Color(0x35E53935))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (hasAccessibilityPermission) "ACTIVE" else "PERMISSION NEEDED",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasAccessibilityPermission) accentGreen else accentCoral
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Two-Stage Academic Focus Warning & 3-Hour Lock",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = accentLime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                            Switch(
                                checked = isAiGuardEnabled,
                                onCheckedChange = { isEnabled ->
                                    if (isEnabled && !hasAccessibilityPermission) {
                                        showAiAccessibilityOptInDialog = true
                                    } else {
                                        AiStudyGuardManager.setAiGuardEnabled(context, isEnabled)
                                        isAiGuardEnabled = isEnabled
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NearBlack,
                                    checkedTrackColor = accentLime,
                                    uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB),
                                    uncheckedThumbColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Stage 1 (Warning): High-priority alert & in-app dialog with 3-minute grace window when non-study casual chat is detected.\n\nStage 2 (Block): 3-hour lockout applied only if non-study usage continues.\n\nStage 3 (Resolved): Instant positive confirmation when you return to study focus.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        )

                        if (!hasAccessibilityPermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentLime)
                                    .clickable {
                                        showAiAccessibilityOptInDialog = true
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Enable Accessibility for AI Study Guard",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = NearBlack,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // AI Discipline & Penalty History Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "AI Discipline & Punishment History",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = textPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Audit trail of warnings, resolutions, and penalties synced to Supabase",
                            fontSize = 11.sp,
                            color = textSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (punishmentLogs.isEmpty()) {
                            Text(
                                text = "🎯 No discipline violations recorded. Your AI study record is spotless!",
                                fontSize = 12.sp,
                                color = accentGreen,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                punishmentLogs.take(10).forEach { log ->
                                    val (statusBg, statusFg, statusLabel) = when (log.status) {
                                        "blocked" -> Triple(Color(0x35E53935), Color(0xFFEF4444), "⛔ BLOCKED (3h)")
                                        "warned_resolved" -> Triple(Color(0x354CAF50), Color(0xFF10B981), "✅ RESOLVED")
                                        else -> Triple(Color(0x35FFA000), Color(0xFFF59E0B), "⚠️ WARNED")
                                    }
                                    val friendlyApp = when (log.app_package) {
                                        "com.openai.chatgpt" -> "ChatGPT"
                                        "com.anthropic.claude" -> "Claude"
                                        else -> log.app_package.substringAfterLast('.')
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = friendlyApp,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textPrimary
                                            )
                                            Text(
                                                text = log.reason,
                                                fontSize = 11.sp,
                                                color = textSecondary,
                                                maxLines = 2
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(statusBg)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = statusFg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Active Timer Card OR Standalone Lock Timer Configuration
            item {
                if (isShieldTimerRunning) {
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    val timeStr = String.format("%02d:%02d", mins, secs)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0x45E53935), Color(0x407C4DFF))
                                )
                            )
                            .border(1.2.dp, Color(0x70E53935), RoundedCornerShape(22.dp))
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(accentCoral)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "STRICT LOCK ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFFF8A80),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.4.sp,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 34.sp,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "Unlocks strictly at 00:00 · Cannot be bypassed",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            // Strict Lock Indicator (Per user instruction: NO off/end button while timer runs)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x35E53935))
                                    .border(1.dp, Color(0x60E53935), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Locked",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color(0xFFFF8A80),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STANDALONE APP LOCK TIMER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = accentLime,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.4.sp,
                                        fontSize = 11.sp
                                    )
                                )
                                if (blockedAttemptsCount > 0) {
                                    Text(
                                        text = "$blockedAttemptsCount Intercepted",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = accentCoral,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Strictly lock selected apps for custom duration:",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Duration Chips (15m, 25m, 45m, 60m)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(15, 25, 45, 60).forEach { mins ->
                                    val isSelected = selectedDurationMinutes == mins
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isSelected) {
                                                    accentLime
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) accentLime else MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .clickable { selectedDurationMinutes = mins }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${mins}m",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color = if (isSelected) NearBlack else textPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(accentLime)
                                    .clickable {
                                        if (!isAllPermissionsGranted) {
                                            pendingAction = { onStartStandaloneShield(selectedDurationMinutes) }
                                            showPermissionSheet = true
                                        } else {
                                            onStartStandaloneShield(selectedDurationMinutes)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Start ${selectedDurationMinutes}m App Lock",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = NearBlack,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Real-time Test Simulation and Live Verification
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INSTANT TEST SIMULATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = accentLime,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "Verify Interception",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!isAllPermissionsGranted) {
                                        pendingAction = { onTriggerTestIntercept("Instagram & Reels") }
                                        showPermissionSheet = true
                                    } else {
                                        onTriggerTestIntercept("Instagram & Reels")
                                    }
                                },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = accentCoral,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Test Instagram Lock",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = textPrimary
                                    )
                                )
                                Text(
                                    text = if (isAllPermissionsGranted) "Preview Shield Overlay" else "Requires Permission",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isAllPermissionsGranted) textSecondary else Color(0xFFFFB74D),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        GlassCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!isAllPermissionsGranted) {
                                        pendingAction = { onTriggerTestIntercept("YouTube & Shorts") }
                                        showPermissionSheet = true
                                    } else {
                                        onTriggerTestIntercept("YouTube & Shorts")
                                    }
                                },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = accentLime,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Test Shorts Lock",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = textPrimary
                                    )
                                )
                                Text(
                                    text = if (isAllPermissionsGranted) "Preview Shield Overlay" else "Requires Permission",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isAllPermissionsGranted) textSecondary else Color(0xFFFFB74D),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Strict Mode (Anti-Uninstall Protection) Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isStrictModeEnabled) Color(0xFFFF5252).copy(alpha = 0.2f) else Color(0x18FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = if (isStrictModeEnabled) Color(0xFFFF5252) else textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Strict Focus Protection",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = textPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Keeps distracting apps securely locked until your study session completes.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = textSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = isStrictModeEnabled,
                                onCheckedChange = { isChecked ->
                                    if (isChecked && !isAllPermissionsGranted) {
                                        pendingAction = { com.example.util.StrictModeManager.setStrictModeEnabled(context, true) }
                                        showPermissionSheet = true
                                    } else {
                                        com.example.util.StrictModeManager.setStrictModeEnabled(context, isChecked)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NearBlack,
                                    checkedTrackColor = Color(0xFFFF5252),
                                    uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB)
                                )
                            )
                        }
                    }
                }
            }

            // Deep Focus Task Lock & Screen Pinning Card
            item {
                DeepFocusToggleCard(
                    isEnabled = isDeepFocusEnabled,
                    onToggle = { isChecked ->
                        com.example.util.DeepFocusManager.setDeepFocusEnabled(context, isChecked)
                    },
                    isSessionActive = com.example.util.DeepFocusManager.isDeepFocusSessionActive(context)
                )
            }

            // YouTube Shorts & Instagram Reels Blocker Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(accentLime.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = accentLime,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Block YouTube Shorts",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = textPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Allows full YouTube videos/lectures, but strictly locks addictive Shorts.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = textSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = isShortsLockEnabled,
                                onCheckedChange = { isChecked ->
                                    if (isChecked && !hasAccessibilityPermission) {
                                        pendingAction = { com.example.util.ShortsLockManager.setShortsLockEnabled(context, true) }
                                        showPermissionSheet = true
                                    } else {
                                        com.example.util.ShortsLockManager.setShortsLockEnabled(context, isChecked)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NearBlack,
                                    checkedTrackColor = accentLime,
                                    uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(accentCoral.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = accentCoral,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Block Instagram Reels",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = textPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Intercepts the Reels tab and viewer to protect study focus.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = textSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = isReelsLockEnabled,
                                onCheckedChange = { isChecked ->
                                    if (isChecked && !hasAccessibilityPermission) {
                                        pendingAction = { com.example.util.ShortsLockManager.setReelsLockEnabled(context, true) }
                                        showPermissionSheet = true
                                    } else {
                                        com.example.util.ShortsLockManager.setReelsLockEnabled(context, isChecked)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NearBlack,
                                    checkedTrackColor = accentCoral,
                                    uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB)
                                )
                            )
                        }
                    }
                }
            }

            // YouTube Intelligent AI Content Filter Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(accentCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = accentCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "YouTube AI Content Filter",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = textPrimary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = accentCyan.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "AI ACCESSIBILITY",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = accentCyan,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Only allows educational lectures & coding tutorials. Automatically blocks songs, movies, gaming & entertainment vlogs.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = textSecondary,
                                            fontSize = 11.5.sp,
                                            lineHeight = 15.sp
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = isYouTubeFilterEnabled,
                                onCheckedChange = { isChecked ->
                                    if (isChecked && !hasAccessibilityPermission) {
                                        pendingAction = { com.example.util.YouTubeStudyGuardManager.setFilterEnabled(context, true) }
                                        showPermissionSheet = true
                                    } else {
                                        com.example.util.YouTubeStudyGuardManager.setFilterEnabled(context, isChecked)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NearBlack,
                                    checkedTrackColor = accentCyan,
                                    uncheckedTrackColor = if (isDark) Color(0xFF374151) else Color(0xFFD1D5DB)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live AI Classifier Tester
                        Surface(
                            color = if (isDark) Color(0xFF131B26) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Test YouTube AI Classifier",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = accentCyan,
                                        fontSize = 11.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Sample Quick Chips
                                val sampleQueries = listOf(
                                    "Physics Class 12 — Electrostatics Full Chapter",
                                    "Arijit Singh Latest Song",
                                    "JEE Main 2027 Physics Preparation",
                                    "Funny College Vlog"
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    sampleQueries.forEach { sample ->
                                        Surface(
                                            color = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(8.dp),
                                            onClick = {
                                                testYouTubeQuery = sample
                                                testYouTubeResult = com.example.util.YouTubeVideoClassifier.evaluate(sample)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = sample,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 11.sp,
                                                        color = textPrimary
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "Test",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = accentCyan,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                if (testYouTubeResult != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val isEdu = testYouTubeResult!!.isEducational
                                    val badgeColor = if (isEdu) accentLime else accentCoral
                                    val badgeBg = if (isEdu) accentLime.copy(alpha = 0.15f) else accentCoral.copy(alpha = 0.15f)

                                    Surface(
                                        color = badgeBg,
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isEdu) "✅ UNLOCKED (Study Content)" else "🔒 LOCKED (Distraction)",
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        color = badgeColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp
                                                    )
                                                )
                                                Text(
                                                    text = "Confidence: ${(testYouTubeResult!!.confidence * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = textSecondary,
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Category: ${testYouTubeResult!!.category}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = textPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                            Text(
                                                text = "Reason: ${testYouTubeResult!!.reason}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = textSecondary,
                                                    fontSize = 10.5.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Blocked Apps List Section Header
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SELECT APPS TO BLOCK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = accentLime,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "${displayApps.count { it.isBlocked }} Protected",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = accentGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search input for device installed apps
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search installed apps...",
                                color = textSecondary.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = cardBackground,
                            unfocusedContainerColor = cardBackground,
                            focusedBorderColor = accentLime,
                            unfocusedBorderColor = cardBorderColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Social", "Games", "Entertainment", "Messaging", "Blocked").forEach { cat ->
                            val isSelected = selectedCategoryTab == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) accentLime else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) accentLime else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedCategoryTab = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Color.White else textPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bulk action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1DE53935))
                                .border(1.dp, Color(0x60E53935), RoundedCornerShape(12.dp))
                                .clickable { onBlockAllSocial() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Block All Social",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = accentCoral,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable { onUnblockAll() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Unblock All",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // Apps List
            if (displayApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching apps found",
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(displayApps, key = { it.id }) { app ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        accentBorder = app.isBlocked
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (app.isBlocked) Color(0x35E53935) else Color(0x22FFFFFF)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val fallback = if (app.category.contains("Gaming") || app.category.contains("Game")) {
                                        Icons.Default.Games
                                    } else if (app.category.contains("Video") || app.category.contains("Media")) {
                                        Icons.Default.Videocam
                                    } else {
                                        Icons.Default.Lock
                                    }
                                    AppIconView(
                                        packageName = app.packageName,
                                        contentDescription = app.name,
                                        modifier = Modifier.size(26.dp),
                                        fallbackVector = fallback,
                                        fallbackTint = if (app.isBlocked) accentCoral else textSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = app.name,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = textPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = app.category,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = textSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = app.isBlocked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked && !isAllPermissionsGranted) {
                                        pendingAction = { onToggleAppBlocked(app.packageName, true) }
                                        showPermissionSheet = true
                                    } else {
                                        onToggleAppBlocked(app.packageName, isChecked)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NearBlack,
                                    checkedTrackColor = accentLime,
                                    uncheckedThumbColor = Color(0xFFB0BEC5),
                                    uncheckedTrackColor = Color(0x35FFFFFF)
                                )
                            )
                        }
                    }
                }
            }

            // How Android App Blocking Works Explainer Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accentLime.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = accentLime,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "How Does Android App Blocking Work?",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Focus Shield runs a lightweight background monitor using Android's official Usage Access API. When a blacklisted package enters the foreground during an active focus timer, Focus Shield instantly launches the full-screen lock overlay, preventing mindless scrolling until the session finishes.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                }
            }
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
    }
}
