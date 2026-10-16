package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.InstalledAppsManager

/**
 * v1.1 — dedicated Blocked Apps list. This is the NEW destination of the
 * Study Hub's "Blocked Apps" row (previously it opened the whole Focus
 * Shield settings page).
 *
 * - Header with the live blocked count
 * - One row per blocked app: real app icon + name + remove/unblock action
 * - "Add apps" entry reuses the EXISTING app picker ([PreStudyAppBlockSheet]
 *   in addMode) — no rebuilt picker, no new storage: the list is
 *   userPreferences.blockedAppsList, the same source the shield reads.
 *
 * The Focus Shield page itself is untouched and stays reachable from
 * Guard Hub / Profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedAppsListSheet(
    blockedPackages: Set<String>,
    onRemoveApp: (packageName: String) -> Unit,
    onSaveBlockedApps: (packages: Set<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddPicker by remember { mutableStateOf(false) }

    val T = focivoTokens()
    val isDark = T.isDark
    val lime = T.accent
    val sheetBackground = T.bg
    val textPrimary = T.text
    val textSecondary = T.muted
    val rowBg = T.surface
    val rowBorder = T.accent.copy(alpha = 0.2f)
    val deepLimeText = T.accentText

    // Resolve real app names + icons from the installed-apps cache.
    val cachedApps = remember {
        InstalledAppsManager.getCachedApps() ?: InstalledAppsManager.getInstalledApps(context, blockedPackages)
    }
    val appByPackage = remember(cachedApps) { cachedApps.associateBy { it.packageName } }
    val sortedPackages = remember(blockedPackages) { blockedPackages.sorted() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("blocked_apps_list_sheet"),
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
                FeatureIcon(
                    drawableRes = R.drawable.ic3d_shield,
                    contentDescription = null,
                    size = 44.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Blocked Apps",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = textPrimary
                    )
                    Text(
                        text = "Locked automatically during focus sessions",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(lime)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${blockedPackages.size}",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NearBlack
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
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
                // Add entry — opens the existing picker in addMode
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(lime.copy(alpha = 0.12f))
                            .border(1.dp, lime.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .pressFeedback()
                            .clickable { showAddPicker = true }
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                            .testTag("blocked_apps_add_entry"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(lime),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = NearBlack,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add apps to block",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = textPrimary
                            )
                            Text(
                                text = "Pick from your installed apps",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 11.5.sp,
                                color = textSecondary
                            )
                        }
                    }
                }

                if (sortedPackages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(rowBg)
                                .border(1.dp, rowBorder, RoundedCornerShape(20.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.mascot_empty_state),
                                contentDescription = null,
                                modifier = Modifier.size(88.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No blocked apps yet",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap \"Add apps to block\" above — distracting apps get locked the moment a focus session starts.",
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(sortedPackages, key = { it }) { pkg ->
                        val info = appByPackage[pkg]
                        val appName = info?.appName ?: pkg.substringAfterLast(".")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(rowBg)
                                .border(1.dp, rowBorder, RoundedCornerShape(18.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bitmap = remember(pkg) {
                                try {
                                    info?.iconBitmap
                                        ?: info?.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = appName,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isDark) Color(0xFF283028) else Color(0xFFE8EDE4)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = appName.take(1).uppercase(),
                                        fontFamily = PoppinsFontFamily,
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appName,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = textPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Blocked during focus",
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xFFE05252).copy(alpha = 0.12f))
                                    .border(
                                        1.dp,
                                        Color(0xFFE05252).copy(alpha = 0.4f),
                                        CircleShape
                                    )
                                    .pressFeedback()
                                    .clickable { onRemoveApp(pkg) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Unblock",
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFE05252)
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(28.dp)) }
            }
        }
    }

    // The EXISTING app picker, reused in addMode (single save button).
    if (showAddPicker) {
        PreStudyAppBlockSheet(
            currentBlockedList = blockedPackages.joinToString(","),
            onConfirmAndStart = { chosen, _ ->
                onSaveBlockedApps(chosen)
                showAddPicker = false
            },
            onDismiss = { showAddPicker = false },
            addMode = true
        )
    }
}
