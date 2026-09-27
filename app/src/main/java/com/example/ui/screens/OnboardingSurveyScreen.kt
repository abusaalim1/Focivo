package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.R
import com.example.ui.components.GlassCard
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeLight
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.AiStudyGuardManager
import com.example.util.PermissionUtils
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
    QUESTIONS
}

enum class PermissionOverlayType(
    val title: String,
    val shortSubtitle: String,
    val emoji: String,
    val permissionName: String,
    val buttonText: String
) {
    NOTIFICATIONS(
        title = "Allow Notifications",
        shortSubtitle = "To keep you motivated with timely study reminders, streak milestones, and focus timer alerts, I need permission to send you notifications.",
        emoji = "",
        permissionName = "Notification Alerts",
        buttonText = "Enable Notifications"
    ),
    BATTERY_OPTIMIZATION(
        title = "Disable Battery Saver",
        shortSubtitle = "I want to keep watching over your focus sessions even when your phone tries to save battery — turning this off keeps me active in the background so I never miss a moment.",
        emoji = "",
        permissionName = "Background Battery Exemption",
        buttonText = "Disable Battery Saver"
    ),
    ACCESSIBILITY_SHIELD(
        title = "Enable Accessibility Shield",
        shortSubtitle = "To keep distracting apps like Instagram Reels and YouTube Shorts away while you study, I need this permission — it lets me step in and block them the moment they try to pull you away.",
        emoji = "",
        permissionName = "Accessibility Shield Guard",
        buttonText = "Enable Shield Guard"
    ),
    USAGE_ACCESS(
        title = "Grant Usage Access",
        shortSubtitle = "This lets me see how much time you're really spending on your phone, so I can track your progress accurately and help you hit your daily focus goals.",
        emoji = "",
        permissionName = "App Usage Statistics",
        buttonText = "Grant Usage Access"
    ),
    OVERLAY_LOCK(
        title = "Display Over Other Apps",
        shortSubtitle = "When a blocked app tries to open during your study time, I need this permission to pop up instantly and remind you to stay focused.",
        emoji = "",
        permissionName = "Overlay Lock Window",
        buttonText = "Enable App Lock"
    )
}

/**
 * OnboardingSurveyScreen:
 * 1. Intro Stage: Static Mascot Image (No video, no motion animation) with typewriter text.
 * 2. Questions Flow: Option completion triggers a smooth, premium Obsidian Modal Overlay.
 *    Overlay contains clear explanation, "Enable Now" and "Maybe Later" options.
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

    // Active Permission Overlay State
    var activeOverlayType by remember { mutableStateOf<PermissionOverlayType?>(null) }

    // Intro typewriter state
    var introDisplayText by remember { mutableStateOf("") }
    var isIntroTypingFinished by remember { mutableStateOf(false) }

    // Track real permission statuses updated on resume
    var isAccessibilityGranted by remember { mutableStateOf(AiStudyGuardManager.isAccessibilityPermissionGranted(context)) }
    var isUsageStatsGranted by remember { mutableStateOf(PermissionUtils.hasUsageStatsPermission(context)) }
    var isOverlayGranted by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var isNotificationsGranted by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
    var isBatteryOptimizationDisabled by remember { mutableStateOf(PermissionUtils.isBatteryOptimizationDisabled(context)) }

    // Refresh permission states when returning from settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val newAcc = AiStudyGuardManager.isAccessibilityPermissionGranted(context)
                val newUsage = PermissionUtils.hasUsageStatsPermission(context)
                val newOverlay = PermissionUtils.hasOverlayPermission(context)
                val newNotif = PermissionUtils.hasNotificationPermission(context)
                val newBatt = PermissionUtils.isBatteryOptimizationDisabled(context)

                isAccessibilityGranted = newAcc
                isUsageStatsGranted = newUsage
                isOverlayGranted = newOverlay
                isNotificationsGranted = newNotif
                isBatteryOptimizationDisabled = newBatt

                // If user just granted the active overlay permission, smoothly dismiss and advance
                when (activeOverlayType) {
                    PermissionOverlayType.NOTIFICATIONS -> {
                        if (newNotif) {
                            activeOverlayType = null
                            if (currentStepIndex == 0) currentStepIndex++
                        }
                    }
                    PermissionOverlayType.BATTERY_OPTIMIZATION -> {
                        if (newBatt) {
                            activeOverlayType = null
                            if (currentStepIndex == 2) currentStepIndex++
                        }
                    }
                    PermissionOverlayType.ACCESSIBILITY_SHIELD -> {
                        if (newAcc) {
                            activeOverlayType = null
                            if (currentStepIndex == 4) currentStepIndex++
                        }
                    }
                    PermissionOverlayType.USAGE_ACCESS -> {
                        if (newUsage) {
                            activeOverlayType = null
                            if (currentStepIndex == 6) currentStepIndex++
                        }
                    }
                    PermissionOverlayType.OVERLAY_LOCK -> {
                        if (newOverlay) {
                            activeOverlayType = null
                        }
                    }
                    null -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Form field states
    var userName by remember { mutableStateOf(initialUserName) }
    var selectedClassLevel by remember { mutableStateOf("Class 11 – 12 (Higher Secondary) 🎒") }
    var isBoardExamYear by remember { mutableStateOf(true) }
    var selectedGoal by remember { mutableStateOf("Acing Exams & Top Academic Grades 🏆") }
    var selectedDistractionApps by remember { mutableStateOf(setOf<String>()) }
    var selectedTimeWindow by remember { mutableStateOf("Evening (6 PM – 10 PM) 🌆") }
    var selectedDailyFocusMinutes by remember { mutableIntStateOf(240) }
    var selectedMotivationStyles by remember { mutableStateOf(setOf<String>()) }

    // Typewriter effect for Intro
    val introTargetText = "Hi, I'm Civo — think of me as your focus companion. I'll help you block distractions, build strong study habits, and stay on track!"
    LaunchedEffect(stage) {
        if (stage == OnboardingStage.INTRO && !isIntroTypingFinished) {
            introDisplayText = ""
            for (i in introTargetText.indices) {
                introDisplayText = introTargetText.substring(0, i + 1)
                delay(14L)
            }
            isIntroTypingFinished = true
        }
    }

    val questions = listOf(
        OnboardingSurveyQuestion(
            id = 0,
            stepLabel = "STEP 1 OF 8 • PROFILE",
            questionTitle = "What should I call you?",
            subtitle = "Enter your preferred name or study alias.",
            icon = Icons.Default.Person
        ),
        OnboardingSurveyQuestion(
            id = 1,
            stepLabel = "STEP 2 OF 8 • ACADEMICS",
            questionTitle = "What is your academic level?",
            subtitle = "Optimizes study intervals and guard strictness.",
            icon = Icons.Default.School,
            options = listOf(
                "Class 9 – 10 (Secondary)",
                "Class 11 – 12 (Higher Secondary)",
                "Competitive Exams (JEE / NEET / UPSC / GATE)",
                "College / University Degree",
                "Self-Learner / Professional"
            )
        ),
        OnboardingSurveyQuestion(
            id = 2,
            stepLabel = "STEP 3 OF 8 • MILESTONE",
            questionTitle = "Is this a major exam year?",
            subtitle = "Enables background guard and smart reminders.",
            icon = Icons.Default.Star,
            options = listOf(
                "Yes — Exam / Milestone Year",
                "No — Regular Academic Year"
            )
        ),
        OnboardingSurveyQuestion(
            id = 3,
            stepLabel = "STEP 4 OF 8 • GOAL",
            questionTitle = "What is your main focus goal?",
            subtitle = "Personalizes daily motivation and recommendations.",
            icon = Icons.Default.GraphicEq,
            options = listOf(
                "Acing Exams & Academic Excellence",
                "Building Daily Study Consistency",
                "Beating Social Media Distractions",
                "Maximizing Deep Focus Hours"
            )
        ),
        OnboardingSurveyQuestion(
            id = 4,
            stepLabel = "STEP 5 OF 8 • DISTRACTIONS",
            questionTitle = "Which apps distract you most?",
            subtitle = "Focivo will shield you from these during study hours.",
            icon = Icons.Default.Phone,
            options = listOf(
                "Instagram / Reels",
                "YouTube / Shorts",
                "Snapchat / Messaging",
                "Mobile Games",
                "Reddit / Web Browsing"
            ),
            isMultiSelect = true
        ),
        OnboardingSurveyQuestion(
            id = 5,
            stepLabel = "STEP 6 OF 8 • PEAK HOURS",
            questionTitle = "When do you feel most alert?",
            subtitle = "Schedules study sessions during high-energy windows.",
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
            questionTitle = "What is your daily focus target?",
            subtitle = "Track screen time savings and build your streak.",
            icon = Icons.Default.Timer,
            options = listOf(
                "1 Hour (60 mins)",
                "2 Hours (120 mins)",
                "3 Hours (180 mins)",
                "4+ Hours (240+ mins)"
            )
        ),
        OnboardingSurveyQuestion(
            id = 7,
            stepLabel = "STEP 8 OF 8 • PROTECTION",
            questionTitle = "Choose your protection style",
            subtitle = "Select features to keep you in the zone.",
            icon = Icons.Default.Shield,
            options = listOf(
                "Strict App Blocker & Study Lock",
                "AI Academic Sentinel (ChatGPT / Claude)",
                "Smart Scheduled Study Windows",
                "Streak Leaderboard & Daily Tracking"
            ),
            isMultiSelect = true
        )
    )

    val currentQuestion = questions[currentStepIndex]
    val totalSteps = questions.size

    val bgGradient = if (darkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0D151E),
                Color(0xFF070C12),
                Color(0xFF030508)
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
                            if (stage == OnboardingStage.QUESTIONS) {
                                IconButton(
                                    onClick = {
                                        if (currentStepIndex > 0) {
                                            currentStepIndex--
                                        } else {
                                            stage = OnboardingStage.INTRO
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

                            // Header Text
                            Text(
                                text = when (stage) {
                                    OnboardingStage.INTRO -> "FOCIVO ONBOARDING"
                                    else -> questionTitleHeader(currentQuestion.stepLabel)
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

                        // Vivid Lime Green Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (darkTheme) Color(0xFF1B2820) else Color(0xFFE2E8F0))
                        ) {
                            val activeProgress = when (stage) {
                                OnboardingStage.INTRO -> 0.10f
                                else -> 0.10f + (0.90f * ((currentStepIndex + 1).toFloat() / totalSteps.toFloat()))
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
                                            stage = OnboardingStage.QUESTIONS
                                            currentStepIndex = 0
                                        }
                                        .testTag("survey_intro_next_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Start Onboarding →",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = NearBlack
                                        )
                                    )
                                }
                            }

                            else -> {
                                val buttonEnabled = when (currentStepIndex) {
                                    0 -> userName.isNotBlank()
                                    4 -> selectedDistractionApps.isNotEmpty()
                                    7 -> selectedMotivationStyles.isNotEmpty()
                                    else -> true
                                }

                                val isLastStep = currentStepIndex == totalSteps - 1

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .shadow(
                                            elevation = if (buttonEnabled) 4.dp else 0.dp,
                                            shape = CircleShape,
                                            spotColor = RegainLimePrimary.copy(alpha = 0.4f),
                                            ambientColor = RegainLimePrimary.copy(alpha = 0.2f)
                                        )
                                        .clip(CircleShape)
                                        .background(
                                            if (buttonEnabled) RegainLimePrimary else (if (darkTheme) Color(0x33FFFFFF) else Color(0xFFCBD5E1))
                                        )
                                        .clickable(enabled = buttonEnabled) {
                                            when (currentStepIndex) {
                                                0 -> {
                                                    if (!isNotificationsGranted) {
                                                        activeOverlayType = PermissionOverlayType.NOTIFICATIONS
                                                    } else {
                                                        currentStepIndex++
                                                    }
                                                }
                                                1 -> {
                                                    if (!isNotificationsGranted) {
                                                        activeOverlayType = PermissionOverlayType.NOTIFICATIONS
                                                    } else {
                                                        currentStepIndex++
                                                    }
                                                }
                                                2 -> {
                                                    if (!isBatteryOptimizationDisabled) {
                                                        activeOverlayType = PermissionOverlayType.BATTERY_OPTIMIZATION
                                                    } else {
                                                        currentStepIndex++
                                                    }
                                                }
                                                3 -> {
                                                    if (!isNotificationsGranted) {
                                                        activeOverlayType = PermissionOverlayType.NOTIFICATIONS
                                                    } else {
                                                        currentStepIndex++
                                                    }
                                                }
                                                4 -> {
                                                    if (!isAccessibilityGranted) {
                                                        activeOverlayType = PermissionOverlayType.ACCESSIBILITY_SHIELD
                                                    } else {
                                                        currentStepIndex++
                                                    }
                                                }
                                                5 -> {
                                                    if (!isUsageStatsGranted) {
                                                        activeOverlayType = PermissionOverlayType.USAGE_ACCESS
                                                    } else {
                                                        currentStepIndex++
                                                    }
                                                }
                                                6 -> {
                                                    if (!isUsageStatsGranted) {
                                                        activeOverlayType = PermissionOverlayType.USAGE_ACCESS
                                                    } else {
                                                        currentStepIndex++
                                                    }
                                                }
                                                7 -> {
                                                    if (!isOverlayGranted) {
                                                        activeOverlayType = PermissionOverlayType.OVERLAY_LOCK
                                                    } else {
                                                        onFinishSurvey(
                                                            userName.ifBlank { "Focus Champion" },
                                                            selectedClassLevel,
                                                            isBoardExamYear,
                                                            selectedGoal,
                                                            if (selectedDistractionApps.isEmpty()) "Instagram / Reels, YouTube / Shorts" else selectedDistractionApps.joinToString(", "),
                                                            selectedTimeWindow,
                                                            selectedDailyFocusMinutes,
                                                            if (selectedMotivationStyles.isEmpty()) "Strict App Blocker & Study Lock" else selectedMotivationStyles.joinToString(", ")
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    if (isLastStep) {
                                                        onFinishSurvey(
                                                            userName.ifBlank { "Focus Champion" },
                                                            selectedClassLevel,
                                                            isBoardExamYear,
                                                            selectedGoal,
                                                            if (selectedDistractionApps.isEmpty()) "Instagram / Reels, YouTube / Shorts" else selectedDistractionApps.joinToString(", "),
                                                            selectedTimeWindow,
                                                            selectedDailyFocusMinutes,
                                                            if (selectedMotivationStyles.isEmpty()) "Strict App Blocker & Study Lock" else selectedMotivationStyles.joinToString(", ")
                                                        )
                                                    } else {
                                                        currentStepIndex++
                                                    }
                                                }
                                            }
                                        }
                                        .testTag("survey_next_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isLastStep) "Finish & Launch Focivo" else "Next Step →",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (buttonEnabled) NearBlack else Color.Gray
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    AnimatedContent(
                        targetState = stage to currentStepIndex,
                        transitionSpec = {
                            if (targetState.first == OnboardingStage.INTRO) {
                                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                            } else {
                                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                            }
                        },
                        label = "onboarding_stage_transition"
                    ) { (targetStage, stepIndex) ->
                        when (targetStage) {
                            OnboardingStage.INTRO -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Static Mascot Container (Strictly NO motion, NO floating effect, NO video)
                                    Box(
                                        modifier = Modifier.size(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(160.dp)
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
                                        Image(
                                            painter = painterResource(id = R.drawable.mascot_welcome),
                                            contentDescription = "Static Focivo Mascot",
                                            modifier = Modifier.size(180.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // Civo Title Header
                                    Text(
                                        text = "MEET CIVO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            color = if (darkTheme) RegainLimePrimary else Color(0xFF15803D)
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Your Focus Companion ✨",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 24.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Glass Card with Speech
                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(22.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = RegainLimePrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = "CIVO SAYS:",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = PoppinsFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.2.sp,
                                                        color = if (darkTheme) RegainLimePrimary else Color(0xFF15803D)
                                                    )
                                                )
                                            }

                                            Text(
                                                text = introDisplayText,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontFamily = PoppinsFontFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 15.sp,
                                                    lineHeight = 22.sp,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                val question = questions[stepIndex]

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
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

                                    // Question Options Flow
                                    when (question.id) {
                                        0 -> { // Step 1: Name Question
                                            GlassCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(18.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(18.dp)) {
                                                    OutlinedTextField(
                                                        value = userName,
                                                        onValueChange = { userName = it },
                                                        label = { Text("Your Preferred Name or Alias ✨") },
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

                                        1 -> { // Step 2: Academics Level
                                            OptionList(
                                                options = question.options ?: emptyList(),
                                                selectedOption = selectedClassLevel,
                                                onSelect = {
                                                    selectedClassLevel = it
                                                    if (!isNotificationsGranted) {
                                                        activeOverlayType = PermissionOverlayType.NOTIFICATIONS
                                                    }
                                                }
                                            )
                                        }

                                        2 -> { // Step 3: Milestone Exam -> Triggers Battery Optimization Overlay on Click
                                            val boardOptions = question.options ?: emptyList()
                                            OptionList(
                                                options = boardOptions,
                                                selectedOption = if (isBoardExamYear) boardOptions.getOrNull(0) ?: "" else boardOptions.getOrNull(1) ?: "",
                                                onSelect = { option ->
                                                    isBoardExamYear = option.startsWith("Yes")
                                                    if (!isBatteryOptimizationDisabled) {
                                                        activeOverlayType = PermissionOverlayType.BATTERY_OPTIMIZATION
                                                    }
                                                }
                                            )
                                        }

                                        3 -> { // Step 4: Productivity Goal
                                            OptionList(
                                                options = question.options ?: emptyList(),
                                                selectedOption = selectedGoal,
                                                onSelect = {
                                                    selectedGoal = it
                                                    if (!isNotificationsGranted) {
                                                        activeOverlayType = PermissionOverlayType.NOTIFICATIONS
                                                    }
                                                }
                                            )
                                        }

                                        4 -> { // Step 5: Distraction Apps -> Accessibility Permission on selection
                                            MultiSelectOptionList(
                                                options = question.options ?: emptyList(),
                                                selectedOptions = selectedDistractionApps,
                                                onToggleOption = { option ->
                                                    selectedDistractionApps = if (selectedDistractionApps.contains(option)) {
                                                        selectedDistractionApps - option
                                                    } else {
                                                        selectedDistractionApps + option
                                                    }
                                                    if (!isAccessibilityGranted) {
                                                        activeOverlayType = PermissionOverlayType.ACCESSIBILITY_SHIELD
                                                    }
                                                }
                                            )
                                        }

                                        5 -> { // Step 6: Peak Hours
                                            OptionList(
                                                options = question.options ?: emptyList(),
                                                selectedOption = selectedTimeWindow,
                                                onSelect = {
                                                    selectedTimeWindow = it
                                                    if (!isUsageStatsGranted) {
                                                        activeOverlayType = PermissionOverlayType.USAGE_ACCESS
                                                    }
                                                }
                                            )
                                        }

                                        6 -> { // Step 7: Daily Focus Target -> Usage Access Permission on selection
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
                                                    if (!isUsageStatsGranted) {
                                                        activeOverlayType = PermissionOverlayType.USAGE_ACCESS
                                                    }
                                                }
                                            )
                                        }

                                        7 -> { // Step 8: Accountability / Blocker -> Overlay Permission
                                            MultiSelectOptionList(
                                                options = question.options ?: emptyList(),
                                                selectedOptions = selectedMotivationStyles,
                                                onToggleOption = { option ->
                                                    selectedMotivationStyles = if (selectedMotivationStyles.contains(option)) {
                                                        selectedMotivationStyles - option
                                                    } else {
                                                        selectedMotivationStyles + option
                                                    }
                                                    if (!isOverlayGranted) {
                                                        activeOverlayType = PermissionOverlayType.OVERLAY_LOCK
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Smooth Animated Obsidian Permission Modal Overlay
            activeOverlayType?.let { overlayType ->
                val isGranted = when (overlayType) {
                    PermissionOverlayType.NOTIFICATIONS -> isNotificationsGranted
                    PermissionOverlayType.BATTERY_OPTIMIZATION -> isBatteryOptimizationDisabled
                    PermissionOverlayType.ACCESSIBILITY_SHIELD -> isAccessibilityGranted
                    PermissionOverlayType.USAGE_ACCESS -> isUsageStatsGranted
                    PermissionOverlayType.OVERLAY_LOCK -> isOverlayGranted
                }

                ObsidianPermissionModalOverlay(
                    visible = true,
                    title = overlayType.title,
                    emoji = overlayType.emoji,
                    shortSubtitle = overlayType.shortSubtitle,
                    permissionName = overlayType.permissionName,
                    buttonText = overlayType.buttonText,
                    isGranted = isGranted,
                    onEnableClick = {
                        when (overlayType) {
                            PermissionOverlayType.NOTIFICATIONS -> {
                                PermissionUtils.openAppNotificationSettings(context)
                            }
                            PermissionOverlayType.BATTERY_OPTIMIZATION -> {
                                PermissionUtils.openBatteryOptimizationSettings(context)
                            }
                            PermissionOverlayType.ACCESSIBILITY_SHIELD -> {
                                AiStudyGuardManager.openAccessibilitySettings(context)
                            }
                            PermissionOverlayType.USAGE_ACCESS -> {
                                PermissionUtils.openUsageStatsSettings(context)
                            }
                            PermissionOverlayType.OVERLAY_LOCK -> {
                                PermissionUtils.openOverlaySettings(context)
                            }
                        }
                    },
                    onLaterClick = {
                        activeOverlayType = null
                        // Advance to next step smoothly when user taps later
                        if (currentStepIndex < totalSteps - 1) {
                            currentStepIndex++
                        } else if (currentStepIndex == totalSteps - 1 && overlayType == PermissionOverlayType.OVERLAY_LOCK) {
                            onFinishSurvey(
                                userName.ifBlank { "Focus Champion" },
                                selectedClassLevel,
                                isBoardExamYear,
                                selectedGoal,
                                if (selectedDistractionApps.isEmpty()) "Instagram / Reels 📸, YouTube / Shorts 🎥" else selectedDistractionApps.joinToString(", "),
                                selectedTimeWindow,
                                selectedDailyFocusMinutes,
                                if (selectedMotivationStyles.isEmpty()) "Strict AI Guard & App Blocker 🔒" else selectedMotivationStyles.joinToString(", ")
                            )
                        }
                    },
                    onDismissRequest = {
                        activeOverlayType = null
                    }
                )
            }
        }
    }
}

private fun questionTitleHeader(stepLabel: String): String {
    return stepLabel.replace(" • ", " - ")
}

/**
 * Premium Obsidian Modal Overlay with smooth animated entrance/exit,
 * dark frosted glass backdrop, glowing lime/cyan gradient border, and clean typography.
 */
@Composable
private fun ObsidianPermissionModalOverlay(
    visible: Boolean,
    title: String,
    emoji: String,
    shortSubtitle: String,
    permissionName: String,
    buttonText: String,
    isGranted: Boolean,
    onEnableClick: () -> Unit,
    onLaterClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC04070D))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismissRequest() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(
                    initialScale = 0.88f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                ) + slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                ) + fadeIn(tween(220)),
                exit = scaleOut(targetScale = 0.90f, animationSpec = tween(180)) +
                        slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = tween(180)) +
                        fadeOut(tween(180))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .padding(horizontal = 16.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(26.dp),
                            spotColor = Color.Black.copy(alpha = 0.8f),
                            ambientColor = Color.Black.copy(alpha = 0.6f)
                        )
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0F151E),
                                    Color(0xFF080C12),
                                    Color(0xFF05070B)
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x55FFFFFF),
                                    Color(0x20FFFFFF),
                                    Color(0x0DFFFFFF)
                                )
                            ),
                            shape = RoundedCornerShape(26.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* prevent backdrop clicks from propagating */ }
                        .padding(22.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Mascot Icon with Elegant Ambient Halo
                        Box(
                            modifier = Modifier
                                .size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0x28FFFFFF),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            )
                            Image(
                                painter = painterResource(id = R.drawable.mascot_welcome),
                                contentDescription = "Civo Mascot",
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color(0x35FFFFFF), CircleShape)
                            )
                        }

                        // Badge / Permission category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0x18FFFFFF))
                                .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "SYSTEM PERMISSION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    letterSpacing = 1.3.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            )
                        }

                        // Title
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = Color.White
                            ),
                            textAlign = TextAlign.Center
                        )

                        // Short Subtitle
                        Text(
                            text = shortSubtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                color = Color(0xFF94A3B8)
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Action Buttons
                        if (!isGranted) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Enable Button (Glowing Neon Lime)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .shadow(
                                            elevation = 6.dp,
                                            shape = RoundedCornerShape(14.dp),
                                            spotColor = RegainLimePrimary.copy(alpha = 0.5f),
                                            ambientColor = RegainLimePrimary.copy(alpha = 0.25f)
                                        )
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    RegainLimePrimary,
                                                    Color(0xFFAEF72A),
                                                    RegainLimeLight
                                                )
                                            )
                                        )
                                        .clickable { onEnableClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = buttonText,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = NearBlack
                                        )
                                    )
                                }

                                // Later Button (Ghost button)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x14FFFFFF))
                                        .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(14.dp))
                                        .clickable { onLaterClick() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Maybe Later",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = PoppinsFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    )
                                }
                            }
                        } else {
                            // Granted feedback
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x2522C55E))
                                    .border(1.dp, Color(0x6022C55E), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Permission Granted",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = RegainLimePrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionList(
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit
) {
    val darkTheme = isAppInDarkTheme()

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { option ->
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

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { option ->
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
