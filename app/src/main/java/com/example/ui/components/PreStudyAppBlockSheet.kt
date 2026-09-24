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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.util.AiStudyGuardManager
import com.example.util.DeviceAppInfo
import com.example.util.EssentialAppsGuard
import com.example.util.InstalledAppsManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PreStudyAppBlockSheet(
    currentBlockedList: String = "",
    onConfirmAndStart: (selectedPackages: Set<String>, dontShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hasAccessibility = remember { AiStudyGuardManager.isAccessibilityPermissionGranted(context) }
    val hasOverlay = remember { FocusShieldPermissions.hasOverlayPermission(context) }
    val hasUsageStats = remember { FocusShieldPermissions.hasUsageStatsPermission(context) }

    val needsPermissions = !hasAccessibility || !hasOverlay || !hasUsageStats

    val initialBlockedSet = remember(currentBlockedList) {
        currentBlockedList.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    val allInstalled = remember {
        InstalledAppsManager.getInstalledApps(context, initialBlockedSet)
    }

    // Filter out essential apps (Phone, Contacts, Camera, Gallery, YouTube, Payment/Banking)
    val availableApps: List<DeviceAppInfo> = remember(allInstalled) {
        allInstalled.filterNot { EssentialAppsGuard.isEssentialApp(context, it.packageName) }
    }

    val selectedPackages = remember {
        val initialSanitized = EssentialAppsGuard.sanitizeBlockedPackages(context, initialBlockedSet)
        mutableStateMapOf<String, Boolean>().apply {
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
    val filteredApps: List<DeviceAppInfo> = remember(availableApps, searchQuery) {
        if (searchQuery.isBlank()) availableApps else availableApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    val categorizedApps: Map<String, List<DeviceAppInfo>> = remember(filteredApps) {
        filteredApps.groupBy { it.category }
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

    val totalSelectedCount = selectedPackages.filter { it.value }.size

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

            // Permission Warning Banner if missing
            if (needsPermissions) {
                Surface(
                    color = Color(0xFF2A1C12),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
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
                            text = "To strictly block apps and trigger Mascot warnings, please enable permissions:",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFFFE0B2),
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!hasAccessibility) {
                                OutlinedButton(
                                    onClick = { AiStudyGuardManager.openAccessibilitySettings(context) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("AI Guard", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!hasOverlay) {
                                OutlinedButton(
                                    onClick = { FocusShieldPermissions.openOverlaySettings(context) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Screen Over", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!hasUsageStats) {
                                OutlinedButton(
                                    onClick = { FocusShieldPermissions.openUsageAccessSettings(context) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Usage Stat", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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
                modifier = Modifier.fillMaxWidth().height(50.dp)
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

            // Categorized List
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
                            items(appsInCat, key = { it.packageName }) { appInfo ->
                                val isChecked = selectedPackages[appInfo.packageName] == true
                                Surface(
                                    color = if (isChecked) Color(0xFF222B20) else Color(0xFF171B16),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isChecked) RegainLimePrimary.copy(alpha = 0.4f) else Color(0x10FFFFFF)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPackages[appInfo.packageName] = !isChecked
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (appInfo.icon != null) {
                                            Image(
                                                bitmap = appInfo.icon.toBitmap().asImageBitmap(),
                                                contentDescription = appInfo.appName,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(Color(0xFF283028), RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = appInfo.appName.take(1).uppercase(),
                                                    color = Color(0xFFF0F4ED),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = appInfo.appName,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFFF0F4ED),
                                                    fontSize = 13.sp
                                                )
                                            )
                                        }

                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedPackages[appInfo.packageName] = checked
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = RegainLimePrimary,
                                                uncheckedColor = Color(0xFFA0A89E),
                                                checkmarkColor = Color(0xFF021207)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            var rememberChoiceChecked by remember { mutableStateOf(false) }

            // Don't show again Checkbox row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { rememberChoiceChecked = !rememberChoiceChecked }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberChoiceChecked,
                    onCheckedChange = { rememberChoiceChecked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = RegainLimePrimary,
                        uncheckedColor = Color(0xFFA0A89E),
                        checkmarkColor = Color(0xFF021207)
                    ),
                    modifier = Modifier.testTag("dont_show_again_checkbox")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Don't show again before study",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Always auto-block selected apps for future study sessions",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 11.sp,
                        color = Color(0xFFA0A89E)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val chosen = selectedPackages.filter { it.value }.keys.toSet()
                        onConfirmAndStart(chosen, rememberChoiceChecked)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegainLimePrimary,
                        contentColor = Color(0xFF021207)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_study_and_block_button")
                ) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (totalSelectedCount > 0) "Start Study & Block ($totalSelectedCount Apps)" else "Start Study Without Blocking",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val chosen = selectedPackages.filter { it.value }.keys.toSet()
                            onConfirmAndStart(chosen, true)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RegainLimePrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RegainLimePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("save_and_dont_show_again_button")
                    ) {
                        Text(
                            text = "Save & Don't Show Again",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("cancel_pre_study_sheet_button")
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color(0xFFA0A89E)
                        )
                    }
                }
            }
        }
    }
}
