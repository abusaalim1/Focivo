package com.example.screenshots.v124

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.MoreOptionsExpander
import com.example.ui.components.PomodoroDesignPicker
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.ui.timer.PomodoroStyle
import com.example.util.PomodoroStyleManager
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
 * v1.2.4 screenshot verify (branch fix/v124-hotfix):
 *  - Customize Timer sheet progressive disclosure: the "More options"
 *    expander row (collapsed + expanded states) and the Pomodoro design
 *    picker at 0 Focus Points, where only TICK_RING is unlocked and the
 *    other three designs render with the gold lock + price badge.
 * PNGs land in app/src/test/screenshots/v124/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V124ScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun themed(dark: Boolean = true, content: @Composable () -> Unit) {
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                        ThemedScreenBackground()
                        content()
                    }
                }
            }
        }
    }

    @Test
    fun v124_customize_more_options() {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, "aurora")
        PomodoroStyleManager.init(context)
        val textPrimary = Color(0xFFF0F4ED)
        val textSecondary = Color(0xFFA0A89E)
        val cardBg = Color(0xFF131315)
        val cardBorder = Color(0xFF1F1F22)
        val accent = Color(0xFFB6F13A)
        themed(dark = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1410))
                    .padding(20.dp)
                    .testTag("more_options_section")
            ) {
                MoreOptionsExpander(
                    expanded = false,
                    onToggle = {},
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    accent = accent
                )
                Spacer(modifier = Modifier.height(12.dp))
                MoreOptionsExpander(
                    expanded = true,
                    onToggle = {},
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    accent = accent
                )
                Spacer(modifier = Modifier.height(20.dp))
                // Fresh prefs carry 0 Focus Points: only TICK_RING unlocked,
                // the other three render with gold lock + price.
                PomodoroDesignPicker(
                    selectedStyle = PomodoroStyle.TICK_RING,
                    onStyleTap = {},
                    textPrimary = textPrimary,
                    cardBorder = cardBorder,
                    focusPoints = 0,
                    isAdmin = false,
                    onLockedTap = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_options_section", useUnmergedTree = true)
            .captureRoboImage("src/test/screenshots/v124/v124_customize_more_options.png")
    }
}
