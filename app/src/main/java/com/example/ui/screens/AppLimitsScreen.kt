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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.AppDailyLimitEntity
import com.example.ui.components.AddEditAppLimitSheet
import com.example.ui.components.FocusShieldPermissions
import com.example.ui.components.GlassCard
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.util.AppDailyLimitsManager
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

    // Refresh usage statistics whenever permission is available or resumed
    LaunchedEffect(hasUsagePermission) {
        if (hasUsagePermission) {
            withContext(Dispatchers.IO) {
                val usage = AppDailyLimitsManager.queryAllAppsUsageMinutesToday(context)
                withContext(Dispatchers.Main) {
                    appUsageMap = usage
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
            val effectiveUsage = maxOf(limit?.minutesUsedToday ?: 0, realUsage)
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
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF0F4ED)
                            )
                        )
                        Text(
                            text = "Continuous time budget per app",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFA0A89E),
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
                            tint = Color(0xFFF0F4ED)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111411)
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
                containerColor = RegainLimePrimary,
                contentColor = Color(0xFF021207),
                modifier = Modifier.testTag("add_app_limit_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Limit"
                )
            }
        },
        containerColor = Color(0xFF111411),
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
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x28F59E0B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Usage Access",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF0F4ED)
                                    )
                                )
                                Text(
                                    text = "Required to see live screen time spent on each app & enforce limits",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Color(0xFFA0A89E),
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
                                containerColor = RegainLimePrimary,
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
                                color = RegainLimePrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val screenHrs = totalDeviceScreenTimeMins / 60
                        val screenMins = totalDeviceScreenTimeMins % 60
                        val formattedScreenTime = if (screenHrs > 0) "${screenHrs}h ${screenMins}m logged" else "${screenMins}m logged"
                        Text(
                            text = formattedScreenTime,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFF0F4ED),
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
                                color = Color(0xFFA0A89E),
                                fontSize = 12.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(RegainLimePrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = RegainLimePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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
                        color = Color(0xFFA0A89E)
                    )
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFFA0A89E))
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1B201B),
                    unfocusedContainerColor = Color(0xFF1B201B),
                    focusedBorderColor = RegainLimePrimary,
                    unfocusedBorderColor = Color(0x358CE000),
                    focusedTextColor = Color(0xFFF0F4ED),
                    unfocusedTextColor = Color(0xFFF0F4ED)
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
                        color = if (isSelected) RegainLimePrimary.copy(alpha = 0.18f) else Color(0xFF1B201B),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) RegainLimePrimary else Color(0x208CE000)
                        ),
                        modifier = Modifier.testTag("filter_tab_$tabKey")
                    ) {
                        Text(
                            text = tabName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) RegainLimePrimary else Color(0xFFA0A89E)
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
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(RegainLimePrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = RegainLimePrimary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No Apps Found" else "No matching apps found",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFF0F4ED),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Try changing your filter tab." else "Try a different search keyword.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFA0A89E)
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
    val formattedSpentToday = when {
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

    val progressFraction = if (hasLimit && limit!!.dailyLimitMinutes > 0) {
        (usedMins.toFloat() / limit.dailyLimitMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val progressColor = when {
        !hasLimit || !isLimitEnabled -> Color.Gray
        progressFraction >= 1.0f -> Color(0xFFEF4444)
        progressFraction >= 0.8f -> Color(0xFFF59E0B)
        else -> RegainLimePrimary
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
                                color = Color(0xFFF0F4ED),
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = Color(0xFF202620),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = appInfo.category,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Color(0xFFA0A89E),
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Time spent today badge
                            Surface(
                                color = if (usedMins > 0) RegainLimePrimary.copy(alpha = 0.15f) else Color(0x15FFFFFF),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (usedMins > 0) "⏱️ $formattedSpentToday today" else "0m today",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = if (usedMins > 0) RegainLimePrimary else Color(0xFFA0A89E),
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
                        Switch(
                            checked = limit.isEnabled,
                            onCheckedChange = { isChecked ->
                                onToggleEnabled(limit.id, isChecked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF021207),
                                checkedTrackColor = RegainLimePrimary,
                                uncheckedThumbColor = Color(0xFFA0A89E),
                                uncheckedTrackColor = Color(0xFF262C24)
                            ),
                            modifier = Modifier.testTag("limit_toggle_${limit.id}")
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
                            contentColor = RegainLimePrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RegainLimePrimary),
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
                        trackColor = Color(0xFF262C24),
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
                        color = RegainLimePrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🚨 ${limit.emergencyUsesRemainingToday}/${limit.emergencyUsesAllowed} Emergency Uses",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = RegainLimePrimary,
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

