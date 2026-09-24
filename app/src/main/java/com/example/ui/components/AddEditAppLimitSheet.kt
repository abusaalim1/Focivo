package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppDailyLimitEntity
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.util.AppIconView
import com.example.util.DeviceAppInfo
import com.example.util.EssentialAppsGuard
import com.example.util.InstalledAppsManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditAppLimitSheet(
    limitToEdit: AppDailyLimitEntity?,
    installedApps: List<DeviceAppInfo>,
    preselectedApp: DeviceAppInfo? = null,
    onDismiss: () -> Unit,
    onSave: (AppDailyLimitEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedAppPackage by remember {
        mutableStateOf(limitToEdit?.appPackage ?: preselectedApp?.packageName ?: "")
    }
    var selectedAppName by remember {
        mutableStateOf(limitToEdit?.appName ?: preselectedApp?.appName ?: "")
    }
    var limitMinutes by remember { mutableIntStateOf(limitToEdit?.dailyLimitMinutes ?: 60) }
    var emergencyAllowed by remember { mutableIntStateOf(limitToEdit?.emergencyUsesAllowed ?: 2) }
    var showReminders by remember { mutableStateOf(limitToEdit?.showReminders ?: true) }
    var strictModeEnabled by remember { mutableStateOf(limitToEdit?.strictModeEnabled ?: false) }

    var appSearchQuery by remember { mutableStateOf("") }
    var isSelectingApp by remember {
        mutableStateOf(limitToEdit == null && preselectedApp == null)
    }

    // Filter out essential apps completely from selection
    val availableApps = remember(installedApps) {
        installedApps.filterNot { EssentialAppsGuard.isEssentialApp(context, it.packageName) }
    }

    val filteredApps = remember(availableApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) availableApps else availableApps.filter {
            it.appName.contains(appSearchQuery, ignoreCase = true) ||
            it.packageName.contains(appSearchQuery, ignoreCase = true)
        }
    }

    // Category grouping: Social Media, Games, Entertainment/Video, Messaging, Other
    val categorizedApps = remember(filteredApps) {
        filteredApps.groupBy { it.category }
    }

    val expandedCategories = remember {
        mutableStateMapOf<String, Boolean>().apply {
            put(InstalledAppsManager.CATEGORY_SOCIAL, true)
            put(InstalledAppsManager.CATEGORY_GAMES, true)
            put(InstalledAppsManager.CATEGORY_ENTERTAINMENT, true)
            put(InstalledAppsManager.CATEGORY_MESSAGING, true)
            put(InstalledAppsManager.CATEGORY_OTHER, false)
        }
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("add_edit_app_limit_sheet")
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (limitToEdit == null) "Set App Time Limit" else "Edit Limit: $selectedAppName",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF0F4ED)
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFFA0A89E))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isSelectingApp) {
                // Step 1: Select App
                Text(
                    text = "Select an App to Restrict",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        color = RegainLimePrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    placeholder = { Text("Search installed apps...", fontFamily = PoppinsFontFamily, color = Color(0xFFA0A89E)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFA0A89E)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1B201B),
                        unfocusedContainerColor = Color(0xFF1B201B),
                        focusedBorderColor = RegainLimePrimary,
                        unfocusedBorderColor = Color(0x358CE000),
                        focusedTextColor = Color(0xFFF0F4ED),
                        unfocusedTextColor = Color(0xFFF0F4ED)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InstalledAppsManager.ALL_CATEGORIES.forEach { category ->
                        val apps = categorizedApps[category] ?: emptyList()
                        if (apps.isNotEmpty()) {
                            val isExpanded = expandedCategories[category] ?: false

                            item(key = "cat_header_$category") {
                                Surface(
                                    color = Color(0xFF1D221C),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedCategories[category] = !isExpanded }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = category,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    color = RegainLimePrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = RegainLimePrimary.copy(alpha = 0.15f),
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = "${apps.size}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = PoppinsFontFamily,
                                                        color = RegainLimePrimary,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = RegainLimePrimary
                                        )
                                    }
                                }
                            }

                            if (isExpanded) {
                                items(apps, key = { it.packageName }) { app ->
                                    GlassCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedAppPackage = app.packageName
                                                selectedAppName = app.appName
                                                isSelectingApp = false
                                            },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AppIconView(
                                                packageName = app.packageName,
                                                contentDescription = app.appName,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = app.appName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontFamily = PoppinsFontFamily,
                                                        color = Color(0xFFF0F4ED),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                )
                                                Text(
                                                    text = app.packageName,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = PoppinsFontFamily,
                                                        color = Color(0xFFA0A89E),
                                                        fontSize = 11.sp
                                                    )
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Select",
                                                tint = RegainLimePrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Step 2: Configure Time Budget & Settings
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIconView(
                                packageName = selectedAppPackage,
                                contentDescription = selectedAppName,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = selectedAppName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Color(0xFFF0F4ED),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = selectedAppPackage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Color(0xFFA0A89E),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        if (limitToEdit == null) {
                            OutlinedButton(
                                onClick = { isSelectingApp = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RegainLimePrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RegainLimePrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Change", fontFamily = PoppinsFontFamily, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time Presets
                Text(
                    text = "Daily Time Budget",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = Color(0xFFF0F4ED),
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(15, 30, 45, 60, 90, 120, 180)
                    presets.forEach { mins ->
                        val isSelected = limitMinutes == mins
                        val label = if (mins < 60) "${mins}m" else if (mins % 60 == 0) "${mins/60}h" else "${mins/60}h ${mins%60}m"

                        Surface(
                            color = if (isSelected) RegainLimePrimary else Color(0xFF1D221C),
                            contentColor = if (isSelected) Color(0xFF021207) else Color(0xFFF0F4ED),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) RegainLimePrimary else Color(0x358CE000)
                            ),
                            modifier = Modifier.clickable { limitMinutes = mins }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Minute Stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Custom minutes:",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            color = Color(0xFFA0A89E)
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (limitMinutes > 5) limitMinutes -= 5 },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1D221C))
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = RegainLimePrimary)
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        val hrs = limitMinutes / 60
                        val m = limitMinutes % 60
                        val displayStr = if (hrs > 0) "${hrs}h ${m}m" else "${m}m"
                        Text(
                            text = displayStr,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                color = RegainLimePrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        IconButton(
                            onClick = { if (limitMinutes < 720) limitMinutes += 5 },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1D221C))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = RegainLimePrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Emergency Uses Allowance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Daily Emergency Passes",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFF0F4ED),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "15-minute extensions allowed per day",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFA0A89E),
                                fontSize = 11.sp
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (emergencyAllowed > 0) emergencyAllowed-- },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1D221C))
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = RegainLimePrimary)
                        }

                        Text(
                            text = "$emergencyAllowed",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFFF0F4ED),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        IconButton(
                            onClick = { if (emergencyAllowed < 5) emergencyAllowed++ },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1D221C))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = RegainLimePrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Switches (Reminders & Strict Mode)
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show Warnings & Reminders",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Color(0xFFF0F4ED),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = "Notifies at 80% usage and ~15m remaining",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Color(0xFFA0A89E),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Switch(
                                checked = showReminders,
                                onCheckedChange = { showReminders = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF021207),
                                    checkedTrackColor = RegainLimePrimary,
                                    uncheckedThumbColor = Color(0xFFA0A89E),
                                    uncheckedTrackColor = Color(0xFF262C24)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Strict Blocking Overlay",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Color(0xFFF0F4ED),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = "Forcefully blocks app screen when limit is reached",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        color = Color(0xFFA0A89E),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Switch(
                                checked = strictModeEnabled,
                                onCheckedChange = { strictModeEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF021207),
                                    checkedTrackColor = RegainLimePrimary,
                                    uncheckedThumbColor = Color(0xFFA0A89E),
                                    uncheckedTrackColor = Color(0xFF262C24)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (limitToEdit != null) {
                        OutlinedButton(
                            onClick = { onDelete(limitToEdit.id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            val newLimit = AppDailyLimitEntity(
                                id = limitToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                                userId = limitToEdit?.userId ?: "",
                                appPackage = selectedAppPackage,
                                appName = selectedAppName,
                                dailyLimitMinutes = limitMinutes,
                                emergencyUsesAllowed = emergencyAllowed,
                                emergencyUsesRemainingToday = limitToEdit?.emergencyUsesRemainingToday ?: emergencyAllowed,
                                showReminders = showReminders,
                                strictModeEnabled = strictModeEnabled,
                                minutesUsedToday = limitToEdit?.minutesUsedToday ?: 0,
                                lastResetDate = limitToEdit?.lastResetDate ?: "",
                                isEnabled = limitToEdit?.isEnabled ?: true,
                                emergencyBypassUntilMs = limitToEdit?.emergencyBypassUntilMs ?: 0L,
                                reminder80SentToday = limitToEdit?.reminder80SentToday ?: false,
                                reminder15MinSentToday = limitToEdit?.reminder15MinSentToday ?: false,
                                limit100SentToday = limitToEdit?.limit100SentToday ?: false
                            )
                            onSave(newLimit)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RegainLimePrimary,
                            contentColor = Color(0xFF021207)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        enabled = selectedAppPackage.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (limitToEdit == null) "Create Limit" else "Save Changes",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
