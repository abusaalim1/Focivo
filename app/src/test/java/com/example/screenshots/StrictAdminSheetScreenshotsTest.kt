package com.example.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.ui.components.StrictAdminDisableSheet
import com.example.ui.components.StrictAdminExplainerSheet
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.MascotWardrobe
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
 * v1.3.0 follow-up (owner order 2026-10-17, item 2) — screenshot verification
 * for the device-admin premium sheets: explainer (strict enable path) and
 * disable guidance (protection-active path), in BOTH dark and light theme at
 * Pixel 8 size. PNGs land in app/src/test/screenshots/v130-admin/.
 *
 * The sheets are captured without launching the system activation screen
 * (onEnable/onOptOut/onDismiss are no-ops here); the real activation path is
 * exercised by the unit tests + CI compile.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class StrictAdminSheetScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun initAll() {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, "midnight")
        MascotWardrobe.init(context)
    }

    private fun themed(dark: Boolean, content: @Composable () -> Unit) {
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ThemedScreenBackground()
                        content()
                    }
                }
            }
        }
    }

    private fun shot(dark: Boolean, name: String, content: @Composable () -> Unit) {
        initAll()
        themed(dark, content)
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v130-admin/$name.png")
    }

    @Test
    fun explainer_dark() {
        shot(true, "admin_explainer_dark") {
            StrictAdminExplainerSheet(onEnable = {}, onOptOut = {}, onDismiss = {})
        }
    }

    @Test
    fun explainer_light() {
        shot(false, "admin_explainer_light") {
            StrictAdminExplainerSheet(onEnable = {}, onOptOut = {}, onDismiss = {})
        }
    }

    @Test
    fun disable_dark() {
        shot(true, "admin_disable_dark") {
            StrictAdminDisableSheet(onOpenSettings = {}, onDismiss = {})
        }
    }

    @Test
    fun disable_light() {
        shot(false, "admin_disable_light") {
            StrictAdminDisableSheet(onOpenSettings = {}, onDismiss = {})
        }
    }
}
