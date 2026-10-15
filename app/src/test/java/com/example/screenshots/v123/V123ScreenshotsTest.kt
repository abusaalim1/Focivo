package com.example.screenshots.v123

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.splash.FocivoSplash
import com.example.ui.splash.SplashVariant
import com.example.ui.splash.WelcomeInfo
import com.example.ui.theme.FocuslyTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.2.3 splash verify (branch feat/v123-splash, NEVER merged blindly):
 *  - splash NEW_USER mid-frame: radar WITHOUT the lime glow and WITHOUT the
 *    filled disc body (the 1.2.1 owner removal stays in effect)
 *  - splash RETURNING: the NEW WelcomeScene (aurora + greeting + real-data
 *    streak/today pill) with seeded WelcomeInfo(name="Abu Saalim",
 *    streakDays=7, todayMinutes=95), captured late in the animation
 * PNGs land in app/src/test/screenshots/v123/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V123ScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun v123_splash_new_user_midframe() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                FocivoSplash(
                    variant = SplashVariant.NEW_USER,
                    onFinished = {},
                    testProgress = 0.45f
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_splash_new_user_midframe.png")
    }

    @Test
    fun v123_splash_returning_welcome() {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                FocivoSplash(
                    variant = SplashVariant.RETURNING,
                    welcome = WelcomeInfo(name = "Abu Saalim", streakDays = 7, todayMinutes = 95),
                    onFinished = {},
                    testProgress = 0.85f
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v123/v123_splash_returning_welcome.png")
    }
}
