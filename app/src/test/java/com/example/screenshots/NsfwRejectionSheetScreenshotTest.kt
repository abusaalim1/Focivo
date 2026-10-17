package com.example.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.components.NsfwRejectionSheetContent
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.focivoTokens
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Test-sources-only debug stub that force-shows the NSFW rejection sheet body
 * so the design can be eyeballed (Focivo 1.3.2, item 6).
 *
 * Renders [NsfwRejectionSheetContent] inside a sheet-styled container over the
 * design-system scrim — deterministic under Robolectric (no bottom-sheet
 * show animation involved). The production wrapper is
 * [com.example.ui.components.NsfwRejectionSheet].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class NsfwRejectionSheetScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun nsfw_rejection_sheet_screenshot() {
        composeTestRule.setContent {
            FocuslyTheme {
                val tokens = focivoTokens()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tokens.scrim)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(tokens.surface)
                    ) {
                        NsfwRejectionSheetContent(
                            onDismiss = {},
                            onChooseAnother = {}
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/nsfw-rejection-sheet.png"
        )
    }
}
