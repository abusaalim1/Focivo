package com.example.screenshots

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.LeaderboardUser
import com.example.ui.components.MascotPose
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FlowOption
import com.example.ui.screens.FlowQuestion
import com.example.ui.screens.FlowQuestionContent
import com.example.ui.screens.GroupStepContent
import com.example.ui.screens.OpeningIntroVisual
import com.example.ui.screens.PermissionGrantContent
import com.example.ui.screens.PermissionSpec
import com.example.ui.screens.ShowcaseSlide
import com.example.ui.screens.ShowcaseSlideContent
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.TimerThemeManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.5.0 rebuilt onboarding — every step of the new flow rendered for
 * eyeball review: opening animation frame, two question screens, two mascot
 * showcase slides, the group step, and two permission screens.
 * PNGs land in app/src/test/screenshots/round7/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V150OnboardingScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun useTheme(id: String) {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, id)
    }

    private fun themed(content: @Composable () -> Unit) {
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ThemedScreenBackground()
                        content()
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/round7/$name.png")
    }

    private fun fakeUsers(): List<LeaderboardUser> = listOf(
        LeaderboardUser(id = "1", displayName = "Riya Sharma", todayStudySeconds = 7200L, weeklyStudySeconds = 374400L, studySeconds = 180000L, streak = 12),
        LeaderboardUser(id = "2", displayName = "Arjun Mehta", todayStudySeconds = 5400L, weeklyStudySeconds = 316800L, studySeconds = 150000L, streak = 9),
        LeaderboardUser(id = "3", displayName = "Abu Saalim", todayStudySeconds = 3600L, weeklyStudySeconds = 270000L, studySeconds = 120000L, streak = 7, isCurrentUser = true)
    )

    @Test
    fun onboarding_opening_animation_frame() {
        useTheme("midnight")
        themed { OpeningIntroVisual(progress = 1f) }
        capture("onboarding_opening_animation")
    }

    @Test
    fun onboarding_question_name() {
        useTheme("midnight")
        val q = FlowQuestion(
            key = "name",
            stepLabel = "STEP 1 OF 8 · PROFILE",
            title = "What should I call you?",
            subtitle = "Your name or study alias.",
            headerIconRes = R.drawable.ic3d_person,
            isNameInput = true
        )
        themed {
            FlowQuestionContent(
                question = q,
                stepLabel = q.stepLabel,
                progress = 1f / 17f,
                nameText = "",
                onNameChange = {},
                selectedValues = emptySet(),
                onToggleValue = {},
                onContinue = {},
                onBack = null
            )
        }
        capture("onboarding_question_name")
    }

    @Test
    fun onboarding_question_goal() {
        useTheme("midnight")
        val q = FlowQuestion(
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
        )
        themed {
            FlowQuestionContent(
                question = q,
                stepLabel = q.stepLabel,
                progress = 4f / 17f,
                nameText = "",
                onNameChange = {},
                selectedValues = setOf("Building Daily Study Consistency"),
                onToggleValue = {},
                onContinue = {},
                onBack = {}
            )
        }
        capture("onboarding_question_goal")
    }

    @Test
    fun onboarding_showcase_blocking() {
        useTheme("midnight")
        val slide = ShowcaseSlide(
            pose = MascotPose.STOP_SIGN,
            caption = "SMART BLOCKING",
            line = "Distracting apps stay closed while you study."
        )
        themed {
            ShowcaseSlideContent(
                slide = slide,
                slideIndex = 0,
                totalSlides = 4,
                onContinue = {},
                onSkip = {}
            )
        }
        capture("onboarding_showcase_blocking")
    }

    @Test
    fun onboarding_showcase_ai_guard() {
        useTheme("midnight")
        val slide = ShowcaseSlide(
            pose = MascotPose.GUARD,
            caption = "AI STUDY GUARD",
            line = "The AI guard keeps every session honest."
        )
        themed {
            ShowcaseSlideContent(
                slide = slide,
                slideIndex = 1,
                totalSlides = 4,
                onContinue = {},
                onSkip = {}
            )
        }
        capture("onboarding_showcase_ai_guard")
    }

    @Test
    fun onboarding_group_step() {
        useTheme("midnight")
        themed {
            GroupStepContent(
                users = fakeUsers(),
                onContinue = {},
                onSkip = {}
            )
        }
        capture("onboarding_group_step")
    }

    @Test
    fun onboarding_permission_notifications() {
        useTheme("midnight")
        val spec = PermissionSpec(
            id = "notifications",
            rowLabel = "Notification permission",
            bubbleLead = "To ",
            bubbleAccent = "send you focus reminders and timer alerts",
            bubbleTail = ", we need notification access.",
            privacyLine = "Your privacy matters to us. We never share your data.",
            whyBody = "Reminders for your study windows and timer alerts. Nothing else — no spam, ever.",
            pose = MascotPose.WELCOME
        )
        themed {
            PermissionGrantContent(
                spec = spec,
                progress = 14f / 17f,
                onAllow = {},
                onLater = {},
                onBack = {}
            )
        }
        capture("onboarding_permission_notifications")
    }

    @Test
    fun onboarding_permission_usage_access() {
        useTheme("midnight")
        val spec = PermissionSpec(
            id = "usage",
            rowLabel = "Usage permission",
            bubbleLead = "To ",
            bubbleAccent = "find your distracting apps",
            bubbleTail = ", we need usage access.",
            privacyLine = "Your privacy matters to us. Usage stats never leave your phone.",
            whyBody = "This shows which apps eat your study time, so the guard blocks the right ones.",
            pose = MascotPose.STUDY_POSE
        )
        themed {
            PermissionGrantContent(
                spec = spec,
                progress = 15f / 17f,
                onAllow = {},
                onLater = {},
                onBack = {}
            )
        }
        capture("onboarding_permission_usage_access")
    }
}
