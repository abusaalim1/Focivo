package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoTokens
import com.example.util.AiStudyGuardManager
import com.example.util.DeviceAdminGuard
import com.example.util.StrictModeManager
import com.example.util.YouTubeStudyGuardManager

/**
 * v1.3.3 (ITEM 5 — owner UI order): the AI Guard control cluster on the
 * Study screen, placed LEFT of the START STUDY button.
 *
 * Three icon toggles — Strict Mode, AI Guard, AI YouTube Filter — plus a
 * "screen-time limits" entry that routes to the budget settings
 * ([com.example.util.ScreenTimeBudgetStore.SCREEN_TIME_BUDGETS_ROUTE_ID]).
 *
 * Owner interaction rule, enforced exactly:
 * - Tapping the BUTTON (icon) toggles the feature on/off immediately.
 * - Tapping the TEXT/label next to a toggle opens a premium explainer sheet.
 *
 * State sources (SAME managers as the hubs — no parallel state):
 * - Strict Mode  -> StrictModeManager.isStrictModeState / setStrictModeEnabled
 * - AI Guard     -> AiStudyGuardManager.isAiGuardEnabled / setAiGuardEnabled
 * - YT Filter    -> YouTubeStudyGuardManager.isFilterEnabledState / setFilterEnabled
 *
 * Strict keeps its existing opt-in flow: enabling without the device admin
 * active offers the premium admin explainer (same as the pre-session prompt
 * and the Deep Focus sheet). AI Guard / YT Filter keep the accessibility
 * opt-in dialog when the permission is missing.
 */

// ------------------------------------------------------------------
// Explainer content (pure data — unit-tested for presence/coverage).
// ------------------------------------------------------------------

enum class GuardExplainerKey { STRICT, AI_GUARD, YT_FILTER }

data class GuardExplainerContent(
    val eyebrow: String,
    val headline: String,
    val body: String,
    val points: List<String>,
    val footnote: String
)

/**
 * Concise, honest, English-only copy for each guard toggle. The cluster's
 * label taps open these; the sheets render [GuardExplainerContent].
 */
fun guardExplainerContent(key: GuardExplainerKey): GuardExplainerContent = when (key) {
    GuardExplainerKey.STRICT -> GuardExplainerContent(
        eyebrow = "STRICT MODE",
        headline = "A promise you can't quietly break.",
        body = "When Strict Mode is on, a study session becomes a commitment. " +
            "Pause and Stop are locked while the timer runs — only finishing " +
            "the session ends it. Uninstalling Focivo can also be blocked " +
            "during a strict session.",
        points = listOf(
            "Pause and Stop show a reminder instead of ending the session early.",
            "Uninstall protection (device admin, optional) stops a weak moment from removing Focivo mid-session.",
            "Turn it off freely whenever no session is running — nothing is ever trapped."
        ),
        footnote = "Skipping the device-admin step is fine: Strict Mode keeps working, uninstall protection simply shows OFF."
    )
    GuardExplainerKey.AI_GUARD -> GuardExplainerContent(
        eyebrow = "AI STUDY GUARD",
        headline = "Your sentinel for AI chats and browsers.",
        body = "During a study session, the guard watches ChatGPT, Claude and " +
            "Chrome. Off-topic conversations get one warning with a 3-minute " +
            "grace window — keep chatting off-topic and that app locks for 3 hours.",
        points = listOf(
            "Warns first: a mascot popup plus a heads-up notification.",
            "3-minute grace: return to study topics and no block is applied.",
            "Ignored warnings lock only that app for 3 hours; everything else stays open."
        ),
        footnote = "Needs the accessibility service to read on-screen text. YouTube video filtering is a separate toggle below."
    )
    GuardExplainerKey.YT_FILTER -> GuardExplainerContent(
        eyebrow = "YOUTUBE CONTENT FILTER",
        headline = "Only study-worthy videos get through.",
        body = "During a study session, the filter scores each YouTube video " +
            "you open. Lectures, tutorials and coding walkthroughs pass; " +
            "songs, movies, gaming and vlogs are blocked.",
        points = listOf(
            "Runs only while a study session is active — off-duty YouTube is untouched.",
            "Explicitly opt-in: it never turns itself on.",
            "Watches video titles and the player — Shorts blocking stays a separate lock."
        ),
        footnote = "Needs the accessibility service. It verdict-scores YouTube; it never blocklists the app itself."
    )
}

// ------------------------------------------------------------------
// The cluster.
// ------------------------------------------------------------------

/** Which feature still needs the accessibility opt-in dialog. */
private enum class GuardOptInTarget { AI_GUARD, YT_FILTER }

@Composable
fun GuardControlCluster(
    onOpenScreenTimeBudgets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val T = focivoTokens()
    val lifecycleOwner = LocalLifecycleOwner.current

    val isStrictOn by StrictModeManager.isStrictModeState.collectAsState()
    val isYtFilterOn by YouTubeStudyGuardManager.isFilterEnabledState.collectAsState()

    var isAiGuardOn by remember { mutableStateOf(AiStudyGuardManager.isAiGuardEnabled(context)) }
    var hasAccessibility by remember { mutableStateOf(AiStudyGuardManager.isAccessibilityPermissionGranted(context)) }
    var explainerKey by remember { mutableStateOf<GuardExplainerKey?>(null) }
    var optInTarget by remember { mutableStateOf<GuardOptInTarget?>(null) }
    // v1.3.3: mirrors the existing Strict opt-in flow (pre-session prompt +
    // Deep Focus sheet) — enabling Strict without the device admin active
    // offers the premium admin explainer instead of silently skipping it.
    var showAdminExplainer by remember { mutableStateOf(false) }

    // Same refresh cadence as AiStudyGuardHubSheet: re-read on resume so a
    // permission granted in Settings is reflected immediately.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
                isAiGuardOn = AiStudyGuardManager.isAiGuardEnabled(context)
                YouTubeStudyGuardManager.init(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onStrictIconTap = {
        if (isStrictOn) {
            StrictModeManager.setStrictModeEnabled(context, false)
        } else {
            StrictModeManager.setStrictModeEnabled(context, true)
            if (!DeviceAdminGuard.isAdminActive(context)) {
                showAdminExplainer = true
            }
        }
    }

    val onGuardIconTap = {
        if (isAiGuardOn) {
            AiStudyGuardManager.setAiGuardEnabled(context, false)
            isAiGuardOn = false
        } else if (!hasAccessibility) {
            optInTarget = GuardOptInTarget.AI_GUARD
        } else {
            AiStudyGuardManager.setAiGuardEnabled(context, true)
            isAiGuardOn = true
        }
    }

    val onYtFilterIconTap = {
        if (isYtFilterOn) {
            YouTubeStudyGuardManager.setFilterEnabled(context, false)
        } else if (!hasAccessibility) {
            optInTarget = GuardOptInTarget.YT_FILTER
        } else {
            YouTubeStudyGuardManager.setFilterEnabled(context, true)
        }
    }

    Column(
        modifier = modifier.testTag("guard_control_cluster"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GuardToggleRow(
            icon = Icons.Default.Lock,
            label = "Strict",
            isOn = isStrictOn,
            onIconClick = onStrictIconTap,
            onLabelClick = { explainerKey = GuardExplainerKey.STRICT },
            iconTestTag = "guard_cluster_strict_icon",
            labelTestTag = "guard_cluster_strict_label"
        )
        GuardToggleRow(
            icon = Icons.Default.Shield,
            label = "AI Guard",
            isOn = isAiGuardOn,
            onIconClick = onGuardIconTap,
            onLabelClick = { explainerKey = GuardExplainerKey.AI_GUARD },
            iconTestTag = "guard_cluster_guard_icon",
            labelTestTag = "guard_cluster_guard_label"
        )
        GuardToggleRow(
            icon = Icons.Default.OndemandVideo,
            label = "YT Filter",
            isOn = isYtFilterOn,
            onIconClick = onYtFilterIconTap,
            onLabelClick = { explainerKey = GuardExplainerKey.YT_FILTER },
            iconTestTag = "guard_cluster_ytfilter_icon",
            labelTestTag = "guard_cluster_ytfilter_label"
        )
        // Screen-time limits entry (icon + label both navigate; neutral style).
        GuardToggleRow(
            icon = Icons.Default.Timer,
            label = "Limits",
            stateCaption = "Budgets",
            isOn = null,
            onIconClick = onOpenScreenTimeBudgets,
            onLabelClick = onOpenScreenTimeBudgets,
            iconTestTag = "guard_cluster_limits_icon",
            labelTestTag = "guard_cluster_limits_label",
            contentDescription = "Open screen-time budget settings"
        )
    }

    // Premium explainer sheets — label taps only.
    explainerKey?.let { key ->
        GuardExplainerSheet(
            key = key,
            onDismiss = { explainerKey = null }
        )
    }

    // Accessibility opt-in for AI Guard / YT Filter (same dialog as the hub).
    optInTarget?.let { target ->
        AiStudyAccessibilityOptInDialog(
            onConfirmOptIn = {
                optInTarget = null
                AiStudyGuardManager.openAccessibilitySettings(context)
            },
            onDismiss = { optInTarget = null }
        )
    }

    // Strict's existing opt-in flow: the premium admin explainer.
    if (showAdminExplainer) {
        StrictAdminExplainerSheet(
            onEnable = {
                showAdminExplainer = false
                StrictModeManager.setUninstallProtectionEnabled(context, true)
                DeviceAdminGuard.launchActivation(context)
            },
            onOptOut = {
                showAdminExplainer = false
                StrictModeManager.setUninstallProtectionEnabled(context, false)
            },
            onDismiss = { showAdminExplainer = false }
        )
    }
}

/**
 * One cluster row: icon button (tap = immediate toggle) + label (tap =
 * explainer). The button itself carries icon + on/off state only — no text
 * clutter. isOn = null renders the neutral (non-toggle) style.
 */
@Composable
private fun GuardToggleRow(
    icon: ImageVector,
    label: String,
    isOn: Boolean?,
    onIconClick: () -> Unit,
    onLabelClick: () -> Unit,
    iconTestTag: String,
    labelTestTag: String,
    stateCaption: String? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    val on = isOn == true
    val caption = stateCaption ?: if (on) "On" else "Off"

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isOn == null -> T.surface2
                        on -> T.accent
                        else -> T.surface2
                    }
                )
                .border(
                    1.dp,
                    when {
                        isOn == null -> T.line
                        on -> T.accent
                        else -> T.chipBorder
                    },
                    CircleShape
                )
                .clickable(onClick = onIconClick)
                .testTag(iconTestTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription ?: "$label toggle",
                tint = when {
                    isOn == null -> T.muted
                    on -> T.onAccent
                    else -> T.muted
                },
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Column(
            modifier = Modifier
                .clickable(onClick = onLabelClick)
                .testTag(labelTestTag)
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = label,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                lineHeight = 15.sp,
                color = if (isOn == null || on) T.text else T.muted
            )
            Text(
                text = caption,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = if (on) T.accentText else T.muted
            )
        }
    }
}

// ------------------------------------------------------------------
// Premium explainer sheet.
// ------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardExplainerSheet(
    key: GuardExplainerKey,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    val content = guardExplainerContent(key)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = T.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier.testTag("guard_explainer_sheet_${key.name.lowercase()}")
    ) {
        GuardExplainerContent(
            content = content,
            onDismiss = onDismiss
        )
    }
}

/**
 * The sheet's inner content, factored out so Roborazzi can render it
 * standalone (ModalBottomSheet windows capture poorly in screenshot tests).
 */
@Composable
fun GuardExplainerContent(
    content: GuardExplainerContent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 12.dp)
            .testTag("guard_explainer_content"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SheetDragHandle()
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(T.accent.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(T.accent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = content.eyebrow, style = focivoEyebrow())
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = content.headline,
            fontFamily = SerifItalicFontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center,
            color = T.text
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = content.body,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.5.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            color = T.muted
        )
        Spacer(modifier = Modifier.height(10.dp))

        content.points.forEach { point ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(T.accent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = point,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = T.muted
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = content.footnote,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            color = T.muted.copy(alpha = 0.85f)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RegainLimePrimary,
                contentColor = RegainLimeDeepText
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("guard_explainer_got_it")
        ) {
            Text(
                text = "Got it",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}
