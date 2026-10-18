package com.example.screenshots.v133

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.ui.components.CodeSheetMode
import com.example.ui.components.RecoveryCodeSheet
import com.example.ui.theme.FocuslyTheme
import com.example.ui.viewmodel.RecoveryCodeFlow
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.3.3 (ITEM 10): premium 6-digit OTP code sheet - dark AND light, recovery
 * mode and signup mode (wrong-code state). PNGs land in
 * app/src/test/screenshots/v133/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@OptIn(ExperimentalMaterial3Api::class)
class V133RecoveryCodeSheetScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun themed(dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                content()
            }
        }
    }

    private fun capture(name: String) {
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v133/recoverycode_$name.png")
    }

    @Test
    fun sheet_recovery_dark_cooldown() {
        themed(dark = true) {
            RecoveryCodeSheet(
                email = "neomix@example.com",
                mode = CodeSheetMode.RECOVERY,
                codeState = RecoveryCodeFlow.CodeState.Entry,
                resendCooldownSeconds = 42,
                onSubmitCode = {},
                onResend = {},
                onDismiss = {}
            )
        }
        capture("recovery_dark")
    }

    @Test
    fun sheet_signup_light_wrong_code() {
        themed(dark = false) {
            RecoveryCodeSheet(
                email = "neomix@example.com",
                mode = CodeSheetMode.SIGNUP,
                codeState = RecoveryCodeFlow.CodeState.WrongCode(1),
                resendCooldownSeconds = 0,
                onSubmitCode = {},
                onResend = {},
                onDismiss = {}
            )
        }
        capture("signup_light_wrong")
    }

    @Test
    fun sheet_recovery_dark_expired() {
        themed(dark = true) {
            RecoveryCodeSheet(
                email = "neomix@example.com",
                mode = CodeSheetMode.RECOVERY,
                codeState = RecoveryCodeFlow.CodeState.ExpiredCode,
                resendCooldownSeconds = 0,
                onSubmitCode = {},
                onResend = {},
                onDismiss = {}
            )
        }
        capture("recovery_dark_expired")
    }
}
