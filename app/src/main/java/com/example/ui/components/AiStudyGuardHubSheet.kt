package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.ui.components.FeatureIcon
import com.example.ui.theme.BalooBrandFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.AiStudyGuardManager
import com.example.util.GeminiContentClassifier
import com.example.util.PermissionUtils
import com.example.util.ShortsLockManager
import com.example.util.StrictModeManager
import com.example.util.YouTubeStudyGuardManager
import com.example.util.brain.FocivoBrainCorrections
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated AI Study Guard section: mascot-guarded hub for everything the guard does —
 * master switch, accessibility permission status, Shorts/Reels locks,
 * strict mode, punishment history and a "how it works" explainer. Reuses the existing
 * managers; no parallel state. (v1.2.2: the YouTube AI filter is no longer merged
 * into the master switch — it is its own explicit toggle in the Focus Shield hub.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudyGuardHubSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAiGuardEnabled by remember { mutableStateOf(AiStudyGuardManager.isAiGuardEnabled(context)) }
    var hasAccessibility by remember { mutableStateOf(AiStudyGuardManager.isAccessibilityPermissionGranted(context)) }
    // v1.1.6: liveness — distinguishes a truly listening service from a stale "Granted" flag
    var guardListening by remember { mutableStateOf(AiStudyGuardManager.isGuardListeningRecently(context)) }
    var isStudyActiveNow by remember { mutableStateOf(AiStudyGuardManager.isStudyPeriodActive(context)) }
    var batteryExempt by remember { mutableStateOf(PermissionUtils.isBatteryOptimizationDisabled(context)) }
    var notifGranted by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
    // v1.2.3 (owner 2026-10-16): consolidated "Guard state" row inputs —
    // every switch that decides whether the guard can fire, in one glance.
    var ytFilterEffective by remember { mutableStateOf(YouTubeStudyGuardManager.isFilterEnabled(context)) }
    var geminiKeyPresent by remember { mutableStateOf(GeminiContentClassifier.isApiKeyPresent()) }
    var showAutostartGuide by remember { mutableStateOf(false) }
    var showOptInDialog by remember { mutableStateOf(false) }

    val isShortsReelsBlocked by ShortsLockManager.isShortsReelsBlockState.collectAsState()
    val isStrictModeEnabled by StrictModeManager.isStrictModeState.collectAsState()
    val punishmentLogs by AiStudyGuardManager.punishmentLogs.collectAsState()
    // v1.1.9: diagnostic activity log
    val guardActivity by AiStudyGuardManager.guardActivity.collectAsState()
    var activityExpanded by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted || PermissionUtils.hasNotificationPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
                guardListening = AiStudyGuardManager.isGuardListeningRecently(context)
                isAiGuardEnabled = AiStudyGuardManager.isAiGuardEnabled(context)
                isStudyActiveNow = AiStudyGuardManager.isStudyPeriodActive(context)
                batteryExempt = PermissionUtils.isBatteryOptimizationDisabled(context)
                notifGranted = PermissionUtils.hasNotificationPermission(context)
                ShortsLockManager.init(context)
                YouTubeStudyGuardManager.init(context)
                ytFilterEffective = YouTubeStudyGuardManager.isFilterEnabled(context)
                geminiKeyPresent = GeminiContentClassifier.isApiKeyPresent()
                AiStudyGuardManager.loadLocalLogs(context)
                // v1.1.9: refresh the diagnostic activity log
                AiStudyGuardManager.loadGuardActivity(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val T = focivoTokens()
    val isDark = T.isDark
    val sheetBackground = T.bg
    val textPrimary = T.text
    val textSecondary = T.muted
    val accentLime = T.accentText

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
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
                Text(
                    text = "AI Study Guard",
                    fontFamily = BalooBrandFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Guard mascot header in a glass card with shield badge
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (isDark) 0.10f else 0.55f),
                                        accentLime.copy(alpha = 0.10f)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Image(
                                    painter = painterResource(id = R.drawable.mascot_guard),
                                    contentDescription = "AI Study Guard mascot",
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                FeatureIcon(
                                    drawableRes = R.drawable.ic3d_shield,
                                    contentDescription = null,
                                    size = 30.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Study Sentinel",
                                    fontFamily = BalooBrandFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    color = textPrimary
                                )
                                Text(
                                    text = "Watches your AI chats and browser during study — off-topic chats get a warning first; ignored warnings lock the app for 3 hours. YouTube video filtering is a separate toggle (Focus Shield hub).",
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    color = textSecondary
                                )
                            }
                        }
                    }
                }

                // 1. AI Study Guard master — AI chat + browser detection.
                // (v1.2.2: no longer merged with the YouTube AI filter — that is
                // its own explicit toggle in the Focus Shield hub.)
                item {
                    GuardSectionTitle("AI Study Guard", textPrimary)
                    GuardCard {
                        GuardSwitchRow(
                            title = "AI Study Guard",
                            description = if (hasAccessibility)
                                "AI chat + browser detection during study"
                            else
                                "Needs permissions below to work",
                            checked = isAiGuardEnabled,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentLime = accentLime,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasAccessibility) {
                                    showOptInDialog = true
                                } else {
                                    AiStudyGuardManager.setAiGuardEnabled(context, enabled)
                                    isAiGuardEnabled = enabled
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "The guard needs these permissions:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary
                        )
                        GuardPermissionRow(
                            label = "Accessibility service",
                            granted = hasAccessibility,
                            textSecondary = textSecondary,
                            accentLime = accentLime,
                            onFix = { AiStudyGuardManager.openAccessibilitySettings(context) }
                        )
                        GuardPermissionRow(
                            label = "Battery optimization OFF",
                            granted = batteryExempt,
                            textSecondary = textSecondary,
                            accentLime = accentLime,
                            onFix = { PermissionUtils.openBatteryOptimizationSettings(context) }
                        )
                        GuardPermissionRow(
                            label = "Notifications",
                            granted = notifGranted,
                            textSecondary = textSecondary,
                            accentLime = accentLime,
                            onFix = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    PermissionUtils.openAppNotificationSettings(context)
                                }
                            }
                        )
                        // Autostart guide (no reliable API — per-brand manual steps)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAutostartGuide = !showAutostartGuide }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF8A65))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Autostart",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
                                )
                                Text(
                                    text = "Manual step — tap for your phone's guide",
                                    fontSize = 11.5.sp,
                                    color = textSecondary
                                )
                            }
                            Icon(
                                imageVector = if (showAutostartGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (showAutostartGuide) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentLime.copy(alpha = 0.08f))
                                    .padding(12.dp)
                            ) {
                                val steps = listOf(
                                    "Xiaomi / Redmi / Poco" to "Settings → Apps → Manage apps → Focivo → Autostart ON",
                                    "Oppo / Realme" to "Settings → App management → Focivo → Allow auto-start",
                                    "Samsung" to "Settings → Apps → Focivo → Battery → Unrestricted",
                                    "Vivo" to "Settings → Battery → Background power consumption → allow Focivo"
                                )
                                steps.forEach { (brand, step) ->
                                    Text(
                                        text = brand,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                    Text(
                                        text = step,
                                        fontSize = 12.sp,
                                        color = textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Shorts & Reels Block — ONE merged toggle for both
                item {
                    GuardSectionTitle("Distraction Locks", textPrimary)
                    GuardCard {
                        GuardSwitchRow(
                            title = "Shorts & Reels Block",
                            description = "Blocks the YouTube Shorts player and Instagram Reels viewer during study",
                            checked = isShortsReelsBlocked,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentLime = accentLime,
                            onCheckedChange = { enabled ->
                                ShortsLockManager.setShortsReelsBlockEnabled(context, enabled)
                            }
                        )
                    }
                }

                // Discipline
                item {
                    GuardSectionTitle("Discipline", textPrimary)
                    GuardCard {
                        GuardSwitchRow(
                            title = "Strict Mode",
                            description = "Protects the app from being uninstalled during study",
                            checked = isStrictModeEnabled,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentLime = accentLime,
                            onCheckedChange = { StrictModeManager.setStrictModeEnabled(context, it) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Flow: 1st non-study detection → warning with the mascot → keep going → the app locks for 3 hours. Returning to study topics clears the warning.",
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = textSecondary
                        )
                    }
                }

                // Punishment history
                item {
                    GuardSectionTitle("Recent Warnings & Blocks", textPrimary)
                    GuardCard {
                        if (punishmentLogs.isEmpty()) {
                            Text(
                                text = "No warnings yet — keep it that way!",
                                fontSize = 13.sp,
                                color = textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                        } else {
                            punishmentLogs.take(12).forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (log.status == "blocked") Icons.Default.Lock else Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = if (log.status == "blocked") Color(0xFFE05252) else accentLime,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = friendlyPackageName(log.app_package) + " · " + log.status.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = formatLogDate(log.warned_at) + (log.block_duration_minutes?.let { " · locked ${it}m" } ?: ""),
                                            fontSize = 11.5.sp,
                                            color = textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // How it works
                item {
                    GuardSectionTitle("How it works", textPrimary)
                    GuardCard {
                        val steps = listOf(
                            "1. Turn on AI Study Guard and grant the permissions above — it arms automatically during focus timers and study schedules.",
                            "2. It watches ChatGPT, Claude, Gemini, Copilot, Perplexity and your browser — only during study.",
                            "3. Off-topic AI chats get a warning first; YouTube video filtering is a separate toggle in the Focus Shield hub.",
                            "4. Shorts & Reels Block stops the Shorts player and Reels viewer during study.",
                            "5. Ignore the warning and keep going → that app locks for 3 hours. Genuine study doubts stay allowed."
                        )
                        steps.forEach { step ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = accentLime,
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = step, fontSize = 12.5.sp, lineHeight = 17.sp, color = textSecondary)
                            }
                        }
                    }
                }


                // Pipeline self-test — bypasses the classifier, runs the real
                // warning -> 3-hour lock flow with a fake message
                item {
                    GuardSectionTitle("Test", textPrimary)
                    GuardCard {
                        Text(
                            text = "Runs the real warning-to-3-hour-lock pipeline with a fake test message — no classifier needed. The AI Guard master switch must be ON.",
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(accentLime)
                                .clickable {
                                    AiStudyGuardManager.onNonStudyDetected(
                                        context = context,
                                        packageName = "com.test.guard",
                                        messageText = "test: movie dekhega kya bhai?",
                                        reason = "Manual pipeline test (Test Guard button)"
                                    )
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Test Guard Pipeline",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "First tap shows the mascot warning. Tapping again after the warning locks the test app for 3 hours.",
                            fontSize = 11.5.sp,
                            color = textSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // v1.2.3 (owner 2026-10-16): single consolidated "Guard state"
                // block — every switch that decides whether the guard can
                // fire, in one glance. The YouTube content filter row shows
                // the EFFECTIVE state (toggle + permission + study period);
                // the toggle itself lives in the Focus Shield hub and is
                // never auto-enabled. Gemini key is boolean-only, never the key.
                item {
                    GuardSectionTitle("Guard state", textPrimary)
                    GuardCard {
                        GuardStatusRow(
                            label = "Accessibility service",
                            stateText = if (hasAccessibility) "Connected" else "OFF",
                            ok = hasAccessibility,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentLime = accentLime
                        )
                        // v1.1.6: liveness — "Connected" can be a stale settings flag;
                        // this shows whether the service is actually receiving events.
                        if (hasAccessibility) {
                            Spacer(modifier = Modifier.height(8.dp))
                            GuardStatusRow(
                                label = "Service events",
                                stateText = if (guardListening) "Listening now" else "No recent events",
                                ok = guardListening,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                accentLime = accentLime
                            )
                            if (!guardListening) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Service granted but silent — toggle accessibility OFF then ON once.",
                                    fontSize = 11.5.sp,
                                    color = textSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        GuardStatusRow(
                            label = "Study period",
                            stateText = if (isStudyActiveNow) "Active now" else "Inactive",
                            ok = isStudyActiveNow,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentLime = accentLime
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GuardStatusRow(
                            label = "AI Guard master",
                            stateText = if (isAiGuardEnabled) "ON" else "OFF",
                            ok = isAiGuardEnabled,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentLime = accentLime
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GuardStatusRow(
                            label = "YouTube content filter",
                            stateText = if (ytFilterEffective) "ON" else "OFF",
                            ok = ytFilterEffective,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentLime = accentLime
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Separate toggle in the Focus Shield hub — never auto-enabled.",
                            fontSize = 11.5.sp,
                            color = textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GuardStatusRow(
                            label = "Shorts & Reels lock",
                            stateText = if (isShortsReelsBlocked) "ON" else "OFF",
                            ok = isShortsReelsBlocked,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentLime = accentLime
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GuardStatusRow(
                            label = "Gemini key",
                            stateText = if (geminiKeyPresent) "Present" else "Unavailable",
                            ok = geminiKeyPresent,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            accentLime = accentLime
                        )
                        if (!geminiKeyPresent) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Uncertain titles are allowed (fail-open) until a key is present.",
                                fontSize = 11.5.sp,
                                color = textSecondary
                            )
                        }
                        if (isAiGuardEnabled && !hasAccessibility) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Accessibility is OFF — the guard will not work",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE05252)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Open settings",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentLime,
                                modifier = Modifier.clickable {
                                    AiStudyGuardManager.openAccessibilitySettings(context)
                                }
                            )
                        }
                    }
                }

                // v1.2.3 (owner 2026-10-16): the single most recent YouTube
                // title evaluation — what the chain last decided, glanceable.
                item {
                    GuardSectionTitle("Last YouTube evaluation", textPrimary)
                    GuardCard {
                        val lastEval by AiStudyGuardManager.lastYouTubeEvaluation.collectAsState()
                        if (lastEval.ts == 0L) {
                            Text(
                                text = "No YouTube evaluation yet. Play a video during an active study period with the YouTube content filter on.",
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                color = textSecondary
                            )
                        } else {
                            Text(
                                text = lastEval.title.ifBlank { "(no title extracted)" },
                                fontFamily = SerifItalicFontFamily,
                                fontSize = 16.sp,
                                lineHeight = 21.sp,
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            GuardEvalRow(
                                label = "Player evidence",
                                value = if (lastEval.playerEvidence) "Yes" else "No",
                                valueColor = if (lastEval.playerEvidence) textPrimary else Color(0xFFE05252),
                                textSecondary = textSecondary
                            )
                            GuardEvalRow(
                                label = "Title source",
                                value = lastEval.titleSource.ifBlank { "—" },
                                valueColor = textPrimary,
                                textSecondary = textSecondary
                            )
                            GuardEvalRow(
                                label = "p_ent band",
                                value = lastEval.pEntBand.ifBlank { "—" },
                                valueColor = textPrimary,
                                textSecondary = textSecondary
                            )
                            GuardEvalRow(
                                label = "Verdict",
                                value = lastEval.verdict.ifBlank { "—" },
                                valueColor = when (lastEval.verdict) {
                                    "NON_STUDY" -> Color(0xFFE05252)
                                    "STUDY" -> accentLime
                                    else -> Color(0xFFFF8A65)
                                },
                                textSecondary = textSecondary
                            )
                            GuardEvalRow(
                                label = "Action",
                                value = lastEval.action.ifBlank { "—" },
                                valueColor = textPrimary,
                                textSecondary = textSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Evaluated at " + try {
                                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastEval.ts))
                                } catch (_: Exception) { "--:--" },
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                            // v1.3.0 (Worker E): user corrections — layer 4 of
                            // the on-device brain. The override is recorded
                            // against the FULL title and checked FIRST on
                            // future judgments.
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Wrong verdict? Correct it — your call is remembered first next time.",
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                color = textSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            var correctionNote by remember(lastEval.ts) { mutableStateOf<String?>(null) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val corrTitle = lastEval.fullTitle.ifBlank { lastEval.title }
                                CorrectionPill(
                                    label = "Was study",
                                    borderColor = accentLime,
                                    textColor = textPrimary,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    FocivoBrainCorrections.record(context, corrTitle, true)
                                    correctionNote = "Saved — treated as study from now on."
                                }
                                CorrectionPill(
                                    label = "Not study",
                                    borderColor = Color(0xFFE05252),
                                    textColor = textPrimary,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    FocivoBrainCorrections.record(context, corrTitle, false)
                                    correctionNote = "Saved — treated as non-study from now on."
                                }
                            }
                            if (correctionNote != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = correctionNote!!,
                                    fontSize = 11.5.sp,
                                    color = accentLime
                                )
                            }
                        }
                    }
                }

                // v1.1.9: diagnostic — recent guard activity. Lets the owner screenshot
                // exactly where detection dies (each evaluation incl. skips is logged).
                item {
                    GuardSectionTitle("Recent guard activity", textPrimary)
                    GuardCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activityExpanded = !activityExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Diagnostics log",
                                fontSize = 13.sp,
                                color = textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${guardActivity.size}",
                                fontSize = 12.sp,
                                color = textSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                if (activityExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (activityExpanded) "Collapse" else "Expand",
                                tint = textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (activityExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (guardActivity.isEmpty()) {
                                Text(
                                    text = "No guard activity yet — start a timer, then use YouTube or an AI chat app.",
                                    fontSize = 12.sp,
                                    color = textSecondary
                                )
                            } else {
                                guardActivity.forEach { entry ->
                                    GuardActivityRow(entry, textPrimary, textSecondary, accentLime)
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Clear log",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentLime,
                                modifier = Modifier.clickable { AiStudyGuardManager.clearGuardActivity(context) }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showOptInDialog) {
        AiStudyAccessibilityOptInDialog(
            onConfirmOptIn = {
                showOptInDialog = false
                AiStudyGuardManager.openAccessibilitySettings(context)
            },
            onDismiss = { showOptInDialog = false }
        )
    }
}

@Composable
private fun GuardSectionTitle(text: String, color: Color) {
    Text(
        text = text,
        fontFamily = BalooBrandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = color
    )
}

/**
 * Shared glass card — reuses the app's central [GlassCard] style
 * (translucent surface, 1dp light border, soft shadow, top highlight).
 */
@Composable
private fun GuardCard(
    content: @Composable () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun GuardStatusRow(
    label: String,
    stateText: String,
    ok: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accentLime: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (ok) accentLime else Color(0xFFFF8A65))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stateText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) textPrimary else Color(0xFFE05252)
        )
    }
}

/**
 * v1.3.0 (Worker E): small bordered pill button for the Guard Hub
 * correction overrides ("Was study" / "Not study").
 */
@Composable
private fun CorrectionPill(
    label: String,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
private fun GuardEvalRow(
    label: String,
    value: String,
    valueColor: Color,
    textSecondary: Color
) {    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun GuardActivityRow(
    entry: AiStudyGuardManager.GuardActivityEntry,
    textPrimary: Color,
    textSecondary: Color,
    accentLime: Color
) {
    val timeStr = try {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.ts))
    } catch (_: Exception) {
        "--:--"
    }
    val verdictColor = when (entry.verdict) {
        "NON_STUDY" -> Color(0xFFE05252)
        "STUDY" -> accentLime
        "SKIPPED" -> Color(0xFFFF8A65)
        else -> textSecondary
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = timeStr, fontSize = 11.5.sp, color = textSecondary)
            Text(text = " · ", fontSize = 11.5.sp, color = textSecondary)
            Text(
                text = entry.app,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            Text(text = " · ", fontSize = 11.5.sp, color = textSecondary)
            Text(
                text = entry.verdict,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = verdictColor
            )
        }
        val detail = buildString {
            if (entry.stage.isNotBlank()) append(entry.stage)
            if (entry.reason.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(entry.reason)
            }
            if (entry.snippet.isNotBlank()) {
                if (isNotEmpty()) append(" — ")
                append("“").append(entry.snippet).append("”")
            }
        }
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                fontSize = 11.sp,
                color = textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun GuardPermissionRow(
    label: String,
    granted: Boolean,
    textSecondary: Color,
    accentLime: Color,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (granted) accentLime else Color(0xFFFF8A65))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (granted) textSecondary else Color(0xFFE05252)
            )
            Text(
                text = if (granted) "Granted" else "Missing — guard may not work",
                fontSize = 11.5.sp,
                color = textSecondary
            )
        }
        if (!granted) {
            Text(
                text = "Fix",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentLime,
                modifier = Modifier
                    .clickable { onFix() }
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun GuardSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accentLime: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
            Text(text = description, fontSize = 12.sp, color = textSecondary)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentLime,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFC9CFC4)
            )
        )
    }
}

private fun friendlyPackageName(pkg: String): String {
    return when {
        pkg.contains("chatgpt") || pkg.contains("openai") -> "ChatGPT"
        pkg.contains("claude") || pkg.contains("anthropic") -> "Claude"
        pkg.contains("bard") || pkg.contains("gemini") -> "Gemini"
        pkg.contains("copilot") -> "Copilot"
        pkg.contains("perplexity") -> "Perplexity"
        pkg.contains("youtube") -> "YouTube"
        pkg.contains("instagram") -> "Instagram"
        else -> pkg.substringAfterLast(".")
    }
}

private fun formatLogDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "recently"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val date = parser.parse(iso) ?: return "recently"
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        "recently"
    }
}
