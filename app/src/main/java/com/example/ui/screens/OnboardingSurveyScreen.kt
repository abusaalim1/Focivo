package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.components.GlassCard
import com.example.ui.components.MascotPose
import com.example.ui.components.RegainMascotView
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.AiStudyGuardManager
import kotlinx.coroutines.delay

data class OnboardingSurveyQuestion(
    val id: Int,
    val stepLabel: String,
    val questionTitle: String,
    val subtitle: String,
    val icon: ImageVector,
    val options: List<String>? = null,
    val isMultiSelect: Boolean = false
)

enum class OnboardingStage {
    INTRO,
    ACCESSIBILITY_PERMISSION,
    QUESTIONS
}

/**
 * OnboardingSurveyScreen:
 * 1. Introduction: Focivo mascot in upper-middle (~200dp), Apple-style animated typewriter text, Next button.
 * 2. Accessibility Permission: Focivo asks directly with Mascot, provides clear warning/explanation, "Later" vs "Turn On Now" options.
 * 3. Direct Questions Flow: Vivid Lime Green progress bar, Apple-style 3D minimal icons (no raw Android emojis).
 */
@Composable
fun OnboardingSurveyScreen(
    initialUserName: String = "",
    onFinishSurvey: (
        name: String,
        studentClassLevel: String,
        isBoardExamYear: Boolean,
        primaryStudyGoal: String,
        biggestDistractionApp: String,
        preferredStudyTimeWindow: String,
        dailyScreenTimeGoalMinutes: Int,
        motivationStyle: String
    ) -> Unit = { _, _, _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val darkTheme = isAppInDarkTheme()

    var stage by remember { mutableStateOf(OnboardingStage.INTRO) }
    var currentStepIndex by remember { mutableIntStateOf(0) }

    // Intro typewriter state
    var introDisplayText by remember { mutableStateOf("") }
    var isIntroTypingFinished by remember { mutableStateOf(false) }

    // Permission state
    var isAccessibilityGranted by remember {
        mutableStateOf(AiStudyGuardManager.isAccessibilityPermissionGranted(context))
    }

    // Refresh permission state on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityGranted = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Form field states
    var userName by remember { mutableStateOf(initialUserName) }
    var selectedClassLevel by remember { mutableStateOf("Class 11 – 12 (Higher Secondary)") }
    var isBoardExamYear by remember { mutableStateOf(true) }
    var selectedGoal by remember { mutableStateOf("Acing Exams & Top Academic Grades") }
    var selectedDistractionApps by remember { mutableStateOf(setOf("Instagram / Reels", "YouTube / Shorts")) }
    var selectedTimeWindow by remember { mutableStateOf("Evening (6 PM – 10 PM)") }
    var selectedDailyFocusMinutes by remember { mutableIntStateOf(240) }
    var selectedMotivationStyles by remember { mutableStateOf(setOf("Strict AI Guard & App Blocker")) }

    // Typewriter effect for Intro
    val introTargetText = "Hey, I'm Focivo! Your dedicated AI study companion & focus guardian. I help you eliminate doomscrolling, build deep study streaks, and protect your mind during critical study hours."
    LaunchedEffect(stage) {
        if (stage == OnboardingStage.INTRO && !isIntroTypingFinished) {
            introDisplayText = ""
            for (i in introTargetText.indices) {
                introDisplayText = introTargetText.substring(0, i + 1)
                delay(18L)
            }
            isIntroTypingFinished = true
        }
    }

    // Floating animation for mascot
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    val questions = listOf(
        OnboardingSurveyQuestion(
            id = 0,
            stepLabel = "STEP 1 OF 8 • PROFILE",
            questionTitle = "What should I call you?",
            subtitle = "Enter your preferred name or study alias so I can personalize your daily study coach.",
            icon = Icons.Default.Person
        ),
        OnboardingSurveyQuestion(
            id = 1,
            stepLabel = "STEP 2 OF 8 • ACADEMICS",
            questionTitle = "What is your current academic level?",
            subtitle = "This helps me optimize study intervals and AI guard strictness for your level.",
            icon = Icons.Default.School,
            options = listOf(
                "Class 9 – 10 (Secondary School)",
                "Class 11 – 12 (Higher Secondary)",
                "Competitive Exams (JEE / NEET / UPSC / GATE)",
                "College / University Degree",
                "Self-Learner / Working Professional"
            )
        ),
        OnboardingSurveyQuestion(
            id = 2,
            stepLabel = "STEP 3 OF 8 • MILESTONE",
            questionTitle = "Is this a major exam or milestone year for you?",
            subtitle = "Milestone years activate priority focus reminders and smart scheduling.",
            icon = Icons.Default.Star,
            options = listOf(
                "Yes — Board / Entrance / Final Exam Year",
                "No — Regular Academic / Skill Building Year"
            )
        ),
        OnboardingSurveyQuestion(
            id = 3,
            stepLabel = "STEP 4 OF 8 • PRODUCTIVITY GOAL",
            questionTitle = "What is your primary productivity goal?",
            subtitle = "Choose what you want me to help you accomplish most.",
            icon = Icons.Default.GraphicEq,
            options = listOf(
                "Acing Exams & Top Academic Grades",
                "Building Daily Study Consistency",
                "Beating Smartphone & Social Media Addiction",
                "Maximizing Deep Focus Hours"
            )
        ),
        OnboardingSurveyQuestion(
            id = 4,
            stepLabel = "STEP 5 OF 8 • DISTRACTIONS",
            questionTitle = "Which apps drain your focus the most?",
            subtitle = "Select all apps that distract you. AI Study Guard will automatically shield you from them.",
            icon = Icons.Default.Phone,
            options = listOf(
                "Instagram / Reels",
                "YouTube / Shorts",
                "Snapchat / Messaging",
                "BGMI / Mobile Gaming",
                "Reddit / X / Web Browser"
            ),
            isMultiSelect = true
        ),
        OnboardingSurveyQuestion(
            id = 5,
            stepLabel = "STEP 6 OF 8 • STUDY HABITS",
            questionTitle = "When do you feel most focused and alert?",
            subtitle = "I'll suggest optimal deep work sessions during your peak energy hours.",
            icon = Icons.Default.Coffee,
            options = listOf(
                "Early Morning (5 AM – 9 AM)",
                "Afternoon (12 PM – 4 PM)",
                "Evening (6 PM – 10 PM)",
                "Late Night (10 PM – 2 AM)"
            )
        ),
        OnboardingSurveyQuestion(
            id = 6,
            stepLabel = "STEP 7 OF 8 • DAILY TARGET",
            questionTitle = "What is your daily deep focus target?",
            subtitle = "Set a realistic daily goal to track your consistency on the leaderboard.",
            icon = Icons.Default.Timer,
            options = listOf(
                "1 Hour (60 mins) — Light Focus",
                "2 Hours (120 mins) — Balanced Consistency",
                "3 Hours (180 mins) — Deep Academic Focus",
                "4+ Hours (240+ mins) — High Intensity Study"
            )
        ),
        OnboardingSurveyQuestion(
            id = 7,
            stepLabel = "STEP 8 OF 8 • DISCIPLINE STYLE",
            questionTitle = "How should I keep you accountable?",
            subtitle = "Select one or more motivation drivers to unlock your peak potential.",
            icon = Icons.Default.Shield,
            options = listOf(
                "Strict AI Guard & App Blocker",
                "Pomodoro Sprints & Interval Rest",
                "Soft Encouragement & Mascot Buddy",
                "Study Streaks & Peer Consistency"
            ),
            isMultiSelect = true
        )
    )

    val currentQuestion = questions[currentStepIndex]
    val totalSteps = questions.size

    val bgGradient = if (darkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F1A13),
                Color(0xFF080D09),
                Color(0xFF030504)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF7FCF5),
                Color(0xFFEFF9EB),
                Color(0xFFE4F5DC)
            )
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_survey_screen"),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
        ) {
            // Ambient soft lime glow at top center
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                RegainLimePrimary.copy(alpha = if (darkTheme) 0.18f else 0.25f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (stage != OnboardingStage.INTRO) {
                                IconButton(
                                    onClick = {
                                        when (stage) {
                                            OnboardingStage.ACCESSIBILITY_PERMISSION -> stage = OnboardingStage.INTRO
                                            OnboardingStage.QUESTIONS -> {
                                                if (currentStepIndex > 0) {
                                                    currentStepIndex--
                                                } else {
                                                    stage = OnboardingStage.ACCESSIBILITY_PERMISSION
                                                }
                                            }
                                            else -> {}
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(RegainLimePrimary.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = if (darkTheme) RegainLimePrimary else Color(0xFF15803D),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(38.dp))
                            }

                            // Clean top header
                            Text(
                                text = when (stage) {
                                    OnboardingStage.INTRO -> "FOCIVO ONBOARDING"
                                    OnboardingStage.ACCESSIBILITY_PERMISSION -> "STUDY SHIELD SETUP"
                                    OnboardingStage.QUESTIONS -> questionTitleHeader(currentQuestion.stepLabel)
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.3.sp,
                                    color = if (darkTheme) RegainLimePrimary else Color(0xFF15803D)
                                )
                            )

                            Spacer(modifier = Modifier.size(38.dp))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Vivid Lime Green Progress Bar (Pure Lime Palette, NO deep green)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (darkTheme) Color(0xFF1B2820) else Color(0xFFE2E8F0))
                        ) {
                            val activeProgress = when (stage) {
                                OnboardingStage.INTRO -> 0.12f
                                OnboardingStage.ACCESSIBILITY_PERMISSION -> 0.25f
                                OnboardingStage.QUESTIONS -> 0.25f + (0.75f * ((currentStepIndex + 1).toFloat() / totalSteps.toFloat()))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(activeProgress)
                                    .height(5.dp)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = RoundedCornerShape(3.dp),
                                        spotColor = RegainLimePrimary,
                                        ambientColor = RegainLimePrimary.copy(alpha = 0.6f)
                                    )
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                RegainLimePrimary,
                                                Color(0xFFAEF72A),
                                                RegainLimeLight,
                                                RegainLimePrimary
                                            )
                                        )
                                    )
                            )
                        }
                    }
                },
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .imePadding()
                    ) {
                        when (stage) {
                            OnboardingStage.INTRO -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .shadow(
                                            elevation = 4.dp,
                                            shape = CircleShape,
                                            spotColor = RegainLimePrimary.copy(alpha = 0.4f),
                                            ambientColor = RegainLimePrimary.copy(alpha = 0.2f)
                                        )
                                        .clip(CircleShape)
                                        .background(RegainLimePrimary)
                                        .clickable {
                                            stage = OnboardingStage.ACCESSIBILITY_PERMISSION
                                        }
                                        .testTag("survey_intro_next_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Next →",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = NearBlack
                                        )
                                    )
                                }
                            }

                            OnboardingStage.ACCESSIBILITY_PERMISSION -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Primary: Turn On Shield Now (or Continue if already granted)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .shadow(
                                                elevation = 4.dp,
                                                shape = CircleShape,
                                                spotColor = RegainLimePrimary.copy(alpha = 0.4f),
                                                ambientColor = RegainLimePrimary.copy(alpha = 0.2f)
                                            )
                                            .clip(CircleShape)
                                            .background(RegainLimePrimary)
                                            .clickable {
                                                if (isAccessibilityGranted) {
                                                    stage = OnboardingStage.QUESTIONS
                                                } else {
                                                    AiStudyGuardManager.openAccessibilitySettings(context)
                                                }
                                            }
                                            .testTag("onboarding_grant_accessibility_btn"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isAccessibilityGranted) "Shield Active • Continue →" else "Turn On Shield Now",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = NearBlack
                                            )
                                        )
                                    }

                                    // Secondary: I'll do it later (as requested: later or abhi on kar du)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .clip(CircleShape)
                                            .background(if (darkTheme) Color(0x18FFFFFF) else Color(0x0E000000))
                                            .clickable {
                                                stage = OnboardingStage.QUESTIONS
                                            }
                                            .testTag("onboarding_skip_accessibility_btn"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "I'll do it later",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }

                            OnboardingStage.QUESTIONS -> {
                                val buttonEnabled = when (currentStepIndex) {
                                    0 -> userName.isNotBlank()
                                    4 -> selectedDistractionApps.isNotEmpty()
                                    7 -> selectedMotivationStyles.isNotEmpty()
                                    else -> true
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .shadow(
                                            elevation = if (buttonEnabled) 4.dp else 0.dp,
                                            shape = CircleShape,
                                            spotColor = RegainLimePrimary.copy(alpha = 0.35f),
                                            ambientColor = RegainLimePrimary.copy(alpha = 0.20f)
                                        )
                                        .clip(CircleShape)
                                        .background(
                                            if (buttonEnabled) RegainLimePrimary else (if (darkTheme) Color(0xFF1E2822) else Color(0xFFE2E8F0))
                                        )
                                        .clickable(enabled = buttonEnabled) {
                                            if (currentStepIndex < totalSteps - 1) {
                                                currentStepIndex++
                                            } else {
                                                val finalName = userName.ifBlank { "Deep Worker" }
                                                onFinishSurvey(
                                                    finalName,
                                                    selectedClassLevel,
                                                    isBoardExamYear,
                                                    selectedGoal,
                                                    selectedDistractionApps.joinToString(", "),
                                                    selectedTimeWindow,
                                                    selectedDailyFocusMinutes,
                                                    selectedMotivationStyles.joinToString(", ")
                                                )
                                            }
                                        }
                                        .testTag("survey_continue_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (currentStepIndex == totalSteps - 1) "Activate Focus Shield & Finish" else "Continue →",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (buttonEnabled) NearBlack else (if (darkTheme) Color(0x66FFFFFF) else Color(0xFF94A3B8))
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp)
                ) {
                    when (stage) {
                        OnboardingStage.INTRO -> {
                            // FOCIVO MASCOT INTRODUCTION SCREEN (Upper-Middle Large Mascot + Animated Text)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(10.dp))

                                // Upper-Middle Big Mascot (~210dp) with soft breathing animation
                                Box(
                                    modifier = Modifier
                                        .size(210.dp)
                                        .offset(y = floatOffset.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Radiant Lime Aura
                                    Box(
                                        modifier = Modifier
                                            .size(170.dp)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        RegainLimePrimary.copy(alpha = 0.35f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                    )
                                    RegainMascotView(
                                        pose = MascotPose.WELCOME,
                                        size = 200.dp
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Apple-style Pill Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(RegainLimePrimary.copy(alpha = 0.18f))
                                        .border(1.dp, RegainLimePrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (darkTheme) RegainLimePrimary else Color(0xFF15803D),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "MEET FOCIVO",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = PoppinsFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.2.sp,
                                                color = if (darkTheme) RegainLimePrimary else Color(0xFF15803D)
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Hey! I'm Focivo.",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Apple-style Typewriter / Animated Reveal Text
                                Text(
                                    text = if (isIntroTypingFinished) introTargetText else introDisplayText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                // Sleek 3D Minimal Feature Badges (Apple design pattern, no raw emojis)
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        IntroFeatureItem(
                                            icon = Icons.Default.Shield,
                                            iconTint = RegainLimePrimary,
                                            title = "Smart App Blocker Guard",
                                            description = "Detects doomscrolling & YouTube Shorts to instantly shield your attention."
                                        )

                                        IntroFeatureItem(
                                            icon = Icons.Default.Timer,
                                            iconTint = Color(0xFF38BDF8),
                                            title = "Deep Study Intervals",
                                            description = "Scientifically timed sprints to keep your cognitive stamina high."
                                        )

                                        IntroFeatureItem(
                                            icon = Icons.Default.Star,
                                            iconTint = Color(0xFFFBBF24),
                                            title = "Academic Consistency Streaks",
                                            description = "Track daily study streaks and build unshakeable discipline."
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        OnboardingStage.ACCESSIBILITY_PERMISSION -> {
                            // FOCIVO ASKS ACCESSIBILITY PERMISSION (Mascot-led, clear warning, later vs now options)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(6.dp))

                                // Mascot asking with thoughtful pose
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .offset(y = floatOffset.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        RegainLimePrimary.copy(alpha = 0.30f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                    )
                                    RegainMascotView(
                                        pose = if (isAccessibilityGranted) MascotPose.CELEBRATING else MascotPose.CONCERNED,
                                        size = 150.dp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = if (isAccessibilityGranted) "Shield Armed & Ready!" else "I Need Your Permission to Guard You",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (isAccessibilityGranted)
                                        "Accessibility is granted! Focivo can now block distracting feeds and keep you focused."
                                    else
                                        "To pop up the lock screen whenever you accidentally open a blocked app, Focivo needs Accessibility access.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Clear Explanation & Transparent Privacy Card
                                GlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Security,
                                                    contentDescription = null,
                                                    tint = RegainLimePrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "WHAT ACCESSIBILITY DOES",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = PoppinsFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.sp,
                                                        color = if (darkTheme) RegainLimePrimary else Color(0xFF15803D)
                                                    )
                                                )
                                            }

                                            // Status Chip
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isAccessibilityGranted)
                                                            RegainLimePrimary.copy(alpha = 0.2f)
                                                        else
                                                            Color(0x22F59E0B)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (isAccessibilityGranted) "Active ✓" else "Action Needed",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = if (isAccessibilityGranted)
                                                            (if (darkTheme) RegainLimePrimary else Color(0xFF15803D))
                                                        else
                                                            Color(0xFFF59E0B)
                                                    )
                                                )
                                            }
                                        }

                                        IntroFeatureItem(
                                            icon = Icons.Default.Lock,
                                            iconTint = RegainLimePrimary,
                                            title = "Instant Study Overlay",
                                            description = "Immediately presents the lock screen directly over blocked apps without lag or system crashes."
                                        )

                                        IntroFeatureItem(
                                            icon = Icons.Default.NotificationsActive,
                                            iconTint = Color(0xFF38BDF8),
                                            title = "Quiet Study Mode",
                                            description = "Automatically silences non-urgent alerts while your focus timer is active."
                                        )

                                        // Important Privacy Warning Box
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (darkTheme) Color(0x20FFFFFF) else Color(0x0D000000))
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PrivacyTip,
                                                    contentDescription = null,
                                                    tint = RegainLimePrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "Privacy Guarantee: Focivo works 100% locally on your phone. It never monitors your personal chats, banking details, passwords, or personal data.",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = PoppinsFontFamily,
                                                        fontSize = 11.5.sp,
                                                        lineHeight = 16.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        OnboardingStage.QUESTIONS -> {
                            // DIRECT QUESTIONS FLOW (Smooth transitions, no deep green, sleek Apple style)
                            AnimatedContent(
                                targetState = currentQuestion,
                                transitionSpec = {
                                    if (targetState.id > initialState.id) {
                                        (slideInHorizontally { width -> width } + fadeIn(animationSpec = tween(250))) togetherWith
                                                (slideOutHorizontally { width -> -width } + fadeOut(animationSpec = tween(250)))
                                    } else {
                                        (slideInHorizontally { width -> -width } + fadeIn(animationSpec = tween(250))) togetherWith
                                                (slideOutHorizontally { width -> -width } + fadeOut(animationSpec = tween(250)))
                                    }
                                },
                                label = "question_step_anim"
                            ) { question ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Question Header
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(RegainLimePrimary.copy(alpha = 0.18f))
                                                .border(1.dp, RegainLimePrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = question.icon,
                                                contentDescription = null,
                                                tint = if (darkTheme) RegainLimePrimary else Color(0xFF15803D),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = question.questionTitle,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = question.subtitle,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Question Options Section
                                    when (question.id) {
                                        0 -> {
                                            GlassCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(18.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(18.dp)) {
                                                    OutlinedTextField(
                                                        value = userName,
                                                        onValueChange = { userName = it },
                                                        label = { Text("Your Name or Alias") },
                                                        singleLine = true,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .testTag("survey_name_input"),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = RegainLimePrimary,
                                                            unfocusedBorderColor = if (darkTheme) Color.White.copy(alpha = 0.3f) else Color(0xFF94A3B8),
                                                            focusedLabelColor = if (darkTheme) RegainLimePrimary else Color(0xFF15803D),
                                                            unfocusedLabelColor = if (darkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF475569),
                                                            focusedTextColor = if (darkTheme) Color.White else Color(0xFF0F172A),
                                                            unfocusedTextColor = if (darkTheme) Color.White else Color(0xFF0F172A)
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        1 -> {
                                            OptionList(
                                                options = question.options ?: emptyList(),
                                                selectedOption = selectedClassLevel,
                                                onSelect = { selectedClassLevel = it }
                                            )
                                        }

                                        2 -> {
                                            val boardOptions = question.options ?: emptyList()
                                            OptionList(
                                                options = boardOptions,
                                                selectedOption = if (isBoardExamYear) boardOptions.getOrNull(0) ?: "" else boardOptions.getOrNull(1) ?: "",
                                                onSelect = { isBoardExamYear = it.startsWith("Yes") }
                                            )
                                        }

                                        3 -> {
                                            OptionList(
                                                options = question.options ?: emptyList(),
                                                selectedOption = selectedGoal,
                                                onSelect = { selectedGoal = it }
                                            )
                                        }

                                        4 -> {
                                            MultiSelectOptionList(
                                                options = question.options ?: emptyList(),
                                                selectedOptions = selectedDistractionApps,
                                                onToggleOption = { option ->
                                                    selectedDistractionApps = if (selectedDistractionApps.contains(option)) {
                                                        if (selectedDistractionApps.size > 1) selectedDistractionApps - option else selectedDistractionApps
                                                    } else {
                                                        selectedDistractionApps + option
                                                    }
                                                }
                                            )
                                        }

                                        5 -> {
                                            OptionList(
                                                options = question.options ?: emptyList(),
                                                selectedOption = selectedTimeWindow,
                                                onSelect = { selectedTimeWindow = it }
                                            )
                                        }

                                        6 -> {
                                            val focusOptions = question.options ?: emptyList()
                                            val currentSelectedOption = when (selectedDailyFocusMinutes) {
                                                60 -> focusOptions.getOrNull(0) ?: ""
                                                120 -> focusOptions.getOrNull(1) ?: ""
                                                180 -> focusOptions.getOrNull(2) ?: ""
                                                else -> focusOptions.getOrNull(3) ?: ""
                                            }
                                            OptionList(
                                                options = focusOptions,
                                                selectedOption = currentSelectedOption,
                                                onSelect = { option ->
                                                    selectedDailyFocusMinutes = when {
                                                        option.contains("1 Hour") -> 60
                                                        option.contains("2 Hours") -> 120
                                                        option.contains("3 Hours") -> 180
                                                        else -> 240
                                                    }
                                                }
                                            )
                                        }

                                        7 -> {
                                            MultiSelectOptionList(
                                                options = question.options ?: emptyList(),
                                                selectedOptions = selectedMotivationStyles,
                                                onToggleOption = { option ->
                                                    selectedMotivationStyles = if (selectedMotivationStyles.contains(option)) {
                                                        if (selectedMotivationStyles.size > 1) selectedMotivationStyles - option else selectedMotivationStyles
                                                    } else {
                                                        selectedMotivationStyles + option
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun questionTitleHeader(stepLabel: String): String {
    return stepLabel.replace(" • ", " - ")
}

@Composable
private fun OptionList(
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit
) {
    val darkTheme = isAppInDarkTheme()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(options) { option ->
            val isSelected = option == selectedOption

            val cardBg = if (isSelected) {
                if (darkTheme) Color(0xEE1C2F22) else Color(0xF2F0FDF4)
            } else {
                if (darkTheme) Color(0xCC111813) else Color(0xFAFFFFFF)
            }

            val borderColor = if (isSelected) {
                RegainLimePrimary
            } else {
                if (darkTheme) Color(0x22FFFFFF) else Color(0xFFE2E8F0)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = if (isSelected) 3.dp else 1.dp,
                        shape = RoundedCornerShape(14.dp),
                        spotColor = if (isSelected) RegainLimePrimary.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.08f),
                        ambientColor = Color.Black.copy(alpha = 0.04f)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 14.5.sp,
                            color = if (isSelected) {
                                if (darkTheme) Color.White else NearBlack
                            } else {
                                if (darkTheme) Color(0xDDFFFFFF) else MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(RegainLimePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = NearBlack,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 1.5.dp,
                                    color = if (darkTheme) Color(0x40FFFFFF) else Color(0xFFCBD5E1),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiSelectOptionList(
    options: List<String>,
    selectedOptions: Set<String>,
    onToggleOption: (String) -> Unit
) {
    val darkTheme = isAppInDarkTheme()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(options) { option ->
            val isSelected = selectedOptions.contains(option)

            val cardBg = if (isSelected) {
                if (darkTheme) Color(0xEE1C2F22) else Color(0xF2F0FDF4)
            } else {
                if (darkTheme) Color(0xCC111813) else Color(0xFAFFFFFF)
            }

            val borderColor = if (isSelected) {
                RegainLimePrimary
            } else {
                if (darkTheme) Color(0x22FFFFFF) else Color(0xFFE2E8F0)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = if (isSelected) 3.dp else 1.dp,
                        shape = RoundedCornerShape(14.dp),
                        spotColor = if (isSelected) RegainLimePrimary.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.08f),
                        ambientColor = Color.Black.copy(alpha = 0.04f)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onToggleOption(option) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            fontSize = 14.5.sp,
                            color = if (isSelected) {
                                if (darkTheme) Color.White else NearBlack
                            } else {
                                if (darkTheme) Color(0xDDFFFFFF) else MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) RegainLimePrimary else Color.Transparent)
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) RegainLimePrimary else (if (darkTheme) Color(0x40FFFFFF) else Color(0xFFCBD5E1)),
                                shape = RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Checked",
                                tint = NearBlack,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroFeatureItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    val darkTheme = isAppInDarkTheme()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = if (darkTheme) 0.18f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
