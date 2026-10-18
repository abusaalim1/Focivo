package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
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
 * v1.1.1 (owner UI order): the old four-row guard cluster (Strict / AI Guard /
 * YT Filter / Limits, whose 4th row was overlapped by the bottom nav bar) is
 * REPLACED by a single compact "Block" entry left of the START STUDY button.
 *
 * - Tapping the entry opens [BlockHubSheet]: one premium sheet holding Strict
 *   Mode, AI Guard, AI YouTube Filter (each with toggle + explainer) and the
 *   Screen-time limits entry. Nothing from the old cluster was lost.
 * - Inside the sheet the established interaction language holds: the SWITCH
 *   toggles the feature immediately; tapping the row's LABEL opens the
 *   premium explainer.
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

/**
 * v1.1.1 — the single "Block" entry left of the START STUDY button.
 *
 * Compact row (same 46.dp icon language as the old cluster rows): shield
 * icon + "Block" label + a caption summarising which guards are armed.
 * Tapping opens [BlockHubSheet]. No bottom-nav overlap is possible anymore:
 * this is ONE row, not a four-row column.
 */
@Composable
fun BlockHubEntry(
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val T = focivoTokens()
    val labelColor = if (contentColor != Color.Unspecified) contentColor else T.text

    val isStrictOn by StrictModeManager.isStrictModeState.collectAsState()
    val isYtFilterOn by YouTubeStudyGuardManager.isFilterEnabledState.collectAsState()
    var isAiGuardOn by remember { mutableStateOf(AiStudyGuardManager.isAiGuardEnabled(context)) }

    // Re-read on resume so the caption reflects grants made in Settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAiGuardOn = AiStudyGuardManager.isAiGuardEnabled(context)
                YouTubeStudyGuardManager.init(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val armed = buildList {
        if (isStrictOn) add("Strict")
        if (isAiGuardOn) add("AI Guard")
        if (isYtFilterOn) add("YT Filter")
    }
    val caption = if (armed.isEmpty()) "All off" else armed.joinToString(", ")
    val anyOn = armed.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("block_hub_entry"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (anyOn) T.accent else T.surface2)
                .border(
                    1.dp,
                    if (anyOn) T.accent else T.chipBorder,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Open Block settings",
                tint = if (anyOn) T.onAccent else T.muted,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = "Block",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                lineHeight = 15.sp,
                color = labelColor
            )
            Text(
                text = caption,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = labelColor
            )
        }
    }
}

/**
 * v1.1.1 — the one premium "Block" sheet: Strict Mode, AI Guard and
 * AI YouTube Filter each with an immediate switch plus a label that opens
 * its premium explainer, and the Screen-time limits entry. Everything the
 * old four-row cluster held, inside one sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockHubSheet(
    onOpenScreenTimeBudgets: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val T = focivoTokens()

    val isStrictOn by StrictModeManager.isStrictModeState.collectAsState()
    val isYtFilterOn by YouTubeStudyGuardManager.isFilterEnabledState.collectAsState()

    var isAiGuardOn by remember { mutableStateOf(AiStudyGuardManager.isAiGuardEnabled(context)) }
    var hasAccessibility by remember { mutableStateOf(AiStudyGuardManager.isAccessibilityPermissionGranted(context)) }
    var explainerKey by remember { mutableStateOf<GuardExplainerKey?>(null) }
    var optInTarget by remember { mutableStateOf<GuardOptInTarget?>(null) }
    var showAdminExplainer by remember { mutableStateOf(false) }

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

    val toggleStrict = {
        if (isStrictOn) {
            StrictModeManager.setStrictModeEnabled(context, false)
        } else {
            StrictModeManager.setStrictModeEnabled(context, true)
            if (!DeviceAdminGuard.isAdminActive(context)) {
                showAdminExplainer = true
            }
        }
    }
    val toggleGuard = {
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
    val toggleYtFilter = {
        if (isYtFilterOn) {
            YouTubeStudyGuardManager.setFilterEnabled(context, false)
        } else if (!hasAccessibility) {
            optInTarget = GuardOptInTarget.YT_FILTER
        } else {
            YouTubeStudyGuardManager.setFilterEnabled(context, true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = T.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier.testTag("block_hub_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetDragHandle()
            Spacer(modifier = Modifier.height(14.dp))

            if (explainerKey == null) {
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
                    Text(text = "BLOCK", style = focivoEyebrow())
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Your study-time guards.",
                    fontFamily = SerifItalicFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                    color = T.text
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Flip a switch to arm a guard. Tap a name to learn what it does.",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    color = T.muted
                )
                Spacer(modifier = Modifier.height(16.dp))

                BlockGuardRow(
                    icon = Icons.Default.Lock,
                    label = "Strict Mode",
                    caption = if (isStrictOn) "On" else "Off",
                    isOn = isStrictOn,
                    onToggle = toggleStrict,
                    onLabelClick = { explainerKey = GuardExplainerKey.STRICT },
                    rowTestTag = "block_hub_strict_row"
                )
                Spacer(modifier = Modifier.height(6.dp))
                BlockGuardRow(
                    icon = Icons.Default.Shield,
                    label = "AI Guard",
                    caption = if (isAiGuardOn) "On" else "Off",
                    isOn = isAiGuardOn,
                    onToggle = toggleGuard,
                    onLabelClick = { explainerKey = GuardExplainerKey.AI_GUARD },
                    rowTestTag = "block_hub_guard_row"
                )
                Spacer(modifier = Modifier.height(6.dp))
                BlockGuardRow(
                    icon = Icons.Default.OndemandVideo,
                    label = "AI YouTube Filter",
                    caption = if (isYtFilterOn) "On" else "Off",
                    isOn = isYtFilterOn,
                    onToggle = toggleYtFilter,
                    onLabelClick = { explainerKey = GuardExplainerKey.YT_FILTER },
                    rowTestTag = "block_hub_ytfilter_row"
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Screen-time limits entry (whole row navigates; neutral style).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(T.surface2)
                        .border(1.dp, T.line, RoundedCornerShape(16.dp))
                        .clickable {
                            onDismiss()
                            onOpenScreenTimeBudgets()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("block_hub_limits_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(T.accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = T.accentText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screen-time limits",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = T.text
                        )
                        Text(
                            text = "Per-app and daily budgets",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = T.muted
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open",
                        tint = T.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            } else {
                // Explainer detail inside the sheet — the "Got it" button
                // returns to the guard list.
                GuardExplainerContent(
                    content = guardExplainerContent(explainerKey!!),
                    onDismiss = { explainerKey = null }
                )
            }
        }
    }

    // Accessibility opt-in for AI Guard / YT Filter (same dialog as the hub).
    optInTarget?.let {
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
 * One Block-sheet row: icon + label (tap = explainer) + immediate switch.
 * Matches the premium sheet language of the rest of the app.
 */
@Composable
private fun BlockGuardRow(
    icon: ImageVector,
    label: String,
    caption: String,
    isOn: Boolean,
    onToggle: () -> Unit,
    onLabelClick: () -> Unit,
    rowTestTag: String,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(T.surface2)
            .border(1.dp, T.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(rowTestTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isOn) T.accent else T.surface)
                .border(1.dp, if (isOn) T.accent else T.chipBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isOn) T.onAccent else T.muted,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onLabelClick)
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = label,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = T.text
            )
            Text(
                text = caption,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = T.muted
            )
        }
        androidx.compose.material3.Switch(
            checked = isOn,
            onCheckedChange = { onToggle() },
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = T.onAccent,
                checkedTrackColor = T.accent,
                uncheckedThumbColor = T.muted,
                uncheckedTrackColor = T.surface
            ),
            modifier = Modifier.testTag("${rowTestTag}_switch")
        )
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
