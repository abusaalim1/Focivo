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
import com.example.ui.components.PresetChooserContent
import com.example.ui.components.PresetChooserMode
import com.example.ui.components.ThemedScreenBackground
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
 * v1.3.2 ITEM 8 — preset picture pickers. Both chooser bodies (profile
 * avatars + group logos) at real phone size (Pixel 8 qualifiers) in
 * app-dark ("midnight" backdrop) and app-light ("aurora" backdrop), with
 * a preset pre-selected to show the lime selected ring.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V132PresetPickerScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    /** Same stack as MainActivity: backdrop behind everything. */
    private fun themed(dark: Boolean, themeId: String, content: @Composable () -> Unit) {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, themeId)
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

    private fun capture(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v132_presets/$name.png"
        )
    }

    private fun avatarChooser(): @Composable () -> Unit = {
        PresetChooserContent(
            mode = PresetChooserMode.AVATAR,
            selectedRef = "preset:avatar:a03",
            onSelectionChange = {},
            onUploadPhoto = {},
            onPresetConfirmed = {},
        )
    }

    private fun groupChooser(): @Composable () -> Unit = {
        PresetChooserContent(
            mode = PresetChooserMode.GROUP,
            selectedRef = "preset:group:g07",
            onSelectionChange = {},
            onUploadPhoto = {},
            onPresetConfirmed = {},
        )
    }

    @Test
    fun avatarChooser_dark() {
        themed(dark = true, themeId = "midnight") { avatarChooser()() }
        capture("preset_avatars_dark")
    }

    @Test
    fun avatarChooser_light() {
        themed(dark = false, themeId = "aurora") { avatarChooser()() }
        capture("preset_avatars_light")
    }

    @Test
    fun groupChooser_dark() {
        themed(dark = true, themeId = "midnight") { groupChooser()() }
        capture("preset_groups_dark")
    }

    @Test
    fun groupChooser_light() {
        themed(dark = false, themeId = "aurora") { groupChooser()() }
        capture("preset_groups_light")
    }
}
