package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoTokens
import com.example.util.MascotPose

/**
 * v1.2.3 (Task 13.2) — pre-session Strict Mode + AI Guard prompt sheet.
 *
 * Shown when the user manually starts a study session while either Strict
 * Mode or the AI Guard is off. Each option's "Turn on" button REALLY enables
 * the feature (same calls the hub toggles use); "Start session" / dismiss is
 * the skip path — the session starts exactly as before, with no changes.
 *
 * Design language: the unified system (tokens, Poppins body + Instrument
 * Serif italic display accent, lime accent, generous spacing), mascot in the
 * STUDY pose, staggered enter motion — same premium bar as the Session
 * Complete share-card experience. Zero emojis, English-only copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictGuardPromptSheet(
    strictOn: Boolean,
    guardOn: Boolean,
    onEnableStrict: () -> Unit,
    onEnableGuard: () -> Unit,
    onStartSession: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val t = focivoTokens()

    // Staggered enter: mascot pops first, cards slide in one after another.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = t.bg,
        dragHandle = null,
        modifier = modifier.testTag("strict_guard_prompt_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 14.dp, bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Custom drag handle.
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(t.line)
            )
            Spacer(Modifier.height(22.dp))

            // Eyebrow.
            Text(
                text = "BEFORE YOU BEGIN",
                style = focivoEyebrow(),
                modifier = Modifier.testTag("strict_guard_prompt_eyebrow")
            )
            Spacer(Modifier.height(10.dp))

            // Headline: Poppins semi-bold + Instrument Serif italic lime accent.
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, color = t.text)) {
                        append("Lock in your ")
                    }
                    withStyle(
                        SpanStyle(
                            fontFamily = SerifItalicFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            color = t.accentText
                        )
                    ) {
                        append("focus.")
                    }
                },
                fontSize = 30.sp,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("strict_guard_prompt_headline")
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Turn on protection for this session — or skip and start as usual.",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.5.sp,
                color = t.muted,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(18.dp))

            // Mascot — STUDY pose, lime glow ring behind.
            AnimatedVisibility(
                visible = entered,
                enter = scaleIn(initialScale = 0.7f, animationSpec = tween(450)) +
                        fadeIn(animationSpec = tween(450))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(118.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(t.softAccent, Color.Transparent)
                                )
                            )
                    )
                    BuddyMascot(
                        modifier = Modifier.size(96.dp),
                        pose = MascotPose.STUDY
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            // Option cards — staggered slide-in.
            PromptOptionCard(
                visible = entered,
                delayMs = 90,
                icon = Icons.Filled.Lock,
                title = "Strict Mode",
                subtitle = "Locks the session down — no early exits, no pausing, no phone distractions until the timer finishes.",
                isOn = strictOn,
                onEnable = onEnableStrict,
                testTagPrefix = "strict_guard_prompt_strict"
            )
            Spacer(Modifier.height(12.dp))
            PromptOptionCard(
                visible = entered,
                delayMs = 170,
                icon = Icons.Filled.AutoAwesome,
                title = "AI Guard",
                subtitle = "Watches which apps you open during the session and blocks non-study ones in real time.",
                isOn = guardOn,
                onEnable = onEnableGuard,
                testTagPrefix = "strict_guard_prompt_guard"
            )
            Spacer(Modifier.height(22.dp))

            // Primary CTA — skip path: starts the session exactly as before.
            AnimatedVisibility(
                visible = entered,
                enter = fadeIn(animationSpec = tween(400, delayMillis = 240)) +
                        slideInVertically(
                            animationSpec = tween(400, delayMillis = 240),
                            initialOffsetY = { it / 3 }
                        )
            ) {
                Button(
                    onClick = onStartSession,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = t.accent,
                        contentColor = t.onAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("strict_guard_prompt_start")
                ) {
                    Text(
                        text = "Start session",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(
                visible = entered,
                enter = fadeIn(animationSpec = tween(400, delayMillis = 300))
            ) {
                Text(
                    text = "Nothing changes unless you tap Turn on.",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = t.muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * One protection option row: icon tile, title + subtitle, and either an
 * "On" pill (already enabled) or a lime "Turn on" button that genuinely
 * enables the feature.
 */
@Composable
private fun PromptOptionCard(
    visible: Boolean,
    delayMs: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isOn: Boolean,
    onEnable: () -> Unit,
    testTagPrefix: String
) {
    val t = focivoTokens()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400, delayMillis = delayMs)) +
                slideInVertically(
                    animationSpec = tween(400, delayMillis = delayMs),
                    initialOffsetY = { it / 2 }
                )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(t.surface)
                .border(1.dp, t.line, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon tile.
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(t.softAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = t.accentText,
                    modifier = Modifier.size(23.dp)
                )
            }
            // Copy.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = t.text
                )
                Text(
                    text = subtitle,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.5.sp,
                    color = t.muted,
                    lineHeight = 17.sp
                )
            }
            // State pill / enable button.
            if (isOn) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(t.softAccent)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = t.success,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "On",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = t.success
                    )
                }
            } else {
                Button(
                    onClick = onEnable,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = t.accent,
                        contentColor = t.onAccent
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 10.dp
                    ),
                    modifier = Modifier.testTag("${testTagPrefix}_enable")
                ) {
                    Text(
                        text = "Turn on",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}
