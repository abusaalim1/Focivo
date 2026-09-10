package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.theme.RegainNeonEmerald
import com.example.ui.theme.VioletAccent

data class StudentQuestion(
    val id: Int,
    val stepLabel: String,
    val questionTitle: String,
    val subtitle: String,
    val icon: ImageVector,
    val options: List<String>? = null
)

@Composable
fun QuestionnaireScreen(
    initialUserName: String = "",
    onFinishQuestions: (
        name: String,
        age: Int,
        studentClass: String,
        stream: String,
        studySchedule: String,
        mobileBreakTime: String,
        goal: String,
        focusStyle: String,
        targetHours: Int,
        peakTime: String,
        distraction: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onFinishIntakeSurvey: (
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
    val isDark = isSystemInDarkTheme()

    var currentStep by remember { mutableIntStateOf(0) }

    // Student Intake Answers State
    var answerName by remember { mutableStateOf(initialUserName) }
    var answerClassLevel by remember { mutableStateOf("Class 11") }
    var answerIsBoardExam by remember { mutableStateOf(true) }
    var answerPrimaryGoal by remember { mutableStateOf("Improve grades & top my class") }
    var answerDistractionApp by remember { mutableStateOf("Instagram Reels & TikTok") }
    var answerPreferredTime by remember { mutableStateOf("Evening (6 PM - 10 PM)") }
    var answerScreenTimeGoalMinutes by remember { mutableIntStateOf(480) }
    var answerMotivationStyle by remember { mutableStateOf("Streaks (Don't break daily streak)") }

    val questions = remember {
        listOf(
            StudentQuestion(
                id = 0,
                stepLabel = "STEP 01 OF 08 · PERSONALIZATION",
                questionTitle = "What should we\ncall you?",
                subtitle = "Focivo will personalize your daily focus goals, reminders, and study shield in your name.",
                icon = Icons.Default.Person
            ),
            StudentQuestion(
                id = 1,
                stepLabel = "STEP 02 OF 08 · ACADEMICS",
                questionTitle = "Which class or year\nare you in?",
                subtitle = "This helps calibrate daily study pressure and lock intensity.",
                icon = Icons.Default.School,
                options = listOf(
                    "Class 9th / 10th (Secondary)",
                    "Class 11th",
                    "Class 12th (Board Exam)",
                    "College / University",
                    "Competitive Exam Aspirant (JEE / NEET / CA / UPSC)"
                )
            ),
            StudentQuestion(
                id = 2,
                stepLabel = "STEP 03 OF 08 · EXAM FOCUS",
                questionTitle = "Is this a board or\nmajor exam year?",
                subtitle = "Board exam students receive specialized focus warnings when screen time exceeds goals.",
                icon = Icons.Default.MenuBook,
                options = listOf(
                    "Yes — Board / Major Competitive Exam Year",
                    "No — Regular Academic Year"
                )
            ),
            StudentQuestion(
                id = 3,
                stepLabel = "STEP 04 OF 08 · CORE OBJECTIVE",
                questionTitle = "What is your primary\nstudy goal?",
                subtitle = "We will align your daily study streak and target hours with this objective.",
                icon = Icons.Default.Timer,
                options = listOf(
                    "Pass exams with strong marks",
                    "Improve grades & top my class",
                    "Build an unbreakable daily focus habit",
                    "Crack a competitive exam"
                )
            ),
            StudentQuestion(
                id = 4,
                stepLabel = "STEP 05 OF 08 · FOCUS THREAT",
                questionTitle = "Which app steals\nyour focus most?",
                subtitle = "AI Shield will prioritize monitoring and blocking this app during focus sessions.",
                icon = Icons.Default.Phone,
                options = listOf(
                    "Instagram Reels & TikTok",
                    "YouTube Shorts & Videos",
                    "Snapchat & Social Messaging",
                    "BGMI / Free Fire & Games",
                    "Reddit & Twitter"
                )
            ),
            StudentQuestion(
                id = 5,
                stepLabel = "STEP 06 OF 08 · PEAK TIME",
                questionTitle = "When do you study\nbest during the day?",
                subtitle = "Your scheduled study blocks will automatically default to this window.",
                icon = Icons.Default.Coffee,
                options = listOf(
                    "Morning (6:00 AM – 10:00 AM)",
                    "Afternoon (1:00 PM – 5:00 PM)",
                    "Evening (6:00 PM – 10:00 PM)",
                    "Night (9:00 PM – 2:00 AM)"
                )
            ),
            StudentQuestion(
                id = 6,
                stepLabel = "STEP 07 OF 08 · SCREEN TIME LIMIT",
                questionTitle = "What is your daily\nscreen time limit goal?",
                subtitle = "Crossing this limit triggers personalized break reminders based on your exam urgency.",
                icon = Icons.Default.GraphicEq,
                options = listOf(
                    "4 Hours / day (240 mins)",
                    "6 Hours / day (360 mins)",
                    "8 Hours / day (480 mins)",
                    "10 Hours / day (600 mins)"
                )
            ),
            StudentQuestion(
                id = 7,
                stepLabel = "STEP 08 OF 08 · MOTIVATION STYLE",
                questionTitle = "What motivates you\nmost to stay disciplined?",
                subtitle = "Your home banners and notification messages will adapt to your preferred style.",
                icon = Icons.Default.Shield,
                options = listOf(
                    "Streaks (Keep daily study habit alive)",
                    "Leaderboard competition (Compete for top rank)",
                    "Quiet reminders (Gentle, calm guidance)"
                )
            )
        )
    }

    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            listOf(Color(0xFF0F0D18), Color(0xFF161226), Color(0xFF0C0A14))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFFAF8FE), Color(0xFFF2EEFD), Color(0xFFFAF9FF))
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("questionnaire_screen"),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Progress Bar & Back button
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (currentStep > 0) {
                            IconButton(
                                onClick = { currentStep-- },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0x1AFFFFFF) else Color(0x0E000000))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Question",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(38.dp))
                        }

                        Text(
                            text = "STUDENT CALIBRATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${currentStep + 1}/8",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = VioletAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Skip",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier
                                    .clickable {
                                        onFinishIntakeSurvey(
                                            answerName.trim().ifEmpty { "Student Scholar" },
                                            answerClassLevel,
                                            answerIsBoardExam,
                                            answerPrimaryGoal,
                                            answerDistractionApp,
                                            answerPreferredTime,
                                            answerScreenTimeGoalMinutes,
                                            answerMotivationStyle
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 8-step Segmented Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (i in 0..7) {
                            val isPassed = i <= currentStep
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isPassed) VioletAccent else (if (isDark) Color(0x22FFFFFF) else Color(0x18000000))
                                    )
                            )
                        }
                    }
                }

                // Question Body with Smooth Slide/Fade Transition
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(350)) { it / 3 } + fadeIn(tween(350))) togetherWith
                                    (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)))
                        } else {
                            (slideInHorizontally(tween(350)) { -it / 3 } + fadeIn(tween(350))) togetherWith
                                    (slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(300)))
                        }
                    },
                    label = "student_question_transition",
                    modifier = Modifier.weight(1f)
                ) { stepIdx ->
                    val q = questions[stepIdx]
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Question Icon Badge
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(VioletAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = q.icon,
                                    contentDescription = null,
                                    tint = VioletAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = q.stepLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = VioletAccent,
                                    letterSpacing = 1.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = q.questionTitle,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Light,
                                    fontSize = 26.sp,
                                    lineHeight = 34.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = q.subtitle,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        // Question input depending on step
                        if (stepIdx == 0) {
                            item {
                                OutlinedTextField(
                                    value = answerName,
                                    onValueChange = { answerName = it },
                                    label = { Text("Your Full Name or Nickname") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("student_name_input"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = VioletAccent,
                                        unfocusedBorderColor = if (isDark) Color(0x35FFFFFF) else Color(0x25000000),
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Student Protection Highlight Card
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isDark) Color(0x1F7C3AED) else Color(0x127C3AED),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VioletAccent.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "🎓 100% Student-Centric Focus Mode",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = VioletAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Automatic timetable locking, emergency calls pass-through, and doubt-clearing with Claude/ChatGPT enabled.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        } else {
                            val options = q.options ?: emptyList()
                            val selectedOption = when (stepIdx) {
                                1 -> answerClassLevel
                                2 -> if (answerIsBoardExam) options.firstOrNull() ?: "" else options.lastOrNull() ?: ""
                                3 -> answerPrimaryGoal
                                4 -> answerDistractionApp
                                5 -> answerPreferredTime
                                6 -> when (answerScreenTimeGoalMinutes) {
                                    240 -> options.getOrNull(0) ?: ""
                                    360 -> options.getOrNull(1) ?: ""
                                    600 -> options.getOrNull(3) ?: ""
                                    else -> options.getOrNull(2) ?: ""
                                }
                                7 -> answerMotivationStyle
                                else -> ""
                            }

                            items(options) { opt ->
                                val isSelected = opt == selectedOption
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            when (stepIdx) {
                                                1 -> answerClassLevel = opt
                                                2 -> answerIsBoardExam = opt.startsWith("Yes")
                                                3 -> answerPrimaryGoal = opt
                                                4 -> answerDistractionApp = opt
                                                5 -> answerPreferredTime = opt
                                                6 -> {
                                                    answerScreenTimeGoalMinutes = when {
                                                        opt.startsWith("4") -> 240
                                                        opt.startsWith("6") -> 360
                                                        opt.startsWith("10") -> 600
                                                        else -> 480
                                                    }
                                                }
                                                7 -> answerMotivationStyle = opt
                                            }
                                        },
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = opt,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) VioletAccent else MaterialTheme.colorScheme.onBackground,
                                                fontSize = 13.5.sp
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.5.dp,
                                                    color = if (isSelected) VioletAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                    shape = CircleShape
                                                )
                                                .background(if (isSelected) VioletAccent else Color.Transparent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // If on the last question (stepIdx == 7), show the AI Shield Promise Card
                            if (stepIdx == 7) {
                                item {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isDark) Color(0x1810B981) else Color(0x1010B981),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, RegainNeonEmerald.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Shield,
                                                    contentDescription = null,
                                                    tint = RegainNeonEmerald,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "AI Automatic Study Shield Policy",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = RegainNeonEmerald,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "• AI will automatically lock distracting apps during study hours.\n• Emergency phone calls will never be blocked.\n• Claude & ChatGPT allowed for clearing doubts.\n• Personalized focus warnings for board exam prep.",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp,
                                                    lineHeight = 16.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Button
                Column(modifier = Modifier.fillMaxWidth()) {
                    val isNextEnabled = if (currentStep == 0) answerName.isNotBlank() else true
                    val isLastStep = currentStep == 7

                    GlassButton(
                        text = if (isLastStep) "Activate AI Focus Shield & Start" else "Continue",
                        onClick = {
                            if (isLastStep) {
                                onFinishIntakeSurvey(
                                    answerName.trim().ifEmpty { "Student Scholar" },
                                    answerClassLevel,
                                    answerIsBoardExam,
                                    answerPrimaryGoal,
                                    answerDistractionApp,
                                    answerPreferredTime,
                                    answerScreenTimeGoalMinutes,
                                    answerMotivationStyle
                                )
                            } else {
                                currentStep++
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("student_question_next_button"),
                        isPrimary = true,
                        enabled = isNextEnabled
                    )
                }
            }
        }
    }
}
