package com.example.ui.screens

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.data.LeaderboardUser
import com.example.service.FocivoDeviceAdminReceiver
import com.example.ui.components.FeatureIcon
import com.example.ui.components.MascotPose
import com.example.ui.components.RegainMascotView
import com.example.ui.components.SensitivePermission
import com.example.ui.components.TrustExplainerDialog
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.AiStudyGuardManager
import com.example.util.PermissionUtils
import kotlinx.coroutines.delay

/**
 * v1.5.0 — rebuilt first-launch onboarding (owner spec, exact order):
 *  1. OPENING ANIMATION — mascot float/reveal + "Focivo" wordmark, <=1.2s,
 *     skippable, new users only (flag in focivo_onboarding_prefs).
 *  2. QUESTIONS — one question per screen bound ONLY to the existing intake
 *     fields (name, class level, exam year, goal, distraction, peak hours,
 *     daily target, protection style).
 *  3. MASCOT SHOWCASE — 4 full-screen slides, one benefit line each.
 *  4. GROUP STEP — "Join a focus group and compete on the leaderboard" with
 *     the live leaderboard preview; Continue joins (opens leaderboard after
 *     onboarding), Skip is always visible.
 *  5. PERMISSIONS LAST — one per screen: Notifications -> Usage Access ->
 *     Display over other apps -> Battery optimization; Device Admin only when
 *     the uninstall-protection/strict path was explicitly enabled before.
 *     Every screen has Allow + a visible Later that skips without penalty;
 *     skipped permissions stay grantable from Settings / Guard Hub / Shield
 *     Hub and the in-context prompts.
 */

private const val ONBOARDING_PREFS = "focivo_onboarding_prefs"
private const val KEY_OPENING_SEEN = "onboarding_opening_seen"
private const val KEY_NOTIFICATIONS_DECIDED = "onboarding_notifications_decided"

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

data class FlowOption(val label: String, val iconRes: Int)

data class FlowQuestion(
    val key: String,
    val stepLabel: String,
    val title: String,
    val subtitle: String,
    val headerIconRes: Int,
    val options: List<FlowOption> = emptyList(),
    val multiSelect: Boolean = false,
    val isNameInput: Boolean = false
)

data class ShowcaseSlide(
    val pose: MascotPose,
    val caption: String,
    val line: String
)

data class PermissionSpec(
    val id: String,
    val rowLabel: String,
    val bubbleLead: String,
    val bubbleAccent: String,
    val bubbleTail: String,
    val privacyLine: String,
    val whyBody: String,
    val pose: MascotPose
)

private fun buildQuestions(): List<FlowQuestion> = listOf(
    FlowQuestion(
        key = "name",
        stepLabel = "STEP 1 OF 8 · PROFILE",
        title = "What should I call you?",
        subtitle = "Your name or study alias.",
        headerIconRes = R.drawable.ic3d_person,
        isNameInput = true
    ),
    FlowQuestion(
        key = "class",
        stepLabel = "STEP 2 OF 8 · ACADEMICS",
        title = "What is your academic level?",
        subtitle = "We tune study intervals around this.",
        headerIconRes = R.drawable.ic3d_book,
        options = listOf(
            FlowOption("Class 9 – 10 (Secondary)", R.drawable.ic3d_book),
            FlowOption("Class 11 – 12 (Higher Secondary)", R.drawable.ic3d_book),
            FlowOption("Competitive Exams (JEE / NEET / UPSC / GATE)", R.drawable.ic3d_target),
            FlowOption("College / University Degree", R.drawable.ic3d_book),
            FlowOption("Self-Learner / Professional", R.drawable.ic3d_person)
        )
    ),
    FlowQuestion(
        key = "examYear",
        stepLabel = "STEP 3 OF 8 · MILESTONE",
        title = "Is this a major exam year?",
        subtitle = "We adjust reminders and guard strictness.",
        headerIconRes = R.drawable.ic3d_calendar,
        options = listOf(
            FlowOption("Yes — Exam / Milestone Year", R.drawable.ic3d_flame),
            FlowOption("No — Regular Academic Year", R.drawable.ic3d_calendar)
        )
    ),
    FlowQuestion(
        key = "goal",
        stepLabel = "STEP 4 OF 8 · GOAL",
        title = "What is your main focus goal?",
        subtitle = "This personalizes your daily motivation.",
        headerIconRes = R.drawable.ic3d_target,
        options = listOf(
            FlowOption("Acing Exams & Academic Excellence", R.drawable.ic3d_trophy),
            FlowOption("Building Daily Study Consistency", R.drawable.ic3d_calendar),
            FlowOption("Beating Social Media Distractions", R.drawable.ic3d_shield),
            FlowOption("Maximizing Deep Focus Hours", R.drawable.ic3d_timer)
        )
    ),
    FlowQuestion(
        key = "distraction",
        stepLabel = "STEP 5 OF 8 · DISTRACTIONS",
        title = "Which apps steal your focus?",
        subtitle = "We will shield these during study time.",
        headerIconRes = R.drawable.ic3d_bolt,
        options = listOf(
            FlowOption("Instagram / Reels", R.drawable.ic3d_flame),
            FlowOption("YouTube / Shorts", R.drawable.ic3d_bolt),
            FlowOption("Snapchat / Messaging", R.drawable.ic3d_heart),
            FlowOption("Mobile Games", R.drawable.ic3d_trophy),
            FlowOption("Reddit / Web Browsing", R.drawable.ic3d_home)
        )
    ),
    FlowQuestion(
        key = "peakHours",
        stepLabel = "STEP 6 OF 8 · PEAK HOURS",
        title = "When do you feel most alert?",
        subtitle = "We schedule sessions in your peak window.",
        headerIconRes = R.drawable.ic3d_moon,
        options = listOf(
            FlowOption("Early Morning (5 AM – 9 AM)", R.drawable.ic3d_moon),
            FlowOption("Afternoon (12 PM – 4 PM)", R.drawable.ic3d_bolt),
            FlowOption("Evening (6 PM – 10 PM)", R.drawable.ic3d_moon),
            FlowOption("Late Night (10 PM – 2 AM)", R.drawable.ic3d_moon)
        )
    ),
    FlowQuestion(
        key = "dailyTarget",
        stepLabel = "STEP 7 OF 8 · DAILY TARGET",
        title = "What is your daily focus target?",
        subtitle = "A sustainable goal you can grow into.",
        headerIconRes = R.drawable.ic3d_timer,
        options = listOf(
            FlowOption("1 Hour (60 mins)", R.drawable.ic3d_timer),
            FlowOption("2 Hours (120 mins)", R.drawable.ic3d_timer),
            FlowOption("3 Hours (180 mins)", R.drawable.ic3d_timer),
            FlowOption("4+ Hours (240+ mins)", R.drawable.ic3d_timer)
        )
    ),
    FlowQuestion(
        key = "protection",
        stepLabel = "STEP 8 OF 8 · PROTECTION",
        title = "Choose your protection style",
        subtitle = "Pick the tools that keep you in the zone.",
        headerIconRes = R.drawable.ic3d_shield,
        multiSelect = true,
        options = listOf(
            FlowOption("Strict App Blocker & Study Lock", R.drawable.ic3d_shield),
            FlowOption("AI Academic Sentinel (ChatGPT / Claude)", R.drawable.ic3d_lock),
            FlowOption("Smart Scheduled Study Windows", R.drawable.ic3d_calendar),
            FlowOption("Streak Leaderboard & Daily Tracking", R.drawable.ic3d_trophy)
        )
    )
)

private fun buildShowcaseSlides(): List<ShowcaseSlide> = listOf(
    ShowcaseSlide(
        pose = MascotPose.STOP_SIGN,
        caption = "SMART BLOCKING",
        line = "Distracting apps stay closed while you study."
    ),
    ShowcaseSlide(
        pose = MascotPose.GUARD,
        caption = "AI STUDY GUARD",
        line = "The AI guard keeps every session honest."
    ),
    ShowcaseSlide(
        pose = MascotPose.WELCOME,
        caption = "THEMES",
        line = "Make it yours — 23 themes to match your vibe."
    ),
    ShowcaseSlide(
        pose = MascotPose.CELEBRATING,
        caption = "STREAKS",
        line = "Streaks turn small wins into momentum."
    )
)

private fun buildPermissionSpecs(): List<PermissionSpec> = listOf(
    PermissionSpec(
        id = "notifications",
        rowLabel = "Notification permission",
        bubbleLead = "To ",
        bubbleAccent = "send you focus reminders and streak alerts",
        bubbleTail = ", we need notification access.",
        privacyLine = "Your privacy matters to us. We never share your data.",
        whyBody = "Reminders for your study windows, streak milestones and timer alerts. Nothing else — no spam, ever.",
        pose = MascotPose.WELCOME
    ),
    PermissionSpec(
        id = "usage",
        rowLabel = "Usage permission",
        bubbleLead = "To ",
        bubbleAccent = "find your distracting apps",
        bubbleTail = ", we need usage access.",
        privacyLine = "Your privacy matters to us. Usage stats never leave your phone.",
        whyBody = "This shows which apps eat your study time, so the guard blocks the right ones.",
        pose = MascotPose.STUDY_POSE
    ),
    PermissionSpec(
        id = "overlay",
        rowLabel = "Display permission",
        bubbleLead = "When a blocked app opens, we ",
        bubbleAccent = "pop up a gentle reminder",
        bubbleTail = " to keep you on track.",
        privacyLine = "Your privacy matters to us. We never share your data.",
        whyBody = "Without this, a blocked app would open silently with no reminder to stay focused.",
        pose = MascotPose.GUARD_WARNING
    ),
    PermissionSpec(
        id = "battery",
        rowLabel = "Battery permission",
        bubbleLead = "So the guard keeps working ",
        bubbleAccent = "even in the background",
        bubbleTail = ", we need to stay exempt from battery saving.",
        privacyLine = "Your privacy matters to us. We never share your data.",
        whyBody = "Some phones aggressively kill background apps. This keeps your focus sessions protected.",
        pose = MascotPose.GUARD
    ),
    PermissionSpec(
        id = "exact_alarm",
        rowLabel = "Alarm permission",
        bubbleLead = "So your focus sessions and reminders ",
        bubbleAccent = "start on time",
        bubbleTail = ", we need exact alarm access.",
        privacyLine = "Your privacy matters to us. We never share your data.",
        whyBody = "Session end alarms, scheduled blocks and study reminders fire exactly when promised. Without it, the system may delay them.",
        pose = MascotPose.GUARD
    ),
    PermissionSpec(
        id = "accessibility",
        rowLabel = "AI Study Guard",
        bubbleLead = "To ",
        bubbleAccent = "detect non-study apps during focus",
        bubbleTail = ", the AI Study Guard needs accessibility access.",
        privacyLine = "Detection happens on your phone. We never collect or share your data.",
        whyBody = "This is what powers the guard: when a blocked app opens during a focus session, Focivo notices and brings up the block screen.",
        pose = MascotPose.GUARD_WARNING
    ),
    PermissionSpec(
        id = "device_admin",
        rowLabel = "Device admin",
        bubbleLead = "To ",
        bubbleAccent = "prevent uninstalls during strict sessions",
        bubbleTail = ", we need device admin access.",
        privacyLine = "Only active while a strict session runs. Removable anytime from settings.",
        whyBody = "This stops accidental (or tempted) uninstalls mid-session. You stay in control.",
        pose = MascotPose.STOP_SIGN
    )
)

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

private enum class StepKind { INTRO, QUESTION, SHOWCASE, GROUP, PERMISSION }
private data class FlowStep(val kind: StepKind, val index: Int)

@Composable
private fun FlowProgressBar(progress: Float, isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(if (isDark) Color(0x22FFFFFF) else Color(0x18000000))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(CircleShape)
                .background(RegainLimePrimary)
        )
    }
}

/** Single clean white pill — the one primary action per screen. */
@Composable
fun OnboardingWhitePill(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String = ""
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color(0x55FFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color(0xFF0D1B05) else Color(0x990D1B05),
                fontSize = 16.sp
            )
        )
    }
}

@Composable
private fun QuietTextButton(text: String, onClick: () -> Unit, testTag: String = "") {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8E9B8A),
                fontSize = 14.sp
            )
        )
    }
}

// ---------------------------------------------------------------------------
// 1. Opening animation (new users only, <=1.2s, skippable)
// ---------------------------------------------------------------------------

@Composable
fun OpeningIntroVisual(progress: Float) {
    val isDark = isAppInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - progress) * 90f
                    alpha = progress
                }
            ) {
                RegainMascotView(
                    pose = MascotPose.WELCOME,
                    size = 150.dp,
                    animate = false
                )
            }
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "Focivo",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    letterSpacing = 8.sp,
                    color = if (isDark) Color(0xFFF0F4ED) else NearBlack
                ),
                modifier = Modifier.alpha(progress)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Focus, protected.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontSize = 14.sp,
                    color = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
                ),
                modifier = Modifier.alpha(progress)
            )
        }
    }
}

@Composable
private fun OpeningIntroStep(onDone: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf(0f) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1100)
        ) { value, _ -> progress = value }
        if (!finished) {
            finished = true
            context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OPENING_SEEN, true).apply()
            onDone()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OpeningIntroVisual(progress = progress)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
        ) {
            QuietTextButton(
                text = "Skip",
                testTag = "opening_skip",
                onClick = {
                    if (!finished) {
                        finished = true
                        context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
                            .edit().putBoolean(KEY_OPENING_SEEN, true).apply()
                        onDone()
                    }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2. Questions — one per screen
// ---------------------------------------------------------------------------

@Composable
fun FlowQuestionContent(
    question: FlowQuestion,
    stepLabel: String,
    progress: Float,
    nameText: String,
    onNameChange: (String) -> Unit,
    selectedValues: Set<String>,
    onToggleValue: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)?
) {
    val isDark = isAppInDarkTheme()
    val tPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val tSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val cardBg = if (isDark) Color(0xFF171C16) else Color.White
    val cardBorder = if (isDark) Color(0x22FFFFFF) else Color(0xFFE3EADF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7))
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        FlowProgressBar(progress = progress, isDark = isDark)
        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stepLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) RegainLimePrimary else Color(0xFF4C8A00),
                        letterSpacing = 1.6.sp,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = question.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = tPrimary,
                        fontSize = 24.sp,
                        lineHeight = 30.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = question.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        color = tSecondary,
                        fontSize = 14.sp
                    )
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // Small mascot in the corner
            RegainMascotView(
                pose = MascotPose.WELCOME,
                size = 64.dp,
                animate = false,
                modifier = Modifier.testTag("question_corner_mascot")
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (question.isNameInput) {
            OutlinedTextField(
                value = nameText,
                onValueChange = onNameChange,
                placeholder = {
                    Text(
                        "e.g. Alex",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = PoppinsFontFamily,
                            color = tSecondary.copy(alpha = 0.6f)
                        )
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onContinue() }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = tPrimary,
                    fontSize = 17.sp
                ),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RegainLimePrimary,
                    unfocusedBorderColor = cardBorder,
                    cursorColor = RegainLimePrimary,
                    focusedContainerColor = cardBg,
                    unfocusedContainerColor = cardBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("question_name_input")
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                question.options.forEach { option ->
                    val isSelected = selectedValues.contains(option.label)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) RegainLimePrimary.copy(alpha = if (isDark) 0.14f else 0.12f)
                                else cardBg
                            )
                            .border(
                                1.5.dp,
                                if (isSelected) RegainLimePrimary else cardBorder,
                                RoundedCornerShape(18.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onToggleValue(option.label) }
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .testTag("question_option_${option.label.take(12)}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FeatureIcon(
                                drawableRes = option.iconRes,
                                contentDescription = null,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected && isDark) RegainLimePrimary
                                    else if (isSelected) Color(0xFF3E6E00)
                                    else tPrimary,
                                    fontSize = 15.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            // Radio / checkbox indicator
                            if (question.multiSelect) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .border(
                                            2.dp,
                                            if (isSelected) RegainLimePrimary else tSecondary.copy(alpha = 0.4f),
                                            RoundedCornerShape(7.dp)
                                        )
                                        .background(if (isSelected) RegainLimePrimary else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (isDark) Color(0xFF121511) else Color.White)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .border(
                                            2.dp,
                                            if (isSelected) RegainLimePrimary else tSecondary.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .background(if (isSelected) RegainLimePrimary else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isDark) Color(0xFF121511) else Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        OnboardingWhitePill(
            text = "Continue",
            onClick = onContinue,
            testTag = "question_continue"
        )
        if (onBack != null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                QuietTextButton(text = "Back", onClick = onBack, testTag = "question_back")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Mascot showcase — full-screen slides, one benefit line each
// ---------------------------------------------------------------------------

@Composable
fun ShowcaseSlideContent(
    slide: ShowcaseSlide,
    slideIndex: Int,
    totalSlides: Int,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val tPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val tSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7))
            .padding(horizontal = 24.dp)
            .padding(top = 72.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalSlides) { i ->
                Box(
                    modifier = Modifier
                        .width(if (i == slideIndex) 28.dp else 8.dp)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == slideIndex) RegainLimePrimary
                            else (if (isDark) Color(0x22FFFFFF) else Color(0x18000000))
                        )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        RegainMascotView(
            pose = slide.pose,
            size = 190.dp,
            animate = true,
            modifier = Modifier.testTag("showcase_mascot")
        )

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = slide.caption,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (isDark) RegainLimePrimary else Color(0xFF4C8A00),
                letterSpacing = 2.sp,
                fontSize = 11.sp
            )
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = slide.line,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                color = tPrimary,
                fontSize = 23.sp,
                lineHeight = 30.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        OnboardingWhitePill(
            text = if (slideIndex == totalSlides - 1) "Let's Go" else "Continue",
            onClick = onContinue,
            testTag = "showcase_continue"
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            QuietTextButton(text = "Skip tour", onClick = onSkip, testTag = "showcase_skip")
        }
    }
}

// ---------------------------------------------------------------------------
// 4. Group step — join a focus group, compete on the leaderboard
// ---------------------------------------------------------------------------

private fun formatGroupHours(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    return if (h >= 1) "${h}h" else "${(totalSeconds / 60).coerceAtLeast(1)}m"
}

@Composable
fun GroupStepContent(
    users: List<LeaderboardUser>,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val tPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val tSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val preview = remember(users) {
        users.sortedByDescending { it.weeklyStudySeconds }.take(3)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7))
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Leaderboard preview card (live data)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF141716))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .testTag("group_leaderboard_preview")
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Leaderboard",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF7F8F4),
                            fontSize = 16.sp
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(RegainLimePrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFF9AA79B),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                if (preview.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        RegainMascotView(
                            pose = MascotPose.EMPTY_STATE,
                            size = 84.dp,
                            animate = false
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your leaderboard will appear here as you focus.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFF9AA79B),
                                fontSize = 13.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    preview.forEachIndexed { rank, user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${rank + 1}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9AA79B),
                                    fontSize = 14.sp
                                ),
                                modifier = Modifier.width(24.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (user.isCurrentUser) RegainLimePrimary
                                        else Color(0xFF2A332A)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.displayName.trim().take(1).uppercase().ifBlank { "S" },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = if (user.isCurrentUser) Color(0xFF0D1B05) else Color.White,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (user.isCurrentUser) "You" else user.displayName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFF7F8F4),
                                    fontSize = 15.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatGroupHours(user.weeklyStudySeconds),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF7F8F4),
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = buildAnnotatedString {
                append("Join a ")
                withStyle(SpanStyle(color = RegainLimePrimary, fontWeight = FontWeight.Bold)) {
                    append("focus group")
                }
                append(" and compete on the leaderboard")
            },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                color = tPrimary,
                fontSize = 26.sp,
                lineHeight = 33.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Studying is easier together. Climb the ranks with friends.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = PoppinsFontFamily,
                color = tSecondary,
                fontSize = 14.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        OnboardingWhitePill(
            text = "Continue",
            onClick = onContinue,
            testTag = "group_continue"
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            QuietTextButton(text = "Skip", onClick = onSkip, testTag = "group_skip")
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Permissions — one per screen, last
// ---------------------------------------------------------------------------

private fun isPermissionGranted(context: Context, id: String): Boolean = when (id) {
    "notifications" -> PermissionUtils.hasNotificationPermission(context)
    "usage" -> PermissionUtils.hasUsageStatsPermission(context)
    "overlay" -> PermissionUtils.hasOverlayPermission(context)
    "battery" -> PermissionUtils.isBatteryOptimizationDisabled(context)
    "exact_alarm" -> PermissionUtils.hasExactAlarmPermission(context)
    "accessibility" -> AiStudyGuardManager.isAccessibilityPermissionGranted(context)
    "device_admin" -> {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        dpm?.isAdminActive(FocivoDeviceAdminReceiver.getComponentName(context)) == true
    }
    else -> false
}

@Composable
fun PermissionGrantContent(
    spec: PermissionSpec,
    progress: Float,
    onAllow: () -> Unit,
    onLater: () -> Unit,
    onBack: (() -> Unit)?
) {
    val isDark = isAppInDarkTheme()
    val tPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val tSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val bubbleBg = if (isDark) Color(0xFF1B211A) else Color.White
    val bubbleBorder = if (isDark) Color(0x22FFFFFF) else Color(0xFFE3EADF)
    val rowBg = if (isDark) Color(0xFF171B16) else Color(0xFF10140F)
    var whyExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7))
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        FlowProgressBar(progress = progress, isDark = isDark)
        Spacer(modifier = Modifier.height(40.dp))

        // Mascot + speech bubble (reference pattern)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1E251D) else Color(0xFFF0F4EC))
                    .border(1.dp, RegainLimePrimary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                RegainMascotView(
                    pose = spec.pose,
                    size = 54.dp,
                    animate = false
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(bubbleBg)
                    .border(1.dp, bubbleBorder, RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = tPrimary)) { append(spec.bubbleLead) }
                        withStyle(SpanStyle(color = RegainLimePrimary, fontWeight = FontWeight.Bold)) {
                            append(spec.bubbleAccent)
                        }
                        withStyle(SpanStyle(color = tPrimary)) { append(spec.bubbleTail) }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 23.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Dark card row with white Allow pill (reference pattern)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(rowBg)
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("permission_row_${spec.id}")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = spec.rowLabel,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF7F8F4),
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAllow
                        )
                        .padding(horizontal = 26.dp, vertical = 12.dp)
                        .testTag("permission_allow_${spec.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Allow",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D1B05),
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = spec.privacyLine,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = PoppinsFontFamily,
                color = tSecondary,
                fontSize = 13.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // "Why these permissions?" expander (reference pattern)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(bubbleBg)
                .border(1.dp, bubbleBorder, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { whyExpanded = !whyExpanded }
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Why these permissions?",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = tSecondary,
                        fontSize = 14.sp
                    )
                )
                Icon(
                    imageVector = if (whyExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = tSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = whyExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = spec.whyBody,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    color = tSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))

        // Quiet navigation: Back + Later (skip without penalty)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                QuietTextButton(text = "Back", onClick = onBack, testTag = "permission_back_${spec.id}")
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            QuietTextButton(text = "Later", onClick = onLater, testTag = "permission_later_${spec.id}")
        }
    }
}

// ---------------------------------------------------------------------------
// Orchestrator
// ---------------------------------------------------------------------------

@Composable
fun OnboardingFlowScreen(
    initialUserName: String = "",
    leaderboardUsers: List<LeaderboardUser> = emptyList(),
    onFinishSurvey: (
        name: String,
        studentClassLevel: String,
        isBoardExamYear: Boolean,
        primaryStudyGoal: String,
        biggestDistractionApp: String,
        preferredStudyTimeWindow: String,
        dailyScreenTimeGoalMinutes: Int,
        motivationStyle: String
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onJoinGroup: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val questions = remember { buildQuestions() }
    val slides = remember { buildShowcaseSlides() }
    val permSpecs = remember {
        // v1.1 final: every permission the app requests appears here —
        // no conditional gating. Later/NotNow keeps the user inside onboarding.
        buildPermissionSpecs()
    }

    val showIntro = remember {
        !context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_OPENING_SEEN, false)
    }

    val steps = remember(showIntro, permSpecs) {
        buildList {
            if (showIntro) add(FlowStep(StepKind.INTRO, 0))
            questions.forEachIndexed { i, _ -> add(FlowStep(StepKind.QUESTION, i)) }
            slides.forEachIndexed { i, _ -> add(FlowStep(StepKind.SHOWCASE, i)) }
            add(FlowStep(StepKind.GROUP, 0))
            permSpecs.forEachIndexed { i, _ -> add(FlowStep(StepKind.PERMISSION, i)) }
        }
    }

    var stepPos by remember { mutableIntStateOf(0) }
    var permTick by remember { mutableIntStateOf(0) }
    // v1.1 final: trust explainer card for sensitive permissions (Accessibility,
    // Device Admin) — shown before the system prompt.
    var explainerFor by remember { mutableStateOf<PermissionSpec?>(null) }

    // Answers — bound only to the existing intake fields.
    var nameText by remember { mutableStateOf(initialUserName) }
    var classLevel by remember { mutableStateOf("Class 11 – 12 (Higher Secondary)") }
    var examYearYes by remember { mutableStateOf(true) }
    var goal by remember { mutableStateOf("Acing Exams & Academic Excellence") }
    var distraction by remember { mutableStateOf("Instagram / Reels") }
    var peakHours by remember { mutableStateOf("Evening (6 PM – 10 PM)") }
    var dailyTargetMin by remember { mutableIntStateOf(240) }
    var protectionSet by remember { mutableStateOf(setOf("Strict App Blocker & Study Lock")) }
    var joinedGroup by remember { mutableStateOf(false) }

    fun currentStep(): FlowStep? = steps.getOrNull(stepPos)

    fun finish() {
        onFinishSurvey(
            nameText.ifBlank { "Focus Champion" },
            classLevel,
            examYearYes,
            goal,
            distraction,
            peakHours,
            dailyTargetMin,
            if (protectionSet.isEmpty()) "Strict App Blocker & Study Lock"
            else protectionSet.joinToString(", ")
        )
        if (joinedGroup) onJoinGroup()
    }

    fun advance() {
        if (stepPos < steps.lastIndex) stepPos++ else finish()
    }

    fun markNotificationsDecided() {
        context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIFICATIONS_DECIDED, true).apply()
    }

    // Notifications runtime-permission launcher (Android 13+).
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        markNotificationsDecided()
        permTick++
    }

    // v1.2.1: Allow/Continue ALWAYS fires the step's request (defensive —
    // a failed intent surfaces a toast instead of silently doing nothing).
    // Shared implementation with the Start-Study permission gate.
    fun firePermissionIntent(spec: PermissionSpec) {
        if (spec.id == "notifications") {
            markNotificationsDecided()
            val ok = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    true
                } else {
                    PermissionUtils.fireFocusPermissionIntent(context, spec.id)
                }
            } catch (_: Exception) { false }
            if (!ok) {
                Toast.makeText(
                    context,
                    "Couldn't open the permission prompt — please allow notifications in Settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
            permTick++
            return
        }
        val ok = PermissionUtils.fireFocusPermissionIntent(context, spec.id)
        if (!ok) {
            Toast.makeText(
                context,
                "Couldn't open Settings — please grant \"${spec.rowLabel}\" manually.",
                Toast.LENGTH_LONG
            ).show()
        }
        permTick++
    }

    fun requestPermission(spec: PermissionSpec) {
        // v1.1 final: sensitive permissions (Accessibility, Device Admin) go
        // through the trust explainer card first (already-built component).
        if (spec.id == "accessibility" || spec.id == "device_admin") {
            explainerFor = spec
            return
        }
        firePermissionIntent(spec)
    }


    // Refresh permission state on resume; auto-advance a permission step the
    // moment its permission becomes granted (including pre-granted ones).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(stepPos, permTick) {
        val step = currentStep()
        if (step != null && step.kind == StepKind.PERMISSION) {
            val spec = permSpecs.getOrNull(step.index)
            if (spec != null && isPermissionGranted(context, spec.id)) {
                delay(350)
                advance()
            }
        }
    }

    // Progress across everything after the intro animation.
    val nonIntroSteps = remember(steps) { steps.filter { it.kind != StepKind.INTRO } }
    fun progressFor(pos: Int): Float {
        val step = steps.getOrNull(pos) ?: return 0f
        if (step.kind == StepKind.INTRO) return 0f
        val idx = nonIntroSteps.indexOf(step)
        return if (nonIntroSteps.isEmpty()) 1f else (idx + 1).toFloat() / nonIntroSteps.size.toFloat()
    }

    val step = currentStep() ?: return

    when (step.kind) {
        StepKind.INTRO -> {
            OpeningIntroStep(onDone = { advance() })
        }
        StepKind.QUESTION -> {
            val q = questions[step.index]
            val selected: Set<String> = when (q.key) {
                "class" -> setOf(classLevel)
                "examYear" -> setOf(if (examYearYes) "Yes — Exam / Milestone Year" else "No — Regular Academic Year")
                "goal" -> setOf(goal)
                "distraction" -> setOf(distraction)
                "peakHours" -> setOf(peakHours)
                "dailyTarget" -> setOf(
                    when (dailyTargetMin) {
                        60 -> "1 Hour (60 mins)"
                        120 -> "2 Hours (120 mins)"
                        180 -> "3 Hours (180 mins)"
                        else -> "4+ Hours (240+ mins)"
                    }
                )
                "protection" -> protectionSet
                else -> emptySet()
            }
            FlowQuestionContent(
                question = q,
                stepLabel = q.stepLabel,
                progress = progressFor(stepPos),
                nameText = nameText,
                onNameChange = { nameText = it },
                selectedValues = selected,
                onToggleValue = { label ->
                    when (q.key) {
                        "class" -> classLevel = label
                        "examYear" -> examYearYes = label.startsWith("Yes")
                        "goal" -> goal = label
                        "distraction" -> distraction = label
                        "peakHours" -> peakHours = label
                        "dailyTarget" -> dailyTargetMin = when {
                            label.contains("1 Hour") -> 60
                            label.contains("2 Hours") -> 120
                            label.contains("3 Hours") -> 180
                            else -> 240
                        }
                        "protection" -> protectionSet =
                            if (protectionSet.contains(label)) protectionSet - label
                            else protectionSet + label
                    }
                },
                onContinue = { advance() },
                onBack = if (stepPos > 0) ({ stepPos-- }) else null
            )
        }
        StepKind.SHOWCASE -> {
            ShowcaseSlideContent(
                slide = slides[step.index],
                slideIndex = step.index,
                totalSlides = slides.size,
                onContinue = { advance() },
                onSkip = {
                    // Jump past the remaining showcase slides to the group step.
                    val next = steps.indexOfFirst { it.kind == StepKind.GROUP }
                    if (next >= 0) stepPos = next else advance()
                }
            )
        }
        StepKind.GROUP -> {
            GroupStepContent(
                users = leaderboardUsers,
                onContinue = {
                    joinedGroup = true
                    advance()
                },
                onSkip = { advance() }
            )
        }
        StepKind.PERMISSION -> {
            val spec = permSpecs.getOrNull(step.index) ?: return
            PermissionGrantContent(
                spec = spec,
                progress = progressFor(stepPos),
                onAllow = { requestPermission(spec) },
                onLater = {
                    if (spec.id == "notifications") markNotificationsDecided()
                    advance()
                },
                onBack = if (stepPos > 0) ({ stepPos-- }) else null
            )
        }
    }

    // v1.1 final: trust explainer card before the system prompt for sensitive
    // permissions. NotNow keeps the user inside onboarding (same as Later).
    explainerFor?.let { spec ->
        TrustExplainerDialog(
            permission = if (spec.id == "device_admin") SensitivePermission.DEVICE_ADMIN else SensitivePermission.ACCESSIBILITY,
            onContinue = {
                val pending = spec
                explainerFor = null
                firePermissionIntent(pending)
            },
            onNotNow = {
                explainerFor = null
                if (spec.id == "notifications") markNotificationsDecided()
                advance()
            }
        )
    }
}
