package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.AppDailyLimitEntity
import com.example.R
import com.example.ui.components.FeatureIcon
import com.example.ui.components.AddEditAppLimitSheet
import com.example.ui.components.FocusShieldPermissions
import com.example.ui.components.GlassCard
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import com.example.util.DailyUsagePipeline
import com.example.util.AppIconView
import com.example.util.DeviceAppInfo
import com.example.util.EssentialAppsGuard
import com.example.util.InstalledAppsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppUsageRankItem(
    val appInfo: DeviceAppInfo,
    val usageMinutesToday: Int,
    val limit: AppDailyLimitEntity?
)

// Owner's Full App Redesign token system (dark + light aware).
// Dark values are exactly the clean-redesign tokens.
private object Limits {
    val Lime @Composable get() = focivoTokens().accent
    val Surface @Composable get() = focivoTokens().surface
    val Line @Composable get() = focivoTokens().line
    val Text @Composable get() = focivoTokens().text
    val Muted @Composable get() = focivoTokens().muted
    val TrackOff @Composable get() = focivoTokens().trackOff
    val ThumbOff @Composable get() = focivoTokens().thumbOff
    val Bg @Composable get() = focivoTokens().bg
    val SerifItalic @Composable get() = SerifItalicFontFamily
}

/** The design's toggle: 48x28, dark track / grey thumb, lime when on. */
@Composable
private fun LimitsShieldToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .size(48.dp, 28.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        colors = SwitchDefaults.colors(
            checkedThumbColor = Limits.Bg,
            checkedTrackColor = Limits.Lime,
            uncheckedThumbColor = Limits.ThumbOff,
            uncheckedTrackColor = Limits.TrackOff,
            uncheckedBorderColor = Color.Transparent,
            checkedBorderColor = Color.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitsScreen(
    limits: List<AppDailyLimitEntity>,
    installedApps: List<DeviceAppInfo>,
    onBack: () -> Unit,
    onSaveLimit: (AppDailyLimitEntity) -> Unit,
    onDeleteLimit: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasUsagePermission by remember {
        mutableStateOf(FocusShieldPermissions.hasUsageStatsPermission(context))
    }

    var appUsageMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    // v1.1.4d (item D3): the overview total comes from the SAME pipeline
    // snapshot as the per-app map (and as the Study header), so the number
    // here and the number on the Study page can never disagree.
    var snapshotTotalMillis by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableStateOf("All Apps") } // "All Apps", "With Limits", "Most Used", "Social", "Games"

    var selectedLimitForEdit by remember { mutableStateOf<AppDailyLimitEntity?>(null) }
    var preselectedAppForAdd by remember { mutableStateOf<DeviceAppInfo?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    // Live permission monitoring on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = FocusShieldPermissions.hasUsageStatsPermission(context)
                hasUsagePermission = granted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Refresh usage statistics whenever permission is available or resumed.
    // v1.3.3: reads from DailyUsagePipeline — the same source as the
    // Study-top header and the global budget.
    LaunchedEffect(hasUsagePermission) {
        if (hasUsagePermission) {
            withContext(Dispatchers.IO) {
                val snapshot = DailyUsagePipeline.getSnapshot(context, forceRefresh = true)
                val usage = snapshot?.perAppMillis
                    ?.mapValues { (_, ms) -> (ms / (1000 * 60)).toInt() }
                    ?: emptyMap()
                withContext(Dispatchers.Main) {
                    appUsageMap = usage
                    snapshotTotalMillis = snapshot?.totalMillis
                }
            }
        }
    }

    // Filter available apps (excluding essential protected apps)
    val nonEssentialApps = remember(installedApps) {
        installedApps.filterNot { EssentialAppsGuard.isEssentialApp(context, it.packageName) }
    }

    // Build unified ranked list
    val rankedItems = remember(nonEssentialApps, limits, appUsageMap) {
        val limitMap = limits.associateBy { it.appPackage.lowercase() }
        val itemsList = nonEssentialApps.map { app ->
            val limit = limitMap[app.packageName.lowercase()]
            val realUsage = appUsageMap[app.packageName] ?: 0
            // v1.1.4b (item B, clauses 10-11): without the usage grant there
            // are no real minutes — and stored limit minutes are stale
            // leftovers from a previous granted session (or revocation).
            // Never surface them as today's usage.
            val effectiveUsage =
                if (hasUsagePermission) maxOf(limit?.minutesUsedToday ?: 0, realUsage) else 0
            AppUsageRankItem(
                appInfo = app,
                usageMinutesToday = effectiveUsage,
                limit = limit
            )
        }
        // Primary sort: Most screen time spent today first (Descending)
        // Secondary: Apps with limits first, then alphabetical
        itemsList.sortedWith(
            compareByDescending<AppUsageRankItem> { it.usageMinutesToday }
                .thenByDescending { it.limit != null }
                .thenBy { it.appInfo.appName.lowercase() }
        )
    }

    // Filter by tab and search query
    val filteredItems = remember(rankedItems, searchQuery, selectedFilterTab) {
        rankedItems.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.appInfo.appName.contains(searchQuery, ignoreCase = true) ||
                    item.appInfo.packageName.contains(searchQuery, ignoreCase = true)

            val matchesTab = when (selectedFilterTab) {
                "With Limits" -> item.limit != null
                "Most Used" -> item.usageMinutesToday > 0
                "Social" -> item.appInfo.category == InstalledAppsManager.CATEGORY_SOCIAL
                "Games" -> item.appInfo.category == InstalledAppsManager.CATEGORY_GAMES
                "Entertainment" -> item.appInfo.category == InstalledAppsManager.CATEGORY_ENTERTAINMENT
                else -> true
            }

            matchesSearch && matchesTab
        }
    }

    // v1.1.4d (item D3): the day's top app — the first entry of the
    // usage-ranked list with real time on it. Highlighted only in the
    // default view (All Apps tab, no search), where "most time today" is
    // unambiguous; filtered/searched views don't carry the badge.
    val topAppPackage = remember(rankedItems, hasUsagePermission, selectedFilterTab, searchQuery) {
        if (hasUsagePermission && selectedFilterTab == "All Apps" && searchQuery.isBlank()) {
            rankedItems.firstOrNull { it.usageMinutesToday > 0 }?.appInfo?.packageName
        } else {
            null
        }
    }

    val totalAllocatedMinutes = remember(limits) {
        limits.filter { it.isEnabled }.sumOf { it.dailyLimitMinutes }
    }

    val activeLimitsCount = remember(limits) {
        limits.count { it.isEnabled }
    }

    val totalDeviceScreenTimeMins = remember(rankedItems) {
        rankedItems.sumOf { it.usageMinutesToday }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Per-App Daily Limits",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = Limits.SerifItalic,
                                fontWeight = FontWeight.Bold,
                                color = Limits.Text
                            )
                        )
                        Text(
                            text = "Continuous time budget per app",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Limits.Muted,
                                fontSize = 12.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Limits.Text
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Limits.Bg
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedLimitForEdit = null
                    preselectedAppForAdd = null
                    showAddSheet = true
                },
                containerColor = Limits.Lime,
                contentColor = Color(0xFF021207),
                modifier = Modifier.testTag("add_app_limit_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Limit"
                )
            }
        },
        containerColor = Limits.Bg,
        modifier = modifier.testTag("app_limits_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Permission Prompt Card (If Usage Access not granted)
            if (!hasUsagePermission) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("usage_access_permission_banner"),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0x30F59E0B),
                                        Color(0x15111411)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FeatureIcon(
                                drawableRes = R.drawable.ic3d_key,
                                contentDescription = null,
                                size = 44.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Usage Access",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Limits.Text
                                    )
                                )
                                Text(
                                    text = "Required to see live screen time spent on each app & enforce limits",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Limits.Muted,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                FocusShieldPermissions.openUsageAccessSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Limits.Lime,
                                contentColor = Color(0xFF021207)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_usage_permission_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Grant Usage Permission",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Overview Header GlassCard
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TODAY'S SCREEN TIME & LIMITS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Limits.Lime,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.76.sp,
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // v1.1.4d (item D3): total = the pipeline snapshot's
                        // device total (identical source to the Study
                        // header). Without the grant: "--", never a
                        // dressed-up "0m logged" (Round B honesty rule).
                        val overviewMins = snapshotTotalMillis?.let { (it / 60_000L).toInt() }
                            ?: totalDeviceScreenTimeMins
                        val screenHrs = overviewMins / 60
                        val screenMins = overviewMins % 60
                        val formattedScreenTime = when {
                            !hasUsagePermission -> "--"
                            screenHrs > 0 -> "${screenHrs}h ${screenMins}m logged"
                            else -> "${screenMins}m logged"
                        }
                        Text(
                            text = formattedScreenTime,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Limits.Text,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val hrs = totalAllocatedMinutes / 60
                        val mins = totalAllocatedMinutes % 60
                        val formattedBudget = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                        Text(
                            text = "$activeLimitsCount active limits ($formattedBudget budget)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Limits.Muted,
                                fontSize = 12.sp
                            )
                        )
                    }

                    FeatureIcon(
                        drawableRes = R.drawable.ic3d_timer,
                        contentDescription = "App Limits",
                        size = 48.dp
                    )
                }
            }

            // 3. Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search apps on device...",
                        fontFamily = PoppinsFontFamily,
                        color = Limits.Muted
                    )
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Limits.Muted)
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Limits.Surface,
                    unfocusedContainerColor = Limits.Surface,
                    focusedBorderColor = Limits.Lime,
                    unfocusedBorderColor = Limits.Line,
                    focusedTextColor = Limits.Text,
                    unfocusedTextColor = Limits.Text
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("app_limits_search_input")
            )

            // 4. Filter Chips Row
            val filterTabs = listOf("All Apps", "With Limits (${limits.size})", "Most Used", "Social", "Games", "Entertainment")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                items(filterTabs) { tabName ->
                    val tabKey = if (tabName.startsWith("With Limits")) "With Limits" else tabName
                    val isSelected = selectedFilterTab == tabKey
                    Surface(
                        onClick = { selectedFilterTab = tabKey },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Limits.Lime.copy(alpha = 0.18f) else Limits.Surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Limits.Lime else Limits.Line
                        ),
                        modifier = Modifier.testTag("filter_tab_$tabKey")
                    ) {
                        Text(
                            text = tabName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Limits.Lime else Limits.Muted
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 5. Ranked App List
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        FeatureIcon(
                            drawableRes = R.drawable.ic3d_shield,
                            contentDescription = null,
                            size = 72.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No Apps Found" else "No matching apps found",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Limits.Text,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Try changing your filter tab." else "Try a different search keyword.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Limits.Muted
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredItems, key = { it.appInfo.packageName }) { item ->
                        AppRankAndLimitCard(
                            item = item,
                            hasUsagePermission = hasUsagePermission,
                            isTopApp = item.appInfo.packageName == topAppPackage,
                            onSetLimit = {
                                selectedLimitForEdit = null
                                preselectedAppForAdd = item.appInfo
                                showAddSheet = true
                            },
                            onEditLimit = { limit ->
                                selectedLimitForEdit = limit
                                preselectedAppForAdd = item.appInfo
                                showAddSheet = true
                            },
                            onDeleteLimit = { limitId ->
                                onDeleteLimit(limitId)
                            },
                            onToggleEnabled = { limitId, isChecked ->
                                onToggleEnabled(limitId, isChecked)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddEditAppLimitSheet(
            limitToEdit = selectedLimitForEdit,
            installedApps = installedApps,
            preselectedApp = preselectedAppForAdd,
            onDismiss = {
                showAddSheet = false
                selectedLimitForEdit = null
                preselectedAppForAdd = null
            },
            onSave = { updatedLimit ->
                onSaveLimit(updatedLimit)
                showAddSheet = false
                selectedLimitForEdit = null
                preselectedAppForAdd = null
            },
            onDelete = { limitId ->
                onDeleteLimit(limitId)
                showAddSheet = false
                selectedLimitForEdit = null
                preselectedAppForAdd = null
            }
        )
    }
}

@Composable
fun AppRankAndLimitCard(
    item: AppUsageRankItem,
    // v1.1.4b (item B, clause 10): the no-grant state renders "--" for the
    // spent time instead of "0m" dressed as data.
    hasUsagePermission: Boolean = true,
    // v1.1.4d (item D3): the day's most-used app carries a lime
    // "MOST TIME TODAY" chip so the answer to "kis app me jyada time
    // gaya" is readable at a glance.
    isTopApp: Boolean = false,
    onSetLimit: () -> Unit,
    onEditLimit: (AppDailyLimitEntity) -> Unit,
    onDeleteLimit: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val limit = item.limit
    val appInfo = item.appInfo
    val usedMins = item.usageMinutesToday

    val hrs = usedMins / 60
    val mins = usedMins % 60
    // v1.1.4b (item B, clause 10): no grant -> "--", never "0m".
    val formattedSpentToday = when {
        !hasUsagePermission -> "--"
        hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
        hrs > 0 -> "${hrs}h"
        mins > 0 -> "${mins}m"
        else -> "0m"
    }

    val hasLimit = limit != null
    val isLimitEnabled = limit?.isEnabled == true

    val limitHrs = (limit?.dailyLimitMinutes ?: 0) / 60
    val limitMins = (limit?.dailyLimitMinutes ?: 0) % 60
    val limitText = if (limitHrs > 0) "${limitHrs}h ${limitMins}m" else "${limitMins}m"

    // v1.1.4b (item B, clause 10): no grant -> neutral empty bar.
    val progressFraction = if (!hasUsagePermission) {
        0f
    } else if (hasLimit && limit!!.dailyLimitMinutes > 0) {
        (usedMins.toFloat() / limit.dailyLimitMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val progressColor = when {
        !hasLimit || !isLimitEnabled -> Color.Gray
        progressFraction >= 1.0f -> Color(0xFFEF4444)
        progressFraction >= 0.8f -> Color(0xFFF59E0B)
        else -> Limits.Lime
    }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (limit != null) {
                    onEditLimit(limit)
                } else {
                    onSetLimit()
                }
            }
            .testTag("app_item_${appInfo.packageName}"),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // App Icon + Title + Category
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AppIconView(
                        packageName = appInfo.packageName,
                        contentDescription = appInfo.appName,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = appInfo.appName.ifBlank { appInfo.packageName },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Limits.Text,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isTopApp) {
                                Surface(
                                    color = Limits.Lime.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.testTag("top_app_badge")
                                ) {
                                    Text(
                                        text = "MOST TIME TODAY",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            color = Limits.Lime,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.8.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Surface(
                                color = Limits.Line,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = appInfo.category,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Limits.Muted,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Time spent today badge
                            Surface(
                                color = if (usedMins > 0) Limits.Lime.copy(alpha = 0.15f) else Color(0x15FFFFFF),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    // v1.1.4d: no grant -> "--" here too; the
                                    // old else-branch printed "0m today" even
                                    // without the grant (a dressed-up zero).
                                    text = if (!hasUsagePermission) "--" else if (usedMins > 0) "$formattedSpentToday today" else "0m today",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = if (usedMins > 0) Limits.Lime else Limits.Muted,
                                        fontWeight = if (usedMins > 0) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Limit Controls or "+ Set Limit" Button
                if (limit != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LimitsShieldToggle(
                            checked = limit.isEnabled,
                            onCheckedChange = { isChecked ->
                                onToggleEnabled(limit.id, isChecked)
                            },
                            testTag = "limit_toggle_${limit.id}"
                        )
                        IconButton(onClick = { onDeleteLimit(limit.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Limit",
                                tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onSetLimit,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Limits.Lime
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Limits.Lime),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("set_limit_btn_${appInfo.packageName}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Set Limit",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // If limit is set, show progress bar and limit badges
            if (limit != null) {
                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Used today: ${usedMins}m / ${limitText}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = progressColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "${(progressFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = progressColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = progressColor,
                        trackColor = Limits.TrackOff,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Badges / Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Strict Mode badge
                    Surface(
                        color = if (limit.strictModeEnabled) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF3B82F6).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (limit.strictModeEnabled) Icons.Default.Lock else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (limit.strictModeEnabled) Color(0xFFEF4444) else Color(0xFF3B82F6),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (limit.strictModeEnabled) "Strict Block" else "Soft Reminder",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = if (limit.strictModeEnabled) Color(0xFFEF4444) else Color(0xFF3B82F6),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    // Emergency uses badge
                    Surface(
                        color = Limits.Lime.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${limit.emergencyUsesRemainingToday}/${limit.emergencyUsesAllowed} Emergency Uses",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = Limits.Lime,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

