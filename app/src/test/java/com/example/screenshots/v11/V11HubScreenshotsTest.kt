package com.example.screenshots.v11

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.AiStudyGuardHubSheet
import com.example.ui.components.FocusShieldHubSheet
import com.example.ui.components.WidgetsShowcaseSheet
import com.example.ui.screens.StudyChannelsScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.MascotWardrobe
import com.example.util.TimerFaceManager
import com.example.util.TimerThemeManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1: hub + guard + widgets surfaces.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V11HubScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun initManagers() {
        TimerThemeManager.init(context)
        TimerFaceManager.init(context)
        MascotWardrobe.init(context)
    }

    private fun themed(dark: Boolean = true, content: @Composable () -> Unit) {
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.ui.components.ThemedScreenBackground()
                        content()
                    }
                }
            }
        }
    }

    @Test
    fun v11_study_channels() {
        initManagers()
        themed(dark = true) {
            StudyChannelsScreen(onBack = {})
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/study_channels.png")
    }

    @Test
    fun v11_ai_guard_hub() {
        initManagers()
        themed(dark = true) {
            AiStudyGuardHubSheet(onDismiss = {})
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/ai_guard_hub.png")
    }

    @Test
    fun v11_focus_shield_hub() {
        initManagers()
        themed(dark = true) {
            FocusShieldHubSheet(
                isShieldEnabled = true,
                isShieldTimerRunning = false,
                remainingSeconds = 0,
                blockedPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
                blockedAttemptsCount = 3,
                installedApps = emptyList(),
                onToggleMasterShield = {},
                onToggleAppBlocked = { _, _ -> },
                onDismiss = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/focus_shield_hub.png")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun v11_widgets_gallery() {
        initManagers()
        themed(dark = true) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            WidgetsShowcaseSheet(
                sheetState = sheetState,
                onDismiss = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/widgets_gallery.png")
    }
}
