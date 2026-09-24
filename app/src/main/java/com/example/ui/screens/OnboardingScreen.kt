package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CivoChatBubble
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme

@Composable
fun OnboardingScreen(
    onCompleteOnboarding: (dailyGoalMinutes: Int) -> Unit
) {
    val isDark = isAppInDarkTheme()
    var currentStep by remember { mutableIntStateOf(0) }
    var selectedGoalHours by remember { mutableIntStateOf(4) }
    var isTypingFinished by remember { mutableStateOf(false) }

    val steps = listOf(
        Pair("Hi, I'm Civo. I'll help you stay focused on your studies!", "Eliminate modern cognitive noise. Organize your daily priorities and dedicate pure unbroken time to your essential work."),
        Pair("Build deeper focus habits with me.", "Harness science-backed focus cadences, customizable deep-work intervals, and calm generative ambient soundscapes."),
        Pair("Track your study growth effortless.", "Insightful metrics without judgment. Observe your peak focus hours, consistency streaks, and energy flow effortlessly."),
        Pair("Set your daily focus goal with Civo.", "Choose a sustainable target. You can adjust this anytime in your preferences.")
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("onboarding_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Indicator Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 0..3) {
                    val isActive = i <= currentStep
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) RegainLimePrimary else (if (isDark) Color(0x22FFFFFF) else Color(0x18000000))
                            )
                    )
                }
            }

            // Main Content Area with Animated Transition & Civo Chat Bubble
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "onboarding_step"
            ) { step ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    CivoChatBubble(
                        text = steps[step].first,
                        subtext = steps[step].second,
                        avatarSize = 58.dp,
                        typingSpeedMs = 35L,
                        onTypingFinished = { isTypingFinished = true }
                    )

                    if (step == 3) {
                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = "Daily Focus Target",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Goal Options
                        val goalOptions = listOf(2, 4, 6, 8)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            goalOptions.forEach { hrs ->
                                val isSelected = hrs == selectedGoalHours
                                GlassCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedGoalHours = hrs },
                                    shape = RoundedCornerShape(18.dp),
                                    accentBorder = isSelected
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSelected) RegainLimePrimary.copy(alpha = 0.15f) else Color.Transparent
                                            )
                                            .padding(vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "${hrs}h",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                color = if (isSelected) RegainLimePrimary else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = if (hrs == 4) "Balanced" else if (hrs == 6) "Deep" else if (hrs == 2) "Light" else "Intense",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) (if (isDark) RegainLimePrimary else RegainLimeDeepText) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                currentStep -= 1
                                isTypingFinished = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Back",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                GlassButton(
                    text = if (currentStep == 3) "Start Studying with Civo" else "Continue",
                    onClick = {
                        if (currentStep < 3) {
                            currentStep += 1
                            isTypingFinished = false
                        } else {
                            onCompleteOnboarding(selectedGoalHours * 60)
                        }
                    },
                    isPrimary = true,
                    enabled = isTypingFinished
                )
            }
        }
    }
}

