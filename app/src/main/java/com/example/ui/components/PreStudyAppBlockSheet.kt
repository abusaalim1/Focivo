package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.util.AiStudyGuardManager
import com.example.util.DeviceAppInfo
import com.example.util.EssentialAppsGuard
import com.example.util.InstalledAppsManager
import com.example.util.PermissionUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreStudyAppBlockSheet(
    currentBlockedList: String = "",
    onConfirmAndStart: (selectedPackages: Set<String>, dontShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 1. Live, reactive permission states with instant resume detection
    var hasAccessibility by remember {
        mutableStateOf(AiStudyGuardManager.isAccessibilityPermissionGranted(context))
    }
    var hasOverlay by remember {
        mutableStateOf(PermissionUtils.hasOverlayPermission(context))
    }
    var hasUsageStats by remember {
        mutableStateOf(PermissionUtils.hasUsageStatsPermission(context))
    }

    fun refreshPermissions() {
        hasAccessibility = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
        hasOverlay = PermissionUtils.hasOverlayPermission(context)
        hasUsageStats = PermissionUtils.hasUsageStatsPermission(context)
    }

    // Refresh immediately when returning from system Settings screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isAllPermissionsGranted = hasOverlay && hasUsageStats
    val needsPermissions = !isAllPermissionsGranted

    val initialBlockedSet = remember(currentBlockedList) {
        currentBlockedList.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    // 2. High-speed app loading: Use pre-cached list instantly (0ms delay)
    var allInstalled by remember {
        mutableStateOf<List<DeviceAppInfo>>(
            InstalledAppsManager.getCachedApps() ?: InstalledAppsManager.getInstalledApps(context, initialBlockedSet)
        )
    }
    var isLoadingApps by remember {
        mutableStateOf(false)
    }

    // Filter out essential apps (Phone, Contacts, Camera, Gallery, YouTube, Payment/Banking)
    val availableApps: List<DeviceAppInfo> = remember(allInstalled) {
        allInstalled.filterNot { EssentialAppsGuard.isEssentialApp(context, it.packageName) }
    }

    val selectedPackages = remember(availableApps) {
        mutableStateMapOf<String, Boolean>().apply {
            val initialSanitized = EssentialAppsGuard.sanitizeBlockedPackages(context, initialBlockedSet)
            if (initialSanitized.isNotEmpty()) {
                initialSanitized.forEach { put(it, true) }
            } else {
                // Default pre-select Social and Games
                availableApps.filter {
                    it.category == InstalledAppsManager.CATEGORY_SOCIAL ||
                    it.category == InstalledAppsManager.CATEGORY_GAMES
                }.forEach { put(it.packageName, true) }
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredApps: List<DeviceAppInfo> by remember(availableApps, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) availableApps else availableApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val categorizedApps: Map<String, List<DeviceAppInfo>> by remember(filteredApps) {
        derivedStateOf {
            filteredApps.groupBy { it.category }
        }
    }

    val expandedCategories = remember {
        mutableStateMapOf<String, Boolean>().apply {
            put(InstalledAppsManager.CATEGORY_SOCIAL, true)
            put(InstalledAppsManager.CATEGORY_GAMES, true)
            put(InstalledAppsManager.CATEGORY_ENTERTAINMENT, true)
            put(InstalledAppsManager.CATEGORY_MESSAGING, false)
            put(InstalledAppsManager.CATEGORY_OTHER, false)
        }
    }

    val totalSelectedCount by remember {
        derivedStateOf { selectedPackages.count { it.value } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141814),
        contentColor = Color(0xFFF0F4ED)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .testTag("pre_study_app_block_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RegainMascotView(
                        pose = MascotPose.STUDYING,
                        width = 38.dp,
                        height = 38.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Apps to Block During Study",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF0F4ED),
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = "Distracting apps will be locked until timer ends",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFA0A89E),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFA0A89E))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Live Permissions Box: Shows missing permissions with direct access + instant "Granted" confirmation
            AnimatedVisibility(
                visible = needsPermissions,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = Color(0xFF241B12),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Permissions Required for Full Protection",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFCC80)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To strictly lock apps and trigger Mascot warnings, please enable missing permissions:",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFFFE0B2),
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Live interactive permission buttons with immediate status feedback
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Accessibility / AI Guard
                            PermissionStatusButton(
                                title = "AI Guard",
                                isGranted = hasAccessibility,
                                onClick = {
                                    AiStudyGuardManager.openAccessibilitySettings(context)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Overlay / Draw on screen
                            PermissionStatusButton(
                                title = "Screen Over",
                                isGranted = hasOverlay,
                                onClick = {
                                    PermissionUtils.openOverlaySettings(context)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Usage Stats
                            PermissionStatusButton(
                                title = "Usage Stat",
                                isGranted = hasUsageStats,
                                onClick = {
                                    PermissionUtils.openUsageStatsSettings(context)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Green success banner when all permissions are successfully enabled
            AnimatedVisibility(
                visible = isAllPermissionsGranted,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = Color(0xFF142416),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RegainLimePrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = RegainLimePrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All Block Permissions Active (AI Guard, Overlay, Usage)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = RegainLimePrimary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Quick actions & Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps...", color = Color(0xFFA0A89E), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFFA0A89E))
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFFA0A89E))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RegainLimePrimary,
                    unfocusedBorderColor = Color(0x358CE000),
                    focusedContainerColor = Color(0xFF1D221C),
                    unfocusedContainerColor = Color(0xFF1D221C),
                    focusedTextColor = Color(0xFFF0F4ED),
                    unfocusedTextColor = Color(0xFFF0F4ED)
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Category Multi-select Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$totalSelectedCount apps selected",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        color = RegainLimePrimary,
                        fontWeight = FontWeight.Bold
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            availableApps.filter {
                                it.category == InstalledAppsManager.CATEGORY_SOCIAL ||
                                it.category == InstalledAppsManager.CATEGORY_GAMES ||
                                it.category == InstalledAppsManager.CATEGORY_ENTERTAINMENT
                            }.forEach { selectedPackages[it.packageName] = true }
                        }
                    ) {
                        Text("Select Distractions", color = RegainLimePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { selectedPackages.clear() }
                    ) {
                        Text("Clear All", color = Color(0xFFA0A89E), fontSize = 11.sp)
                    }
                }
            }

            // 4. Smooth, fast non-blocking App List
            if (isLoadingApps && availableApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = RegainLimePrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Loading installed apps...",
                            color = Color(0xFFA0A89E),
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf(
                        InstalledAppsManager.CATEGORY_SOCIAL to "📱 Social Media",
                        InstalledAppsManager.CATEGORY_GAMES to "🎮 Games",
                        InstalledAppsManager.CATEGORY_ENTERTAINMENT to "🎬 Video & Entertainment",
                        InstalledAppsManager.CATEGORY_MESSAGING to "💬 Messaging",
                        InstalledAppsManager.CATEGORY_OTHER to "🌐 Other Apps"
                    )

                    categories.forEach { (catKey, catTitle) ->
                        val appsInCat = categorizedApps[catKey] ?: emptyList()
                        if (appsInCat.isNotEmpty()) {
                            val isExpanded = expandedCategories[catKey] ?: true
                            val selectedInCat = appsInCat.count { selectedPackages[it.packageName] == true }

                            item(key = "header_$catKey") {
                                Surface(
                                    color = Color(0xFF1B201A),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x208CE000)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedCategories[catKey] = !(expandedCategories[catKey] ?: true)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = catTitle,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFF0F4ED),
                                                    fontSize = 13.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = if (selectedInCat > 0) RegainLimePrimary.copy(alpha = 0.2f) else Color(0x15FFFFFF),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "$selectedInCat/${appsInCat.size}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = PoppinsFontFamily,
                                                        color = if (selectedInCat > 0) RegainLimePrimary else Color(0xFFA0A89E),
                                                        fontSize = 10.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = {
                                                    val allSelected = appsInCat.all { selectedPackages[it.packageName] == true }
                                                    appsInCat.forEach {
                                                        selectedPackages[it.packageName] = !allSelected
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = if (selectedInCat == appsInCat.size) "Deselect" else "All",
                                                    color = RegainLimePrimary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = Color(0xFFA0A89E),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (isExpanded) {
                                items(
                                    items = appsInCat,
                                    key = { it.packageName }
                                ) { appInfo ->
                                    val isChecked = selectedPackages[appInfo.packageName] == true
                                    AppRowItem(
                                        appInfo = appInfo,
                                        isChecked = isChecked,
                                        onToggle = { newChecked ->
                                            selectedPackages[appInfo.packageName] = newChecked
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Helper function to ensure permissions are granted before starting block
            fun handleBlockAction(savePermanently: Boolean) {
                if (!hasOverlay) {
                    android.widget.Toast.makeText(context, "Please grant Display Over Other Apps permission to show block shield", android.widget.Toast.LENGTH_SHORT).show()
                    PermissionUtils.openOverlaySettings(context)
                    return
                }
                if (!hasUsageStats) {
                    android.widget.Toast.makeText(context, "Please grant Usage Access permission to track study sessions", android.widget.Toast.LENGTH_SHORT).show()
                    PermissionUtils.openUsageStatsSettings(context)
                    return
                }
                val chosen = selectedPackages.filter { it.value }.keys.toSet()
                onConfirmAndStart(chosen, savePermanently)
            }

            // Action Buttons: "Block" (this session only) vs "Save & Block" (save permanently for auto-block)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. "Block" Button
                Button(
                    onClick = { handleBlockAction(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A342B),
                        contentColor = Color(0xFFE0E6DC)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("block_current_session_button")
                ) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp), tint = RegainLimePrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Block ($totalSelectedCount)",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                // 2. "Save & Block" Button
                Button(
                    onClick = { handleBlockAction(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegainLimePrimary,
                        contentColor = Color(0xFF021207)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("save_and_block_button")
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save & Block",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cancel button
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("cancel_pre_study_sheet_button")
            ) {
                Text(
                    text = "Cancel",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Color(0xFFA0A89E)
                )
            }
        }
    }
}

/**
 * Button showing permission status:
 * - If Granted: Crisp green pill badge "✓ Active"
 * - If Not Granted: Orange actionable button "Enable"
 */
@Composable
private fun PermissionStatusButton(
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isGranted) {
        Surface(
            color = Color(0xFF142B18),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, RegainLimePrimary.copy(alpha = 0.5f)),
            modifier = modifier.height(34.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = RegainLimePrimary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RegainLimePrimary,
                    maxLines = 1
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFFFB74D),
                containerColor = Color(0x20FF9800)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier.height(34.dp)
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * High performance row component for individual apps.
 * Avoids on-the-fly Bitmap conversions during scrolling or recomposition.
 */
@Composable
private fun AppRowItem(
    appInfo: DeviceAppInfo,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        color = if (isChecked) Color(0xFF222B20) else Color(0xFF171B16),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isChecked) RegainLimePrimary.copy(alpha = 0.4f) else Color(0x10FFFFFF)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isChecked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Render pre-converted ImageBitmap directly
            if (appInfo.iconBitmap != null) {
                Image(
                    bitmap = appInfo.iconBitmap,
                    contentDescription = appInfo.appName,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Fit
                )
            } else if (appInfo.icon != null) {
                val fallbackBmp = remember(appInfo.packageName) {
                    try {
                        appInfo.icon.toBitmap(width = 72, height = 72).asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
                if (fallbackBmp != null) {
                    Image(
                        bitmap = fallbackBmp,
                        contentDescription = appInfo.appName,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    AppLetterPlaceholder(appInfo.appName)
                }
            } else {
                AppLetterPlaceholder(appInfo.appName)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = appInfo.appName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF0F4ED),
                    fontSize = 13.sp
                ),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            Checkbox(
                checked = isChecked,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = RegainLimePrimary,
                    uncheckedColor = Color(0xFFA0A89E),
                    checkmarkColor = Color(0xFF021207)
                )
            )
        }
    }
}

@Composable
private fun AppLetterPlaceholder(name: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color(0xFF283028), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = Color(0xFFF0F4ED),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
