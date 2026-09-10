package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.MascotPose
import com.example.ui.components.RegainMascotView
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary

@Composable
fun OnboardingScreen(
    onCompleteOnboarding: (dailyGoalMinutes: Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var currentStep by remember { mutableIntStateOf(0) }
    var selectedGoalHours by remember { mutableIntStateOf(4) }

    val steps = listOf(
        Pair("Make time for\nwhat matters.", "Eliminate modern cognitive noise. Organize your daily priorities and dedicate pure unbroken time to your life's essential work."),
        Pair("Build deeper\nfocus habits.", "Harness science-backed focus cadences, customizable deep-work intervals, and calm generative ambient soundscapes."),
        Pair("Understand your\nproductivity.", "Insightful metrics without judgment. Observe your peak focus hours, consistency streaks, and energy flow effortlessly."),
        Pair("Set your daily\nfocus goal.", "Choose a sustainable target. You can adjust this anytime in your preferences.")
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Indicator Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 0..3) {
                    val isActive = i == currentStep
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

            // Main Content Area with Animated Transition
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "onboarding_step"
            ) { step ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (step == 0) {
                        RegainMascotView(
                            size = 120.dp,
                            pose = MascotPose.IDLE,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    } else if (step == 1) {
                        RegainMascotView(
                            size = 100.dp,
                            pose = MascotPose.STUDYING,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                    }

                    Text(
                        text = steps[step].first,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Normal,
                            lineHeight = 46.sp,
                            fontSize = 36.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = steps[step].second,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 24.sp
                        )
                    )

                    if (step == 3) {
                        Spacer(modifier = Modifier.height(32.dp))

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
                                            .padding(vertical = 18.dp),
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
                            .clickable { currentStep -= 1 }
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
                    text = if (currentStep == 3) "Enter Focivo" else "Continue",
                    onClick = {
                        if (currentStep < 3) {
                            currentStep += 1
                        } else {
                            onCompleteOnboarding(selectedGoalHours * 60)
                        }
                    },
                    isPrimary = true
                )
            }
        }
    }
}
