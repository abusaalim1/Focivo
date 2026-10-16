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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.FocuslyRepository
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.TimerTheme
import com.example.util.TimerThemeManager

/**
 * v1.5.0 — the ONE-BUTTON hub for the Study screen.
 *
 * The timer screen keeps only: timer presentation + Start/Pause + this menu
 * button. Everything that used to live in scattered rows/cards now lives
 * here: Focus Music, Themes, Blocked Apps, Ultra Focus Settings, AI Study
 * Guard, Customize Timer, Deep Focus mode, and the distraction logger.
 * English-only, zero emoji — feature icons are the existing 3D drawables.
 */
private val StudyAccent = Color(0xFF8FD400)

private val AmbientSounds = listOf("Silent", "Rain", "White noise", "Cafe", "Forest", "Ocean")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyHubSheet(
    onDismiss: () -> Unit,
    isRunning: Boolean,
    ambientSound: String,
    onSelectAmbientSound: (String) -> Unit,
    isDeepFocusEnabled: Boolean,
    onToggleDeepFocus: (Boolean) -> Unit,
    distractionsCount: Int,
    onLogDistraction: (String) -> Unit,
    blockedAppsCount: Int,
    isShieldActive: Boolean = false,
    onOpenBlockedApps: () -> Unit,
    onOpenUltraFocus: () -> Unit,
    onOpenAiGuard: () -> Unit,
    onOpenCustomizeTimer: () -> Unit,
    modifier: Modifier = Modifier,
    // v1.5.0: test hook — screenshot tests open a section directly.
    initialExpandedSection: String? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isDark = isAppInDarkTheme()
    val sheetBackground = T.bg
    val T = focivoTokens()
    val textPrimary = T.text
    val textSecondary = T.muted
    val rowBg = T.surface
    val rowBorder = T.line

    var expandedSection by remember { mutableStateOf(initialExpandedSection) }
    var showDistractionDialog by remember { mutableStateOf(false) }
    var hubLockedTheme by remember { mutableStateOf<TimerTheme?>(null) }

    val selectedTheme by TimerThemeManager.selectedTheme.collectAsState()
    val prefsFlow = remember(context) { FocuslyRepository.getInstance(context).userPreferences }
    val prefs by prefsFlow.collectAsState(initial = null)
    val focusPoints = prefs?.focusPoints ?: 0

    hubLockedTheme?.let { lockedTheme ->
        LockedThemeDialog(
            lockedTheme = lockedTheme,
            focusPoints = focusPoints,
            onDismiss = { hubLockedTheme = null }
        )
    }
    if (showDistractionDialog) {
        DistractionDialog(
            onDismiss = { showDistractionDialog = false },
            onLogDistraction = { type ->
                onLogDistraction(type)
                showDistractionDialog = false
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("study_hub_sheet"),
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
                        text = "Study hub",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = textPrimary
                    )
                    Text(
                        text = "Every control for this session, in one place.",
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
                    HubToggleRow(
                        icon3dRes = R.drawable.ic3d_moon,
                        title = "Deep focus mode",
                        subtitle = "Full lock-in: blocks, guard and protection for this session.",
                        checked = isDeepFocusEnabled,
                        onCheckedChange = onToggleDeepFocus,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "hub_deep_focus_toggle"
                    )
                }

                item {
                    HubSectionLabel(text = "Session setup", textSecondary = textSecondary)
                }

                item {
                    HubActionRow(
                        icon3dRes = R.drawable.ic3d_music,
                        title = "Focus Music",
                        subtitle = "Now playing: $ambientSound",
                        onClick = {
                            expandedSection = if (expandedSection == "music") null else "music"
                        },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "hub_music_row"
                    )
                }
                if (expandedSection == "music") {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(rowBg)
                                .border(1.dp, rowBorder, RoundedCornerShape(18.dp))
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AmbientSounds.forEach { sound ->
                                val isSelected = sound == ambientSound
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressFeedback()
                                        .clickable { onSelectAmbientSound(sound) }
                                        .padding(horizontal = 16.dp, vertical = 11.dp)
                                        .testTag("hub_sound_${sound.lowercase().replace(" ", "_")}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sound,
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = if (isSelected) {
                                            if (isDark) StudyAccent else Color(0xFF4A7A00)
                                        } else textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (isDark) StudyAccent else Color(0xFF4A7A00),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    // Theme row icon: the selected theme's own backdrop art,
                    // like the deep-focus sheet; Aurora gets a live thumbnail.
                    val themeTileRes = resolveThemeBackdropRes(context, selectedTheme)
                    HubActionRow(
                        icon3dRes = themeTileRes,
                        iconCustom = if (themeTileRes == null) {
                            { AuroraBackground(modifier = Modifier.fillMaxSize()) }
                        } else null,
                        title = "Themes",
                        subtitle = "${selectedTheme.name} — backdrop, mascot and glow",
                        onClick = {
                            expandedSection = if (expandedSection == "themes") null else "themes"
                        },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "hub_themes_row"
                    )
                }
                if (expandedSection == "themes") {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(rowBg)
                                .border(1.dp, rowBorder, RoundedCornerShape(18.dp))
                                .padding(14.dp)
                        ) {
                            TimerThemePicker(
                                focusPoints = focusPoints,
                                selectedThemeId = selectedTheme.id,
                                textPrimary = textPrimary,
                                cardBorder = rowBorder,
                                onThemeTap = { theme, isLocked ->
                                    if (isLocked) {
                                        hubLockedTheme = theme
                                    } else {
                                        TimerThemeManager.trySelectTheme(context, theme.id, focusPoints)
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    HubActionRow(
                        icon3dRes = R.drawable.ic3d_timer,
                        title = "Customize timer",
                        subtitle = if (isRunning) "Available when the timer is idle"
                        else "Durations · pomodoro cycle · quick picks",
                        onClick = onOpenCustomizeTimer,
                        enabled = !isRunning,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "hub_customize_row"
                    )
                }

                item {
                    HubSectionLabel(text = "Protection", textSecondary = textSecondary)
                }

                item {
                    HubActionRow(
                        icon3dRes = R.drawable.ic3d_shield,
                        title = "Blocked Apps",
                        subtitle = if (isShieldActive) "Shield active · $blockedAppsCount apps blocked"
                        else if (blockedAppsCount > 0) "$blockedAppsCount apps blocked during focus"
                        else "Choose apps to block during focus",
                        onClick = onOpenBlockedApps,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        badgeCount = blockedAppsCount,
                        testTag = "hub_blocked_apps_row"
                    )
                }
                item {
                    HubActionRow(
                        icon3dRes = R.drawable.ic3d_bolt,
                        title = "Ultra Focus Settings",
                        subtitle = "Strict mode · notifications · uninstall & home-screen",
                        onClick = onOpenUltraFocus,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "hub_ultra_focus_row"
                    )
                }
                item {
                    HubActionRow(
                        iconCustom = {
                            Image(
                                painter = painterResource(id = R.drawable.mascot_guard),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        title = "AI Study Guard",
                        subtitle = "YouTube study mode · AI chat monitor",
                        onClick = onOpenAiGuard,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        testTag = "hub_ai_guard_row"
                    )
                }

                item {
                    HubSectionLabel(text = "Session", textSecondary = textSecondary)
                }

                item {
                    HubActionRow(
                        icon3dRes = R.drawable.ic3d_bell,
                        title = "Log distraction",
                        subtitle = "Note what pulled you away, stay honest",
                        onClick = { showDistractionDialog = true },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        rowBg = rowBg,
                        rowBorder = rowBorder,
                        badgeCount = distractionsCount,
                        testTag = "hub_distraction_row"
                    )
                }

                item { Spacer(modifier = Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun HubSectionLabel(text: String, textSecondary: Color) {
    Text(
        text = text.uppercase(),
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        color = textSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun HubToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    rowBg: Color,
    rowBorder: Color,
    testTag: String,
    icon3dRes: Int? = null,
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
        FeatureIcon(
            drawableRes = icon3dRes,
            contentDescription = null,
            size = 44.dp,
            customContent = iconCustom
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
                checkedTrackColor = StudyAccent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = T.trackOff
            )
        )
    }
}

@Composable
private fun HubActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    rowBg: Color,
    rowBorder: Color,
    testTag: String,
    icon3dRes: Int? = null,
    iconCustom: (@Composable () -> Unit)? = null,
    iconVector: ImageVector? = null,
    badgeCount: Int = 0,
    enabled: Boolean = true
) {
    val isDark = isAppInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(18.dp))
            .background(rowBg)
            .border(1.dp, rowBorder, RoundedCornerShape(18.dp))
            .then(if (enabled) Modifier.pressFeedback().clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FeatureIcon(
            drawableRes = icon3dRes,
            imageVector = iconVector,
            contentDescription = null,
            size = 44.dp,
            customContent = iconCustom,
            vectorCircleColor = if (badgeCount > 0) StudyAccent.copy(alpha = 0.18f)
            else T.surface2,
            vectorTint = if (badgeCount > 0) (if (isDark) StudyAccent else Color(0xFF4A7A00))
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
        if (badgeCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(StudyAccent)
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
