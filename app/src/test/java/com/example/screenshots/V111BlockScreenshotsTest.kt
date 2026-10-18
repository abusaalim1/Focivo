package com.example.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.BlockHubEntry
import com.example.ui.components.BlockHubSheet
import com.example.ui.components.PresetChooserContent
import com.example.ui.components.PresetChooserMode
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.StrictModeManager
import com.example.util.TimerThemeManager
import com.example.util.YouTubeStudyGuardManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1.1 — ITEM 3 (avatar picker: the "Use this design" save button must be
 * visible, pinned at the sheet bottom) and ITEM 4 (the single "Block" entry
 * + the Block hub sheet), at real phone size in dark and light.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V111BlockScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

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
            filePath = "src/test/screenshots/v111_block/$name.png"
        )
    }

    private fun initGuards() {
        StrictModeManager.init(context)
        YouTubeStudyGuardManager.init(context)
    }

    // ---------- ITEM 3: avatar chooser with the pinned save button ----------

    @Test
    fun v111_avatarChooser_dark() {
        themed(dark = true, themeId = "midnight") {
            PresetChooserContent(
                mode = PresetChooserMode.AVATAR,
                selectedRef = "preset:avatar:a03",
                onSelectionChange = {},
                onUploadPhoto = {},
                onPresetConfirmed = {},
            )
        }
        capture("avatar_chooser_dark")
    }

    @Test
    fun v111_avatarChooser_light() {
        themed(dark = false, themeId = "aurora") {
            PresetChooserContent(
                mode = PresetChooserMode.AVATAR,
                selectedRef = "preset:avatar:a03",
                onSelectionChange = {},
                onUploadPhoto = {},
                onPresetConfirmed = {},
            )
        }
        capture("avatar_chooser_light")
    }

    // ---------- ITEM 4: Block entry + Block hub sheet ----------

    @Test
    fun v111_blockEntry_dark() {
        initGuards()
        themed(dark = true, themeId = "midnight") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0B))
                    .padding(24.dp)
            ) {
                BlockHubEntry(
                    contentColor = Color.White,
                    onOpen = {}
                )
            }
        }
        capture("block_entry_dark")
    }

    @Test
    fun v111_blockHubSheet_dark() {
        initGuards()
        themed(dark = true, themeId = "midnight") {
            BlockHubSheet(
                onOpenScreenTimeBudgets = {},
                onDismiss = {}
            )
        }
        capture("block_hub_sheet_dark")
    }

    @Test
    fun v111_blockHubSheet_light() {
        initGuards()
        themed(dark = false, themeId = "aurora") {
            BlockHubSheet(
                onOpenScreenTimeBudgets = {},
                onDismiss = {}
            )
        }
        capture("block_hub_sheet_light")
    }
}
