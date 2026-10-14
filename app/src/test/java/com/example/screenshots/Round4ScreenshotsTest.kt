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
import com.example.data.model.FocusSessionEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.DeepFocusSettingsSheet
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FocusScreen
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
 * v1.4.0-round4: owner-review screenshots.
 * (a) Theme picker with the points-lock mix: repository default balance is
 *     0 Focus Points, so Aurora/Forest/Mint are unlocked and the other 8
 *     show lock badges + "N pts" labels with the "Your Focus Points: 0"
 *     header.
 * (b-d) Timer in Sakura / Midnight / Royal — with the NEW distinct
 *     backdrops (blossoms, stars+moon, marble+gold) replacing the smoke ones.
 * Screens are wrapped in the same stack MainActivity uses:
 * Box { ThemedScreenBackground(); <Screen/> }.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class Round4ScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun useTheme(id: String) {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, id)
    }

    private fun fakePrefs(): UserPreferencesEntity = UserPreferencesEntity(
        currentUserName = "Scholar",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 0
    )

    /** Same stack as MainActivity: backdrop behind everything. */
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

    private fun focusScreen() {
        themed {
            FocusScreen(
                remainingSeconds = 1500,
                targetSeconds = 1500,
                isRunning = false,
                currentMode = "Deep Work",
                currentTaskTitle = "Physics",
                distractionsCount = 0,
                ambientSound = "Silent",
                userPreferences = fakePrefs(),
                onStartTimer = {},
                onPauseTimer = {},
                onResumeTimer = {},
                onFinishEarly = {},
                onAddFiveMinutes = {},
                onSkipBreak = {},
                onSelectMode = { _, _ -> },
                onSelectAmbientSound = {},
                onLogDistraction = {},
                onUpdateTaskTitle = {}
            )
        }
    }

    @Test
    fun theme_picker_locked_mix() {
        useTheme("aurora")
        themed {
            DeepFocusSettingsSheet(
                onDismiss = {},
                initialShowThemePicker = true
            )
        }
        capture("theme_picker_locked_mix")
    }

    @Test
    fun study_timer_sakura_new_backdrop() {
        useTheme("sakura")
        focusScreen()
        capture("study_timer_sakura_new")
    }

    @Test
    fun study_timer_midnight_new_backdrop() {
        useTheme("midnight")
        focusScreen()
        capture("study_timer_midnight_new")
    }

    @Test
    fun study_timer_royal_new_backdrop() {
        useTheme("royal")
        focusScreen()
        capture("study_timer_royal_new")
    }

    @Test
    fun study_timer_galaxy_new_backdrop() {
        useTheme("galaxy")
        focusScreen()
        capture("study_timer_galaxy_new")
    }

    private fun capture(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/round4/$name.png"
        )
    }
}
